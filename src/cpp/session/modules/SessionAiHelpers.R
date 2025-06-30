# SessionAiHelpers.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#
#

.rs.setVar("ai_max_input", 20000)
.rs.setVar("conversation_max_tokens", 20000)

.rs.setVar("ai_in_error", FALSE)



.rs.addFunction("find_highest_conversation_index", function() {
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   
   if (!dir.exists(conversations_dir)) {
      return(1)
   }
   
   all_dirs <- list.dirs(conversations_dir, full.names = FALSE, recursive = FALSE)
   
   conversation_dirs <- grep("^conversation_[0-9]+$", all_dirs, value = TRUE)
   
   if (length(conversation_dirs) == 0) {
      return(1)
   }
   
   indices <- as.integer(gsub("conversation_", "", conversation_dirs))
   
   max_index <- max(indices)
   return(max_index)
})

tryCatch({
   highest_index <- .rs.find_highest_conversation_index()
   if (is.null(highest_index) || !is.numeric(highest_index) || highest_index < 1) {
      highest_index <- 1
   }
   .rs.setVar("current_conversation_index", highest_index)
   
   if (exists(".rs.load_conversation_variables", mode = "function")) {
      .rs.load_conversation_variables(highest_index)
   }
}, error = function(e) {
   .rs.setVar("current_conversation_index", 1)
   if (exists(".rs.initialize_conversation_defaults", mode = "function")) {
      .rs.initialize_conversation_defaults()
   }
})

.rs.setVar("message_id_counter", 0)

.rs.addFunction("get_next_message_id", function() {
   .rs.setVar("message_id_counter", .rs.getVar("message_id_counter") + 1)
   return(as.integer(.rs.getVar("message_id_counter")))
})

.rs.setVar("topics_env", new.env(parent = emptyenv()))

.rs.addFunction("json_to_str", function(obj) {
   jsonlite::toJSON(obj, auto_unbox = TRUE, pretty = TRUE, force = TRUE, na = "null", null = "null")
})

.rs.addFunction("extract_r_code_from_response", function(response, message_id) {
   return(response)
})

.rs.addFunction("exists_in_global_env", function(name) {
   exists(name, envir = .GlobalEnv)
})

.rs.addFunction("remove_from_global_env", function(name) {
   if (exists(name, envir = .GlobalEnv)) {
      rm(list = name, envir = .GlobalEnv)
   }
   return(TRUE)
})

.rs.addFunction("get_current_conversation_index", function() {
   current_conversation_index <- .rs.getVar("current_conversation_index")
   
   if (is.null(current_conversation_index)) {
      stop("No current conversation index")
   }
   
   return(current_conversation_index)
})

.rs.addFunction("set_current_conversation_index", function(index) {
   if (!is.numeric(index) || index < 1) {
      stop("Conversation index must be a positive integer")
   }
   .rs.setVar("current_conversation_index", as.integer(index))
   return(TRUE)
})

.rs.addFunction("create_new_conversation_runner", function() {
   .rs.check_required_packages()
   
   existing_indices <- .rs.list_conversation_indices()
   
   current_conversation_index <- .rs.get_current_conversation_index()
   
   # Check if the most recent conversation is blank and reuse it if so
   if (length(existing_indices) > 0) {
      most_recent_index <- max(existing_indices)
      
      # Check if most recent conversation is empty using the existing function
      if (exists(".rs.is_conversation_empty", mode = "function") && 
          .rs.is_conversation_empty(most_recent_index)) {
         # Reuse the existing blank conversation
         new_index <- most_recent_index
         .rs.setVar("current_conversation_index", new_index)
         
         # Reset the conversation state but don't store variables since we're reusing
         .rs.setVar("message_id_counter", 0)
         
         if (exists(".rs.initialize_conversation_defaults", mode = "function")) {
            .rs.initialize_conversation_defaults()
         }
         
         if (exists(".rs.reset_assistant_message_count", mode = "function")) {
            .rs.reset_assistant_message_count()
         }
         
         # Clear any existing content and reset to blank state
         paths <- .rs.get_ai_file_paths()
         
         initial_json <- list(
            messages = data.frame(
               id = integer(),
               type = character(),
               text = character(),
               timestamp = character(),
               related_to = integer(),
               stringsAsFactors = FALSE
            )
         )
         initial_log <- list()
         .rs.write_conversation_log(initial_log)
         
         empty_history <- data.frame(filename = character(), order = integer(), stringsAsFactors = FALSE)
         write.table(empty_history, paths$script_history_path, sep = "\t", row.names = FALSE, quote = FALSE)
         
         initial_changes_log <- list(changes = list())
         .rs.write_file_changes_log(initial_changes_log)
         
         default_name <- "New conversation"
         .rs.set_conversation_name(new_index, default_name)
         
         .rs.update_conversation_display()

         empty_buttons <- data.frame(
            message_id = integer(),
            buttons_run = character(),
            next_button = character(),
            on_deck_button = character(),
            stringsAsFactors = FALSE
         )
         .rs.write_message_buttons(empty_buttons)
         
         return(new_index)
      } else {
         # Most recent conversation is not empty, store its variables and create new one
         .rs.store_conversation_variables(current_conversation_index)
         new_index <- max(existing_indices) + 1
      }
   } else {
      new_index <- 1
   }
   
   .rs.setVar("current_conversation_index", new_index)
   
   .rs.setVar("message_id_counter", 0)
   
   if (exists(".rs.initialize_conversation_defaults", mode = "function")) {
      .rs.initialize_conversation_defaults()
   }
   
   if (exists(".rs.reset_assistant_message_count", mode = "function")) {
      .rs.reset_assistant_message_count()
   }
   
   paths <- .rs.get_ai_file_paths()
   
   # No need to initialize conversation.json anymore - using conversation_log.json exclusively
   
   initial_log <- list()
   .rs.write_conversation_log(initial_log)
   
   empty_history <- data.frame(filename = character(), order = integer(), stringsAsFactors = FALSE)
   write.table(empty_history, paths$script_history_path, sep = "\t", row.names = FALSE, quote = FALSE)
   
   initial_changes_log <- list(changes = list())
   .rs.write_file_changes_log(initial_changes_log)
   
   default_name <- "New conversation"
   .rs.set_conversation_name(new_index, default_name)
   
   .rs.update_conversation_display()

   empty_buttons <- data.frame(
      message_id = integer(),
      buttons_run = character(),
      next_button = character(),
      on_deck_button = character(),
      stringsAsFactors = FALSE
   )
   .rs.write_message_buttons(empty_buttons)
   
   return(new_index)
})

.rs.addFunction("list_conversation_indices", function() {
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   
   if (!dir.exists(conversations_dir)) {
      return(1)
   }
   
   all_dirs <- list.dirs(conversations_dir, full.names = FALSE, recursive = FALSE)
   
   conversation_dirs <- grep("^conversation_[0-9]+$", all_dirs, value = TRUE)
   
   indices <- as.integer(gsub("conversation_", "", conversation_dirs))
   
   if (length(indices) == 0) {
      return(1)
   }
   
   return(sort(indices))
})

.rs.addJsonRpcHandler("get_current_conversation_index", function() {
   tryCatch({
      result <- .rs.get_current_conversation_index()
      
      # Convert to a clean integer without attributes
      clean_result <- as.integer(result)
      
      return(clean_result)
   }, error = function(e) {
      stop(paste0("Error in .rs.get_current_conversation_index():", e$message))
   })
})

.rs.addFunction("compute_line_diff", function(old_lines, new_lines, is_from_edit_file = FALSE) {
   if (is.null(old_lines) || length(old_lines) == 0 || 
       (is.list(old_lines) && length(old_lines) == 0) || 
       identical(old_lines, list()) || identical(old_lines, structure(list(), names = character(0)))) {
      result <- lapply(seq_along(new_lines), function(i) {
         list(type = "added", content = new_lines[i], new_line = i, old_line = NA_integer_)
      })
      return(list(
         diff = result,
         added = length(new_lines),
         deleted = 0
      ))
   }
   if (is.null(new_lines) || length(new_lines) == 0) {
      result <- lapply(seq_along(old_lines), function(i) {
         list(type = "deleted", content = old_lines[i], old_line = i, new_line = NA_integer_)
      })
      return(list(
         diff = result,
         added = 0,
         deleted = length(old_lines)
      ))
   }
   
   m <- length(old_lines)
   n <- length(new_lines)
   
   lcs <- matrix(0, nrow = m + 1, ncol = n + 1)
   for (i in 1:m) {
      for (j in 1:n) {
         if (old_lines[i] == new_lines[j]) {
            lcs[i + 1, j + 1] <- lcs[i, j] + 1
         } else {
            lcs[i + 1, j + 1] <- max(lcs[i + 1, j], lcs[i, j + 1])
         }
      }
   }
   
   diff <- list()
   i <- m
   j <- n
   added <- 0
   deleted <- 0
   
   while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && old_lines[i] == new_lines[j]) {
         diff <- c(list(list(type = "unchanged", content = old_lines[i], old_line = i, new_line = j)), diff)
         i <- i - 1
         j <- j - 1
      } else if (j > 0 && (i == 0 || lcs[i + 1, j] >= lcs[i, j + 1])) {
         diff <- c(list(list(type = "added", content = new_lines[j], old_line = NA_integer_, new_line = j)), diff)
         j <- j - 1
         added <- added + 1
      } else if (i > 0) {
         diff <- c(list(list(type = "deleted", content = old_lines[i], old_line = i, new_line = NA_integer_)), diff)
         i <- i - 1
         deleted <- deleted + 1
      }
   }
   
   # For edit_file widgets, convert to unified diff format for proper display
   if (is_from_edit_file) {
      unified_diff <- .rs.convert_to_unified_diff_format(diff, old_lines, new_lines)
      return(list(
         diff = unified_diff,
         added = added,
         deleted = deleted
      ))
   }
   
   return(list(
      diff = diff,
      added = added,
      deleted = deleted
   ))
})

# Convert standard diff to unified diff format for proper display in ACE editor
.rs.addFunction("convert_to_unified_diff_format", function(diff, old_lines, new_lines) {
   unified_lines <- list()
   display_line_num <- 1
   
   # Group consecutive changes together for better unified diff display
   i <- 1
   while (i <= length(diff)) {
      entry <- diff[[i]]
      
      if (entry$type == "unchanged") {
         # Add unchanged line without prefix
         unified_lines[[length(unified_lines) + 1]] <- list(
            type = "unchanged",
            content = entry$content,
            display_line = display_line_num,
            old_line = entry$old_line,
            new_line = entry$new_line
         )
         display_line_num <- display_line_num + 1
         i <- i + 1
      } else {
         # Group consecutive deleted and added lines
         deleted_lines <- list()
         added_lines <- list()
         
         # Collect all consecutive deleted lines
         while (i <= length(diff) && diff[[i]]$type == "deleted") {
            deleted_lines[[length(deleted_lines) + 1]] <- diff[[i]]
            i <- i + 1
         }
         
         # Collect all consecutive added lines
         while (i <= length(diff) && diff[[i]]$type == "added") {
            added_lines[[length(added_lines) + 1]] <- diff[[i]]
            i <- i + 1
         }
         
         # Add deleted lines first (they show the original content)
         for (del_line in deleted_lines) {
            unified_lines[[length(unified_lines) + 1]] <- list(
               type = "deleted",
               content = del_line$content,
               display_line = display_line_num,
               old_line = del_line$old_line,
               new_line = NA_integer_
            )
            display_line_num <- display_line_num + 1
         }
         
         # Then add added lines (they show the new content)
         for (add_line in added_lines) {
            unified_lines[[length(unified_lines) + 1]] <- list(
               type = "added",
               content = add_line$content,
               display_line = display_line_num,
               old_line = NA_integer_,
               new_line = add_line$new_line
            )
            display_line_num <- display_line_num + 1
         }
      }
   }
   
   return(unified_lines)
})

# Conversation diff storage functions
.rs.addFunction("get_ai_base_directory", function() {
   # Get the base AI directory path where all AI-related files are stored
   return(.rs.get_ai_base_dir())
})

.rs.addFunction("get_conversation_diffs_file_path", function() {
   # Get the path to the conversation_diffs.json file using the same path as SessionAiIO.R
   paths <- .rs.get_ai_file_paths()
   return(paths$conversation_diff_log_path)
})

.rs.addFunction("read_conversation_diffs", function() {
   # Read the conversation diffs from JSON file
   diffs_file <- .rs.get_conversation_diffs_file_path()
   
   if (!file.exists(diffs_file)) {
      # Return empty structure if file doesn't exist
      return(list(diffs = list()))
   }
   
   tryCatch({
      content <- readLines(diffs_file, warn = FALSE)
      if (length(content) == 0) {
         return(list(diffs = list()))
      }
      
      # Parse JSON content
      diffs_data <- jsonlite::fromJSON(paste(content, collapse = "\n"), simplifyVector = FALSE)
      
      if (is.null(diffs_data$diffs)) {
         diffs_data$diffs <- list()
      }
      
      return(diffs_data)
   }, error = function(e) {
      cat("Error reading conversation diffs:", e$message, "\n")
      return(list(diffs = list()))
   })
})

.rs.addFunction("write_conversation_diffs", function(diffs_data) {
   # Write the conversation diffs to JSON file
   diffs_file <- .rs.get_conversation_diffs_file_path()
   
   tryCatch({
      json_content <- jsonlite::toJSON(diffs_data, auto_unbox = TRUE, pretty = TRUE)
      writeLines(json_content, diffs_file)
      return(TRUE)
   }, error = function(e) {
      cat("Error writing conversation diffs:", e$message, "\n")
      return(FALSE)
   })
})

.rs.addFunction("store_diff_data", function(message_id, diff_data, old_content = NULL, new_content = NULL, flags = NULL) {
   # Store diff data for a specific message ID
   conversation_index <- .rs.get_current_conversation_index()
   
   diffs_data <- .rs.read_conversation_diffs()
   
   # Create diff entry
   diff_entry <- list(
      message_id = as.character(message_id),
      conversation_index = conversation_index,
      timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
      diff_data = diff_data,
      old_content = old_content,
      new_content = new_content
   )
   
   # Add flags if provided
   if (!is.null(flags)) {
      diff_entry$flags <- flags
   }
   
   # Store by message ID
   diffs_data$diffs[[as.character(message_id)]] <- diff_entry
   
   # Write back to file
   .rs.write_conversation_diffs(diffs_data)
   
   return(TRUE)
})

.rs.addFunction("get_stored_diff_data", function(message_id) {
   # Retrieve diff data for a specific message ID
   diffs_data <- .rs.read_conversation_diffs()
   
   msg_id_char <- as.character(message_id)
   diff_entry <- diffs_data$diffs[[msg_id_char]]
   
   if (!is.null(diff_entry)) {
      # Clean up the diff data to ensure proper NA handling
      cleaned_diff_data <- diff_entry$diff_data
      if (!is.null(cleaned_diff_data) && length(cleaned_diff_data) > 0) {
         for (i in seq_along(cleaned_diff_data)) {
            diff_item <- cleaned_diff_data[[i]]
            
            # Fix old_line field
            if (!is.null(diff_item$old_line)) {
               if (is.list(diff_item$old_line) && length(diff_item$old_line) == 0) {
                  cleaned_diff_data[[i]]$old_line <- NA_integer_
               } else if (is.na(diff_item$old_line)) {
                  cleaned_diff_data[[i]]$old_line <- NA_integer_
               }
            }
            
            # Fix new_line field
            if (!is.null(diff_item$new_line)) {
               if (is.list(diff_item$new_line) && length(diff_item$new_line) == 0) {
                  cleaned_diff_data[[i]]$new_line <- NA_integer_
               } else if (is.na(diff_item$new_line)) {
                  cleaned_diff_data[[i]]$new_line <- NA_integer_
               }
            }
         }
      }
      
      # Return the full structure including flags
      result <- list(diff = cleaned_diff_data)
      
      # Add flags if they exist
      if (!is.null(diff_entry$flags)) {
         result$is_start_edit <- if (!is.null(diff_entry$flags$is_start_edit)) diff_entry$flags$is_start_edit else FALSE
         result$is_end_edit <- if (!is.null(diff_entry$flags$is_end_edit)) diff_entry$flags$is_end_edit else FALSE
      } else {
         result$is_start_edit <- FALSE
         result$is_end_edit <- FALSE
      }
      
      return(result)
   }
   
   return(NULL)
})

.rs.addFunction("get_conversation_tokens", function(conversation_index) {
   if (is.null(conversation_index)) {
      conversation_index <- .rs.get_current_conversation_index()
   }
   
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   token_path <- file.path(conversations_dir, paste0("conversation_", conversation_index), "token_usage.txt")
  
  if (!file.exists(token_path)) {
    return(0)
  }
  
  total_tokens <- as.numeric(readLines(token_path, warn = FALSE)[1])
  return(total_tokens)
})

# Smart content merging: when new content is shorter than existing content,
# attempts to find and replace the matching section rather than replacing entire file.
# Uses first/last line pattern matching and similarity scoring to identify the best replacement location.
.rs.addFunction("apply_unified_diff_processing", function(previous_content, new_content, file_type = NULL) {
  # Handle empty content cases
  if (is.null(previous_content) || length(previous_content) == 0 || nchar(previous_content) == 0) {
    return(list(
      previous_content = "",
      content = new_content
    ))
  }
  
  if (is.null(new_content) || length(new_content) == 0 || nchar(new_content) == 0) {
    return(list(
      previous_content = previous_content,
      content = ""
    ))
  }
  
  previous_lines <- strsplit(previous_content, "\n")[[1]]
  current_lines <- strsplit(new_content, "\n")[[1]]
  
  is_rmd <- FALSE
  if (!is.null(file_type) && (file_type == "rmd" || tolower(tools::file_ext(file_type)) == "rmd")) {
    is_rmd <- TRUE
  }
  
  # Smart replacement: only attempt when new content is shorter (likely a section replacement)
  # COMMENTED OUT: Direct replacement - use provided new content as-is
  # if (length(current_lines) < length(previous_lines) && length(current_lines) > 2) {
  #   # Use first and last lines as boundary markers to find matching section
  #   first_line <- current_lines[1]
  #   last_line <- current_lines[length(current_lines)]
  #   
  #   search_start_idx <- 1
  #   if (is_rmd) {
  #     if (is_rmd) {
  #       search_start_idx <- 1
  #     }
  #   }
  #   
  #   # Escape regex special characters for exact line matching
  #   escaped_first_line <- gsub("([\\(\\)\\[\\]\\{\\}\\+\\*\\?\\^\\$\\|\\.\\\\])", "\\\\\\1", first_line)
  #   escaped_last_line <- gsub("([\\(\\)\\[\\]\\{\\}\\+\\*\\?\\^\\$\\|\\.\\\\])", "\\\\\\1", last_line)
  #   
  #   # Find all occurrences of the first line in the existing content
  #   exact_first_line_matches <- grep(paste0("^", escaped_first_line, "$"), 
  #                                previous_lines[search_start_idx:length(previous_lines)], perl = TRUE)
  #   
  #   if (length(exact_first_line_matches) > 0) {
  #     exact_first_line_matches <- exact_first_line_matches + search_start_idx - 1
  #   }
  #   
  #   # Track best matching section using similarity scoring
  #   partial_match_found <- FALSE
  #   best_match_start_idx <- NULL
  #   best_match_end_idx <- NULL
  #   best_match_score <- 0
  #   
  #   if (length(exact_first_line_matches) > 0) {
  #     # For each potential start position, look for matching end position
  #     for (start_idx in exact_first_line_matches) {
  #       remaining_lines <- previous_lines[(start_idx+1):length(previous_lines)]
  #       
  #       exact_last_line_matches <- grep(paste0("^", escaped_last_line, "$"), 
  #                              remaining_lines, perl = TRUE)
  #       
  #       if (length(exact_last_line_matches) > 0) {
  #         for (last_match_idx in exact_last_line_matches) {
  #           end_idx <- start_idx + last_match_idx
  #           
  #           section_length <- end_idx - start_idx + 1
  #           
  #           # Only consider sections that are large enough and not at file boundaries
  #           if (section_length >= length(current_lines) && 
  #               (start_idx > search_start_idx || end_idx < length(previous_lines))) {
  #             
  #             original_section <- previous_lines[start_idx:end_idx]
  #             
  #             # Calculate similarity score based on content overlap
  #             content_similarity_score <- 0
  #             
  #             if (length(current_lines) >= 2 && length(original_section) >= 2) {
  #               if (current_lines[2] == original_section[2]) {
  #                 content_similarity_score <- content_similarity_score + 20
  #               } else {
  #                 max_chars <- max(nchar(current_lines[2]), nchar(original_section[2]))
  #                 if (max_chars > 0) {
  #                   second_line_similarity <- sum(strsplit(current_lines[2], "")[[1]] %in% 
  #                                              strsplit(original_section[2], "")[[1]]) / max_chars
  #                   if (!is.na(second_line_similarity)) {
  #                     content_similarity_score <- content_similarity_score + (second_line_similarity * 15)
  #                   }
  #                 }
  #               }
  #             }
  #             
  #             if (length(current_lines) >= 3 && length(original_section) >= 3) {
  #               min_lines <- min(4, min(length(current_lines), length(original_section)))
  #               for (i in 3:min_lines) {
  #                 max_chars <- max(nchar(current_lines[i]), nchar(original_section[i]))
  #                 if (max_chars > 0) {
  #                   line_similarity <- sum(strsplit(current_lines[i], "")[[1]] %in% 
  #                                        strsplit(original_section[i], "")[[1]]) / max_chars
  #                   if (!is.na(line_similarity)) {
  #                     content_similarity_score <- content_similarity_score + (line_similarity * 5)
  #                   }
  #                 }
  #               }
  #             }
  #             
  #             section_text <- paste(original_section, collapse = " ")
  #             new_content_text <- paste(current_lines, collapse = " ")
  #             section_text_len <- nchar(section_text)
  #             
  #             context_score <- 0
  #             if (section_text_len > 0) {
  #               context_score <- sum(strsplit(section_text, "")[[1]] %in% 
  #                                  strsplit(new_content_text, "")[[1]]) / section_text_len
  #               if (is.na(context_score)) {
  #                 context_score <- 0
  #               }
  #             }
  #             
  #             match_score <- content_similarity_score + (context_score * 30)
  #             
  #             # Keep track of the best matching section - ensure no NA values
  #             if (!is.na(match_score) && !is.na(best_match_score) && match_score > best_match_score) {
  #               best_match_score <- match_score
  #               best_match_start_idx <- start_idx
  #               best_match_end_idx <- end_idx
  #             }
  #           }
  #         }
  #       }
  #     }
  #     
  #     # If found a good match (score > 10), replace that section
  #     if (!is.null(best_match_start_idx) && !is.null(best_match_end_idx) && best_match_score > 10) {
  #       new_content_array <- c(
  #         previous_lines[1:(best_match_start_idx - 1)],
  #         current_lines,
  #         previous_lines[(best_match_end_idx + 1):length(previous_lines)]
  #       )
  #       
  #       current_lines <- new_content_array
  #       new_content <- paste(current_lines, collapse = "\n")
  #       partial_match_found <- TRUE
  #     }
  #   }
  # }
    
  # Normalize newline handling between old and new content
  tryCatch({
    if (!is.character(previous_content)) {
      previous_content <- as.character(previous_content)
    }
    if (!is.character(new_content)) {
      new_content <- as.character(new_content)
    }
    ends_with_newline_prev <- FALSE
    if (is.character(previous_content) && length(previous_content) > 0) {
      ends_with_newline_prev <- grepl("\n$", previous_content)
      if (is.na(ends_with_newline_prev)) ends_with_newline_prev <- FALSE
    }
    
    ends_with_newline_new <- FALSE
    if (is.character(new_content) && length(new_content) > 0) {
      ends_with_newline_new <- grepl("\n$", new_content)
      if (is.na(ends_with_newline_new)) ends_with_newline_new <- FALSE
    }
    
    if (ends_with_newline_prev && !ends_with_newline_new) {
      new_content <- paste0(new_content, "\n")
    } else if (!ends_with_newline_prev && ends_with_newline_new) {
      new_content <- sub("\n$", "", new_content)
    }
  }, error = function(e) {
  })
  return(list(
    previous_content = previous_content,
    content = new_content
  ))
})



.rs.addFunction("get_conversation_diff", function(msg_id) {
   diff_log <- .rs.read_conversation_diff_log()
   msg_id_char <- as.character(msg_id)
   return(diff_log$diffs_by_msg_id[[msg_id_char]])
})

.rs.addFunction("clear_conversation_diff_log", function() {
   paths <- .rs.get_ai_file_paths()
   
   initial_diff_log <- list(diffs_by_msg_id = list())
   writeLines(jsonlite::toJSON(initial_diff_log, auto_unbox = TRUE, pretty = TRUE), paths$conversation_diff_log_path)
   
   return(TRUE)
})

.rs.addFunction("get_file_name_for_message_id", function(message_id, for_display = FALSE) {
   conversation_log <- .rs.read_conversation_log()
   target_message <- NULL
   
   # Find the target message
   for (i in seq_along(conversation_log)) {
      if (conversation_log[[i]]$id == message_id) {
         target_message <- conversation_log[[i]]
         break
      }
   }
   
   if (is.null(target_message)) {
      return("Unknown")
   }
   
   # Check if it's a message with related_to pointing to an edit_file function call
   if (!is.null(target_message$related_to)) {
      for (i in seq_along(conversation_log)) {
         if (!is.null(conversation_log[[i]]$function_call) && 
             !is.null(conversation_log[[i]]$id) &&
             conversation_log[[i]]$id == target_message$related_to &&
             !is.null(conversation_log[[i]]$function_call$name) &&
             conversation_log[[i]]$function_call$name == "edit_file") {
            
              args <- if (is.character(conversation_log[[i]]$function_call$arguments)) {
                jsonlite::fromJSON(conversation_log[[i]]$function_call$arguments, simplifyVector = FALSE)
              } else {
                conversation_log[[i]]$function_call$arguments
              }
              
              if (!is.null(args$filename)) {
                filename <- if (for_display) basename(args$filename) else args$filename
                return(filename)
              }
         }
      }
   }
   
   # Check if it's a function_call for run_console_cmd, run_terminal_cmd, delete_file, or run_file
   if (!is.null(target_message$function_call) && 
       !is.null(target_message$function_call$name)) {
      
      function_name <- target_message$function_call$name
      
      if (function_name == "run_console_cmd") {
         return("Console")
      } else if (function_name == "run_terminal_cmd") {
         return("Terminal")
      } else if (function_name == "delete_file") {
         return("Delete file")
      } else if (function_name == "run_file") {
         # Extract arguments to create custom title
         args <- tryCatch({
            if (is.character(target_message$function_call$arguments)) {
               jsonlite::fromJSON(target_message$function_call$arguments, simplifyVector = FALSE)
            } else {
               target_message$function_call$arguments
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
   
   return("Script")
})

.rs.addFunction("clear_file_changes_log", function() {
   paths <- .rs.get_ai_file_paths()
   
   initial_changes_log <- list(changes = list())
   writeLines(jsonlite::toJSON(initial_changes_log, auto_unbox = TRUE, pretty = TRUE), paths$diff_log_path)
   
   return(TRUE)
})

.rs.addFunction("limit_output_text", function(output_text) {
    if (!is.character(output_text)) {
        output_text <- as.character(output_text)
    }
    
    total_length <- sum(nchar(output_text))
    
    # Check if we need to truncate based on total length
    if (total_length > 4000) {
        # First, limit the number of lines if necessary
        if (length(output_text) > 20) {
            output_text <- output_text[1:20]
            output_text <- c(output_text, "... (output truncated)")
        }
    }
    
    # Truncate individual lines that are too long
    output_text <- vapply(output_text, function(line) {
        if (nchar(line) > 200) {
            paste0(substr(line, 1, 197), "...")
        } else {
            line
        }
    }, character(1), USE.NAMES = FALSE)
    
    return(output_text)
})

.rs.addFunction("check_required_packages", function(pkgs = c("httr2", "httr", "jsonlite", "curl", "commonmark", "htmltools", "base64enc", "processx", "callr")) {
  installed <- vapply(pkgs, function(pkg) {
     location <- find.package(pkg, quiet = TRUE)
     length(location) > 0
  }, FUN.VALUE = logical(1))
  
  missing <- pkgs[!installed]
  if (length(missing) > 0) {
     title <- "Install Required Packages"
     message <- paste(
        "The following packages are required for AI features and will be installed. This may take a few seconds. Please wait until they are installed:\n",
        paste("-", missing),
        "\nWould you like to proceed?",
        sep = "\n"
     )
     
     ok <- .rs.api.showQuestion(title, message)
     if (!ok) {
        stop("Cannot proceed with AI request; required dependencies not installed", call. = FALSE)
     }
     call <- substitute(
        install.packages(missing),
        list(missing = missing)
     )
     
     writeLines(paste(getOption("prompt"), format(call), sep = ""))
     
     tryCatch({
        suppressWarnings(
          utils::install.packages(missing)
        )
     }, error = function(e) {
        if (grepl("Updating loaded packages", e$message)) {
        } else {
           stop(e$message)
        }
     })
  }
  return(TRUE)
})

.rs.addFunction("extract_packages_from_rmd", function(content) {
  packages <- character(0)
  lines <- character(0)
  
  if (is.character(content) && length(content) == 1) {
    if (grepl("\n", content)) {
      lines <- strsplit(content, "\n")[[1]]
    } else if (file.exists(content)) {
      lines <- readLines(content, warn = FALSE)
    } else {
      lines <- content
    }
  } else {
    return(character(0))
  }
  if (length(lines) == 0) {
    return(character(0))
  }
  
  in_r_chunk <- FALSE
  
  chunk_start_pattern <- "^\\s*```+\\s*\\{[rR].*\\}\\s*$"
  chunk_end_pattern <- "^\\s*```+\\s*$"
  library_pattern <- "^\\s*library\\s*\\(\\s*[\"\']?([A-Za-z0-9\\.]+)[\"\']?\\s*[,\\)]"
  require_pattern <- "^\\s*require\\s*\\(\\s*[\"\']?([A-Za-z0-9\\.]+)[\"\']?\\s*[,\\)]"
  namespace_pattern <- "([A-Za-z0-9\\.]+)(:::|::)"
  for (i in seq_along(lines)) {
    line <- lines[i]
    
    if (!in_r_chunk && grepl(chunk_start_pattern, line, perl = TRUE)) {
      in_r_chunk <- TRUE
    } 
    else if (in_r_chunk && grepl(chunk_end_pattern, line, perl = TRUE)) {
      in_r_chunk <- FALSE
    }
    else {
      library_matches <- regmatches(line, gregexpr(library_pattern, line, perl = TRUE))[[1]]
      if (length(library_matches) > 0) {
        for (match in library_matches) {
          pkg <- gsub(library_pattern, "\\1", match, perl = TRUE)
          packages <- c(packages, pkg)
        }
      }
      

      require_matches <- regmatches(line, gregexpr(require_pattern, line, perl = TRUE))[[1]]
      if (length(require_matches) > 0) {
        for (match in require_matches) {
          pkg <- gsub(require_pattern, "\\1", match, perl = TRUE)
          packages <- c(packages, pkg)
        }
      }
      

      namespace_matches <- regmatches(line, gregexpr(namespace_pattern, line, perl = TRUE))[[1]]
      if (length(namespace_matches) > 0) {
        for (match in namespace_matches) {
          pkg <- gsub(namespace_pattern, "\\1", match, perl = TRUE)
          if (pkg != "base" && pkg != "stats" && pkg != "utils" && 
              pkg != "graphics" && pkg != "grDevices" && pkg != "methods") {
            packages <- c(packages, pkg)
          }
        }
      }
    }
  }
  
  packages <- unique(packages)
  packages <- sort(packages)
  
  return(packages)
})



.rs.addFunction("extract_packages_from_r_script", function(content) {
  packages <- character(0)
  lines <- character(0)
  if (is.character(content) && length(content) == 1) {
    if (grepl("\n", content)) {
      lines <- strsplit(content, "\n")[[1]]
    } else if (file.exists(content)) {
      lines <- readLines(content, warn = FALSE)
    } else {
      lines <- content
    }
  } else {
    return(character(0))
  }
  
  if (length(lines) == 0) {
    return(character(0))
  }
  
  library_pattern <- "^\\s*library\\s*\\(\\s*[\"\']?([A-Za-z0-9\\.]+)[\"\']?\\s*[,\\)]"
  require_pattern <- "^\\s*require\\s*\\(\\s*[\"\']?([A-Za-z0-9\\.]+)[\"\']?\\s*[,\\)]"
  namespace_pattern <- "([A-Za-z0-9\\.]+)(:::|::)"
  for (i in seq_along(lines)) {
    line <- lines[i]
    
    library_matches <- regmatches(line, gregexpr(library_pattern, line, perl = TRUE))[[1]]
    if (length(library_matches) > 0) {
      for (match in library_matches) {
        pkg <- gsub(library_pattern, "\\1", match, perl = TRUE)
        packages <- c(packages, pkg)
      }
    }
    
    require_matches <- regmatches(line, gregexpr(require_pattern, line, perl = TRUE))[[1]]
    if (length(require_matches) > 0) {
      for (match in require_matches) {
        pkg <- gsub(require_pattern, "\\1", match, perl = TRUE)
        packages <- c(packages, pkg)
      }
    }
    namespace_matches <- regmatches(line, gregexpr(namespace_pattern, line, perl = TRUE))[[1]]
    if (length(namespace_matches) > 0) {
      for (match in namespace_matches) {
        pkg <- gsub(namespace_pattern, "\\1", match, perl = TRUE)
        if (pkg != "base" && pkg != "stats" && pkg != "utils" && 
            pkg != "graphics" && pkg != "grDevices" && pkg != "methods") {
          packages <- c(packages, pkg)
        }
      }
    }
  }
  
  packages <- unique(packages)
  packages <- sort(packages)
  
  return(packages)
})

.rs.addFunction("check_package_dependencies", function(content, type = NULL) {
  if (is.null(type)) {
    if (is.character(content) && length(content) == 1) {
      if (file.exists(content)) {
        if (grepl("\\.Rmd$|\\.rmd$", content, ignore.case = TRUE)) {
          type <- "rmd"
        } else {
          type <- "r"
        }
              } else if (grepl("\n", content)) {
          type <- "r"
      }
    }
  }
  
  if (type == "rmd") {
    packages <- .rs.extract_packages_from_rmd(content)
  } else {
    packages <- .rs.extract_packages_from_r_script(content)
  }
  
  if (length(packages) == 0) {
    return(TRUE)
  }
  
  installed <- vapply(packages, function(pkg) {
    location <- find.package(pkg, quiet = TRUE)
    length(location) > 0
  }, FUN.VALUE = logical(1))
  
  missing <- packages[!installed]
  if (length(missing) == 0) {
    return(TRUE)
  }
  
  title <- "Missing R Packages"
  message <- paste(
    paste0("The following packages required by this ", if(type == "rmd") "R Markdown document" else "R code", " are not installed:"),
    paste("-", missing, collapse = "\n"),
    paste0("\n\nWould you like to install these packages before ", if(type == "rmd") "knitting?" else "running the code?"),
    sep = "\n"
  )
  
  ok <- .rs.api.showQuestion(title, message)
  
  if (!ok) {
    return(FALSE)
  }
  
  has_bioc_manager <- length(find.package("BiocManager", quiet = TRUE)) > 0
  
  if (!has_bioc_manager) {
    cran_pkgs <- tryCatch({
      rownames(available.packages(repos = getOption("repos")))
    }, error = function(e) {
      warning("Could not retrieve CRAN package list: ", e$message)
      character(0)
    })
    
    bioc_needed <- FALSE
    if (length(cran_pkgs) > 0) {
      not_cran <- missing[!missing %in% cran_pkgs]
      if (length(not_cran) > 0) {
        bioc_needed <- TRUE
      }
          } else {
        bioc_needed <- TRUE
    }
    
    if (bioc_needed) {
      writeLines("Installing BiocManager package")
      
      tryCatch({
        utils::install.packages("BiocManager")
        has_bioc_manager <- length(find.package("BiocManager", quiet = TRUE)) > 0
      }, error = function(e) {
        warning("Error installing BiocManager: ", e$message)
        has_bioc_manager <- FALSE
      })
    }
  }
  
  cran_pkgs <- tryCatch({
    rownames(available.packages(repos = getOption("repos")))
  }, error = function(e) {
    warning("Could not retrieve CRAN package list: ", e$message)
    character(0)
  })
  
  installable <- missing
  bioc_pkgs <- character(0)
  
  if (has_bioc_manager && length(cran_pkgs) > 0) {
    not_cran <- missing[!missing %in% cran_pkgs]
    
    if (length(not_cran) > 0) {
      bioc_pkgs <- not_cran
      installable <- missing[missing %in% cran_pkgs]
    }
  }
  
  if (length(installable) > 0) {
    writeLines(paste("Installing packages from CRAN:", paste(installable, collapse = ", ")))
    
    tryCatch({
      utils::install.packages(installable)
    }, error = function(e) {
      warning("Error installing packages: ", e$message)
    })
  }
  
  if (length(bioc_pkgs) > 0 && has_bioc_manager) {
    writeLines(paste("Installing packages from BioConductor:", paste(bioc_pkgs, collapse = ", ")))
    
    tryCatch({
      BiocManager::install(bioc_pkgs)
    }, error = function(e) {
      warning("Error installing BioConductor packages: ", e$message)
    })
  }
  
  still_missing <- vapply(missing, function(pkg) {
    location <- find.package(pkg, quiet = TRUE)
    length(location) == 0
  }, FUN.VALUE = logical(1))
  
  if (any(still_missing)) {
    warning("Some packages could not be installed: ", paste(missing[still_missing], collapse = ", "))
    return(FALSE)
  }
  
  return(TRUE)
})

.rs.addFunction("get_original_content_for_diff", function(message_id) {
   # Get the conversation diff log to find original content
   diff_log <- .rs.read_conversation_diff_log()
   msg_id_char <- as.character(message_id)
   
   # Look for the diff entry for this message ID
   diff_entry <- diff_log$diffs_by_msg_id[[msg_id_char]]
   
   if (!is.null(diff_entry) && !is.null(diff_entry$previous_content)) {
      return(diff_entry$previous_content)
   }
   
   # If no diff entry found, try to get original content from file
   filename <- .rs.get_file_name_for_message_id(message_id)
   
   if (!is.null(filename) && filename != "" && !is.na(filename) && filename != "Script") {
      file_path <- if (startsWith(filename, "/") || grepl("^[A-Za-z]:", filename)) {
         filename
      } else {
         file.path(getwd(), filename)
      }
      
      # Use get_effective_file_content to get content from editor if open, otherwise from disk
      original_content <- .rs.get_effective_file_content(file_path)
      if (!is.null(original_content)) {
            return(original_content)
      }
   }
   
   # Return empty string if no original content found
   return("")
})

.rs.addJsonRpcHandler("get_original_content_for_diff", function(message_id) {
   result <- .rs.get_original_content_for_diff(message_id)
   return(result)
})

.rs.addFunction("get_diff_data_for_edit_file", function(message_id) {
   # Get pre-computed diff data for edit_file widget highlighting
   
   tryCatch({
      # First check if this is actually a cancel_edit function call
      conversation_log <- .rs.read_conversation_log()
      if (!is.null(conversation_log) && length(conversation_log) > 0) {
         for (entry in conversation_log) {
            if (!is.null(entry$id) && entry$id == message_id && 
                !is.null(entry$function_call) && !is.null(entry$function_call$name) &&
                entry$function_call$name == "cancel_edit") {
               # This is a cancel_edit call, return empty diff structure
               return(list(
                  diff = list(),
                  is_start_edit = FALSE,
                  is_end_edit = FALSE,
                  is_insert_mode = FALSE,
                  is_line_range_mode = FALSE,
                  start_line = NULL,
                  end_line = NULL,
                  insert_line = NULL,
                  added = 0L,
                  deleted = 0L,
                  filename_with_stats = "Edit cancelled"
               ))
            }
         }
      }
      
      # First check if we have stored diff data
      stored_diff <- .rs.get_stored_diff_data(message_id)
      if (!is.null(stored_diff)) {
         
         # Get the edit_file entry to extract filename for stats
         edit_file_entry <- NULL
         conversation_log <- .rs.read_conversation_log()
         for (entry in conversation_log) {
            if (!is.null(entry$id) && entry$id == message_id && 
                !is.null(entry$function_call) && !is.null(entry$function_call$name) &&
                entry$function_call$name == "edit_file") {
               edit_file_entry <- entry
               break
            }
         }
         
         # Calculate missing fields
         added_count <- 0
         deleted_count <- 0
         if (!is.null(stored_diff$diff)) {
            for (diff_item in stored_diff$diff) {
               if (!is.null(diff_item$type)) {
                  if (diff_item$type == "added") {
                     added_count <- added_count + 1
                  } else if (diff_item$type == "deleted") {
                     deleted_count <- deleted_count + 1
                  }
               }
            }
         }
         
         # Format filename with diff stats
         filename_with_stats <- "unknown"
         if (!is.null(edit_file_entry)) {
            args <- tryCatch({
               if (is.character(edit_file_entry$function_call$arguments)) {
                  jsonlite::fromJSON(edit_file_entry$function_call$arguments, simplifyVector = FALSE)
               } else {
                  edit_file_entry$function_call$arguments
               }
            }, error = function(e) {
               return(NULL)
            })
            
            if (!is.null(args) && !is.null(args$filename)) {
               filename_with_stats <- basename(args$filename)
               # Format diff stats using the same logic as conversation history loading
               if (added_count > 0 || deleted_count > 0) {
                  # Format diff stats with CSS classes for proper styling
                  addition_text <- paste0('<span class="addition">+', added_count, '</span>')
                  removal_text <- paste0('<span class="removal">-', deleted_count, '</span>')
                  diff_text <- paste(addition_text, removal_text)
                  # Return filename with diff stats in a span that can be styled
                  filename_with_stats <- paste0(filename_with_stats, ' <span class="diff-stats">', diff_text, '</span>')
               }
            }
         }
         
         # Return complete structure
         result <- list(
            diff = stored_diff$diff,
            is_start_edit = if (!is.null(stored_diff$is_start_edit)) as.logical(stored_diff$is_start_edit) else FALSE,
            is_end_edit = if (!is.null(stored_diff$is_end_edit)) as.logical(stored_diff$is_end_edit) else FALSE,
            is_insert_mode = if (!is.null(stored_diff$is_insert_mode)) as.logical(stored_diff$is_insert_mode) else FALSE,
            is_line_range_mode = if (!is.null(stored_diff$is_line_range_mode)) as.logical(stored_diff$is_line_range_mode) else FALSE,
            start_line = stored_diff$start_line,
            end_line = stored_diff$end_line,
            insert_line = stored_diff$insert_line,
            added = as.integer(added_count),
            deleted = as.integer(deleted_count),
            filename_with_stats = filename_with_stats
         )
         
         return(result)
      }
      
      # If no stored data, compute it fresh
      
      # Find the assistant message and related edit_file function call
      conversation_log <- .rs.read_conversation_log()
      if (is.null(conversation_log) || length(conversation_log) == 0) {
         return(list(diff = list()))
      }
      
      # The message_id passed in is the edit_file function call ID
      # Find the assistant message that's related to this edit_file call
      assistant_message <- NULL
      for (entry in conversation_log) {
         if (!is.null(entry$related_to) && entry$related_to == message_id && 
             !is.null(entry$role) && entry$role == "assistant") {
            assistant_message <- entry
            break
         }
      }
      
      if (is.null(assistant_message)) {
         return(list(diff = list()))
      }
      
      # Find the edit_file function call
      edit_file_entry <- NULL
      for (entry in conversation_log) {
         if (!is.null(entry$id) && entry$id == message_id && 
             !is.null(entry$function_call) && !is.null(entry$function_call$name) &&
             entry$function_call$name == "edit_file") {
            edit_file_entry <- entry
            break
         }
      }
      
      if (is.null(edit_file_entry)) {
         cat("DEBUG: No edit_file entry found with message_id:", message_id, "\n")
         return(list(diff = list()))
      }
      
      # Extract arguments from edit_file call
      args <- tryCatch({
         if (is.character(edit_file_entry$function_call$arguments)) {
            jsonlite::fromJSON(edit_file_entry$function_call$arguments, simplifyVector = FALSE)
         } else {
            edit_file_entry$function_call$arguments
         }
      }, error = function(e) {
         cat("DEBUG get_diff_data_for_edit_file: Error parsing arguments:", e$message, "\n")
         return(NULL)
      })
      
      if (is.null(args) || is.null(args$filename)) {
         cat("DEBUG: No filename found in edit_file arguments\n")
         return(list(diff = list()))
      }
      
      # Get the new content from the assistant message
      new_content <- assistant_message$content
      if (is.null(new_content)) {
         cat("DEBUG: No content found in assistant message\n")
         return(list(diff = list()))
      }
      
      # Parse the code block content to get the actual file content
      cleaned_content <- .rs.parse_code_block_content(new_content, args$filename)
      
      # Get the previous content (what existed before the edit)
      # Handle different edit modes based on line parameters
      previous_content <- ""
      
      # Check for line range parameters
      start_line <- args$start_line
      end_line <- args$end_line
      insert_line <- args$insert_line
      
      # Check if this is a keyword-based edit (not "start" or "end")
      # Also exclude cases where keyword is just the filename (common for new file creation)
      is_keyword_edit <- !is.null(args$keyword) && args$keyword != "start" && args$keyword != "end" && 
                        args$keyword != args$filename && args$keyword != basename(args$filename)
      
      # Handle different edit modes for previous content extraction
      if (!is.null(insert_line)) {
         previous_content <- ""
      } else if (!is.null(start_line) && !is.null(end_line)) {
         # First try to find the function_call_output that corresponds to this edit_file call
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
         
         # function_call_output always exists for edit_file calls, so this should never be null
         if (is.null(function_output) || is.null(function_output$output)) {
            stop("ERROR: function_call_output missing for edit_file call")
         }
         
         previous_content <- function_output$output
         # Use the line numbers from the function_call_output if they exist and are valid
         if (!is.null(function_output$start_line) && !is.null(function_output$end_line)) {
            start_line <- function_output$start_line
            end_line <- function_output$end_line
         }
      } else if (is_keyword_edit) {
         # Find the function_call_output that corresponds to this edit_file call
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
            previous_content <- function_output$output
            # Use the line numbers from the function_call_output if they exist and are valid
            if (!is.null(function_output$start_line) && !is.null(function_output$end_line)) {
               start_line <- function_output$start_line
               end_line <- function_output$end_line
            }
         } else {
            cat("DEBUG: No function_call_output found, falling back to empty content\n")
            previous_content <- ""
         }
      } else {
         # For non-keyword edits (start/end/filename), use the entire file content
         if (!is.null(args$filename)) {
            file_path <- if (startsWith(args$filename, "/") || startsWith(args$filename, "~") || grepl("^[A-Za-z]:", args$filename)) {
               args$filename
            } else {
               file.path(getwd(), args$filename)
            }
            
            # Use get_effective_file_content to get content from editor if open, otherwise from disk
            previous_content <- .rs.get_effective_file_content(file_path)
            if (is.null(previous_content)) {
               previous_content <- ""
            }
         }
      }
      
      # Split content into lines for diff calculation
      old_lines <- if (nchar(previous_content) > 0) {
         strsplit(previous_content, "\n", fixed = TRUE)[[1]]
      } else {
         character(0)
      }
      
      new_lines <- if (nchar(cleaned_content) > 0) {
         strsplit(cleaned_content, "\n", fixed = TRUE)[[1]]
      } else {
         character(0)
      }
      
      # Check if this is a start or end edit, or handle insertion/line range modes
      is_start_edit <- FALSE
      is_end_edit <- FALSE
      is_insert_mode <- !is.null(insert_line) && !is.na(insert_line)
      is_line_range_mode <- !is.null(start_line) && !is.null(end_line) && !is.na(start_line) && !is.na(end_line) && length(start_line) > 0 && length(end_line) > 0
      
      if (!is.null(args$keyword)) {
         if (args$keyword == "start") {
            is_start_edit <- TRUE
         } else if (args$keyword == "end") {
            is_end_edit <- TRUE
         }
      }
      
      # Create special diff structure for start/end edits, insertion mode, and line range mode
      if (is_start_edit || is_end_edit || is_insert_mode || is_line_range_mode) {
         diff_entries <- list()
         
         # Calculate the correct line numbers for the final file
         if (is_start_edit) {
            # For start edits: new lines go at the beginning (lines 1, 2, 3, ...)
            for (i in seq_along(new_lines)) {
               diff_entries[[length(diff_entries) + 1]] <- list(
                  type = "added",
                  content = new_lines[i],
                  old_line = NA,
                  new_line = i
               )
            }
         } else if (is_end_edit) {
            # For end edits: new lines go after existing content (existing_count + 1, existing_count + 2, ...)
            existing_line_count <- length(old_lines)
            for (i in seq_along(new_lines)) {
               diff_entries[[length(diff_entries) + 1]] <- list(
                  type = "added",
                  content = new_lines[i],
                  old_line = NA,
                  new_line = existing_line_count + i
               )
            }
         } else if (is_insert_mode) {
            # For insertion mode: new lines go after insert_line
            for (i in seq_along(new_lines)) {
               # The new lines should start at insert_line + 1, insert_line + 2, etc.
               new_line_number <- insert_line + i
               
               diff_entries[[length(diff_entries) + 1]] <- list(
                  type = "added",
                  content = new_lines[i],
                  old_line = NA,
                  new_line = new_line_number
               )
            }
         } else if (is_line_range_mode) {
            # For line range mode: compute proper diff between old and new content
            # but adjust line numbers to reflect actual file positions
            range_diff_result <- .rs.compute_line_diff(old_lines, new_lines, is_from_edit_file = TRUE)
            
            # Adjust the line numbers in the diff result to reflect actual file positions
            for (i in seq_along(range_diff_result$diff)) {
               diff_line <- range_diff_result$diff[[i]]
               
               # Adjust old_line and new_line to reflect actual file positions
               if (!is.null(diff_line$old_line) && !is.na(diff_line$old_line)) {
                  diff_line$old_line <- start_line + diff_line$old_line - 1
               }
               if (!is.null(diff_line$new_line) && !is.na(diff_line$new_line)) {
                  diff_line$new_line <- start_line + diff_line$new_line - 1
               }
               
               diff_entries[[length(diff_entries) + 1]] <- diff_line
            }
         }

         # Store the computed diff data for future use with special flags
         flags <- list(
            is_start_edit = is_start_edit,
            is_end_edit = is_end_edit,
            is_insert_mode = is_insert_mode,
            is_line_range_mode = is_line_range_mode,
            start_line = start_line,
            end_line = end_line,
            insert_line = insert_line
         )
         .rs.store_diff_data(message_id, diff_entries, previous_content, cleaned_content, flags)
         
         # Calculate diff stats  
         if (is_line_range_mode) {
            # For line range mode, use the diff stats from the computed diff
            added_count <- range_diff_result$added
            deleted_count <- range_diff_result$deleted
         } else {
            # For other modes, count the diff entries
            added_count <- length(which(sapply(diff_entries, function(x) x$type == "added")))
            deleted_count <- length(which(sapply(diff_entries, function(x) x$type == "deleted")))
         }
         
         # Format filename with diff stats using the same logic as conversation history loading
         filename_with_stats <- basename(args$filename)
         if (added_count > 0 || deleted_count > 0) {
            # Format diff stats with CSS classes for proper styling
            addition_text <- paste0('<span class="addition">+', added_count, '</span>')
            removal_text <- paste0('<span class="removal">-', deleted_count, '</span>')
            diff_text <- paste(addition_text, removal_text)
            # Return filename with diff stats in a span that can be styled
            filename_with_stats <- paste0(filename_with_stats, ' <span class="diff-stats">', diff_text, '</span>')
         }
         
         # Return the special diff structure with flags and stats (same format as regular edits)
         result_start_end <- list(
            diff = diff_entries,
            is_start_edit = as.logical(is_start_edit),
            is_end_edit = as.logical(is_end_edit),
            is_insert_mode = as.logical(is_insert_mode),
            is_line_range_mode = as.logical(is_line_range_mode),
            start_line = start_line,
            end_line = end_line,
            insert_line = insert_line,
            added = as.integer(added_count),
            deleted = as.integer(deleted_count),
            filename_with_stats = filename_with_stats
         )
         return(result_start_end)
         
      } else {
         # Regular diff calculation for normal edits (including keyword-based edits)
         diff_result <- .rs.compute_line_diff(old_lines, new_lines, is_from_edit_file = TRUE)
         
         # Store the computed diff data for future use
         .rs.store_diff_data(message_id, diff_result$diff, previous_content, cleaned_content)
         
         # Format filename with diff stats using the same logic as conversation history loading
         filename_with_stats <- basename(args$filename)
         if (diff_result$added > 0 || diff_result$deleted > 0) {
            # Format diff stats with CSS classes for proper styling
            addition_text <- paste0('<span class="addition">+', diff_result$added, '</span>')
            removal_text <- paste0('<span class="removal">-', diff_result$deleted, '</span>')
            diff_text <- paste(addition_text, removal_text)
            # Return filename with diff stats in a span that can be styled
            filename_with_stats <- paste0(filename_with_stats, ' <span class="diff-stats">', diff_text, '</span>')
         }
         # Return both diff array and formatted filename with diff stats for streaming
         result <- list(
            diff = diff_result$diff,
            is_start_edit = FALSE,
            is_end_edit = FALSE,
            is_insert_mode = FALSE,
            is_line_range_mode = FALSE,
            start_line = start_line,
            end_line = end_line,
            insert_line = NULL,
            added = as.integer(diff_result$added),
            deleted = as.integer(diff_result$deleted),
            filename_with_stats = filename_with_stats
         )
         return(result)
      }
   }, error = function(e) {
      cat("DEBUG: Error in get_diff_data_for_edit_file:", e$message, "\n")
   })
})

.rs.addJsonRpcHandler("get_diff_data_for_edit_file", function(message_id) {
   result <- .rs.get_diff_data_for_edit_file(message_id)
   return(result)
})

# Open Document Management Functions
.rs.addFunction("get_open_document_by_path", function(file_path) {
   # Get open document info by file path
   # Returns document object if found, NULL if not open
   
   if (is.null(file_path) || !is.character(file_path) || length(file_path) == 0) {
      return(NULL)
   }
   
   # For unsaved files with __UNSAVED_ patterns, don't normalize the path
   # as it would break the special pattern matching in C++
   path_to_use <- if (startsWith(file_path, "__UNSAVED")) {
      file_path
   } else {
      # Normalize path for comparison only for regular files
      tryCatch({
         normalizePath(file_path, winslash = "/", mustWork = FALSE)
      }, error = function(e) {
         file_path
      })
   }
   
   # Call C++ function to get document content via RPC
   result <- tryCatch({
      .rs.invokeRpc("get_open_document_content", path_to_use)
   }, error = function(e) {
      return(NULL)
   })
   
   if (!is.null(result) && !is.null(result$found) && result$found) {
      return(result)
   }
   
   return(NULL)
})

.rs.addFunction("get_open_document_content", function(file_path) {
   # Get the current editor content (including unsaved changes) for a file
   # Returns content string or NULL if not open
   
   doc_info <- .rs.get_open_document_by_path(file_path)
   if (!is.null(doc_info) && !is.null(doc_info$content)) {
      return(doc_info$content)
   }
   
   return(NULL)
})

.rs.addFunction("is_file_open_in_editor", function(file_path) {
   # Quick check if file is currently open in the editor
   # Returns TRUE/FALSE
   
   if (is.null(file_path) || !is.character(file_path) || length(file_path) == 0) {
      return(FALSE)
   }
   
   # For unsaved files with __UNSAVED_ patterns, don't normalize the path
   # as it would break the special pattern matching in C++
   path_to_use <- if (startsWith(file_path, "__UNSAVED")) {
      file_path
   } else {
      # Normalize path for comparison only for regular files
      tryCatch({
         normalizePath(file_path, winslash = "/", mustWork = FALSE)
      }, error = function(e) {
         file_path
      })
   }
   
   # Call C++ function to check if file is open via RPC
   result <- tryCatch({
      .rs.invokeRpc("is_file_open_in_editor", path_to_use)
   }, error = function(e) {
      return(FALSE)
   })
   
   return(as.logical(result))
})

.rs.addFunction("get_all_open_documents", function() {
   # Get all currently open documents
   # Returns list of document objects
   
   result <- tryCatch({
      .rs.invokeRpc("get_all_open_documents")
   }, error = function(e) {
      return(list())
   })
   
   if (is.null(result)) {
      return(list())
   }
   
   return(result)
})

.rs.addFunction("get_effective_file_content", function(file_path, start_line = NULL, end_line = NULL) {
   # Get file content - from editor if open, otherwise from disk
   # This is the main routing function that chooses editor vs disk content
   
   if (is.null(file_path) || !is.character(file_path) || length(file_path) == 0) {
      return(NULL)
   }
   
   # Check if file is open in editor first
   if (.rs.is_file_open_in_editor(file_path)) {
      # Get content from editor (includes unsaved changes)
      editor_content <- .rs.get_open_document_content(file_path)
      
      if (!is.null(editor_content)) {
         # Apply line range if specified
         if (!is.null(start_line) || !is.null(end_line)) {
            content_lines <- strsplit(editor_content, "\n")[[1]]
            total_lines <- length(content_lines)
            
            start_line <- if (is.null(start_line)) 1 else max(1, as.integer(start_line))
            end_line <- if (is.null(end_line)) total_lines else min(total_lines, as.integer(end_line))
            
            if (start_line <= end_line && start_line <= total_lines) {
               selected_lines <- content_lines[start_line:end_line]
               return(paste(selected_lines, collapse = "\n"))
            } else {
               return("")
            }
         }
         
         return(editor_content)
      }
   }
   
   # File not open in editor, get from disk
   return(.rs.get_disk_file_content(file_path, start_line, end_line))
})

.rs.addFunction("get_disk_file_content", function(file_path, start_line = NULL, end_line = NULL) {
   # Get file content from disk (original behavior)
   
   if (!file.exists(file_path)) {
      return(NULL)
   }
   
   tryCatch({
      all_lines <- readLines(file_path, warn = FALSE)
      
      # Apply line range if specified
      if (!is.null(start_line) || !is.null(end_line)) {
         total_lines <- length(all_lines)
         start_line <- if (is.null(start_line)) 1 else max(1, as.integer(start_line))
         end_line <- if (is.null(end_line)) total_lines else min(total_lines, as.integer(end_line))
         
         if (start_line <= end_line && start_line <= total_lines) {
            selected_lines <- all_lines[start_line:end_line]
            return(paste(selected_lines, collapse = "\n"))
         } else {
            return("")
         }
      }
      
      return(paste(all_lines, collapse = "\n"))
   }, error = function(e) {
      return(NULL)
   })
})

.rs.addFunction("apply_file_edit", function(file_path, new_content, edit_metadata = NULL) {
   # Apply edit to file - to editor if open, otherwise to disk
   # This is the main routing function for applying edits
   
   if (is.null(file_path) || !is.character(file_path) || length(file_path) == 0) {
      return(FALSE)
   }
   
   if (is.null(new_content) || !is.character(new_content)) {
      cat("DEBUG apply_file_edit: Invalid new_content, returning FALSE\n")
      return(FALSE)
   }
      
   # First try to update the open document via the new RPC mechanism
   # This will update both the source database and trigger a client refresh
   # For apply_file_edit (accept operations), always mark_clean = FALSE to keep document marked as dirty
   result <- tryCatch({
      .rs.invokeRpc("update_open_document_content", file_path, new_content, FALSE)
   }, error = function(e) {
      cat("DEBUG apply_file_edit: RPC failed with error:", e$message, "\n")
      FALSE
   })
   
   if (result) {
      # Successfully updated the open document
      return(TRUE)
   }
   
   # If the document is not open, or RPC failed, fall back to file system update
   disk_result <- .rs.apply_edit_to_disk(file_path, new_content, edit_metadata)
   return(disk_result)
})

.rs.addFunction("apply_edit_to_disk", function(file_path, new_content, edit_metadata = NULL) {
   # Apply edit directly to disk file (original behavior)
   
   tryCatch({
      # Create directory if needed
      file_dir <- dirname(file_path)
      if (!dir.exists(file_dir)) {
         dir.create(file_dir, recursive = TRUE, showWarnings = FALSE)
      }
      
      # Write content to file
      content_lines <- strsplit(new_content, "\n")[[1]]
      writeLines(content_lines, file_path)
      
      return(TRUE)
   }, error = function(e) {
      cat("Error writing to disk:", e$message, "\n")
      return(FALSE)
   })
})

.rs.addFunction("check_file_pattern_match", function(file_path, include_patterns = NULL, exclude_patterns = NULL) {
   # Helper function to check if a file path matches include/exclude patterns
   # Returns TRUE if the file should be included, FALSE if it should be excluded
   
   # If file_path is NULL or empty, exclude it
   if (is.null(file_path) || file_path == "") {
      return(FALSE)
   }
   
   # For unsaved files, extract just the filename part for pattern matching
   if (startsWith(file_path, "__UNSAVED")) {
      # Extract filename from patterns like "__UNSAVED__/Untitled1" or "__UNSAVED_53B1__/Untitled1"
      if (grepl("/", file_path)) {
         file_name <- sub(".*/(.*)", "\\1", file_path)
         # Use the filename for pattern matching, but keep original path for other checks
         match_path <- file_name
      } else {
         file_name <- file_path
         match_path <- file_path
      }
   } else {
      # For regular files, use basename for filename and full path for matching
      file_name <- basename(file_path)
      match_path <- file_path
   }
   
   # Check exclude patterns first (exclusions take precedence)
   if (!is.null(exclude_patterns) && length(exclude_patterns) > 0) {
      for (pattern in exclude_patterns) {
         if (pattern == "") next
         
         # Handle file extension patterns (*.ext)
         if (grepl("^\\*\\.[a-zA-Z0-9]+$", pattern)) {
            ext <- sub("^\\*\\.", "", pattern)
            # Check all variations: lowercase, uppercase, first-letter-capitalized
            if (grepl(paste0("\\.", tolower(ext), "$"), file_name, ignore.case = FALSE) ||
                grepl(paste0("\\.", toupper(ext), "$"), file_name, ignore.case = FALSE) ||
                grepl(paste0("\\.", paste0(toupper(substr(ext, 1, 1)), tolower(substr(ext, 2, nchar(ext)))), "$"), file_name, ignore.case = FALSE)) {
               return(FALSE)  # Excluded
            }
         } else {
            # Handle other patterns using glob-style matching
            pattern_regex <- glob2rx(pattern)
            if (grepl(pattern_regex, file_name) || grepl(pattern_regex, match_path)) {
               return(FALSE)  # Excluded
            }
         }
      }
   }
   
   # If no include patterns specified, include by default (unless excluded above)
   if (is.null(include_patterns) || length(include_patterns) == 0) {
      return(TRUE)
   }
   
   # Check include patterns
   for (pattern in include_patterns) {
      if (pattern == "") next
      
      # Handle file extension patterns (*.ext)
      if (grepl("^\\*\\.[a-zA-Z0-9]+$", pattern)) {
         ext <- sub("^\\*\\.", "", pattern)
         # Check all variations: lowercase, uppercase, first-letter-capitalized
         if (grepl(paste0("\\.", tolower(ext), "$"), file_name, ignore.case = FALSE) ||
             grepl(paste0("\\.", toupper(ext), "$"), file_name, ignore.case = FALSE) ||
             grepl(paste0("\\.", paste0(toupper(substr(ext, 1, 1)), tolower(substr(ext, 2, nchar(ext)))), "$"), file_name, ignore.case = FALSE)) {
            return(TRUE)  # Included
         }
      } else {
         # Handle other patterns using glob-style matching
         pattern_regex <- glob2rx(pattern)
         if (grepl(pattern_regex, file_name) || grepl(pattern_regex, match_path)) {
            return(TRUE)  # Included
         }
      }
   }
   
   # If include patterns were specified but none matched, exclude
   return(FALSE)
})

.rs.addFunction("grep_in_open_documents", function(pattern, case_sensitive = FALSE, include_patterns = NULL, exclude_patterns = NULL) {
   # Search for pattern in all open document contents
   # Now supports include/exclude pattern filtering like the disk search
   # Returns list of matches with file paths and line information
   
   results <- list()
   
   # Get all open documents
   open_docs <- .rs.get_all_open_documents()
   
   if (length(open_docs) == 0) {
      return(results)
   }
   
   # Search each document
   for (doc in open_docs) {
      # Skip documents without contents
      if (is.null(doc$contents) || doc$contents == "") {
         next
      }
      
      # Create the path to use for pattern matching and display
      display_path <- NULL
      
      if (!is.null(doc$path) && doc$path != "") {
         # Saved file with a path - make it relative to cwd
         cwd <- getwd()
         display_path <- gsub(paste0("^", cwd, "/"), "", doc$path)
      } else if (!is.null(doc$properties) && !is.null(doc$properties$tempName) && doc$properties$tempName != "") {
         # Unsaved file - construct path from tempName and document ID
         temp_name <- doc$properties$tempName
         
         if (!is.null(doc$id) && doc$id != "") {
            display_path <- paste0("__UNSAVED_", substr(doc$id, 1, 4), "__/", temp_name)
         } else {
            display_path <- paste0("__UNSAVED__/", temp_name)
         }
      } else {
         # Skip documents without path or tempName - no fallbacks
         next
      }
      
      
      if (is.null(display_path)) {
         next
      }
      
      # Apply include/exclude pattern filtering
      # For unsaved files, consider the document type when checking patterns
      should_include_file <- if (startsWith(display_path, "__UNSAVED")) {
         # For unsaved files, create a temporary filename with extension for pattern matching only
         pattern_test_name <- temp_name
         
         # If include/exclude patterns are specified and the temp_name has no extension,
         # get the extension that would correspond to this document type for testing
         if ((!is.null(include_patterns) && length(include_patterns) > 0) || 
             (!is.null(exclude_patterns) && length(exclude_patterns) > 0)) {
            if (!grepl("\\.", temp_name)) {
               doc_type <- if (!is.null(doc$type)) doc$type else ""
               extension <- switch(doc_type,
                  "r_source" = ".R",        # kSourceDocumentTypeRSource
                  "r_markdown" = ".Rmd",    # kSourceDocumentTypeRMarkdown
                  "quarto_markdown" = ".qmd", # kSourceDocumentTypeQuartoMarkdown
                  "r_html" = ".Rhtml",      # kSourceDocumentTypeRHTML
                  "sweave" = ".Rnw",        # kSourceDocumentTypeSweave
                  "cpp" = ".cpp",           # kSourceDocumentTypeCpp
                  "python" = ".py",         # kSourceDocumentTypePython
                  "sql" = ".sql",           # kSourceDocumentTypeSQL
                  "js" = ".js",             # kSourceDocumentTypeJS
                  "sh" = ".sh",             # kSourceDocumentTypeShell
                  ""  # default: no extension for unknown types
               )
               if (extension != "") {
                  pattern_test_name <- paste0(temp_name, extension)
               }
            }
         }
         
         # Test pattern matching against the test name, but use original display_path for results
         pattern_test_path <- if (!is.null(doc$id) && doc$id != "") {
            paste0("__UNSAVED_", substr(doc$id, 1, 4), "__/", pattern_test_name)
         } else {
            paste0("__UNSAVED__/", pattern_test_name)
         }
         
         .rs.check_file_pattern_match(pattern_test_path, include_patterns, exclude_patterns)
      } else {
         # For saved files, use the display_path directly
         .rs.check_file_pattern_match(display_path, include_patterns, exclude_patterns)
      }
      
      if (!should_include_file) {
         next  # Skip this file due to include/exclude patterns
      }
      
      # Split content into lines
      content_lines <- strsplit(doc$contents, "\n")[[1]]
      
      # Search each line
      for (line_num in seq_along(content_lines)) {
         line_content <- content_lines[line_num]
         
         # Perform grep search
         if (case_sensitive) {
            match_found <- grepl(pattern, line_content, perl = TRUE)
         } else {
            match_found <- grepl(pattern, line_content, ignore.case = TRUE, perl = TRUE)
         }
         
         if (match_found) {
            # Add match result
            match_entry <- list(
               file = display_path,
               line = line_num,
               content = line_content,
               source = "EDITOR"
            )
            
            if (is.null(results[[display_path]])) {
               results[[display_path]] <- list()
            }
            
            results[[display_path]][[length(results[[display_path]]) + 1]] <- match_entry
         }
      }
   }
   
   return(results)
})

# Helper function to generate unique display names for files, especially for unsaved files with duplicate base names
.rs.addFunction("get_unique_display_name", function(file_path, all_paths = NULL) {
   # If no other paths provided, just return basename
   if (is.null(all_paths) || length(all_paths) <= 1) {
      if (startsWith(file_path, "__UNSAVED")) {
         # Extract just the filename part after the last /
         if (grepl("/", file_path)) {
            return(sub(".*/(.*)", "\\1", file_path))
         }
      }
      return(basename(file_path))
   }
   
   # Get base names of all paths
   base_names <- character(length(all_paths))
   for (i in seq_along(all_paths)) {
      path <- all_paths[i]
      if (startsWith(path, "__UNSAVED")) {
         # Extract just the filename part for __UNSAVED__ paths
         if (grepl("/", path)) {
            base_names[i] <- sub(".*/(.*)", "\\1", path)
         } else {
            base_names[i] <- path
         }
      } else {
         base_names[i] <- basename(path)
      }
   }
   
   # Get the base name for current file
   current_base_name <- if (startsWith(file_path, "__UNSAVED")) {
      if (grepl("/", file_path)) {
         sub(".*/(.*)", "\\1", file_path)
      } else {
         file_path
      }
   } else {
      basename(file_path)
   }
   
   # Count how many files have the same base name
   duplicate_count <- sum(base_names == current_base_name)
   
   # If no duplicates, return simple base name
   if (duplicate_count <= 1) {
      return(current_base_name)
   }
   
   # If there are duplicates and this is an unsaved file, return the full __UNSAVED__ pattern
   if (startsWith(file_path, "__UNSAVED")) {
      return(file_path)  # Return full pattern like "__UNSAVED_53B1__/Untitled1"
   }
   
   # For saved files with duplicates, return the full path to distinguish them
   return(file_path)
})

