# SessonAiConversationDisplay.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

# ==============================================
# SEQUENCE NUMBER TRACKING FOR AI OPERATIONS
# ==============================================
# Track sequence numbers per conversation to handle out-of-order events

.rs.addFunction("get_next_ai_operation_sequence", function() {
   conversation_index <- .rs.get_current_conversation_index()
   
   # Generate sequence variable name based on conversation index
   sequence_var_name <- paste0("ai_operation_sequence_", conversation_index)
   current_sequence <- .rs.get_conversation_var(sequence_var_name)
   
   if (is.null(current_sequence)) {
      current_sequence <- 0
   }
   
   current_sequence <- current_sequence + 1
   
   # Save the updated sequence
   .rs.set_conversation_var(sequence_var_name, current_sequence)
   
   return(current_sequence)
})

.rs.addFunction("reset_ai_operation_sequence", function(conversation_index) {
   if (is.null(conversation_index)) {
      conversation_index <- .rs.get_current_conversation_index()
   }
   
   # Reset sequence counter for this conversation (start at 0 so first increment returns 1)
   sequence_var_name <- paste0("ai_operation_sequence_", conversation_index)
   .rs.set_conversation_var(sequence_var_name, 0)
})

.rs.addFunction("send_ai_operation", function(operation_type, data = list()) {
   # Get sequence number for current conversation
   sequence <- .rs.get_next_ai_operation_sequence()
   
   # Add sequence to the data
   data$operation_type <- operation_type
   data$sequence <- sequence
   
   # Send the operation event with sequence number
   .rs.enqueClientEvent("ai_operation", data)
})



.rs.addFunction("update_conversation_display", function() {
   tryCatch({
   # Get file paths
   file_paths <- .rs.get_ai_file_paths()
   
   # Get current conversation index
   conversation_index <- .rs.get_conversation_var("current_conversation_index")
   
   # Generate and save conversation display
   result <- .rs.generate_and_save_conversation_display(conversation_index)
      
   return(result)
   }, error = function(e) {
    return(FALSE)
   })
})

.rs.addFunction("generate_and_save_conversation_display", function(conversation_index) {  
  # Read conversation_log for widget recreation
  conversation_log <- .rs.read_conversation_log()
  
  # Recreate console/terminal/edit_file widgets for this conversation
  .rs.recreate_console_widgets_for_conversation(conversation_log)
  
  # Create revert buttons for user messages
  if (length(conversation_log) > 0) {
    .rs.create_revert_buttons_for_user_messages(conversation_log)
  }
  
  return(TRUE)
})

.rs.addFunction("get_message_title", function(message_id, conversation_log) {
   # Find the message
   message <- NULL
   for (entry in conversation_log) {
      if (!is.null(entry$id) && entry$id == message_id) {
         message <- entry
         break
      }
   }
   
   if (is.null(message)) {
      return(NULL)
   }
   
   # Check for run_console_cmd, run_terminal_cmd, delete_file, or run_file
   if (!is.null(message$function_call) && !is.null(message$function_call$name)) {
      if (message$function_call$name == "run_console_cmd") {
         return("Console")
      } else if (message$function_call$name == "run_terminal_cmd") {
         return("Terminal")
             } else if (message$function_call$name == "delete_file") {
          return("Console")
      } else if (message$function_call$name == "run_file") {
         # Extract arguments to create custom title
         args <- tryCatch({
            if (is.character(message$function_call$arguments)) {
               jsonlite::fromJSON(message$function_call$arguments, simplifyVector = FALSE)
            } else {
               message$function_call$arguments
            }
         }, error = function(e) {
            return(NULL)
         })
         
         if (!is.null(args) && !is.null(args$filename)) {
            filename <- basename(args$filename)
            start_line <- args$start_line_one_indexed
            end_line <- args$end_line_one_indexed_inclusive
            
            if (!is.null(start_line) && !is.null(end_line)) {
               return(paste0("Running: ", filename, " (", start_line, "-", end_line, ")"))
            } else if (!is.null(start_line)) {
               return(paste0("Running: ", filename, " (", start_line, "-end)"))
            } else if (!is.null(end_line)) {
               return(paste0("Running: ", filename, " (1-", end_line, ")"))
            } else {
               return(paste0("Running: ", filename))
            }
         }
         return("Running file")
      }
   }
   
   # Check if it's an assistant message with related_to pointing to edit_file
   if (!is.null(message$role) && message$role == "assistant" && !is.null(message$related_to)) {
      # Find the related edit_file function call
      for (entry in conversation_log) {
         if (!is.null(entry$id) && entry$id == message$related_to && 
             !is.null(entry$function_call) && !is.null(entry$function_call$name) &&
             entry$function_call$name == "edit_file") {
            
            # Extract filename from the edit_file call
            args <- tryCatch({
               if (is.character(entry$function_call$arguments)) {
                  jsonlite::fromJSON(entry$function_call$arguments, simplifyVector = FALSE)
               } else {
                  entry$function_call$arguments
               }
            }, error = function(e) {
               return(NULL)
            })
            
            if (!is.null(args) && !is.null(args$filename)) {
               filename <- basename(args$filename)
               
               # Calculate diff statistics
               diff_stats <- .rs.get_edit_file_diff_stats(message_id, conversation_log)
               
               if (!is.null(diff_stats) && (diff_stats$added > 0 || diff_stats$deleted > 0)) {
                  # Format diff stats with CSS classes for proper styling
                  addition_text <- paste0('<span class="addition">+', diff_stats$added, '</span>')
                  removal_text <- paste0('<span class="removal">-', diff_stats$deleted, '</span>')
                  diff_text <- paste(addition_text, removal_text)
                  # Return filename with diff stats in a span that can be styled
                  return(paste0(filename, ' <span class="diff-stats">', diff_text, '</span>'))
               }
               
               return(filename)
            }
         }
      }
   }
   
   return(NULL)
})

.rs.addFunction("get_edit_file_diff_stats", function(message_id, conversation_log) {
   # Find the assistant message
   assistant_message <- NULL
   for (entry in conversation_log) {
      if (!is.null(entry$id) && entry$id == message_id && 
          !is.null(entry$role) && entry$role == "assistant") {
         assistant_message <- entry
         break
      }
   }
   
   if (is.null(assistant_message) || is.null(assistant_message$related_to)) {
      return(NULL)
   }
   
   # Always use the conversation_diffs.json file to get diff statistics
   # Never use raw files since they might change
   
   # Read the conversation diffs data
   diffs_data <- .rs.read_conversation_diffs()
   
   if (is.null(diffs_data$diffs) || length(diffs_data$diffs) == 0) {
      return(NULL)
   }
   
   # Look for the related_to ID in the diffs data (edit_file function call ID)
   edit_file_id_str <- as.character(assistant_message$related_to)
   
   if (!is.null(diffs_data$diffs[[edit_file_id_str]])) {
      diff_entry <- diffs_data$diffs[[edit_file_id_str]]
      
      if (!is.null(diff_entry$diff_data) && length(diff_entry$diff_data) > 0) {
         # Count lines added and deleted from the diff_data array
         added_count <- 0
         deleted_count <- 0
         
         for (diff_item in diff_entry$diff_data) {
            if (!is.null(diff_item$type)) {
               if (diff_item$type == "added") {
                  added_count <- added_count + 1
               } else if (diff_item$type == "deleted") {
                  deleted_count <- deleted_count + 1
               }
            }
         }
         
         return(list(
            added = added_count,
            deleted = deleted_count
         ))
      }
   }
   
   return(NULL)
})

.rs.addFunction("parse_code_block_content", function(content, filename = NULL) {
   if (is.null(content) || nchar(content) == 0) {
      return(content)
   }
   
   # Check for different types of code blocks
   has_rmd_block <- grepl("````", content, fixed = TRUE) || grepl("````rmd", content, fixed = TRUE) || grepl("````markdown", content, fixed = TRUE) || grepl("```rmd", content, fixed = TRUE)
   has_regular_code_block <- grepl("```", content, fixed = TRUE)
   
   cleaned_content <- content
   
   if (has_rmd_block) {
      # Handle RMD blocks with ````
      # Pattern: ````(language)?\n...content...````
      rmd_pattern <- "````([a-zA-Z]*)?\\s*\\n([\\s\\S]*?)````"
      if (grepl(rmd_pattern, content, perl = TRUE)) {
         matches <- regmatches(content, gregexpr(rmd_pattern, content, perl = TRUE))
         if (length(matches) > 0 && length(matches[[1]]) > 0) {
            match <- matches[[1]][1]
            # Remove opening ````language\n
            start_pattern <- "^````([a-zA-Z]*)?\\s*\\n"
            match <- gsub(start_pattern, "", match, perl = TRUE)
            # Remove closing ````
            extracted_code <- gsub("````$", "", match, perl = TRUE)
            
            cleaned_content <- extracted_code
         }
      }
   }
   else if (has_regular_code_block) {
      # Handle regular code blocks with ```
      # Determine language from filename extension
      language <- .rs.get_language_from_filename(filename)
      
      # Pattern: ```(language)?\n...content...```
      if (!is.null(language)) {
         # Try specific language first
         code_pattern <- paste0("```", language, "\\s*\\n([\\s\\S]*?)```")
         if (grepl(code_pattern, content, perl = TRUE, ignore.case = TRUE)) {
            cleaned_content <- gsub(code_pattern, "\\1", content, perl = TRUE, ignore.case = TRUE)
         } else {
            # Try uppercase version
            code_pattern <- paste0("```", toupper(language), "\\s*\\n([\\s\\S]*?)```")
            if (grepl(code_pattern, content, perl = TRUE)) {
               cleaned_content <- gsub(code_pattern, "\\1", content, perl = TRUE)
            }
         }
      }
      
      # If no specific language match, try generic code block
      if (cleaned_content == content) {
         generic_pattern <- "```[a-zA-Z]*?\\s*\\n([\\s\\S]*?)```"
         if (grepl(generic_pattern, content, perl = TRUE)) {
            cleaned_content <- gsub(generic_pattern, "\\1", content, perl = TRUE)
         }
      }
   }
   
   return(trimws(cleaned_content))
})

.rs.addFunction("get_language_from_filename", function(filename) {
   if (is.null(filename)) {
      return(NULL)
   }
   
   lower_filename <- tolower(filename)
   if (grepl("\\.r$", lower_filename)) {
      return("r")
   } else if (grepl("\\.py$", lower_filename)) {
      return("python")
   } else if (grepl("\\.js$", lower_filename)) {
      return("javascript")
   } else if (grepl("\\.java$", lower_filename)) {
      return("java")
   } else if (grepl("\\.(cpp|c)$", lower_filename)) {
      return("cpp")
   } else if (grepl("\\.(sh|bash)$", lower_filename)) {
      return("bash")
   } else if (grepl("\\.sql$", lower_filename)) {
      return("sql")
   } else if (grepl("\\.html$", lower_filename)) {
      return("html")
   } else if (grepl("\\.css$", lower_filename)) {
      return("css")
   } else if (grepl("\\.json$", lower_filename)) {
      return("json")
   } else if (grepl("\\.rmd$", lower_filename)) {
      return("rmd")
   }
   
   return(NULL)
})

.rs.addFunction("is_plot_message", function(entry) {
   # Check if this entry has plots and plots_file fields (top-level message fields)
   has_plots_field <- !is.null(entry$plots) && length(entry$plots) > 0
   has_plots_file_field <- !is.null(entry$plots_file)
   
   # Also check if content contains input_image type
   has_image_content <- FALSE
   if (!is.null(entry$content) && is.list(entry$content)) {
      for (content_item in entry$content) {
         if (!is.null(content_item$type) && content_item$type == "input_image") {
            has_image_content <- TRUE
            break
         }
      }
   }
   
   # A message is considered a plot message if it has plots metadata OR image content
   return(has_plots_field || has_plots_file_field || has_image_content)
})

.rs.addFunction("recreate_console_widgets_for_conversation", function(conversation_log) {   
   # First, reset sequence for this conversation and signal start of background recreation
   conversation_index <- .rs.get_current_conversation_index()
   .rs.reset_ai_operation_sequence(conversation_index)
   
   .rs.send_ai_operation("start_background_recreation", list(
      message_id = "recreation_start",
      command = "",
      explanation = "Starting background conversation recreation"
   ))
   
   # Clear the conversation in background mode
   .rs.send_ai_operation("clear_conversation")
   
   # Handle empty conversations - still do the atomic swap but with empty content
   if (length(conversation_log) == 0) {
      # Signal end of background recreation and swap to foreground (empty content)
      .rs.send_ai_operation("finish_background_recreation", list(
         message_id = "recreation_finish",
         command = "",
         explanation = "Finishing background conversation recreation (empty)"
      ))
      return(TRUE)  # Return TRUE to indicate recreation was performed
   }
   
   # Sort conversation_log by ID to ensure chronological processing
   conversation_log_sorted <- conversation_log[order(sapply(conversation_log, function(x) x$id %||% 0))]
   
   items_created <- 0
   
   # Process entries in order - all existing operations will go to background automatically
   for (entry in conversation_log_sorted) {
      # Handle user messages (but exclude procedural messages and plot/image messages)
      if (!is.null(entry$role) && entry$role == "user" && !is.null(entry$content) &&
          (is.null(entry$procedural) || !entry$procedural) &&
          !.rs.is_plot_message(entry)) {
         .rs.send_ai_operation("create_user_message", list(
            message_id = as.numeric(entry$id),
            content = entry$content
         ))
         items_created <- items_created + 1
      }
      
      # Handle function calls
      if (!is.null(entry$function_call) && !is.null(entry$function_call$name)) {
         function_name <- entry$function_call$name
         
         # Handle function calls that should show as permanent messages (no widgets)
         if (function_name %in% c("find_keyword_context", "grep_search", "read_file", "view_image", "search_for_file", "list_dir")) {
            # Parse function call arguments using safe function
            args <- .rs.safe_parse_function_arguments(entry$function_call)
            
            # Generate function message using shared function
            function_message <- .rs.generate_function_call_message(function_name, args, is_thinking = FALSE)
            
            # Send function call message creation event to client
            .rs.send_ai_operation("create_function_call_message", list(
               message_id = as.numeric(entry$id),
               content = function_message,
               request_id = entry$request_id
            ))
            items_created <- items_created + 1
         } 
         # Handle function calls that create widgets (console/terminal/edit_file)
         else if (function_name == "run_console_cmd" || function_name == "run_terminal_cmd" || function_name == "delete_file" || function_name == "run_file") {
            # Re-run handle_run_file to get the current file content for the widget
            function_result <- NULL
            if (function_name == "run_file") {
               # Re-run handle_run_file to get the current file content for the widget
               function_result <- tryCatch({
                  .rs.handle_run_file(entry$function_call, conversation_log, entry$related_to, entry$request_id)
               }, error = function(e) {
                  # If handle_run_file fails, create a fallback with an error message
                  list(
                     command = paste0("# Error retrieving file content: ", e$message),
                     explanation = .rs.get_message_title(entry$id, conversation_log) %||% "Running file"
                  )
               })
            }
            
            # Create widget operation using shared function
            widget_op <- .rs.create_function_call_widget_operation(entry, function_result)
            
            if (!is.null(widget_op)) {
               # Send widget creation event to client
               .rs.send_ai_operation(widget_op$operation_type, list(
                  message_id = widget_op$message_id,
                  command = widget_op$command,
                  explanation = widget_op$explanation,
                  request_id = widget_op$request_id
               ))
               items_created <- items_created + 1
               
               # CRITICAL: Check if buttons should be hidden after widget creation
               if (.rs.should_hide_buttons_for_restored_widget(entry$id)) {
                  widget_type <- if (widget_op$is_console) "console" else "terminal"
                  .rs.send_ai_operation("hide_widget_buttons", list(
                     message_id = as.character(entry$id),
                     content = widget_type  # widget_type goes in content field for Java mapping
                  ))
               }
            }
         }
      }
      
      # Handle assistant messages (but skip edit_file related ones as they become widgets)
      if (!is.null(entry$role) && entry$role == "assistant" && !is.null(entry$content)) {
         # Check if this is related to an edit_file function call
         is_edit_file_related <- FALSE
         if (!is.null(entry$related_to)) {
            for (related_entry in conversation_log) {
               if (!is.null(related_entry$id) && related_entry$id == entry$related_to && 
                   !is.null(related_entry$function_call) && !is.null(related_entry$function_call$name) &&
                   related_entry$function_call$name == "edit_file") {
                  is_edit_file_related <- TRUE
                  
                  # Create edit_file widget instead of assistant message
                  filename <- "unknown"
                  args <- .rs.safe_parse_function_arguments(related_entry$function_call)
                  
                  if (!is.null(args) && !is.null(args$filename)) {
                     filename <- args$filename
                  }
                  
                  # Get the filename with diff stats
                  filename_with_stats <- .rs.get_message_title(entry$id, conversation_log)
                  if (is.null(filename_with_stats)) {
                     filename_with_stats <- basename(filename)
                  }
                  
                  # Check if this is a cancelled edit (assistant message says "The model chose to cancel the edit.")
                  is_cancelled_edit <- (!is.null(entry$content) && entry$content == "The model chose to cancel the edit.")
                  if (is_cancelled_edit) {
                     # Get the request_id from the related edit_file function call for cancelled edits too
                     related_request_id_cancelled <- NULL
                     if (!is.null(related_entry$request_id)) {
                        related_request_id_cancelled <- related_entry$request_id
                     }
                     
                     # For cancelled edits, create edit_file widget that shows cancellation message and has no buttons
                     .rs.send_ai_operation("edit_file_command", list(
                        message_id = as.numeric(entry$related_to),  # Use edit_file function call ID as widget ID
                        filename = filename_with_stats,
                        content = paste0("CANCELLED:", entry$content),  # Mark as cancelled with prefix
                        explanation = paste("Edit", basename(filename), "(cancelled)"),
                        request_id = related_request_id_cancelled  # Use the request_id from edit_file function call
                     ))
                     items_created <- items_created + 1
                  } else {
                     # Parse and clean the content to remove code block markers
                     cleaned_content <- .rs.parse_code_block_content(entry$content, filename)
                     
                     # Get the request_id from the related edit_file function call
                     related_request_id <- NULL
                     if (!is.null(related_entry$request_id)) {
                        related_request_id <- related_entry$request_id
                     }
                     
                     # Use the related_to (edit_file function call ID) as the widget ID to match streaming events
                     .rs.send_ai_operation("edit_file_command", list(
                        message_id = as.numeric(entry$related_to),  # Use related_to to match streaming
                        filename = filename_with_stats,
                        content = cleaned_content,
                        explanation = paste("Edit", basename(filename)),
                        request_id = related_request_id  # Use the request_id from edit_file function call
                     ))
                     items_created <- items_created + 1
                     
                     # CRITICAL: Check if buttons should be hidden after widget creation
                     if (.rs.should_hide_buttons_for_restored_widget(entry$related_to)) {
                        .rs.send_ai_operation("hide_widget_buttons", list(
                           message_id = as.numeric(entry$related_to),
                           content = "edit_file"  # widget_type goes in content field for Java mapping
                        ))
                     }
                  }
                  break
               }
            }
         }
         
         # Only create assistant message if it's not edit_file related
         if (!is_edit_file_related) {
            # Do NOT clean triple backticks from regular assistant messages
            # The markdown renderer will properly convert them to code blocks
            # Only edit_file content should have backticks stripped (handled in Java parseCodeBlockContent)
            
            .rs.send_ai_operation("create_assistant_message", list(
               message_id = as.numeric(entry$id),
               content = entry$content
            ))
            items_created <- items_created + 1
         }
      }
   }
   
   # Signal end of background recreation and swap to foreground
   .rs.send_ai_operation("finish_background_recreation", list(
      message_id = "recreation_finish",
      command = "",
      explanation = "Finishing background conversation recreation"
   ))
   
   return(items_created > 0)
})

.rs.addFunction("create_revert_buttons_for_user_messages", function(conversation_log) {   
   if (length(conversation_log) == 0) {
      cat("DEBUG: No conversation log entries found\n")
      return(FALSE)
   }
   
   revert_buttons_created <- 0
   
   for (i in seq_along(conversation_log)) {
      entry <- conversation_log[[i]]
      
      # Check if this entry is a user message
      if (!is.null(entry$role) && entry$role == "user" && !is.null(entry$id)) {
         .rs.send_ai_operation("revert_button", list(
            message_id = as.numeric(entry$id)
         ))
         revert_buttons_created <- revert_buttons_created + 1
      }
   }
   
   return(revert_buttons_created > 0)
})

.rs.addFunction("get_function_call_type_for_message", function(message_id, conversation_log = NULL) {
   if (is.null(conversation_log)) {
      conversation_log <- .rs.read_conversation_log()
   }
   
   message <- NULL
   for (entry in conversation_log) {
      if (!is.null(entry$id) && entry$id == message_id) {
         message <- entry
         break
      }
   }
   
   if (is.null(message)) {
      return(NULL)
   }
   
   # Case 1: The message itself is a function call
   if (!is.null(message$function_call) && !is.null(message$function_call$name)) {
      return(message$function_call$name)
   }
   
   # Case 2: The message is a function call output
   if (!is.null(message$type) && message$type == "function_call_output" && !is.null(message$call_id)) {
      for (entry in conversation_log) {
         if (!is.null(entry$function_call) && 
             !is.null(entry$function_call$call_id) &&
             entry$function_call$call_id == message$call_id) {
            return(entry$function_call$name)
         }
      }
   }
   
   # Case 3: Check if this is an assistant message related to an edit_file function call
   # Only edit_file results should be treated specially - other assistant messages should remain as text
   if (!is.null(message$role) && message$role == "assistant" && !is.null(message$related_to)) {
      # Look for edit_file function calls that this message is related to
      for (entry in conversation_log) {
         if (!is.null(entry$function_call) && 
             !is.null(entry$function_call$name) &&
             entry$function_call$name == "edit_file" &&
             !is.null(entry$id) &&
             entry$id == message$related_to) {
            return("edit_file")
         }
      }
   }
   
   return(NULL)
})

.rs.addFunction("get_filename_from_edit_file_message", function(message_id, conversation_log = NULL) {
   if (is.null(conversation_log)) {
      conversation_log <- .rs.read_conversation_log()
   }
   
   message <- NULL
   for (entry in conversation_log) {
      if (!is.null(entry$id) && entry$id == message_id) {
         message <- entry
         break
      }
   }
   
   if (is.null(message)) {
      return(NULL)
   }
   
   # This function should only work for assistant messages related to an edit_file function call
   if (!is.null(message$role) && message$role == "assistant" && !is.null(message$related_to)) {
      # Look for edit_file function calls that this message is related to
      for (entry in conversation_log) {
         if (!is.null(entry$function_call) && 
             !is.null(entry$function_call$name) &&
             entry$function_call$name == "edit_file" &&
             !is.null(entry$id) &&
             entry$id == message$related_to) {
            # Extract filename from the function call arguments
            if (!is.null(entry$function_call$arguments)) {
               tryCatch({
                  args <- jsonlite::fromJSON(entry$function_call$arguments)
                  if (!is.null(args$filename)) {
                     return(args$filename)
                  }
               }, error = function(e) {
                  stop("DEBUG: Error parsing edit_file arguments:", e$message)
               })
            }
         }
      }
   }
   
   return(NULL)
})
                           
.rs.addFunction("is_last_function_edit_file", function() {
   conversation_log <- .rs.read_conversation_log()
   
   if (is.null(conversation_log) || length(conversation_log) == 0) {
      return(FALSE)
   }
   
   # Find the most recent edit_file function call
   edit_file_entry_id <- NULL
   edit_file_entry_index <- NULL
   
   for (i in length(conversation_log):1) {
      entry <- conversation_log[[i]]
      if (!is.null(entry) && !is.null(entry$function_call) && 
          !is.null(entry$function_call$name) && 
          entry$function_call$name == "edit_file") {
         edit_file_entry_id <- entry$id
         edit_file_entry_index <- i
         break
      }
   }
   
   # If no edit_file found, return FALSE
   if (is.null(edit_file_entry_id)) {
      return(FALSE)
   }
   
   # Check if there's an assistant response related to this edit_file call
   # If so, tools should be restored (return FALSE)
   for (i in (edit_file_entry_index + 1):length(conversation_log)) {
      if (i <= length(conversation_log)) {
         entry <- conversation_log[[i]]
         if (!is.null(entry) && !is.null(entry$role) && 
             entry$role == "assistant" && 
             !is.null(entry$related_to) && 
             entry$related_to == edit_file_entry_id) {
            # Found assistant response to this edit_file - tools should be restored
            return(FALSE)
         }
      }
   }
   
   # No assistant response found for the edit_file - tools should still be restricted
   return(TRUE)
})