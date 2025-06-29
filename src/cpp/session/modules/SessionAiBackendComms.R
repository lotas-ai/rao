#
# SessionAiBackendComms.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#


.rs.setVar("backend_environments", list(
  local = list(
    url = "http://localhost:8080",
    name = "Local Development"
  ),
  production = list(
    url = "https://api.lotas.ai",
    name = "Production"
  )
))

.rs.addFunction("initialize_backend_defaults", function() {
  environments <- .rs.getVar("backend_environments")
  config <- environments[["production"]]
  .rs.setVar("backend_server_url", config$url)
  .rs.setVar("backend_environment", "production")
})

.rs.initialize_backend_defaults()


.rs.addFunction("detect_backend_environment", function() {
  # Check if localhost:8080 backend is available
  local_available <- tryCatch({
    response <- httr2::req_perform(
      httr2::req_timeout(
        httr2::request("http://localhost:8080/actuator/health"),
        3
      )
    )
    httr2::resp_status(response) == 200
  }, error = function(e) FALSE)
  
  environments <- .rs.getVar("backend_environments")
  
  if (local_available) {
    # Use local environment
    config <- environments[["local"]]
    .rs.setVar("backend_server_url", config$url)
    .rs.setVar("backend_environment", "local")
    .rs.setVar("backend_environment_checked", TRUE)
    return("local")
  } else {
    # Use production environment
    config <- environments[["production"]]
    .rs.setVar("backend_server_url", config$url)
    .rs.setVar("backend_environment", "production")
    .rs.setVar("backend_environment_checked", TRUE)
    return("production")
  }
})

.rs.addFunction("get_backend_config", function(conversation = NULL, additional_data = NULL) {
  # Only check environment if we haven't checked yet
  if (is.null(.rs.getVar("backend_environment_checked")) || !.rs.getVar("backend_environment_checked")) {
    .rs.detect_backend_environment()
  }
  
  list(
    url = .rs.getVar("backend_server_url"),
    environment = .rs.getVar("backend_environment")
  )
})

.rs.addFunction("reset_backend_environment", function() {
  # Reset the environment check flag to force re-detection on next get_backend_config call
  .rs.setVar("backend_environment_checked", FALSE)
  # Re-detect immediately
  .rs.detect_backend_environment()
})

.rs.addFunction("generate_backend_auth", function(provider = NULL) {
  if (is.null(provider)) {
    provider <- .rs.get_active_provider()
  }
  
  # Always require a real Rao API key regardless of local/production mode
  api_key <- .rs.get_api_key(provider)
  
  if (is.null(api_key)) {
    stop("No API key found. Please set up a valid Rao API key at www.lotas.ai/account.")
  }
  
  list(
    api_key = api_key
  )
})

.rs.addFunction("remove_rmd_frontmatter", function(content) {
  if (is.null(content) || nchar(content) == 0) {
    return(content)
  }
  
  if (grepl("```rmd|```Rmd|```markdown|````rmd|````Rmd|````markdown", content, ignore.case = TRUE)) {
    
    lines <- strsplit(content, "\n")[[1]]
    
    rmd_start_line <- NULL
    for (i in 1:length(lines)) {
      if (grepl("```+\\s*[rR]md|```+\\s*markdown", lines[i])) {
        rmd_start_line <- i
        break
      }
    }
    
    if (!is.null(rmd_start_line)) {
      if (rmd_start_line < length(lines) && grepl("^---\\s*$", lines[rmd_start_line + 1])) {
        frontmatter_end_line <- NULL
        for (i in (rmd_start_line + 2):length(lines)) {
          if (grepl("^---\\s*$", lines[i])) {
            frontmatter_end_line <- i
            break
          }
        }
        
        if (!is.null(frontmatter_end_line)) {
          lines <- c(
            lines[1:rmd_start_line],
            lines[(frontmatter_end_line + 1):length(lines)]
          )
        }
      }
      
      lines[rmd_start_line] <- sub("```+\\s*([rR]md|markdown)", "````\\1", lines[rmd_start_line])
      
      has_closing_backticks <- FALSE
      for (i in length(lines):1) {
        if (i > rmd_start_line && grepl("^```+\\s*$", lines[i])) {
          lines[i] <- "````"
          has_closing_backticks <- TRUE
          break
        }
      }
      
      if (!has_closing_backticks) {
        lines <- c(lines, "````")
      }
      
      processed_lines <- character(0)
      skip_next <- FALSE
      for (i in 1:(length(lines)-1)) {
        if (skip_next) {
          skip_next <- FALSE
          next
        }
        
        if (grepl("^````\\s*$", lines[i]) && grepl("^````\\s*$", lines[i+1])) {
          processed_lines <- c(processed_lines, lines[i])
          skip_next <- TRUE
        } else {
          processed_lines <- c(processed_lines, lines[i])
        }
      }
      
      if (!skip_next && length(lines) > 0) {
        processed_lines <- c(processed_lines, lines[length(lines)])
      }
      
      content <- paste(processed_lines, collapse = "\n")
    }
  }
  
  if (grepl("```````$", content)) {
    content <- gsub("```````$", "```\n````", content)
  }

  return(content)
})

.rs.addFunction("extract_file_references_from_code", function(code) {
  if (is.null(code) || nchar(code) == 0) {
    return(character(0))
  }
  
  pattern <- "[\"\']([^\"\']*/)?([^\"\'/]+\\.[a-zA-Z0-9]+)[\"\']"
  
  matches <- gregexpr(pattern, code, perl = TRUE)
  if (length(matches) == 0 || matches[[1]][1] == -1) {
    return(character(0))
  }
  
  all_matches <- regmatches(code, matches)[[1]]
  
  cleaned_matches <- gsub("^[\"\']|[\"\']$", "", all_matches)
  
  return(unique(cleaned_matches))
})

.rs.addFunction("process_backend_response", function(response, conversation = NULL, provider = NULL, model = NULL) {
  if (is.null(response) || !is.character(response) || length(response) == 0) {
    return(response)
  }
  
  response <- .rs.remove_rmd_frontmatter(response)
    
  return(response)
})


.rs.addFunction("prepare_attachment_data", function() {
  conversation_index <- .rs.get_current_conversation_index()
     base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversation_index))
  csv_path <- file.path(conversation_dir, "attachments.csv")
  
  if (!file.exists(csv_path)) {
    return(NULL)
  }
  
  attachments <- tryCatch({
    read.csv(csv_path, stringsAsFactors = FALSE)
  }, error = function(e) {
    return(NULL)
  })
  
  if (is.null(attachments) || nrow(attachments) == 0) {
    return(NULL)
  }
  
  vector_store_id <- attachments$vector_store_id[1]
  
  attachment_data <- list()
  
  for (i in seq_len(nrow(attachments))) {
    attachment_info <- list(
      file_path = attachments$file_path[i],
      file_name = basename(attachments$file_path[i]),
      file_id = attachments$file_id[i],
      vector_store_id = attachments$vector_store_id[i],
      timestamp = attachments$timestamp[i],
      message_id = attachments$message_id[i]
    )
    

    
    attachment_data[[length(attachment_data) + 1]] <- attachment_info
  }
  
  result <- list(
    attachments = attachment_data,
    vector_store_id = vector_store_id,
    has_attachments = TRUE
  )
  

  
  return(result)
})

.rs.addFunction("extract_symbols_for_backend", function(conversation) {
  return(.rs.check_message_for_symbols(conversation))
})

.rs.addFunction("gather_user_environment_info", function() {
  os_info <- Sys.info()
  os_version <- paste(os_info[["sysname"]], os_info[["release"]], sep = " ")
  if (!is.na(os_info[["version"]]) && os_info[["version"]] != "") {
    os_version <- paste(os_version, os_info[["version"]], sep = "-")
  }
  
  workspace_path <- getwd()
  
  shell <- Sys.getenv("SHELL", unset = "")
  if (shell == "") {
    if (os_info[["sysname"]] == "Windows") {
      shell <- Sys.getenv("COMSPEC", unset = "cmd.exe")
    } else {
      shell <- "/bin/bash"
    }
  }
  
  return(list(
    user_os_version = os_version,
    user_workspace_path = workspace_path,
    user_shell = shell
  ))
})

.rs.addFunction("send_backend_query", function(request_type, conversation, provider = NULL, model = NULL, request_id, additional_data = NULL) {
  .rs.check_required_packages()
  
  # CRITICAL FIX: Check cancellation FIRST before any backend communication
  # This prevents new API calls when user has already cancelled
  if (.rs.get_conversation_var("ai_cancelled")) {
    cat("DEBUG CANCELLATION: send_backend_query returning NULL due to ai_cancelled\n")
    return(NULL)
  }
  
  is_conversation_name_request <- (request_type == "generate_conversation_name")
  is_summarization_request <- (request_type == "summarize_conversation")
  
  # Check if there's already a thinking message active and set default if not
  # Skip thinking messages for conversation name generation (silent background operation)
  if (!is_conversation_name_request && !is_summarization_request) {
    last_thinking_time <- .rs.getVar("last_thinking_message_time")
    current_time <- Sys.time()
    
    # If no thinking message was set in the last 2 seconds, set a default one
    if (is.null(last_thinking_time) || difftime(current_time, last_thinking_time, units = "secs") > 2) {
      .rs.enqueClientEvent("update_thinking_message", list(message = "Thinking..."))
      .rs.setVar("last_thinking_message_time", current_time)
    }
  }
  
  if (.rs.get_conversation_var("ai_cancelled")) {
    if (!is_conversation_name_request && !is_summarization_request) {
      .rs.enqueClientEvent("update_thinking_message", list(message = "", hide_cancel = TRUE))
    }
    .rs.reset_ai_cancellation()
    # Don't cancel summarization requests
    if (is_summarization_request) {
      .rs.reset_ai_cancellation()
    } else {
      return(NULL)
    }
  }
  
  is_after_edit_file <- .rs.is_last_function_edit_file()
  
  if (is.null(provider)) {
    provider <- .rs.get_active_provider()
    
    if (is.null(provider)) {
      if (!is.null(.rs.get_api_key("openai"))) {
        provider <- "openai"
      } else {
        if (!is_conversation_name_request) {
          .rs.enqueClientEvent("update_thinking_message", list(message = "", hide_cancel = TRUE))
        }
        stop("No AI provider available. Please set up an API key in the API key management pane (key icon) before attempting to use AI features.")
      }
    }
  }

  api_key <- .rs.get_api_key(provider)
  if (is.null(api_key)) {
    if (!is_conversation_name_request) {
      .rs.enqueClientEvent("update_thinking_message", list(message = "", hide_cancel = TRUE))
    }
    stop(paste0("No API key found for ", provider, ". Please set up a valid API key in the API key management pane."))
  }
  
  symbols_note <- NULL
  preserve_symbols <- if (!is.null(additional_data$preserve_symbols)) additional_data$preserve_symbols else FALSE
  
  # Process symbols (skip for summarization requests)
  # This ensures context items are included in the symbols note for both providers
  if (!is_summarization_request) {
    if ((provider == "openai" || provider == "anthropic") && !preserve_symbols) {
      symbols_note <- .rs.check_message_for_symbols(conversation)
    } else if ((provider == "openai" || provider == "anthropic") && preserve_symbols) {
      symbols_note <- .rs.check_message_for_symbols(conversation)
    }
  }
  
  config <- .rs.get_backend_config(conversation, additional_data)
  
  user_env_info <- .rs.gather_user_environment_info()
  
  attachment_data <- .rs.prepare_attachment_data()
  
  # Sort conversation by ID to ensure chronological order before sending to backend
  # This ensures all conversation data sent to rao-backend is properly ordered by message_id
  sorted_conversation <- conversation[order(sapply(conversation, function(x) x$id %||% 0))]
  
  # Get client version for backend tracking
  client_version <- tryCatch({
    version_info <- .Call("rs_rstudioLongVersion")
    if (is.null(version_info) || version_info == "") "unknown" else version_info
  }, error = function(e) "unknown")
  
  request_data <- list(
    request_type = request_type,
    conversation = sorted_conversation,
    provider = provider,
    model = model,
    request_id = request_id,
    client_version = client_version,
    symbols_note = symbols_note,
    last_function_was_edit_file = is_after_edit_file,
    user_os_version = user_env_info$user_os_version,
    user_workspace_path = user_env_info$user_workspace_path,
    user_shell = user_env_info$user_shell
  )
  
  if (!is.null(attachment_data) && !is.null(attachment_data$has_attachments) && attachment_data$has_attachments) {
    request_data$attachments = attachment_data$attachments
    request_data$vector_store_id = attachment_data$vector_store_id
    request_data$has_attachments = TRUE
  }

  # For reasoning models, add previous_response_id if available
  # BUT ONLY if this is NOT the first message in a conversation
  if (!is.null(provider) && provider == "openai" && !is.null(model) && 
      (model == "o4-mini" || model == "o3-mini" || model == "o1" || model == "o3")) {
    
    # Check if this is the first message in the conversation
    is_first_message <- .rs.is_first_message_in_conversation(sorted_conversation)
    
    if (is_first_message) {
      # For first messages, don't include previous_response_id
    } else {
              # Get the response_id from the highest ID message in the conversation log
        lastResponseId <- .rs.get_response_id_from_highest_message()
        if (!is.null(lastResponseId)) {
          request_data$previous_response_id <- lastResponseId
      }
    }
  }
  
  if (!is.null(additional_data)) {
    additional_data$preserve_symbols <- NULL
    if (length(additional_data) > 0) {
      request_data <- c(request_data, additional_data)
    }
  }
  
  request_data$auth <- .rs.generate_backend_auth(provider)
  
  cancel_dir <- NULL
  if (!is.null(request_id)) {
    temp_dir <- .rs.get_temp_dir()
    cancel_dir <- file.path(temp_dir, "ai_cancel")
    dir.create(cancel_dir, showWarnings = FALSE, recursive = TRUE)
  }
  
  tryCatch({
    check_cancelled <- function() {
      if (.rs.get_conversation_var("ai_cancelled")) {
        return(TRUE)
      }
      
      if (!is.null(request_id) && !is.null(cancel_dir)) {
        cancel_file <- file.path(cancel_dir, paste0("cancel_", request_id))
        if (file.exists(cancel_file)) {
          .rs.set_conversation_var("ai_cancelled", TRUE)
          return(TRUE)
        }
      }
      
      return(FALSE)
    }
    
    if (check_cancelled()) {
      if (!is_conversation_name_request) {
        .rs.enqueClientEvent("update_thinking_message", list(message = "", hide_cancel = TRUE))
      }
      return(NULL)
    }
    
    # Check backend connectivity before making API requests
    # Skip health check for conversation name and summarization requests to avoid delays
    if (!is_conversation_name_request && !is_summarization_request) {
      backend_healthy <- tryCatch({
        .rs.check_backend_health()
      }, error = function(e) FALSE)
      
      if (!backend_healthy) {
        stop("Cannot connect to backend server. Please check your connectivity. If the problem persists, please open a thread at https://community.lotas.ai/.")
      }
    }
    
    async_info <- .rs.run_api_request_async(request_data = request_data, request_id = request_id)
    response <- .rs.poll_api_request_result(async_info)

    if (check_cancelled()) {
      if (!is_conversation_name_request) {
        .rs.enqueClientEvent("update_thinking_message", list(message = "", hide_cancel = TRUE))
      }
      return(NULL)
    }
    
    if (!is.null(response$cancelled) && response$cancelled) {
      if (!is_conversation_name_request && !is_summarization_request) {
        .rs.enqueClientEvent("update_thinking_message", list(message = "", hide_cancel = TRUE))
      }
      
      # If cancellation occurred but we have accumulated content, return it for preservation
      if (!is.null(response$accumulated_response) && nchar(response$accumulated_response) > 0) {
         # Transform the accumulated_response into the expected format for downstream processing
         response$response <- response$accumulated_response
         response$partial_content <- TRUE
         return(response)
      }
      return(NULL)
    }
    
    if (!is.null(response$error)) {
      # Handle structured error responses from backend
      if (is.character(response$error)) {
        # Legacy string error - pass through directly
        stop(response$error)
      } else {
        # Structured error response
        error_message <- if (!is.null(response$error$user_message)) {
          response$error$user_message
        } else if (!is.null(response$error$error_message)) {
          response$error$error_message
        } else {
          "Unknown error from backend"
        }
        stop(error_message)
      }
    }

    return(response)
    
  }, error = function(e) {
    if (!is_conversation_name_request && !is_summarization_request) {
      .rs.enqueClientEvent("update_thinking_message", list(message = "", hide_cancel = TRUE))
    }
    
    if (check_cancelled()) {
      return(NULL)
    }
    
    # Check for specific error types and provide appropriate messages
    error_msg <- e$message
    
    # Authentication errors (only specific authentication failures)
    if (grepl("Authentication failed|API key|No user found|401|403|Unauthorized|Forbidden", error_msg, ignore.case = TRUE)) {
      stop("Authentication failed. Please check your Rao API key in the API key management pane (key icon in the top right). If the problem persists, please open a thread at https://community.lotas.ai/.")
    }
    
    # Connection errors
    if (grepl("Connection refused|Cannot connect|timeout|DNS|network|Could not resolve host|Backend.*unreachable|Backend request timed out|Backend connection error", error_msg, ignore.case = TRUE)) {
      stop("Cannot connect to backend server. Please check that the backend is running at ", config$url, " If the problem persists, please open a thread at https://community.lotas.ai/.")
    }
    
    # Rate limiting errors
    if (grepl("rate limit|too many requests|429", error_msg, ignore.case = TRUE)) {
      stop("Rate limit exceeded. Please wait a moment before trying again.")
    }
    
    # Server errors
    if (grepl("500|502|503|504|Internal Server Error|Bad Gateway|Service Unavailable|Gateway Timeout", error_msg, ignore.case = TRUE)) {
      stop("Backend server error. Please try again in a few moments. If the problem persists, please open a thread at https://community.lotas.ai/.")
    }
    
    # Pass through all other error messages directly - structured errors will have been handled above
    stop(e$message)
  })
})

.rs.addFunction("is_first_message_in_conversation", function(conversation) {
  if (is.null(conversation) || length(conversation) == 0) {
    return(TRUE)  # Empty conversation = first message
  }
  
  user_message_count <- 0
  has_assistant_response <- FALSE
  
  for (entry in conversation) {
    if (!is.null(entry$role)) {
      role <- entry$role
      
      # Count actual user messages (exclude procedural messages)
      if (role == "user" && !is.null(entry$content)) {
        # Skip procedural messages
        is_procedural <- !is.null(entry$procedural) && entry$procedural == TRUE
        if (!is_procedural) {
          user_message_count <- user_message_count + 1
        }
      }
      
      # Check for any assistant responses
      if (role == "assistant") {
        has_assistant_response <- TRUE
      }
    }
  }
  
  # First message if: only 1 user message AND no assistant responses
  is_first_message <- user_message_count <= 1 && !has_assistant_response
  
  return(is_first_message)
})

.rs.addFunction("get_response_id_from_highest_message", function() {
  # Get the response_id from the message with the highest ID that actually has a response_id
  tryCatch({
    conversation_log <- .rs.read_conversation_log()
    
    if (length(conversation_log) == 0) {
      return(NULL)
    }
    
    # Find the message with the highest ID that has a response_id
    highest_id_with_response <- -1
    response_id_to_return <- NULL
    messages_with_response_id <- 0
    
    for (entry in conversation_log) {
      if (!is.null(entry$id) && is.numeric(entry$id) && 
          !is.null(entry$response_id)) {
        messages_with_response_id <- messages_with_response_id + 1
        
        if (entry$id > highest_id_with_response) {
          highest_id_with_response <- entry$id
          response_id_to_return <- entry$response_id
        }
      }
    }
    
    return(response_id_to_return)
  }, error = function(e) {
    cat("Error getting response_id from highest message:", e$message, "\n")
    return(NULL)
  })
})

.rs.addFunction("backend_ai_api_call", function(conversation, provider = NULL, model = NULL, preserve_symbols = FALSE, request_id) {
     if (.rs.get_conversation_var("ai_cancelled")) {
      return(NULL)
   }
   
   if (is.null(model)) {
      model <- .rs.get_selected_model()
   }
  
  if (is.null(provider)) {
    provider <- .rs.get_active_provider()
  }
  
  if (!is.null(request_id)) {
    .rs.set_conversation_var("active_api_request_id", request_id)
  }

  # Check for persistent background summarization (non-blocking check)
  .rs.check_persistent_background_summarization()
  
  # Prepare conversation with existing summaries
  conversation_with_summary <- .rs.prepare_conversation_with_summaries(conversation)
  conversation_to_send <- conversation_with_summary$conversation
  
  additional_data <- list(
    preserve_symbols = preserve_symbols,
    function_call_depth = .rs.get_conversation_var("function_call_depth", 0)
  )
  
  # Add summary if available
  if (!is.null(conversation_with_summary$summary)) {
    additional_data$previous_summary <- conversation_with_summary$summary
  }
  
  # Use the new streaming send_backend_query (which now uses streaming /ai/query)
  response <- .rs.send_backend_query(
    request_type = "ai_api_call",
    conversation = conversation_to_send,
    provider = provider,
    model = model,
    request_id = request_id,
    additional_data = additional_data
  )
  
  .rs.set_conversation_var("active_api_request_id", NULL)
  
  if (is.null(response)) {
    # Check for persistent background summarization (non-blocking)
    .rs.check_persistent_background_summarization()
    return(NULL)
  }
  
  if (!is.null(response$response)) {
    processed_response <- .rs.process_backend_response(response$response, conversation_to_send, provider, model)
    # Return the full response structure with the processed response
    response$response <- processed_response
    return(response)
  }
  
  if (!is.null(response$end_turn) && isTRUE(response$end_turn)) {
    result <- list(
      end_turn = TRUE
    )
    return(result)
  }
  
  return(response)
})

.rs.addFunction("backend_generate_conversation_name", function(conversation, provider = NULL, model = NULL) {
  if (is.null(provider)) {
    provider <- .rs.get_active_provider()
  }
  
  # Generate a request_id for conversation naming since the background process needs it
  request_id <- paste0("conv_name_", as.integer(as.numeric(Sys.time())), "_", sample(10000:99999, 1))
  
  response <- .rs.send_backend_query(
    request_type = "generate_conversation_name",
    conversation = conversation,
    provider = provider,
    model = model,
    request_id = request_id,
    additional_data = NULL
  )
  
  if (is.null(response)) {
    return(NULL)
  }
  
  if (!is.null(response$error)) {
    stop("Backend error in conversation name generation: ", response$error)
  }
  if (!is.null(response$conversation_name)) {
    return(response$conversation_name)
  } else if (!is.null(response$response)) {
    return(response$response)
  } else {
    stop("Backend error: No conversation_name or response field in conversation name generation response")
  }
})

.rs.addFunction("check_backend_health", function() {
  config <- .rs.get_backend_config()
  
  tryCatch({
    request <- httr2::request(config$url)
    request <- httr2::req_url_path(request, "/actuator/health")
    request <- httr2::req_method(request, "GET")
    request <- httr2::req_timeout(request, 5)
    response <- httr2::req_perform(request)
    
    status <- httr2::resp_status(response)
    if (status == 200) {
      body <- httr2::resp_body_json(response)
      return(!is.null(body$status) && body$status == "UP")
    }
    return(FALSE)
    
  }, error = function(e) {
    return(FALSE)
  })
})

.rs.addFunction("cancel_backend_request", function(request_id) {
  if (is.null(request_id) || request_id == "") {
    stop("request_id is required for cancellation; request_id is missing")
  }
  
  config <- .rs.get_backend_config()
  
  tryCatch({
    request <- httr2::request(config$url)
    request <- httr2::req_url_path(request, "/ai/cancel")
    request <- httr2::req_url_query(request, requestId = trimws(request_id))
    request <- httr2::req_method(request, "POST")
    request <- httr2::req_timeout(request, 5)
    
    # Add authentication headers if needed
    api_key <- .rs.get_api_key("rao")
    if (!is.null(api_key)) {
      request <- httr2::req_headers(request, "Authorization" = paste("Bearer", api_key))
    }
    
    response <- httr2::req_perform(request)
    
    status_code <- httr2::resp_status(response)    
    response_data <- httr2::resp_body_json(response)
    # Check for success based on the documented response format
    if (status_code == 200 && !is.null(response_data$message)) {
      return(TRUE)
    } else {
      return(FALSE)
    }
    
  }, error = function(e) {
    # No warning here because this can indicate the request hadn't started yet
    return(FALSE)
  })
})



# Function to cleanup conversation attachments (calls backend to delete files from OpenAI/Anthropic APIs)
.rs.addFunction("cleanup_conversation_attachments", function(conversation_id) {
   tryCatch({
      # Get the conversation's attachments file path
      base_ai_dir <- .rs.get_ai_base_dir()
      conversations_dir <- file.path(base_ai_dir, "conversations")
      conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversation_id))
      csv_path <- file.path(conversation_dir, "attachments.csv")
      
      # If no attachments file exists, nothing to cleanup
      if (!file.exists(csv_path)) {
         return(TRUE)
      }
      
      # Read the attachments CSV to get file IDs
      attachments <- tryCatch({
         read.csv(csv_path, stringsAsFactors = FALSE)
      }, error = function(e) {
         return(NULL)
      })
      
      # If no attachments or failed to read, return success
      if (is.null(attachments) || nrow(attachments) == 0) {
         return(TRUE)
      }
      
      # Separate file IDs by provider type using vector_store_id as indicator
      openai_file_ids <- c()
      anthropic_file_ids <- c()
      
      for (i in 1:nrow(attachments)) {
         file_id <- attachments$file_id[i]
         vector_store_id <- attachments$vector_store_id[i]
         
         if (!is.null(file_id) && !is.na(file_id) && nchar(trimws(file_id)) > 0) {
            # Use vector_store_id to distinguish: Anthropic files have empty vector_store_id
            if (is.null(vector_store_id) || is.na(vector_store_id) || nchar(trimws(vector_store_id)) == 0) {
               # Empty vector_store_id indicates Anthropic file
               anthropic_file_ids <- c(anthropic_file_ids, file_id)
            } else {
               # Non-empty vector_store_id indicates OpenAI file
               openai_file_ids <- c(openai_file_ids, file_id)
            }
         }
      }
      
      # Build query parameters for the DELETE request
      config <- .rs.get_backend_config()
      url <- paste0(config$url, "/attachments/conversation/", conversation_id, "/attachments")
      
      # Create query parameters
      query_params <- list()
      if (length(openai_file_ids) > 0) {
         query_params$file_ids <- openai_file_ids
      }
      if (length(anthropic_file_ids) > 0) {
         query_params$anthropic_file_ids <- anthropic_file_ids
      }
      
      # Get Rao API key for backend authentication
      api_key <- .rs.get_api_key("rao")
      if (is.null(api_key)) {
         return(FALSE)
      }
      
      # Add API key to query parameters for authentication
      query_params$api_key <- api_key
      
      # Make the DELETE request with file IDs and API key
      request <- httr2::request(url)
      if (length(query_params) > 0) {
         request <- httr2::req_url_query(request, !!!query_params)
      }
      
      response <- httr2::req_perform(
         httr2::req_method(request, "DELETE")
      )
      
      if (httr2::resp_status(response) == 200) {
         return(TRUE)
      } else {
         return(FALSE)
      }
      
   }, error = function(e) {
      return(FALSE)
   })
})

# Conversation Summarization Functions

.rs.addFunction("get_summaries_file_path", function() {
  paths <- .rs.get_ai_file_paths()
  return(file.path(dirname(paths$conversation_log_path), "summaries.json"))
})

.rs.addFunction("load_conversation_summaries", function() {
  summaries_path <- .rs.get_summaries_file_path()
  
  if (!file.exists(summaries_path)) {
    return(list(summaries = list()))
  }
  
  tryCatch({
    jsonlite::fromJSON(summaries_path, simplifyVector = FALSE)
  }, error = function(e) {
    list(summaries = list())
  })
})

.rs.addFunction("save_conversation_summary", function(query_number, summary_text) {
  
  summaries_path <- .rs.get_summaries_file_path()
  
  all_summaries <- .rs.load_conversation_summaries()
  
  # Add new summary as text
  summary_entry <- list(
    query_number = query_number,
    timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
    summary_text = summary_text
  )
  
  all_summaries$summaries[[as.character(query_number)]] <- summary_entry
  
  # Save back to file
  tryCatch({
    writeLines(jsonlite::toJSON(all_summaries, auto_unbox = TRUE, pretty = TRUE), summaries_path)
    
    # Verify file was written
    if (file.exists(summaries_path)) {
      file_size <- file.size(summaries_path)
    } else {
      cat("DEBUG: save_conversation_summary - ERROR: File does not exist after writing\n")
    }
    
    return(TRUE)
  }, error = function(e) {
    cat("DEBUG: save_conversation_summary - ERROR writing file:", e$message, "\n")
    return(FALSE)
  })
})

.rs.addFunction("count_original_queries", function(conversation_log) {
  # Count messages with role="user" that have original_query=TRUE
  count <- 0
  for (msg in conversation_log) {
    if (!is.null(msg$role) && msg$role == "user" && 
        !is.null(msg$original_query) && msg$original_query == TRUE) {
      count <- count + 1
    }
  }
  return(count)
})

.rs.addFunction("should_trigger_summarization", function(conversation_log) {
  # Trigger summarization if we have 2+ original queries
  # Query N triggers summarization of query N-1, so we start with query 2
  return(.rs.count_original_queries(conversation_log) >= 2)
})

.rs.addFunction("get_highest_summarized_query", function() {
  summaries <- .rs.load_conversation_summaries()
  if (length(summaries$summaries) == 0) {
    return(0)
  }
  
  query_numbers <- as.numeric(names(summaries$summaries))
  return(max(query_numbers))
})

.rs.addFunction("start_background_summarization", function(conversation_log, target_query_number) {
  # Generate request ID for summarization
  request_id <- paste0("summary_", as.integer(as.numeric(Sys.time())), "_", sample(10000:99999, 1))
    
  # Extract the conversation portion for the target query
  conversation_portion <- .rs.extract_query_conversation_portion(conversation_log, target_query_number)
  
  # Get the previous summary S_{target_query_number - 1} if it exists, otherwise use most recent available
  previous_summary <- NULL
  if (target_query_number > 1) {
    summaries <- .rs.load_conversation_summaries()
    previous_summary_key <- as.character(target_query_number - 1)
    
    # First try to get the exact previous summary
    if (!is.null(summaries$summaries[[previous_summary_key]])) {
      previous_summary <- summaries$summaries[[previous_summary_key]]
    } else if (length(summaries$summaries) > 0) {
      # Fallback: use the most recent available summary (highest query number less than target)
      available_query_numbers <- as.numeric(names(summaries$summaries))
      valid_summaries <- available_query_numbers[available_query_numbers < target_query_number]
      
      if (length(valid_summaries) > 0) {
        most_recent_query <- max(valid_summaries)
        previous_summary <- summaries$summaries[[as.character(most_recent_query)]]
        message("DEBUG: Using fallback summary for query ", most_recent_query, " instead of ", target_query_number - 1, "\n")
      }
    }
  }
  
  # Start async summarization request
  tryCatch({
    
    async_info <- .rs.run_api_request_async(
      request_data = list(
        request_type = "summarize_conversation",
        conversation = conversation_portion,  # Only the specific query portion
        provider = "openai",
        model = "gpt-4.1-mini", # Backend will override this anyway
        request_id = request_id,
        auth = .rs.generate_backend_auth("openai"),
        target_query_number = target_query_number,
        previous_summary = previous_summary  # Include previous summary
      ),
      request_id = request_id,
      is_background = TRUE  # Use different stream file for background requests
    )
    
    # Save state persistently instead of using conversation variables
    .rs.save_background_summarization_state(request_id, target_query_number, async_info)
    return(TRUE)
    
  }, error = function(e) {
    cat("DEBUG: Failed to start background summarization:", e$message, "\n")
    return(FALSE)
  })
})

.rs.addFunction("save_background_summarization_state", function(request_id, target_query, async_info) {
  state_path <- .rs.get_background_summarization_state_path()
  
  # Extract essential information from async_info
  process_id <- NULL
  if (!is.null(async_info$bg_process)) {
    tryCatch({
      process_id <- async_info$bg_process$get_pid()
    }, error = function(e) {
      cat("DEBUG: Could not get process ID:", e$message, "\n")
    })
  }
  
  state <- list(
    request_id = request_id,
    target_query = target_query,
    stream_file = async_info$stream_file,
    process_id = process_id,
    timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S")
  )
  
  tryCatch({
    writeLines(jsonlite::toJSON(state, auto_unbox = TRUE, pretty = TRUE), state_path)
    return(TRUE)
  }, error = function(e) {
    cat("DEBUG: Failed to save background summarization state:", e$message, "\n")
    return(FALSE)
  })
})

.rs.addFunction("load_background_summarization_state", function() {
  state_path <- .rs.get_background_summarization_state_path()
  
  if (!file.exists(state_path)) {
    return(NULL)
  }
  
  tryCatch({
    jsonlite::fromJSON(state_path, simplifyVector = FALSE)
  }, error = function(e) {
    cat("DEBUG: Failed to load background summarization state:", e$message, "\n")
    return(NULL)
  })
})

.rs.addFunction("clear_background_summarization_state", function() {
  state_path <- .rs.get_background_summarization_state_path()
  
  if (file.exists(state_path)) {
    tryCatch({
      file.remove(state_path)
      return(TRUE)
    }, error = function(e) {
      cat("DEBUG: Failed to clear background summarization state:", e$message, "\n")
      return(FALSE)
    })
  }
  return(TRUE)
})

.rs.addFunction("check_persistent_background_summarization", function() {
  # Check if there's a persistent background summarization in progress
  state <- .rs.load_background_summarization_state()
  
  if (is.null(state)) {
    return(FALSE)
  }
  
  # Check if summarization is complete by checking the stream file directly
  tryCatch({
    stream_file <- state$stream_file
    
    if (is.null(stream_file)) {
      .rs.clear_background_summarization_state()
      return(FALSE)
    }
    
    if (!file.exists(stream_file)) {
      .rs.clear_background_summarization_state()
      return(FALSE)
    }
        
    # Read the stream file to check for completion
    stream_content <- readLines(stream_file, warn = FALSE)
    
    # Look for the actual completion marker: "COMPLETE" line
    has_completion <- any(stream_content == "COMPLETE")
    has_error <- any(grepl("^BG ERROR:", stream_content))
    
    if (has_completion || has_error) {
      if (has_error) {
        cat("DEBUG: Background summarization failed with error for query", state$target_query, "\n")
      }
      
      # Extract the final response from EVENT: lines
      response_text <- ""
      event_count <- 0
      for (line in stream_content) {
        if (startsWith(line, "EVENT:")) {
          event_count <- event_count + 1
          json_data <- substring(line, 7)
          tryCatch({
            event_data <- jsonlite::fromJSON(json_data, simplifyVector = FALSE)
            if (!is.null(event_data$delta)) {
              response_text <- paste0(response_text, event_data$delta)
            }
          }, error = function(e) {
            cat("DEBUG: Error parsing EVENT line:", e$message, "\n")
          })
        }
      }
      
      # Clean up
      .rs.clear_background_summarization_state()
      if (file.exists(stream_file)) {
        file.remove(stream_file)
      }
      
      # Save summary if we got content (and no error)
      if (!has_error && nchar(response_text) > 0) {
        .rs.save_conversation_summary(state$target_query, response_text)
        return(TRUE)
      } else if (has_error) {
        cat("DEBUG: Background summarization had error, not saving summary\n")
      }
    }
    
    return(FALSE)
    
  }, error = function(e) {
    cat("DEBUG: Error checking background summarization:", e$message, "\n")
    .rs.clear_background_summarization_state()
    return(FALSE)
  })
})

.rs.addFunction("wait_for_persistent_background_summarization", function() {
  # Wait for persistent background summarization if needed (blocking)
  state <- .rs.load_background_summarization_state()
  
  if (is.null(state)) {
    return(FALSE)
  }
    
  # Wait for summarization to complete by polling the stream file
  tryCatch({
    stream_file <- state$stream_file
    
    if (is.null(stream_file)) {
      cat("DEBUG: No stream file found in state\n")
      .rs.clear_background_summarization_state()
      return(FALSE)
    }
    
    # Poll for completion with timeout
    max_wait_time <- 10  # 10 seconds timeout
    start_time <- Sys.time()
    
    while (difftime(Sys.time(), start_time, units = "secs") < max_wait_time) {
      if (file.exists(stream_file)) {
        stream_content <- readLines(stream_file, warn = FALSE)
        has_completion <- any(stream_content == "COMPLETE")
        has_error <- any(grepl("^BG ERROR:", stream_content))
        
        if (has_completion || has_error) {
          # Extract response from EVENT: lines
          response_text <- ""
          for (line in stream_content) {
            if (startsWith(line, "EVENT:")) {
              json_data <- substring(line, 7)
              tryCatch({
                event_data <- jsonlite::fromJSON(json_data, simplifyVector = FALSE)
                if (!is.null(event_data$delta)) {
                  response_text <- paste0(response_text, event_data$delta)
                }
              }, error = function(e) {
                # Skip malformed JSON lines
              })
            }
          }
          
          # Clean up
          .rs.clear_background_summarization_state()
          if (file.exists(stream_file)) {
            file.remove(stream_file)
          }
          
          # Save summary if successful (and no error)
          if (!has_error && nchar(response_text) > 0) {
            .rs.save_conversation_summary(state$target_query, response_text)
            return(TRUE)
          }
          
          break
        }
      }
      
      # Wait a bit before checking again
      Sys.sleep(0.5)
    }
    
    .rs.clear_background_summarization_state()
    return(FALSE)
    
  }, error = function(e) {
    cat("DEBUG: Error waiting for background summarization:", e$message, "\n")
    .rs.clear_background_summarization_state()
    return(FALSE)
  })
})

.rs.addFunction("prepare_conversation_with_summaries", function(conversation) {
  summaries <- .rs.load_conversation_summaries()
  
  # Find all original queries in ID order
  original_queries <- list()
  for (i in seq_along(conversation)) {
    msg <- conversation[[i]]
    if (!is.null(msg$role) && msg$role == "user" && 
        !is.null(msg$original_query) && msg$original_query == TRUE) {
      original_queries[[length(original_queries) + 1]] <- list(
        index = i,
        id = msg$id,
        message = msg
      )
    }
  }
  
  # Sort original queries by ID to ensure proper chronological order
  if (length(original_queries) > 0) {
    original_queries <- original_queries[order(sapply(original_queries, function(x) x$id))]
  }
  
  current_query_count <- length(original_queries)
  
  if (current_query_count == 0) {
    # No original queries found, return original conversation with no summary
    return(list(conversation = conversation, summary = NULL))
  }
  
  if (current_query_count == 1) {
    # Only one original query, keep everything from that query onward
    latest_original_query <- original_queries[[1]]
    latest_original_query_index <- latest_original_query$index
    recent_conversation <- conversation[latest_original_query_index:length(conversation)]    
    return(list(conversation = recent_conversation, summary = NULL))
  }
  
  # For 2+ original queries, keep the previous query (N-1) and current query (N) plus everything in between
  previous_original_query <- original_queries[[current_query_count - 1]]  # N-1
  current_original_query <- original_queries[[current_query_count]]       # N
  
  # Start from the previous original query (N-1)
  start_index <- previous_original_query$index
  recent_conversation <- conversation[start_index:length(conversation)]
  
  # Get the summary S_{N-2} if it exists, otherwise use most recent available (summary up to query N-2)
  previous_summary <- NULL
  if (length(summaries$summaries) > 0 && current_query_count > 2) {
    # Look for summary S_{N-2} (the summary of query N-2)
    target_summary_query <- current_query_count - 2
    
    # First try to get the exact N-2 summary
    if (!is.null(summaries$summaries[[as.character(target_summary_query)]])) {
      summary_entry <- summaries$summaries[[as.character(target_summary_query)]]
      previous_summary <- list(
        query_number = target_summary_query,
        timestamp = summary_entry$timestamp,
        summary_text = summary_entry$summary_text
      )
    } else {
      # Fallback: use the most recent available summary (highest query number less than N-1)
      available_query_numbers <- as.numeric(names(summaries$summaries))
      # Only use summaries that are older than the previous query (N-1)
      max_allowed_query <- current_query_count - 2
      valid_summaries <- available_query_numbers[available_query_numbers <= max_allowed_query]
      
      if (length(valid_summaries) > 0) {
        most_recent_query <- max(valid_summaries)
        summary_entry <- summaries$summaries[[as.character(most_recent_query)]]
        previous_summary <- list(
          query_number = most_recent_query,
          timestamp = summary_entry$timestamp,
          summary_text = summary_entry$summary_text
        )
        message("DEBUG: Using fallback summary for query ", most_recent_query, " instead of ", target_summary_query, " for API call\n")
      }
    }
  }
  
  # Return conversation and summary separately
  return(list(
    conversation = recent_conversation,
    summary = previous_summary
  ))
})

.rs.addFunction("get_background_summarization_state_path", function() {
  paths <- .rs.get_ai_file_paths()
  return(file.path(dirname(paths$conversation_log_path), "background_summarization.json"))
})

.rs.addFunction("extract_query_conversation_portion", function(conversation_log, target_query_number) {
  # Find all original queries in ID order
  original_queries <- list()
  for (i in seq_along(conversation_log)) {
    msg <- conversation_log[[i]]
    if (!is.null(msg$role) && msg$role == "user" && 
        !is.null(msg$original_query) && msg$original_query == TRUE) {
      original_queries[[length(original_queries) + 1]] <- list(
        index = i,
        id = msg$id,
        query_number = length(original_queries) + 1
      )
    }
  }
  
  # Sort original queries by ID to ensure proper chronological order
  if (length(original_queries) > 0) {
    original_queries <- original_queries[order(sapply(original_queries, function(x) x$id))]
  }
  
  # Find the target query
  target_query_info <- NULL
  for (query_info in original_queries) {
    if (query_info$query_number == target_query_number) {
      target_query_info <- query_info
      break
    }
  }
  
  if (is.null(target_query_info)) {
    cat("DEBUG: Could not find target query", target_query_number, "\n")
    return(list())
  }
  
  # Find the start and end indices for this query's conversation
  start_index <- target_query_info$index
  
  # Find the end index (before the next original query, or end of conversation)
  end_index <- length(conversation_log)
  for (query_info in original_queries) {
    if (query_info$query_number == target_query_number + 1) {
      end_index <- query_info$index - 1
      break
    }
  }
  
  # Extract the conversation portion
  if (start_index <= end_index) {
    return(conversation_log[start_index:end_index])
  } else {
    return(list())
  }
})
