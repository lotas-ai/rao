# SessionAiSearch.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.

# Global helper to safely extract and parse function call arguments 
# Handles both string and list formats consistently across the codebase
.rs.addFunction("safe_parse_function_arguments", function(function_call) {
   arguments_data <- function_call$arguments
   
   # Handle case where arguments comes as a list with one element containing JSON string
   # (typical R behavior when receiving JSON arrays from C++)
   if (is.list(arguments_data) && length(arguments_data) == 1) {
      # Extract the first element which should be the JSON string
      arguments_data <- arguments_data[[1]]
   }
   
   # Handle case where arguments is now a JSON string
   if (is.character(arguments_data)) {
      tryCatch({
         parsed_args <- jsonlite::fromJSON(arguments_data)
         return(parsed_args)
      }, error = function(e) {
         stop("Invalid JSON in function arguments: ", e$message)
      })
   }
   
   # Handle case where arguments is already a proper list (parsed)
   if (is.list(arguments_data)) {
      return(arguments_data)
   }
   
   # Fallback - empty list
   return(list())
})

.rs.addFunction("reset_ai_cancellation", function() {
   .rs.set_conversation_var("ai_cancelled", FALSE)
})

.rs.addFunction("process_assistant_response", function(assistant_response, msg_id, related_to_id, conversation_index, source_function_name = NULL, message_metadata = NULL, existing_conversation_log = NULL) {
   if (.rs.get_conversation_var("ai_cancelled")) {
      return(FALSE)
   }
   
   # Check assistant message limit before processing
   limit_check <- .rs.check_assistant_message_limit()
   if (limit_check$exceeded) {
      # Return error result instead of processing
      paths <- .rs.get_ai_file_paths()
      
      # Add error message to conversation
      # Use provided msg_id if available (streaming scenario), otherwise generate new one
      error_msg_id <- if (!is.null(msg_id)) msg_id else .rs.get_next_message_id()
      error_message <- paste0("Rao currently stops after ", limit_check$limit, " assistant messages. Please paste a new message to continue.")
      
      conversation_log <- .rs.read_conversation_log()
      
      # Add error message as assistant response
      conversation_log <- c(conversation_log, 
                         list(list(id = error_msg_id, role = "assistant", content = error_message, related_to = related_to_id)))
      
      # Note: Error messages are now stored only in conversation_log.json
      
      .rs.write_conversation_log(conversation_log)
      
      # Note: Don't increment assistant_message_count here because we're already at the limit
      # This error message is added specifically because the limit was exceeded
      
      .rs.update_conversation_display()
      
      return(list(
         conversation_index = conversation_index,
         assistant_msg_id = error_msg_id,
         limit_exceeded = TRUE,
         status = "done"
      ))
   }
   
   if (.rs.get_conversation_var("ai_cancelled")) {
      conversation_log <- .rs.read_conversation_log()
      
      .rs.write_conversation_log(conversation_log)
      
      .rs.reset_ai_cancellation()
      
      paths <- .rs.get_ai_file_paths()
      
      # Use provided msg_id if available (streaming scenario), otherwise generate new one
      assistant_msg_id <- if (!is.null(msg_id)) msg_id else .rs.get_next_message_id()
      
      result <- list(
         conversation_index = conversation_index,
         assistant_msg_id = assistant_msg_id
      )
      return(result)
   }
   
   if (is.null(assistant_response)) {
      .rs.reset_ai_cancellation()
      
      paths <- .rs.get_ai_file_paths()
      
      # Use provided msg_id if available (streaming scenario), otherwise generate new one
      assistant_msg_id <- if (!is.null(msg_id)) msg_id else .rs.get_next_message_id()
      
      result <- list(
         conversation_index = conversation_index,
         assistant_msg_id = assistant_msg_id
      )
      return(result)
   }
   
   if (!is.null(source_function_name) && source_function_name == "function_call_handler") {
      .rs.set_conversation_var("function_call_depth", 0)
   }
   
   response_msg_id <- if (!is.null(msg_id)) msg_id else .rs.get_next_message_id()
      
   function_call_type <- .rs.get_function_call_type_for_message(response_msg_id)
   
   if (!is.null(source_function_name) && source_function_name == "function_call_handler") {
      if (is.null(existing_conversation_log)) {
         stop("ERROR: process_assistant_response called from function_call_handler without providing conversation log")
      }
      conversation_log <- existing_conversation_log
   } else {
      conversation_log <- .rs.read_conversation_log()
   }
   
   # Create assistant message entry with optional metadata
   assistant_entry <- list(id = response_msg_id, role = "assistant", content = assistant_response, related_to = related_to_id)
   
   # Add metadata if provided (e.g., for cancelled partial responses, response_id)
   if (!is.null(message_metadata)) {
      assistant_entry <- c(assistant_entry, message_metadata)
   }
   
   conversation_log <- c(conversation_log, list(assistant_entry))

   # Increment assistant message count when adding assistant response
   .rs.increment_assistant_message_count()
   
   .rs.write_conversation_log(conversation_log)

   # Check if this assistant message relates to an edit_file function call
   # If so, create the "Response pending..." message now (after the assistant message)
   # Look up the related function call to see if it's edit_file
   related_function_call <- NULL
   for (entry in conversation_log) {
      if (!is.null(entry$id) && entry$id == related_to_id && 
          !is.null(entry$function_call) && !is.null(entry$function_call$name) &&
          entry$function_call$name == "edit_file") {
         related_function_call <- entry
         break
      }
   }
   
   if (!is.null(related_function_call)) {
      # Create procedural user message for edit_file pending
      pending_message_id <- .rs.get_next_message_id()
      pending_message <- list(
         id = pending_message_id,
         role = "user",
         content = "Response pending...",
         related_to = related_to_id,  # Point to the edit_file function call ID
         procedural = TRUE  # Mark as procedural so it doesn't show in UI
      )
      conversation_log <- c(conversation_log, list(pending_message))
      .rs.write_conversation_log(conversation_log)
   }

   text_content <- if (is.list(assistant_response)) {
      jsonlite::toJSON(assistant_response, auto_unbox = TRUE)
   } else {
      assistant_response
   }

   # Ensure related_to_id is converted to integer to match existing data frame structure
   # related_to_id is required and should never be null  
   if (is.null(related_to_id)) {
      stop("related_to_id is required and cannot be NULL in process_assistant_response")
   }
   
   related_to_int <- if (is.list(related_to_id)) {
      as.integer(related_to_id[[1]])
   } else {
      as.integer(related_to_id)
   }

   # Note: Assistant messages are now stored only in conversation_log.json

   # Note: Do not call update_conversation_display() here - widgets are already created during streaming
   # update_conversation_display() is only needed for conversation switching or page refresh
      
   result <- list(
      conversation_index = conversation_index,
      response_msg_id = response_msg_id,
      message_metadata = message_metadata
   )
   return(result)
})


.rs.addFunction("handle_find_keyword_context", function(function_call, current_log, related_to_id, request_id) {
   arguments <- .rs.safe_parse_function_arguments(function_call)
   
   keyword <- arguments$keyword
   
   symbol_results <- .rs.find_symbol(keyword)
   
   formatted_result <- if (is.character(symbol_results)) {
      symbol_results
   } else if (length(symbol_results) > 0) {
      result_lines <- c(paste0("Results for keyword '", keyword, "':"))
      
      symbol_names <- sapply(symbol_results, function(s) s$name)
      has_duplicates <- any(duplicated(symbol_names))
      
      for (i in seq_along(symbol_results)) {
         symbol <- symbol_results[[i]]
         symbol_info <- if(has_duplicates) {
            paste0("[", i, "] - Name: ", symbol$name, ", Type: ", symbol$type)
         } else {
            paste0("- Name: ", symbol$name, ", Type: ", symbol$type)
         }
         
         if (!is.null(symbol$file)) {
            symbol_info <- paste0(symbol_info, "\n  File: ", symbol$file)
         }
         if (!is.null(symbol$filename)) {
            symbol_info <- paste0(symbol_info, "\n  Filename (also keyword): ", symbol$filename)
         }
         
         if (!is.null(symbol$line_start) || !is.null(symbol$line_end)) {
            lineInfo <- ""
            if (!is.null(symbol$line_start)) {
               lineInfo <- paste0("Line Start: ", symbol$line_start)
            }
            if (!is.null(symbol$line_end)) {
               if (lineInfo != "") lineInfo <- paste0(lineInfo, ", ")
               lineInfo <- paste0(lineInfo, "Line End: ", symbol$line_end)
            }
            symbol_info <- paste0(symbol_info, "\n  ", lineInfo)
         }
         
         if (!is.null(symbol$location)) {
            symbol_info <- paste0(symbol_info, "\n  Location: ", symbol$location)
         }
         
         if (!is.null(symbol$parents) && symbol$parents != "") {
            symbol_info <- paste0(symbol_info, "\n  Parents: ", symbol$parents)
         }
         
         if (!is.null(symbol$signature) && symbol$signature != "") {
            symbol_info <- paste0(symbol_info, "\n  Signature: ", symbol$signature)
         }
         
         if (!is.null(symbol$description) && symbol$description != "") {
            symbol_info <- paste0(symbol_info, "\n  Description: ", symbol$description)
         }
         
         if (!is.null(symbol$children) && length(symbol$children) > 0) {
            symbol_info <- paste0(symbol_info, "\n  Children (also keyword): ", paste(symbol$children, collapse = ", "))
         }
         
         result_lines <- c(result_lines, symbol_info)
      }
      
      paste(result_lines, collapse = "\n\n")
   } else {
      paste0("'", keyword, "' is not a keyword and did not return any results. Only find context for keyword.")
   }
   
   function_output_id <- .rs.get_next_message_id()
   function_call_output <- list(
     id = function_output_id,
     type = "function_call_output",
     call_id = function_call$call_id,
     output = formatted_result,
     related_to = function_call$msg_id
   )
   
   return(list(
      function_call_output = function_call_output,
      function_output_id = function_output_id
   ))
})

.rs.addFunction("handle_view_image", function(function_call, current_log, related_to_id, request_id) {
   arguments <- .rs.safe_parse_function_arguments(function_call)
   
   image_path <- arguments$image_path
   
   # COMPREHENSIVE PATH HANDLING FIX - prevents duplication issues
   if (!is.null(image_path)) {
      original_path <- image_path
      current_wd <- getwd()
      
      # CRITICAL FIX: Check if this is an absolute path that lost its leading slash
      # This happens when the AI backend processes the path and strips the leading /
      if (!startsWith(image_path, "/") && !grepl("^[A-Za-z]:", image_path)) {
         # Path appears relative, but check if it's actually an absolute path missing the leading /
         # If the "relative" path starts with common absolute path prefixes, it's likely missing the /
         if (startsWith(image_path, "Users/") || startsWith(image_path, "home/") || 
             startsWith(image_path, "opt/") || startsWith(image_path, "var/") ||
             startsWith(image_path, "tmp/") || startsWith(image_path, "usr/")) {
            # This looks like an absolute path missing the leading slash
            image_path <- paste0("/", image_path)
         } else {
            # Treat as genuinely relative path
            image_path <- file.path(current_wd, image_path)
         }
      }
      
      # General duplication fix: detect if any directory appears twice in succession
      # Split path into components and look for duplicated sequences
      path_components <- strsplit(image_path, "/")[[1]]
      path_components <- path_components[path_components != ""]  # Remove empty components
      
      # Look for duplicated directory sequences
      if (length(path_components) > 2) {
         # Check for any directory that appears twice in a row with intervening path
         for (i in 1:(length(path_components) - 1)) {
            dir_name <- path_components[i]
            if (nchar(dir_name) > 0) {
               # Look for this directory name appearing again later in the path
               later_matches <- which(path_components[(i+1):length(path_components)] == dir_name)
               if (length(later_matches) > 0) {
                  # Found duplication - take everything from the second occurrence
                  duplicate_index <- i + later_matches[1]
                  corrected_components <- path_components[duplicate_index:length(path_components)]
                  image_path <- paste0("/", paste(corrected_components, collapse = "/"))
                  break
               }
            }
         }
      }
      
      # Alternative approach: if the working directory appears in the path multiple times
      if (nchar(current_wd) > 1 && grepl(current_wd, image_path)) {
         # Simple approach: find all occurrences of the working directory
         wd_positions <- gregexpr(current_wd, image_path, fixed = TRUE)[[1]]
         if (length(wd_positions) > 1 && wd_positions[1] != -1) {
            # Multiple occurrences found - use the last one and take everything from there
            last_wd_pos <- wd_positions[length(wd_positions)]
            remaining_path <- substring(image_path, last_wd_pos + nchar(current_wd))
            if (startsWith(remaining_path, "/")) {
               remaining_path <- substring(remaining_path, 2)
            }
            if (nchar(remaining_path) > 0) {
               image_path <- file.path(current_wd, remaining_path)
            }
         }
      }
   }
   
   # Validate file exists
   file_exists <- file.exists(image_path)
   
   # Enhanced file validation
   if (file_exists) {
      # Check if it's actually a file (not a directory)
      if (file.info(image_path)$isdir) {
         function_response <- paste0("Error: Path is a directory, not a file: ", image_path)
         file_exists <- FALSE
      } else {
         # Check file size (limit to 10MB like many image services)
         file_size <- file.info(image_path)$size
         max_size <- 10 * 1024 * 1024  # 10MB
         if (file_size > max_size) {
            function_response <- paste0("Error: Image file too large (", round(file_size / 1024 / 1024, 1), "MB). Maximum size is 10MB: ", basename(image_path))
            file_exists <- FALSE
         } else {
            # Basic file type validation by extension
            file_ext <- tolower(tools::file_ext(image_path))
            supported_formats <- c("png", "jpg", "jpeg", "gif", "svg", "bmp", "tiff", "webp")
            if (!file_ext %in% supported_formats) {
               function_response <- paste0("Error: Unsupported image format '.", file_ext, "'. Supported formats: ", paste(supported_formats, collapse = ", "))
               file_exists <- FALSE
            } else {
               function_response <- paste0("Success: Image found at ", basename(image_path), " (", round(file_size / 1024, 1), "KB)")
            }
         }
      }
   } else {
      function_response <- paste0("Error: Image not found: ", image_path)
   }
   
   function_output_id <- .rs.get_next_message_id()
   function_call_output <- list(
      id = function_output_id,
      type = "function_call_output",
      call_id = function_call$call_id,
      output = function_response,
      related_to = function_call$msg_id
   )
   
   image_message_entry <- NULL
   image_msg_id <- NULL
   
   if (file_exists) {
      image_bin <- readBin(image_path, 'raw', file.info(image_path)$size)
      
      image_b64 <- base64enc::base64encode(image_bin)
      
      # Enhanced MIME type detection
      file_ext <- tolower(tools::file_ext(image_path))
      mime_type <- switch(file_ext,
         "png" = "image/png",
         "jpg" = "image/jpeg", 
         "jpeg" = "image/jpeg",
         "gif" = "image/gif",
         "svg" = "image/svg+xml",
         "bmp" = "image/bmp",
         "tiff" = "image/tiff",
         "webp" = "image/webp",
         "image/png"  # default fallback
      )
      
      image_data <- paste0("data:", mime_type, ";base64,", image_b64)
      
      image_msg_id <- .rs.get_next_message_id()
      
      image_content <- list(
         list(type = "input_text", text = paste0("Image: ", basename(image_path))),
         list(type = "input_image", image_url = image_data)
      )
      
      image_message_entry <- list(
         id = image_msg_id,
         role = "user",
         content = image_content,
         related_to = function_output_id
      )
   }
   
   return(list(
      function_call_output = function_call_output,
      function_output_id = function_output_id,
      image_message_entry = image_message_entry,
      image_msg_id = image_msg_id
   ))
})

.rs.addFunction("handle_edit_file", function(function_call, current_log, related_to_id, request_id) {
   arguments <- .rs.safe_parse_function_arguments(function_call)

   filename <- arguments$filename
   keyword <- arguments$keyword
   index <- arguments$index
   start_line <- arguments$start_line
   end_line <- arguments$end_line
   insert_line <- arguments$insert_line
   
   file_content <- NULL
   
   # Handle insert_line mode - return empty content since nothing should be shown for editing
   if (!is.null(insert_line)) {
      file_content <- ""
   } else {
      # Use effective file content (editor if open, otherwise disk)
      effective_content <- .rs.get_effective_file_content(filename)
      
      if (is.null(effective_content)) {
         # File doesn't exist and isn't open in editor
         all_lines <- character(0)
      } else {
         all_lines <- strsplit(effective_content, "\n")[[1]]
      }
      
      # Handle line range mode
      if (!is.null(start_line) && !is.null(end_line)) {
         # Extract only the specified line range
         if (start_line <= length(all_lines) && end_line <= length(all_lines) && start_line <= end_line) {
            file_content <- paste(all_lines[start_line:end_line], collapse = "\n")
         } else {
            file_content <- ""  # Invalid range, return empty
         }
      } else if (!is.null(keyword)) {
         if (keyword == "start") {
            # Return from beginning of file
            file_content <- paste(all_lines, collapse = "\n")
         } else if (keyword == "end") {
            # Return from end of file
            file_content <- paste(all_lines, collapse = "\n")
         } else if (keyword == basename(filename)) {
            # Return entire file content without any footer processing
            file_content <- paste(all_lines, collapse = "\n")
         } else {
            # Look for specific keyword/symbol in file
            symbol_results <- .rs.find_symbol(keyword)
            
            if (!is.null(symbol_results) && length(symbol_results) > 0) {
               file_symbols <- list()
               for (symbol in symbol_results) {
                  if (!is.null(symbol$file) && normalizePath(symbol$file, winslash = "/", mustWork = FALSE) == normalizePath(filename, winslash = "/", mustWork = FALSE)) {
                     file_symbols <- c(file_symbols, list(symbol))
                  }
               }
               
               if (length(file_symbols) > 0) {
                  symbol_names <- sapply(file_symbols, function(s) s$name)
                  has_duplicate_symbols <- any(duplicated(symbol_names))
                  
                  selected_symbol <- NULL
                  if (has_duplicate_symbols && !is.null(index) && is.numeric(index) && 
                      index > 0 && index <= length(file_symbols)) {
                     selected_symbol <- file_symbols[[index]]
                  } else {
                     selected_symbol <- file_symbols[[1]]
                  }
                  
                  if (!is.null(selected_symbol$line_start) && !is.null(selected_symbol$line_end) && 
                      selected_symbol$line_start > 0 && selected_symbol$line_end > 0) {
                     if (selected_symbol$line_start <= length(all_lines)) {
                        adjusted_end <- min(selected_symbol$line_end, length(all_lines))
                        file_content <- paste(all_lines[selected_symbol$line_start:adjusted_end], collapse = "\n")
                        start_line <- selected_symbol$line_start
                        end_line <- adjusted_end
                     }
                  }
               }
            } else {
               # Fallback: search for keyword as plain text
               matching_lines <- which(grepl(keyword, all_lines, fixed = TRUE))
               if (length(matching_lines) > 0) {
                  line_number <- matching_lines[1]
                  enclosing_block <- .rs.find_enclosing_code_block(filename, line_number)
                  if (!is.null(enclosing_block)) {
                     file_content <- enclosing_block$content
                     start_line <- enclosing_block$start_line
                     end_line <- enclosing_block$end_line
                  } else {
                     file_content <- all_lines[line_number]
                     start_line <- line_number
                     end_line <- line_number
                  }
               }
            }
         }
      } else {
         # No keyword provided, return entire file
         file_content <- paste(all_lines, collapse = "\n")
      }
   }

   function_output_id <- .rs.get_next_message_id()
   function_call_output <- list(
     id = function_output_id,
     type = "function_call_output",
     call_id = function_call$call_id,
     output = if (is.null(file_content)) "" else file_content,
     related_to = function_call$msg_id,
     start_line = start_line,
     end_line = end_line,
     insert_line = insert_line
   )
   
   return(list(
      function_call_output = function_call_output,
      function_output_id = function_output_id
   ))
})

.rs.addFunction("handle_grep_search", function(function_call, current_log, related_to_id, request_id) {
   arguments <- .rs.safe_parse_function_arguments(function_call)
   
   query <- arguments$query
   
   # Validate required parameters
   if (is.null(query) || query == "") {
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = "Error: query parameter is required for grep_search",
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   if (is.null(arguments$case_sensitive)) {
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = "Error: case_sensitive parameter is required for grep_search",
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   cwd <- getwd()
   
   case_flag <- if (!arguments$case_sensitive) "-i" else ""
   
   # Get ripgrep binary path (same query as pandoc)
   rg_exe <- if (.rs.platform.isWindows) "rg.exe" else "rg"
   rg_binary <- file.path(Sys.getenv("RSTUDIO_RIPGREP"), rg_exe)
   
   # Build arguments vector for cross-platform compatibility
   args <- c("-n")
   if (case_flag != "") args <- c(args, case_flag)
   
   # Handle include querys - split by semicolons and commas, each gets its own -g
   if (!is.null(arguments$include_pattern) && arguments$include_pattern != "") {
      include_patterns <- strsplit(arguments$include_pattern, "[;,| ]")[[1]]
      include_patterns <- trimws(include_patterns)  # Remove whitespace
      include_patterns <- include_patterns[include_patterns != ""]  # Remove empty patterns
      for (pattern in include_patterns) {
         # Check if this is a file extension pattern (*.ext)
         if (grepl("^\\*\\.[a-zA-Z]+$", pattern)) {
            ext <- sub("^\\*\\.", "", pattern)
            # Add lowercase, uppercase, and first-letter-capitalized versions
            args <- c(args, "-g", paste0("*.", tolower(ext)))
            if (tolower(ext) != toupper(ext)) {  # Only add uppercase if different
               args <- c(args, "-g", paste0("*.", toupper(ext)))
            }
            # Add first-letter-capitalized version if different from others
            first_cap <- paste0(toupper(substr(ext, 1, 1)), tolower(substr(ext, 2, nchar(ext))))
            if (first_cap != tolower(ext) && first_cap != toupper(ext)) {
               args <- c(args, "-g", paste0("*.", first_cap))
            }
         } else {
            args <- c(args, "-g", pattern)
         }
      }
   }
   
   # Handle exclude patterns - split by semicolons and commas, each gets its own -g !
   if (!is.null(arguments$exclude_pattern) && arguments$exclude_pattern != "") {
      exclude_patterns <- strsplit(arguments$exclude_pattern, "[;,]")[[1]]
      exclude_patterns <- trimws(exclude_patterns)  # Remove whitespace
      exclude_patterns <- exclude_patterns[exclude_patterns != ""]  # Remove empty patterns
      for (pattern in exclude_patterns) {
         # Check if this is a file extension pattern (*.ext)
         if (grepl("^\\*\\.[a-zA-Z]+$", pattern)) {
            ext <- sub("^\\*\\.", "", pattern)
            # Add lowercase, uppercase, and first-letter-capitalized versions
            args <- c(args, "-g", paste0("!*.", tolower(ext)))
            if (tolower(ext) != toupper(ext)) {  # Only add uppercase if different
               args <- c(args, "-g", paste0("!*.", toupper(ext)))
            }
            # Add first-letter-capitalized version if different from others
            first_cap <- paste0(toupper(substr(ext, 1, 1)), tolower(substr(ext, 2, nchar(ext))))
            if (first_cap != tolower(ext) && first_cap != toupper(ext)) {
               args <- c(args, "-g", paste0("!*.", first_cap))
            }
         } else {
            args <- c(args, "-g", paste0("!", pattern))
         }
      }
   }
   
   args <- c(args, query, cwd)

   # Parse include and exclude patterns for open document filtering
   include_patterns <- NULL
   exclude_patterns <- NULL
   
   if (!is.null(arguments$include_pattern) && arguments$include_pattern != "") {
      include_patterns <- strsplit(arguments$include_pattern, "[;,| ]")[[1]]
      include_patterns <- trimws(include_patterns)  # Remove whitespace
      include_patterns <- include_patterns[include_patterns != ""]  # Remove empty patterns
   }
   
   if (!is.null(arguments$exclude_pattern) && arguments$exclude_pattern != "") {
      exclude_patterns <- strsplit(arguments$exclude_pattern, "[;,]")[[1]]
      exclude_patterns <- trimws(exclude_patterns)  # Remove whitespace
      exclude_patterns <- exclude_patterns[exclude_patterns != ""]  # Remove empty patterns
   }

   # First search in open documents (editor content) with include/exclude filtering
   open_doc_results <- .rs.grep_in_open_documents(query, !is.null(arguments$case_sensitive) && arguments$case_sensitive, include_patterns, exclude_patterns)
   
   result <- processx::run(rg_binary, args, timeout = 5, error_on_status = FALSE)
   if (result$timeout) {
      grep_results <- "Results:\n\n_error: ripgrep timed out. Concisely ask the user to set a more specific working directory with setwd()."
   } else {
      file_content <- result$stdout
      
      # Check if we have open document results even if no disk results
      if (nchar(file_content) == 0 && length(open_doc_results) == 0) {
         grep_results <- paste0("Results:\n\nNo matches")
      } else {
         matches <- strsplit(file_content, "\n")[[1]]
         
         match_count_note <- ""
         if (length(matches) > 50) {
            match_count_note <- paste0("\n(Showing 50 of ", length(matches), " matches)")
            matches <- matches[1:50]
         }
         
         results <- list()
         
         # Start with open document results (these take precedence)
         for (file_path in names(open_doc_results)) {
            for (match_info in open_doc_results[[file_path]]) {
               if (is.null(results[[file_path]])) {
                  results[[file_path]] <- character(0)
               }
               results[[file_path]] <- c(results[[file_path]], 
                                        paste0("Line ", match_info$line, ": ", match_info$content, " [EDITOR]"))
            }
         }
         
         # Add disk results, but only for files not already found in editor
         for (match in matches) {
            if (match == "") next
            
            parts <- strsplit(match, ":", fixed = TRUE)[[1]]
            
            if (length(parts) >= 3) {
               filepath <- parts[1]
               line_num <- parts[2]
               content <- paste(parts[-(1:2)], collapse = ":")
               
               relative_path <- gsub(paste0("^", cwd, "/"), "", filepath)
               
               # Skip if we already have editor results for this file
               if (!is.null(open_doc_results[[relative_path]])) {
                  next
               }
               
               if (grepl("\\.(png|jpg|jpeg|gif|bmp|ico|pdf|zip|tar|gz|rar|7z|exe|dll|so|dylib)$", relative_path, ignore.case = TRUE)) {
                  next
               }
               
               content_len <- nchar(content)
               if (content_len > 100) {
                  match_pos <- regexpr(query, content, ignore.case = TRUE, perl = TRUE)[1]
                  
                  start_pos <- max(1, match_pos - 30)
                  end_pos <- min(content_len, match_pos + 30)
                  
                  first_part <- substr(content, 1, 20)
                  middle_part <- substr(content, start_pos, end_pos)
                  last_part <- substr(content, content_len - 19, content_len)
                  
                  content <- paste0(first_part, "...", middle_part, "...", last_part)
               }
               
               if (is.null(results[[relative_path]])) {
                  results[[relative_path]] <- character(0)
               }
               
               results[[relative_path]] <- c(results[[relative_path]], 
                                            paste0("Line ", line_num, ": ", content))
            }
         }
         
         if (length(results) > 0) {
            result_lines <- c(paste0("Results:", match_count_note))
            
            for (file in names(results)) {
               result_lines <- c(result_lines, paste0("\nFile: ", file))
               result_lines <- c(result_lines, results[[file]])
            }
            
            grep_results <- paste(result_lines, collapse = "\n")
         } else {
            grep_results <- paste0("Results:\n\nNo matches")
         }
      }
   }
   
   function_output_id <- .rs.get_next_message_id()
   function_call_output <- list(
     id = function_output_id,
     type = "function_call_output",
     call_id = function_call$call_id,
     output = grep_results,
     related_to = function_call$msg_id
   )
   
   return(list(
      function_call_output = function_call_output,
      function_output_id = function_output_id
   ))
})

.rs.addFunction("handle_read_file", function(function_call, current_log, related_to_id, request_id) {
   
   tryCatch({
      arguments <- .rs.safe_parse_function_arguments(function_call)
   }, error = function(e) {
      stop(e)
   })
   
   file_path <- arguments$filename
   shouldReadEntireFile <- arguments$should_read_entire_file
   startLine <- arguments$start_line_one_indexed
   endLine <- arguments$end_line_one_indexed_inclusive
   
   # Validate that startLine is not NULL or empty
   if (is.null(startLine) || length(startLine) == 0) {
      startLine <- 1  # Default to line 1 if startLine is missing
   }
   
   # Validate that endLine is not NULL or empty
   if (is.null(endLine) || length(endLine) == 0) {
      endLine <- startLine + 199  # Default to reading 200 lines if endLine is missing
   }
   
   baseMaxLines <- 50
   baseMaxChars <- 5000
   
   absoluteMaxLines <- 250
   absoluteMaxChars <- 25000
   
   maxLines <- baseMaxLines
   maxChars <- baseMaxChars
   
   prevReadSameFile <- FALSE
   prevMaxLines <- baseMaxLines
   
   for (i in length(current_log):1) {
      if (!is.null(current_log[[i]]$function_call) && 
          !is.null(current_log[[i]]$function_call$name) && 
          (current_log[[i]]$function_call$name == "read_file" || current_log[[i]]$function_call$name == "read_file_lines")) {
         
         prevArgs <- tryCatch({
            .rs.safe_parse_function_arguments(current_log[[i]])
         }, error = function(e) {
            NULL
         })
         
         prev_file_path <- if (!is.null(prevArgs$filename)) prevArgs$filename else prevArgs$file_path
         
         if (!is.null(prevArgs) && !is.null(prev_file_path) && prev_file_path == file_path) {
            prevReadSameFile <- TRUE
            
            for (j in i:length(current_log)) {
               if (!is.null(current_log[[j]]$type) && 
                  current_log[[j]]$type == "function_call_output" && 
                  current_log[[j]]$call_id == current_log[[i]]$function_call$call_id) {
                  
                  output <- current_log[[j]]$output
                  maxLinesMatch <- regexpr("Truncated due to length at line ([0-9]+)", output)
                  
                  if (maxLinesMatch > 0) {
                     truncLine <- as.integer(sub(".*Truncated due to length at line ([0-9]+).*", "\\1", regmatches(output, maxLinesMatch)))
                     
                     prevStartLine <- if (!is.null(prevArgs$start_line_one_indexed)) prevArgs$start_line_one_indexed else prevArgs$start_line
                     
                     if (!is.na(truncLine) && !is.null(prevStartLine) && truncLine > prevStartLine) {
                        line_count <- truncLine - prevStartLine
                        if (line_count > prevMaxLines) {
                           prevMaxLines <- line_count
                        }
                     }
                  } else {
                     linesMatch <- regexpr("Lines ([0-9]+)-([0-9]+)", output)
                     if (linesMatch > 0) {
                        linesStr <- regmatches(output, linesMatch)
                        startEndLines <- as.integer(strsplit(gsub("Lines ([0-9]+)-([0-9]+)", "\\1,\\2", linesStr), ",")[[1]])
                        if (length(startEndLines) == 2) {
                           line_count <- startEndLines[2] - startEndLines[1] + 1
                           if (line_count > prevMaxLines) {
                              prevMaxLines <- line_count
                           }
                        }
                     }
                  }
                  
                  break
               }
            }
         }
      }
   }
   
   if (prevReadSameFile) {
      maxLines <- prevMaxLines * 2
      maxChars <- baseMaxChars * (maxLines / baseMaxLines)
      
      if (maxLines > absoluteMaxLines) {
         maxLines <- absoluteMaxLines
         maxChars <- absoluteMaxChars
      }
   }
   
   # First try to get content from editor (handles both saved and unsaved files)
   effective_content <- .rs.get_effective_file_content(file_path)
   
   # If not found in editor and file doesn't exist on disk, try symbol search
   # BUT only for regular files - for __UNSAVED_ files, respect the full identifier
   if (is.null(effective_content) && !file.exists(file_path)) {
      found_file <- NULL
      
      # For unsaved files with __UNSAVED_ pattern, don't fall back to basename search
      # as this would incorrectly match other unsaved files with the same basename
      if (!startsWith(file_path, "__UNSAVED")) {
         # Only use basename fallback for regular files
         base_filename <- basename(file_path)
         
         symbol_results <- .rs.find_symbol(base_filename)
         
         file_symbols <- list()
         if (!is.null(symbol_results)) {
            for (symbol in symbol_results) {
               if (!is.null(symbol$file)) {
                  # Include both disk files and unsaved files (which may have __UNSAVED__ paths)
                  if (file.exists(symbol$file) || .rs.is_file_open_in_editor(symbol$file)) {
                     file_symbols <- c(file_symbols, list(symbol))
                  }
               }
            }
         }
         
         if (length(file_symbols) == 1) {
            found_file <- file_symbols[[1]]$file
            # Try again with the found file path
            effective_content <- .rs.get_effective_file_content(found_file)
            if (!is.null(effective_content)) {
               file_path <- found_file
            }
         }
      }
      
      if (is.null(effective_content)) {
         file_content <- paste0("Error: File not found, try using your tools to look elsewhere for: ", file_path)
         # Set end_line_to_read for error case
         end_line_to_read <- startLine
      }
   }
   
   if (!exists("file_content") && !is.null(effective_content)) {
      all_lines <- strsplit(effective_content, "\n")[[1]]
      
      if (!is.null(shouldReadEntireFile) && shouldReadEntireFile) {
         result <- paste(all_lines, collapse = "\n")
         header <- paste0("File: ", file_path, "\nEntire file content (", length(all_lines), " total lines):\n\n")
         file_content <- paste0(header, result)
         # Set line range for entire file
         startLine <- 1
         end_line_to_read <- length(all_lines)
      } else {
         if (startLine < 1) {
            startLine <- 1
         }
         if (endLine > length(all_lines)) {
            endLine <- length(all_lines)
         }
         if (startLine > endLine) {
            file_content <- paste0("Error: Invalid line range. Start line (", startLine, ") is greater than end line (", endLine, ").")
            # Set end_line_to_read for error case
            end_line_to_read <- startLine
         } else {
            # When user explicitly provides both start and end lines, respect their exact request
            # Only apply automatic expansion logic for cases where the request is ambiguous
            user_requested_range <- endLine - startLine + 1
            should_respect_exact_range <- !is.null(startLine) && !is.null(endLine) && user_requested_range <= 50
            
            if (should_respect_exact_range) {
               # User requested a small specific range - give them exactly what they asked for
               end_line_to_read <- endLine
               lines_to_read <- user_requested_range
               
               requested_lines <- all_lines[startLine:end_line_to_read]
               result <- paste(requested_lines, collapse = "\n")
               
               # No truncation for exact ranges - user gets exactly what they asked for
               header <- paste0("File: ", file_path, "\nLines ", startLine, "-", end_line_to_read, " (of ", length(all_lines), " total lines):\n\n")
               file_content <- paste0(header, result)
            } else {
               # Apply the existing logic for larger ranges or ambiguous requests
               lines_to_read <- min(endLine - startLine + 1, maxLines)
               
               if ((endLine - startLine + 1) >= 200 && lines_to_read < 200) {
                  lines_to_read <- 200
               }
               
               end_line_to_read <- startLine + lines_to_read - 1
               
               requested_lines <- all_lines[startLine:end_line_to_read]
               
               total_chars <- sum(nchar(requested_lines)) + length(requested_lines)
               
               result <- paste(requested_lines, collapse = "\n")
               
               if (end_line_to_read < endLine || total_chars > maxChars) {
               if (total_chars > maxChars) {
                  chars_count <- 0
                  lines_included <- 0
                  for (i in 1:length(requested_lines)) {
                     line_len <- nchar(requested_lines[i]) + 1
                     if (chars_count + line_len <= maxChars) {
                        chars_count <- chars_count + line_len
                        lines_included <- i
                     } else {
                        break
                     }
                  }
                  
                           if (lines_included == 0) {
                     lines_included <- 1
                  }
                  requested_lines <- requested_lines[1:lines_included]
                  result <- paste(requested_lines, collapse = "\n")
                  truncated_line <- startLine + lines_included
               } else {
                  truncated_line <- end_line_to_read + 1
               }
               
                  result <- paste0(result, "\n\n...[Truncated due to length at line ", truncated_line, ". If more lines are needed, start reading from here. The number of lines you can read doubles on each call.]")
               }
               
               header <- paste0("File: ", file_path, "\nLines ", startLine, "-", end_line_to_read, " (of ", length(all_lines), " total lines):\n\n")
               
               file_content <- paste0(header, result)
            }
         }
      }
   }
   
   function_output_id <- .rs.get_next_message_id()
   function_call_output <- list(
      id = function_output_id,
      type = "function_call_output",
      call_id = function_call$call_id,
      output = file_content,
      related_to = function_call$msg_id,
      start_line = if (exists("startLine")) startLine else 1,
      end_line = if (exists("end_line_to_read")) end_line_to_read else NULL
   )
   
   return(list(
      function_call_output = function_call_output,
      function_output_id = function_output_id
   ))
})

.rs.addFunction("handle_list_dir", function(function_call, current_log, related_to_id, request_id) {
   tryCatch({
      arguments <- .rs.safe_parse_function_arguments(function_call)
   }, error = function(e) {
      cat("ERROR: handle_list_dir argument parsing failed:", e$message, "\n")
      stop(e)
   })
   
   dir_path <- arguments$relative_workspace_path
   
   cwd <- getwd()
   full_path <- if (dir_path == "." || dir_path == "") {
      cwd
   } else {
      file.path(cwd, dir_path)
   }
   
   full_path <- normalizePath(full_path, winslash = "/", mustWork = FALSE)
   if (!dir.exists(full_path)) {
      dirListing <- paste0("Error: Directory not found: ", dir_path)
   } else {
      files <- list.files(full_path, full.names = FALSE, all.files = TRUE, include.dirs = TRUE)
      
      if (length(files) == 0) {
         dirListing <- paste0("Directory: ", dir_path, "\n\n(empty directory)")
      } else {
         file_info <- file.info(file.path(full_path, files))
         
         result_lines <- c(paste0("Directory: ", dir_path, "\n"))
         
         file_info$name <- files
         file_info$is_dir <- file_info$isdir
         file_info$is_dir[is.na(file_info$is_dir)] <- FALSE
         
         ordered <- order(file_info$is_dir, decreasing = TRUE, file_info$name)
         
         for (i in ordered) {
            file_name <- files[i]
            info <- file_info[i, ]
            
            if (file_name %in% c(".", "..")) {
               next
            }
            
            if (!is.na(info$isdir) && info$isdir) {
               result_lines <- c(result_lines, paste0("  ", file_name, "/"))
            } else {
               if (!is.na(info$size)) {
                  size_strr <- if (info$size < 1024) {
                     paste0(info$size, "B")
                  } else if (info$size < 1024^2) {
                     paste0(round(info$size / 1024, 1), "KB")
                  } else if (info$size < 1024^3) {
                     paste0(round(info$size / 1024^2, 1), "MB")
                  } else {
                     paste0(round(info$size / 1024^3, 1), "GB")
                  }
                  result_lines <- c(result_lines, paste0("  ", file_name, " (", size_strr, ")"))
               } else {
                  result_lines <- c(result_lines, paste0("  ", file_name))
               }
            }
         }
         
         dirListing <- paste(result_lines, collapse = "\n")
      }
   }
   
   function_output_id <- .rs.get_next_message_id()
   function_call_output <- list(
     id = function_output_id,
     type = "function_call_output",
     call_id = function_call$call_id,
     output = dirListing,
     related_to = function_call$msg_id
   )
   
   return(list(
      function_call_output = function_call_output,
      function_output_id = function_output_id
   ))
})

.rs.addFunction("handle_search_for_file", function(function_call, current_log, related_to_id, request_id) {
   arguments <- .rs.safe_parse_function_arguments(function_call)
   
   query <- arguments$query
   
   cwd <- getwd()
   
   all_files <- list.files(cwd, recursive = TRUE, full.names = TRUE, all.files = FALSE, include.dirs = FALSE)
   
   relative_files <- gsub(paste0("^", cwd, "/"), "", all_files)
   
   relative_files <- relative_files[!grepl("^\\.", relative_files)]
   relative_files <- relative_files[!grepl("/\\.", relative_files)]
   relative_files <- relative_files[!grepl("\\.(log|tmp|cache|bak)$", relative_files, ignore.case = TRUE)]
   
   scores <- sapply(relative_files, function(filepath) {
      lower_path <- tolower(filepath)
      lower_query <- tolower(query)
      
      score <- 0
      
      if (grepl(lower_query, lower_path, fixed = TRUE)) {
         score <- score + 100
         
         filename <- basename(lower_path)
         if (grepl(lower_query, filename, fixed = TRUE)) {
            score <- score + 50
         }
         
         if (startsWith(filename, lower_query)) {
            score <- score + 25
         }
      }
      
      query_chars <- strsplit(lower_query, "")[[1]]
      path_chars <- strsplit(lower_path, "")[[1]]
      
      query_index <- 1
      for (i in seq_along(path_chars)) {
         if (query_index <= length(query_chars) && path_chars[i] == query_chars[query_index]) {
            score <- score + 1
            query_index <- query_index + 1
         }
      }
      
      if (query_index > length(query_chars)) {
         score <- score + 10
      }
      
      path_depth <- length(strsplit(filepath, "/")[[1]])
      score <- score - path_depth
      
      return(score)
   })
   
   valid_matches <- relative_files[scores > 0]
   valid_scores <- scores[scores > 0]
   
   if (length(valid_matches) == 0) {
      search_results <- paste0("No files found matching: ", query)
   } else {
      sorted_indices <- order(valid_scores, decreasing = TRUE)
      top_matches <- valid_matches[sorted_indices[1:min(10, length(sorted_indices))]]
      top_scores <- valid_scores[sorted_indices[1:min(10, length(sorted_indices))]]
      
      result_lines <- c(paste0("File search results for '", query, "':"))
      
      for (i in seq_along(top_matches)) {
         full_path <- file.path(cwd, top_matches[i])
         file_info <- file.info(full_path)
         
         size_strr <- if (!is.na(file_info$size)) {
            if (file_info$size < 1024) {
               paste0(file_info$size, "B")
            } else if (file_info$size < 1024^2) {
               paste0(round(file_info$size / 1024, 1), "KB")
            } else if (file_info$size < 1024^3) {
               paste0(round(file_info$size / 1024^2, 1), "MB")
            } else {
               paste0(round(file_info$size / 1024^3, 1), "GB")
            }
         } else {
            "unknown size"
         }
         
         result_lines <- c(result_lines, paste0(i, ". ", top_matches[i], " (", size_strr, ")"))
      }
      
      if (length(valid_matches) > 10) {
         result_lines <- c(result_lines, paste0("\n(Showing top 10 of ", length(valid_matches), " matches)"))
      }
      
      search_results <- paste(result_lines, collapse = "\n")
   }
   
   function_output_id <- .rs.get_next_message_id()
   function_call_output <- list(
     id = function_output_id,
     type = "function_call_output",
     call_id = function_call$call_id,
     output = search_results,
     related_to = function_call$msg_id
   )
   
   return(list(
      function_call_output = function_call_output,
      function_output_id = function_output_id
   ))
})

.rs.addFunction("handle_delete_file", function(function_call, current_log, related_to_id, request_id) {
   arguments <- .rs.safe_parse_function_arguments(function_call)
   
   target_file <- arguments$filename
   explanation <- if (!is.null(arguments$explanation)) arguments$explanation else paste("Deleting file:", target_file)
   
   cwd <- getwd()
   
   if (startsWith(target_file, "/")) {
      # Return error immediately for absolute paths
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = "Error: Absolute paths not allowed for security reasons. Use relative paths only.",
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   full_path <- file.path(cwd, target_file)
   normalized_path <- normalizePath(full_path, winslash = "/", mustWork = FALSE)
   
   if (!startsWith(normalized_path, normalizePath(cwd, winslash = "/", mustWork = FALSE))) {
      # Return error immediately for path traversal
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = "Error: Path traversal detected. File must be within the current working directory.",
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   file_name <- basename(normalized_path)
   if (file_name %in% c(".", "..", ".git", ".gitignore", "README.md", "LICENSE")) {
      # Return error immediately for protected files
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = paste0("Error: Cannot delete protected file: ", file_name),
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   if (dir.exists(normalized_path)) {
      # Return error immediately for directories
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = "Error: Cannot delete directories. Use appropriate directory deletion tools instead.",
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   unlink_command <- paste0("unlink(\"", target_file, "\")")
   
   normalized_function_call <- list(
      name = if (is.list(function_call$name)) function_call$name[[1]] else function_call$name,
      arguments = if (is.list(function_call$arguments)) function_call$arguments[[1]] else function_call$arguments,
      call_id = if (is.list(function_call$call_id)) function_call$call_id[[1]] else function_call$call_id
   )
   
   result <- list(
      command = unlink_command,
      related_to_id = related_to_id,
      is_console = TRUE,
      breakout_of_function_calls = TRUE
   )
   
   return(result)
})

.rs.addFunction("handle_run_file", function(function_call, current_log, related_to_id, request_id) {
   arguments <- .rs.safe_parse_function_arguments(function_call)
   
   target_file <- arguments$filename
   start_line <- arguments$start_line_one_indexed
   end_line <- arguments$end_line_one_indexed_inclusive
   
   # Create custom explanation with line range like in conversation reload
   if (!is.null(start_line) && !is.null(end_line)) {
      explanation <- paste0("Running: ", basename(target_file), " (", start_line, "-", end_line, ")")
   } else if (!is.null(start_line)) {
      explanation <- paste0("Running: ", basename(target_file), " (", start_line, "-end)")
   } else if (!is.null(end_line)) {
      explanation <- paste0("Running: ", basename(target_file), " (1-", end_line, ")")
   } else {
      explanation <- paste0("Running: ", basename(target_file))
   }
   
   cwd <- getwd()
   
   full_path <- file.path(cwd, target_file)
   normalized_path <- normalizePath(full_path, winslash = "/", mustWork = FALSE)
   
   if (!startsWith(normalized_path, normalizePath(cwd, winslash = "/", mustWork = FALSE))) {
      # Return error immediately for path traversal
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = "Error: Path traversal detected. File must be within the current working directory.",
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   # Read file content using effective content (editor if open, otherwise disk)
   effective_content <- .rs.get_effective_file_content(target_file)
   
   # Check if file exists on disk only if not found in editor
   if (is.null(effective_content) && !file.exists(normalized_path)) {
      # Return error for non-existent files that are not open in editor
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = paste0("Error: File does not exist: ", target_file),
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   if (file.exists(normalized_path) && dir.exists(normalized_path)) {
      # Return error immediately for directories
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = "Error: Cannot run directories. Specify a file instead.",
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   if (is.null(effective_content)) {
      # Return error for unreadable files
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = paste0("Error: Cannot read file: ", target_file),
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   # Split content into lines for line range processing
   file_content <- strsplit(effective_content, "\n")[[1]]
   
   if (is.list(file_content)) return(file_content)  # Error case
   
   # Apply line range if specified
   if (!is.null(start_line) || !is.null(end_line)) {
      total_lines <- length(file_content)
      start_line <- if (is.null(start_line)) 1 else max(1, as.integer(start_line))
      end_line <- if (is.null(end_line)) total_lines else min(total_lines, as.integer(end_line))
      
      if (start_line > total_lines) {
         function_output_id <- .rs.get_next_message_id()
         function_call_output <- list(
           id = function_output_id,
           type = "function_call_output",
           call_id = function_call$call_id,
           output = paste0("Error: Start line ", start_line, " exceeds file length (", total_lines, " lines)"),
           related_to = function_call$msg_id
         )
         
         return(list(
            function_call_output = function_call_output,
            function_output_id = function_output_id
         ))
      }
      
      file_content <- file_content[start_line:end_line]
   }
   
   # Check if this is an R Markdown file and extract code chunks if so
   file_ext <- tolower(tools::file_ext(target_file))
   if (file_ext %in% c("rmd", "qmd")) {
      # Extract only R code chunks from the content
      code_content <- .rs.extract_r_code_from_rmd(file_content)
      command <- paste(code_content, collapse = "\n")
   } else {
      # For regular files, use all content
      command <- paste(file_content, collapse = "\n")
   }
   
   if (nchar(trimws(command)) == 0) {
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
        id = function_output_id,
        type = "function_call_output",
        call_id = function_call$call_id,
        output = "Error: No executable code found in the specified file or range.",
        related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   normalized_function_call <- list(
      name = if (is.list(function_call$name)) function_call$name[[1]] else function_call$name,
      arguments = if (is.list(function_call$arguments)) function_call$arguments[[1]] else function_call$arguments,
      call_id = if (is.list(function_call$call_id)) function_call$call_id[[1]] else function_call$call_id
   )
   
   result <- list(
      command = command,
      explanation = explanation,
      related_to_id = related_to_id,
      is_console = TRUE,
      breakout_of_function_calls = TRUE
   )
   
   return(result)
})

.rs.addFunction("extract_r_code_from_rmd", function(file_lines) {
   code_lines <- character(0)
   in_r_chunk <- FALSE
   
   for (line in file_lines) {
      # Check for R code chunk start
      if (grepl("^```\\{r", line) || grepl("^```r\\s*$", line)) {
         in_r_chunk <- TRUE
         next
      }
      
      # Check for code chunk end
      if (grepl("^```\\s*$", line)) {
         in_r_chunk <- FALSE
         next
      }
      
      # If we're in an R chunk, collect the line
      if (in_r_chunk) {
         code_lines <- c(code_lines, line)
      }
   }
   
   return(code_lines)
})

.rs.addFunction("handle_run_terminal_cmd", function(function_call, current_log, related_to_id, request_id) {
   arguments <- .rs.safe_parse_function_arguments(function_call)
   
   command <- arguments$command
   explanation <- if (!is.null(arguments$explanation)) arguments$explanation else "Running terminal command"
   
   if (is.null(command) || command == "") {
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
         id = function_output_id,
         type = "function_call_output",
         call_id = function_call$call_id,
         output = "Error: Empty command provided",
         related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   # Remove triple backticks with shell/bash language specifiers
   trimmed_command <- gsub("^```(?:shell|bash|sh)?\\s*\\n?", "", command, perl = TRUE)
   trimmed_command <- gsub("\\n?```\\s*$", "", trimmed_command, perl = TRUE)
   # Remove any remaining instances of ```\n that weren't caught by the querys above
   trimmed_command <- gsub("```\\n", "", trimmed_command, perl = TRUE)
   trimmed_command <- trimws(trimmed_command)
   
   conversation_index <- .rs.get_current_conversation_index()
   
   normalized_function_call <- list(
      name = if (is.list(function_call$name)) function_call$name[[1]] else function_call$name,
      arguments = if (is.list(function_call$arguments)) function_call$arguments[[1]] else function_call$arguments,
      call_id = if (is.list(function_call$call_id)) function_call$call_id[[1]] else function_call$call_id
   )
      
   result <- list(
      command = trimmed_command,
      related_to_id = related_to_id,
      is_console = FALSE,
      breakout_of_function_calls = TRUE
   )
   
   return(result)
})

.rs.addFunction("handle_run_console_cmd", function(function_call, current_log, related_to_id, request_id) {
   
   arguments <- .rs.safe_parse_function_arguments(function_call)
   
   command <- arguments$command
   explanation <- if (!is.null(arguments$explanation)) arguments$explanation else "Running R console command"
   
   if (is.null(command) || command == "") {
      
      function_output_id <- .rs.get_next_message_id()
      function_call_output <- list(
         id = function_output_id,
         type = "function_call_output",
         call_id = function_call$call_id,
         output = "Error: Empty R command provided",
         related_to = function_call$msg_id
      )
      
      return(list(
         function_call_output = function_call_output,
         function_output_id = function_output_id
      ))
   }
   
   trimmed_command <- gsub("^```[rR]?[mM]?[dD]?\\s*\\n?", "", command, perl = TRUE)
   trimmed_command <- gsub("\\n?```\\s*$", "", trimmed_command, perl = TRUE)
   trimmed_command <- gsub("```\\n", "", trimmed_command, perl = TRUE)
   trimmed_command <- trimws(trimmed_command)
   
   conversation_index <- .rs.get_current_conversation_index()
   
   normalized_function_call <- list(
      name = if (is.list(function_call$name)) function_call$name[[1]] else function_call$name,
      arguments = if (is.list(function_call$arguments)) function_call$arguments[[1]] else function_call$arguments,
      call_id = if (is.list(function_call$call_id)) function_call$call_id[[1]] else function_call$call_id
   )
      
   result <- list(
      command = trimmed_command,
      related_to_id = related_to_id,
      is_console = TRUE,
      breakout_of_function_calls = TRUE
   )
   
   return(result)
})