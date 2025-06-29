# SessionAiIO.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

.rs.addFunction("get_ai_base_dir", function() {
   # Helper function to get the platform-appropriate base directory for AI data
   # Following industry standards and platform conventions:
   # - Linux/Unix: ~/.local/share/rstudio-ai/
   # - macOS: ~/Library/Application Support/rstudio-ai/
   # - Windows: %LOCALAPPDATA%/rstudio-ai/
   
   if (exists("R_user_dir", envir = asNamespace("tools"), mode = "function")) {
      # Use R's standard cross-platform user directory function (R >= 4.0.0)
      tools::R_user_dir("rstudio-ai", which = "data")
   } else {
      # Fallback for older R versions or test environments
      # Manually implement platform-specific directory logic following XDG/OS conventions
      sysname <- Sys.info()[["sysname"]]
      home_dir <- Sys.getenv("HOME", unset = path.expand("~"))
      
      if (identical(sysname, "Windows")) {
         # Windows: use LOCALAPPDATA for user data
         appdata <- Sys.getenv("LOCALAPPDATA", unset = Sys.getenv("APPDATA", unset = file.path(home_dir, "AppData", "Local")))
         file.path(appdata, "rstudio-ai")
      } else if (identical(sysname, "Darwin")) {
         # macOS: use ~/Library/Application Support/
         file.path(home_dir, "Library", "Application Support", "rstudio-ai")
      } else {
         # Linux/Unix: follow XDG Base Directory Specification
         xdg_data_home <- Sys.getenv("XDG_DATA_HOME", unset = file.path(home_dir, ".local", "share"))
         file.path(xdg_data_home, "rstudio-ai")
      }
   }
})

.rs.addFunction("get_ai_file_paths", function() {
   # Use mocked get_current_conversation_index if available, otherwise default to 1 for tests
   conversation_index <- if (exists(".rs.get_current_conversation_index", mode = "function")) {
      .rs.get_current_conversation_index()
   } else {
      1L
   }
   
   # Use platform-appropriate data directory following industry standards
   base_ai_dir <- .rs.get_ai_base_dir()
   
   # All conversations go in a conversations subfolder
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversation_index))
   
   if (!dir.exists(conversation_dir)) {
      dir.create(conversation_dir, recursive = TRUE, showWarnings = FALSE)
   }
   
   list(
      ai_dir = conversation_dir,
      conversation_log_path = file.path(conversation_dir, "conversation_log.json"),
      script_history_path = file.path(conversation_dir, "script_history.tsv"),
      diff_log_path = file.path(conversation_dir, "file_changes.json"),
      conversation_diff_log_path = file.path(conversation_dir, "conversation_diffs.json"),
      buttons_csv_path = file.path(conversation_dir, "message_buttons.csv"),
      attachments_csv_path = file.path(conversation_dir, "attachments.csv")
   )
})

# Removed read_conversation and write_conversation functions - now using conversation_log.json exclusively

.rs.addFunction("read_conversation_log", function() {
   paths <- .rs.get_ai_file_paths()
   
   if (!file.exists(paths$conversation_log_path)) {
      initial_log <- list()
      writeLines(jsonlite::toJSON(initial_log, auto_unbox = TRUE), paths$conversation_log_path)
   }
   
   tryCatch({
      conversation_log <- jsonlite::fromJSON(paths$conversation_log_path, simplifyVector = FALSE)
   }, error = function(e) {
      cat("ERROR: SessionAiIO.R line 120 jsonlite::fromJSON failed:", e$message, "\n")
      stop(e)
   })
   

   for (i in seq_along(conversation_log)) {
      entry <- conversation_log[[i]]
      related_to_info <- if(is.null(entry$related_to)) {
         "NULL"
      } else if(is.list(entry$related_to)) {
         paste0("list(length:", length(entry$related_to), ")")
      } else {
         paste0(class(entry$related_to), "(", entry$related_to, ")")
      }

   }
   
   return(conversation_log)
})

# Removed write_conversation function - now using conversation_log.json exclusively

.rs.addFunction("write_conversation_log", function(conversation_log) {
   paths <- .rs.get_ai_file_paths()
   
   # Only add IDs if the function exists and IDs are actually missing
   if (exists(".rs.get_next_message_id", mode = "function")) {
      for (i in seq_along(conversation_log)) {
         if (is.null(conversation_log[[i]]$id)) {
            conversation_log[[i]]$id <- .rs.get_next_message_id()
         }
      }
   } else {
      # In test environment, just use sequential IDs for missing ones
      next_id <- 1
      for (i in seq_along(conversation_log)) {
         if (is.null(conversation_log[[i]]$id)) {
            conversation_log[[i]]$id <- next_id
            next_id <- next_id + 1
         }
      }
   }
   
   tryCatch({
      json_string <- jsonlite::toJSON(conversation_log, auto_unbox = TRUE)
   }, error = function(e) {
      cat("ERROR: jsonlite::toJSON failed:", e$message, "\n")
      stop(e)
   })
   
   tryCatch({
      test_parsed <- jsonlite::fromJSON(json_string, simplifyVector = FALSE)
   }, error = function(e) {
      cat("ERROR: SessionAiIO.R line 201 jsonlite::fromJSON failed:", e$message, "\n")
      stop(e)
   })
   if (length(test_parsed) > 0 && !is.null(test_parsed[[length(test_parsed)]]$related_to)) {
      if (!is.numeric(test_parsed[[length(test_parsed)]]$related_to)) {
         cat("WARNING: JSON corruption detected - related_to became:", class(test_parsed[[length(test_parsed)]]$related_to), "\n")
      }
   }
   
   writeLines(json_string, paths$conversation_log_path)
   return(TRUE)
})

.rs.addFunction("get_script_history", function() {
   paths <- .rs.get_ai_file_paths()
   
   if (file.exists(paths$script_history_path)) {
      history <- read.table(paths$script_history_path, header = TRUE, sep = "\t", stringsAsFactors = FALSE)
   } else {
      history <- data.frame(filename = character(), order = integer(), conversation_index = integer(), stringsAsFactors = FALSE)
   }
   
   if (!"conversation_index" %in% names(history)) {
      current_index <- if (exists(".rs.get_current_conversation_index", mode = "function")) {
         .rs.get_current_conversation_index()
      } else {
         1  # Default for test environment
      }
      history$conversation_index <- rep(current_index, nrow(history))
   }
   
   return(history)
})

.rs.addFunction("save_script_to_history", function(filename) {
   history <- .rs.get_script_history()
   paths <- .rs.get_ai_file_paths()
   
   conversation_index <- if (exists(".rs.get_current_conversation_index", mode = "function")) {
      .rs.get_current_conversation_index()
   } else {
      1  # Default for test environment
   }
   
   existing_index <- which(history$filename == filename)
   
   if (length(existing_index) > 0) {
      history <- history[-existing_index, , drop = FALSE]
   }
   
   next_order <- if(nrow(history) > 0) max(history$order) + 1 else 1
   new_row <- data.frame(
      filename = filename, 
      order = next_order, 
      conversation_index = conversation_index,
      stringsAsFactors = FALSE
   )
   
   history <- rbind(history, new_row)
   
   write.table(history, paths$script_history_path, sep = "\t", row.names = FALSE, quote = FALSE)
   
   return(history)
})

.rs.addFunction("clear_script_history_after_revert", function(revert_message_index, conversation_index) {
         history <- .rs.get_script_history()
   paths <- .rs.get_ai_file_paths()
   
   if (nrow(history) == 0) {
      return(history)
   }
   
   changes_log <- .rs.read_file_changes_log()
   
   file_creation_ids <- list()
   if (length(changes_log$changes) > 0) {
      for (change in changes_log$changes) {
         if (change$action == "create" && change$conversation_index == conversation_index) {
            filename <- change$file_path
            message_id <- as.numeric(change$conversation_id)
            
            if (is.null(file_creation_ids[[filename]]) || message_id < file_creation_ids[[filename]]) {
               file_creation_ids[[filename]] <- message_id
            }
         }
      }
   }
   
   files_to_remove <- c()
   for (filename in names(file_creation_ids)) {
      if (file_creation_ids[[filename]] >= revert_message_index) {
         files_to_remove <- c(files_to_remove, filename)
      }
   }
   
   if (length(files_to_remove) > 0) {
      history <- history[!history$filename %in% files_to_remove, , drop = FALSE]
   }
   
   if (nrow(history) > 0) {
      pre_revert_orders <- c()
      for (i in 1:nrow(history)) {
         filename <- history$filename[i]
         file_id <- file_creation_ids[[filename]]
         
         if (is.null(file_id) || file_id < revert_message_index) {
            pre_revert_orders <- c(pre_revert_orders, history$order[i])
         }
      }
      
      if (length(pre_revert_orders) > 0) {
         history <- history[order(history$order), , drop = FALSE]
         
         history$order <- 1:nrow(history)
         
         write.table(history, paths$script_history_path, sep = "\t", row.names = FALSE, quote = FALSE)
      }
   }
   
   return(history)
})

.rs.addFunction("read_file_changes_log", function() {
   paths <- .rs.get_ai_file_paths()
   
   if (!file.exists(paths$diff_log_path)) {
         initial_log <- list(
      changes = list()
   )
   writeLines(jsonlite::toJSON(initial_log, auto_unbox = TRUE, pretty = TRUE), paths$diff_log_path)
   }
   
   tryCatch({
      jsonlite::fromJSON(paths$diff_log_path, simplifyVector = FALSE)
   }, error = function(e) {
      list(changes = list())
   })
})

.rs.addFunction("read_conversation_diff_log", function() {
   paths <- .rs.get_ai_file_paths()
   
   if (!file.exists(paths$conversation_diff_log_path)) {
      initial_log <- list(
         diffs_by_msg_id = list()
      )
      writeLines(jsonlite::toJSON(initial_log, auto_unbox = TRUE, pretty = TRUE), paths$conversation_diff_log_path)
   }
   
   diff_log <- jsonlite::fromJSON(paths$conversation_diff_log_path, simplifyVector = FALSE)
   
   # Ensure diffs_by_msg_id exists
   if (is.null(diff_log$diffs_by_msg_id)) {
      diff_log$diffs_by_msg_id <- list()
   }
   
   return(diff_log)
})

.rs.addFunction("write_file_changes_log", function(changes_log) {
   paths <- .rs.get_ai_file_paths()
   
   json_content <- jsonlite::toJSON(changes_log, auto_unbox = TRUE, pretty = TRUE)   
   tryCatch({
      writeLines(json_content, paths$diff_log_path)
      return(TRUE)
   }, error = function(e) {
      cat("DEBUG write_file_changes_log: error writing file:", e$message, "\n")
      return(FALSE)
   })
})

.rs.addFunction("write_conversation_diff_log", function(diff_log) {
   paths <- .rs.get_ai_file_paths()
   
   writeLines(jsonlite::toJSON(diff_log, auto_unbox = TRUE, pretty = TRUE), paths$conversation_diff_log_path)
   return(TRUE)
})

.rs.addFunction("record_file_creation", function(file_path) {
   if (!file.exists(file_path)) {
      return(FALSE)
   }
   
   changes_log <- .rs.read_file_changes_log()
   
   msg_id <- if (exists(".rs.getVar", mode = "function")) {
      .rs.getVar("message_id_counter")
   } else {
      1  # Default for test environment
   }
   
   conversation_index <- if (exists(".rs.get_current_conversation_index", mode = "function")) {
      .rs.get_current_conversation_index()
   } else {
      1  # Default for test environment
   }
   
   file_content <- paste(readLines(file_path, warn = FALSE), collapse = "\n")
   
   # File creations are always considered saved operations
   was_unsaved <- FALSE
   
   new_change <- list(
      id = length(changes_log$changes) + 1,
      conversation_id = msg_id,
      conversation_index = conversation_index,
      timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
      action = "create",
      file_path = file_path,
      content = file_content,
      was_unsaved = was_unsaved
   )
   
   changes_log$changes <- c(changes_log$changes, list(new_change))
   
   .rs.write_file_changes_log(changes_log)
   
   return(TRUE)
})

.rs.addFunction("safe_text_compare", function(text, pattern, mode) {
  if (is.null(text) || is.null(pattern) || nchar(text) == 0 || nchar(pattern) == 0) {
    return(FALSE)
  }
  
     clean_text <- function(input) {
    input <- trimws(input)
    
    if (startsWith(input, "```") || startsWith(input, "````")) {
      first_newline <- regexpr("\n", input)
      if (first_newline > 0) {
        input <- substr(input, first_newline + 1, nchar(input))
      }
      
      if (endsWith(input, "```") || endsWith(input, "````")) {
        last_lines <- tail(strsplit(input, "\n")[[1]], 2)
        if (length(last_lines) > 1 && (last_lines[2] == "```" || last_lines[2] == "````")) {
          input <- substr(input, 1, nchar(input) - nchar(last_lines[2]) - 1)
        }
      }
    }
    
    return(input)
  }
  
  cleaned_text <- clean_text(text)
  cleaned_pattern <- clean_text(pattern)
  
  if (mode == "startsWith") {
    pattern_length <- nchar(cleaned_pattern)
    if (nchar(cleaned_text) < pattern_length) return(FALSE)
    
    text_start <- substr(cleaned_text, 1, pattern_length)
    return(text_start == cleaned_pattern)
  } 
  else if (mode == "endsWith") {
    pattern_length <- nchar(cleaned_pattern)
    if (nchar(cleaned_text) < pattern_length) return(FALSE)
    
    text_end <- substr(cleaned_text, nchar(cleaned_text) - pattern_length + 1, nchar(cleaned_text))
    return(text_end == cleaned_pattern)
  }
  else if (mode == "contains") {
    return(grepl(cleaned_pattern, cleaned_text, fixed=TRUE))
  }
  
  return(FALSE)
})

.rs.addFunction("detect_code_replacement", function(previous_content, new_content) {
   previous_lines <- trimws(strsplit(previous_content, "\n")[[1]])
   new_lines <- trimws(strsplit(new_content, "\n")[[1]])
   
   if (length(previous_lines) == 0 || length(new_lines) == 0) {
      return(list(is_replacement = FALSE))
   }
   
   matching_lines <- 0
   match_indexes <- integer(0)
   
   for (i in seq_along(new_lines)) {
      if (nchar(new_lines[i]) == 0) {
         next
      }
      
      matches <- which(previous_lines == new_lines[i])
      
      if (length(matches) > 0) {
         matching_lines <- matching_lines + 1
         match_indexes <- c(match_indexes, matches)
      }
   }
   
   non_empty_new_lines <- sum(nchar(new_lines) > 0)
   match_percentage <- if (non_empty_new_lines > 0) matching_lines / non_empty_new_lines else 0
   
   if (match_percentage > 0.5 && matching_lines >= 3) {
      first_match <- min(match_indexes)
      last_match <- max(match_indexes)
      
      return(list(
         is_replacement = TRUE,
         first_match_line = first_match,
         last_match_line = last_match,
         match_percentage = match_percentage
      ))
   }
   
   return(list(is_replacement = FALSE))
})

.rs.addFunction("record_file_modification_with_diff_with_state", function(file_path, previous_content, new_content, was_originally_unsaved) {
   if (!file.exists(file_path)) {
      return(FALSE)
   }
   
   changes_log <- .rs.read_file_changes_log()
   
   msg_id <- if (exists(".rs.getVar", mode = "function")) {
      .rs.getVar("message_id_counter")
   }
   
   conversation_index <- if (exists(".rs.get_current_conversation_index", mode = "function")) {
      .rs.get_current_conversation_index()
   }
   
   file_type <- tools::file_ext(file_path)
   processed_content <- if (exists(".rs.apply_unified_diff_processing", mode = "function")) {
      .rs.apply_unified_diff_processing(previous_content, new_content, file_type)
   } else {
      list(previous_content = previous_content, content = new_content)  # Default for test environment
   }
   previous_content <- processed_content$previous_content
   new_content <- processed_content$content
   
   # Use the passed-in original state rather than trying to detect it after modification
   was_unsaved <- was_originally_unsaved
   is_start_addition <- FALSE
   is_end_addition <- FALSE
   diff_type <- "modify"
   
   conversation_log <- .rs.read_conversation_log()
   prompt_based_addition <- FALSE
   
   # Look for the most recent edit_file function call with "start" or "end" keyword directly
   for (i in length(conversation_log):1) {
      if (!is.null(conversation_log[[i]]$function_call) && 
          !is.null(conversation_log[[i]]$function_call$name) && 
          conversation_log[[i]]$function_call$name == "edit_file" &&
          !is.null(conversation_log[[i]]$function_call$arguments)) {
         
         args <- tryCatch({
            if (is.character(conversation_log[[i]]$function_call$arguments)) {
               jsonlite::fromJSON(conversation_log[[i]]$function_call$arguments, simplifyVector = FALSE)
            } else {
               conversation_log[[i]]$function_call$arguments
            }
         }, error = function(e) {
            return(NULL)
         })
         
         if (!is.null(args) && !is.null(args$keyword)) {
            # Check if this edit_file call is for the same file
            target_file <- if (!is.null(args$filename)) args$filename else ""
            if (target_file != "" && basename(target_file) == basename(file_path)) {
               
               if (args$keyword == "start") {
                  is_start_addition <- TRUE
                  is_end_addition <- FALSE
                  diff_type <- "prepend"
                  prompt_based_addition <- TRUE
                  break
               } else if (args$keyword == "end") {
                  is_end_addition <- TRUE
                  is_start_addition <- FALSE
                  diff_type <- "append"
                  prompt_based_addition <- TRUE
                  break
               }
            }
         }
      }
   }
   
   if (!prompt_based_addition) {
      replacement_info <- .rs.detect_code_replacement(previous_content, new_content)
      
      if (replacement_info$is_replacement) {
         diff_type <- "replace"
      }
   }
   
   new_change <- list(
      id = length(changes_log$changes) + 1,
      conversation_id = msg_id,
      conversation_index = conversation_index,
      timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
      action = "modify",
      file_path = file_path,
      content = new_content,
      previous_content = previous_content,
      diff_type = diff_type,
      was_unsaved = was_unsaved
   )

   changes_log$changes <- c(changes_log$changes, list(new_change))
   
   .rs.write_file_changes_log(changes_log)
   
   return(TRUE)
})

.rs.addFunction("record_file_modification_with_diff", function(file_path, previous_content, new_content) {
   if (!file.exists(file_path)) {
      return(FALSE)
   }
   
   changes_log <- .rs.read_file_changes_log()
   
   msg_id <- if (exists(".rs.getVar", mode = "function")) {
      .rs.getVar("message_id_counter")
   }
   
   conversation_index <- if (exists(".rs.get_current_conversation_index", mode = "function")) {
      .rs.get_current_conversation_index()
   }
   
   file_type <- tools::file_ext(file_path)
   processed_content <- if (exists(".rs.apply_unified_diff_processing", mode = "function")) {
      .rs.apply_unified_diff_processing(previous_content, new_content, file_type)
   } else {
      list(previous_content = previous_content, content = new_content)  # Default for test environment
   }
   previous_content <- processed_content$previous_content
   new_content <- processed_content$content
   
   # Determine if the original file was saved or unsaved
   was_unsaved <- FALSE
   if (exists(".rs.is_file_open_in_editor", mode = "function") && .rs.is_file_open_in_editor(file_path)) {
      # File is open in editor, check if it had unsaved changes before this edit
      doc_info <- .rs.get_open_document_by_path(file_path)
      if (!is.null(doc_info) && !is.null(doc_info$dirty)) {
         was_unsaved <- as.logical(doc_info$dirty)
      }
   }
   
   
   is_start_addition <- FALSE
   is_end_addition <- FALSE
   diff_type <- "modify"
   
   conversation_log <- .rs.read_conversation_log()
   prompt_based_addition <- FALSE
   
   # Look for the most recent edit_file function call with "start" or "end" keyword directly
   for (i in length(conversation_log):1) {
      if (!is.null(conversation_log[[i]]$function_call) && 
          !is.null(conversation_log[[i]]$function_call$name) && 
          conversation_log[[i]]$function_call$name == "edit_file" &&
          !is.null(conversation_log[[i]]$function_call$arguments)) {
         
         args <- tryCatch({
            if (is.character(conversation_log[[i]]$function_call$arguments)) {
               jsonlite::fromJSON(conversation_log[[i]]$function_call$arguments, simplifyVector = FALSE)
            } else {
               conversation_log[[i]]$function_call$arguments
            }
         }, error = function(e) {
            return(NULL)
         })
         
         if (!is.null(args) && !is.null(args$keyword)) {
            # Check if this edit_file call is for the same file
            target_file <- if (!is.null(args$filename)) args$filename else ""
            if (target_file != "" && basename(target_file) == basename(file_path)) {
               
               if (args$keyword == "start") {
                  is_start_addition <- TRUE
                  is_end_addition <- FALSE
                  diff_type <- "prepend"
                  prompt_based_addition <- TRUE
                  break
               } else if (args$keyword == "end") {
                  is_end_addition <- TRUE
                  is_start_addition <- FALSE
                  diff_type <- "append"
                  prompt_based_addition <- TRUE
                  break
               }
            }
         }
      }
   }
   
   if (!prompt_based_addition) {
      replacement_info <- .rs.detect_code_replacement(previous_content, new_content)
      
      if (replacement_info$is_replacement) {
         diff_type <- "replace"
      }
   }
   
   new_change <- list(
      id = length(changes_log$changes) + 1,
      conversation_id = msg_id,
      conversation_index = conversation_index,
      timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
      action = "modify",
      file_path = file_path,
      content = new_content,
      previous_content = previous_content,
      diff_type = diff_type,
      was_unsaved = was_unsaved
   )

   changes_log$changes <- c(changes_log$changes, list(new_change))
   
   .rs.write_file_changes_log(changes_log)
   
   return(TRUE)
})

.rs.addFunction("record_file_deletion", function(file_path, original_content) {
   changes_log <- .rs.read_file_changes_log()
   
   msg_id <- if (exists(".rs.getVar", mode = "function")) {
      .rs.getVar("message_id_counter")
   }
   
   conversation_index <- if (exists(".rs.get_current_conversation_index", mode = "function")) {
      .rs.get_current_conversation_index()
   }
   
   new_change <- list(
      id = length(changes_log$changes) + 1,
      conversation_id = msg_id,
      conversation_index = conversation_index,
      timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
      action = "remove",
      file_path = file_path,
      content = "",
      previous_content = original_content,
      diff_type = "delete",
      was_unsaved = FALSE
   )

   changes_log$changes <- c(changes_log$changes, list(new_change))
   .rs.write_file_changes_log(changes_log)
   
   return(TRUE)
})

.rs.addJsonRpcHandler("delete_folder", function(path) {
    .rs.delete_folder(path)
    return(TRUE)
})

.rs.addFunction("delete_folder", function(path) {
   # Use the same base directory logic as get_ai_file_paths
   base_ai_dir <- .rs.get_ai_base_dir()
   
   # Check if this is a conversation folder (starts with "conversation_")
   if (startsWith(path, "conversation_")) {
      # Conversation folders are now in the conversations subdirectory
      conversations_dir <- file.path(base_ai_dir, "conversations")
      full_path <- file.path(conversations_dir, path)
   } else {
      # Other files remain directly in base_ai_dir
      full_path <- file.path(base_ai_dir, path)
   }
   
   if (!dir.exists(full_path)) {
      return(FALSE)
   }
   
   csv_path <- file.path(full_path, "attachments.csv")
   
   # Clean up attachments before deleting folder
   if (file.exists(csv_path)) {
      tryCatch({
         attachments <- read.csv(csv_path, stringsAsFactors = FALSE)
         
         if (!is.null(attachments) && nrow(attachments) > 0) {
            config <- .rs.get_backend_config()
            active_provider <- .rs.get_active_provider()
            
            # Delete files based on provider
            if (active_provider == "anthropic") {
               # Delete Anthropic files (requires API key for authentication)
               api_key <- .rs.get_api_key("rao")
               if (!is.null(api_key)) {
                  for (i in 1:nrow(attachments)) {
                     file_id <- attachments$file_id[i]
                     if (!is.null(file_id) && file_id != "") {
                        tryCatch({
                           deleteResponse <- httr2::resp_body_json(
                               httr2::req_perform(
                                   httr2::req_method(
                                       httr2::req_url_query(
                                           httr2::request(paste0(config$url, "/attachments/anthropic-files/", file_id)),
                                           api_key = api_key
                                       ),
                                       "DELETE"
                                   )
                               )
                           )
                           
                           if (!deleteResponse$success) {
                              cat("Failed to delete Anthropic file", file_id, ":", deleteResponse$message, "\n")
                           }
                        }, error = function(e) {
                           # Ignore errors when deleting files
                        })
                     }
                  }
               }
            } else {
               # Delete OpenAI files (requires API key for backend authentication)
               api_key <- .rs.get_api_key("rao")
               if (!is.null(api_key)) {
                  for (i in 1:nrow(attachments)) {
                     file_id <- attachments$file_id[i]
                     if (!is.null(file_id) && file_id != "") {
                        tryCatch({
                           deleteResponse <- httr2::resp_body_json(
                               httr2::req_perform(
                                   httr2::req_method(
                                       httr2::req_url_query(
                                           httr2::request(paste0(config$url, "/attachments/openai-files/", file_id)),
                                           api_key = api_key
                                       ),
                                       "DELETE"
                                   )
                               )
                           )
                           
                           if (!deleteResponse$success) {
                           }
                        }, error = function(e) {
                           # Ignore errors when deleting files
                        })
                     }
                  }
                  
                  # Delete vector store for OpenAI (Anthropic doesn't use vector stores)
                  vector_store_id <- attachments$vector_store_id[1]
                  if (!is.null(vector_store_id) && vector_store_id != "") {
                     tryCatch({
                        deleteVsResponse <- httr2::resp_body_json(
                            httr2::req_perform(
                                httr2::req_method(
                                    httr2::req_url_query(
                                        httr2::request(paste0(config$url, "/attachments/vector-stores/", vector_store_id)),
                                        api_key = api_key
                                    ),
                                    "DELETE"
                                )
                            )
                        )
                        
                        if (!deleteVsResponse$success) {
                           cat("Failed to delete vector store", vector_store_id, ":", deleteVsResponse$error, "\n")
                        }
                     }, error = function(e) {
                        # Ignore errors when deleting vector store
                     })
                  }
               }
            }
         }
      }, error = function(e) {
         # Log error but continue with folder deletion
         cat("Warning: Error cleaning up attachments:", e$message, "\n")
      })
   }
   
   unlink(full_path, recursive = TRUE)
   
   return(TRUE)
})
