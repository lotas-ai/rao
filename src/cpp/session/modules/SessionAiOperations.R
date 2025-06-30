# SessionAiOperations.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

.rs.addFunction("update_diff_with_editor_content", function(edited_code, edit_file_message_id, diff_data) {
   # Update diff data with current editor content and save it
   # Returns the updated diff_data
   
   edited_lines <- strsplit(edited_code, "\n")[[1]]
   diff_lines_count <- length(diff_data$diff)
   edited_lines_count <- length(edited_lines)
   
   # Check if line counts match (with tolerance for leading/trailing empty lines)
   lines_match <- diff_lines_count == edited_lines_count
   
   if (!lines_match) {
      # Handle simple cases: leading/trailing empty lines
      if (abs(diff_lines_count - edited_lines_count) <= 2) {
         # Try to handle minor differences by padding or trimming
         if (diff_lines_count > edited_lines_count) {
            # Add empty lines to edited_lines to match
            while (length(edited_lines) < diff_lines_count) {
               edited_lines <- c(edited_lines, "")
            }
         } else {
            # Trim edited_lines to match (remove trailing empty lines)
            while (length(edited_lines) > diff_lines_count && 
                   edited_lines[length(edited_lines)] == "") {
               edited_lines <- edited_lines[-length(edited_lines)]
            }
         }
         lines_match <- length(edited_lines) == diff_lines_count
      }
      
      if (!lines_match) {
         stop("Line count mismatch in edit_file: diff has ", diff_lines_count, 
              " lines but edited_code has ", edited_lines_count, 
              " lines. Advanced line handling not yet implemented.")
      }
   }
   
   # Update each diff entry's content with the corresponding line from edited_code
   for (i in seq_along(diff_data$diff)) {
      diff_data$diff[[i]]$content <- edited_lines[i]
   }
   
   # Save the updated diff data back to conversation_diffs.json
   diffs_data <- .rs.read_conversation_diffs()
   msg_id_char <- as.character(edit_file_message_id)
   if (!is.null(diffs_data$diffs[[msg_id_char]])) {
      # Update the existing diff entry with modified content - save just the diff array
      diffs_data$diffs[[msg_id_char]]$diff_data <- diff_data$diff
      .rs.write_conversation_diffs(diffs_data)
   }
   
   return(diff_data)
})

.rs.addFunction("generate_function_call_message", function(function_name, arguments, is_thinking = FALSE) {
   # Generate user-friendly message for function calls
   # is_thinking: TRUE for "Searching..." style, FALSE for "Searched..." style
   
   suffix <- if (is_thinking) "..." else ""
   
   message <- switch(function_name,
      "find_keyword_context" = {
         keyword <- if (!is.null(arguments$keyword)) arguments$keyword else "unknown"
         if (is_thinking) {
            paste0("Searching repository for '", keyword, "'", suffix)
         } else {
            paste0("Searched repository for '", keyword, "'")
         }
      },
      "grep_search" = {
         pattern <- if (!is.null(arguments$query)) arguments$query else "unknown"
         display_pattern <- if (nchar(pattern) > 25) paste0(substr(pattern, 1, 25), "...") else pattern
         
         # Build patterns info
         patterns_info <- ""
         if (!is.null(arguments$include_pattern) && arguments$include_pattern != "" ||
             !is.null(arguments$exclude_pattern) && arguments$exclude_pattern != "") {
            
            parts <- c()
            if (!is.null(arguments$include_pattern) && arguments$include_pattern != "") {
               parts <- c(parts, paste0("include: ", arguments$include_pattern))
            }
            if (!is.null(arguments$exclude_pattern) && arguments$exclude_pattern != "") {
               parts <- c(parts, paste0("exclude: ", arguments$exclude_pattern))
            }
            patterns_info <- paste0(" (", paste(parts, collapse = ", "), ")")
         }
         
         if (is_thinking) {
            paste0("Searching pattern '", display_pattern, "'", patterns_info, suffix)
         } else {
            paste0("Searched pattern '", display_pattern, "'", patterns_info)
         }
      },
      "read_file" = {
         target_file <- if (!is.null(arguments$filename)) basename(arguments$filename) else "unknown"
         if (is_thinking) {
            paste0("Reading ", target_file, suffix)
         } else {
            paste0("Read ", target_file)
         }
      },
      "view_image" = {
         image_path <- if (!is.null(arguments$image_path)) basename(arguments$image_path) else "unknown"
         if (is_thinking) {
            paste0("Viewing image: ", image_path, suffix)
         } else {
            paste0("Viewed image: ", image_path)
         }
      },
      "search_for_file" = {
         query <- if (!is.null(arguments$query)) arguments$query else "unknown"
         if (is_thinking) {
            paste0("Searching for files matching '", query, "'", suffix)
         } else {
            paste0("Searched for files matching '", query, "'")
         }
      },
      "list_dir" = {
         dir_path <- if (!is.null(arguments$relative_workspace_path)) arguments$relative_workspace_path else "unknown"
         display_path <- if (dir_path == ".") "the current directory" else dir_path
         if (is_thinking) {
            paste0("Listing contents of ", display_path, suffix)
         } else {
            paste0("Listed contents of ", display_path)
         }
      },
      # Default fallback
      if (is_thinking) {
         paste0("Calling function: ", function_name, suffix)
      } else {
         paste0("Called function: ", function_name)
      }
   )
   
   return(message)
})

.rs.addFunction("find_function_call_by_call_id", function(conversation_log, call_id) {
   # Find a function call entry in conversation log by call_id
   for (entry in conversation_log) {
      if (!is.null(entry$function_call)) {
         entry_call_id <- if (is.list(entry$function_call$call_id)) {
            entry$function_call$call_id[[1]]
         } else {
            entry$function_call$call_id
         }
         if (entry_call_id == call_id) {
            return(entry)
         }
      }
   }
   return(NULL)
})

.rs.addFunction("find_function_call_by_message_id", function(conversation_log, message_id) {
   # Find a function call entry in conversation log by message ID
   for (entry in conversation_log) {
      if (!is.null(entry$function_call) && !is.null(entry$id) && entry$id == message_id) {
         return(entry)
      }
   }
   return(NULL)
})

.rs.addFunction("handle_cancel_edit", function(normalized_function_call, conversation_log, related_to_id, request_id) {
   # Handle cancel_edit function call
   # 1. Find corresponding edit_file function call using related_to_id
   # 2. Update existing function_call_output for edit_file with cancel_edit explanation
   # 3. Create assistant message only if one doesn't already exist
   # 4. Return continue_silent status
   
   # Parse cancel_edit arguments to get explanation
   cancel_edit_args <- tryCatch({
      if (is.character(normalized_function_call$arguments)) {
         jsonlite::fromJSON(normalized_function_call$arguments, simplifyVector = FALSE)
      } else {
         normalized_function_call$arguments
      }
   }, error = function(e) {
      cat("DEBUG handle_cancel_edit: Error parsing arguments:", e$message, "\n")
      return(list(explanation = "Edit cancelled by model"))
   })
      
   explanation <- if (!is.null(cancel_edit_args$explanation)) {
      cancel_edit_args$explanation
   } else {
      "Edit cancelled by model"
   }
      
   # Use related_to_id to find the edit_file function call
   edit_file_entry <- NULL
   edit_file_call_id <- NULL
      
      # related_to_id is required and should never be null
   if (is.null(related_to_id)) {
      stop("related_to_id is required and cannot be NULL in handle_cancel_edit")
   }
   
   for (entry in conversation_log) {
      if (!is.null(entry$id) && entry$id == related_to_id &&
          !is.null(entry$function_call) && !is.null(entry$function_call$name) &&
          entry$function_call$name == "edit_file") {
         edit_file_entry <- entry
         edit_file_call_id <- entry$function_call$call_id
         break
      }
   }
      
   if (is.null(edit_file_entry)) {
      # No edit_file found - this shouldn't happen, but handle gracefully
      stop("cancel_edit received but no corresponding edit_file found for related_to_id:", related_to_id)
   }
   
   # Read fresh conversation log that includes the cancel_edit function call FIRST
   conversation_log <- .rs.read_conversation_log()
   
   # Debug: check what's in the fresh conversation log
   for (i in seq_along(conversation_log)) {
      entry <- conversation_log[[i]]
      content_preview <- if (!is.null(entry$content)) substr(entry$content, 1, 30) else "NO_CONTENT"
      function_name <- if (!is.null(entry$function_call) && !is.null(entry$function_call$name)) entry$function_call$name else "NO_FUNC"
   }
   
   # Find and update existing function_call_output for the edit_file
   existing_output_index <- NULL
   
   for (i in seq_along(conversation_log)) {
      entry <- conversation_log[[i]]
      if (!is.null(entry$type) && entry$type == "function_call_output" && 
          !is.null(entry$call_id) && entry$call_id == edit_file_call_id) {
         existing_output_index <- i
         break
      }
   }
   
   if (!is.null(existing_output_index)) {
      # Update existing function_call_output
      conversation_log[[existing_output_index]]$output <- explanation
   } else {
      # Create new function_call_output if none exists
      function_output_id <- .rs.get_next_message_id()
      edit_file_output <- list(
         id = function_output_id,
         type = "function_call_output",
         call_id = edit_file_call_id,
         output = explanation,
         related_to = edit_file_entry$id,
         procedural = TRUE
      )
      conversation_log <- c(conversation_log, list(edit_file_output))
   }
   
   # Check if assistant message already exists for this edit_file
   existing_assistant_message <- NULL
   for (i in seq_along(conversation_log)) {
      entry <- conversation_log[[i]]
      if (!is.null(entry$role) && entry$role == "assistant" && 
          !is.null(entry$related_to) && entry$related_to == edit_file_entry$id) {
         existing_assistant_message <- entry
         content_preview <- if (!is.null(entry$content)) substr(entry$content, 1, 50) else "NO_CONTENT"
         break
      }
   }
   
   # FIRST: Transform the cancel_edit function call into a plain assistant message
   cancel_edit_call_id <- normalized_function_call$call_id
   
   # Find the cancel_edit function call and transform it
   for (i in seq_along(conversation_log)) {
      if (!is.null(conversation_log[[i]]$function_call) && 
          !is.null(conversation_log[[i]]$function_call$call_id)) {
         entry_call_id <- if (is.list(conversation_log[[i]]$function_call$call_id)) {
            conversation_log[[i]]$function_call$call_id[[1]]
         } else {
            conversation_log[[i]]$function_call$call_id
         }
         
         if (entry_call_id == cancel_edit_call_id) {
            
            # Transform this entry from function call to plain assistant message
            conversation_log[[i]]$content <- "The model chose to cancel the edit."
            conversation_log[[i]]$function_call <- NULL  # Remove the function call
            
            break
         }
      }
   }
   
   # Assistant message has been transformed above - no additional creation/update needed
   
   # Write updated conversation log
   for (i in seq_along(conversation_log)) {
      entry <- conversation_log[[i]]
      content_preview <- if (!is.null(entry$content)) substr(entry$content, 1, 30) else "NO_CONTENT"
   }
   .rs.write_conversation_log(conversation_log)
   
   # Update display to show the cancellation
   .rs.update_conversation_display()
   
   # Return continue_and_display status - cancel_edit should update display then continue
   # The related_to_id should be the original user message ID that triggered the edit_file
   if (is.null(edit_file_entry$related_to)) {
      stop("edit_file entry missing required related_to field - this should never happen as related_to is required")
   }
   user_message_id <- edit_file_entry$related_to
   
   # CRITICAL FIX: Check for cancellation before returning continue_silent
   # If cancelled, return done to stop the conversation chain
   if (.rs.get_conversation_var("ai_cancelled")) {
      return(list(
         status = "done",
         data = list(
            message = "Edit cancelled by model - request cancelled, stopping conversation chain",
            related_to_id = user_message_id,
            conversation_index = .rs.get_current_conversation_index(),
            request_id = request_id
         )
      ))
   }
   
   return(list(
      status = "continue_silent",
      data = list(
         message = "Edit cancelled by model",
         related_to_id = user_message_id,
         conversation_index = .rs.get_current_conversation_index(),
         request_id = request_id
      )
   ))
})

.rs.addFunction("extract_command_and_explanation", function(function_call_entry, function_result = NULL) {
   # Extract command and explanation from function call entry
   # function_result: optional result from function execution (for run_file explanations)
   
   if (is.null(function_call_entry) || is.null(function_call_entry$function_call)) {
      return(list(command = "", explanation = ""))
   }
   
   function_name <- function_call_entry$function_call$name
   
   # Parse arguments
   args <- tryCatch({
      if (is.character(function_call_entry$function_call$arguments)) {
         jsonlite::fromJSON(function_call_entry$function_call$arguments, simplifyVector = FALSE)
      } else {
         function_call_entry$function_call$arguments
      }
   }, error = function(e) {
      return(NULL)
   })
   
   if (is.null(args)) {
      return(list(command = "", explanation = ""))
   }
   
   command <- ""
   explanation <- ""
   
   if (function_name == "delete_file") {
      # For delete_file, create the unlink command from the filename
      filename <- args$filename
      command <- paste0("unlink(\"", filename, "\")")
      explanation <- if (!is.null(args$explanation)) args$explanation else paste("Deleting file:", filename)
   } else if (function_name == "run_file") {
      # For run_file, use the already-processed command from handle_run_file
      if (!is.null(function_result) && !is.null(function_result$command)) {
         # Use the command that was already processed in handle_run_file
         command <- function_result$command
         explanation <- if (!is.null(function_result$explanation)) {
            function_result$explanation
         } else {
            # Fallback: create explanation from args
            filename <- args$filename
            start_line <- args$start_line_one_indexed
            end_line <- args$end_line_one_indexed_inclusive
            
            if (!is.null(start_line) && !is.null(end_line)) {
               paste0("Running: ", basename(filename), " (", start_line, "-", end_line, ")")
            } else if (!is.null(start_line)) {
               paste0("Running: ", basename(filename), " (", start_line, "-end)")
            } else if (!is.null(end_line)) {
               paste0("Running: ", basename(filename), " (1-", end_line, ")")
            } else {
               paste0("Running: ", basename(filename))
            }
         }
      } else {
         # For conversation reload: use modified_script field if available
         if (!is.null(function_call_entry$modified_script)) {
            command <- function_call_entry$modified_script
         } else {
            command <- ""
         }
         
         # Create explanation from args
         filename <- args$filename
         start_line <- args$start_line_one_indexed
         end_line <- args$end_line_one_indexed_inclusive
         
         if (!is.null(start_line) && !is.null(end_line)) {
            explanation <- paste0("Running: ", basename(filename), " (", start_line, "-", end_line, ")")
         } else if (!is.null(start_line)) {
            explanation <- paste0("Running: ", basename(filename), " (", start_line, "-end)")
         } else if (!is.null(end_line)) {
            explanation <- paste0("Running: ", basename(filename), " (1-", end_line, ")")
         } else {
            explanation <- paste0("Running: ", basename(filename))
         }
      }
   } else {
      # For run_console_cmd and run_terminal_cmd
      command <- if (!is.null(args$command)) args$command else ""
      
      # Trim triple backticks from console and terminal commands 
      if (function_name == "run_console_cmd") {
         # Apply same trimming logic as handle_run_console_cmd
         command <- gsub("^```[rR]?[mM]?[dD]?\\s*\\n?", "", command, perl = TRUE)
         command <- gsub("\\n?```\\s*$", "", command, perl = TRUE)
         command <- gsub("```\\n", "", command, perl = TRUE)
         command <- trimws(command)
      } else if (function_name == "run_terminal_cmd") {
         # Apply same trimming logic as handle_run_terminal_cmd
         command <- gsub("^```(?:shell|bash|sh)?\\s*\\n?", "", command, perl = TRUE)
         command <- gsub("\\n?```\\s*$", "", command, perl = TRUE)
         command <- gsub("```\\n", "", command, perl = TRUE)
         command <- trimws(command)
      }
      
      # For run_file, use the computed explanation from the function result if available
      if (!is.null(function_result$explanation)) {
         explanation <- function_result$explanation
      } else if (!is.null(args$explanation)) {
         explanation <- args$explanation
      } else {
         explanation <- ""
      }
   }
   
   return(list(command = command, explanation = explanation))
})

.rs.addFunction("create_function_call_widget_operation", function(function_call_entry, function_result = NULL) {
   # Create widget operation for console/terminal/edit_file function calls
   # Returns list with operation_type, message_id, command, explanation, is_console
   
   if (is.null(function_call_entry) || is.null(function_call_entry$function_call)) {
      return(NULL)
   }
   
   function_name <- function_call_entry$function_call$name
   
   # Only handle widget-creating function calls
   if (!function_name %in% c("run_console_cmd", "run_terminal_cmd", "delete_file", "run_file")) {
      return(NULL)
   }
   
   # Extract command and explanation
   cmd_info <- .rs.extract_command_and_explanation(function_call_entry, function_result)
   
   # Determine if this is a console or terminal command
   is_console <- (function_name %in% c("run_console_cmd", "delete_file", "run_file"))
   
   operation_type <- if (is_console) "create_console_command" else "create_terminal_command"
   
   return(list(
      operation_type = operation_type,
      message_id = as.numeric(function_call_entry$id),
      command = cmd_info$command,
      explanation = cmd_info$explanation,
      request_id = function_call_entry$request_id,
      is_console = is_console
   ))
})

.rs.addFunction("create_ai_operation_result", function(status, data = NULL, error = NULL, function_call = NULL) {
  result <- list(
    status = status,
    timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S")
  )
  
  if (!is.null(data)) {
    result$data <- data
  }
  
  if (!is.null(error)) {
    result$error <- error
  }
  
  if (!is.null(function_call)) {
    result$function_call <- function_call
  }
  
  return(result)
})

.rs.addFunction("check_assistant_message_limit", function() {
  max_assistant_messages <- 25
  current_count <- .rs.get_conversation_var("assistant_message_count", 0)
  
  if (current_count >= max_assistant_messages) {
    return(list(
      exceeded = TRUE,
      count = current_count,
      limit = max_assistant_messages
    ))
  }
  
  return(list(
    exceeded = FALSE,
    count = current_count,
    limit = max_assistant_messages
  ))
})

.rs.addFunction("increment_assistant_message_count", function() {
  current_count <- .rs.get_conversation_var("assistant_message_count", 0)
  new_count <- current_count + 1
  .rs.set_conversation_var("assistant_message_count", new_count)
  return(new_count)
})

.rs.addFunction("reset_assistant_message_count", function() {
  .rs.set_conversation_var("assistant_message_count", 0)
  return(TRUE)
})

.rs.addFunction("is_api_response_function_call", function(response) {
  direct_function_call <- (!is.null(response) && is.list(response) && 
                        !is.null(response$name) && !is.null(response$call_id))
  
  wrapped_function_call <- (!is.null(response) && is.list(response) && 
                         !is.null(response$function_call) && is.list(response$function_call) &&
                         !is.null(response$function_call$name) && !is.null(response$function_call$call_id))
  
  return(direct_function_call || wrapped_function_call)
  })

.rs.addJsonRpcHandler("check_terminal_complete", function(message_id) {
   if (!exists(".rs.check_terminal_complete", mode = "function")) {
      return(FALSE)
   }
   
   result <- .rs.check_terminal_complete(message_id)
   return(result)
})

.rs.addJsonRpcHandler("finalize_console_command", function(message_id, request_id, console_output = "") {
   result <- .rs.finalize_console_command(message_id, request_id, console_output)
   return(result)
})

.rs.addJsonRpcHandler("finalize_terminal_command", function(message_id, request_id) {
   result <- .rs.finalize_terminal_command(message_id, request_id)
   return(result)
})


.rs.addFunction("accept_edit_file_command", function(edited_code, message_id, request_id) {
   
   conversation_index <- .rs.get_current_conversation_index()
   
   modification_made <- FALSE
   file_written <- FALSE
   
   latest_message_id <- as.numeric(message_id)
   
   conversation_log <- .rs.read_conversation_log()
   
   .rs.update_conversation_display()
   
   # The message_id passed in should be the edit_file function call ID directly
   edit_file_message_id <- latest_message_id
   
   # Verify this is actually an edit_file function call and extract filename
   edit_file_entry <- NULL
   for (entry in conversation_log) {
      if (!is.null(entry$id) && entry$id == edit_file_message_id && 
          !is.null(entry$function_call) && !is.null(entry$function_call$name) &&
          entry$function_call$name == "edit_file") {
         edit_file_entry <- entry
         break
      }
   }
   
   if (is.null(edit_file_entry)) {
      cat("DEBUG accept_edit_file_command: No edit_file function call found for message ID:", latest_message_id, "\n")
      return(FALSE)
   }
   
   # Extract filename and line parameters from edit_file arguments
   edit_args <- tryCatch({
      if (is.character(edit_file_entry$function_call$arguments)) {
         jsonlite::fromJSON(edit_file_entry$function_call$arguments, simplifyVector = FALSE)
      } else {
         edit_file_entry$function_call$arguments
      }
   }, error = function(e) {
      return(NULL)
   })
   
   start_line <- edit_args$start_line
   end_line <- edit_args$end_line
   insert_line <- edit_args$insert_line
   
   if (is.null(edit_args) || is.null(edit_args$filename)) {
      cat("DEBUG accept_edit_file_command: No filename found in edit_file arguments\n")
      return(FALSE)
   }
   
   filename <- edit_args$filename
   
   if (is.null(filename) || filename == "" || is.na(filename)) {
      return(FALSE)
   }
   
   # Determine if the original file was saved or unsaved BEFORE any edits
   original_was_unsaved <- FALSE
   if (.rs.is_file_open_in_editor(filename)) {
      # File is open in editor
      doc_info <- .rs.get_open_document_by_path(filename)
      if (!is.null(doc_info) && !is.null(doc_info$dirty)) {
         original_was_unsaved <- as.logical(doc_info$dirty)
      }
   }
   
   # DEBUG: Log the original state
   if (.rs.is_file_open_in_editor(filename)) {
      doc_info <- .rs.get_open_document_by_path(filename)
   }
   
   # Get original content using effective file content (editor if open, otherwise disk)
   original_file_content <- .rs.get_effective_file_content(filename)
   if (is.null(original_file_content)) {
      original_file_content <- ""  # Empty string for non-existent files
   }
   file_existed <- !is.null(.rs.get_effective_file_content(filename)) || file.exists(filename)
   
   # Create directory structure if filename contains slashes
   # Skip directory creation for special __UNSAVED patterns (these are virtual identifiers, not real paths)
   if (!startsWith(filename, "__UNSAVED") && (grepl("/", filename) || grepl("\\\\", filename))) {
      file_dir <- dirname(filename)
      if (!dir.exists(file_dir)) {
         dir.create(file_dir, recursive = TRUE, showWarnings = FALSE)
      }
   }

   # Handle different edit modes based on line parameters
   if (!is.null(insert_line)) {
      # Insert mode: add new content after specified line
      new_lines <- strsplit(edited_code, "\n")[[1]]
      
      if (file_existed && nchar(original_file_content) > 0) {
         existing_lines <- strsplit(original_file_content, "\n")[[1]]
         
         # Insert new content after insert_line
         if (insert_line == 0) {
            # Insert at beginning
            processed_code <- c(new_lines, existing_lines)
         } else if (insert_line >= length(existing_lines)) {
            # Insert at end
            processed_code <- c(existing_lines, new_lines)
         } else {
            # Insert in middle
            before_lines <- existing_lines[1:insert_line]
            after_lines <- existing_lines[(insert_line + 1):length(existing_lines)]
            processed_code <- c(before_lines, new_lines, after_lines)
         }
      } else {
         # New file, just use the new content
         processed_code <- new_lines
      }
      
   } else if (!is.null(start_line) && !is.null(end_line)) {
      # Line range mode: process diff to extract only non-deleted lines
      diff_data <- .rs.get_diff_data_for_edit_file(edit_file_message_id)
      
      # Update diff data with current editor content from edited_code
      diff_data <- .rs.update_diff_with_editor_content(edited_code, edit_file_message_id, diff_data)
            
      # Process diff to get only the lines that should remain (not deleted)
      processed_range_lines <- character(0)
      
      for (i in seq_along(diff_data$diff)) {
         diff_entry <- diff_data$diff[[i]]
         editor_line <- diff_entry$content
            
         if (diff_entry$type == "unchanged" || diff_entry$type == "added") {
            # This line should be in the final output
            processed_range_lines <- c(processed_range_lines, editor_line)
         }
      }
      
      # Now replace the range in the original file
      if (file_existed && nchar(original_file_content) > 0) {
         existing_lines <- strsplit(original_file_content, "\n")[[1]]
         
         if (start_line <= length(existing_lines) && end_line <= length(existing_lines) && start_line <= end_line) {
            # Replace the specified range with processed content
            before_lines <- if (start_line > 1) existing_lines[1:(start_line - 1)] else character(0)
            after_lines <- if (end_line < length(existing_lines)) existing_lines[(end_line + 1):length(existing_lines)] else character(0)
            processed_code <- c(before_lines, processed_range_lines, after_lines)
         } else {
            stop("Invalid line range in edit_file command")
         }
      } else {
         # New file, just use the processed content
         processed_code <- processed_range_lines
      }
      
   } else {
      # Use diff data consistently for all edit modes to respect user modifications
      diff_data <- .rs.get_diff_data_for_edit_file(edit_file_message_id)
      
      # Update diff data with current editor content from edited_code
      diff_data <- .rs.update_diff_with_editor_content(edited_code, edit_file_message_id, diff_data)
      
      # Extract only the lines that should be in the final result (added + unchanged)
      processed_lines <- character(0)
      
      for (i in seq_along(diff_data$diff)) {
         diff_entry <- diff_data$diff[[i]]
         editor_line <- diff_entry$content
         
         if (diff_entry$type == "unchanged" || diff_entry$type == "added") {
            # This line should be in the final output
            processed_lines <- c(processed_lines, editor_line)
         }
         # Skip lines with type == "deleted" - they're removed from final result
      }
      
      # For different edit types, we need to combine the processed diff content with original file structure
      is_start_edit <- !is.null(diff_data$is_start_edit) && diff_data$is_start_edit
      is_end_edit <- !is.null(diff_data$is_end_edit) && diff_data$is_end_edit
      is_keyword_edit <- !is.null(edit_args$keyword) && edit_args$keyword != "start" && edit_args$keyword != "end"
      
      if (is_start_edit) {
         # For start edits: processed diff content + existing file content
         if (file_existed && nchar(original_file_content) > 0) {
            existing_lines <- strsplit(original_file_content, "\n")[[1]]
            processed_code <- c(processed_lines, existing_lines)
         } else {
            processed_code <- processed_lines
         }
         
      } else if (is_end_edit) {
         # For end edits: existing file content + processed diff content
         if (file_existed && nchar(original_file_content) > 0) {
            existing_lines <- strsplit(original_file_content, "\n")[[1]]
            processed_code <- c(existing_lines, processed_lines)
         } else {
            processed_code <- processed_lines
         }
         
      } else if (is_keyword_edit) {
         # For keyword edits: replace the keyword section with processed diff content
         function_output <- NULL
         for (entry in conversation_log) {
            if (!is.null(entry$type) && entry$type == "function_call_output" &&
               !is.null(entry$call_id) && 
               !is.null(edit_file_entry$function_call$call_id) &&
               entry$call_id == edit_file_entry$function_call$call_id) {
               function_output <- entry
               break
            }
         }
         
         if (!is.null(function_output) && !is.null(function_output$output)) {
            original_keyword_content <- function_output$output
            
            if (file_existed && nchar(original_file_content) > 0) {
               original_lines <- strsplit(original_file_content, "\n")[[1]]
               keyword_lines <- strsplit(original_keyword_content, "\n")[[1]]
               
               # Try to find and replace the keyword section
               replacement_made <- FALSE
               for (start_search in seq_along(original_lines)) {
                  if (start_search + length(keyword_lines) - 1 <= length(original_lines)) {
                     section_match <- all(original_lines[start_search:(start_search + length(keyword_lines) - 1)] == keyword_lines)
                     
                     if (section_match) {
                        before_lines <- if (start_search > 1) original_lines[1:(start_search - 1)] else character(0)
                        after_lines <- if (start_search + length(keyword_lines) <= length(original_lines)) original_lines[(start_search + length(keyword_lines)):length(original_lines)] else character(0)
                        processed_code <- c(before_lines, processed_lines, after_lines)
                        replacement_made <- TRUE
                        break
                     }
                  }
               }
               
               if (!replacement_made) {
                  # Fallback: use processed diff content only
                  processed_code <- processed_lines
               }
            } else {
               processed_code <- processed_lines
            }
         } else {
            # Fallback: use processed diff content
            processed_code <- processed_lines
         }
      } else {
         # Full file replacement - use processed diff content as-is
         processed_code <- processed_lines
      }
   }

   final_content <- paste(processed_code, collapse = "\n")
   
   # Handle different scenarios based on whether original file was saved or unsaved
   if (original_was_unsaved) {
      # Original file was unsaved - update editor without saving to disk
      # For accept operations, always mark_clean = FALSE to keep document marked as dirty
      success <- tryCatch({
         .rs.invokeRpc("update_open_document_content", filename, final_content, FALSE)
      }, error = function(e) {
         FALSE
      })
      
      if (success) {
         file_written <- TRUE
         # Check if this is a file creation (no previous content) or modification
         if (is.null(original_file_content) || nchar(original_file_content) == 0) {
            # This is a file creation - record as such
            .rs.record_file_creation(filename)
         } else {
            # This is a file modification - record with was_unsaved flag
            .rs.record_file_modification_with_diff_with_state(filename, original_file_content, final_content, original_was_unsaved)
         }
      }
   } else {
      # Original file was saved - update both editor and disk
      # Check if content has actually changed
      current_content <- .rs.get_effective_file_content(filename)
      content_changed <- is.null(current_content) || current_content != final_content
      
      if (content_changed) {
         # Apply edit using the routing system (which handles both editor and disk)
         success <- .rs.apply_file_edit(filename, final_content)
         if (success) {
            file_written <- TRUE
            # Check if this is a file creation (no previous content) or modification
            if (is.null(original_file_content) || nchar(original_file_content) == 0) {
               # This is a file creation - record as such
               .rs.record_file_creation(filename)
            } else {
               # This is a file modification - record with was_unsaved flag
               .rs.record_file_modification_with_diff_with_state(filename, original_file_content, final_content, original_was_unsaved)
            }
          }
      }
   }
   
   modification_made <- TRUE

   # Only call documentOpen for actual files, not for unsaved documents with special patterns
   # For unsaved files, the content was already updated via update_open_document_content RPC
   if (!original_was_unsaved && !startsWith(filename, "__UNSAVED")) {
      .rs.api.documentOpen(filename)
   }
   .rs.save_script_to_history(filename)
   .rs.build_symbol_index()
   
   # Look for existing "Response pending..." procedural user message and replace it
   conversation_log <- .rs.read_conversation_log()
   
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
   
   # Replace the pending message with acceptance
   pending_entry_index <- pending_entries[1]
   
   # Get the actual line range from the diff data to show where the edit appears in the final file
   diff_data <- .rs.get_diff_data_for_edit_file(edit_file_message_id)
   line_info <- ""
   
   if (!is.null(diff_data) && !is.null(diff_data$diff) && length(diff_data$diff) > 0) {
      # Extract line numbers from the diff data - look for new_line values that show where content appears in the final file
      new_line_numbers <- c()
      for (diff_entry in diff_data$diff) {
         if (!is.null(diff_entry$new_line) && !is.na(diff_entry$new_line) && diff_entry$new_line > 0) {
            # Only include lines that are actually in the final file (added or unchanged)
            if (!is.null(diff_entry$type) && (diff_entry$type == "added" || diff_entry$type == "unchanged")) {
               new_line_numbers <- c(new_line_numbers, diff_entry$new_line)
            }
         }
      }
      
      if (length(new_line_numbers) > 0) {
         new_line_numbers <- sort(unique(new_line_numbers))
         if (length(new_line_numbers) == 1) {
            line_info <- as.character(new_line_numbers[1])
         } else {
            # Show range from first to last line
            line_info <- paste0(min(new_line_numbers), "-", max(new_line_numbers))
         }
      } else {
         # Fallback: use line count if no specific line numbers found
         line_count <- length(processed_code)
         if (line_count > 0) {
            line_info <- paste0("1-", line_count)
         } else {
            line_info <- "1"
         }
      }
   } else {
      # Fallback: use line count if no diff data available
      line_count <- length(processed_code)
      if (line_count > 0) {
         line_info <- paste0("1-", line_count)
      } else {
         line_info <- "1"
      }
   }
   
   # Create enhanced procedural message with actual line and file information
   acceptance_message <- paste0("Edit file command accepted by user. The edit now constitutes lines ", 
                               line_info, " of the file ", basename(filename))
   
   conversation_log[[pending_entry_index]]$content <- acceptance_message
   # Keep procedural flag so this remains hidden from UI
   conversation_log[[pending_entry_index]]$procedural <- TRUE
   
   .rs.write_conversation_log(conversation_log)
   
   # Check if there are messages after this function call in the conversation
   # If so, trigger API continuation - similar to console/terminal commands
   function_call_message_id <- as.numeric(message_id)
   has_newer_messages <- any(sapply(conversation_log, function(entry) {
      if (is.null(entry$id) || entry$id <= function_call_message_id) {
         return(FALSE)
      }
      
      # For edit_file, exclude ALL messages related to this specific edit_file command:
      # - function_call_output (type = "function_call_output", related_to = function_call_id)
      # - assistant message (role = "assistant", related_to = function_call_id) 
      # - procedural user message (role = "user", procedural = true, related_to = function_call_id)
      # - images related to this function call (role = "user", related_to = function_call_id)
      if (!is.null(entry$related_to) && entry$related_to == function_call_message_id) {
         return(FALSE)
      }
      
      return(TRUE)
   }))
   
   # Return different status based on whether conversation has moved on
   # For continuation, we need to return the original user message ID, not the function call ID
   original_user_message_id <- edit_file_entry$related_to
   
   if (has_newer_messages) {
      result <- .rs.create_ai_operation_result(
         status = "done",
         data = list(
            message = "Edit file command accepted - conversation has moved on, not continuing API",
            related_to_id = as.integer(original_user_message_id),
            conversation_index = .rs.get_current_conversation_index(),
            request_id = request_id
         )
      )
      return(result)
   } else {
      # CRITICAL FIX: Check for cancellation before returning continue_silent
      # If cancelled, return done to stop the conversation chain
      if (.rs.get_conversation_var("ai_cancelled")) {
         result <- .rs.create_ai_operation_result(
            status = "done",
            data = list(
               message = "Edit file command accepted - request cancelled, stopping conversation chain",
               related_to_id = as.integer(original_user_message_id),
               conversation_index = .rs.get_current_conversation_index(),
               request_id = request_id
            )
         )
         return(result)
      }
      
      result <- .rs.create_ai_operation_result(
         status = "continue_silent",
         data = list(
            message = "Edit file command accepted - returning control to orchestrator",
            related_to_id = as.integer(original_user_message_id),
            conversation_index = .rs.get_current_conversation_index(),
            request_id = request_id
         )
      )
      return(result)
   }
  })

.rs.addJsonRpcHandler("revert_ai_message", function(message_id) {
   .rs.revert_ai_message(message_id)
   
   return(TRUE)
})

.rs.addFunction("revert_ai_message", function(message_id) {   
   
   conversation_log <- .rs.read_conversation_log()
   
   # STEP 1: Determine which query we're reverting to
   # Count original queries before and after the revert point
   original_queries_before_revert <- 0
   for (entry in conversation_log) {
      if (!is.null(entry$id) && entry$id < as.numeric(message_id) &&
          !is.null(entry$role) && entry$role == "user" &&
          !is.null(entry$original_query) && entry$original_query == TRUE) {
         original_queries_before_revert <- original_queries_before_revert + 1
      }
   }
   
   # STEP 2: Clear any background summarization state
   # Cancel any ongoing background summarization
   if (exists(".rs.clear_background_summarization_state", mode = "function")) {
      .rs.clear_background_summarization_state()
   }
   
   # STEP 3: Clean up summaries for queries after the revert point
   # Remove summaries for queries that will no longer exist after revert
   if (exists(".rs.load_conversation_summaries", mode = "function") && 
       exists(".rs.get_summaries_file_path", mode = "function")) {
      
      tryCatch({
         summaries <- .rs.load_conversation_summaries()
         
         if (length(summaries$summaries) > 0) {
            # Keep only summaries for queries that will still exist after revert
            filtered_summaries <- list()
            
            for (query_key in names(summaries$summaries)) {
               query_number <- as.numeric(query_key)
               
               # Keep summaries for queries that are before or at the revert point
               if (query_number <= original_queries_before_revert) {
                  filtered_summaries[[query_key]] <- summaries$summaries[[query_key]]
               }
            }
            
            # Update the summaries file
            updated_summaries <- list(summaries = filtered_summaries)
            summaries_path <- .rs.get_summaries_file_path()
            
            writeLines(jsonlite::toJSON(updated_summaries, auto_unbox = TRUE, pretty = TRUE), summaries_path)
         }
      }, error = function(e) {
         cat("DEBUG: Error cleaning up summaries during revert:", e$message, "\n")
         # Don't fail the revert operation due to summary cleanup errors
      })
   }
   
   # STEP 4: Proceed with normal revert logic
   filtered_log <- list()
   for (i in seq_along(conversation_log)) {
      if ((!is.null(conversation_log[[i]]$id) && conversation_log[[i]]$id < as.numeric(message_id)) ||
          (!is.null(conversation_log[[i]]$role) && conversation_log[[i]]$role == "developer")) {
         filtered_log <- c(filtered_log, list(conversation_log[[i]]))
      }
   }
   
   .rs.write_conversation_log(filtered_log)
   
   conversation_index <- .rs.get_current_conversation_index()
   
   .rs.clear_script_history_after_revert(as.numeric(message_id), conversation_index)
   
   changes_log <- .rs.read_file_changes_log()
   
   # Track files that need to be refreshed in the editor
   files_to_refresh <- character(0)
   
   if (length(changes_log$changes) > 0) {
      changes_to_revert <- list()
      for (i in seq_along(changes_log$changes)) {
         if (changes_log$changes[[i]]$conversation_id >= as.numeric(message_id)) {
            changes_to_revert <- c(changes_to_revert, list(changes_log$changes[[i]]))
         }
      }
      
      if (length(changes_to_revert) > 0) {
         change_ids <- sapply(changes_to_revert, function(change) change$id)
         sorted_indices <- order(change_ids, decreasing = TRUE)
         changes_to_revert <- changes_to_revert[sorted_indices]
         
         for (change in changes_to_revert) {
            file_path <- change$file_path
            
            if (change$action == "create") {
               # Skip if file doesn't exist (already reverted)
               if (!file.exists(file_path)) {
                  next
               }
               
               # File creations are always considered saved operations - delete from disk
               unlink(file_path)
               
               # Close any open tabs for this deleted file
               .rs.request_document_close_for_revert(file_path)
            } else if (change$action == "remove" && !is.null(change$previous_content)) {
               # Restore deleted file by recreating it with original content
               writeLines(strsplit(change$previous_content, "\n")[[1]], file_path)
               
               # Add file to refresh list
               files_to_refresh <- c(files_to_refresh, file_path)
            } else if (change$action == "modify" && !is.null(change$previous_content)) {
               # Skip if file doesn't exist (may have been deleted outside the AI system)
               if (!file.exists(file_path)) {
                  next
               }
               # Handle different revert scenarios based on whether original was saved or unsaved
               was_originally_unsaved <- !is.null(change$was_unsaved) && as.logical(change$was_unsaved)
               
               if (was_originally_unsaved) {
                  # Original file was unsaved - update editor without saving to disk
                  # For originally unsaved files, mark_clean = FALSE to keep document marked as dirty
                  success <- tryCatch({
                     .rs.invokeRpc("update_open_document_content", file_path, change$previous_content, FALSE)
                  }, error = function(e) {
                     FALSE
                  })
                  
                  if (!success) {
                     # Fallback: if file is not open, this does nothing (which is correct for unsaved files)
                     cat("DEBUG: Could not update unsaved file in editor during revert:", file_path, "\n")
                  }
               } else {
                  # Original file was saved - update editor first, then disk to avoid conflict dialog
                  
                  # First update the editor if the file is open
                  editor_updated <- FALSE
                  if (.rs.is_file_open_in_editor(file_path)) {
                     # For originally saved files, mark_clean = TRUE to mark document as clean (back to saved state)
                     editor_updated <- tryCatch({
                        .rs.invokeRpc("update_open_document_content", file_path, change$previous_content, TRUE)
                        TRUE
                     }, error = function(e) {
                        cat("DEBUG revert: Editor update RPC failed:", e$message, "\n")
                        FALSE
                     })
                  }
                  
                  # Then update the disk - this should not trigger a conflict dialog since editor is already updated
                  writeLines(strsplit(change$previous_content, "\n")[[1]], file_path)
                  
                  
               }
               
               # Add file to refresh list if it still exists after revert
               if (file.exists(file_path)) {
                  files_to_refresh <- c(files_to_refresh, file_path)
               }
            }
         }
         
         remaining_changes <- list()
         for (i in seq_along(changes_log$changes)) {
            if (changes_log$changes[[i]]$conversation_id < as.numeric(message_id)) {
               remaining_changes <- c(remaining_changes, list(changes_log$changes[[i]]))
            }
         }
         
         changes_log$changes <- remaining_changes
         .rs.write_file_changes_log(changes_log)
      }
   }
   
   # Refresh files in the editor by calling document open
   # NOTE: Temporarily disabled to avoid conflicts with direct refresh events
   # if (length(files_to_refresh) > 0) {
   #    for (file_path in unique(files_to_refresh)) {
   #       tryCatch({
   #          .rs.api.documentOpen(file_path)
   #       }, error = function(e) {
   #          # Silently continue if document open fails
   #       })
   #    }
   # }
   
   # STEP 5: Clean up conversation_diffs.json - remove diff entries for reverted messages
   tryCatch({
      diffs_data <- .rs.read_conversation_diffs()
      
      if (!is.null(diffs_data$diffs) && length(diffs_data$diffs) > 0) {
         # Filter out diffs for messages that are being reverted
         filtered_diffs <- list()
         
         for (msg_id_key in names(diffs_data$diffs)) {
            msg_id_numeric <- as.numeric(msg_id_key)
            if (!is.na(msg_id_numeric) && msg_id_numeric < as.numeric(message_id)) {
               filtered_diffs[[msg_id_key]] <- diffs_data$diffs[[msg_id_key]]
            }
         }
         
         # Update the diffs data structure
         diffs_data$diffs <- filtered_diffs
         .rs.write_conversation_diffs(diffs_data)
      }
   }, error = function(e) {
      cat("DEBUG: Error cleaning up conversation diffs during revert:", e$message, "\n")
      # Don't fail the revert operation due to diff cleanup errors
   })
   
   # STEP 6: Clean up message_buttons.csv - remove button entries for reverted messages
   tryCatch({
      message_buttons <- .rs.read_message_buttons()
      
      if (nrow(message_buttons) > 0) {
         # Filter out button entries for messages that are being reverted
         filtered_buttons <- message_buttons[message_buttons$message_id < as.numeric(message_id), , drop = FALSE]
         
         .rs.write_message_buttons(filtered_buttons)
      }
   }, error = function(e) {
      cat("DEBUG: Error cleaning up message buttons during revert:", e$message, "\n")
      # Don't fail the revert operation due to button cleanup errors
   })

   .rs.update_conversation_display()
   
   return(TRUE)
})

.rs.addFunction("request_document_close_for_revert", function(file_path) {   
   # Convert to absolute path and normalize for path matching
   # RStudio stores paths in a specific format, so we need to match that
   abs_path <- if (startsWith(file_path, "/") || grepl("^[A-Za-z]:", file_path)) {
      file_path
   } else {
      file.path(getwd(), file_path)
   }
   
   # Convert to the tilde-prefixed format that RStudio uses
   home_dir <- Sys.getenv("HOME")
   if (startsWith(abs_path, home_dir)) {
      tilde_path <- paste0("~/", substring(abs_path, nchar(home_dir) + 2))
   } else {
      tilde_path <- abs_path
   }
   
   .rs.enqueClientEvent("request_document_close_for_revert", list(
      file_path = tilde_path
   ))
})

.rs.addFunction("display_error_and_refresh", function(error_message) {  
  .rs.enqueClientEvent("update_thinking_message", list(message = "", hide_cancel = TRUE))
  
  error_obj <- list(
    error = TRUE,
    message = error_message,
    refresh = TRUE
  )
  
  to_json <- .rs.json_to_str(error_obj)
  
  stop(to_json)
})

.rs.addFunction("find_enclosing_code_block", function(file_path, line_number) {
   # Get file content from either disk or open editor (handles unsaved files)
   file_content <- .rs.get_effective_file_content(file_path)
   if (is.null(file_content)) {
      return(NULL)
   }
   
   all_lines <- strsplit(file_content, "\n")[[1]]
   
   code_blocks <- list()
   
   func_start_pattern <- "^\\s*([a-zA-Z0-9_\\.]+)\\s*<-\\s*function\\s*\\("
   func_start_pattern2 <- "^\\s*function\\s*\\("
   arrow_func_pattern <- "^\\s*([a-zA-Z0-9_\\.]+)\\s*<-\\s*\\(.*\\)\\s*=>\\s*\\{"
   
   in_function <- FALSE
   function_start_line <- NULL
   function_name <- NULL
   brace_count <- 0
   
   in_chunk <- FALSE
   chunk_start_line <- NULL
   chunk_pattern <- "^\\s*```\\{r.*\\}\\s*$"
   chunk_end_pattern <- "^\\s*```\\s*$"
   
   for (i in seq_along(all_lines)) {
      line <- all_lines[i]
      
      if (!in_function) {
         func_match <- regexpr(func_start_pattern, line, perl = TRUE)
         if (func_match > 0) {
            in_function <- TRUE
            function_start_line <- i
            match_text <- regmatches(line, func_match)
            function_name <- gsub(func_start_pattern, "\\1", match_text)
            
            brace_count <- brace_count + sum(gregexpr("\\{", line, perl = TRUE)[[1]] > 0)
            brace_count <- brace_count - sum(gregexpr("\\}", line, perl = TRUE)[[1]] > 0)
            
            if (grepl("\\{", line, perl = TRUE)) {
               brace_count <- brace_count + 1
            }
            next
         }
         
         func_match <- regexpr(func_start_pattern2, line, perl = TRUE)
         if (func_match > 0) {
            in_function <- TRUE
            function_start_line <- i
            function_name <- "anonymous"
            
            brace_count <- brace_count + sum(gregexpr("\\{", line, perl = TRUE)[[1]] > 0)
            brace_count <- brace_count - sum(gregexpr("\\}", line, perl = TRUE)[[1]] > 0)
            
            if (grepl("\\{", line, perl = TRUE)) {
               brace_count <- brace_count + 1
            }
            next
         }
         
         func_match <- regexpr(arrow_func_pattern, line, perl = TRUE)
         if (func_match > 0) {
            in_function <- TRUE
            function_start_line <- i
            match_text <- regmatches(line, func_match)
            function_name <- gsub(arrow_func_pattern, "\\1", match_text)
            
            brace_count <- brace_count + sum(gregexpr("\\{", line, perl = TRUE)[[1]] > 0)
            brace_count <- brace_count - sum(gregexpr("\\}", line, perl = TRUE)[[1]] > 0)
            next
         }
      } else {
         brace_count <- brace_count + sum(gregexpr("\\{", line, perl = TRUE)[[1]] > 0)
         brace_count <- brace_count - sum(gregexpr("\\}", line, perl = TRUE)[[1]] > 0)
         
         if (brace_count <= 0) {
            code_blocks <- c(code_blocks, list(list(
               type = "function",
               name = function_name,
               start_line = function_start_line,
               end_line = i,
               content = paste(all_lines[function_start_line:i], collapse = "\n")
            )))
            
            in_function <- FALSE
            function_start_line <- NULL
            function_name <- NULL
            brace_count <- 0
         }
      }
      
      if (!in_chunk && grepl(chunk_pattern, line, perl = TRUE)) {
         in_chunk <- TRUE
         chunk_start_line <- i
      } else if (in_chunk && grepl(chunk_end_pattern, line, perl = TRUE)) {
         code_blocks <- c(code_blocks, list(list(
            type = "chunk",
            name = paste0("chunk_", chunk_start_line),
            start_line = chunk_start_line,
            end_line = i,
            content = paste(all_lines[chunk_start_line:i], collapse = "\n")
         )))
         
         in_chunk <- FALSE
         chunk_start_line <- NULL
      }
   }
   
   if (in_function && !is.null(function_start_line)) {
      code_blocks <- c(code_blocks, list(list(
         type = "function",
         name = function_name,
         start_line = function_start_line,
         end_line = length(all_lines),
         content = paste(all_lines[function_start_line:length(all_lines)], collapse = "\n")
      )))
   }
   
   if (in_chunk && !is.null(chunk_start_line)) {
      code_blocks <- c(code_blocks, list(list(
         type = "chunk",
         name = paste0("chunk_", chunk_start_line),
         start_line = chunk_start_line,
         end_line = length(all_lines),
         content = paste(all_lines[chunk_start_line:length(all_lines)], collapse = "\n")
      )))
   }
   
   code_blocks <- c(code_blocks, list(list(
      type = "file",
      name = basename(file_path),
      start_line = 1,
      end_line = length(all_lines),
      content = paste(all_lines, collapse = "\n")
   )))
   
   most_relevant <- NULL
   
   for (block in code_blocks) {
      if (block$type == "function" && 
          block$start_line <= line_number && 
          block$end_line >= line_number) {
         most_relevant <- block
         break
      }
   }
   
   if (is.null(most_relevant)) {
      for (block in code_blocks) {
         if (block$type == "chunk" && 
             block$start_line <= line_number && 
             block$end_line >= line_number) {
            most_relevant <- block
            break
         }
      }
   }
   
   if (is.null(most_relevant)) {
      for (block in code_blocks) {
         if (block$type == "file") {
            most_relevant <- block
            break
         }
      }
   }
   
   return(most_relevant)
})

.rs.addFunction("extract_rmd_code_chunks", function(filename) {
   if (!file.exists(filename)) {
      return(list())
   }
   
   lines <- readLines(filename, warn = FALSE)
   
   chunks <- list()
   in_chunk <- FALSE
   current_chunk_start <- NULL
   current_chunk_lines <- character(0)
   current_chunk_label <- NULL
   
   chunk_start_pattern <- "^\\s*```+\\s*\\{[rR](.*)\\}\\s*$"
   chunk_end_pattern <- "^\\s*```+\\s*$"
   
   for (i in seq_along(lines)) {
      line <- lines[i]
      
      if (!in_chunk && grepl(chunk_start_pattern, line, perl = TRUE)) {
         in_chunk <- TRUE
         current_chunk_start <- i
         current_chunk_lines <- character(0)
         
         label_match <- regexec(chunk_start_pattern, line, perl = TRUE)
         if (length(label_match[[1]]) > 1) {
            chunk_options <- label_match[[1]][2]
            label_pattern <- ".*label\\s*=\\s*[\"']?([^,\"']+)[\"']?.*"
            if (grepl(label_pattern, chunk_options, perl = TRUE)) {
               label_extract <- regexec(label_pattern, chunk_options, perl = TRUE)
               if (length(label_extract[[1]]) > 1) {
                  current_chunk_label <- trimws(label_extract[[1]][2])
               }
            }
         }
      } 
      else if (in_chunk && !grepl(chunk_end_pattern, line, perl = TRUE)) {
         current_chunk_lines <- c(current_chunk_lines, line)
      } 
      else if (in_chunk && grepl(chunk_end_pattern, line, perl = TRUE)) {
         chunks <- c(chunks, list(list(
            label = current_chunk_label,
            start_line = current_chunk_start,
            end_line = i,
            code = paste(current_chunk_lines, collapse = "\n")
         )))
         
         in_chunk <- FALSE
         current_chunk_start <- NULL
         current_chunk_lines <- character(0)
         current_chunk_label <- NULL
      }
   }
   
   if (in_chunk) {
      chunks <- c(chunks, list(list(
         label = current_chunk_label,
         start_line = current_chunk_start,
         end_line = length(lines),
         code = paste(current_chunk_lines, collapse = "\n")
      )))
   }
   
   return(chunks)
})

.rs.addFunction("track_plot_state", function() {
   assign(".rs.previous_plots", list(), envir = .GlobalEnv)
   
   current_dev <- grDevices::dev.cur()
   if (current_dev > 1) {
      assign(".rs.previous_device", current_dev, envir = .GlobalEnv)
      
      tryCatch({
         current_plot <- grDevices::recordPlot()
         assign(".rs.previous_plot_record", current_plot, envir = .GlobalEnv)
         
         plot_dir <- getwd()
         
         width <- height <- NULL
         tryCatch({
            width <- grDevices::dev.size()[1]
            height <- grDevices::dev.size()[2]
         }, error = function(e) {
         })
         
         plot_info <- list(
            device = current_dev,
            timestamp = Sys.time(),
            directory = plot_dir,
            width = width,
            height = height
         )
         assign(".rs.plot_info", plot_info, envir = .GlobalEnv)
      }, error = function(e) {
         warning("Error recording plot state: ", e$message)
      })
   }
   
   invisible(TRUE)
})

.rs.addFunction("capture_new_plots", function() {
   new_plots <- list()
   
   current_dev <- grDevices::dev.cur()
   if (current_dev <= 1) {
      return(new_plots)
   }
   
   previous_dev <- if (exists(".rs.previous_device", envir = .GlobalEnv)) {
      get(".rs.previous_device", envir = .GlobalEnv)
   } else {
      0
   }
   
   previous_plot <- if (exists(".rs.previous_plot_record", envir = .GlobalEnv)) {
      get(".rs.previous_plot_record", envir = .GlobalEnv)
   } else {
      NULL
   }
   
   plot_files <- list()
   
   temp_dir <- tempdir()
   plot_patterns <- c("rs-graphics-", "Rplot")
   
   for (pattern in plot_patterns) {
      temp_files <- list.files(
         path = temp_dir, 
         pattern = paste0(pattern, ".*\\.(png|jpeg|jpg|bmp|tiff|svg)$"),
         full.names = TRUE
      )
      if (length(temp_files) > 0) {
         file_times <- file.info(temp_files)$mtime
         temp_files <- temp_files[order(file_times, decreasing = TRUE)]
         plot_files <- c(plot_files, temp_files)
      }
   }
   
   if (length(plot_files) > 0) {
      recent_file <- plot_files[1]
      
      if (file.exists(recent_file)) {
         file_time <- file.info(recent_file)$mtime
         if (exists(".rs.plot_info", envir = .GlobalEnv)) {
            plot_info <- get(".rs.plot_info", envir = .GlobalEnv)
            if (!is.null(plot_info$timestamp) && file_time > plot_info$timestamp) {
               new_plots[[1]] <- list(
                  filename = basename(recent_file),
                  filepath = recent_file,
                  index = 1,
                  is_active = TRUE
               )
            }
         } else {
            new_plots[[1]] <- list(
               filename = basename(recent_file),
               filepath = recent_file,
               index = 1,
               is_active = TRUE
            )
         }
      }
   }
   

   if (length(new_plots) == 0 && current_dev > 1) {
      
      tryCatch({
         current_plot <- grDevices::recordPlot()
         
         
         is_new_plot <- TRUE
         if (!is.null(previous_plot)) {
            
            tryCatch({
               is_new_plot <- !identical(current_plot, previous_plot)
                          }, error = function(e) {
               is_new_plot <- TRUE
            })
         }
         
         if (is_new_plot) {
 
            temp_file <- tempfile(pattern = "ai_plot_", fileext = ".png")
            
            
            tryCatch({
               
               current_device <- grDevices::dev.cur()
               grDevices::dev.copy(grDevices::png, filename = temp_file, width = 800, height = 600)
                                grDevices::dev.off()
               
               
               if (file.exists(temp_file) && file.info(temp_file)$size > 0) {
                  new_plots[[1]] <- list(
                     filename = basename(temp_file),
                     filepath = temp_file,
                     index = 1,
                     is_active = TRUE
                  )
               }
            }, error = function(e) {
               warning("Error saving plot to file: ", e$message)
            })
         }
      }, error = function(e) {
         warning("Error recording current plot: ", e$message)
      })
   }
   
   return(new_plots)
})


.rs.addFunction("add_plots_to_conversation", function(new_plots, message_id) {
   
   if (length(new_plots) == 0) {
      return(invisible(FALSE))
   }
   
   paths <- .rs.get_ai_file_paths()
   plots_dir <- file.path(paths$ai_dir, 'plots')
   if (!dir.exists(plots_dir)) {
      dir.create(plots_dir, recursive = TRUE)
   }

   message_id <- as.integer(message_id)
   
   plots_file <- file.path(plots_dir, paste0('plots_', message_id, '.json'))
   plots_data <- list()
   plot_names_for_log <- list()
   plot_images <- list()
   
   if (length(new_plots) > 0) {
      plot <- new_plots[[1]]
      
      if (is.null(plot$filepath) || !file.exists(plot$filepath)) {
         warning("Plot file does not exist: ", if(!is.null(plot$filepath)) plot$filepath else "NULL")
         return(invisible(FALSE))
      }
      
      plot_name <- paste0('plot_', format(Sys.time(), '%Y%m%d_%H%M%S'))
      
      tryCatch({
         plot_bin <- readBin(plot$filepath, 'raw', file.info(plot$filepath)$size)
         plot_b64 <- base64enc::base64encode(plot_bin)
         
         plots_data[[plot_name]] <- plot_b64
         plot_names_for_log <- c(plot_names_for_log, plot_name)
         
         mime_type <- "image/png"
         if (grepl("\\.jpe?g$", plot$filepath, ignore.case = TRUE)) {
            mime_type <- "image/jpeg"
         }
         
         base64_data <- paste0("data:", mime_type, ";base64,", plot_b64)
         
         plot_images[[1]] <- base64_data
         
      }, error = function(e) {
         warning("Error processing plot ", plot$filename, ": ", e$message)
         return(invisible(FALSE))
      })
   }
   
   if (length(plots_data) > 0) {
      jsonlite::write_json(plots_data, plots_file, auto_unbox = TRUE)
      
      conversation_log <- .rs.read_conversation_log()
      
      if (length(plot_images) == 0) {
         return(invisible(FALSE))
      }
      
      plots_message <- 'Generated plot:'
      
      image_content <- list(
         list(type = "input_text", text = plots_message)
      )
      
      image_content <- c(image_content, list(list(
         type = "input_image",
                    image_url = plot_images[[1]]
      )))
      
      plots_msg_id <- .rs.get_next_message_id()
      
      plots_log_entry <- list(
         id = plots_msg_id,
         role = 'user',
         content = image_content,
         related_to = as.integer(message_id),
         plots = plot_names_for_log,
         plots_file = plots_file
      )
      
      conversation_log <- c(conversation_log, list(plots_log_entry))
      .rs.write_conversation_log(conversation_log)
      
      return(invisible(TRUE))
   }
   
   return(invisible(FALSE))
})

.rs.addFunction("accept_terminal_command", function(message_id, script, request_id) {

   if (!is.null(message_id) && message_id != 0) {
      conversation_log <- .rs.read_conversation_log()
      for (i in seq_along(conversation_log)) {
         if (conversation_log[[i]]$id == message_id) {
            conversation_log[[i]]$modified_script <- script
            .rs.write_conversation_log(conversation_log)
            
            break
         }
      }
   }

   # Execute the terminal command
   terminal_id <- NULL
   
   terminal_id <- tryCatch({
      .rs.api.terminalExecute(
         command = script,
         workingDir = getwd(),
         show = TRUE
      )
   }, error = function(e) {
      NULL
   })
   
   
   if (!is.null(terminal_id) && nchar(terminal_id) > 0) {
      assign(".rs.terminal_id", terminal_id, envir = .GlobalEnv)
      assign(".rs.terminal_message_id", message_id, envir = .GlobalEnv)
      assign(".rs.terminal_done", FALSE, envir = .GlobalEnv)
   } else {
      terminal_id <- NULL
   }
   
   if (!is.null(terminal_id)) {
      return(list(success = TRUE, message = "Terminal command started"))
   } else {
      return(list(success = FALSE, error = "Failed to create terminal"))
   }
})

.rs.addFunction("accept_delete_file", function(message_id, script) {
   if (!is.null(message_id) && message_id != 0) {
      conversation_log <- .rs.read_conversation_log()
      for (i in seq_along(conversation_log)) {
         if (conversation_log[[i]]$id == message_id) {
            conversation_log[[i]]$modified_script <- script
            .rs.write_conversation_log(conversation_log)
            break
         }
      }
   }
   
   # Extract filename from script (format: unlink("filename"))
   filename_match <- regexpr('unlink\\("([^"]+)"\\)', script)
   if (filename_match > 0) {
      filename <- gsub('unlink\\("([^"]+)"\\)', "\\1", script)
      
      # Perform the deletion
      deletion_success <- tryCatch({
         if (file.exists(filename)) {
            # Store original content for reversion
            original_content <- readLines(filename, warn = FALSE)
            
            # Delete the file
            unlink(filename)
            
            # Close any open tabs for this deleted file
            .rs.request_document_close_for_revert(filename)
            
            # Record deletion in file changes log
            deletion_logged <- .rs.record_file_deletion(filename, paste(original_content, collapse = "\n"))

            TRUE
         } else {
            cat("DEBUG accept_delete_file: file does not exist, cannot delete\n")
            FALSE
         }
      }, error = function(e) {
         cat("DEBUG accept_delete_file: error during deletion:", e$message, "\n")
         FALSE
      })
      
      if (deletion_success) {
         return(list(success = TRUE, message = paste("File deleted:", filename)))
      } else {
         return(list(success = FALSE, error = paste("Failed to delete file:", filename)))
      }
   } else {
      return(list(success = FALSE, error = "Invalid delete command format"))
   }
})


.rs.addFunction("cancel_terminal_command", function(message_id, request_id) {
   # Update conversation log with modified script (same as accept_terminal_command)
   if (!is.null(message_id) && message_id != 0) {
      conversation_log <- .rs.read_conversation_log()
      for (i in seq_along(conversation_log)) {
         if (conversation_log[[i]]$id == message_id) {
            # For cancel, we don't have a modified script, so we can skip this part
            # conversation_log[[i]]$modified_script <- script
            # .rs.write_conversation_log(conversation_log)
            break
         }
      }
   }
   
   # Look up call_id from conversation log using message_id
   conversation_log <- .rs.read_conversation_log()
   call_id <- NULL
   
   # Find the message and extract its call_id
   for (entry in conversation_log) {
      if (entry$id == as.integer(message_id)) {
         if (!is.null(entry$function_call) && !is.null(entry$function_call$call_id)) {
            call_id <- entry$function_call$call_id
            break
         }
      }
   }
   
   if (is.null(call_id)) {
      warning("No function call found for message ID: ", message_id)
      return(FALSE)
   }
   
   # Store cancellation info for finalize_terminal_command to use
   assign(".rs.terminal_cancellation_message", "Terminal command cancelled by user", envir = .GlobalEnv)
   
   # Set up terminal state exactly like accept_terminal_command does
   # We need a dummy terminal_id for the polling mechanism to work
   terminal_id <- "cancelled"  # Use a special value to indicate cancellation
   assign(".rs.terminal_id", terminal_id, envir = .GlobalEnv)
   assign(".rs.terminal_message_id", message_id, envir = .GlobalEnv)
   assign(".rs.terminal_done", TRUE, envir = .GlobalEnv)  # Set to TRUE since we're already "done" (cancelled)
   
   # Check if there are messages after this function call in the conversation
   # If so, don't trigger API continuation - just update the output
   # Exclude the function_call_output for this specific call_id
   function_call_message_id <- as.numeric(message_id)
   has_newer_messages <- any(sapply(conversation_log, function(entry) {
      if (is.null(entry$id) || entry$id <= function_call_message_id) {
         return(FALSE)
      }
      
      # Exclude the function_call_output for this specific terminal command
      if (!is.null(entry$type) && entry$type == "function_call_output" && 
          !is.null(entry$call_id) && entry$call_id == call_id) {
         return(FALSE)
      }
      
      # Exclude images related to this function call (role = "user", related_to = function_call_message_id)
      if (!is.null(entry$role) && entry$role == "user" && 
          !is.null(entry$related_to) && entry$related_to == function_call_message_id) {
         return(FALSE)
      }
      
      return(TRUE)
   }))
      
   .rs.write_conversation_log(conversation_log)
   .rs.update_conversation_display()
   
   # Return simple result like accept_terminal_command does
   return(list(
      success = TRUE,
      message = "Terminal command cancelled"
   ))
})

.rs.addFunction("accept_console_command", function(message_id, script, request_id) {
   if (!is.null(message_id) && message_id != 0) {
      conversation_log <- .rs.read_conversation_log()
      for (i in seq_along(conversation_log)) {
         if (conversation_log[[i]]$id == message_id) {
            conversation_log[[i]]$modified_script <- script
            .rs.write_conversation_log(conversation_log)
            break
         }
      }
   }

   # Check package dependencies by creating a temporary file
   temp_file <- tempfile(fileext = ".R")
   writeLines(script, temp_file)
   
   packages_ok <- .rs.check_package_dependencies(temp_file, "r")
   
   # Clean up temporary file
   unlink(temp_file)
   
   if (!packages_ok) {
      .rs.api.sendToConsole("Code execution canceled: missing required packages", execute = FALSE)
      return(list(success = FALSE, error = "Missing required packages"))
   }
   
   # Set up plotting tracking before execution
   if (exists(".rs.track_plot_state", mode = "function")) {
      assign(".rs.console_message_id", message_id, envir = .GlobalEnv)
      .rs.track_plot_state()
      assign(".rs.tracking_plots", TRUE, envir = .GlobalEnv)
   }
   
   # Escape single quotes for R console
   # escaped_script <- gsub("'", "\\'", script, fixed = TRUE)
   
   # Check if this is a delete operation (unlink command) and handle file deletion logging
   if (grepl("^unlink\\s*\\(", script)) {
      
      # Extract filename from unlink command
      filename_match <- regexpr('unlink\\s*\\(\\s*["\']([^"\']+)["\']\\s*\\)', script, perl = TRUE)
      if (filename_match > 0) {
         filename <- gsub('unlink\\s*\\(\\s*["\']([^"\']+)["\']\\s*\\)', '\\1', script, perl = TRUE)
         
         # Store original content before deletion if file exists
         if (file.exists(filename)) {
            original_content <- tryCatch({
               readLines(filename, warn = FALSE)
            }, error = function(e) {
               cat("DEBUG accept_console_command: error reading file:", e$message, "\\n")
               character(0)
            })
            
            if (length(original_content) > 0) {
               if (exists(".rs.record_file_deletion", mode = "function")) {
                  deletion_logged <- .rs.record_file_deletion(filename, paste(original_content, collapse = "\\n"))
               }
            }
             
            # Close any open tabs for this file
            .rs.request_document_close_for_revert(filename)
         }
         
         # Close any open tabs for this file regardless of whether it exists on disk
         .rs.request_document_close_for_revert(filename)
      } else {
         cat("DEBUG accept_console_command: could not extract filename from unlink command\\n")
      }
   }
   
   # Execute the console command directly - let Java handle output tracking
   .rs.api.sendToConsole(script, execute = TRUE)
   
   return(list(
      success = TRUE,
      message = "Console command started - Java will handle output tracking"
   ))
})


.rs.addFunction("cancel_console_command", function(message_id, request_id) {
   # Look up call_id from conversation log using message_id
   conversation_log <- .rs.read_conversation_log()
   call_id <- NULL
   function_call_name <- NULL
   
   # Find the message and extract its call_id and function name
   for (entry in conversation_log) {
      if (entry$id == as.numeric(message_id)) {
         if (!is.null(entry$function_call) && !is.null(entry$function_call$call_id)) {
            call_id <- entry$function_call$call_id
            function_call_name <- entry$function_call$name
            break
         }
      }
   }
   
   if (is.null(call_id)) {
      warning("No function call found for message ID: ", message_id)
      return(FALSE)
   }
   
   # Create specific rejection message based on function type
   rejection_content <- switch(function_call_name,
      "delete_file" = "File deletion cancelled by user",
      "run_file" = "File execution cancelled by user", 
      "run_console_cmd" = "Console command cancelled by user",
      "Console command cancelled by user"  # fallback for unknown types
   )
   
   # Store cancellation info for finalize_console_command to use
   assign(".rs.console_cancellation_message", rejection_content, envir = .GlobalEnv)
   
   # Check if there are messages after this function call in the conversation
   # If so, don't trigger API continuation - just update the output
   # Exclude the function_call_output for this specific call_id
   function_call_message_id <- as.numeric(message_id)
   has_newer_messages <- any(sapply(conversation_log, function(entry) {
      if (is.null(entry$id) || entry$id <= function_call_message_id) {
         return(FALSE)
      }
      
      # Exclude the function_call_output for this specific console command
      if (!is.null(entry$type) && entry$type == "function_call_output" && 
          !is.null(entry$call_id) && entry$call_id == call_id) {
         return(FALSE)
      }

      # Exclude images related to this function call (role = "user", related_to = function_call_message_id)
      if (!is.null(entry$role) && entry$role == "user" && 
          !is.null(entry$related_to) && entry$related_to == function_call_message_id) {
         return(FALSE)
      }

      
      return(TRUE)
   }))
   
   .rs.write_conversation_log(conversation_log)
   .rs.update_conversation_display()
   
   # Set up console state for tracking (Java handles completion detection)
   assign(".rs.console_message_id", message_id, envir = .GlobalEnv)
   
   # Return simple result like accept_console_command does
   return(list(
      success = TRUE,
      message = "Console command cancelled"
   ))
})

.rs.addFunction("check_terminal_complete", function(message_id) {
   
   if (!exists(".rs.terminal_id", envir = .GlobalEnv)) {
      return(FALSE)
   }
   
   terminal_id <- get(".rs.terminal_id", envir = .GlobalEnv)
   
   # Handle cancelled terminals - they're immediately "complete"
   if (terminal_id == "cancelled") {
      # For cancelled terminals, we don't need to check if they're busy
      # They're already done by definition
      return(TRUE)
   }
   
   is_busy <- tryCatch({
      busy_result <- .rs.api.terminalBusy(terminal_id)
      busy_result
   }, error = function(e) {
      FALSE
   })
   
   if (!is_busy) {
 
      terminal_output <- tryCatch({
         buffer <- .rs.api.terminalBuffer(terminal_id)
         buffer
      }, error = function(e) {
         "Terminal command executed successfully"
      })
      
      
      exit_code <- tryCatch({
         code <- .rs.api.terminalExitCode(terminal_id)
         if (is.null(code)) 0 else code
              }, error = function(e) {
           0
      })
      
      if (is.character(terminal_output) && length(terminal_output) > 0) {
         terminal_output <- paste(terminal_output, collapse = "\n")
         terminal_output <- gsub("\033\\[[0-9;]*m", "", terminal_output)
         terminal_output <- trimws(terminal_output)
         if (nchar(terminal_output) == 0) {
            terminal_output <- "Terminal command executed successfully"
         }
      } else {
         terminal_output <- "Terminal command executed successfully"
      }
      
      
      if (exit_code != 0) {
         terminal_output <- paste0(terminal_output, "\n\nExit code: ", exit_code)
      } else {
         terminal_output <- paste0(terminal_output, "\n\nExit code: 0 (success)")
      }
      
      
      assign(".rs.terminal_output", terminal_output, envir = .GlobalEnv)
      assign(".rs.terminal_exit_code", exit_code, envir = .GlobalEnv)
      assign(".rs.terminal_done", TRUE, envir = .GlobalEnv)
      
      return(TRUE)
   }
   
   
   return(FALSE)
})

.rs.addFunction("finalize_terminal_command", function(message_id, request_id) {
   conversation_log <- .rs.read_conversation_log()
   
   function_call <- NULL
   call_id <- NULL
   
   assistant_message <- NULL
   for (entry in conversation_log) {
      if (entry$id == message_id) {
         assistant_message <- entry
         break
      }
   }

   if (is.null(assistant_message)) {
      return(FALSE)
   }
   
   if (!is.null(assistant_message$function_call) && 
       !is.null(assistant_message$function_call$name) &&
       assistant_message$function_call$name == "run_terminal_cmd") {
      function_call <- assistant_message
      call_id <- assistant_message$function_call$call_id
   } else {
      stop("No run_terminal_cmd found for message ID: ", message_id)
   }
   
   # For continuation, we need the original user message ID, not the function call ID
   related_to_id <- if (!is.null(assistant_message$related_to)) {
      as.numeric(assistant_message$related_to)
   } else {
      stop("No related_to found for message ID: ", message_id)
   }

   if (is.null(function_call)) {
      stop("No run_terminal_cmd found for message ID: ", message_id)
      return(FALSE)
   }
   
   # CRITICAL: Generate user_only HTML BEFORE calling API
   # At this point conversation has: user message, function call, code block, function output
   # But NOT yet the assistant's response to the output - this is perfect for user_only
   .rs.update_conversation_display()
   
   command_output <- ""
   
   # Check if this was a cancelled command
   if (exists(".rs.terminal_cancellation_message", envir = .GlobalEnv)) {
      command_output <- get(".rs.terminal_cancellation_message", envir = .GlobalEnv)
      rm(".rs.terminal_cancellation_message", envir = .GlobalEnv)
   } else if (exists(".rs.terminal_output", envir = .GlobalEnv)) {
      output <- get(".rs.terminal_output", envir = .GlobalEnv)
      if (length(output) > 0 && nchar(output) > 0) {
         command_output <- output
      } else {
         command_output <- "Terminal command executed successfully"
      }
   } else {
      command_output <- "Terminal command executed successfully"
   }
   
   # Find the unique pending message for this call_id
   fresh_log <- .rs.read_conversation_log()
   pending_entries <- which(sapply(fresh_log, function(entry) {
      !is.null(entry$type) && entry$type == "function_call_output" && 
      !is.null(entry$call_id) && entry$call_id == call_id &&
      !is.null(entry$output) && entry$output == "Response pending..."
   }))
   
   # Must find exactly one pending message
   if (length(pending_entries) != 1) {
      stop("Expected exactly 1 pending message for call_id ", call_id, ", found ", length(pending_entries))
   }
   
   # Replace the pending message
   pending_entry_index <- pending_entries[1]
   fresh_log[[pending_entry_index]]$output <- command_output
   # Keep the original message ID - don't assign a new one
   # Keep procedural flag - users see output in terminal widget, not conversation
   fresh_log[[pending_entry_index]]$procedural <- TRUE
   
   # Check if there are messages after this function call in the conversation
   # If so, don't trigger API continuation - just update the output
   # Exclude the function_call_output for this specific call_id
   function_call_message_id <- as.numeric(message_id)
   has_newer_messages <- any(sapply(fresh_log, function(entry) {
      if (is.null(entry$id) || entry$id <= function_call_message_id) {
         return(FALSE)
      }
      
      # Exclude the function_call_output for this specific terminal command
      if (!is.null(entry$type) && entry$type == "function_call_output" && 
          !is.null(entry$call_id) && entry$call_id == call_id) {
         return(FALSE)
      }

      # Exclude images related to this function call (role = "user", related_to = function_call_message_id)
      if (!is.null(entry$role) && entry$role == "user" && 
          !is.null(entry$related_to) && entry$related_to == function_call_message_id) {
         return(FALSE)
      }
      
      return(TRUE)
   }))
   
   .rs.write_conversation_log(fresh_log)
   

   if (exists(".rs.terminal_id", envir = .GlobalEnv)) {
      rm(".rs.terminal_id", envir = .GlobalEnv)
   }
   if (exists(".rs.terminal_output", envir = .GlobalEnv)) {
      rm(".rs.terminal_output", envir = .GlobalEnv)
   }
   if (exists(".rs.terminal_done", envir = .GlobalEnv)) {
      rm(".rs.terminal_done", envir = .GlobalEnv)
   }
   if (exists(".rs.terminal_message_id", envir = .GlobalEnv)) {
      rm(".rs.terminal_message_id", envir = .GlobalEnv)
   }
   
   # Return different status based on whether conversation has moved on
   if (has_newer_messages) {
      result <- .rs.create_ai_operation_result(
         status = "done",
         data = list(
            message = "Terminal command finalized - conversation has moved on, not continuing API",
            related_to_id = related_to_id,
            conversation_index = .rs.get_current_conversation_index(),
            request_id = request_id
         )
      )
   } else {
      # CRITICAL FIX: Check for cancellation before returning continue_silent
      # If cancelled, return done to stop the conversation chain
      if (.rs.get_conversation_var("ai_cancelled")) {
         result <- .rs.create_ai_operation_result(
            status = "done",
            data = list(
               message = "Terminal command finalized - request cancelled, stopping conversation chain",
               related_to_id = related_to_id,
               conversation_index = .rs.get_current_conversation_index(),
               request_id = request_id
            )
         )
      } else {
         result <- .rs.create_ai_operation_result(
            status = "continue_silent",
            data = list(
               message = "Terminal command finalized - returning control to orchestrator",
               related_to_id = related_to_id,
               conversation_index = .rs.get_current_conversation_index(),
               request_id = request_id
            )
         )
      }
   }
   
   return(result)
})

  
.rs.addFunction("finalize_console_command", function(message_id, request_id, console_output = "") {
   conversation_log <- .rs.read_conversation_log()
   
   function_call <- NULL
   call_id <- NULL
   
   assistant_message <- NULL
   for (entry in conversation_log) {
      if (entry$id == message_id) {
         assistant_message <- entry
         break
      }
   }

   if (is.null(assistant_message)) {
      return(FALSE)
   }
   
   if (!is.null(assistant_message$function_call) && 
       !is.null(assistant_message$function_call$name) &&
       (assistant_message$function_call$name == "run_console_cmd" || 
        assistant_message$function_call$name == "delete_file" ||
        assistant_message$function_call$name == "run_file")) {
      function_call <- assistant_message
      call_id <- assistant_message$function_call$call_id
   } else {
      stop("No run_console_cmd, delete_file, or run_file found for message ID: ", message_id)
   }
   
   # For continuation, we need the original user message ID, not the function call ID
   related_to_id <- if (!is.null(assistant_message$related_to)) {
      as.numeric(assistant_message$related_to)
   } else {
      as.numeric(message_id)  # fallback to function call ID if no related_to
   }

   if (is.null(function_call)) {
      stop("No run_console_cmd, delete_file, or run_file found for message ID: ", message_id)
      return(FALSE)
   }
      
   .rs.update_conversation_display()
   
   # Check for new plots generated during execution
   if (exists(".rs.tracking_plots", envir = .GlobalEnv) && get(".rs.tracking_plots", envir = .GlobalEnv) && 
       exists(".rs.capture_new_plots", mode = "function")) {
      Sys.sleep(0.2)  # Give plots time to be generated
      new_plots <- .rs.capture_new_plots()
      if (length(new_plots) > 0 && exists(".rs.add_plots_to_conversation", mode = "function")) {
         .rs.add_plots_to_conversation(new_plots, message_id)
      }
      assign(".rs.tracking_plots", FALSE, envir = .GlobalEnv)
   }
   
   # Re-read conversation log after plot detection to include any plot messages that were added
   conversation_log <- .rs.read_conversation_log()
   
   command_output <- ""
   
   # Check if this was a cancelled command
   if (exists(".rs.console_cancellation_message", envir = .GlobalEnv)) {
      command_output <- get(".rs.console_cancellation_message", envir = .GlobalEnv)
      rm(".rs.console_cancellation_message", envir = .GlobalEnv)
   } else if (!is.null(console_output) && nchar(console_output) > 0) {
      # Use Java-tracked console output - treat it as the command output
      command_output <- console_output
   } else {
      # No console output provided - check if this is a delete_file operation and provide specific feedback
      if (!is.null(assistant_message$function_call) && 
          !is.null(assistant_message$function_call$name) &&
          assistant_message$function_call$name == "delete_file") {
         
         # Extract filename from arguments for more specific feedback
         args <- tryCatch({
            if (is.character(assistant_message$function_call$arguments)) {
               jsonlite::fromJSON(assistant_message$function_call$arguments, simplifyVector = FALSE)
            } else {
               assistant_message$function_call$arguments
            }
         }, error = function(e) NULL)
         
         filename <- if (!is.null(args) && !is.null(args$filename)) args$filename else "file"
         command_output <- paste0("File '", basename(filename), "' was successfully deleted")
      } else {
         command_output <- "Code executed successfully with no output"
      }
   }
      
   # Find the unique pending message for this call_id
   pending_entries <- which(sapply(conversation_log, function(entry) {
      !is.null(entry$type) && entry$type == "function_call_output" && 
      !is.null(entry$call_id) && entry$call_id == call_id &&
      !is.null(entry$output) && entry$output == "Response pending..."
   }))
   
   # Must find exactly one pending message
   if (length(pending_entries) != 1) {
      stop("Expected exactly 1 pending message for call_id ", call_id, ", found ", length(pending_entries))
   }
   
   # Replace the pending message
   pending_entry_index <- pending_entries[1]
   conversation_log[[pending_entry_index]]$output <- command_output
   # Keep the original message ID - don't assign a new one
   # Keep procedural flag - users see output in console widget, not conversation
   conversation_log[[pending_entry_index]]$procedural <- TRUE
   
   # Check if there are messages after this function call in the conversation
   # If so, don't trigger API continuation - just update the output
   # Exclude the function_call_output for this specific call_id
   function_call_message_id <- as.numeric(message_id)
   has_newer_messages <- any(sapply(conversation_log, function(entry) {
      if (is.null(entry$id) || entry$id <= function_call_message_id) {
         return(FALSE)
      }
      
      # Exclude the function_call_output for this specific console command
      if (!is.null(entry$type) && entry$type == "function_call_output" && 
          !is.null(entry$call_id) && entry$call_id == call_id) {
         return(FALSE)
      }

      # Exclude images related to this function call (role = "user", related_to = function_call_message_id)
      if (!is.null(entry$role) && entry$role == "user" && 
          !is.null(entry$related_to) && entry$related_to == function_call_message_id) {
         return(FALSE)
      }
      
      return(TRUE)
   }))
   
   .rs.write_conversation_log(conversation_log)

   # Clean up tracking variables
   if (exists(".rs.console_message_id", envir = .GlobalEnv)) {
      rm(".rs.console_message_id", envir = .GlobalEnv)
   }
   
   # Return different status based on whether conversation has moved on
   if (has_newer_messages) {
      result <- .rs.create_ai_operation_result(
         status = "done",
         data = list(
            message = "Console command finalized - conversation has moved on, not continuing API",
            related_to_id = related_to_id,
            conversation_index = .rs.get_current_conversation_index(),
            request_id = request_id
         )
      )
   } else {
      # CRITICAL FIX: Check for cancellation before returning continue_silent
      # If cancelled, return done to stop the conversation chain
      if (.rs.get_conversation_var("ai_cancelled")) {
         result <- .rs.create_ai_operation_result(
            status = "done",
            data = list(
               message = "Console command finalized - request cancelled, stopping conversation chain",
               related_to_id = related_to_id,
               conversation_index = .rs.get_current_conversation_index(),
               request_id = request_id
            )
         )
      } else {
         result <- .rs.create_ai_operation_result(
            status = "continue_silent",
            data = list(
               message = "Console command finalized - returning control to orchestrator",
               related_to_id = related_to_id,
               conversation_index = .rs.get_current_conversation_index(),
               request_id = request_id
            )
         )
      }
   }
   
   return(result)
})

.rs.addFunction("process_single_function_call", function(function_call, related_to_id, request_id, response_id = NULL) {
   if (.rs.get_conversation_var("ai_cancelled")) {
      return(.rs.create_ai_operation_result(
         status = "done",
         data = list(message = "Request cancelled by user")
      ))
   }
   
   conversation_index <- .rs.get_current_conversation_index()

   conversation_log <- .rs.read_conversation_log()
   
   function_name <- if (is.list(function_call$name)) function_call$name[[1]] else function_call$name
   call_id <- if (is.list(function_call$call_id)) function_call$call_id[[1]] else function_call$call_id
      
   current_logForChecking <- Filter(function(entry) {
      if (is.null(entry$function_call)) {
         return(TRUE)
      }
      
      entry_call_id <- if (is.list(entry$function_call$call_id)) entry$function_call$call_id[[1]] else entry$function_call$call_id
      if (entry_call_id == call_id) {
         return(FALSE)
      }
      return(TRUE)
   }, conversation_log)
      
   function_callExists <- FALSE
   for (entry in conversation_log) {
      if (!is.null(entry$function_call)) {
 
         entry_call_id <- if (is.list(entry$function_call$call_id)) entry$function_call$call_id[[1]] else entry$function_call$call_id
         if (entry_call_id == call_id) {
            function_callExists <- TRUE
            break
         }
      }
   }

   if (!function_callExists) {
      function_call_id <- .rs.get_next_message_id()
      # No function calls need to be excluded - end_turn is now handled as standalone event
      should_exclude <- FALSE
      
      
      normalized_function_call <- list(
         name = function_name,
         arguments = if (is.list(function_call$arguments)) function_call$arguments[[1]] else function_call$arguments,
         call_id = call_id,
         msg_id = function_call_id
      )
      
      # Only add to conversation log if not excluded
      if (!should_exclude) {
         function_callEntry <- list(
            id = function_call_id,
            role = "assistant",
            function_call = normalized_function_call,
            related_to = related_to_id,
            request_id = request_id
         )
         
         if (function_name == "edit_file") {
            function_callEntry$source_function <- "edit_file"
         }
         
         conversation_log <- c(conversation_log, list(function_callEntry))
      }
      
      # Create immediate "Response pending..." for interactive functions
      if (function_name %in% c("run_console_cmd", "run_terminal_cmd", "delete_file", "run_file")) {
         # For console/terminal commands: create function_call_output
         pending_output_id <- .rs.get_next_message_id()
         pending_output <- list(
            id = pending_output_id,
            type = "function_call_output",
            call_id = call_id,
            output = "Response pending...",
            related_to = function_call_id,
            procedural = TRUE  # Mark as procedural so it doesn't show in UI
         )
         conversation_log <- c(conversation_log, list(pending_output))
      }
      
      .rs.write_conversation_log(conversation_log)
      
      # Note: Function calls are now stored only in conversation_log.json (not conversation.json)
   }
   
   # Progress messages
   arguments <- .rs.safe_parse_function_arguments(function_call)
   
   # Process the function call using specific handlers
   # Note: end_turn is now handled as standalone event at backend level, not as function call
   if (function_name == "run_console_cmd") {
      function_result <- .rs.handle_run_console_cmd(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "run_terminal_cmd") {
      function_result <- .rs.handle_run_terminal_cmd(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "edit_file") {
      function_result <- .rs.handle_edit_file(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "find_keyword_context") {
      function_result <- .rs.handle_find_keyword_context(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "grep_search") {
      function_result <- .rs.handle_grep_search(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "read_file") {
      function_result <- .rs.handle_read_file(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "view_image") {
      function_result <- .rs.handle_view_image(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "search_for_file") {
      function_result <- .rs.handle_search_for_file(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "list_dir") {
      function_result <- .rs.handle_list_dir(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "delete_file") {
      function_result <- .rs.handle_delete_file(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "run_file") {
      function_result <- .rs.handle_run_file(normalized_function_call, conversation_log, related_to_id, request_id)
   } else if (function_name == "cancel_edit") {
      function_result <- .rs.handle_cancel_edit(normalized_function_call, conversation_log, related_to_id, request_id)
   } else {
      # Fallback for unknown function calls
      function_output_id <- .rs.get_next_message_id()
      
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = normalized_function_call$call_id,
        output = paste0("Function call to ", function_name, " received. Full implementation coming soon."),
        related_to = normalized_function_call$msg_id
      )
      
      function_result <- list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      )
   }
   
   # Create function call message for functions that don't have dedicated widgets
   if (function_name %in% c("find_keyword_context", "grep_search", "read_file", "view_image", "search_for_file", "list_dir")) {
      # Find the function call message ID from conversation log
      conversation_log <- .rs.read_conversation_log()
      function_callMsgId <- NULL
      for (entry in conversation_log) {
         if (!is.null(entry$function_call)) {
            entry_call_id <- if (is.list(entry$function_call$call_id)) entry$function_call$call_id[[1]] else entry$function_call$call_id
            if (entry_call_id == call_id) {
               function_callMsgId <- entry$id
               break
            }
         }
      }
      
      if (!is.null(function_callMsgId)) {
         # Generate the function call message using shared function
         arguments <- .rs.safe_parse_function_arguments(normalized_function_call)
         function_message <- .rs.generate_function_call_message(function_name, arguments, is_thinking = FALSE)
         
         # Send function call message creation event to client
         .rs.send_ai_operation("create_function_call_message", list(
            message_id = as.numeric(function_callMsgId),
            content = function_message,
            request_id = request_id
         ))
      }
   }
   
   # Handle cancel_edit special case
   if (function_name == "cancel_edit") {
      # cancel_edit returns a special status and should not create widgets or continue with normal processing
      if (is.list(function_result) && !is.null(function_result$status) && function_result$status == "continue_silent") {
         operation_result <- .rs.create_ai_operation_result(
            status = "continue_silent",
            data = function_result$data  # Pass through the complete data from handle_cancel_edit
         )
         return(operation_result)
      }
   }
   
   # Handle breakout_of_function_calls for run_console_cmd/run_terminal_cmd/delete_file  
   if (function_name == "run_console_cmd" || function_name == "run_terminal_cmd" || function_name == "delete_file" || function_name == "run_file") {
      # Find the function call entry using shared function
      conversation_log <- .rs.read_conversation_log()
      function_call_entry <- .rs.find_function_call_by_call_id(conversation_log, call_id)
      
      if (!is.null(function_call_entry)) {
         # Create widget operation using shared function
         widget_op <- .rs.create_function_call_widget_operation(function_call_entry, function_result)
         
         if (!is.null(widget_op)) {
            # Send widget creation event to client
            .rs.send_ai_operation(widget_op$operation_type, list(
               message_id = widget_op$message_id,
               command = widget_op$command,
               explanation = widget_op$explanation,
               request_id = widget_op$request_id
            ))
            
            # Determine command type for return value
            command_type <- if (widget_op$is_console) "Console" else "Terminal"
            
            operation_result <- .rs.create_ai_operation_result(
               status = "pending",
               data = list(
                  command_type = tolower(command_type),
                  message_id = widget_op$message_id,
                  conversation_index = conversation_index
               )
            )      
            return(operation_result)
         }
      }
   } else if (!is.null(function_result$function_call_output)) {
      conversation_log <- .rs.read_conversation_log()
      updated_log <- c(conversation_log, list(function_result$function_call_output))
      
      # CRITICAL FIX: Special handling for view_image - add the image message to conversation log
      if (function_name == "view_image" && !is.null(function_result$image_message_entry)) {
         updated_log <- c(updated_log, list(function_result$image_message_entry))
      }
      
      .rs.write_conversation_log(updated_log)
      
      # Only call update_conversation_display() for functions that create UI elements
      # Skip for simple text output functions to avoid clearing thinking messages
      needs_ui_update <- function_name %in% c("edit_file", "view_image")
      if (needs_ui_update) {
         .rs.update_conversation_display()
      }
      
      # CRITICAL FIX: For edit_file, the continue status should pass the function call message ID
      # as related_to_id so the assistant response relates to the function call, not the user message
      continue_related_to_id <- related_to_id  # default fallback
      if (function_name == "edit_file" && !is.null(function_result$function_call_output$related_to)) {
         continue_related_to_id <- function_result$function_call_output$related_to
      } else if (function_name == "view_image" && !is.null(function_result$image_msg_id)) {
         # For view_image, the assistant should respond to the image message
         continue_related_to_id <- function_result$image_msg_id
      }
      
      operation_result <- .rs.create_ai_operation_result(
         status = "continue_and_display",
         data = list(
            message = paste("Function", function_name, "completed successfully"),
            conversation_updated = TRUE,
            conversation_index = conversation_index,
            related_to_id = continue_related_to_id,
            request_id = request_id
         )
      )
      
      # Return immediately for normal functions - don't go through conversion logic
      return(operation_result)
   } else {
      operation_result <- .rs.create_ai_operation_result(
         status = "error",
         error = paste("Unexpected result from function", function_name)
      )
      
      # Return immediately for error cases - don't go through conversion logic
      return(operation_result)
   }

})


# Function for processing single function calls from Java
.rs.addFunction("process_function_call", function(function_call, related_to_id, request_id, response_id = NULL) {
   if (is.null(related_to_id)) {
      stop("related_to_id is required and cannot be NULL")
   }
   
   result <- .rs.process_single_function_call(function_call, related_to_id, request_id, response_id)
   return(result)
})

.rs.addFunction("initialize_conversation", function(query, request_id) {
   if (!exists(".rs.complete_deferred_conversation_init", mode = "function")) {
      .rs.initialize_conversationDefaults()
   } else {
      .rs.complete_deferred_conversation_init()
   }
   

   if (is.null(query) || !is.character(query) || nchar(query) == 0) {
      return(.rs.create_ai_operation_result(
         status = "error",
         error = "Invalid query parameter"
      ))
   }
   
   .rs.reset_ai_cancellation()
   
   # Reset assistant message count for new conversations
   .rs.reset_assistant_message_count()
   
   if (!is.null(request_id) && nchar(request_id) > 0) {
      .rs.setVar("active_api_request_id", request_id)
      .rs.enqueClientEvent("store_active_request_id", list(id = request_id))
   }
   
   conversation_index <- .rs.get_current_conversation_index()
   conversation_log <- .rs.read_conversation_log()
   msg_id <- .rs.get_next_message_id()
   
   conversation_log <- .rs.read_conversation_log()
   
   conversation_log <- c(conversation_log, list(list(id = msg_id, role = "user", content = query, original_query = TRUE)))
   
   .rs.write_conversation_log(conversation_log)
   .rs.update_conversation_display()
   
   # Handle background summarization for original queries
   should_trigger <- .rs.should_trigger_summarization(conversation_log)
   
   if (should_trigger) {
      current_query_count <- .rs.count_original_queries(conversation_log)
      highest_summarized <- .rs.get_highest_summarized_query()
      
      # Check if we need to start summarization for query N-1
      # Query N triggers summarization of query N-1
      target_query <- current_query_count - 1

      if (target_query > highest_summarized && target_query >= 1) {
         .rs.start_background_summarization(conversation_log, target_query)
      }
   }
   
   result <- .rs.create_ai_operation_result(
      status = "done",
      data = list(
         conversation_index = conversation_index,
         user_message_id = msg_id
      )
   )
   
   return(result)
})

# make_api_call compatibility wrapper is now defined above in the main consolidation

if (exists(".rs.complete_deferred_conversation_init", mode = "function")) {
   .rs.complete_deferred_conversation_init()
}

# NEW CONSOLIDATED AI OPERATION FLOW

# Helper function to determine if conversation should continue automatically
# For OpenAI: Only continue if last assistant message was a function call
# For Anthropic: Always continue (they handle end_turn properly)
.rs.addFunction("should_auto_continue_conversation", function() {
   provider <- .rs.get_active_provider()
   
   # Anthropic models always continue (they handle end_turn properly)
   if (is.null(provider) || provider != "openai") {
      return(TRUE)
   }
   
   # For OpenAI models, check if the last assistant message was a function call
   conversation_log <- .rs.read_conversation_log()
   
   # Find the last assistant message (traverse backwards)
   for (i in rev(seq_along(conversation_log))) {
      msg <- conversation_log[[i]]
      if (!is.null(msg$role) && msg$role == "assistant") {
         # If it has a function_call, continue; if it's just text, don't continue
         return(!is.null(msg$function_call))
      }
   }
   
   # No assistant messages found, continue by default
   return(TRUE)
})

.rs.addFunction("ai_operation", function(operation_type, 
                                       query = NULL, 
                                       request_id, 
                                       function_call = NULL, 
                                       api_response = NULL,
                                       related_to_id,
                                       model = NULL, 
                                       preserve_symbols = TRUE,
                                       is_continue = FALSE) {
   
   # CRITICAL FIX: Check cancellation FIRST before any other processing
   # This must be the very first check to prevent continue_silent returns
   if (.rs.get_conversation_var("ai_cancelled")) {
      return(.rs.create_ai_operation_result(
         status = "done",
         data = list(message = "Request cancelled by user")
      ))
   }

   # Input validation and defaults
   conversation_index <- .rs.get_current_conversation_index()
   
   if (is.null(model)) {
      model <- .rs.get_selected_model()
   }
   
   # ==========================================
   # OPERATION TYPE: MAKE_API_CALL (now uses streaming)
   # ==========================================
   if (operation_type == "make_api_call") {
      # Check if we need to wait for background summarization before making API call
      # This ensures we have the most recent summary available when needed
      conversation_log <- .rs.read_conversation_log()
      current_query_count <- .rs.count_original_queries(conversation_log)
      
      # If we have 3+ queries and there's background summarization in progress,
      # we need to wait for it since we might need the summary for N-2
      if (current_query_count >= 3) {
         state <- .rs.load_background_summarization_state()
         if (!is.null(state)) {
            .rs.wait_for_persistent_background_summarization()
         }
      }
      
      conversation_log <- .rs.read_conversation_log()
      api_conversation_log <- conversation_log
      
      # related_to_id is required for ai_operation - error out if not provided
      if (is.null(related_to_id)) {
         stop("related_to_id is required for ai_operation but was NULL")
      }
      
      # Set the related_to_id in conversation variables so streaming can access it
      .rs.set_conversation_var("current_related_to_id", related_to_id)
      
      # Check assistant message limit before making API call
      limit_check <- .rs.check_assistant_message_limit()
      if (limit_check$exceeded) {
         # Display error message in UI without storing in conversation log
         error_message <- paste0("Rao currently stops after ", limit_check$limit, " assistant messages. Please paste a new message to continue.")
         
         # Use existing ai_operation system to display temporary assistant message
         # This will show in the UI like an assistant message but won't be stored in conversation log
         .rs.send_ai_operation("create_assistant_message", list(
            message_id = .rs.get_next_message_id(),
            content = error_message
         ))
         
         return(.rs.create_ai_operation_result(
            status = "done",
            data = list(
               message = paste0("Maximum assistant message limit reached (", limit_check$limit, " messages)"),
               conversation_index = conversation_index
            )
         ))
      }
      
      # Increment assistant message count right before API call
      # This ensures every assistant response (text or function calls) is counted
      .rs.increment_assistant_message_count()
      
      # Use backend_ai_api_call (now uses streaming infrastructure)
      streaming_result <- .rs.backend_ai_api_call(
         conversation = api_conversation_log, 
         model = model, 
         request_id = request_id
      )
      
      # Clear the related_to_id after streaming completes, but keep assistant_message_id for process_assistant_response
      .rs.set_conversation_var("current_related_to_id", NULL)
      
      # Check if the result contains a function call
      if (is.list(streaming_result) && !is.null(streaming_result$action) && streaming_result$action == "function_call") {
         return(.rs.process_single_function_call(streaming_result$function_call,
                                               related_to_id,
                                               request_id,
                                               streaming_result$response_id))
      }
      
      # For text responses - check if this is edit_file related (needs post-streaming save)
      # Note: end_turn signals are handled within the response processing logic below
      if (is.list(streaming_result) && !is.null(streaming_result$response)) {
         
         response_content <- streaming_result$response
         
         # Only process if we have content to work with
         if (!is.null(response_content)) {
         # Check if this response is edit_file related and needs to be saved here
         # edit_file responses are intentionally NOT saved during streaming to prevent duplicates
         conversation_log <- .rs.read_conversation_log()
         is_edit_file_related <- FALSE
         
         # related_to_id should always be present at this point
         if (is.null(related_to_id)) {
            stop("related_to_id is required but was NULL when checking for edit_file relation")
         }
         
         for (entry in conversation_log) {
            if (!is.null(entry$id) && entry$id == related_to_id && 
                !is.null(entry$function_call) && !is.null(entry$function_call$name) &&
                entry$function_call$name == "edit_file") {
               is_edit_file_related <- TRUE
               break
            }
         }
         
         if (is_edit_file_related) {
            # edit_file responses need to be saved here since they skip streaming saves
            assistant_msg_id <- if (!is.null(streaming_result$assistant_message_id)) {
              streaming_result$assistant_message_id
            } else {
              NULL
            }
            
            # Add metadata for cancelled partial responses and response_id
            message_metadata <- NULL
            if (!is.null(streaming_result$cancelled) && streaming_result$cancelled) {
               message_metadata <- list(cancelled = TRUE, partial_content = TRUE)
            }
            
            # Include response_id if available for reasoning model chaining
            if (!is.null(streaming_result$response_id)) {
              if (is.null(message_metadata)) {
                message_metadata <- list()
              }
              message_metadata$response_id <- streaming_result$response_id
            }
            
            result <- .rs.process_assistant_response(response_content, assistant_msg_id, related_to_id,
               conversation_index, "ai_operation", message_metadata, NULL)
            
            if (is.list(result) && !is.null(result$limit_exceeded) && result$limit_exceeded) {
               return(.rs.create_ai_operation_result(
                  status = "done",
                  data = list(
                     message = "Assistant message limit exceeded",
                     conversation_index = conversation_index
                  )
               ))
            }
            
            response_message <- if (!is.null(streaming_result$cancelled) && streaming_result$cancelled) {
               "Partial edit_file response preserved after cancellation"
            } else if (!is.null(streaming_result$end_turn) && streaming_result$end_turn == TRUE) {
               "edit_file response completed with end_turn signal"
            } else {
               "edit_file response processed and saved"
            }
            return(.rs.create_ai_operation_result(
               status = "done",
               data = list(
                  message = response_message,
                  conversation_index = conversation_index,
                  related_to_id = related_to_id
               )
            ))
         } else {
            # Regular assistant messages are saved during streaming completion in SessionAiAPI.R
            # EXCEPT for cancelled responses - those need to be saved here to preserve partial content
            if (!is.null(streaming_result$cancelled) && streaming_result$cancelled) {
               # Save cancelled partial content to conversation log
               assistant_msg_id <- if (!is.null(streaming_result$assistant_message_id)) {
                 streaming_result$assistant_message_id
               } else {
                 NULL
               }
               
               # Add metadata for cancelled partial responses
               message_metadata <- list(cancelled = TRUE, partial_content = TRUE)
               
               # Include response_id if available for reasoning model chaining
               if (!is.null(streaming_result$response_id)) {
                 message_metadata$response_id <- streaming_result$response_id
               }
               
               result <- .rs.process_assistant_response(response_content, assistant_msg_id, related_to_id,
                  conversation_index, "ai_operation", message_metadata, NULL)
               
               if (is.list(result) && !is.null(result$limit_exceeded) && result$limit_exceeded) {
                  return(.rs.create_ai_operation_result(
                     status = "done",
                     data = list(
                        message = "Assistant message limit exceeded",
                        conversation_index = conversation_index
                     )
                  ))
               }
               
               return(.rs.create_ai_operation_result(
                  status = "done",
                  data = list(
                     message = "Partial assistant response preserved after cancellation",
                     conversation_index = conversation_index,
                     related_to_id = related_to_id
                  )
               ))
            }
            
            response_message <- if (!is.null(streaming_result$end_turn) && streaming_result$end_turn == TRUE) {
               "Assistant response completed with end_turn signal"
            } else {
               "Assistant response processed and saved during streaming"
            }
            
            # CRITICAL FIX: Check for cancellation before returning continue_silent
            # If cancelled, return done to stop the conversation chain
            if (.rs.get_conversation_var("ai_cancelled")) {
               return(.rs.create_ai_operation_result(
                  status = "done",
                  data = list(
                     message = "Request cancelled by user - stopping conversation chain",
                     conversation_index = conversation_index,
                     related_to_id = related_to_id
                  )
               ))
            }
            
            # CRITICAL FIX: If we have an end_turn signal, always return done - never continue
            if (!is.null(streaming_result$end_turn) && streaming_result$end_turn == TRUE) {
               return(.rs.create_ai_operation_result(
                  status = "done",
                  data = list(
                     message = "Assistant indicated end of turn",
                     conversation_index = conversation_index,
                     related_to_id = related_to_id
                  )
               ))
            }
            
            # NEW FIX: For OpenAI models, only continue automatically if the last assistant message was a function call
            # This prevents duplicate responses and unnecessarily long conversation chains
            should_continue <- .rs.should_auto_continue_conversation()
            
            if (should_continue) {
               return(.rs.create_ai_operation_result(
                  status = "continue_silent",
                  data = list(
                     message = response_message,
                     conversation_index = conversation_index,
                     related_to_id = related_to_id,
                     request_id = request_id
                  )
               ))
            } else {
               # For OpenAI text responses, return done to stop auto-continuation
               return(.rs.create_ai_operation_result(
                  status = "done",
                  data = list(
                     message = paste(response_message, "- conversation chain stopped (OpenAI text response)"),
                     conversation_index = conversation_index,
                     related_to_id = related_to_id
                  )
               ))
            }
         }
         }  # Close if (!is.null(response_content))
      }
      
      # Handle end_turn signals without response content
      if (is.list(streaming_result) && !is.null(streaming_result$end_turn) && streaming_result$end_turn == TRUE) {
         return(.rs.create_ai_operation_result(
            status = "done",
            data = list(
               message = "Assistant indicated end of turn",
               conversation_index = conversation_index,
               related_to_id = related_to_id
            )
         ))
      }
      
      # Handle early cancellation case - when streaming_result is NULL because cancellation 
      # happened before API call even started (R is single-threaded)
      if (is.null(streaming_result)) {
         
         # Mark the user's message as cancelled
         conversation_log <- .rs.read_conversation_log()
         for (i in seq_along(conversation_log)) {
            if (!is.null(conversation_log[[i]]$id) && conversation_log[[i]]$id == related_to_id) {
               # Add cancellation metadata to the user message
               conversation_log[[i]]$cancelled <- TRUE
               break
            }
         }
         .rs.write_conversation_log(conversation_log)
         
         return(.rs.create_ai_operation_result(
            status = "done",
            data = list(
               message = "Request cancelled before reaching API",
               conversation_index = conversation_index,
               related_to_id = related_to_id
            )
         ))
      }
      
      if (is.list(streaming_result) && !is.null(streaming_result$using_backend)) {
         # Streaming completed but no text response - this means only function calls occurred
         # Don't create an empty assistant response, just return success
         return(.rs.create_ai_operation_result(
            status = "done",
            data = list(
               message = "Streaming completed - function calls only",
               conversation_index = conversation_index,
               related_to_id = related_to_id
            )
         ))
      }
      
      # For streaming completion without explicit response field - return success
      return(.rs.create_ai_operation_result(
         status = "done",
         data = list(
            message = "Streaming completed",
            conversation_index = conversation_index,
            related_to_id = related_to_id
         )
      ))
   }
   
   # OPERATION TYPE: FUNCTION_CALL (delegated to process_single_function_call)
   else if (operation_type == "function_call") {      

      return(.rs.process_single_function_call(function_call, related_to_id, request_id, NULL))
   }

   # INVALID OPERATION TYPE
   else {
      stop("DEBUG: Unknown operation_type received:", operation_type)
   }
})

.rs.addFunction("clean_summaries_after_revert", function(max_valid_query_number) {
   # Clean up any summaries for queries beyond the specified number
   # This ensures that after a revert, we don't have stale summaries
   
   if (!exists(".rs.load_conversation_summaries", mode = "function") || 
       !exists(".rs.get_summaries_file_path", mode = "function")) {
      return(FALSE)
   }
   
   tryCatch({
      summaries <- .rs.load_conversation_summaries()
      
      if (length(summaries$summaries) == 0) {
         return(TRUE)  # Nothing to clean
      }
      
      # Keep only summaries for queries that are still valid
      filtered_summaries <- list()
      removed_count <- 0
      
      for (query_key in names(summaries$summaries)) {
         query_number <- as.numeric(query_key)
         
         if (query_number <= max_valid_query_number) {
            filtered_summaries[[query_key]] <- summaries$summaries[[query_key]]
         } else {
            removed_count <- removed_count + 1
         }
      }
      
      # Update the summaries file only if we actually removed something
      if (removed_count > 0) {
         updated_summaries <- list(summaries = filtered_summaries)
         summaries_path <- .rs.get_summaries_file_path()
         
         writeLines(jsonlite::toJSON(updated_summaries, auto_unbox = TRUE, pretty = TRUE), summaries_path)         
         return(TRUE)
      }
      
      return(TRUE)  # No changes needed
      
   }, error = function(e) {
      cat("DEBUG: Error in clean_summaries_after_revert:", e$message, "\n")
      return(FALSE)
   })
})
