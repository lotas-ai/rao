#
# SessionAiAPI.R
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

.rs.addFunction("get_open_source_documents", function() {
  tryCatch({
    # Get the currently active document context
    active_doc <- .rs.api.getSourceEditorContext()
    
    if (is.null(active_doc)) {
      return(list())
    }
    
    # For now, this function returns just the active document
    # In a full implementation, we'd need a way to get ALL open documents
    # but RStudio's API doesn't seem to expose that directly
    
    result <- list()
    
    if (!is.null(active_doc$id) && !is.null(active_doc$path) && !is.null(active_doc$contents)) {
      if (nzchar(active_doc$path) && nzchar(active_doc$contents)) {
        doc_info <- list(
          id = active_doc$id,
          path = active_doc$path, 
          contents = active_doc$contents
        )
        
        attr(doc_info, "id") <- active_doc$id
        attr(doc_info, "path") <- active_doc$path
        attr(doc_info, "contents") <- active_doc$contents
        
        result[[1]] <- doc_info
      }
    }
    
    result
    
  }, error = function(e) {
    list()
  })
})

.rs.addFunction("get_all_open_source_documents", function() {
  tryCatch({
    # Call the API function to get all open documents
    result <- .rs.api.getAllOpenDocuments(includeContents = TRUE)
    
    # Return empty list if no documents
    if (is.null(result) || length(result) == 0) {
      return(list())
    }
    
    # Get the currently active document ID
    active_doc_id <- .rs.api.documentId(allowConsole = FALSE)
    
    # First pass: collect all document paths for duplicate detection
    all_doc_paths <- character(0)
    for (i in 1:length(result)) {
      doc <- result[[i]]
      if (!is.null(doc)) {
        doc_path <- NULL
        if (!is.null(doc$path) && nzchar(doc$path)) {
          doc_path <- doc$path
        } else if (!is.null(doc$properties) && !is.null(doc$properties$tempName)) {
          # For unsaved files, check if there's an ID we can use for unique naming
          if (!is.null(doc$id) && nzchar(doc$id)) {
            doc_path <- paste0("__UNSAVED_", substr(doc$id, 1, 4), "__/", doc$properties$tempName)
          } else {
            doc_path <- paste0("__UNSAVED__/", doc$properties$tempName)
          }
        } else {
          doc_path <- "__UNSAVED__/Untitled"
        }
        all_doc_paths <- c(all_doc_paths, doc_path)
      }
    }
    
    # Transform each document to only include requested fields
    transformed_docs <- list()
    current_time <- as.numeric(Sys.time()) * 1000  # Convert to milliseconds
    
    for (i in 1:length(result)) {
      doc <- result[[i]]
      if (!is.null(doc)) {
        # Determine the full path first
        doc_path <- NULL
        if (!is.null(doc$path) && nzchar(doc$path)) {
          doc_path <- doc$path
        } else if (!is.null(doc$properties) && !is.null(doc$properties$tempName)) {
          # For unsaved files, check if there's an ID we can use for unique naming
          if (!is.null(doc$id) && nzchar(doc$id)) {
            doc_path <- paste0("__UNSAVED_", substr(doc$id, 1, 4), "__/", doc$properties$tempName)
          } else {
            doc_path <- paste0("__UNSAVED__/", doc$properties$tempName)
          }
        } else {
          doc_path <- "__UNSAVED__/Untitled"
        }
        
        # Use helper function to generate unique display name
        name <- .rs.get_unique_display_name(doc_path, all_doc_paths)
        
        # Calculate minutes since last update
        minutes_since_last_update <- 0
        if (!is.null(doc$last_content_update) && is.numeric(doc$last_content_update)) {
          time_diff_ms <- current_time - doc$last_content_update
          minutes_since_last_update <- round(time_diff_ms / (1000 * 60), 2)  # Convert to minutes
        }
        
        # Create simplified document object
        # Handle case where fields might be arrays or empty - ensure proper types
        doc_id <- if (length(doc$id) > 1) doc$id[1] else if (length(doc$id) == 0) "" else doc$id
        # Use the processed doc_path instead of the original doc$path
        doc_path_field <- if (length(doc$path) > 1) doc$path[1] else if (length(doc$path) == 0) doc_path else doc$path
        doc_type <- if (length(doc$type) > 1) doc$type[1] else if (length(doc$type) == 0) "" else doc$type
        doc_dirty <- if (length(doc$dirty) > 1) doc$dirty[1] else if (length(doc$dirty) == 0) FALSE else doc$dirty
        
        # Check if this document is the currently active one
        is_active <- !is.null(active_doc_id) && nzchar(active_doc_id) && doc_id == active_doc_id
        
        transformed_doc <- list(
          id = as.character(doc_id),
          path = as.character(doc_path_field),
          type = as.character(doc_type),
          dirty = as.logical(doc_dirty),
          name = as.character(name),
          minutes_since_last_update = as.numeric(minutes_since_last_update),
          is_active = as.logical(is_active)
        )
        
        transformed_docs[[i]] <- transformed_doc
      }
    }
    
    return(transformed_docs)
    
  }, error = function(e) {
    # Return empty list on error
    list()
  })
})

.rs.addFunction("check_message_for_symbols", function(conversation) {
  max_symbols <- 15
  
  if (length(conversation) == 0) {
    return(NULL)
  }
  
  last_user_message_index <- NULL
  for (i in length(conversation):1) {
    if (!is.null(conversation[[i]]$role) && conversation[[i]]$role == "user" &&
        !is.null(conversation[[i]]$original_query) && conversation[[i]]$original_query == TRUE) {
      last_user_message_index <- i
      break
    }
  }
  
  if (is.null(last_user_message_index)) {
    return(NULL)
  }
  
  current_request <- conversation[[last_user_message_index]]$content
  
  if (is.list(current_request)) {
    text_parts <- character(0)
    for (item in current_request) {
      if (!is.null(item$type) && item$type == "input_text" && !is.null(item$text)) {
        text_parts <- c(text_parts, item$text)
      }
    }
    current_request <- paste(text_parts, collapse = " ")
  }
  
  exclusion_patterns <- c(
    "Based on our conversation so far, suggest a short, descriptive name for this conversation",
    "Write an informative filename for the script that executes this code",
    "Should this be a new script or overwrite the previous script"
  )
  
  is_excluded <- FALSE
  for (pattern in exclusion_patterns) {
    if (grepl(pattern, current_request, ignore.case = TRUE)) {
      is_excluded <- TRUE
      break
    }
  }
  
  if (is_excluded) {
    return(NULL)
  }
  
  user_message <- conversation[[last_user_message_index]]$content
  
  if (is.list(user_message)) {
    text_parts <- character(0)
    for (item in user_message) {
      if (!is.null(item$type) && item$type == "input_text" && !is.null(item$text)) {
        text_parts <- c(text_parts, item$text)
      }
    }
    user_message <- paste(text_parts, collapse = " ")
  }
  
  words <- tryCatch({
    matches <- gregexpr("[a-z0-9_]+(\\.[a-z0-9_]+)*", tolower(user_message), perl = TRUE)
    unlist(regmatches(tolower(user_message), matches))
  }, error = function(e) {
    text <- tolower(user_message)
    for (punct in c(",", ";", ":", "!", "?", "'", "\"", "(", ")", "[", "]", "{", "}", "/", "\\", "-")) {
      text <- gsub(paste0("\\", punct), " ", text, fixed = FALSE)
    }
    text <- gsub("\\s+", " ", text)
    strsplit(trimws(text), "\\s+")[[1]]
  })
  
  small_words <- c("the", "and", "but", "for", "with", "this", "that", "you", "have", "are", "not")
  words <- words[!(words %in% small_words)]
  
  # Initialize the structured result
  result <- list(
    direct_context = list(),
    keywords = list(),
    environment_variables = list(),
    open_files = list()
  )
  
  # 1. Direct context attached by the user (with full file content for files)
  context_files <- character(0)
  
  tryCatch({
    context_items <- .rs.getVar("context_items")
    
    if (!is.null(context_items) && length(context_items) > 0) {
      # Collect all context paths for duplicate detection
      all_context_paths <- sapply(context_items, function(item) item$path)
      
      for (i in seq_along(context_items)) {
        item <- context_items[[i]]
        if (!is.null(item) && !is.null(item$path)) {
          path <- item$path          
          # Fix: Check both file existence on disk AND open in editor
          if (file.exists(path) || .rs.is_file_open_in_editor(path)) {
            context_files <- c(context_files, path)
            
            # Ensure symbol index for this directory (only for disk files)
            if (file.exists(path)) {
              file_dir <- dirname(path)
              tryCatch({
                .rs.ensure_symbol_index_for_ai_search(file_dir)
              }, error = function(e) {
              })
            }
            
            is_directory <- !is.null(item$type) && item$type == "directory"
            if (!is_directory && file.exists(path)) {
              is_directory <- file.info(path)$isdir
            }
            
            if (is_directory) {
              # For directories, list contents and find symbols
              dir_files <- tryCatch(list.files(path, full.names = FALSE), error = function(e) character(0))
              
              # Get complete symbols for the directory using find_symbol
              dir_symbols <- tryCatch({
                search_term <- basename(path)
                symbol_result <- .rs.find_symbol(search_term)
                if (!is.null(symbol_result) && length(symbol_result) > 0) {
                  # Filter results to only include symbols from the exact directory path
                  filtered_symbols <- list()
                  for (sym in symbol_result) {
                    if (!is.null(sym$file) && !is.null(sym$parent) && sym$parent == path) {
                      filtered_symbols[[length(filtered_symbols) + 1]] <- sym
                    }
                  }
                  filtered_symbols
                } else {
                  list()
                }
              }, error = function(e) {
                list()
              })
              
              result$direct_context[[length(result$direct_context) + 1]] <- list(
                type = "directory",
                name = .rs.get_unique_display_name(path, all_context_paths),
                path = path,
                contents = dir_files,
                symbols = dir_symbols
              )
            } else {
              # For files, handle differently based on whether line numbers are specified
              has_line_numbers <- !is.null(item$start_line) && !is.null(item$end_line)
              
              if (has_line_numbers) {
                # Extract only the specified lines - send as content, no symbols
                file_content <- tryCatch({
                  # Use get_effective_file_content to get content from editor if open, otherwise from disk
                  effective_content <- .rs.get_effective_file_content(path, item$start_line, item$end_line)
                  if (!is.null(effective_content)) {
                    effective_content
                  } else {
                    character(0)
                  }
                }, error = function(e) {
                  paste("Error reading file:", e$message)
                })
                
                context_item <- list(
                  type = "file",
                  name = .rs.get_unique_display_name(path, all_context_paths),
                  path = path,
                  content = paste(file_content, collapse = "\n"),
                  start_line = item$start_line,
                  end_line = item$end_line
                )                
                result$direct_context[[length(result$direct_context) + 1]] <- context_item
              } else {
                # No line numbers - use complete find_symbol results as symbols
                file_symbols <- tryCatch({
                  search_term <- basename(path)
                  symbol_result <- .rs.find_symbol(search_term)
                  if (!is.null(symbol_result) && length(symbol_result) > 0) {
                    # Filter results to only include symbols from the exact file path
                    filtered_symbols <- list()
                    for (sym in symbol_result) {
                      if (!is.null(sym$file) && sym$file == path) {
                        filtered_symbols[[length(filtered_symbols) + 1]] <- sym
                      }
                    }
                    filtered_symbols
                  } else {
                    list()
                  }
                }, error = function(e) {
                  list()
                })
                
                result$direct_context[[length(result$direct_context) + 1]] <- list(
                  type = "file",
                  name = .rs.get_unique_display_name(path, all_context_paths),
                  path = path,
                  symbols = file_symbols
                )
              }
            }
          }
        }
      }
    }
  }, error = function(e) {
    # Continue on error
  })
  
  # 2. Keywords picked up from the query (exclude context files)
  if (length(words) > 0) {
    tryCatch({
      .rs.ensure_symbol_index_for_ai_search()
    }, error = function(e) {
      # Continue on error
    })
    
    directory_symbols <- character(0)
    file_symbols <- character(0)
    function_symbols <- character(0)
    other_symbols <- character(0)
    # Tracking arrays for duplicate detection (with type info)
    directory_symbols_seen <- character(0)
    file_symbols_seen <- character(0)
    function_symbols_seen <- character(0)
    other_symbols_seen <- character(0)
    
    # Process symbols from query words only (not from context files)
    for (word in words) {
      word_symbols <- tryCatch({
        .rs.find_symbol(word)
      }, error = function(e) {
        NULL
      })
      
      if (!is.null(word_symbols) && length(word_symbols) > 0) {
        for (j in seq_along(word_symbols)) {
          symbol <- word_symbols[[j]]
          
          if (!is.null(symbol) && !is.null(symbol$type) && !is.null(symbol$name)) {
            # Skip symbols that are from context files
            is_context_symbol <- FALSE
            if (!is.null(symbol$file) && symbol$file %in% context_files) {
              is_context_symbol <- TRUE
            }
            
            if (!is_context_symbol) {
              # Store objects with both name and type for backend processing
              entry_with_type <- paste0(symbol$name, " (", symbol$type, ")")
              keyword_obj <- list(name = symbol$name, type = symbol$type)
              
              if (symbol$type == "directory") {
                if (!(entry_with_type %in% directory_symbols_seen)) {
                  directory_symbols_seen <- c(directory_symbols_seen, entry_with_type)
                  directory_symbols <- c(directory_symbols, list(keyword_obj))
                }
              } else if (symbol$type == "file") {
                if (!(entry_with_type %in% file_symbols_seen)) {
                  file_symbols_seen <- c(file_symbols_seen, entry_with_type)
                  file_symbols <- c(file_symbols, list(keyword_obj))
                }
              } else if (symbol$type == "function") {
                if (!(entry_with_type %in% function_symbols_seen)) {
                  function_symbols_seen <- c(function_symbols_seen, entry_with_type)
                  function_symbols <- c(function_symbols, list(keyword_obj))
                }
              } else {
                if (!(entry_with_type %in% other_symbols_seen)) {
                  other_symbols_seen <- c(other_symbols_seen, entry_with_type)
                  other_symbols <- c(other_symbols, list(keyword_obj))
                }
              }
            }
          }
        }
      }
    }
    
    all_symbols <- c(directory_symbols, file_symbols, function_symbols, other_symbols)
    all_symbols <- unique(all_symbols)
    
    if (length(all_symbols) > max_symbols) {
      all_symbols <- all_symbols[1:max_symbols]
    }
    
    # Ensure we always have a proper character vector, not empty or NULL
    if (length(all_symbols) == 0) {
      all_symbols <- character(0)
    }
    
    result$keywords <- all_symbols
  }
  
  # 3. Environmental variables
  tryCatch({
    env_vars <- .rs.get_categorized_environment_variables()
    # Convert rs.scalar description fields to plain character strings
    if (!is.null(env_vars) && length(env_vars) > 0) {
      for (category_name in names(env_vars)) {
        category <- env_vars[[category_name]]
        if (is.list(category)) {
          for (i in seq_along(category)) {
            if (!is.null(category[[i]]$description)) {
              category[[i]]$description <- as.character(category[[i]]$description)
            }
          }
          env_vars[[category_name]] <- category
        }
      }
    }
    result$environment_variables <- env_vars
  }, error = function(e) {
    result$environment_variables <- list()
  })
  
  # 4. List of open files
  tryCatch({
    open_files <- .rs.get_all_open_source_documents()
    result$open_files <- open_files
  }, error = function(e) {
    result$open_files <- list()
  })
  
  # Return the structured result (or NULL if everything is empty)
  if (length(result$direct_context) == 0 && 
      length(result$keywords) == 0 && 
      length(result$environment_variables) == 0 && 
      length(result$open_files) == 0) {
    return(NULL)
  }  
  return(result)
})

.rs.addFunction("run_api_request_async", function(api_params = NULL, provider = NULL, api_key = NULL, request_id, request_data = NULL, is_background = FALSE) {
  .rs.setVar("ai_cancelled", FALSE)
  
  temp_dir <- .rs.get_temp_dir()
  cancel_dir <- file.path(temp_dir, "ai_cancel")
  dir.create(cancel_dir, showWarnings = FALSE, recursive = TRUE)
  
  tryCatch({
    if (!is.null(request_data)) {
      final_request_data <- request_data
    } else {
      conversation_log <- .rs.read_conversation_log()
      
      model_from_params <- if (!is.null(api_params$model)) api_params$model else NULL
      
      final_request_data <- list(
        api_params = api_params,
        provider = provider,
        model = model_from_params,
        conversation_log = conversation_log
      )
    }
    
    # Check if there's already a thinking message active and set default if not
    # Skip thinking messages for conversation name generation and summarization (silent background operations)
    is_conversation_name_request <- !is.null(final_request_data$request_type) && final_request_data$request_type == "generate_conversation_name"
    is_summarization_request <- !is.null(final_request_data$request_type) && final_request_data$request_type == "summarize_conversation"
    
    if (!is_conversation_name_request && !is_summarization_request) {
      last_thinking_time <- .rs.getVar("last_thinking_message_time")
      current_time <- Sys.time()
      
      # If no thinking message was set in the last 2 seconds, set a default one
      if (is.null(last_thinking_time) || difftime(current_time, last_thinking_time, units = "secs") > 2) {
        .rs.enqueClientEvent("update_thinking_message", list(message = "Thinking..."))
        .rs.setVar("last_thinking_message_time", current_time)
      }
    }
    # Use SSE approach with httr streaming to /ai/query endpoint
    config <- .rs.get_backend_config()
    
    # Create a background process that uses httr streaming with SSE parsing
    temp_dir <- .rs.get_temp_dir()
    # Use different stream file prefix for background requests (like summarization)
    stream_prefix <- if (is_background) "bg_summary_" else "bg_stream_"
    stream_file <- file.path(temp_dir, paste0(stream_prefix, request_id, ".txt"))
    writeLines("READY", stream_file)
    
    bg_process <- callr::r_bg(
      func = function(request_data, stream_file, config_url, request_id) {
        # Load required libraries in background process
        
        tryCatch({
          cat("BG: Starting SSE streaming request to /ai/query\n", file = stream_file, append = TRUE)
          cat("BG: Request provider:", request_data$provider, "model:", request_data$model, "\n", file = stream_file, append = TRUE)
          
          # Buffer for incomplete lines from chunked streaming
          line_buffer <- ""
          
          # Use httr streaming with SSE parsing
          response <- httr::POST(
            url = paste0(config_url, "/ai/query"),
            body = request_data,
            encode = "json",
            httr::add_headers(
              "Content-Type" = "application/json",
              "Accept" = "text/event-stream"
            ),
            httr::timeout(3600),  # 1-hour timeout for streaming requests (essentially long enough that the cancellation will have to be form a large gap in streaming deltas)
            httr::write_stream(function(x) {
              if (length(x) > 0) {
                chunk_text <- rawToChar(x)
                
                # Add to buffer from previous incomplete chunks
                buffered_text <- paste0(line_buffer, chunk_text)
                
                # Split by newlines to get complete lines
                lines <- strsplit(buffered_text, "\n")[[1]]
                
                # If the chunk doesn't end with \n, the last line is incomplete
                if (!endsWith(buffered_text, "\n")) {
                  # Save the incomplete line for next chunk
                  line_buffer <- lines[length(lines)]
                  # Process only the complete lines
                  lines <- lines[-length(lines)]
                } else {
                  # All lines are complete, clear buffer
                  line_buffer <- ""
                }
                
                # Process complete lines only
                for (line in lines) {
                  line <- trimws(line)
                  if (startsWith(line, "data: ")) {
                    json_data <- substring(line, 7)
                    if (nchar(json_data) > 0 && json_data != "[DONE]") {
                      event_line <- paste0("EVENT:", json_data)
                      cat(event_line, "\n", file = stream_file, append = TRUE)
                    }
                  }
                }
              }
              TRUE
            })
          )
          
          status_code <- httr::status_code(response)
          cat("BG: Completed with status:", status_code, "\n", file = stream_file, append = TRUE)
          
          # Check for HTTP error status codes and extract error message
          if (status_code >= 400) {
            # Check if we already have a structured error event in the stream file
            has_structured_error <- FALSE
            if (file.exists(stream_file)) {
              tryCatch({
                existing_content <- readLines(stream_file, warn = FALSE)
                for (line in existing_content) {
                  if (startsWith(line, "EVENT:")) {
                    json_data <- substring(line, 7)
                    parsed_event <- jsonlite::fromJSON(json_data, simplifyVector = FALSE)
                    if (!is.null(parsed_event$error)) {
                      has_structured_error <- TRUE
                      break
                    }
                  }
                }
              }, error = function(e) {
                # Error checking stream file, continue with HTTP processing
              })
            }
            
            # If we already received a structured error from SSE, don't create another error event
            if (has_structured_error) {
              cat("COMPLETE\n", file = stream_file, append = TRUE)
              return("success")  # Return success since we already have the proper error
            }
            
            error_message <- paste("HTTP", status_code, "error from backend server")
            
            cat("BG: HTTP error status code:", status_code, "\n", file = stream_file, append = TRUE)
            
            # Try to extract error message from response body
            tryCatch({
              response_text <- httr::content(response, as = "text", encoding = "UTF-8")
              cat("BG: Response text length:", if (is.null(response_text)) 0 else nchar(response_text), "\n", file = stream_file, append = TRUE)
              
              # Debug response details (simplified after fix)
              cat("BG: HTTP Status Code:", status_code, "\n", file = stream_file, append = TRUE)
              
              if (!is.null(response_text) && nchar(response_text) > 0) {
                cat("BG: Raw response text:", substr(response_text, 1, 200), "\n", file = stream_file, append = TRUE)
                
                # Check if this is a streaming response (text/event-stream)
                response_headers <- httr::headers(response)
                content_type <- response_headers[["content-type"]]
                is_streaming <- !is.null(content_type) && grepl("text/event-stream", content_type, ignore.case = TRUE)
                
                cat("BG: Content-Type:", if (is.null(content_type)) "NULL" else content_type, "\n", file = stream_file, append = TRUE)
                cat("BG: Is streaming response:", is_streaming, "\n", file = stream_file, append = TRUE)
                
                # Parse error data based on response type
                error_data <- NULL
                if (is_streaming) {
                  # Parse SSE format
                  cat("BG: Parsing as SSE format\n", file = stream_file, append = TRUE)
                  error_data <- .rs.parse_sse_error_response(response_text)
                } else {
                  # Try to parse as regular JSON
                  cat("BG: Parsing as regular JSON\n", file = stream_file, append = TRUE)
                  error_data <- tryCatch({
                    jsonlite::fromJSON(response_text, simplifyVector = FALSE)
                  }, error = function(e) {
                    cat("BG: JSON parse error:", e$message, "\n", file = stream_file, append = TRUE)
                    return(NULL)
                  })
                }
                
                if (!is.null(error_data)) {
                  cat("BG: Parsed error data successfully\n", file = stream_file, append = TRUE)
                  # Extract structured error message - handle both direct and nested error structures
                  if (!is.null(error_data$error) && is.list(error_data$error)) {
                    # Structured error response with nested error object
                    nested_error <- error_data$error
                    if (!is.null(nested_error$user_message)) {
                      error_message <- nested_error$user_message
                      cat("BG: Using nested error user_message:", error_message, "\n", file = stream_file, append = TRUE)
                    } else if (!is.null(nested_error$error_message)) {
                      error_message <- nested_error$error_message
                      cat("BG: Using nested error error_message:", error_message, "\n", file = stream_file, append = TRUE)
                    } else if (!is.null(nested_error$message)) {
                      error_message <- nested_error$message
                      cat("BG: Using nested error message:", error_message, "\n", file = stream_file, append = TRUE)
                    } else {
                      error_message <- jsonlite::toJSON(nested_error, auto_unbox = TRUE)
                      cat("BG: Using nested error as JSON:", error_message, "\n", file = stream_file, append = TRUE)
                    }
                  } else if (!is.null(error_data$user_message)) {
                    error_message <- error_data$user_message
                    cat("BG: Using direct user_message:", error_message, "\n", file = stream_file, append = TRUE)
                  } else if (!is.null(error_data$error_message)) {
                    error_message <- error_data$error_message
                    cat("BG: Using direct error_message:", error_message, "\n", file = stream_file, append = TRUE)
                  } else if (!is.null(error_data$message)) {
                    error_message <- error_data$message
                    cat("BG: Using direct message:", error_message, "\n", file = stream_file, append = TRUE)
                  } else if (!is.null(error_data$error)) {
                    # Sometimes error is a string
                    error_message <- if (is.character(error_data$error)) error_data$error else jsonlite::toJSON(error_data$error, auto_unbox = TRUE)
                    cat("BG: Using error field:", error_message, "\n", file = stream_file, append = TRUE)
                  } else {
                    # Use the raw response as fallback
                    error_message <- response_text
                    cat("BG: Using raw response text as fallback\n", file = stream_file, append = TRUE)
                  }
                } else {
                  cat("BG: Error data parsing failed\n", file = stream_file, append = TRUE)
                  # Not JSON or SSE - use raw response text if it looks meaningful
                  if (nchar(response_text) < 500 && !grepl("<html|<!DOCTYPE", response_text, ignore.case = TRUE)) {
                    error_message <- response_text
                    cat("BG: Using raw response text (not JSON/SSE)\n", file = stream_file, append = TRUE)
                  } else {
                    cat("BG: Response text too long or HTML, using status-based message\n", file = stream_file, append = TRUE)
                  }
                }
              } else {
                cat("BG: No response text available\n", file = stream_file, append = TRUE)
              }
            }, error = function(e) {
              cat("BG: Error reading response body:", e$message, "\n", file = stream_file, append = TRUE)
            })
            
            # Provide status-specific fallback messages if we don't have a good error message
            if (is.null(error_message) || nchar(trimws(error_message)) == 0 || error_message == paste("HTTP", status_code, "error from backend server")) {
              if (status_code == 401) {
                error_message <- "Authentication failed. Invalid API key."
              } else if (status_code == 403) {
                error_message <- "Access forbidden. Please check your API key permissions."
              } else if (status_code == 404) {
                error_message <- "Backend endpoint not found. Please check your backend configuration."
              } else if (status_code == 429) {
                error_message <- "Rate limit exceeded. Please wait before trying again."
              } else if (status_code >= 500) {
                error_message <- "Backend server error. Please try again later."
              } else {
                error_message <- paste("HTTP", status_code, "error from backend server")
              }
            }
            
            cat("BG: Final error message:", error_message, "\n", file = stream_file, append = TRUE)
            
            # Send structured error event to stream
            error_event <- list(
              error = list(
                user_message = error_message,
                http_status = status_code
              )
            )
            cat("EVENT:", jsonlite::toJSON(error_event, auto_unbox = TRUE), "\n", file = stream_file, append = TRUE)
            cat("BG ERROR: HTTP", status_code, "-", error_message, "\n", file = stream_file, append = TRUE)
            cat("COMPLETE\n", file = stream_file, append = TRUE)
            return("error")
          }
          
          cat("COMPLETE\n", file = stream_file, append = TRUE)
          return("success")
          
        }, error = function(e) {
          # Handle connection errors and other failures
          error_message <- e$message
          
          cat("BG: Request failed with error:", error_message, "\n", file = stream_file, append = TRUE)
          
          # Check for specific connection error types (be comprehensive)
          if (grepl("Connection refused|Could not connect|Failed to connect|Connection.*reset|Connection.*closed", error_message, ignore.case = TRUE)) {
            error_message <- "Cannot connect to backend server. Please check your connection."
          } else if (grepl("timeout|timed out|Timeout", error_message, ignore.case = TRUE)) {
            error_message <- "Backend request timed out. Please try again."
          } else if (grepl("Could not resolve host|Name or service not known|nodename nor servname provided|getaddrinfo|DNS", error_message, ignore.case = TRUE)) {
            error_message <- "Cannot resolve backend server address. Please check your network connection."
          } else if (grepl("Network is unreachable|No route to host|Connection.*unreachable", error_message, ignore.case = TRUE)) {
            error_message <- "Backend server is unreachable. Please check your network connection."
          } else if (grepl("SSL|TLS|certificate", error_message, ignore.case = TRUE)) {
            error_message <- "SSL/TLS connection error."
          } else {
            # For any other error during the request, assume it's a connection issue
            error_message <- paste("Backend connection error:", error_message)
          }
          
          # Send error event for connection failures
          error_event <- list(
            error = list(
              user_message = error_message,
              connection_error = TRUE
            )
          )
          cat("EVENT:", jsonlite::toJSON(error_event, auto_unbox = TRUE), "\n", file = stream_file, append = TRUE)
          cat("BG ERROR:", error_message, "\n", file = stream_file, append = TRUE)
          cat("COMPLETE\n", file = stream_file, append = TRUE)
          return("error")
        })
      },
      args = list(
        request_data = final_request_data,
        stream_file = stream_file,
        config_url = config$url,
        request_id = request_id
      ),
      supervise = TRUE
    )
    
    .rs.setVar("active_api_bg_process", bg_process)
    
    return(list(
      request_id = request_id,
      using_backend = TRUE,
      using_callr = TRUE,
      bg_process = bg_process,
      stream_file = stream_file
    ))
    
  }, error = function(e) {
    .rs.setVar("active_api_request_id", NULL)
    
    stop(e)
  })
})

.rs.addFunction("parse_sse_error_response", function(response_text) {
  # Parse Server-Sent Events format to extract JSON data
  # SSE format: "data: {json}\n\n"
  
  if (is.null(response_text) || nchar(response_text) == 0) {
    return(NULL)
  }
  
  # Split by lines and look for "data: " lines
  lines <- strsplit(response_text, "\n")[[1]]
  
  for (line in lines) {
    if (startsWith(line, "data: ")) {
      # Extract JSON from "data: " line
      json_text <- substring(line, 7)  # Remove "data: " prefix
      
      # Try to parse as JSON
      error_data <- tryCatch({
        jsonlite::fromJSON(json_text, simplifyVector = FALSE)
      }, error = function(e) {
        return(NULL)
      })
      
      if (!is.null(error_data)) {
        return(error_data)
      }
    }
  }
  
  return(NULL)
})

.rs.addFunction("check_cancellation_files", function(request_id) {
  # Check if ai_cancelled variable is set
  if (.rs.hasVar("ai_cancelled") && .rs.getVar("ai_cancelled")) {
    TRUE
  } else {
    # Check for cancel file
    temp_dir <- .rs.get_temp_dir()
    cancel_dir <- file.path(temp_dir, "ai_cancel")
    if (dir.exists(cancel_dir)) {
      cancel_file <- file.path(cancel_dir, paste0("cancel_", request_id))
      if (file.exists(cancel_file)) {
        # Clean up the file
        tryCatch({
          unlink(cancel_file)
        }, error = function(e) {
          cat("ERROR CANCELLATION R: Failed to clean up cancel file:", e$message, "\n")
        })
        
        # Set the variable
        .rs.setVar("ai_cancelled", TRUE)
        TRUE
      } else {
        FALSE
      }
    } else {
      FALSE
    }
  }
})

.rs.addFunction("get_temp_dir", function() {
  temp_dir <- tryCatch({
    .Call("rs_session_temp_dir")
  }, error = function(e) {
    tempdir()
  })
  
  if (is.null(temp_dir) || !is.character(temp_dir) || length(temp_dir) == 0) {
    tempdir()
  } else {
    temp_dir
  }
})

.rs.addFunction("poll_api_request_result", function(request_info, max_attempts = 3000, sleep_time = 0.1, blocking = TRUE) {
  request_id <- request_info$request_id
  bg_process <- request_info$bg_process
  
  # Initialize streaming variables
  temp_dir <- .rs.get_temp_dir()
  # Check if this is a background summarization request by looking at the request_id prefix
  is_summary_request <- grepl("^summary_", request_id)
  stream_prefix <- if (is_summary_request) "bg_summary_" else "bg_stream_"
  stream_file <- file.path(temp_dir, paste0(stream_prefix, request_id, ".txt"))
  start_time <- Sys.time()
  last_activity_time <- Sys.time()  # Track last time we received data
  last_line <- 1
  streaming_complete <- FALSE
  accumulated_response <- ""
  last_event_data <- NULL
  assistant_message_id <- NULL  # Will be generated when streaming starts
  captured_response_id <- NULL  # Capture response_id from streaming events
  
  # Timeout configuration - simple 30-second inactivity timeout only
  activity_timeout_seconds <- 30
  
  # Debugging variables to track what went wrong
  total_lines_processed <- 0
  event_lines_seen <- 0
  ready_seen <- FALSE
  complete_seen <- FALSE
  bg_error_seen <- FALSE
  malformed_json_count <- 0
  unmatched_events_count <- 0
  file_existed <- FALSE
  process_was_alive_at_start <- FALSE
  final_process_state <- "unknown"
  last_activity_description <- "none"
  
  polling_iterations <- 0
  
  # Track the streaming request ID for cancellation (different from client request_id)
  streaming_request_id <- NULL
  
  while (TRUE) {
    current_time <- Sys.time()
    polling_iterations <- polling_iterations + 1
    
    # For non-blocking mode, only do one iteration
    if (!blocking && polling_iterations > 1) {
      return(NULL)  # No result yet, return NULL immediately
    }
    
    # Check timeout - only timeout if no activity for the specified time
    time_since_activity <- difftime(current_time, last_activity_time, units = "secs")
    
    if (time_since_activity >= activity_timeout_seconds) {
      cat("DEBUG: Timeout due to inactivity - no deltas received for", time_since_activity, "seconds\n")
      
      # Kill the background process to close the httr streaming request
      tryCatch({
        bg_process$kill()
      }, error = function(e) {
        cat("DEBUG: Error killing background process:", e$message, "\n")
      })
      
      break
    }
    
    if (.rs.get_conversation_var("ai_cancelled")) {
      break
    }
    
    # Check background summarization while polling main request
    if (blocking) {  # Only check when in blocking mode to avoid conflicts
      .rs.check_persistent_background_summarization()
    }
    
    cancel_requested <- .rs.check_cancellation_files(request_id)
    if (cancel_requested) {
      # Use streaming request ID for cancellation if available, otherwise fall back to client request ID
      cancel_request_id <- if (!is.null(streaming_request_id)) streaming_request_id else request_id
      # Send HTTP POST cancellation request to backend
      backend_cancelled <- tryCatch({
        .rs.cancel_backend_request(cancel_request_id)
      }, error = function(e) {
        cat("CANCEL DEBUG: Error calling cancel_backend_request:", e$message, "\n")
        FALSE
      })
      
      tryCatch({
        bg_process$kill()
      }, error = function(e) {
      })
      
      .rs.setVar("ai_cancelled", FALSE)
      .rs.setVar("active_api_request_id", NULL)
      .rs.setVar("active_api_bg_process", NULL)
      .rs.enqueClientEvent("update_thinking_message", list(message = "", hide_cancel = TRUE))
            
      # Preserve partial content when cancelled - create completion event with accumulated content
      if (nchar(accumulated_response) > 0) {
        # Use the assistant message ID for cancellation, generate one if needed
        cancel_message_id <- if (!is.null(assistant_message_id)) assistant_message_id else .rs.get_next_message_id()
        
        # Check if we're in edit_file streaming context for the cancellation
        edit_file_function_call_id <- .rs.get_conversation_var("current_edit_file_function_call_id")
        if (!is.null(edit_file_function_call_id)) {
          cancel_message_id <- as.numeric(edit_file_function_call_id)
        }
        
        # Add sequence number for cancellation completion event
        # Use unified sequence system
        
        # Send completion event to UI with accumulated content marked as complete
        .rs.enqueClientEvent("ai_stream_data", list(
          messageId = cancel_message_id,
          delta = "",
          isComplete = TRUE,
          cancelled = TRUE,  # Mark as cancelled so UI can handle appropriately
          sequence = .rs.get_next_ai_operation_sequence()
        ))
        
        # Create response data with accumulated content for conversation log saving
        last_event_data <- list(
          response = accumulated_response,
          isComplete = TRUE,
          cancelled = TRUE,
          requestId = request_id
        )
        
        # Update assistant_message_id if we generated one for cancellation
        if (is.null(assistant_message_id)) {
          assistant_message_id <- cancel_message_id
        }
      }

      # Clean up edit_file streaming context
      .rs.set_conversation_var("current_edit_file_function_call_id", NULL)
      .rs.set_conversation_var("current_edit_file_filename", NULL)
      .rs.set_conversation_var("current_edit_file_request_id", NULL)
      
      return(list(cancelled = TRUE, accumulated_response = accumulated_response, assistant_message_id = assistant_message_id))
    }
    
    process_alive <- bg_process$is_alive()
    
    # Process streaming events from file
    if (file.exists(stream_file)) {
      file_existed <- TRUE
      content <- readLines(stream_file, warn = FALSE)
      if (length(content) > last_line) {
        # We received new data - update activity time
        last_activity_time <- current_time
        last_activity_description <- "received new lines"
        
        new_lines <- content[(last_line + 1):length(content)]
        total_lines_processed <- total_lines_processed + length(new_lines)
        
        for (line in new_lines) {
          if (line == "READY") {
            ready_seen <- TRUE
            next
          } else if (line == "COMPLETE") {
            complete_seen <- TRUE
            streaming_complete <- TRUE
            break
          } else if (startsWith(line, "BG:")) {
            next
          } else if (startsWith(line, "BG ERROR:")) {
            bg_error_seen <- TRUE
            streaming_complete <- TRUE
            break
          } else if (startsWith(line, "EVENT:")) {
            event_lines_seen <- event_lines_seen + 1
            json_data <- substring(line, 7)
            
            # Add error handling for malformed JSON that causes "premature EOF" errors
            event_data <- tryCatch({
              jsonlite::fromJSON(json_data)
            }, error = function(e) {
              malformed_json_count <- malformed_json_count + 1
              
              # Return a null event to skip this line and continue processing
              return(NULL)
            })
            
            # Skip this iteration if JSON parsing failed
            if (is.null(event_data)) {
              cat("DEBUG: Skipping malformed JSON event, continuing stream processing\n")
              next
            }
            
            # Capture streaming request ID from response_id events for cancellation
            if (!is.null(event_data$response_id) && is.null(streaming_request_id)) {
              streaming_request_id <- event_data$requestId
            }
            
            # Handle different event types based on our streaming format
            if (!is.null(event_data$delta) && nchar(event_data$delta) > 0) {
              # Streaming text delta - this is actual streaming activity, update activity time again
              last_activity_time <- current_time
              last_activity_description <- paste0("received delta of ", nchar(event_data$delta), " characters")
                            
              accumulated_response <- paste0(accumulated_response, event_data$delta)
              
              # Set last_event_data for delta events to ensure we have something to return
              # This handles cases where we only get deltas but no explicit completion event
              last_event_data <- list(
                response = accumulated_response,
                isComplete = FALSE,
                requestId = event_data$requestId
              )

              # Generate assistant message ID once when streaming starts (skip for summarization)
              if (is.null(assistant_message_id) && !is_summary_request) {
                # First delta - generate the message ID that will be used throughout
                assistant_message_id <- .rs.get_next_message_id()
              }
              
              # Clean the delta to remove triple backticks for regular assistant messages (not edit_file)
              cleaned_delta <- event_data$delta
              
              # Create stream event with the assistant message ID (not requestId)
              stream_event <- list(
                messageId = assistant_message_id,
                delta = cleaned_delta,
                isComplete = FALSE
              )
              
              # Check if this is an edit_file related response
              # On first chunk, check if we're in an edit_file streaming context
              if (nchar(accumulated_response) == nchar(event_data$delta)) {
                # This is the first chunk - check if this is edit_file related
                related_to_id <- .rs.get_conversation_var("current_related_to_id")
                
                # related_to_id should always be present
                if (is.null(related_to_id)) {
                  stop("related_to_id is required but was NULL when processing first chunk")
                }
                
                # Check if the related_to_id corresponds to an edit_file function call
                function_call_type <- .rs.get_function_call_type_for_message(related_to_id)
                if (!is.null(function_call_type) && function_call_type == "edit_file") {
                  # For edit_file, use the related_to_id as the message ID so client can find the widget
                  stream_event$messageId <- as.numeric(related_to_id)
                  stream_event$isEditFile <- TRUE
                  
                  # Store the edit_file function call ID for subsequent chunks (for widget identification)
                  .rs.set_conversation_var("current_edit_file_function_call_id", related_to_id)
                  
                  # Get the filename and request_id from the edit_file function call
                  conversation_log <- .rs.read_conversation_log()
                  for (entry in conversation_log) {
                    if (!is.null(entry$id) && entry$id == related_to_id &&
                        !is.null(entry$function_call) && 
                        !is.null(entry$function_call$name) &&
                        entry$function_call$name == "edit_file" &&
                        !is.null(entry$function_call$arguments)) {
                      tryCatch({
                        args <- jsonlite::fromJSON(entry$function_call$arguments)
                        if (!is.null(args$filename)) {
                          stream_event$filename <- args$filename
                          # Store filename for filtering code block markers
                          .rs.set_conversation_var("current_edit_file_filename", args$filename)
                        }
                      }, error = function(e) {
                        cat("DEBUG: Error parsing edit_file arguments:", e$message, "\n")
                      })
                      
                      # Also get the request_id from the function call entry
                      if (!is.null(entry$request_id)) {
                        stream_event$requestId <- entry$request_id
                        # Store requestId for subsequent chunks
                        .rs.set_conversation_var("current_edit_file_request_id", entry$request_id)
                      }
                      
                      break
                    }
                  }
                }
              } else {
                # Not the first chunk - check if we're in an existing edit_file streaming context
                edit_file_function_call_id <- .rs.get_conversation_var("current_edit_file_function_call_id")
                if (!is.null(edit_file_function_call_id)) {
                  # Continue using the edit_file function call ID for all subsequent chunks
                  stream_event$messageId <- as.numeric(edit_file_function_call_id)
                  stream_event$isEditFile <- TRUE
                  
                  # Also include the filename and requestId for all subsequent chunks
                  current_filename <- .rs.get_conversation_var("current_edit_file_filename")
                  if (!is.null(current_filename)) {
                    stream_event$filename <- current_filename
                  }
                  
                  current_request_id <- .rs.get_conversation_var("current_edit_file_request_id")
                  if (!is.null(current_request_id)) {
                    stream_event$requestId <- current_request_id
                  }
                  
                }
              }

              # Note: Triple backticks should NOT be cleaned for regular messages
              # They should only be processed by the markdown renderer to create proper code blocks
              # The parseCodeBlockContent function in Java handles backtick removal only for edit_file content

              # Filter out code block markers for edit_file streaming
              should_send_delta <- TRUE
              if (!is.null(stream_event$isEditFile) && stream_event$isEditFile) {
                filename <- .rs.get_conversation_var("current_edit_file_filename")
                if (!is.null(filename)) {
                  # Get or initialize filtering state for this message
                  message_id <- stream_event$messageId
                  filter_state_key <- paste0("edit_file_filter_state_", message_id)
                  
                  current_filter_state <- .rs.get_conversation_var(filter_state_key, NULL)
                  
                  # Explicit initialization if needed
                  if (is.null(current_filter_state) || length(current_filter_state) == 0 || is.null(current_filter_state$mode)) {
                    current_filter_state <- list(
                      mode = "before_code_block",
                      accumulated_ticks = "",
                      accumulated_opening_line = ""
                    )
                  }
                  
                  is_rmd <- grepl("\\.(rmd|Rmd)$", filename)
                  tick_pattern <- if (is_rmd) "````" else "```"
                  required_ticks <- nchar(tick_pattern)
                  
                  # Process the delta character by character
                  delta_chars <- strsplit(event_data$delta, "")[[1]]
                  filtered_chars <- c()
                  
                  for (char in delta_chars) {
                    if (current_filter_state$mode == "before_code_block") {
                      # Look for opening ticks
                      if (char == "`") {
                        current_filter_state$accumulated_ticks <- paste0(current_filter_state$accumulated_ticks, char)
                        current_filter_state$accumulated_opening_line <- paste0(current_filter_state$accumulated_opening_line, char)
                        
                        # Check if we have enough ticks for our file type
                        if (nchar(current_filter_state$accumulated_ticks) == required_ticks) {
                          # Found opening ticks - now filter rest of opening line until \n
                          current_filter_state$mode <- "filtering_opening_line"
                          current_filter_state$accumulated_ticks <- ""
                        }
                      } else {
                        # Not a tick - reset accumulation and include in output
                        if (nchar(current_filter_state$accumulated_opening_line) > 0) {
                          filtered_chars <- c(filtered_chars, strsplit(current_filter_state$accumulated_opening_line, "")[[1]])
                          current_filter_state$accumulated_opening_line <- ""
                          current_filter_state$accumulated_ticks <- ""
                        }
                        filtered_chars <- c(filtered_chars, char)
                      }
                    } else if (current_filter_state$mode == "filtering_opening_line") {
                      # Filter everything until newline
                      current_filter_state$accumulated_opening_line <- paste0(current_filter_state$accumulated_opening_line, char)
                      if (char == "\n") {
                        # End of opening line - enter code block mode and discard accumulated line
                        current_filter_state$mode <- "in_code_block"
                        current_filter_state$accumulated_opening_line <- ""
                      }
                      # Don't add to filtered_chars - we're filtering this line
                    } else if (current_filter_state$mode == "in_code_block") {
                      # Stream normally but watch for closing ticks
                      if (char == "`") {
                        current_filter_state$accumulated_ticks <- paste0(current_filter_state$accumulated_ticks, char)
                        
                        # Check if we have enough ticks for closing
                        if (nchar(current_filter_state$accumulated_ticks) == required_ticks) {
                          # Found closing ticks - filter them out and return to normal mode
                          current_filter_state$mode <- "before_code_block"
                          current_filter_state$accumulated_ticks <- ""
                        }
                        # Don't add ticks to output while we're accumulating
                      } else {
                        # Not a tick - if we had partial ticks, they weren't closing marker
                        if (nchar(current_filter_state$accumulated_ticks) > 0) {
                          # Add the partial ticks we were accumulating to output
                          filtered_chars <- c(filtered_chars, strsplit(current_filter_state$accumulated_ticks, "")[[1]])
                          current_filter_state$accumulated_ticks <- ""
                        }
                        # Add the current character to output
                        filtered_chars <- c(filtered_chars, char)
                      }
                    }
                  }
                  
                  # Store updated filter state
                  .rs.set_conversation_var(filter_state_key, current_filter_state)
                  
                  # Reconstruct the delta with filtered content
                  if (length(filtered_chars) > 0) {
                    stream_event$delta <- paste(filtered_chars, collapse = "")
                  } else {
                    # If all content was filtered, don't send this delta
                    should_send_delta <- FALSE
                  }
                }
              }

              # Send real-time update to UI only if we have content to send (skip for summarization)
              if (should_send_delta && nchar(stream_event$delta) > 0 && !is_summary_request) {
                # Use the unified sequence system for all events (operations and streaming)
                stream_event$sequence <- .rs.get_next_ai_operation_sequence()
                
                
                .rs.enqueClientEvent("ai_stream_data", stream_event)
              }
            } else if (!is.null(event_data$action) && event_data$action == "function_call") {
              # Function call event - save text portion to conversation log
              
              # Save the text portion to conversation log if we have content (skip for summarization)
              if (!is.null(assistant_message_id) && nchar(accumulated_response) > 0 && !is_summary_request) {
                
                # Get the related_to_id from conversation variables
                related_to_id <- .rs.get_conversation_var("current_related_to_id")
                if (is.null(related_to_id)) {
                  stop("related_to_id is required and cannot be NULL for text part")
                }
                
                tryCatch({
                  conversation_index <- .rs.get_current_conversation_index()
                  
                  # Include response_id in metadata if available for reasoning model chaining
                  metadata <- NULL
                  if (!is.null(captured_response_id)) {
                    metadata <- list(response_id = captured_response_id)
                  }
                  
                  result <- .rs.process_assistant_response(
                    accumulated_response, 
                    assistant_message_id,  # Use the streaming message ID
                    related_to_id,
                    conversation_index, 
                    "ai_operation",  # source_function_name
                    metadata,  # message_metadata with response_id
                    NULL   # existing_conversation_log
                  )
                }, error = function(e) {
                  cat("Error saving text portion before function call:", e$message, "\n")
                })
                
                # Clear accumulated response since we've processed it
                accumulated_response <- ""
              }
              
              # Store the function call data but DON'T end overall streaming - wait for "COMPLETE" line
              last_event_data <- event_data
              
              # Reset assistant_message_id so new content gets a new messageId
              assistant_message_id <- NULL
              
              # Clean up edit_file streaming context
              edit_file_function_call_id <- .rs.get_conversation_var("current_edit_file_function_call_id")
              .rs.set_conversation_var("current_edit_file_function_call_id", NULL)
              .rs.set_conversation_var("current_edit_file_filename", NULL)
              .rs.set_conversation_var("current_edit_file_request_id", NULL)
              
              # Clean up filter state for edit_file streaming
              if (!is.null(edit_file_function_call_id)) {
                filter_state_key <- paste0("edit_file_filter_state_", edit_file_function_call_id)
                .rs.set_conversation_var(filter_state_key, NULL)
              }
              
              # Check if this is a console/terminal command that should NOT send streaming data
              # These commands create their own widgets and don't need assistant message divs
              should_skip_streaming <- FALSE
              if (!is.null(event_data$function_call) && !is.null(event_data$function_call$name)) {
                function_name <- event_data$function_call$name
                if (function_name == "run_console_cmd" || function_name == "run_terminal_cmd") {
                  should_skip_streaming <- TRUE
                }
              }
              
              # Send function call completion to UI only if not a console/terminal command (skip for summarization)
              if (!should_skip_streaming && !is_summary_request) {
                # Generate assistant message ID if not already generated
                if (is.null(assistant_message_id)) {
                  assistant_message_id <- .rs.get_next_message_id()
                }
                
                # Use the assistant message ID
                completion_message_id <- assistant_message_id
                edit_file_function_call_id <- .rs.get_conversation_var("current_edit_file_function_call_id")
                if (!is.null(edit_file_function_call_id)) {
                  completion_message_id <- as.numeric(edit_file_function_call_id)
                }
                
                # Send completion event
                .rs.enqueClientEvent("ai_stream_data", list(
                  messageId = completion_message_id,
                  delta = "",
                  isComplete = TRUE,
                  isFunctionCall = TRUE,
                  sequence = .rs.get_next_ai_operation_sequence()
                ))
              }
            } else if (!is.null(event_data$isComplete) && event_data$isComplete) {
              # Individual message completion - save to conversation log and send completion event
              
              # Store the final data but don't end overall streaming yet
              # Special handling for end_turn events: preserve existing response content
              if (!is.null(event_data$end_turn) && event_data$end_turn == TRUE && 
                  !is.null(last_event_data) && !is.null(last_event_data$response)) {
                # For end_turn events, preserve the response from the previous event
                # This handles the case where text completion comes before end_turn
                event_data$response <- last_event_data$response
              }
              last_event_data <- event_data
              
              # Generate assistant message ID if not already generated (skip for summarization)
              if (is.null(assistant_message_id) && !is_summary_request) {
                assistant_message_id <- .rs.get_next_message_id()
              }
              
              # Save the completed message to conversation log using proper function (skip for summarization)
              if (nchar(accumulated_response) > 0 && !is_summary_request) {
                
                # Get the related_to_id from conversation variables
                related_to_id <- .rs.get_conversation_var("current_related_to_id")
                if (is.null(related_to_id)) {
                  stop("related_to_id is required and cannot be NULL for assistant response completion")
                }
                
                # Check if this is edit_file related - if so, don't save during streaming
                # because ai_operation will save it later, preventing duplicates
                should_save_during_streaming <- TRUE
                conversation_log <- .rs.read_conversation_log()
                for (entry in conversation_log) {
                  if (!is.null(entry$id) && entry$id == related_to_id && 
                      !is.null(entry$function_call) && !is.null(entry$function_call$name) &&
                      entry$function_call$name == "edit_file") {
                    should_save_during_streaming <- FALSE
                    break
                  }
                }
                
                if (should_save_during_streaming) {
                  conversation_index <- .rs.get_current_conversation_index()
                  
                  # Include response_id in metadata if available for reasoning model chaining
                  metadata <- NULL
                  if (!is.null(captured_response_id)) {
                    metadata <- list(response_id = captured_response_id)
                  }
                  
                  result <- .rs.process_assistant_response(
                    accumulated_response, 
                    assistant_message_id,  # Use the streaming message ID
                    related_to_id,
                    conversation_index, 
                    "ai_operation",  # source_function_name
                    metadata,  # message_metadata with response_id
                    NULL   # existing_conversation_log
                  )
                }
              }
              
              # Check if we're in edit_file streaming context for completion event
              # Use the assistant message ID
              completion_message_id <- assistant_message_id
              edit_file_function_call_id <- .rs.get_conversation_var("current_edit_file_function_call_id")
              if (!is.null(edit_file_function_call_id)) {
                completion_message_id <- as.numeric(edit_file_function_call_id)
              }
              
              # Send completion event for this individual message (skip for summarization)
              if (!is_summary_request) {
                .rs.enqueClientEvent("ai_stream_data", list(
                  messageId = completion_message_id,
                  delta = "",
                  isComplete = TRUE,
                  sequence = .rs.get_next_ai_operation_sequence()
                ))
              }
              
              # Reset accumulated response since we've saved it
              accumulated_response <- ""
              
              # Reset assistant_message_id so next content gets a new messageId
              assistant_message_id <- NULL
            } else if (!is.null(event_data$response_id)) {
              # Capture response_id for reasoning model chaining
              captured_response_id <- event_data$response_id
            } else if (!is.null(event_data$error)) {
              # Error event from backend
              last_event_data <- event_data
              streaming_complete <- TRUE
              break
            } else {
              unmatched_events_count <- unmatched_events_count + 1
              cat("DEBUG: Event does not match any conditions. event_data:", jsonlite::toJSON(event_data, auto_unbox = TRUE), "\n")
              cat("DEBUG: event_data$isComplete:", event_data$isComplete, "\n")
            }
          }
        }
        last_line <- length(content)
        if (streaming_complete) {
          break
        }
      }
    }
    
    # If the process is dead and we've processed everything, break
    if (!process_alive && streaming_complete) {
      final_process_state <- "dead_and_complete"
      break
    }
    
    # If the process is dead but we haven't seen COMPLETE yet, give it more time
    if (!process_alive) {
      final_process_state <- "dead_but_incomplete"
      # Increase timeout for dead processes to allow content recovery
      dead_process_wait_time <- if (exists("dead_process_wait_count")) dead_process_wait_count else 0
      dead_process_wait_count <- dead_process_wait_time + 1
      
      # Wait longer initially, then give up after reasonable attempts
      if (dead_process_wait_count <= 10) {  # Up to 2 seconds total
        if (blocking) {
          Sys.sleep(0.2)
        }
      } else {
        # Process is dead and we've waited long enough - force completion
        streaming_complete <- TRUE
        
        # If we have accumulated content but no completion event, create one
        if (nchar(accumulated_response) > 0 && is.null(last_event_data)) {
          last_activity_description <- "created fallback event_data from accumulated_response"
          last_event_data <- list(
            response = accumulated_response,
            isComplete = TRUE,
            requestId = .rs.getVar("active_api_request_id")
          )
        }
        break
      }
    } else {
      final_process_state <- "alive"
      # Check if we haven't recorded initial state yet
      if (!process_was_alive_at_start) {
        process_was_alive_at_start <- TRUE
      }
      # Reset the dead process counter when process is alive
      if (exists("dead_process_wait_count")) {
        rm("dead_process_wait_count")
      }
      if (blocking) {
        Sys.sleep(0.1)
      }
    }
  }
  
  .rs.setVar("active_api_request_id", NULL)
  .rs.setVar("active_api_bg_process", NULL)
  
  # Clean up edit_file streaming context
  .rs.set_conversation_var("current_edit_file_function_call_id", NULL)
  .rs.set_conversation_var("current_edit_file_filename", NULL)
  .rs.set_conversation_var("current_edit_file_request_id", NULL)
    
  # Clean up stream file
  if (file.exists(stream_file)) {
    unlink(stream_file)
  }
  
  # Build response in the same format as the old non-streaming version
  if (!is.null(last_event_data)) {
    # Convert streaming response back to the expected format
    result <- list(using_backend = TRUE)
    
    # Mark if this was a cancelled response with partial content
    if (!is.null(last_event_data$cancelled) && last_event_data$cancelled) {
      result$cancelled = TRUE
      result$partial_content = TRUE
    }
    
    # Handle different response types
    if (!is.null(last_event_data$error)) {
      result$error <- last_event_data$error
      # For structured errors, extract user-friendly message; for string errors, use as-is
      if (is.list(last_event_data$error) && !is.null(last_event_data$error$user_message)) {
        result$message <- last_event_data$error$user_message
      } else if (is.character(last_event_data$error)) {
        result$message <- last_event_data$error
      } else {
        result$message <- "Unknown error from backend"
      }
    } else if (!is.null(last_event_data$response)) {
      result$response <- last_event_data$response
    } else if (!is.null(last_event_data$filename)) {
      result$filename <- last_event_data$filename
    } else if (!is.null(last_event_data$conversation_name)) {
      result$conversation_name <- last_event_data$conversation_name
    } else if (!is.null(last_event_data$interpretation)) {
      result$interpretation <- last_event_data$interpretation
    } else if (!is.null(last_event_data$action)) {
      # Function call or other action
      result$action <- last_event_data$action
      if (!is.null(last_event_data$function_call)) {
        result$function_call <- last_event_data$function_call
      }

    } else if (nchar(accumulated_response) > 0) {
      # Use accumulated response as fallback
      result$response <- accumulated_response
    }
    
    # Include end_turn flag if present in the streaming event
    if (!is.null(last_event_data$end_turn) && last_event_data$end_turn == TRUE) {
      result$end_turn <- TRUE
    }
    
    # Include the assistant message ID so it can be passed to process_assistant_response
    if (!is.null(assistant_message_id)) {
      result$assistant_message_id <- assistant_message_id
    }
    
    # Include captured response_id for reasoning model chaining
    if (!is.null(captured_response_id)) {
      result$response_id <- captured_response_id
    }
    
    return(result)
  } else {
    # Comprehensive debugging for NULL last_event_data cases
    cat("ERROR: poll_api_request_result reached end with NULL last_event_data\n")
    cat("=== DIAGNOSTIC INFORMATION ===\n")
    cat("Request ID:", request_id, "\n")
    cat("Total execution time:", difftime(Sys.time(), start_time, units = "secs"), "seconds\n")
    cat("Final process state:", final_process_state, "\n")
    cat("Process was alive at start:", process_was_alive_at_start, "\n")
    cat("Stream file existed:", file_existed, "\n")
    cat("Stream file path:", stream_file, "\n")
    cat("Total lines processed:", total_lines_processed, "\n")
    cat("Event lines seen:", event_lines_seen, "\n")
    cat("Ready seen:", ready_seen, "\n")
    cat("Complete seen:", complete_seen, "\n")
    cat("BG error seen:", bg_error_seen, "\n")
    cat("Malformed JSON count:", malformed_json_count, "\n")
    cat("Unmatched events count:", unmatched_events_count, "\n")
    cat("Accumulated response length:", nchar(accumulated_response), "\n")
    cat("Last activity description:", last_activity_description, "\n")
    cat("Streaming complete flag:", streaming_complete, "\n")
    
    # Show the last few lines of the stream file for debugging
    if (file.exists(stream_file)) {
      cat("=== LAST 10 LINES OF STREAM FILE ===\n")
      tryCatch({
        all_content <- readLines(stream_file, warn = FALSE)
        if (length(all_content) > 0) {
          start_idx <- max(1, length(all_content) - 9)
          for (i in start_idx:length(all_content)) {
            cat(sprintf("[%d] %s\n", i, all_content[i]))
          }
        } else {
          cat("Stream file is empty\n")
        }
      }, error = function(e) {
        cat("ERROR reading stream file:", e$message, "\n")
      })
    } else {
      cat("Stream file does not exist\n")
    }
    
    # Try to get background process info
    cat("=== BACKGROUND PROCESS INFO ===\n")
    tryCatch({
      cat("Process alive status:", bg_process$is_alive(), "\n")
      if (!bg_process$is_alive()) {
        exit_status <- bg_process$get_exit_status()
        cat("Exit status:", if (is.null(exit_status)) "NULL" else exit_status, "\n")
      }
    }, error = function(e) {
      cat("ERROR getting process info:", e$message, "\n")
    })
    
    cat("==============================\n")
    
    stop("No response received from backend, timeout or error")
  }
})
