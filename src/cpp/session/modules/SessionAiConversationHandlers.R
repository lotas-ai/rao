# SessionAiConversationHandlers.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

.rs.addFunction("get_conversation_names_path", function() {
   base_ai_dir <- .rs.get_ai_base_dir()
   
   if (!dir.exists(base_ai_dir)) {
      dir.create(base_ai_dir, recursive = TRUE, showWarnings = FALSE)
   }
   
   return(file.path(base_ai_dir, "conversation_names.csv"))
})

.rs.addFunction("read_conversation_names", function() {
   csv_path <- .rs.get_conversation_names_path()

   if (!file.exists(csv_path)) {
      names <- data.frame(
         conversation_id = integer(),
         name = character(),
         stringsAsFactors = FALSE
      )
      write.csv(names, csv_path, row.names = FALSE)
      return(names)
   }
   
   tryCatch({
      df <- read.csv(csv_path, stringsAsFactors = FALSE)
      df
   }, error = function(e) {
      names <- data.frame(
         conversation_id = integer(),
         name = character(),
         stringsAsFactors = FALSE
      )
      write.csv(names, csv_path, row.names = FALSE)
      return(names)
   })
})

.rs.addFunction("get_conversation_name", function(conversation_id) {
   names <- .rs.read_conversation_names()
   
   idx <- which(names$conversation_id == conversation_id)
   
   if (length(idx) == 0) {
      return("New conversation")
   } else {
      return(trimws(names$name[idx]))
   }
})

.rs.addFunction("set_conversation_name", function(conversation_id, name) {
   names <- .rs.read_conversation_names()

   idx <- which(names$conversation_id == conversation_id)
   
   if (length(idx) == 0) {
      new_row <- data.frame(
         conversation_id = conversation_id,
         name = name,
         stringsAsFactors = FALSE
      )
      names <- rbind(names, new_row)
   } else {
      names$name[idx] <- name
   }
   
   csv_path <- .rs.get_conversation_names_path()
   write.csv(names, csv_path, row.names = FALSE)

   return(TRUE)
})

.rs.addFunction("delete_conversation_name", function(conversation_id) {
   names <- .rs.read_conversation_names()
   
   idx <- which(names$conversation_id == conversation_id)
   
   if (length(idx) > 0) {
      names <- names[-idx, ]
      
      write.csv(names, .rs.get_conversation_names_path(), row.names = FALSE)
   }
   
   return(TRUE)
})

.rs.addFunction("list_conversation_names", function() {
   names <- .rs.read_conversation_names()
   return(names)
})



.rs.addJsonRpcHandler("get_conversation_name", function(conversation_id) {
   return(.rs.get_conversation_name(as.integer(conversation_id)))
})

.rs.addJsonRpcHandler("set_conversation_name", function(conversation_id, name) {
   result <- .rs.set_conversation_name(as.integer(conversation_id), name)
   return(result)
})

.rs.addJsonRpcHandler("delete_conversation_name", function(conversation_id) {
   return(.rs.delete_conversation_name(as.integer(conversation_id)))
})

.rs.addJsonRpcHandler("list_conversation_names", function() {
   return(.rs.list_conversation_names())
})

.rs.addFunction("should_prompt_for_name", function(conversation_id) {
   names <- .rs.read_conversation_names()
   
   idx <- which(names$conversation_id == conversation_id)
   
   if (length(idx) > 0) {
      current_name <- names$name[idx]
      # If conversation already has a real name, never prompt
      if (current_name != "New conversation" && !grepl("^New conversation [0-9]+$", current_name)) {
         return(FALSE)
      }
      
      if (current_name == "New conversation" || grepl("^New conversation [0-9]+$", current_name)) {
         conversation_log <- .rs.read_conversation_log()
         
         if (length(conversation_log) > 0) {
            user_msgs <- sum(sapply(conversation_log, function(msg) !is.null(msg$role) && msg$role == "user"))
            assistant_msgs <- sum(sapply(conversation_log, function(msg) !is.null(msg$role) && msg$role == "assistant"))
            
            return(user_msgs >= 1 && assistant_msgs >= 1)
         }
      }
      return(FALSE)
   }
   
   return(TRUE)
})

.rs.addJsonRpcHandler("should_prompt_for_name", function() {
   conversation_id <- .rs.get_current_conversation_index()
   return(.rs.should_prompt_for_name(conversation_id))
})

 

.rs.addJsonRpcHandler("clear_ai_conversation", function() {
   .rs.clear_ai_conversation()
   
   return(TRUE)
})

.rs.addFunction("clear_ai_conversation", function() {
   .rs.check_required_packages()
   
   conversation_id <- .rs.get_current_conversation_index()
   
   .rs.store_conversation_variables(conversation_id)
   
   paths <- .rs.get_ai_file_paths()
   
   # No need to initialize conversation.json anymore - using conversation_log.json exclusively
   
   .rs.setVar("message_id_counter", 0)
   
   initial_log <- list()
   .rs.write_conversation_log(initial_log)
   
   empty_history <- data.frame(filename = character(), order = integer(), stringsAsFactors = FALSE)
   write.table(empty_history, paths$script_history_path, sep = "\t", row.names = FALSE, quote = FALSE)
   
   .rs.clear_file_changes_log()
   
   .rs.clear_conversation_diff_log()
   
   empty_buttons <- data.frame(
      message_id = integer(),
      buttons_run = character(),
      next_button = character(),
      on_deck_button = character(),
      stringsAsFactors = FALSE
   )
   .rs.write_message_buttons(empty_buttons)
   
   .rs.clear_conversation_variables()
   
   .rs.reset_assistant_message_count()
   
   .rs.update_conversation_display()

   return(TRUE)
})

.rs.addFunction("cleanup_error_messages", function() {
   conversation_log <- .rs.read_conversation_log()
   
   if (length(conversation_log) <= 2) {
      return()
   }
   
   id_to_index_map <- list()
   for (i in 1:length(conversation_log)) {
      if (!is.null(conversation_log[[i]]$id)) {
         id_to_index_map[[as.character(conversation_log[[i]]$id)]] <- i
      }
   }
   
   error_fix_pairs <- list()
   
   error_indices <- c()
   for (i in 1:length(conversation_log)) {
      msg <- conversation_log[[i]]
      if (!is.null(msg$role) && msg$role == "user") {
         if (is.character(msg$content) && length(msg$content) == 1) {
            is_run_error <- grepl("\\*\\*Output from running .+:\\*\\*", msg$content) && grepl("Error:", msg$content)
            is_terminal_error <- grepl("\\*\\*Output from terminal \\(Exit code: [^0]", msg$content)
            is_knitting_error <- grepl("\\*\\*Output from knitting .+:\\*\\*", msg$content) && 
                              grepl("Error", msg$content, fixed = TRUE)
            
            if (is_run_error || is_terminal_error || is_knitting_error) {
               error_indices <- c(error_indices, i)
            }
         }
      }
   }
   
   for (error_idx in error_indices) {
      error_msg <- conversation_log[[error_idx]]
      
      success_idx <- NULL
      if (error_idx < length(conversation_log)) {
         for (i in (error_idx+1):length(conversation_log)) {
            msg <- conversation_log[[i]]
            if (!is.null(msg$role) && msg$role == "user") {
            if (is.character(msg$content) && length(msg$content) == 1) {
               is_run_success <- grepl("\\*\\*Output from running .+:\\*\\*", msg$content) && !grepl("Error:", msg$content)
               is_terminal_success <- grepl("\\*\\*Output from terminal \\(Exit code: 0", msg$content)
               is_knitting_success <- grepl("\\*\\*Output from knitting .+:\\*\\*", msg$content) && 
                                   !grepl("Error", msg$content, fixed = TRUE)
               
               if (is_run_success || is_terminal_success || is_knitting_success) {
                  success_idx <- i
                  break
               }
            }
         }
      }
      }
      
      if (!is.null(success_idx)) {
         error_fix_pairs <- c(error_fix_pairs, list(list(
            error = error_idx,
            success = success_idx
         )))
      } else {
         error_fix_pairs <- c(error_fix_pairs, list(list(
            error = error_idx,
            success = NULL
         )))
      }
   }
   
   messages_for_exclusion <- c()
   
   for (pair in error_fix_pairs) {
      error_idx <- pair$error
      success_idx <- pair$success
      
      messages_for_exclusion <- c(messages_for_exclusion, error_idx)
      
      error_code_idx <- NULL
      error_code_id <- NULL
      if (!is.null(conversation_log[[error_idx]]$related_to)) {
         error_code_id <- conversation_log[[error_idx]]$related_to
         if (!is.null(id_to_index_map[[as.character(error_code_id)]])) {
            error_code_idx <- id_to_index_map[[as.character(error_code_id)]]
         }
      }
      
      if (is.null(error_code_idx) && error_idx > 1) {
         for (i in (error_idx-1):1) {
            if (!is.null(conversation_log[[i]]$role) && conversation_log[[i]]$role == "assistant" && length(conversation_log[[i]]$content) == 1 && grepl("```", conversation_log[[i]]$content)) {
               error_code_idx <- i
               error_code_id <- conversation_log[[i]]$id
               break
            }
         }
      }
      
      if (!is.null(error_code_idx)) {
         messages_for_exclusion <- c(messages_for_exclusion, error_code_idx)
         for (i in 1:length(conversation_log)) {
            if (!is.null(conversation_log[[i]]$related_to) && 
                conversation_log[[i]]$related_to == error_code_id) {
               messages_for_exclusion <- c(messages_for_exclusion, i)
            }
         }
      }
      
      protected_indices <- c()
      if (!is.null(success_idx)) {
         protected_indices <- c(protected_indices, success_idx)
         
         fixing_code_idx <- NULL
         fixing_code_id <- NULL
         if (!is.null(conversation_log[[success_idx]]$related_to)) {
            fixing_code_id <- conversation_log[[success_idx]]$related_to
            if (!is.null(id_to_index_map[[as.character(fixing_code_id)]])) {
               fixing_code_idx <- id_to_index_map[[as.character(fixing_code_id)]]
            }
         }
         
         if (is.null(fixing_code_idx) && success_idx > max(1, error_idx+1)) {
            for (i in (success_idx-1):max(1, error_idx+1)) {
               if (!is.null(conversation_log[[i]]$role) && conversation_log[[i]]$role == "assistant" && length(conversation_log[[i]]$content) == 1 && grepl("```", conversation_log[[i]]$content)) {
                  fixing_code_idx <- i
                  fixing_code_id <- conversation_log[[i]]$id
                  break
               }
            }
         }
         
         if (!is.null(fixing_code_idx)) {
            protected_indices <- c(protected_indices, fixing_code_idx)
            for (i in 1:length(conversation_log)) {
               if (!is.null(conversation_log[[i]]$related_to) && 
                   conversation_log[[i]]$related_to == fixing_code_id) {
                  protected_indices <- c(protected_indices, i)
               }
            }
            
            for (i in (error_idx+1):(success_idx-1)) {
               if (!(i %in% protected_indices)) {
                  messages_for_exclusion <- c(messages_for_exclusion, i)
               }
            }
         } else {
            for (i in (error_idx+1):(success_idx-1)) {
               messages_for_exclusion <- c(messages_for_exclusion, i)
            }
         }
      } else {
         for (i in (error_idx+1):length(conversation_log)) {
            messages_for_exclusion <- c(messages_for_exclusion, i)
         }
      }
   }
   
   messages_for_exclusion <- unique(messages_for_exclusion)
   
   if (exists("protected_indices")) {
      messages_for_exclusion <- setdiff(messages_for_exclusion, protected_indices)
   }
   
   .rs.write_conversation_log(conversation_log)
})

.rs.addJsonRpcHandler("switch_conversation", function(index) {
   result <- .rs.switch_conversation(index)
   return(result)
})

.rs.addJsonRpcHandler("create_new_conversation", function() {
   return(.rs.create_new_conversation())
})

.rs.addFunction("switch_conversation", function(index) {   
   if (!is.numeric(index)) {
      stop("Index must be a number: ", class(index))
   }
   
   index <- as.integer(index)
   
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", index))
   
   if (!dir.exists(conversation_dir)) {
      return(list(
         success = FALSE,
         message = "Conversation does not exist"
      ))
   }
   
   .rs.check_required_packages()
   
   conversation_id <- .rs.get_current_conversation_index()
   
   .rs.store_conversation_variables(conversation_id)
   
   .rs.set_current_conversation_index(index)
   
   .rs.load_conversation_variables(index)
   
   # Reset the sequence counter for the new conversation
   .rs.reset_ai_operation_sequence(index)
   
   conversation_log <- .rs.read_conversation_log()
   
   max_id <- 0
   # Check conversation_log for highest IDs
   if (length(conversation_log) > 0) {
      log_ids <- sapply(conversation_log, function(msg) {
         if (!is.null(msg$id) && is.numeric(msg$id)) {
            return(msg$id)
         } else {
            return(0)
         }
      })
      
      valid_log_ids <- log_ids[!is.na(log_ids) & is.finite(log_ids)]
      if (length(valid_log_ids) > 0) {
         max_id <- max(max_id, max(valid_log_ids))
      }
   }
   
   .rs.setVar("message_id_counter", max_id)
   
   .rs.update_conversation_display()
   
   return(list(
      success = TRUE,
      index = index
   ))
})

.rs.addFunction("create_new_conversation", function() {
   new_index <- .rs.create_new_conversation_runner()
   
   base_ai_dir <- .rs.get_ai_base_dir()
   return(list(
      success = TRUE,
      index = new_index
   ))
})

.rs.addJsonRpcHandler("list_conversations", function() {
   indices <- .rs.list_conversation_indices()
   
   conversations <- as.character(indices)
   
   return(conversations)
})

.rs.addFunction("ai.generate_conversation_name", function(conversation_id) {
   # BULLETPROOF FIX: Check if conversation already has a name before generating
   # This prevents all double conversation name requests regardless of source
   existing_name <- .rs.get_conversation_name(conversation_id)
   
   # If conversation already has a non-default name, return it without making API call
   if (!is.null(existing_name) && 
       existing_name != "New conversation" && 
       !grepl("^New conversation [0-9]+$", existing_name)) {
      return(existing_name)
   }
   
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversation_id))
   
   conversation_log_file <- file.path(conversation_dir, "conversation_log.json")
   if (!file.exists(conversation_log_file)) {
      return("New conversation")
   }
   
   conversation_log <- jsonlite::fromJSON(readLines(conversation_log_file, warn = FALSE), simplifyVector = FALSE)
   if (length(conversation_log) < 2) {
      return("New conversation")
   }
   
   user_assistant_messages <- Filter(function(msg) {
      return(!is.null(msg$role) && (msg$role == "user" || msg$role == "assistant") && 
            !is.null(msg$content) && 
            is.null(msg$function_call) &&
            (is.null(msg$type) || msg$type != "function_call_output") &&
            (is.list(msg$content) || 
            (is.character(msg$content))))
   }, conversation_log)
   
   if (length(user_assistant_messages) > 3) {
      user_assistant_messages <- user_assistant_messages[1:3]
   }
   if (length(user_assistant_messages) == 0) {
      return("New conversation")
   }
   
   generated_name <- .rs.backend_generate_conversation_name(user_assistant_messages)
   
   # Handle NULL response (e.g., from cancellation)
   if (is.null(generated_name)) {
      return("New conversation")
   }
   
   generated_name <- gsub("[\"'`]", "", generated_name)
   generated_name <- trimws(generated_name)
   
   if (!is.null(generated_name) && nchar(generated_name) > 0 && generated_name != "New conversation") {
      .rs.set_conversation_name(conversation_id, generated_name)
   }
   
   return(generated_name)
})

.rs.addFunction("ai.should_prompt_for_name", function() {
   conversation_id <- .rs.get_current_conversation_index()
   result <- .rs.should_prompt_for_name(conversation_id)
   return(result)
})
.rs.addJsonRpcHandler("generate_conversation_name", function(conversation_id) {
   .rs.ai.generate_conversation_name(conversation_id)
})

.rs.addFunction("add_terminal_output_to_ai_conversation", function(code_message_id) {
   if (exists('.rs.tracking_plots', envir = .GlobalEnv) && 
       get('.rs.tracking_plots', envir = .GlobalEnv) && 
       exists('.rs.capture_new_plots', mode = 'function')) {
      new_plots <- .rs.capture_new_plots()
      if (length(new_plots) > 0 && exists('.rs.add_plots_to_conversation', mode = 'function')) {
         .rs.add_plots_to_conversation(new_plots, code_message_id)
      }
      assign('.rs.tracking_plots', FALSE, envir = .GlobalEnv)
   }
   
   if (!exists(".rs.terminal_output", envir = .GlobalEnv)) {
      warning("Terminal output variable '.rs.terminal_output' not found in global environment")
      return(FALSE)
   }
   
   terminal_output <- get(".rs.terminal_output", envir = .GlobalEnv)
   
   terminal_output <- .rs.limit_output_text(terminal_output)
   exit_code <- 0
   if (exists(".rs.terminal_exit_code", envir = .GlobalEnv)) {
      exit_code <- get(".rs.terminal_exit_code", envir = .GlobalEnv)
   }
   
   has_error <- exit_code != 0
   
   if (!has_error && .rs.getVar("ai_in_error")) {
      .rs.setVar("ai_in_error", FALSE)
      .rs.cleanup_error_messages()
   }
   
   output_msg <- paste0("**Output from terminal (Exit code: ", exit_code, "):**\n\n```\n", 
                     paste(terminal_output, collapse = "\n"), 
                     "\n```")
   
   conversation_log <- .rs.read_conversation_log()
   new_message <- list(
      id = .rs.get_next_message_id(),
      role = "user",
      content = output_msg
   )
   
   new_message$related_to <- code_message_id
   
   conversation_log <- c(conversation_log, list(new_message))
   
   .rs.write_conversation_log(conversation_log)
   .rs.update_conversation_display()

   if (has_error) {
      .rs.setVar("ai_in_error", TRUE)
      .rs.enqueClientEvent("update_thinking_message", list(message = "Fixing error..."))
      .rs.setVar("last_thinking_message_time", Sys.time())
      
      buttons <- .rs.read_message_buttons()
      idx <- which(buttons$message_id == code_message_id)
      if (length(idx) > 0) {
         buttons$on_deck_button[idx] <- ""
         .rs.write_message_buttons(buttons)
      }
   }
   
   if (!has_error) {
      .rs.promote_on_deck_button(code_message_id)
   } else {
      buttons <- .rs.read_message_buttons()
      idx <- which(buttons$message_id == code_message_id)
      if (length(idx) > 0) {
         buttons$on_deck_button[idx] <- ""
         .rs.write_message_buttons(buttons)
      }
   }
   
   if (exists(".rs.terminal_output", envir = .GlobalEnv)) {
      rm(".rs.terminal_output", envir = .GlobalEnv) 
   }
   
   if (exists(".rs.terminal_id", envir = .GlobalEnv)) {
      rm(".rs.terminal_id", envir = .GlobalEnv) 
   }
   
   if (exists(".rs.terminal_exit_code", envir = .GlobalEnv)) {
      rm(".rs.terminal_exit_code", envir = .GlobalEnv)
   }
   return(has_error)
})

.rs.addFunction("add_terminal_output_to_conversation", function(message_id) {
   has_error <- .rs.add_terminal_output_to_ai_conversation(message_id)
   
   return(has_error)
})

.rs.addFunction("is_conversation_empty", function(conversation_id) {
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversation_id))
   
   conversation_log_file <- file.path(conversation_dir, "conversation_log.json")
   
   if (!file.exists(conversation_log_file)) {
      return(TRUE)
   }
   
   conversation_log <- jsonlite::fromJSON(readLines(conversation_log_file, warn = FALSE), simplifyVector = FALSE)
   
   if (length(conversation_log) == 0) {
      return(TRUE)
   }
   
   user_assistant_messages <- Filter(function(msg) {
      return(!is.null(msg$role) && (msg$role == "user" || msg$role == "assistant"))
   }, conversation_log)
   
   return(length(user_assistant_messages) == 0)
})

.rs.addJsonRpcHandler("is_conversation_empty", function(conversation_id) {
   return(.rs.is_conversation_empty(as.integer(conversation_id)))
})

.rs.addFunction("get_conversation_log", function(conversation_id) {
   if (!is.numeric(conversation_id)) {
      return(list(
         success = FALSE,
         error = "Conversation ID must be numeric"
      ))
   }
   
   conversation_id <- as.integer(conversation_id)
   
   # Check if conversation exists
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversation_id))
   
   if (!dir.exists(conversation_dir)) {
      return(list(
         success = FALSE,
         error = "Conversation not found"
      ))
   }
   
   # Read conversation log
   conversation_log_file <- file.path(conversation_dir, "conversation_log.json")
   
   if (!file.exists(conversation_log_file)) {
      return(list(
         success = TRUE,
         messages = list()
      ))
   }
   
   tryCatch({
      conversation_log <- jsonlite::fromJSON(conversation_log_file, simplifyVector = FALSE)
      
      # Filter to only user and assistant messages for display
      display_messages <- Filter(function(msg) {
         result <- !is.null(msg$role) && (msg$role == "user" || msg$role == "assistant")
         return(result)
      }, conversation_log)
      
      
      result <- list(
         success = TRUE,
         messages = display_messages
      )
      
      return(result)
   }, error = function(e) {
      return(list(
         success = FALSE,
         error = paste("Failed to read conversation log:", e$message)
      ))
   })
})

.rs.addJsonRpcHandler("get_conversation_log", function(conversation_id) {
   return(.rs.get_conversation_log(conversation_id))
})

.rs.addFunction("cancel_edit_file_command", function(message_id, request_id) {
   # message_id should be the edit_file function call ID directly
   conversation_log <- .rs.read_conversation_log()
   edit_file_message_id <- as.numeric(message_id)
   
   # Find the unique "Response pending..." procedural user message related to this edit_file
   pending_entries <- which(sapply(conversation_log, function(entry) {
      result <- !is.null(entry$role) && entry$role == "user" && 
      !is.null(entry$related_to) && entry$related_to == edit_file_message_id &&
      !is.null(entry$content) && entry$content == "Response pending..." &&
      !is.null(entry$procedural) && entry$procedural == TRUE
      return(result)
   }))
   
   # Must find exactly one pending message
   if (length(pending_entries) != 1) {
      stop("Expected exactly 1 pending user message for edit_file message ID ", edit_file_message_id, ", found ", length(pending_entries))
   }
   
   # Replace the pending message with rejection
   pending_entry_index <- pending_entries[1]
   conversation_log[[pending_entry_index]]$content <- "Edit file command rejected by user"
   # Keep the original message ID - don't assign a new one
   # Keep procedural flag so this remains hidden from UI
   conversation_log[[pending_entry_index]]$procedural <- TRUE
   
   # Find the original user message ID from the edit_file function call's related_to field
   edit_file_entry <- NULL
   for (entry in conversation_log) {
      if (!is.null(entry$id) && entry$id == edit_file_message_id && 
          !is.null(entry$function_call) && !is.null(entry$function_call$name) &&
          entry$function_call$name == "edit_file") {
         edit_file_entry <- entry
         break
      }
   }
   
   if (is.null(edit_file_entry) || is.null(edit_file_entry$related_to)) {
      stop("Could not find edit_file entry or its related_to field for message ID: ", edit_file_message_id)
   }
   
   original_user_message_id <- edit_file_entry$related_to
   
   # Check if there are messages after this function call in the conversation
   # If so, don't trigger API continuation - just update the output
   function_call_message_id <- edit_file_message_id
   has_newer_messages <- any(sapply(conversation_log, function(entry) {
      if (is.null(entry$id) || entry$id <= function_call_message_id) {
         return(FALSE)
      }
      
      # For edit_file, exclude ALL messages related to this specific edit_file command:
      # - function_call_output (type = "function_call_output", related_to = function_call_id)
      # - assistant message (role = "assistant", related_to = function_call_id) 
      # - procedural user message (role = "user", procedural = true, related_to = function_call_id)
      if (!is.null(entry$related_to) && entry$related_to == function_call_message_id) {
         return(FALSE)
      }
      
      return(TRUE)
   }))
   
   .rs.write_conversation_log(conversation_log)
   .rs.update_conversation_display()
   
   # Return different status based on whether conversation has moved on
   # For continuation, we need to return the original user message ID, not the function call ID
   if (has_newer_messages) {
      result <- .rs.create_ai_operation_result(
         status = "done",
         data = list(
            message = "Edit file command cancelled - conversation has moved on, not continuing API",
            related_to_id = original_user_message_id,
            conversation_index = .rs.get_current_conversation_index(),
            request_id = request_id
         )
      )
   } else {
      result <- .rs.create_ai_operation_result(
         status = "continue_silent",
         data = list(
            message = "Edit file command cancelled - returning control to orchestrator",
            related_to_id = original_user_message_id,
            conversation_index = .rs.get_current_conversation_index(),
            request_id = request_id
         )
      )
   }
   
   return(result)
})

.rs.addJsonRpcHandler("cancel_edit_file_command", function(message_id, request_id) {
   result <- .rs.cancel_edit_file_command(message_id, request_id)
   
   return(result)
})