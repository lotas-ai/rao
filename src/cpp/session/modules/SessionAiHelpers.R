# SessionAiHelpers.R
#
# Copyright (C) 2025 by Lotas Inc.
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

.rs.addFunction("normalize_file_path", function(path) {
  # Normalize file paths by expanding tilde and resolving to absolute path
  # This ensures consistent path format between R and Java
  if (is.null(path) || is.na(path) || nchar(path) == 0) {
    return(path)
  }
    
  expanded <- path.expand(path)
  
  # Try normalizePath first (works for existing files)
  normalized <- normalizePath(expanded, mustWork = FALSE)
  
  # If file doesn't exist and path is still relative, manually construct absolute path
  if (!file.exists(normalized) && !startsWith(normalized, "/") && !grepl("^[A-Za-z]:", normalized)) {
    # Relative path for non-existent file - construct absolute path from working directory
    normalized <- file.path(getwd(), normalized)
    # Normalize the constructed path to clean up . and ..
    normalized <- normalizePath(normalized, mustWork = FALSE)
  }
  
  return(normalized)
})

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

# Initialize AI settings system when the module loads
tryCatch({
   if (exists(".rs.initialize_ai_settings", mode = "function")) {
      .rs.initialize_ai_settings()
   }
}, error = function(e) {
   # Settings initialization failed, but don't break the module
   warning("AI settings initialization failed: ", e$message)
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

.rs.addFunction("compute_line_diff", function(old_lines, new_lines, use_unified_diff_format = FALSE) {
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
   
   # For file editing widgets, convert to unified diff format for proper display
   if (use_unified_diff_format) {
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

.rs.addFunction("filter_diff_for_display", function(diff_data) {
   # Filter diff data to show only lines from 1 before first change to 1 after last change
   # Keep the full diff stored but return filtered version for display
   
   if (is.null(diff_data) || length(diff_data) == 0) {
      return(diff_data)
   }
   
   # Find first and last changed lines (added or deleted)
   first_change_index <- NULL
   last_change_index <- NULL
   
   for (i in seq_along(diff_data)) {
      line_type <- diff_data[[i]]$type
      if (!is.null(line_type) && (line_type == "added" || line_type == "deleted")) {
         if (is.null(first_change_index)) {
            first_change_index <- i
         }
         last_change_index <- i
      }
   }
   
   # If no changes found, return original diff
   if (is.null(first_change_index) || is.null(last_change_index)) {
      return(diff_data)
   }
   
   # Calculate display range: 1 before first change to 1 after last change
   start_index <- max(1, first_change_index - 1)
   end_index <- min(length(diff_data), last_change_index + 1)
   
   # Extract the filtered subset
   filtered_diff <- diff_data[start_index:end_index]
   
   return(filtered_diff)
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

# Persistent diff functions for the gutter manager
.rs.addFunction("get_persistent_diff_data_for_file", function(file_path) {
   # Get fresh diff data for a specific file path by comparing original vs current content
   # This replaces the old approach of using stale line numbers from individual changes
   
   if (is.null(file_path) || file_path == "") {
      return(list(diffs = list()))
   }
   
   # Normalize the file path for comparison
   normalized_file_path <- normalizePath(path.expand(file_path), mustWork = FALSE)
   
   # Step 1: Find the original content from the first change to this file
   original_content <- .rs.get_original_content_for_file(normalized_file_path)
   if (is.null(original_content)) {
      return(list(diffs = list()))
   }
      
   # Step 2: Get current content from the editor (or disk if not open)
   current_content <- .rs.get_effective_file_content(normalized_file_path)
   if (is.null(current_content)) {
      return(list(diffs = list()))
   }
      
   # Step 3: Compare original vs current to see if there are any changes
   if (original_content == current_content) {
      return(list(diffs = list()))
   }
   
   # Step 4: Compute fresh diff between original and current content
   
   # Split content into lines
   original_lines <- if (nchar(original_content) > 0) {
      strsplit(original_content, "\n", fixed = TRUE)[[1]]
   } else {
      character(0)
   }
   
   current_lines <- if (nchar(current_content) > 0) {
      strsplit(current_content, "\n", fixed = TRUE)[[1]]
   } else {
      character(0)
   }
   
   # Use the existing diff computation function
   diff_result <- .rs.compute_line_diff(original_lines, current_lines, use_unified_diff_format = FALSE)
   
   # Step 5: Convert diff results to the format expected by the Java gutter manager
   # Create a single diff entry with the fresh diff data
   file_diffs <- list()
   if (length(diff_result$diff) > 0) {
      # Create a synthetic message ID for the combined diff
      synthetic_id <- paste0("fresh_diff_", as.integer(Sys.time()))
      
      file_diffs[[synthetic_id]] <- list(
         file_path = normalized_file_path,
         diff_data = diff_result$diff,
         accepted = TRUE,  # Show as accepted since these are cumulative changes
         accepted_timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
         is_fresh_diff = TRUE  # Flag to indicate this is a fresh diff, not historical
      )
   }
   
   return(list(
      diffs = file_diffs,
      original_content = original_content
   ))
})

.rs.addFunction("get_original_content_for_file", function(file_path) {
   # Get the original content of a file from the first change in file_changes.json
   
   # Read the file changes log
   changes_log <- .rs.read_file_changes_log()
   
   if (is.null(changes_log$changes) || length(changes_log$changes) == 0) {
      return(NULL)
   }
   
   # Normalize the file path for comparison
   normalized_file_path <- normalizePath(path.expand(file_path), mustWork = FALSE)
   
   # Find the first change to this file (earliest timestamp)
   first_change <- NULL
   earliest_timestamp <- NULL
   
   for (change in changes_log$changes) {
      if (is.null(change$file_path)) next
      
      # Normalize the change file path for comparison
      change_file_path <- normalizePath(path.expand(change$file_path), mustWork = FALSE)
      
      if (change_file_path == normalized_file_path) {
         change_timestamp <- as.POSIXct(change$timestamp, format = "%Y-%m-%d %H:%M:%S")
         
         if (is.null(earliest_timestamp) || change_timestamp < earliest_timestamp) {
            earliest_timestamp <- change_timestamp
            first_change <- change
         }
      }
   }
   
   if (is.null(first_change)) {
      return(NULL)
   }
   
   # Return the previous_content from the first change
   original_content <- first_change$previous_content
   if (is.null(original_content)) {
      return("")
   }
   
   return(original_content)
})

.rs.addFunction("clear_all_persistent_diffs", function() {
   # Clear all persistent diff data - called when starting a new conversation
   # This will clear the conversation_diffs.json file
   
   tryCatch({
      diffs_data <- list(diffs = list())
      .rs.write_conversation_diffs(diffs_data)
      return(TRUE)
   }, error = function(e) {
      return(FALSE)
   })
})

.rs.addFunction("mark_diff_as_accepted", function(message_id, file_path) {
   # Mark a specific diff as accepted for persistent display
   # This is called when a user accepts a file editing change
   
   if (is.null(message_id) || is.null(file_path)) {
      return(FALSE)
   }
   
   # Get the existing diff data
   stored_diff <- .rs.get_stored_diff_data(message_id)
   
   if (is.null(stored_diff)) {
      return(FALSE)
   }
   
   # Read current diffs data
   diffs_data <- .rs.read_conversation_diffs()
   
   # Update the diff entry to mark it as accepted
   msg_id_char <- as.character(message_id)
   if (!is.null(diffs_data$diffs[[msg_id_char]])) {
      diffs_data$diffs[[msg_id_char]]$file_path <- file_path
      diffs_data$diffs[[msg_id_char]]$accepted <- TRUE
      diffs_data$diffs[[msg_id_char]]$accepted_timestamp <- format(Sys.time(), "%Y-%m-%d %H:%M:%S")
      
      # Write back the updated diff data
      .rs.write_conversation_diffs(diffs_data)
      return(TRUE)
   }
   
   return(FALSE)
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
   
   # Check if it's a function_call for search_replace, run_console_cmd, run_terminal_cmd, delete_file, or run_file
   if (!is.null(target_message$function_call) && 
       !is.null(target_message$function_call$name)) {
      
      function_name <- target_message$function_call$name
      
      if (function_name == "search_replace") {
         # Extract filename from search_replace arguments
         args <- tryCatch({
            if (is.character(target_message$function_call$arguments)) {
               jsonlite::fromJSON(target_message$function_call$arguments, simplifyVector = FALSE)
            } else {
               target_message$function_call$arguments
            }
         }, error = function(e) {
            return(NULL)
         })
         
         if (!is.null(args) && !is.null(args$file_path)) {
            filename <- if (for_display) basename(args$file_path) else args$file_path
            return(filename)
         }
      } else if (function_name == "run_console_cmd") {
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

.rs.addFunction("limit_output_text", function(output_text, max_total_chars = 10000, max_lines = 50, max_line_length = 200) {
   if (!is.character(output_text)) {
      output_text <- as.character(output_text)
   }
   
   total_length <- sum(nchar(output_text))
   
   # Only limit if total characters exceed the threshold
   if (total_length > max_total_chars) {
      # First, limit the number of lines if necessary
      if (length(output_text) > max_lines) {
         # Take equal amounts from start and end instead of just from start
         total_lines <- length(output_text)
         lines_per_side <- floor((max_lines - 1) / 2)  # -1 to account for truncation message
         
         start_lines <- output_text[1:lines_per_side]
         end_lines <- output_text[(total_lines - lines_per_side + 1):total_lines]
         
         truncation_msg <- paste0("... (", total_lines - 2 * lines_per_side, " lines truncated) ...")
         output_text <- c(start_lines, truncation_msg, end_lines)
      }
      
      # Only truncate individual lines if we're still over the limit
      current_total <- sum(nchar(output_text))
      if (current_total > max_total_chars) {
         output_text <- vapply(output_text, function(line) {
               if (nchar(line) > max_line_length) {
                  paste0(substr(line, 1, max_line_length - 3), "...")
               } else {
                  line
               }
         }, character(1), USE.NAMES = FALSE)
      }
   }
   
   return(output_text)
})

.rs.addFunction("check_required_packages", function(pkgs = c("httr2", "httr", "jsonlite", "curl", "commonmark", "htmltools", "base64enc", "processx", "callr", "magick", "rmarkdown")) {
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

# RPC handlers for persistent diff system
.rs.addJsonRpcHandler("get_persistent_diff_data", function(file_path) {
   result <- .rs.get_persistent_diff_data_for_file(file_path)
   return(result)
})

.rs.addJsonRpcHandler("clear_all_persistent_diffs", function() {
   result <- .rs.clear_all_persistent_diffs()
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

.rs.addFunction("remove_line_numbers", function(content) {
   # Remove line numbers from code content - equivalent to Java removeLineNumbers() method
   # Removes all types of line number patterns added by addLineNumbers method
   
   if (is.null(content) || !is.character(content) || length(content) == 0) {
      return(content)
   }
   
   if (trimws(content) == "") {
      return(content)
   }
   
   # Split into lines and remove line number patterns
   lines <- strsplit(content, "\n", fixed = TRUE)[[1]]
   
   cleaned_lines <- character(length(lines))
   for (i in seq_along(lines)) {
      line <- lines[i]
      
      # Remove all possible line number patterns at the end of lines
      # Pattern: [space] [comment_prefix] [space] [digits] [optional_space] at end of line
      
      # Handle // comments (Java, JavaScript, C/C++, etc.)
      line <- gsub("\\s*//\\s*\\d+\\s*$", "", line, perl = TRUE)
      
      # Handle # comments (Python, R, Ruby, Bash, etc.)
      line <- gsub("\\s*#\\s*\\d+\\s*$", "", line, perl = TRUE)
      
      # Handle <!-- comments (HTML, XML, SVG)
      line <- gsub("\\s*<!--\\s*\\d+\\s*$", "", line, perl = TRUE)
      
      # Handle % comments (LaTeX)
      line <- gsub("\\s*%\\s*\\d+\\s*$", "", line, perl = TRUE)
      
      # Handle -- comments (SQL, Haskell, Lua)
      line <- gsub("\\s*--\\s*\\d+\\s*$", "", line, perl = TRUE)
      
      # Handle /* */ comments (CSS, some C-style languages)
      line <- gsub("\\s*/\\*\\s*\\d+\\s*\\*/\\s*$", "", line, perl = TRUE)
      
      cleaned_lines[i] <- line
   }
   
   # Join lines back together
   return(paste(cleaned_lines, collapse = "\n"))
})

.rs.addFunction("apply_file_edit", function(file_path, new_content, edit_metadata = NULL) {
   # Apply edit to file - ALWAYS write to disk AND update editor if open
   # This ensures consistency and prevents "file changed on disk" warnings
   
   if (is.null(file_path) || !is.character(file_path) || length(file_path) == 0) {
      return(FALSE)
   }
   
   if (is.null(new_content) || !is.character(new_content)) {
      cat("DEBUG apply_file_edit: Invalid new_content, returning FALSE\n")
      return(FALSE)
   }
   
   # Step 1: Write to disk first
   disk_success <- tryCatch({
      # Create directory if needed
      file_dir <- dirname(file_path)
      if (!dir.exists(file_dir)) {
         dir.create(file_dir, recursive = TRUE, showWarnings = FALSE)
      }
      
      # Write content to file
      content_lines <- strsplit(new_content, "\n")[[1]]
      writeLines(content_lines, file_path)
      TRUE
   }, error = function(e) {
      cat("ERROR apply_file_edit: Failed to write to disk:", e$message, "\n")
      return(FALSE)
   })
   
   if (!disk_success) {
      return(FALSE)
   }
   
   # Step 2: If file is open in editor, update the editor content and mark as clean
   # This syncs the editor with the disk and prevents "file changed on disk" warnings
   if (.rs.is_file_open_in_editor(file_path)) {
      tryCatch({
         # mark_clean = TRUE because we just saved to disk, so editor should match disk
         .rs.invokeRpc("update_open_document_content", file_path, new_content, TRUE)
      }, error = function(e) {
         cat("DEBUG apply_file_edit: Editor update failed (non-fatal):", e$message, "\n")
      })
   }
   
   return(TRUE)
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

.rs.addFunction("grep_in_open_documents", function(pattern, case_sensitive = FALSE, include_patterns = NULL, exclude_patterns = NULL, context_before = 0, context_after = 0, multiline = FALSE, search_path = NULL) {
   # Returns list of matches with file paths and line information
   
   pattern <- gsub('\\\\', '\\', pattern, fixed = TRUE)

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
         # Keep absolute path to match ripgrep output, but expand ~ to full path
         display_path <- path.expand(doc$path)
      } else if (!is.null(doc$properties) && !is.null(doc$properties$tempName) && doc$properties$tempName != "") {
         temp_name <- doc$properties$tempName
         
         if (!is.null(doc$id) && doc$id != "") {
            display_path <- paste0("__UNSAVED_", substr(doc$id, 1, 4), "__/", temp_name)
         } else {
            display_path <- paste0("__UNSAVED__/", temp_name)
         }
      } else {
         next
      }
      
      if (is.null(display_path)) {
         next
      }
      
      # Check path restriction for open documents
      if (!is.null(search_path) && search_path != "") {
         # Get the full document path and expand tilde to compare with search_path
         full_doc_path <- if (!is.null(doc$path)) path.expand(doc$path) else ""
         
         # Check if search_path is a specific file or a directory
         # If it's a file, do exact match; if directory, use startsWith
         if (grepl("\\.[a-zA-Z0-9]+$", search_path)) {
            # Looks like a file path (has extension)
            if (full_doc_path != search_path) {
               next
            }
         } else {
            # Looks like a directory path
            if (full_doc_path != "" && !startsWith(full_doc_path, search_path)) {
               next
            }
         }
      }
      
      # Apply include/exclude pattern filtering
      should_include_file <- .rs.check_file_pattern_match(display_path, include_patterns, exclude_patterns)
      
      if (!should_include_file) {
         next
      }
            
      # Initialize results for this file (even if no matches) so ripgrep knows we searched it
      # This must come AFTER all the filtering checks
      if (is.null(results[[display_path]])) {
         results[[display_path]] <- list()
      }
      
      # Split content into lines
      content_lines <- strsplit(doc$contents, "\n")[[1]]
      
      # First pass: find all matching lines
      matching_lines <- c()
      
      if (multiline) {
         # For multiline mode, test against the entire content
         # Use dotall equivalent behavior where . matches newlines
         multiline_pattern <- if (!startsWith(pattern, "(?s)")) paste0("(?s)", pattern) else pattern
         
         # Find all matches in the document
         if (case_sensitive) {
            matches <- gregexpr(multiline_pattern, doc$contents, perl = TRUE)[[1]]
         } else {
            matches <- gregexpr(multiline_pattern, doc$contents, ignore.case = TRUE, perl = TRUE)[[1]]
         }
         
         if (matches[1] != -1) {
            # For each match, find which lines it spans
            for (i in seq_along(matches)) {
               match_start <- matches[i]
               match_length <- attr(matches, "match.length")[i]
               match_end <- match_start + match_length - 1
               
               # Count newlines before match_start to find starting line
               # Use the same line splitting method we used for content_lines
               text_before <- substr(doc$contents, 1, match_start - 1)
               # If empty, we're on line 1; otherwise count lines
               if (nchar(text_before) == 0) {
                  start_line <- 1
               } else {
                  lines_before <- length(strsplit(text_before, "\n", fixed = TRUE)[[1]])
                  start_line <- lines_before + 1
               }
               
               # Count newlines within match to find ending line
               matched_text <- substr(doc$contents, match_start, match_end)
               lines_in_match <- length(strsplit(matched_text, "\n", fixed = TRUE)[[1]])
               # If match spans multiple lines, end_line is start_line + (number of line breaks)
               end_line <- start_line + (lines_in_match - 1)
               
               # Add all lines in this span
               for (line_num in start_line:end_line) {
                  if (line_num <= length(content_lines)) {
                     matching_lines <- c(matching_lines, line_num)
                  }
               }
            }
            
            # Remove duplicates
            matching_lines <- unique(matching_lines)
         }
      } else {
         # Regular line-by-line search
         for (line_num in seq_along(content_lines)) {
            line_content <- content_lines[line_num]
            
            # Perform grep search
            if (case_sensitive) {
               match_found <- grepl(pattern, line_content, perl = TRUE)
            } else {
               match_found <- grepl(pattern, line_content, ignore.case = TRUE, perl = TRUE)
            }
            
            if (match_found) {
               matching_lines <- c(matching_lines, line_num)
            }
         }
      }
      
      # Second pass: add matching lines plus context
      if (length(matching_lines) > 0) {
         lines_to_include <- c()
         
         for (match_line in matching_lines) {
            # Add context before
            if (context_before > 0) {
               for (i in max(1, match_line - context_before):(match_line - 1)) {
                  lines_to_include <- c(lines_to_include, i)
               }
            }
            
            # Add match line
            lines_to_include <- c(lines_to_include, match_line)
            
            # Add context after
            if (context_after > 0) {
               for (i in (match_line + 1):min(length(content_lines), match_line + context_after)) {
                  lines_to_include <- c(lines_to_include, i)
               }
            }
         }
         
         # Remove duplicates and sort
         lines_to_include <- unique(sort(lines_to_include))
         
         # Add to results
         for (line_num in lines_to_include) {
            is_match <- line_num %in% matching_lines
            line_content <- content_lines[line_num]
            
            # Mark context lines with -- prefix (like ripgrep does)
            if (!is_match) {
               line_content <- paste0("--", line_content)
            }
            
            match_entry <- list(
               file = display_path,
               line = line_num,
               content = line_content,
               source = "EDITOR",
               is_match = is_match
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

.rs.addFunction("format_grep_content", function(ripgrep_output, open_doc_results, pattern, cwd, head_limit = 50, search_file = NULL) {
   # Format grep output in content mode (default)
   # search_file: if ripgrep was searching a specific file, pass it here so we can add it to results
   MAX_RESULTS <- 50
   results <- list()
   total_matches <- 0
   
   # Add results from open documents first
   for (file_path in names(open_doc_results)) {
      for (match_info in open_doc_results[[file_path]]) {
         if (is.null(results[[file_path]])) {
            results[[file_path]] <- character(0)
         }
         
         # Check if this is a context line (starts with --)
         if (startsWith(match_info$content, "--")) {
            # Context line
            context_text <- substring(match_info$content, 3)
            results[[file_path]] <- c(results[[file_path]], 
                                     paste0("Line ", match_info$line, ": ", context_text, " [EDITOR]"))
         } else {
            # Match line
            results[[file_path]] <- c(results[[file_path]], 
                                     paste0("Line ", match_info$line, ": ", match_info$content, " [EDITOR]"))
            total_matches <- total_matches + 1
         }
      }
   }
   
   # Process ripgrep output
   matches <- strsplit(ripgrep_output, "\n")[[1]]
   matches <- matches[matches != ""]
   
   # Respect head_limit but cap at MAX_RESULTS (like vscode does)
   effective_limit <- if (!is.null(head_limit)) min(head_limit, MAX_RESULTS) else MAX_RESULTS
   
   limited_matches <- if (length(matches) > effective_limit) matches[1:effective_limit] else matches
   
   match_count_note <- if (length(matches) > effective_limit) {
      paste0("\n(Showing ", effective_limit, " of ", length(matches), " matches)")
   } else {
      ""
   }
   
   # Add disk results, but only for files not already found in editor
   
   for (match in limited_matches) {
      if (match == "") next
      
      # Handle both match lines (:) and context lines (-)
      is_context_line <- FALSE
      parts <- NULL
      
      if (grepl(":", match, fixed = TRUE)) {
         parts <- strsplit(match, ":", fixed = TRUE)[[1]]
      } else if (grepl("-", match, fixed = TRUE)) {
         parts <- strsplit(match, "-", fixed = TRUE)[[1]]
         is_context_line <- TRUE
      } else {
         next
      }
      
      # Handle both formats:
      # Multi-file: /path/to/file:2:content
      # Single-file: 2:content (when ripgrep searches one specific file)
      if (length(parts) >= 3) {
         # Multi-file format with filepath
         filepath <- parts[1]
         line_num <- parts[2]
         content <- paste(parts[-(1:2)], collapse = if (is_context_line) "-" else ":")
      } else if (length(parts) == 2 && !is.null(search_file)) {
         # Single-file format without filepath - use search_file
         filepath <- search_file
         line_num <- parts[1]
         content <- parts[2]
      } else {
         next
      }
      
      # Skip if we already have editor results for this file (use absolute path from ripgrep)
      if (!is.null(open_doc_results[[filepath]])) {
         next
      }
         
         # Create relative path for display
         relative_path <- gsub(paste0("^", cwd, "/"), "", filepath)
         
         # Skip binary files
         if (grepl("\\.(png|jpg|jpeg|gif|bmp|ico|pdf|zip|tar|gz|rar|7z|exe|dll|so|dylib)$", relative_path, ignore.case = TRUE)) {
            next
         }
         
         # Only truncate long lines for match lines, not context lines
         if (!is_context_line) {
            content_len <- nchar(content)
            if (content_len > 100) {
               match_pos <- regexpr(pattern, content, ignore.case = TRUE, perl = TRUE)[1]
               
               if (match_pos > 0) {
                  start_pos <- max(1, match_pos - 30)
                  end_pos <- min(content_len, match_pos + 30)
                  
                  first_part <- substr(content, 1, 20)
                  middle_part <- substr(content, start_pos, end_pos)
                  last_part <- substr(content, content_len - 19, content_len)
                  
                  content <- paste0(first_part, "...", middle_part, "...", last_part)
               }
            }
            total_matches <- total_matches + 1
         }
         
         if (is.null(results[[relative_path]])) {
            results[[relative_path]] <- character(0)
         }
         
         results[[relative_path]] <- c(results[[relative_path]], 
                                      paste0("Line ", line_num, ": ", content))
   }
   
   # Format final output
   if (length(results) > 0) {
      result_lines <- c(paste0("Results:", match_count_note))
      
      for (file in names(results)) {
         result_lines <- c(result_lines, paste0("\nFile: ", file))
         result_lines <- c(result_lines, results[[file]])
      }
      
      return(paste(result_lines, collapse = "\n"))
   } else {
      return("Results:\n\nNo matches found")
   }
})

.rs.addFunction("format_grep_files_with_matches", function(ripgrep_output, open_doc_results, head_limit = 50) {
   # Format grep output in files_with_matches mode
   MAX_RESULTS <- 50
   files <- character(0)
   
   # Add files from open documents
   for (file_path in names(open_doc_results)) {
      if (length(open_doc_results[[file_path]]) > 0) {
         files <- c(files, file_path)
      }
   }
   
   # Add files from ripgrep output
   ripgrep_files <- strsplit(ripgrep_output, "\n")[[1]]
   ripgrep_files <- ripgrep_files[ripgrep_files != ""]
   
   for (file in ripgrep_files) {
      # Skip if this file is already in open documents (ripgrep returns absolute paths)
      if (is.null(open_doc_results[[file]])) {
         files <- c(files, file)
      }
   }
   
   # Remove duplicates
   files <- unique(files)
   
   # Respect head_limit but cap at MAX_RESULTS
   effective_limit <- if (!is.null(head_limit)) min(head_limit, MAX_RESULTS) else MAX_RESULTS
   limited_files <- if (length(files) > effective_limit) files[1:effective_limit] else files
   
   if (length(limited_files) == 0) {
      return("No files with matches found.")
   }
   
   result_lines <- c(paste0("Files with matches:", 
                           if (length(files) > effective_limit) 
                              paste0(" (showing first ", effective_limit, " of ", length(files), ")") 
                           else ""))
   
   for (file in limited_files) {
      result_lines <- c(result_lines, file)
   }
   
   return(paste(result_lines, collapse = "\n"))
})

.rs.addFunction("format_grep_count", function(ripgrep_output, open_doc_results, head_limit = 50) {
   # Format grep output in count mode
   MAX_RESULTS <- 50
   counts <- list()
   
   # Add counts from open documents
   for (file_path in names(open_doc_results)) {
      # Count only actual matches, not context lines
      match_count <- sum(sapply(open_doc_results[[file_path]], function(m) {
         if (is.null(m$is_match)) TRUE else m$is_match
      }))
      if (match_count > 0) {
         counts[[file_path]] <- match_count
      }
   }
   
   # Add counts from ripgrep output
   count_lines <- strsplit(ripgrep_output, "\n")[[1]]
   count_lines <- count_lines[count_lines != ""]
   
   for (line in count_lines) {
      parts <- strsplit(line, ":", fixed = TRUE)[[1]]
      if (length(parts) == 2) {
         file <- parts[1]
         count <- as.integer(parts[2])
         
         # Skip if this file is already in open documents (ripgrep returns absolute paths)
         if (is.null(open_doc_results[[file]])) {
            counts[[file]] <- count
         }
      }
   }
   
   if (length(counts) == 0) {
      return("No matches found.")
   }
   
   # Convert to data frame for easier handling
   count_entries <- data.frame(
      file = names(counts),
      count = unlist(counts),
      stringsAsFactors = FALSE
   )
   
   # Respect head_limit but cap at MAX_RESULTS
   effective_limit <- if (!is.null(head_limit)) min(head_limit, MAX_RESULTS) else MAX_RESULTS
   limited_entries <- if (nrow(count_entries) > effective_limit) {
      count_entries[1:effective_limit, ]
   } else {
      count_entries
   }
   
   result_lines <- c(paste0("Match counts:", 
                          if (nrow(count_entries) > effective_limit) 
                             paste0(" (showing first ", effective_limit, " of ", nrow(count_entries), ")") 
                          else ""))
   
   for (i in 1:nrow(limited_entries)) {
      result_lines <- c(result_lines, paste0(limited_entries$file[i], ":", limited_entries$count[i]))
   }
   
   return(paste(result_lines, collapse = "\n"))
})

.rs.addFunction("get_plot_images", function() {
   # Get the graphics directory path directly from C++
   graphics_path <- tryCatch({
      .rs.getGraphicsPath()
   }, error = function(e) {
      NULL
   })
   
   if (is.null(graphics_path) || !dir.exists(graphics_path)) {
      return(character(0))
   }
   
   # Get all files in the graphics directory
   all_files <- list.files(graphics_path, full.names = TRUE, all.files = FALSE)
   
   if (length(all_files) == 0) {
      return(character(0))
   }
   
   # Filter to only include image files (exclude .snapshot, .manip, INDEX, and empty.*)
   # Only keep files that look like UUID.png (or other image extensions)
   image_files <- all_files[grepl("\\.(png|jpg|jpeg|svg|tiff|bmp|gif)$", all_files, ignore.case = TRUE)]
   image_files <- image_files[!grepl("^empty\\.", basename(image_files), ignore.case = TRUE)]
   image_files <- image_files[basename(image_files) != "INDEX"]
   
   if (length(image_files) == 0) {
      return(character(0))
   }
   
   # Sort by modification time (most recent first)
   file_info <- file.info(image_files)
   sorted_files <- image_files[order(file_info$mtime, decreasing = TRUE)]
   
   return(sorted_files)
})

.rs.addFunction("get_plot_by_index", function(plot_index) {
   plots <- .rs.get_plot_images()
   
   if (length(plots) == 0) {
      return(list(success = FALSE, error = "No plots available"))
   }
   
   if (plot_index < 1 || plot_index > length(plots)) {
      return(list(success = FALSE, error = paste0("Invalid plot index. Available plots: ", length(plots))))
   }
   
   return(list(success = TRUE, path = plots[plot_index]))
})