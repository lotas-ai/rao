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
  
  context_items <- .rs.getVar("context_items")
    
    if (!is.null(context_items) && length(context_items) > 0) {
      # Collect all context paths for duplicate detection (exclude NULL paths)
      all_context_paths <- character(0)
      for (item in context_items) {
        if (!is.null(item$path)) {
          all_context_paths <- c(all_context_paths, item$path)
        }
      }

      
      for (i in seq_along(context_items)) {
        item <- context_items[[i]]
        if (!is.null(item) && !is.null(item$path)) {
          path <- item$path          
          # Fix: Check both file existence on disk AND open in editor
          if (file.exists(path) || .rs.is_file_open_in_editor(path)) {
            context_files <- c(context_files, path)
            
            is_directory <- !is.null(item$type) && item$type == "directory"
            if (!is_directory && file.exists(path)) {
              is_directory <- file.info(path)$isdir
            }
            
            if (is_directory) {
              # For directories, list contents
              dir_files <- list.files(path, full.names = FALSE)
              
              display_name <- .rs.get_unique_display_name(path, all_context_paths)
              
              directory_item <- list(
                type = "directory",
                name = display_name,
                path = path,
                contents = dir_files
              )
              result$direct_context[[length(result$direct_context) + 1]] <- directory_item
            } else {
              # For files, handle differently based on whether line numbers are specified
              has_line_numbers <- !is.null(item$start_line) && !is.null(item$end_line)
              
              if (has_line_numbers) {
                # Extract only the specified lines - send as content
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
                # No line numbers - just send file path
                result$direct_context[[length(result$direct_context) + 1]] <- list(
                  type = "file",
                  name = .rs.get_unique_display_name(path, all_context_paths),
                  path = path
                )
              }
            }
          }
        }
        
        # Handle chat and docs context items (don't have path field)
        if (!is.null(item$type) && item$type == "chat") {
          # Get conversation summary for chat context items
            summary_text <- .rs.get_conversation_summary_for_context(item$id)
            
            if (!is.null(summary_text) && nchar(summary_text) > 0) {
              context_item <- list(
                type = "chat",
                name = item$name,
                id = item$id,
                summary = summary_text
              )
              result$direct_context[[length(result$direct_context) + 1]] <- context_item
            }
        }
        if (!is.null(item$type) && item$type == "docs") {
          # Convert docs to markdown for docs context items
            
            # Validate topic is not NULL and not empty
            if (!is.null(item$topic) && is.character(item$topic) && nchar(item$topic) > 0) {
              markdown_content <- .rs.get_help_as_md(item$topic, "")
              if (!is.null(markdown_content) && nchar(markdown_content) > 0) {
                context_item <- list(
                  type = "docs",
                  name = item$name,
                  topic = item$topic,
                  markdown = markdown_content
                )
                result$direct_context[[length(result$direct_context) + 1]] <- context_item
              }
            }
        }

      }
    }

  
  # 2. Environmental variables
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
  
  # 3. List of open files
  tryCatch({
    open_files <- .rs.get_all_open_source_documents()
    result$open_files <- open_files
  }, error = function(e) {
    result$open_files <- list()
  })
  
  # 4. Attached images for context
  tryCatch({
    image_context <- .rs.prepare_image_context_data()
    
    if (!is.null(image_context) && !is.null(image_context$has_images) && image_context$has_images) {
      result$attached_images <- image_context$images
    } else {
      result$attached_images <- list()
    }
  }, error = function(e) {
    result$attached_images <- list()
  })
  

  
  # Return the structured result (or NULL if everything is empty)
  if (length(result$direct_context) == 0 && 
      length(result$keywords) == 0 && 
      length(result$environment_variables) == 0 && 
      length(result$open_files) == 0 &&
      length(result$attached_images) == 0) {
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
    request_type <- final_request_data$request_type
    
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
    
    # Get security mode and web search settings in the main process before background execution
    security_mode <- tryCatch({
      result <- .rs.get_security_mode()
      result
    }, error = function(e) {
      "secure"
    })
    
    web_search_enabled <- tryCatch({
      result <- .rs.get_web_search_enabled()
      result
    }, error = function(e) {
      FALSE
    })
    
    # Create a background process that uses httr streaming with SSE parsing
    temp_dir <- .rs.get_temp_dir()
    # Use different stream file prefix for background requests (like summarization)
    stream_prefix <- if (is_background) "bg_summary_" else "bg_stream_"
    stream_file <- file.path(temp_dir, paste0(stream_prefix, request_id, ".txt"))
    writeLines("READY", stream_file)
    
    bg_process <- callr::r_bg(
      func = function(request_data, stream_file, config_url, request_id, security_mode, web_search_enabled) {
        # Load required libraries in background process
        
        tryCatch({
          # Buffer for incomplete lines from chunked streaming
          line_buffer <- ""
          
          # Use httr streaming with SSE parsing
          response <- httr::POST(
            url = paste0(config_url, "/ai/query"),
            body = request_data,
            encode = "json",
            httr::add_headers(
              "Content-Type" = "application/json",
              "Accept" = "text/event-stream",
              "X-Rao-Security-Mode" = security_mode,
              "X-Rao-Web-Search-Enabled" = as.character(web_search_enabled)
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
            
            # Try to extract error message from response body
            tryCatch({
              response_text <- httr::content(response, as = "text", encoding = "UTF-8")
              
              if (!is.null(response_text) && nchar(response_text) > 0) {
                # Check if this is a streaming response (text/event-stream)
                response_headers <- httr::headers(response)
                content_type <- response_headers[["content-type"]]
                is_streaming <- !is.null(content_type) && grepl("text/event-stream", content_type, ignore.case = TRUE)
                
                # Parse error data based on response type
                error_data <- NULL
                if (is_streaming) {
                  # Parse SSE format
                  error_data <- .rs.parse_sse_error_response(response_text)
                } else {
                  # Try to parse as regular JSON
                  error_data <- tryCatch({
                    jsonlite::fromJSON(response_text, simplifyVector = FALSE)
                  }, error = function(e) {
                    return(NULL)
                  })
                }
                
                if (!is.null(error_data)) {
                  # Extract structured error message - handle both direct and nested error structures
                  if (!is.null(error_data$error) && is.list(error_data$error)) {
                    # Structured error response with nested error object
                    nested_error <- error_data$error
                    if (!is.null(nested_error$user_message)) {
                      error_message <- nested_error$user_message
                    } else if (!is.null(nested_error$error_message)) {
                      error_message <- nested_error$error_message
                    } else if (!is.null(nested_error$message)) {
                      error_message <- nested_error$message
                    } else {
                      error_message <- jsonlite::toJSON(nested_error, auto_unbox = TRUE)
                    }
                  } else if (!is.null(error_data$user_message)) {
                    error_message <- error_data$user_message
                  } else if (!is.null(error_data$error_message)) {
                    error_message <- error_data$error_message
                  } else if (!is.null(error_data$message)) {
                    error_message <- error_data$message
                  } else if (!is.null(error_data$error)) {
                    # Sometimes error is a string
                    error_message <- if (is.character(error_data$error)) error_data$error else jsonlite::toJSON(error_data$error, auto_unbox = TRUE)
                  } else {
                    # Use the raw response as fallback
                    error_message <- response_text
                  }
                } else {
                  # Not JSON or SSE - use raw response text if it looks meaningful
                  if (nchar(response_text) < 500 && !grepl("<html|<!DOCTYPE", response_text, ignore.case = TRUE)) {
                    error_message <- response_text
                  }
                }
              }
            }, error = function(e) {
              # Continue with default error message
            })
            
            # Provide status-specific fallback messages if we don't have a good error message
            if (is.null(error_message) || nchar(trimws(error_message)) == 0 || error_message == paste("HTTP", status_code, "error from backend server")) {
              if (status_code == 401) {
                error_message <- "Authentication failed. Invalid log-in or API key."
              } else if (status_code == 403) {
                error_message <- "Access forbidden. Please check your API key permissions."
              } else if (status_code == 404) {
                error_message <- "Backend endpoint not found. Please check your backend configuration."
              } else if (status_code == 429) {
                error_message <- "Rate limit exceeded. Please wait before trying again. If the problem persists, please open a thread at https://community.lotas.ai/."
              } else if (status_code >= 500) {
                error_message <- "Backend server error. Please try again later. If the problem persists, please open a thread at https://community.lotas.ai/."
              } else {
                error_message <- paste("HTTP", status_code, "error from backend server")
              }
            }
            
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
            error_message <- paste("Network connection error:", error_message)
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
        request_id = request_id,
        security_mode = security_mode,
        web_search_enabled = web_search_enabled
      ),
      supervise = TRUE
    )
    
    .rs.setVar("active_api_bg_process", bg_process)
    
    return(list(
      request_id = request_id,
      using_backend = TRUE,
      using_callr = TRUE,
      bg_process = bg_process,
      stream_file = stream_file,
      request_type = request_type,
      model = final_request_data$model
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
  request_type <- request_info$request_type
  is_conversation_name_request <- !is.null(request_type) && request_type == "generate_conversation_name"
  
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
  
  # Variables for search_replace delta accumulation (per call_id)
  search_replace_delta_accumulators <- list()
  search_replace_message_ids <- list()
  search_replace_filename_printed <- FALSE
  search_replace_old_string_started <- FALSE
  search_replace_new_string_started <- FALSE
  search_replace_old_string_streamed <- ""  # Track what old_string content we've already streamed
  search_replace_new_string_streamed <- ""  # Track what new_string content we've already streamed
  search_replace_old_comment_streamed <- FALSE  # Track if "Old content" comment has been streamed
  search_replace_new_comment_streamed <- FALSE  # Track if "New content" comment has been streamed
  
  # Variables for console/terminal command delta accumulation
  # Use lists to track multiple parallel console/terminal commands by call_id
  console_terminal_delta_accumulators <- list()  # call_id -> accumulator string
  interactive_widget_states <- list()  # "widget_created_call_id" -> boolean
  console_terminal_command_states <- list()  # "command_started_call_id" -> boolean
  console_terminal_message_ids <- list()  # call_id -> message_id
  console_terminal_command_streamed_states <- list()  # "command_streamed_call_id" -> streamed content
  
  # Timeout configuration (restore default timeout)
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
    
    # Check timeout
    time_since_activity <- difftime(current_time, last_activity_time, units = "secs")
    if (time_since_activity >= activity_timeout_seconds) {
      tryCatch({ bg_process$kill() }, error = function(e) { })
      break
    }
    
    if (.rs.get_conversation_var("ai_cancelled") && !is_conversation_name_request) {
      break
    }
    
    # Check background summarization while polling main request
    if (blocking) {  # Only check when in blocking mode to avoid conflicts
      .rs.check_persistent_background_summarization()
    }
    
    cancel_requested <- .rs.check_cancellation_files(request_id)
    if (cancel_requested && !is_conversation_name_request) {
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
      }
      
      # Handle cancellation for console/terminal commands that are currently streaming
      # Check if we have any active console/terminal commands and send cancellation events to their widgets
      if (length(console_terminal_message_ids) > 0) {
        for (call_id in names(console_terminal_message_ids)) {
          widget_message_id <- console_terminal_message_ids[[call_id]]
          if (!is.null(widget_message_id)) {
            # Send cancellation event to this specific widget
            .rs.enqueClientEvent("ai_stream_data", list(
              messageId = widget_message_id,
              delta = "",
              isComplete = TRUE,
              cancelled = TRUE,
              sequence = .rs.get_next_ai_operation_sequence()
            ))
          }
        }
      }
      
      # Only process accumulated response if we have content
      if (nchar(accumulated_response) > 0) {
        
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

      # Clean up console/terminal streaming context variables
      console_terminal_delta_accumulators <- list()
      interactive_widget_states <- list()
      console_terminal_command_states <- list()
      console_terminal_message_ids <- list()
      console_terminal_command_streamed_states <- list()
      
      # Clean up widget streaming context
      .rs.set_conversation_var("widget_delta_accumulator", NULL)
      .rs.set_conversation_var("widget_message_id", NULL)
      .rs.set_conversation_var("widget_created", NULL)
      .rs.set_conversation_var("widget_command_started", NULL)
      .rs.set_conversation_var("widget_command_streamed", NULL)
      .rs.set_conversation_var("widget_type", NULL)
      
      # Clean up search_replace streaming context
      search_replace_delta_accumulators <- list()
      search_replace_message_ids <- list()
      search_replace_filename_printed <- FALSE
      search_replace_old_string_started <- FALSE
      search_replace_new_string_started <- FALSE
      search_replace_old_string_streamed <- ""
      search_replace_new_string_streamed <- ""
      search_replace_old_comment_streamed <- FALSE
      search_replace_new_comment_streamed <- FALSE
      
      # Clean up early widget calls tracking
      .rs.set_conversation_var("early_widget_calls", NULL)
      
      # Clean up first function call tracking (only if no buffered calls remain)
      # During cancellation, preserve first_function_call_id if there are buffered calls
      # that might still be processed after cancellation
      has_buffered_calls <- .rs.has_buffered_function_calls()
      if (!has_buffered_calls) {
        .rs.set_conversation_var("first_function_call_id", NULL)
      }
      
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
              
              # For search_replace deltas, accumulate and stream to widget
              if (!is.null(event_data$field) && event_data$field == "search_replace") {
                # Get call_id for this delta
                call_id <- if (!is.null(event_data$call_id)) event_data$call_id else "unknown"
                
                # Pre-allocate message IDs if not already done (delta processing may happen before function_call action event)
                function_name <- "search_replace"
                .rs.preallocate_function_message_ids(function_name, call_id)
                
                # Generate unique message ID per call_id (like console/terminal commands)
                if (is.null(search_replace_message_ids)) {
                  search_replace_message_ids <- list()
                }
                current_search_replace_message_id <- search_replace_message_ids[[call_id]]
                if (is.null(current_search_replace_message_id)) {
                  # Use pre-allocated message ID for search_replace (index 1 = function call itself)
                  current_search_replace_message_id <- .rs.get_preallocated_message_id(call_id, 1)
                  search_replace_message_ids[[call_id]] <- current_search_replace_message_id
                }
                
                # Initialize accumulator for this call_id if not exists
                if (is.null(search_replace_delta_accumulators[[call_id]])) {
                  search_replace_delta_accumulators[[call_id]] <- ""
                }
                
                # Accumulate the delta for this specific call_id
                search_replace_delta_accumulators[[call_id]] <- paste0(search_replace_delta_accumulators[[call_id]], event_data$delta)
                                
                # Extract filename if available and widget not yet created
                if (!search_replace_filename_printed) {
                  current_accumulator <- search_replace_delta_accumulators[[call_id]]
                  if (!is.null(current_accumulator) && grepl('"file_path"\\s*:\\s*"[^"]*"', current_accumulator, perl = TRUE)) {
                    filename_match <- regmatches(current_accumulator, 
                                                regexpr('"file_path"\\s*:\\s*"([^"]*)"', current_accumulator, perl = TRUE))
                    if (length(filename_match) > 0) {
                      # Extract just the filename value
                      filename <- gsub('"file_path"\\s*:\\s*"([^"]*)"', '\\1', filename_match, perl = TRUE)
                      
                      # Only create widget for the first interactive function call during streaming (following console/terminal pattern)
                      widget_created_key <- paste0("widget_created_", call_id)
                      if (is.null(interactive_widget_states[[widget_created_key]]) || !interactive_widget_states[[widget_created_key]]) {

                        # Check if this is the first function call in the parallel set
                        is_first_widget <- .rs.is_first_function_call_in_parallel_set(call_id)
                        
                        if (is_first_widget) {
                          # Do NOT create operation command during streaming - that creates duplicates
                          .rs.enqueClientEvent("ai_stream_data", list(
                            messageId = current_search_replace_message_id,
                            delta = "",
                            isComplete = FALSE,
                            isSearchReplace = TRUE,
                            filename = filename,
                            requestId = request_id,
                            sequence = .rs.get_next_ai_operation_sequence()
                          ))
                          
                          # Mark this call_id as having a widget created
                          interactive_widget_states[[widget_created_key]] <- TRUE
                        }
                      }
                      
                      search_replace_filename_printed <- TRUE
                    }
                  }
                }
                
                # Detect start of old_string for this specific call_id (following console/terminal pattern)
                old_string_started_key <- paste0("old_string_started_", call_id)
                current_old_string_started <- interactive_widget_states[[old_string_started_key]]
                if (is.null(current_old_string_started)) {
                  current_old_string_started <- FALSE
                }
                
                widget_created_key <- paste0("widget_created_", call_id)
                widget_created <- interactive_widget_states[[widget_created_key]]
                if (is.null(widget_created)) {
                  widget_created <- FALSE
                }
                
                if (widget_created && !current_old_string_started) {
                  current_accumulator <- search_replace_delta_accumulators[[call_id]]
                  if (!is.null(current_accumulator) && grepl('"old_string"\\s*:\\s*"', current_accumulator, perl = TRUE)) {
                    interactive_widget_states[[old_string_started_key]] <- TRUE
                    current_old_string_started <- TRUE
                  }
                }
                
                # Extract and stream partial old_string content using helper function (only if widget was created for this call_id)
                if (widget_created && current_old_string_started) {
                  # Stream "Old content" comment before actual content (only once)
                  if (!search_replace_old_comment_streamed) {
                    comment_syntax <- .rs.get_comment_syntax(filename)
                    old_comment <- paste0(comment_syntax, "Old content\n")
                    
                    .rs.enqueClientEvent("ai_stream_data", list(
                      messageId = current_search_replace_message_id,
                      delta = old_comment,
                      isComplete = FALSE,
                      isSearchReplace = TRUE,
                      field = "old_string",
                      filename = filename,
                      requestId = request_id,
                      sequence = .rs.get_next_ai_operation_sequence()
                    ))
                    
                    search_replace_old_comment_streamed <- TRUE
                  }
                  
                  current_accumulator <- search_replace_delta_accumulators[[call_id]]
                  stream_result <- .rs.stream_json_field_content(
                    if (!is.null(current_accumulator)) current_accumulator else "",
                    "old_string",
                    '\\s*"\\s*,\\s*"new_string"',
                    current_search_replace_message_id,
                    search_replace_old_string_streamed,
                    list(
                      isSearchReplace = TRUE,
                      field = "old_string",
                      filename = filename,
                      requestId = request_id
                    )
                  )
                  
                  if (!is.null(stream_result)) {
                    if (!is.null(stream_result$has_new_content) && stream_result$has_new_content) {
                      search_replace_old_string_streamed <- stream_result$new_streamed_content
                    }
                    if (!is.null(stream_result$end_reached) && stream_result$end_reached) {
                      new_string_started_key <- paste0("new_string_started_", call_id)
                      interactive_widget_states[[new_string_started_key]] <- TRUE
                    }
                  }
                }
                
                # Extract and stream partial new_string content using helper function (only if widget was created for this call_id)
                new_string_started_key <- paste0("new_string_started_", call_id)
                current_new_string_started <- interactive_widget_states[[new_string_started_key]]
                if (is.null(current_new_string_started)) {
                  current_new_string_started <- FALSE
                }
                
                if (widget_created && current_new_string_started) {
                  # Stream "New content" comment before actual content (only once)
                  if (!search_replace_new_comment_streamed) {
                    comment_syntax <- .rs.get_comment_syntax(filename)
                    new_comment <- paste0("\n\n", comment_syntax, "New content\n")
                    
                    .rs.enqueClientEvent("ai_stream_data", list(
                      messageId = current_search_replace_message_id,
                      delta = new_comment,
                      isComplete = FALSE,
                      isSearchReplace = TRUE,
                      field = "new_string",
                      filename = filename,
                      requestId = request_id,
                      sequence = .rs.get_next_ai_operation_sequence()
                    ))
                    
                    search_replace_new_comment_streamed <- TRUE
                  }
                  
                  current_accumulator <- search_replace_delta_accumulators[[call_id]]
                  stream_result <- .rs.stream_json_field_content(
                    if (!is.null(current_accumulator)) current_accumulator else "",
                    "new_string",
                    '"}',
                    current_search_replace_message_id,
                    search_replace_new_string_streamed,
                    list(
                      isSearchReplace = TRUE,
                      field = "new_string",
                      filename = filename,
                      requestId = request_id
                    )
                  )
                  
                  if (!is.null(stream_result) && !is.null(stream_result$has_new_content) && stream_result$has_new_content) {
                    search_replace_new_string_streamed <- stream_result$new_streamed_content
                  }
                }
                
                next  # Don't process search_replace deltas further
              }
              
              # For console/terminal command deltas, accumulate and stream to widgets
              if (!is.null(event_data$field) && (event_data$field == "run_console_cmd" || event_data$field == "run_terminal_cmd")) {
                is_console_cmd <- event_data$field == "run_console_cmd"
                call_id <- event_data$call_id
                
                # Pre-allocate message IDs if not already done (delta processing may happen before function_call action event)
                function_name <- event_data$field
                .rs.preallocate_function_message_ids(function_name, call_id)
                
                # Use pre-allocated message ID for each function call (not shared across calls)
                current_widget_message_id <- console_terminal_message_ids[[call_id]]
                if (is.null(current_widget_message_id)) {
                  # Use pre-allocated message ID for console/terminal (index 1 = function call itself)
                  current_widget_message_id <- .rs.get_preallocated_message_id(call_id, 1)
                  console_terminal_message_ids[[call_id]] <- current_widget_message_id
                }
                
                # Accumulate the delta for this specific call_id
                if (is.null(console_terminal_delta_accumulators[[call_id]])) {
                  console_terminal_delta_accumulators[[call_id]] <- ""
                }
                console_terminal_delta_accumulators[[call_id]] <- paste0(console_terminal_delta_accumulators[[call_id]], event_data$delta)
                                
                # Only create widget for the first interactive function call during streaming
                widget_created_key <- paste0("widget_created_", call_id)
                if (is.null(interactive_widget_states[[widget_created_key]]) || !interactive_widget_states[[widget_created_key]]) {

                  # Check if this is the first function call in the parallel set
                  is_first_widget <- .rs.is_first_function_call_in_parallel_set(call_id)
                  if (is_first_widget) {
                    # Create appropriate widget with placeholder values for first function call only
                    if (is_console_cmd) {
                      .rs.send_ai_operation("create_console_command", list(
                        message_id = as.numeric(current_widget_message_id),
                        command = "",
                        explanation = "Execute command",
                        request_id = request_id,
                        function_call_type = function_name
                      ))
                    } else {
                      .rs.send_ai_operation("create_terminal_command", list(
                        message_id = as.numeric(current_widget_message_id),
                        command = "",
                        explanation = "Execute command",
                        request_id = request_id,
                        function_call_type = function_name
                      ))
                    }
                    
                    interactive_widget_states[[widget_created_key]] <- TRUE
                    
                    # Store the widget type for later button creation
                    widget_type_key <- paste0("widget_type_", call_id)
                    interactive_widget_states[[widget_type_key]] <- if (is_console_cmd) "console" else "terminal"
                  }
                }
                
                # Detect start of command content for this specific call_id
                command_started_key <- paste0("command_started_", call_id)
                current_command_started <- console_terminal_command_states[[command_started_key]]
                if (is.null(current_command_started)) {
                  current_command_started <- FALSE
                }
                
                widget_created_key <- paste0("widget_created_", call_id)
                widget_created <- interactive_widget_states[[widget_created_key]]
                if (is.null(widget_created)) {
                  widget_created <- FALSE
                }
                
                if (widget_created && !current_command_started) {
                  current_accumulator <- console_terminal_delta_accumulators[[call_id]]
                  if (!is.null(current_accumulator) && grepl('"command"\\s*:\\s*"', current_accumulator, perl = TRUE)) {
                    console_terminal_command_states[[command_started_key]] <- TRUE
                    current_command_started <- TRUE
                  }
                }
                
                # Extract and stream partial command content
                if (widget_created && current_command_started) {
                  current_accumulator <- console_terminal_delta_accumulators[[call_id]]
                  # Use simple string extraction for command content
                  command_start <- regexpr('"command"\\s*:\\s*"', current_accumulator, perl = TRUE)
                  if (command_start > 0) {
                    # Find the start of the actual content (after the opening quote)
                    content_start_pos <- command_start + attr(command_start, "match.length")
                    
                    # Extract everything from the content start to the end of the accumulator
                    raw_content <- substr(current_accumulator, content_start_pos, nchar(current_accumulator))
                    
                    # First unescape the raw content completely
                    processed_content <- raw_content
                    processed_content <- 
                      gsub('<<<BS>>>', '\\\\',
                      gsub('<<<DQ>>>', '\\"',
                      gsub('<<<TAB>>>', '\\\\t',
                      gsub('<<<NL>>>', '\\\\n',
                      gsub('\\\\t', '\t',
                      gsub('\\\\n', '\n',
                      gsub('\\\\\\\"', '<<<DQ>>>',
                      gsub('\\\\\\\\t', '<<<TAB>>>',
                      gsub('\\\\\\\\n', '<<<NL>>>',
                      gsub('\\\\\\\\', '<<<BS>>>',
                      processed_content))))))))))
                    # Now apply buffering AFTER unescaping to avoid splitting escape sequences
                    # Check if we've reached the end of the command field by looking for ", "explanation"
                    explanation_pattern <- '\\s*"\\s*,\\s*"explanation"'
                    explanation_match <- regexpr(explanation_pattern, processed_content, perl = TRUE)
                    
                    buffer_size <- 20  # Hold back 20 characters to be safe
                    content_to_stream <- processed_content
                    
                    if (explanation_match > 0) {
                      # We found the end of command field - truncate content before any trailing whitespace and quote
                      content_to_stream <- substr(processed_content, 1, explanation_match - 1)
                    } else if (nchar(processed_content) > buffer_size) {
                      # No end marker found yet - stream all but the last buffer_size characters
                      content_to_stream <- substr(processed_content, 1, nchar(processed_content) - buffer_size)
                    } else {
                      # Content is shorter than buffer size - don't stream anything yet
                      content_to_stream <- ""
                    }
                    
                    # Only process if we have content to stream
                    if (nchar(content_to_stream) > 0) {
                      # Apply the same trimming logic as the handlers for proper command execution
                      trimmed_content <- content_to_stream
                      if (is_console_cmd) {
                        # Apply console command trimming (same as handle_run_console_cmd)
                        trimmed_content <- gsub("^```[rR]?[mM]?[dD]?\\s*\\n?", "", trimmed_content, perl = TRUE)
                        trimmed_content <- gsub("\\n?```\\s*$", "", trimmed_content, perl = TRUE)
                        trimmed_content <- gsub("```\\n", "", trimmed_content, perl = TRUE)
                        trimmed_content <- trimws(trimmed_content)
                      } else {
                        # Apply terminal command trimming (same as handle_run_terminal_cmd)
                        trimmed_content <- gsub("^```(?:shell|bash|sh)?\\s*\\n?", "", trimmed_content, perl = TRUE)
                        trimmed_content <- gsub("\\n?```\\s*$", "", trimmed_content, perl = TRUE)
                        trimmed_content <- gsub("```\\n", "", trimmed_content, perl = TRUE)
                        trimmed_content <- trimws(trimmed_content)
                      }
                      
                      # Stream any new content using call_id-specific tracking
                      current_streamed_key <- paste0("command_streamed_", call_id)
                      current_streamed <- console_terminal_command_streamed_states[[current_streamed_key]]
                      if (is.null(current_streamed)) {
                        current_streamed <- ""
                      }
                      
                      if (nchar(trimmed_content) > nchar(current_streamed)) {
                        new_content <- substr(trimmed_content, nchar(current_streamed) + 1, nchar(trimmed_content))
                        
                        if (nchar(new_content) > 0) {
                          # Send streaming delta to Java
                          partial_seq <- .rs.get_next_ai_operation_sequence()
                          .rs.enqueClientEvent("ai_stream_data", list(
                            messageId = current_widget_message_id,
                            delta = new_content,
                            isComplete = FALSE,
                            isConsoleCmd = is_console_cmd,
                            isTerminalCmd = !is_console_cmd,
                            requestId = request_id,
                            sequence = partial_seq
                          ))
                          
                          console_terminal_command_streamed_states[[current_streamed_key]] <- trimmed_content
                        }
                      }
                    }
                  }
                }
                
                next  # Don't process console/terminal deltas further
              }
                            
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
                assistant_message_id <- .rs.get_next_message_id()
              }
              
              # Clean the delta to remove triple backticks for regular assistant messages
              cleaned_delta <- event_data$delta
              
              # Create stream event with the assistant message ID (not requestId)
              stream_event <- list(
                messageId = assistant_message_id,
                delta = cleaned_delta,
                isComplete = FALSE
              )
              
              # Check if this is related to an interactive function
              # For interactive function cases, skip streaming assistant message entirely
              skip_assistant_message_streaming <- FALSE
              
              # On first chunk, check if we're in an interactive function streaming context
              if (nchar(accumulated_response) == nchar(event_data$delta)) {
                # This is the first chunk - check if this is interactive function related
                related_to_id <- .rs.get_conversation_var("current_related_to_id")
                
                # related_to_id should always be present
                if (is.null(related_to_id)) {
                  stop("related_to_id is required but was NULL when processing first chunk")
                }
                
                # Check if the related_to_id corresponds to a console, terminal, or other interactive function call
                function_call_type <- .rs.get_function_call_type_for_message(related_to_id)
                if (!is.null(function_call_type) && (function_call_type == "run_console_cmd" || function_call_type == "run_terminal_cmd" || function_call_type == "search_replace" || function_call_type == "run_file" || function_call_type == "delete_file")) {
                  # For all interactive function calls, DON'T stream assistant message content
                  # Interactive functions either: 1) show content via streaming, or 2) populate content during processing
                  skip_assistant_message_streaming <- TRUE
                } else {
                  # For non-interactive responses, continue with normal streaming logic
                  stream_event$messageId <- assistant_message_id
                }
              } else {
                # Not the first chunk - regular response, continue with normal streaming
                stream_event$messageId <- assistant_message_id
              }

              # Note: Triple backticks should NOT be cleaned for regular messages
              # They should only be processed by the markdown renderer to create proper code blocks

              # Always send deltas for regular streaming
              should_send_delta <- TRUE

              # Send real-time update to UI only if we have content to send and we're not skipping assistant streaming
              if (should_send_delta && nchar(stream_event$delta) > 0 && !is_summary_request && !skip_assistant_message_streaming) {
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
                  related_to_id <- ""
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
              
              # Process function calls normally
              # Set last_event_data to return this function call for processing
              last_event_data <- event_data
              
              # Add to buffer for parallel function call sequential processing
              function_name <- if (!is.null(event_data$function_call$name)) event_data$function_call$name else "UNKNOWN"
              call_id <- if (!is.null(event_data$function_call$call_id)) event_data$function_call$call_id else "UNKNOWN"
              
              # CRITICAL: Mark this as the first function call if it's the first one we encounter
              # This needs to happen for ALL function calls, not just streaming ones
              is_first_function_call <- .rs.is_first_function_call_in_parallel_set(call_id)
              
              # CRITICAL: Pre-allocate ALL message IDs for temporal order preservation
              pre_assigned_message_id <- .rs.preallocate_function_message_ids(function_name, call_id)
              
              function_call_data <- list(
                function_call = event_data$function_call,
                request_id = request_id,
                response_id = captured_response_id,
                message_id = pre_assigned_message_id
              )
              
              # Initialize buffer if not already done
              if (is.null(.rs.get_conversation_var("function_call_buffer"))) {
                .rs.init_function_call_buffer()
              }
              
              # Add to buffer
              buffer_count <- .rs.add_to_function_call_buffer(function_call_data)
              
              # Instead, set last_event_data to a completion event so streaming can finish properly
              last_event_data <- list(
                isComplete = TRUE,
                requestId = request_id,
                buffered_function_calls = TRUE  # Flag to indicate this is from buffered calls
              )
              
              # Reset assistant_message_id so new content gets a new messageId
              assistant_message_id <- NULL
              
              # Clean up console/terminal streaming context variables
              console_terminal_delta_accumulators <- list()
              interactive_widget_states <- list()
              console_terminal_command_states <- list()
              console_terminal_message_ids <- list()
              console_terminal_command_streamed_states <- list()
              
              # Clean up search_replace streaming accumulators  
              search_replace_delta_accumulators <- list()
              search_replace_message_ids <- list()
              
              # Clean up widget streaming context
              .rs.set_conversation_var("widget_delta_accumulator", NULL)
              .rs.set_conversation_var("widget_message_id", NULL)
              .rs.set_conversation_var("widget_created", NULL)
              .rs.set_conversation_var("widget_command_started", NULL)
              .rs.set_conversation_var("widget_command_streamed", NULL)
              .rs.set_conversation_var("widget_type", NULL)
              
              # Clean up search_replace streaming context (except accumulator - that's cleaned up after completion event)
              search_replace_filename_printed <- FALSE
              search_replace_old_string_started <- FALSE
              search_replace_new_string_started <- FALSE
              search_replace_old_string_streamed <- ""
              search_replace_new_string_streamed <- ""
              search_replace_old_comment_streamed <- FALSE
              search_replace_new_comment_streamed <- FALSE
              
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
              
              if (!is.null(event_data$field) && event_data$field == "search_replace" && 
                         event_data$isComplete) {                
                # Use the real call_id from the event, or generate one if missing
                call_id <- if (!is.null(event_data$call_id)) event_data$call_id else stop("call_id is required and cannot be NULL for search_replace completion")
                
                # Always use accumulated content for streaming functions
                arguments_content <- if (event_data$field == "search_replace") {
                  # Get accumulator for this specific call_id
                  current_accumulator <- search_replace_delta_accumulators[[call_id]]
                  if (!is.null(current_accumulator)) current_accumulator else ""
                } else {
                  ""
                }
                                
                # Create the function_call structure WITHOUT modifying event_data
                function_call_structure <- list(
                  name = event_data$field,
                  call_id = call_id,
                  arguments = arguments_content
                )
                
                # Preserve response_id for reasoning model chaining
                if (!is.null(captured_response_id)) {
                  # Use captured response_id if event doesn't have one
                  event_data$response_id <- captured_response_id
                }
                
                # Add this function call to the buffer for sequential processing
                
                # CRITICAL: Pre-assign message ID for ALL function calls to preserve temporal order
                # This ensures the first function call streamed gets the lowest message ID
                streaming_message_id <- NULL
                if (event_data$field == "search_replace") {
                  streaming_message_id <- search_replace_message_ids[[call_id]]
                } else {
                  # For non-streaming functions, pre-allocate all message IDs now to preserve order
                  streaming_message_id <- .rs.preallocate_function_message_ids(event_data$field, call_id)
                }
                
                function_call_data <- list(
                  function_call = function_call_structure,
                  request_id = request_id,
                  response_id = captured_response_id,
                  message_id = streaming_message_id
                )
                
                # Initialize buffer if not already done
                if (is.null(.rs.get_conversation_var("function_call_buffer"))) {
                  .rs.init_function_call_buffer()
                }
                
                # Add to buffer
                buffer_count <- .rs.add_to_function_call_buffer(function_call_data)
                event_data$buffered_function_calls <- TRUE
                
                # Clean up search_replace accumulator after using it for completion
                if (event_data$field == "search_replace") {
                  # Clear this specific call_id's accumulator
                  search_replace_delta_accumulators[[call_id]] <- NULL
                  search_replace_filename_printed <- FALSE
                  search_replace_old_string_started <- FALSE
                  search_replace_new_string_started <- FALSE
                  search_replace_old_string_streamed <- ""
                  search_replace_new_string_streamed <- ""
                  search_replace_new_comment_streamed <- FALSE
                }
              }
              
              # Handle console/terminal command completion events
              if (!is.null(event_data$field) && (event_data$field == "run_console_cmd" || event_data$field == "run_terminal_cmd") && 
                         event_data$isComplete) {
                # Use the real call_id from the event, or generate one if missing
                call_id <- if (!is.null(event_data$call_id)) event_data$call_id else stop("call_id is required and cannot be NULL for console/terminal completion")
                
                # Always use accumulated content for console/terminal commands
                current_accumulator <- console_terminal_delta_accumulators[[call_id]]
                arguments_content <- if (!is.null(current_accumulator)) current_accumulator else ""
                                
                # Create the function_call structure WITHOUT modifying event_data
                function_call_structure <- list(
                  name = event_data$field,  # "run_console_cmd" or "run_terminal_cmd"
                  call_id = call_id,
                  arguments = arguments_content
                )
                
                # Preserve response_id for reasoning model chaining
                if (!is.null(captured_response_id)) {
                  # Use captured response_id if event doesn't have one
                  event_data$response_id <- captured_response_id
                }
                
                # Add this function call to the buffer for sequential processing
                function_call_data <- list(
                  function_call = function_call_structure,
                  request_id = request_id,
                  response_id = captured_response_id,
                  message_id = current_widget_message_id
                )
                
                # Initialize buffer if not already done
                if (is.null(.rs.get_conversation_var("function_call_buffer"))) {
                  .rs.init_function_call_buffer()
                }
                
                # Add to buffer
                buffer_count <- .rs.add_to_function_call_buffer(function_call_data)
                event_data$buffered_function_calls <- TRUE
                
                # Only add function calls that have widgets to conversation log
                # Check if THIS specific call_id has a widget created
                widget_created_key <- paste0("widget_created_", call_id)
                has_widget <- !is.null(interactive_widget_states[[widget_created_key]]) && interactive_widget_states[[widget_created_key]]
                
                if (has_widget) {
                conversation_log <- .rs.read_conversation_log()
                
                # Get the related_to_id from conversation variables (the original user message ID)
                related_to_id <- .rs.get_conversation_var("current_related_to_id")
                if (is.null(related_to_id)) {
                  stop("related_to_id is required but was NULL when processing console/terminal function call")
                }
                
                # Create function call entry
                # Use the same unique message ID that was created for the widget
                function_call_message_id <- console_terminal_message_ids[[call_id]]
                function_call_entry <- list(
                  id = function_call_message_id,
                  role = "assistant",
                  function_call = function_call_structure,
                  related_to = related_to_id,  # This is the original user message ID
                  request_id = request_id
                )
                
                # Add function call entry to conversation log
                conversation_log <- c(conversation_log, list(function_call_entry))
                
                # Also add pending function call output using pre-allocated ID (index 2)
                pending_output_id <- .rs.get_preallocated_message_id(call_id, 2)
                pending_output <- list(
                  id = pending_output_id,
                  type = "function_call_output",
                  call_id = call_id,
                  output = "Response pending...",
                  related_to = function_call_message_id,
                  procedural = TRUE  # Mark as procedural so it doesn't show in UI
                )
                conversation_log <- c(conversation_log, list(pending_output))
                
                .rs.write_conversation_log(conversation_log)
                }
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
                
                # Save during streaming
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
              
              # Only send completion event for regular responses (skip for summarization)
              if (!is_summary_request) {
                .rs.enqueClientEvent("ai_stream_data", list(
                  messageId = assistant_message_id,
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
            } else if (!is.null(event_data$web_search_call)) {
              # Handle web search call events - create immediate display message during streaming
              web_search_call <- event_data$web_search_call
              if (!is.null(web_search_call$status)) {
                status <- web_search_call$status
                search_id <- if (!is.null(web_search_call$id)) web_search_call$id else "unknown"
              }
              
              # Save web_search_call metadata to conversation log for display system to handle
              if (!is_summary_request && !is.null(web_search_call$query)) {
                # If we have accumulated text content, complete the current assistant message first
                if (!is.null(assistant_message_id) && nchar(accumulated_response) > 0) {
                  # Complete the current text message
                  .rs.enqueClientEvent("ai_stream_data", list(
                    messageId = assistant_message_id,
                    delta = "",
                    isComplete = TRUE,
                    sequence = .rs.get_next_ai_operation_sequence()
                  ))
                  
                  # Save the accumulated text to conversation log
                  related_to_id <- .rs.get_conversation_var("current_related_to_id")
                  if (!is.null(related_to_id)) {
                    tryCatch({
                      conversation_index <- .rs.get_current_conversation_index()
                      result <- .rs.process_assistant_response(
                        accumulated_response, 
                        assistant_message_id,
                        related_to_id,
                        conversation_index, 
                        "ai_operation",
                        NULL,
                        NULL
                      )
                    }, error = function(e) {
                      cat("Error saving text before web search:", e$message, "\n")
                    })
                  }
                  
                  # Clear accumulated response and reset assistant message ID for new content
                  accumulated_response <- ""
                  assistant_message_id <- NULL
                }
                
                related_to_id <- .rs.get_conversation_var("current_related_to_id")
                if (is.null(related_to_id)) {
                  related_to_id <- ""
                }
                
                tryCatch({
                  conversation_index <- .rs.get_current_conversation_index()
                  web_search_message_id <- .rs.get_next_message_id()
                  
                  # Create web search metadata entry for conversation display system to process
                  message_data <- list(
                    type = "assistant",
                    text = "",  # Empty text since this is metadata for display system
                    web_search_call = event_data$web_search_call,
                    timestamp = Sys.time(),
                    id = web_search_message_id,
                    related_to = related_to_id
                  )
                  
                  # Add to conversation log
                  conversation_log <- .rs.read_conversation_log()
                  conversation_log <- append(conversation_log, list(message_data))
                  .rs.write_conversation_log(conversation_log)
                  
                  # Trigger conversation display update to immediately show the web search message
                  .rs.update_conversation_display()
                  
                }, error = function(e) {
                  cat("Error saving web_search_call metadata to conversation log:", e$message, "\n")
                })
              }
              
            } else if (!is.null(event_data$error)) {
              # Error event from backend
              last_event_data <- event_data
              streaming_complete <- TRUE
              break
            } else {
              unmatched_events_count <- unmatched_events_count + 1
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
  
  # Send create_widget_buttons operations for the first interactive function call BEFORE cleanup
  # Only the first function call gets a widget, so only it needs buttons
  first_function_call_id <- .rs.get_conversation_var("first_function_call_id")
  
  if (!is.null(first_function_call_id)) {
    # Read conversation log to find the function call details
    conversation_log <- .rs.read_conversation_log()
    
    for (entry in conversation_log) {
      if (!is.null(entry$function_call) && !is.null(entry$function_call$call_id) && 
          entry$function_call$call_id == first_function_call_id) {
        
        function_name <- entry$function_call$name
        
        # Only create buttons for interactive functions
        if (function_name %in% c("run_console_cmd", "run_terminal_cmd", "delete_file", "run_file")) {
          # Get the message ID for this function call (should be the entry ID)
          widget_message_id <- entry$id
          
          # Determine widget type
          widget_type <- if (function_name == "run_console_cmd" || function_name == "run_file") "console" else if (function_name == "run_terminal_cmd") "terminal" else "interactive"
          
          # For console commands and run_file commands, check if we should auto-accept instead of creating buttons
          if (function_name == "run_console_cmd" || function_name == "run_file") {
            
            if (function_name == "run_console_cmd") {
              # Get the command from the function call arguments
              command <- .rs.safe_parse_function_arguments(entry$function_call)$command
              
              # Extract R functions from the console command for allow list
              extracted_functions <- ""
              if (!is.null(command) && nchar(command) > 0) {
                extracted_r_functions <- .rs.extract_r_functions(command)
                # Sanitize tokens
                extracted_r_functions <- unique(trimws(extracted_r_functions))
                extracted_r_functions <- extracted_r_functions[nzchar(extracted_r_functions)]
                if (length(extracted_r_functions) > 0) {
                  extracted_functions <- paste(extracted_r_functions, collapse = ", ")
                  extracted_functions <- trimws(extracted_functions)
                }
              }
              
              # Check if this command should be auto-accepted
              should_auto_accept <- .rs.should_auto_accept_console_command(command)
              
            } else if (function_name == "run_file") {
              # Get the filename from the function call arguments
              args <- .rs.safe_parse_function_arguments(entry$function_call)
              filename <- args$filename
              
              # Extract filename for allow list (use just the filename for display)
              extracted_files <- ""
              if (!is.null(filename) && nchar(filename) > 0) {
                # For run_file, we want to show the full filepath in the allow list
                extracted_files <- filename
              }
              
              # Check if this file should be auto-run
              should_auto_accept <- .rs.should_auto_accept_run_file(filename)
            }
            
            if (should_auto_accept) {
              # For auto-accept: create buttons but mark for immediate auto-execution
              if (function_name == "run_console_cmd") {
                .rs.send_ai_operation("create_widget_buttons", list(
                  message_id = as.character(widget_message_id),
                  content = widget_type,
                  auto_accept = TRUE,  # Flag for orchestrator to auto-accept
                  extracted_functions = extracted_functions
                ))
              } else if (function_name == "run_file") {
                .rs.send_ai_operation("create_widget_buttons", list(
                  message_id = as.character(widget_message_id),
                  content = widget_type,
                  auto_accept = TRUE,  # Flag for orchestrator to auto-accept
                  extracted_files = extracted_files
                ))
              }
            } else {
              # Send create_widget_buttons operation for manual acceptance
              if (function_name == "run_console_cmd") {
                .rs.send_ai_operation("create_widget_buttons", list(
                  message_id = as.character(widget_message_id),
                  content = widget_type,
                  extracted_functions = extracted_functions
                ))
              } else if (function_name == "run_file") {
                .rs.send_ai_operation("create_widget_buttons", list(
                  message_id = as.character(widget_message_id),
                  content = widget_type,
                  extracted_files = extracted_files
                ))
              }
            }
          } else if (function_name == "run_terminal_cmd") {
            # Get the command from the function call arguments
            command <- .rs.safe_parse_function_arguments(entry$function_call)$command
            
            # Extract bash commands from the terminal command for allow list
            extracted_commands <- ""
            if (!is.null(command) && nchar(command) > 0) {
              extracted_bash_commands <- .rs.extract_bash_functions(command)
              if (length(extracted_bash_commands) > 0) {
                extracted_commands <- paste(extracted_bash_commands, collapse = ", ")
              }
            }
            
            # Check if this command should be auto-accepted
            should_auto_accept <- .rs.should_auto_accept_terminal_command(command)
            
            if (should_auto_accept) {
              # For auto-accept: create buttons but mark for immediate auto-execution
              .rs.send_ai_operation("create_widget_buttons", list(
                message_id = as.character(widget_message_id),
                content = widget_type,
                auto_accept = TRUE,  # Flag for orchestrator to auto-accept
                extracted_commands = extracted_commands
              ))
            } else {
              # Send create_widget_buttons operation for manual acceptance
              .rs.send_ai_operation("create_widget_buttons", list(
                message_id = as.character(widget_message_id),
                content = widget_type,
                extracted_commands = extracted_commands
              ))
            }
          } else if (function_name == "delete_file") {
            # Get the filename from the function call arguments
            args <- .rs.safe_parse_function_arguments(entry$function_call)
            filename <- args$filename
            
            # Check if this file should be auto-deleted
            should_auto_accept <- .rs.should_auto_accept_delete_file(filename)
            
            if (should_auto_accept) {
              # For auto-accept: create buttons but mark for immediate auto-execution
              .rs.send_ai_operation("create_widget_buttons", list(
                message_id = as.character(widget_message_id),
                content = widget_type,
                auto_accept = TRUE  # Flag for orchestrator to auto-accept
              ))
            } else {
              # Send create_widget_buttons operation for manual acceptance
              .rs.send_ai_operation("create_widget_buttons", list(
                message_id = as.character(widget_message_id),
                content = widget_type
              ))
            }
          } else {
            # For non-console commands, always create buttons
            .rs.send_ai_operation("create_widget_buttons", list(
              message_id = as.character(widget_message_id),
              content = widget_type
            ))
          }
          
          break
        }
      }
    }
  }
  
  # Clean up search_replace streaming context
  search_replace_delta_accumulators <- list()
  search_replace_message_ids <- list()
  search_replace_filename_printed <- FALSE
  search_replace_old_string_started <- FALSE
  search_replace_new_string_started <- FALSE
  search_replace_old_string_streamed <- ""
  search_replace_new_string_streamed <- ""
  search_replace_old_comment_streamed <- FALSE
  search_replace_new_comment_streamed <- FALSE
  
  # Clean up console/terminal streaming context variables
  console_terminal_delta_accumulators <- list()
  interactive_widget_states <- list()
  console_terminal_command_states <- list()
  console_terminal_message_ids <- list()
  console_terminal_command_streamed_states <- list()
  
  # Clean up widget streaming context
  .rs.set_conversation_var("widget_delta_accumulator", NULL)
  .rs.set_conversation_var("widget_message_id", NULL)
  .rs.set_conversation_var("widget_created", NULL)
  .rs.set_conversation_var("widget_command_started", NULL)
  .rs.set_conversation_var("widget_command_streamed", NULL)
  .rs.set_conversation_var("widget_type", NULL)
  
  # Clean up early widget calls tracking
  .rs.set_conversation_var("early_widget_calls", NULL)
  
  # Clean up first function call tracking (only if no buffered calls remain)
  # If there are still buffered function calls, keep the first_function_call_id 
  # so subsequent API requests don't interfere with temporal ordering
  has_buffered_calls <- .rs.has_buffered_function_calls()
  if (!has_buffered_calls) {
    .rs.set_conversation_var("first_function_call_id", NULL)
  }
      
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
      # Include HTTP status if available
      if (!is.null(last_event_data$http_status)) {
        result$http_status <- last_event_data$http_status
      }
      # For structured errors, extract user-friendly message; for string errors, use as-is
      if (is.list(last_event_data$error) && !is.null(last_event_data$error$user_message)) {
        result$message <- last_event_data$error$user_message
      } else if (is.character(last_event_data$error)) {
        result$message <- last_event_data$error
      } else {
        result$message <- "Unknown error from backend"
      }
    } else if (!is.null(last_event_data$action)) {
      # Function call or other action - prioritize this over response
      result$action <- last_event_data$action
      if (!is.null(last_event_data$function_call)) {
        result$function_call <- last_event_data$function_call
      }
    } else if (!is.null(last_event_data$response)) {
      result$response <- last_event_data$response
    } else if (!is.null(last_event_data$filename)) {
      result$filename <- last_event_data$filename
    } else if (!is.null(last_event_data$conversation_name)) {
      result$conversation_name <- last_event_data$conversation_name
    } else if (!is.null(last_event_data$interpretation)) {
      result$interpretation <- last_event_data$interpretation
    }
    
    # Include end_turn flag if present in the streaming event
    if (!is.null(last_event_data$end_turn) && last_event_data$end_turn == TRUE) {
      result$end_turn <- TRUE
    }
    
    # Include buffered_function_calls flag if present
    if (!is.null(last_event_data$buffered_function_calls) && last_event_data$buffered_function_calls == TRUE) {
      result$buffered_function_calls <- TRUE
    }
    
    # Include the assistant message ID so it can be passed to process_assistant_response
    if (!is.null(assistant_message_id)) {
      result$assistant_message_id <- assistant_message_id
    }
    
    # Include response_id for reasoning model chaining
    # Prioritize response_id from final event, fall back to captured response_id
    if (!is.null(last_event_data$response_id)) {
      result$response_id <- last_event_data$response_id
    } else if (!is.null(captured_response_id)) {
      result$response_id <- captured_response_id
    }
    
    return(result)
  } else {        
    stop("No response received from backend, timeout or error")
  }
})



# Function call buffering system for parallel function calls
.rs.addFunction("init_function_call_buffer", function() {
  .rs.set_conversation_var("function_call_buffer", list())
  .rs.set_conversation_var("function_call_buffer_active", FALSE)
  .rs.set_conversation_var("processing_buffered_function_call", FALSE)
})

.rs.addFunction("add_to_function_call_buffer", function(function_call_data) {
  buffer <- .rs.get_conversation_var("function_call_buffer")
  if (is.null(buffer)) {
    buffer <- list()
  }
  
  # Extract function call details for debugging
  function_name <- if (!is.null(function_call_data$function_call$name)) {
    function_call_data$function_call$name
  } else {
    "UNKNOWN"
  }
  
  call_id <- if (!is.null(function_call_data$function_call$call_id)) {
    function_call_data$function_call$call_id
  } else {
    "UNKNOWN"
  }
  
  # For run_file and delete_file, immediately mark as having widgets to prevent subsequent streaming widgets
  if (function_name == "run_file" || function_name == "delete_file") {
    # Store this in conversation variables so it's accessible during streaming
    existing_widget_calls <- .rs.get_conversation_var("early_widget_calls")
    if (is.null(existing_widget_calls)) {
      existing_widget_calls <- list()
    }
    existing_widget_calls[[call_id]] <- TRUE
    .rs.set_conversation_var("early_widget_calls", existing_widget_calls)
  }
  
  # Add the function call to buffer with timestamp
  buffered_call <- list(
    function_call = function_call_data$function_call,
    request_id = function_call_data$request_id,
    response_id = function_call_data$response_id,
    message_id = function_call_data$message_id,
    timestamp = Sys.time()
  )
  
  buffer <- c(buffer, list(buffered_call))
  .rs.set_conversation_var("function_call_buffer", buffer)
  
  # Mark buffer as active if this is the first function call
  if (length(buffer) == 1) {
    .rs.set_conversation_var("function_call_buffer_active", TRUE)
  }
  
  return(length(buffer))
})

.rs.addFunction("get_next_buffered_function_call", function() {
  buffer <- .rs.get_conversation_var("function_call_buffer")
  if (is.null(buffer) || length(buffer) == 0) {
    return(NULL)
  }
  
  # Get the first function call
  next_call <- buffer[[1]]
  
  # Extract details for debugging
  function_name <- if (!is.null(next_call$function_call$name)) {
    next_call$function_call$name
  } else {
    "UNKNOWN"
  }
  
  call_id <- if (!is.null(next_call$function_call$call_id)) {
    next_call$function_call$call_id
  } else {
    "UNKNOWN"
  }
  
  # Remove it from buffer
  remaining_buffer <- if (length(buffer) > 1) buffer[2:length(buffer)] else list()
  .rs.set_conversation_var("function_call_buffer", remaining_buffer)
  
  # Mark buffer as inactive if no more function calls
  if (length(remaining_buffer) == 0) {
    .rs.set_conversation_var("function_call_buffer_active", FALSE)
    .rs.set_conversation_var("first_function_call_id", NULL)
  }
    
  return(next_call)
})

.rs.addFunction("has_buffered_function_calls", function() {
  buffer <- .rs.get_conversation_var("function_call_buffer")
  return(!is.null(buffer) && length(buffer) > 0)
})

.rs.addFunction("clear_function_call_buffer", function() {
  .rs.set_conversation_var("function_call_buffer", list())
  .rs.set_conversation_var("function_call_buffer_active", FALSE)
  .rs.set_conversation_var("processing_buffered_function_call", FALSE)
})

# Helper function for streaming JSON field content with proper escaping and buffering
.rs.addFunction("stream_json_field_content", function(
  delta_accumulator, 
  field_name, 
  end_marker_pattern, 
  message_id, 
  current_streamed, 
  event_properties = list()
) {
  # Use simple string extraction for field content
  field_start_pattern <- paste0('"', field_name, '"\\s*:\\s*"')
  field_start <- regexpr(field_start_pattern, delta_accumulator, perl = TRUE)
  
  if (field_start > 0) {
    # Find the start of the actual content (after the opening quote)
    content_start_pos <- field_start + attr(field_start, "match.length")
    
    # Extract everything from the content start to the end of the accumulator
    raw_content <- substr(delta_accumulator, content_start_pos, nchar(delta_accumulator))
    
    # First unescape the raw content completely
    processed_content <- raw_content
    processed_content <- 
      gsub('<<<BS>>>', '\\\\',
      gsub('<<<DQ>>>', '\\"',
      gsub('<<<TAB>>>', '\\\\t',
      gsub('<<<NL>>>', '\\\\n',
      gsub('\\\\t', '\t',
      gsub('\\\\n', '\n',
      gsub('\\\\\\\"', '<<<DQ>>>',
      gsub('\\\\\\\\t', '<<<TAB>>>',
      gsub('\\\\\\\\n', '<<<NL>>>',
      gsub('\\\\\\\\', '<<<BS>>>',
      processed_content))))))))))
    
    # Check if we've reached the end of the field by looking for end marker
    end_match <- regexpr(end_marker_pattern, processed_content, perl = TRUE)
      
    buffer_size <- 20  # Hold back 20 characters to be safe
    content_to_stream <- processed_content
    
    result <- list(end_reached = FALSE)
    
    if (end_match > 0) {
      # We found the end of field - truncate content before any trailing whitespace and quote
      content_to_stream <- substr(processed_content, 1, end_match - 1)
      result$end_reached <- TRUE
    } else if (nchar(processed_content) > buffer_size) {
      # No end marker found yet - stream all but the last buffer_size characters
      content_to_stream <- substr(processed_content, 1, nchar(processed_content) - buffer_size)
    } else {
      # Content is shorter than buffer size - don't stream anything yet
      content_to_stream <- ""
    }
    
    # Only process if we have content to stream
    if (nchar(content_to_stream) > 0) {
      # Stream any new content
      if (nchar(content_to_stream) > nchar(current_streamed)) {
        new_content <- substr(content_to_stream, nchar(current_streamed) + 1, nchar(content_to_stream))
        
        if (nchar(new_content) > 0) {
          # Send streaming delta to Java
          partial_seq <- .rs.get_next_ai_operation_sequence()
          
          # Build event with common properties and merge with custom properties
          stream_event <- list(
            messageId = message_id,
            delta = new_content,
            isComplete = FALSE,
            sequence = partial_seq
          )
          
          # Merge custom properties
          for (prop_name in names(event_properties)) {
            stream_event[[prop_name]] <- event_properties[[prop_name]]
          }
          
          .rs.enqueClientEvent("ai_stream_data", stream_event)
          
          result$new_streamed_content <- content_to_stream
          result$has_new_content <- TRUE
        }
      }
    }
    
    return(result)
  }
  
  return(NULL)
})

# Helper function to get comment syntax based on file extension
.rs.addFunction("get_comment_syntax", function(filename) {
  if (is.null(filename) || nchar(filename) == 0) {
    return("# ")  # Default to hash comments
  }
  
  # Extract file extension
  ext <- tolower(tools::file_ext(filename))
  
  # Map extensions to comment syntax
  if (ext %in% c("r", "py", "sh", "bash", "yaml", "yml", "rb", "pl", "ps1")) {
    return("# ")
  } else if (ext %in% c("js", "ts", "java", "c", "cpp", "cc", "cxx", "h", "hpp", "cs", "php", "scala", "kt", "go", "rs", "swift")) {
    return("// ")
  } else if (ext %in% c("sql", "lua")) {
    return("-- ")
  } else if (ext %in% c("html", "xml", "svg")) {
    return("<!-- ")
  } else if (ext %in% c("css")) {
    return("/* ")
  } else if (ext %in% c("tex", "sty")) {
    return("% ")
  } else if (ext %in% c("m", "matlab")) {
    return("% ")
  } else if (ext %in% c("vb", "bas")) {
    return("' ")
  } else if (ext %in% c("f", "f90", "f95", "f03", "f08")) {
    return("! ")
  } else {
    return("# ")  # Default fallback
  }
})

# Helper function to determine how many message IDs a function type needs
.rs.addFunction("get_function_message_id_count", function(function_name) {
  # Based on analysis of actual handlers:
  
  # Simple non-interactive: function_call + function_call_output = 2 IDs
  simple_functions <- c("list_dir", "find_keyword_context", "grep", "read_file", "view_image", "search_for_file")
  
  # Interactive functions: function_call + function_call_output + procedural message = 3 IDs  
  interactive_functions <- c("run_console_cmd", "run_terminal_cmd", "delete_file", "run_file", "search_replace")
  
  if (function_name %in% simple_functions) {
    return(2)
  } else if (function_name %in% interactive_functions) {
    return(3) 
  } else {
    # Default for unknown functions
    return(2)
  }
})

# Helper function to pre-allocate message IDs for a function call
.rs.addFunction("preallocate_function_message_ids", function(function_name, call_id) {
  # Check if this call_id already has pre-allocated IDs
  preallocated_ids <- .rs.get_conversation_var("preallocated_message_ids", list())
  
  if (!is.null(preallocated_ids[[call_id]])) {
    # Already exists - return the first message ID from the existing set
    existing_ids <- preallocated_ids[[call_id]]
    return(existing_ids[[1]])
  }
  
  # Get the number of message IDs needed
  id_count <- .rs.get_function_message_id_count(function_name)
  
  # Pre-allocate all needed message IDs
  message_ids <- list()
  for (i in 1:id_count) {
    message_ids[[i]] <- .rs.get_next_message_id()
  }
  
  # Store them in conversation variables keyed by call_id
  preallocated_ids[[call_id]] <- message_ids
  .rs.set_conversation_var("preallocated_message_ids", preallocated_ids)
  
  # Return the first message ID (for the function call itself)
  return(message_ids[[1]])
})

# Helper function to get pre-allocated message ID for a function call
.rs.addFunction("get_preallocated_message_id", function(call_id, index = 1) {
  preallocated_ids <- .rs.get_conversation_var("preallocated_message_ids", list())
  
  if (!is.null(preallocated_ids[[call_id]]) && length(preallocated_ids[[call_id]]) >= index) {
    return(preallocated_ids[[call_id]][[index]])
  }
  
  # Fallback - generate new ID if not found
  return(.rs.get_next_message_id())
})

# Helper function to determine if this call_id is the first function call in the parallel set
.rs.addFunction("is_first_function_call_in_parallel_set", function(call_id) {
  # Track the first function call we encounter during this streaming session
  first_function_call_id <- .rs.get_conversation_var("first_function_call_id")
  
  if (is.null(first_function_call_id)) {
    # This is the first function call we've encountered - mark it and return TRUE
    .rs.set_conversation_var("first_function_call_id", call_id)
    return(TRUE)
  } else {
    # Check if this call_id matches the first one we encountered
    is_first <- call_id == first_function_call_id
    return(is_first)
  }
})