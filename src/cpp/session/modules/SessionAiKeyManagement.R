# SessionAiKeyManagement.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

.rs.addFunction("get_api_key_management_html", function() {
  # Reset backend environment detection when navigating to API key management
  .rs.reset_backend_environment()
  
  rao_key <- .rs.get_api_key("rao")
  has_rao_key <- !is.null(rao_key)

  subscription_data <- NULL
  subscription_error <- NULL
  if (has_rao_key) {
    tryCatch({
      backend_config <- .rs.get_backend_config()
      
      response <- httr2::resp_body_json(
        httr2::req_perform(
          httr2::req_headers(
            httr2::request(paste0(backend_config$url, "/api/user/subscription-status")),
            "Authorization" = paste("Bearer", rao_key)
          )
        )
      )
      subscription_data <- response
    }, error = function(e) {
      subscription_error <- paste("Error retrieving subscription status:", e$message)
    })
  }
  
  # Helper function to get status badge class
  get_status_badge_class <- function(status) {
    switch(status,
      "trial" = "status-trial",
      "active" = "status-active", 
      "past_due" = "status-past-due",
      "payment_action_required" = "status-payment-action-required",
      "cancelled" = "status-cancelled",
      "expired" = "status-expired",
      "status-unknown"
    )
  }
  
  # Helper function to format status text
  format_status_text <- function(status) {
    switch(status,
      "trial" = "Trial",
      "active" = "Active",
      "past_due" = "Past Due",
      "payment_action_required" = "Payment Required",
      "cancelled" = "Cancelled",
      "expired" = "Expired",
      paste0(toupper(substr(status, 1, 1)), substr(status, 2, nchar(status)))  # Default: capitalize first letter
    )
  }
  
  # Helper function to get status message
  get_status_message <- function(data) {
    if (is.null(data)) return("Unable to retrieve status information")
    
    status <- data$subscription_status
    if (status == "trial") {
      remaining <- if(!is.null(data$trial_queries_remaining)) data$trial_queries_remaining else 0
      trial_end <- if(!is.null(data$trial_ends_at)) {
        date_obj <- as.POSIXct(data$trial_ends_at, format="%Y-%m-%dT%H:%M:%S")
        format(date_obj, "%B %d")
      } else NULL
      base_msg <- paste0("Free trial: ", remaining, " queries remaining")
      if (!is.null(trial_end)) {
        return(paste0(base_msg, " (Ends on ", trial_end, ")"))
      } else {
        return(base_msg)
      }
    } else if (status == "active") {
      remaining <- if(!is.null(data$monthly_queries_remaining)) data$monthly_queries_remaining else 0
      next_billing <- if(!is.null(data$next_billing_date)) {
        date_obj <- as.POSIXct(data$next_billing_date, format="%Y-%m-%dT%H:%M:%S")
        format(date_obj, "%B %d")
      } else NULL
      base_msg <- paste0("Active subscription: ", remaining, " queries remaining this month")
      if (!is.null(next_billing)) {
        return(paste0(base_msg, " (Resets on ", next_billing, ")"))
      } else {
        return(base_msg)
      }
    } else if (status == "past_due") {
      return("Payment failed - please update your payment method to continue service")
    } else if (status == "payment_action_required") {
      return("A manual payment is required. Please update your payment method to continue.")
    } else if (status == "cancelled") {
      remaining <- if(!is.null(data$monthly_queries_remaining)) data$monthly_queries_remaining else 0
      next_billing <- if(!is.null(data$current_period_end)) {
        date_obj <- as.POSIXct(data$current_period_end, format="%Y-%m-%dT%H:%M:%S")
        format(date_obj, "%B %d")
      } else NULL
      base_msg <- paste0("Cancelled subscription: ", remaining, " queries remaining this month")
      if (!is.null(next_billing)) {
        return(paste0(base_msg, " (Ends on ", next_billing, ")"))
      } else {
        return(base_msg)
      }
    } else if (status == "expired") {
      return("Subscription expired - please renew to continue")
    } else {
      return("Unknown subscription status")
    }
  }
  
  # Helper function to get progress bar info
  get_progress_info <- function(data) {
    if (is.null(data)) return(NULL)
    
    status <- data$subscription_status
    if (status == "trial") {
      remaining <- if(!is.null(data$trial_queries_remaining)) data$trial_queries_remaining else 0
      used <- 50 - remaining
      return(list(
        label = "Trial Usage",
        used = used,
        total = 50,
        class = "progress-trial"
      ))
    } else if (status == "active") {
      remaining <- if(!is.null(data$monthly_queries_remaining)) data$monthly_queries_remaining else 0
      used <- 200 - remaining
      return(list(
        label = "Monthly Usage", 
        used = used,
        total = 200,
        class = "progress-active"
      ))
    } else if (status == "cancelled") {
      remaining <- if(!is.null(data$monthly_queries_remaining)) data$monthly_queries_remaining else 0
      used <- 200 - remaining
      return(list(
        label = "Monthly Usage", 
        used = used,
        total = 200,
        class = "progress-cancelled"
      ))
    }
    return(NULL)
  }
  
  html <- paste0(
    "<html><head><title>API Key Management</title>",
    "<style>",
    "body { font-family: sans-serif; margin: 10px; }",
    ".container { max-width: 800px; margin: 0 auto; }",
    "h1 { color: #4D8DC9; margin-bottom: 10px; }",
    ".key-section { background-color: #f5f5f5; border-radius: 5px; padding: 10px; margin-bottom: 10px; }",
    ".key-input { width: 100%; padding: 8px; margin: 8px 0; box-sizing: border-box; font-family: monospace; }",
    ".button { color: white; border: none; padding: 2px 6px; text-align: center; ",
    "text-decoration: none; display: inline-block; font-size: 12px; margin: 4px 2px; cursor: pointer; border-radius: 3px; }",
    ".save-button { background-color: #e6ffe6; color: #006400; border: 1px solid #006400; }",
    ".delete-button { background-color: #ffe6e6; color: #8b0000; border: 1px solid #8b0000; }",
    ".model-select { display: block; width: 100%; padding: 8px; margin: 8px 0; border: 1px solid #ccc; border-radius: 3px; background-color: white; }",
    ".model-row { margin-top: 10px; }",
    ".model-label { display: block; margin-bottom: 5px; font-weight: bold; }",
    ".instruction-text { color: #666; font-size: 14px; margin-top: 15px; font-style: italic; }",
    ".subscription-section { background-color: #f9f9f9; border-radius: 5px; padding: 15px; margin-bottom: 15px; }",
    ".section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; }",
    ".status-badge { display: inline-flex; align-items: center; padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 500; }",
    ".status-trial { background-color: #dbeafe; color: #1e40af; }",
    ".status-active { background-color: #dcfce7; color: #166534; }",
    ".status-past-due { background-color: #fed7aa; color: #c2410c; }",
    ".status-payment-action-required { background-color: #fef3c7; color: #d97706; }",
    ".status-cancelled { background-color: #fecaca; color: #dc2626; }",
    ".status-expired { background-color: #fecaca; color: #dc2626; }",
    ".status-unknown { background-color: #f3f4f6; color: #374151; }",
    ".status-message { color: #6b7280; margin: 15px 0 0 0; line-height: 1.4; font-size: 14px; }",
    ".progress-container { margin: 15px 0; }",
    ".progress-label { display: flex; justify-content: space-between; font-size: 12px; color: #6b7280; margin-bottom: 4px; }",
    ".progress-bar { width: 100%; height: 8px; background-color: #e5e7eb; border-radius: 4px; overflow: hidden; }",
    ".progress-fill { height: 100%; border-radius: 4px; transition: width 0.3s ease; }",
    ".progress-trial .progress-fill { background-color: #3b82f6; }",
    ".progress-active .progress-fill { background-color: #16a34a; }",
    ".progress-cancelled .progress-fill { background-color: #dc2626; }",
    ".usage-billing-section { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 5px; padding: 12px; margin: 15px 0; }",
    ".usage-billing-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }",
    ".usage-billing-title { font-weight: 500; font-size: 14px; color: #1f2937; }",
    ".usage-billing-text { font-size: 12px; color: #6b7280; }",
    ".usage-billing-status { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 8px; font-size: 12px; font-weight: 500; }",
    ".usage-billing-enabled { background-color: #dcfce7; color: #166534; }",
    ".usage-billing-disabled { background-color: #f3f4f6; color: #6b7280; }",
    ".subscription-error { color: #dc2626; font-style: italic; margin: 15px 0; }",
    ".directory-section { background-color: #f5f5f5; border-radius: 5px; padding: 10px; margin-bottom: 10px; }",
    ".directory-input { width: calc(100% - 100px); padding: 8px; margin: 8px 0; box-sizing: border-box; font-family: monospace; }",
    ".directory-error { color: #8b0000; font-style: italic; margin-top: 5px; }",
    ".directory-success { color: #006400; font-style: italic; margin-top: 5px; }",
    ".browse-button { background-color: #f0f0f0; border: 1px solid #ccc; padding: 8px 12px; margin-left: 8px; cursor: pointer; border-radius: 3px; }",
    ".browse-button:hover { background-color: #e0e0e0; }",
    ".directory-container { display: flex; align-items: center; }",
    "</style>",
    "<script>",
    "function saveApiKey() {",
    "  const keyInput = document.getElementById('rao-key');",
    "  const key = keyInput.value.trim();",
    "  if (key) {",
    "    window.parent.aiSaveApiKey('rao', key);",
    "  } else {",
    "    alert('Please enter a valid API key.');",
    "  }",
    "}",
    "function delete_api_key() {",
    "  if (confirm('Are you sure you want to delete this API key?')) {",
    "    window.parent.aiDeleteApiKey('rao');",
    "  }",
    "}",
    "function set_model(model) {",
    "  if (window.parent && typeof window.parent.aiSetModel === 'function') {",
    "    window.parent.aiSetModel('rao', model);",
    "  } else {",
    "    console.error('parent.aiSetModel is not available', window.parent);",
    "  }",
    "}",
    "function setAiWorkingDirectory() {",
    "  var dir = document.getElementById('working-directory').value;",
    "  if (dir) {",
    "    window.parent.aiSetWorkingDirectory(dir);",
    "    document.getElementById('directory-success').style.display = 'block';",
    "    document.getElementById('directory-error').style.display = 'none';",
    "    setTimeout(function() { document.getElementById('directory-success').style.display = 'none'; }, 3000);",
    "  }",
    "}",
    "function browseDirectory() {",
    "  window.parent.aiBrowseDirectory();",
    "}",
    "function update_directory_path(dir) {",
    "  if (dir) {",
    "    document.getElementById('working-directory').value = dir;",
    "    document.getElementById('directory-success').style.display = 'block';",
    "    document.getElementById('directory-error').style.display = 'none';",
    "    setTimeout(function() { document.getElementById('directory-success').style.display = 'none'; }, 3000);",
    "  }",
    "}",
    "</script>",
    "</head><body>",
    "<div class='container'>",
    "<h1>API Key Management</h1>",    
    
    "<div class='key-section'>",
    "<div class='model-label'>API Key</div>",
    
    if(!has_rao_key) paste0(
      "    <input type='password' id='rao-key' class='key-input' placeholder='Enter your Rao API key' value=''>",
      "    <button class='button save-button' onclick='saveApiKey()'>Save Rao API Key</button>"
    ) else paste0(
      "    <div class='model-row'>",
      "      <label class='model-label'>Model:</label>",
      "      <select id='model-select' class='model-select' onchange='set_model(this.value)'>",
            paste0(sapply(.rs.get_available_models(), function(model) {
              display_name <- .rs.get_model_display_names()[model]
              paste0("<option value='", model, "'", 
                     if(model == .rs.get_selected_model()) " selected" else "", 
                     ">", display_name, "</option>")
            }), collapse = ""),
      "      </select>",
      "    </div>",
      "    <button class='button delete-button' onclick='delete_api_key()'>Delete Rao API Key</button>"
    ),
    
    "</div>",

    "<div class='directory-section'>",
    "  <div class='model-label'>Working Directory</div>",
    "  <div>Setting a narrow working directory is helpful to Rao. This is your current working directory:</div>",
    "  <div class='directory-container'>",
    "    <input type='text' id='working-directory' class='directory-input' placeholder='Enter working directory path' value='", getwd(), "'>",
    "    <button class='browse-button' onclick='browseDirectory()'>Browse...</button>",
    "  </div>",
    "  <button class='button save-button' onclick='setAiWorkingDirectory()'>Set Working Directory</button>",
    "  <div id='directory-error' class='directory-error' style='display: none;'>New directory not valid</div>",
    "  <div id='directory-success' class='directory-success' style='display: none;'>Working directory changed successfully</div>",
    "</div>",
    
    if(has_rao_key) 
      "<div class='instruction-text'>Click the plus button in the top left to start a new conversation.</div><p>" 
    else "",
    
    "</div>",

    
    # Subscription Status Section (only show if has API key)
    if(has_rao_key) paste0(
      "<div class='subscription-section'>",
      "  <div class='section-header'>",
      "    <div class='model-label'>Subscription Status</div>",
      if(!is.null(subscription_data)) paste0(
        "    <span class='status-badge ", get_status_badge_class(subscription_data$subscription_status), "'>",
        "      ", format_status_text(subscription_data$subscription_status),
        "    </span>"
      ) else "",
      "  </div>",
      if(!is.null(subscription_data)) paste0(
        
        # Progress bar
        if(!is.null(get_progress_info(subscription_data))) {
          progress <- get_progress_info(subscription_data)
          paste0(
            "  <div class='progress-container ", progress$class, "'>",
            "    <div class='progress-label'>",
            "      <span>", progress$label, "</span>",
            "      <span>", progress$used, "/", progress$total, "</span>",
            "    </div>",
            "    <div class='progress-bar'>",
            "      <div class='progress-fill' style='width: ", (progress$used / progress$total * 100), "%;'></div>",
            "    </div>",
            "  </div>"
          )
        } else "",
        
        "  <div class='status-message'>", get_status_message(subscription_data), "</div>",
        
        # Usage-based billing section (only for active subscriptions)
        if(!is.null(subscription_data$subscription_status) && subscription_data$subscription_status == "active") paste0(
          "  <div class='usage-billing-section'>",
          "    <div class='usage-billing-header'>",
          "      <div class='usage-billing-title'>Usage-Based Billing</div>",
          "      <span class='usage-billing-status ", 
          if(!is.null(subscription_data$usage_based_billing_enabled) && subscription_data$usage_based_billing_enabled) "usage-billing-enabled" else "usage-billing-disabled",
          "'>",
          if(!is.null(subscription_data$usage_based_billing_enabled) && subscription_data$usage_based_billing_enabled) "Enabled" else "Disabled",
          "      </span>",
          "    </div>",
          "    <div class='usage-billing-text'>",
          if(!is.null(subscription_data$usage_based_billing_enabled) && subscription_data$usage_based_billing_enabled) {
            "You'll be invoiced in $10 increments for queries beyond the included monthly amount. Go to your <a href='https://lotas.ai/account'>account page</a> to update this setting."
          } else {
            "Enable to continue using the service when you exceed the included monthly amount. Go to your <a href='https://lotas.ai/account'>account page</a> to update this setting."
          },
          "    </div>",
          "  </div>"
        ) else ""
      ) else if(!is.null(subscription_error)) paste0(
        "  <div class='subscription-error'>", subscription_error, "</div>"
      ) else paste0(
        "  <div class='subscription-error'>Unable to retrieve subscription information</div>"
      ),
      "</div>"
    ) else "",
    
    "</body></html>"
  )
  
  # Generate the HTML file in the proper location for serving
  # The C++ handler expects the file to be directly in the AI base directory
  # as it extracts the filename from "doc/html/" and looks for it in the base directory
  base_ai_dir <- .rs.get_ai_base_dir()
  dir.create(base_ai_dir, recursive = TRUE, showWarnings = FALSE)
  
  api_key_html_path <- file.path(base_ai_dir, "api_key_management.html")
  writeChar(html, api_key_html_path, eos = NULL)
  
  # Return the path that the C++ handler expects to serve
  # The C++ handler will serve this as /ai/doc/html/api_key_management.html
  return("ai/doc/html/api_key_management.html")
})

.rs.addJsonRpcHandler("get_api_key_management", function() {
  path <- .rs.get_api_key_management_html()
  return(list(success = TRUE, path = path))
})

.rs.addFunction("save_api_key", function(provider, key) {
  if (provider == "rao" || provider == "openai") {  # Accept both for compatibility
    .rs.set_rao_key(key)
  }
  
  tryCatch({
    .rs.check_required_packages()
  }, error = function(e) {
    warning("Error checking required packages: ", e$message)
  })
  
  return(list(success = TRUE, message = "Saved Rao API key"))
})

.rs.addFunction("set_rao_key", function(key) {  
  .rs.ai_rao_key <<- key
  
  default_model <- "claude-sonnet-4-20250514"
  .rs.set_selected_model(default_model)
})

.rs.addFunction("get_provider_from_model", function(model) {
  # OpenAI models
  openai_models <- c("gpt-4.1", "o4-mini", "o3")
  
  # Anthropic models  
  anthropic_models <- c("claude-sonnet-4-20250514")
  
  if (model %in% openai_models) {
    return("openai")
  } else if (model %in% anthropic_models) {
    return("anthropic")
  } else {
    return("openai")  # Default to OpenAI for unknown models
  }
})

.rs.addFunction("get_active_provider", function() {
  # Determine provider based on selected model
  if (!is.null(.rs.get_api_key("rao"))) {
    model <- .rs.get_selected_model()
    if (!is.null(model)) {
      return(.rs.get_provider_from_model(model))
    }
    return("openai")  # Default to openai
  } else {
    return(NULL)
  }
})

# Helper function to get available models for a provider
.rs.addFunction("get_available_models", function(provider = NULL) {
  if (is.null(provider)) {
    # Return all available models if no provider specified
    return(c("claude-sonnet-4-20250514", "gpt-4.1", "o4-mini", "o3"))
  } else if (provider == "openai") {
    return(c("gpt-4.1", "o4-mini", "o3"))
  } else if (provider == "anthropic") {
    return(c("claude-sonnet-4-20250514"))
  }
  return(c())
})

.rs.addFunction("get_model_display_names", function() {
  models <- .rs.get_available_models()
  display_names <- c(
    "claude-sonnet-4-20250514 (Superior coding and analysis - recommended)",
    "gpt-4.1 (Quick coding and analysis)",
    "o4-mini (Fast reasoning and coding)",
    "o3 (Advanced reasoning - slower)"
  )
  names(display_names) <- models
  return(display_names)
})

.rs.addFunction("get_selected_model", function() {
  model <- if (exists(".rs.ai_selected_model", envir = .GlobalEnv)) get(".rs.ai_selected_model", envir = .GlobalEnv) else NULL
  
  if (is.null(model)) {
    return("claude-sonnet-4-20250514")  # Default model
  }
  return(model)
})

.rs.addFunction("set_selected_model", function(model) {  
  assign(".rs.ai_selected_model", model, envir = .GlobalEnv)
})

.rs.addFunction("delete_api_key", function(provider) {
  if (provider == "rao" || provider == "openai") {  # Accept both for compatibility
    .rs.set_rao_key(NULL)
  }
  
  return(list(success = TRUE, message = "Deleted Rao API key"))
})

.rs.addFunction("set_model_action", function(provider, model) {
  .rs.set_selected_model(model)
  
  return(TRUE)
})

.rs.addFunction("get_api_key", function(provider) {
  # Frontend only uses RAO_API_KEY regardless of provider
  # Backend handles routing to actual providers based on model
  
  # Check stored key first
  stored_key <- if (exists(".rs.ai_rao_key", envir = .GlobalEnv)) get(".rs.ai_rao_key", envir = .GlobalEnv) else NULL
  if (!is.null(stored_key)) return(stored_key)
  
  # Fallback to environment variable
  env_key <- Sys.getenv("RAO_API_KEY", unset = "")
  return(if (nchar(env_key) > 0) env_key else NULL)
})

.rs.addJsonRpcHandler("save_api_key", function(provider, key) {
  return(.rs.save_api_key(provider, key))
})

.rs.addJsonRpcHandler("delete_api_key", function(provider) {
  return(.rs.delete_api_key(provider))
})

.rs.addJsonRpcHandler("set_model", function(provider, model) {  
  return(.rs.set_model_action(provider, model))
})

.rs.addFunction("set_ai_working_directory", function(dir) {
  if (is.null(dir) || !is.character(dir) || length(dir) != 1) {
    message("Error: Invalid directory path")
    return(list(success = FALSE, error = "Invalid directory path"))
  }
  
  if (!dir.exists(dir)) {
    message("Error: Directory '", dir, "' does not exist")
    return(list(success = FALSE, error = "Directory does not exist"))
  }
  
  tryCatch({
    old_wd <- getwd()
    setwd(dir)
    return(list(success = TRUE))
  }, error = function(e) {
    tryCatch({
      setwd(old_wd)
    }, error = function(e2) {
    })
    message("Error: Cannot change working directory: ", e$message)
    return(list(success = FALSE, error = paste("Cannot change working directory:", e$message)))
  })
})

.rs.addJsonRpcHandler("set_ai_working_directory", function(dir) {
  if (is.null(dir) || !is.character(dir) || length(dir) != 1) {
    return(list(success = FALSE, error = "Invalid directory path"))
  }
  
  if (!dir.exists(dir)) {
    return(list(success = FALSE, error = "Directory does not exist"))
  }
  
  tryCatch({
    old_wd <- getwd()
    setwd(dir)
    return(list(success = TRUE))
  }, error = function(e) {
    tryCatch({
      setwd(old_wd)
    }, error = function(e2) {
    })
    return(list(success = FALSE, error = paste("Cannot change working directory:", e$message)))
  })
})

.rs.addFunction("browse_directory", function() {
   dir <- .rs.api.selectDirectory(
      caption = "Select Working Directory",
      label = "Browse",
      path = getwd()
   )
   
   if (!is.null(dir)) {
      tryCatch({
         old_wd <- getwd()
         setwd(dir)
         return(list(success = TRUE, directory = dir))
      }, error = function(e) {
         tryCatch({
            setwd(old_wd)
         }, error = function(e2) {
         })
         return(list(success = FALSE, error = paste("Cannot change working directory:", e$message)))
      })
   } else {
      return(list(success = FALSE, error = "No directory selected"))
   }
})

.rs.addJsonRpcHandler("browse_directory", function() {
   return(.rs.browse_directory())
})