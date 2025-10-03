# SessionAiSettings.R
#
# Copyright (C) 2025 by Lotas Inc.
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

.rs.addFunction("get_ai_pref", function(key, default_value = NULL) {
  full_key <- paste0("ai_", key)
  pref_value <- .rs.readUserPref(full_key)
  return(if (is.null(pref_value)) default_value else pref_value)
})

.rs.addFunction("set_ai_pref", function(key, value) {
  full_key <- paste0("ai_", key)
  .rs.writeUserPref(full_key, value)
  return(TRUE)
})

.rs.addFunction("get_ai_state", function(key, default_value = NULL) {
  full_key <- paste0("ai_", key)
  state_value <- .rs.readUserState(full_key)
  return(if (is.null(state_value)) default_value else state_value)
})

.rs.addFunction("set_ai_state", function(key, value) {
  full_key <- paste0("ai_", key)
  
  # Ensure numeric values are properly typed
  if (is.numeric(value)) {
    value <- as.numeric(value)
  }
  
  .rs.writeUserState(full_key, value)
  return(TRUE)
})

.rs.addJsonRpcHandler("get_settings", function() {
  # Since we now use DOM/GWT widgets instead of HTML files,
  # we just return success to maintain compatibility
  return(list(success = TRUE, path = "settings"))
})

.rs.addFunction("save_api_key", function(provider, key) {
  if (provider == "rao" || provider == "openai") {  # Accept both for compatibility
    # Store persistently using the secure user state infrastructure
    .rs.writeUserState("rao_api_key", key)
    # Also set in memory for immediate use
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
  
  default_model <- "claude-sonnet-4-5-20250929"
  .rs.set_selected_model(default_model)
})

.rs.addFunction("get_provider_from_model", function(model) {
  # OpenAI models
  openai_models <- c("gpt-5-mini")
  
  # Anthropic models  
  anthropic_models <- c("claude-sonnet-4-5-20250929")
  
  if (model %in% openai_models) {
    return("openai")
  } else if (model %in% anthropic_models) {
    return("anthropic")
  } else {
    return("openai")  # Default to OpenAI for unknown models
  }
})

.rs.addFunction("get_active_provider", function() {
  # Determine provider based on selected model (works for both Rao and BYOK)
  model <- .rs.get_selected_model()
  
  if (!is.null(model)) {
    provider <- .rs.get_provider_from_model(model)
    
    # Verify we have a key for this provider (either Rao or BYOK)
    has_rao_key <- !is.null(.rs.get_api_key(provider))
    has_byok_key <- !is.null(.rs.ai.getBYOKApiKey(provider)) && nchar(.rs.ai.getBYOKApiKey(provider)) > 0
    
    if (has_rao_key || has_byok_key) {
      return(provider)
    }
  }
  
  # Fallback: check if we have any Rao key
  if (!is.null(.rs.get_api_key("rao"))) {
    return("openai")  # Default to openai
  }
  
  # Fallback: check for BYOK keys
  if (!is.null(.rs.ai.getBYOKApiKey("openai")) && nchar(.rs.ai.getBYOKApiKey("openai")) > 0) {
    return("openai")
  }
  if (!is.null(.rs.ai.getBYOKApiKey("anthropic")) && nchar(.rs.ai.getBYOKApiKey("anthropic")) > 0) {
    return("anthropic")
  }
  
  return(NULL)
})

# Helper function to get available models for a provider
.rs.addFunction("get_available_models", function(provider = NULL) {
  # Check if user is signed in with Rao API key
  is_signed_in <- .rs.get_api_key_status()
  
  # If signed in, show all models regardless of BYOK settings
  if (is_signed_in) {
    all_models <- c("claude-sonnet-4-5-20250929", "gpt-5-mini")
    
    # Filter by provider if specified
    if (!is.null(provider)) {
      if (provider == "openai") {
        return(all_models[grepl("^gpt-", all_models)])
      } else if (provider == "anthropic") {
        return(all_models[grepl("^claude-", all_models)])
      }
    }
    
    return(all_models)
  }
  
  # Not signed in - only show models for enabled BYOK providers
  available_models <- c()
  
  # Check if BYOK Anthropic is enabled
  if (.rs.ai.isBYOKEnabled("anthropic")) {
    available_models <- c(available_models, "claude-sonnet-4-5-20250929")
  }
  
  # Check if BYOK OpenAI is enabled
  if (.rs.ai.isBYOKEnabled("openai")) {
    available_models <- c(available_models, "gpt-5-mini")
  }
  
  # If a specific provider is requested, filter to only that provider's models
  if (!is.null(provider)) {
    if (provider == "openai") {
      available_models <- available_models[grepl("^gpt-", available_models)]
    } else if (provider == "anthropic") {
      available_models <- available_models[grepl("^claude-", available_models)]
    }
  }
  
  return(available_models)
})

.rs.addFunction("get_model_display_names", function() {
  models <- .rs.get_available_models()
  
  # Define all possible display names
  all_display_names <- list(
    "claude-sonnet-4-5-20250929" = "claude-sonnet-4-5-20250929 (Superior coding and analysis - recommended)",
    "gpt-5-mini" = "gpt-5-mini (Reasoning tier)"
  )
  
  # Return only display names for available models
  if (length(models) == 0) {
    return(character(0))
  }
  
  display_names <- sapply(models, function(m) {
    if (!is.null(all_display_names[[m]])) {
      return(all_display_names[[m]])
    } else {
      return(m)  # Fallback to model name if no display name defined
    }
  }, USE.NAMES = TRUE)
  
  return(display_names)
})

.rs.addFunction("get_selected_model", function() {
  return(.rs.get_ai_pref("selected_model", "claude-sonnet-4-5-20250929"))
})

.rs.addFunction("set_selected_model", function(model) {
  .rs.set_ai_pref("selected_model", model)
  .rs.ai_selected_model <<- model
  return(TRUE)
})

.rs.addFunction("delete_api_key", function(provider) {
  if (provider == "rao" || provider == "openai") {  # Accept both for compatibility
    # Clear persistent storage
    .rs.writeUserState("rao_api_key", "")
    # Clear in-memory storage
    .rs.set_rao_key(NULL)
  }
  
  return(list(success = TRUE, message = "Deleted Rao API key"))
})

.rs.addFunction("set_model_action", function(provider, model) {
  .rs.set_selected_model(model)
  
  return(TRUE)
})

.rs.addFunction("get_api_key", function(provider) {
  # Check in-memory key first (for immediate use after setting)
  stored_key <- if (exists(".rs.ai_rao_key", envir = .GlobalEnv)) get(".rs.ai_rao_key", envir = .GlobalEnv) else NULL
  if (!is.null(stored_key)) return(stored_key)
  
  # Check persistent storage
  persistent_key <- .rs.readUserState("rao_api_key")
  if (!is.null(persistent_key) && nchar(persistent_key) > 0) {
    # Load into memory for performance and return
    .rs.set_rao_key(persistent_key)
    return(persistent_key)
  }
  
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

.rs.addJsonRpcHandler("sign_in_with_website", function(websiteUrl) {
  return(.rs.sign_in_with_website(websiteUrl))
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
    
    # Save working directory to persistent settings
    .rs.update_ai_setting("working_directory", dir)
    
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

# Add the missing R function implementations for Settings widget operations

.rs.addJsonRpcHandler("get_user_profile", function() {
  backend_config <- .rs.get_backend_config()
  api_key <- .rs.get_api_key("rao")
  
  if (is.null(api_key)) {
    return(list(error = "No API key configured"))
  }
  
  url <- paste0(backend_config$url, "/api/user/profile")
  
  request <- httr2::request(url)
  request <- httr2::req_headers(request, "Authorization" = paste("Bearer", api_key))
  
  tryCatch({
    response <- httr2::req_perform(request)
    
    response_body <- httr2::resp_body_json(response)    
    return(response_body)
  }, error = function(e) {
    # If there's a response object in the error, try to extract details    
    return(list(error = paste("Error retrieving user profile:", e$message)))
  })
})

.rs.addJsonRpcHandler("get_current_working_directory", function() {
  return(getwd())
})

.rs.addJsonRpcHandler("get_available_models", function() {
  return(.rs.get_available_models())
})

.rs.addJsonRpcHandler("get_api_key_status", function() {
  return(.rs.get_api_key_status())
})

.rs.addJsonRpcHandler("get_subscription_status", function() {
  return(.rs.get_subscription_status())
})

# Simple settings persistence for the Settings pane
# Temperature management functions
.rs.addFunction("get_temperature", function() {
  temperature <- .rs.get_ai_pref("temperature", 0.5)
  return(as.numeric(temperature))
})

.rs.addFunction("set_temperature_action", function(temperature) {
  # Ensure temperature is always a double/numeric, not an integer
  # This is critical because when temperature=0, R treats it as integer
  # but the preference schema expects a number (double)
  temperature <- as.double(temperature)
  .rs.set_ai_pref("temperature", temperature)
  return(TRUE)
})

.rs.addJsonRpcHandler("get_temperature", function() {
  return(.rs.get_temperature())
})

.rs.addJsonRpcHandler("set_temperature", function(temperature) {
  return(.rs.set_temperature_action(temperature))
})

# Add R function implementations that the C++ layer calls
.rs.addFunction("get_user_profile", function() {
  backend_config <- .rs.get_backend_config()
  api_key <- .rs.get_api_key("rao")
  
  if (is.null(api_key)) {
    return(list(error = "No API key configured"))
  }
  
  url <- paste0(backend_config$url, "/api/user/profile")
  request <- httr2::request(url)
  request <- httr2::req_headers(request, "Authorization" = paste("Bearer", api_key))
  
  tryCatch({
    response <- httr2::req_perform(request)
    response_body <- httr2::resp_body_json(response)    
    return(response_body)
  }, error = function(e) {
    return(list(error = paste("Error retrieving user profile:", e$message)))
  })
})

.rs.addFunction("get_subscription_status", function() {
  backend_config <- .rs.get_backend_config()
  api_key <- .rs.get_api_key("rao")
  
  if (is.null(api_key)) {
    return(list(error = "No API key configured"))
  }
  
  tryCatch({
    response <- httr2::resp_body_json(
      httr2::req_perform(
        httr2::req_headers(
          httr2::request(paste0(backend_config$url, "/api/user/subscription-status")),
          "Authorization" = paste("Bearer", api_key)
        )
      )
    )
    
    return(response)
  }, error = function(e) {
    return(list(error = paste("Error retrieving subscription status:", e$message)))
  })
})

.rs.addFunction("get_api_key_status", function() {
  api_key <- .rs.get_api_key("rao")
  return(!is.null(api_key))
})

.rs.addJsonRpcHandler("get_selected_model", function() {
  return(.rs.get_selected_model())
})

# Security settings management functions
.rs.addFunction("get_security_mode", function() {
  return(.rs.get_ai_pref("security_mode", "improve"))
})

.rs.addFunction("set_security_mode_action", function(mode) {
  .rs.set_ai_pref("security_mode", mode)
  return(TRUE)
})

.rs.addFunction("get_web_search_enabled", function() {
  return(.rs.get_ai_pref("web_search_enabled", FALSE))
})

.rs.addFunction("set_web_search_enabled_action", function(enabled) {
  .rs.set_ai_pref("web_search_enabled", enabled)
  return(TRUE)
})

# JSON RPC handlers for security settings
.rs.addJsonRpcHandler("get_security_mode", function() {
  return(.rs.get_security_mode())
})

.rs.addJsonRpcHandler("set_security_mode", function(mode) {
  return(.rs.set_security_mode_action(mode))
})

.rs.addJsonRpcHandler("get_web_search_enabled", function() {
  return(.rs.get_web_search_enabled())
})

.rs.addJsonRpcHandler("set_web_search_enabled", function(enabled) {
  return(.rs.set_web_search_enabled_action(enabled))
})

# Interaction mode management functions
.rs.addFunction("get_interaction_mode", function() {
  return(.rs.get_ai_pref("interaction_mode", "agent"))
})

.rs.addFunction("set_interaction_mode_action", function(mode) {
  .rs.set_ai_pref("interaction_mode", mode)
  return(TRUE)
})

.rs.addJsonRpcHandler("get_interaction_mode", function() {
  return(.rs.get_interaction_mode())
})

.rs.addJsonRpcHandler("set_interaction_mode", function(mode) {
  return(.rs.set_interaction_mode_action(mode))
})

# Automation settings management functions
.rs.addFunction("get_auto_accept_edits", function() {
  return(.rs.get_ai_pref("auto_accept_edits", FALSE))
})

.rs.addFunction("set_auto_accept_edits_action", function(enabled) {
  .rs.set_ai_pref("auto_accept_edits", enabled)
  return(TRUE)
})

.rs.addFunction("get_auto_accept_console", function() {
  return(.rs.get_ai_pref("auto_accept_console", FALSE))
})

.rs.addFunction("set_auto_accept_console_action", function(enabled) {
  .rs.set_ai_pref("auto_accept_console", enabled)
  return(TRUE)
})

.rs.addFunction("get_auto_accept_terminal", function() {
  return(.rs.get_ai_pref("auto_accept_terminal", FALSE))
})

.rs.addFunction("set_auto_accept_terminal_action", function(enabled) {
  .rs.set_ai_pref("auto_accept_terminal", enabled)
  return(TRUE)
})

.rs.addFunction("get_auto_run_files", function() {
  return(.rs.get_ai_pref("auto_run_files", FALSE))
})

.rs.addFunction("set_auto_run_files_action", function(enabled) {
  .rs.set_ai_pref("auto_run_files", enabled)
  return(TRUE)
})

.rs.addFunction("get_auto_delete_files", function() {
  return(.rs.get_ai_pref("auto_delete_files", FALSE))
})

.rs.addFunction("set_auto_delete_files_action", function(enabled) {
  .rs.set_ai_pref("auto_delete_files", enabled)
  return(TRUE)
})

# JSON RPC handlers for automation settings
.rs.addJsonRpcHandler("get_auto_accept_edits", function() {
  return(.rs.get_auto_accept_edits())
})

.rs.addJsonRpcHandler("set_auto_accept_edits", function(enabled) {
  return(.rs.set_auto_accept_edits_action(enabled))
})

.rs.addJsonRpcHandler("get_auto_accept_console", function() {
  return(.rs.get_auto_accept_console())
})

.rs.addJsonRpcHandler("set_auto_accept_console", function(enabled) {
  return(.rs.set_auto_accept_console_action(enabled))
})

.rs.addJsonRpcHandler("get_auto_accept_terminal", function() {
  return(.rs.get_auto_accept_terminal())
})

.rs.addJsonRpcHandler("set_auto_accept_terminal", function(enabled) {
  return(.rs.set_auto_accept_terminal_action(enabled))
})

.rs.addJsonRpcHandler("get_auto_run_files", function() {
  return(.rs.get_auto_run_files())
})

.rs.addJsonRpcHandler("set_auto_run_files", function(enabled) {
  return(.rs.set_auto_run_files_action(enabled))
})

.rs.addJsonRpcHandler("get_auto_delete_files", function() {
  return(.rs.get_auto_delete_files())
})

.rs.addJsonRpcHandler("set_auto_delete_files", function(enabled) {
  return(.rs.set_auto_delete_files_action(enabled))
})

# Allow/deny list settings management functions
.rs.addFunction("get_auto_accept_console_allow_anything", function() {
  return(.rs.get_ai_pref("auto_accept_console_allow_anything", FALSE))
})

.rs.addFunction("set_auto_accept_console_allow_anything_action", function(enabled) {
  .rs.set_ai_pref("auto_accept_console_allow_anything", enabled)
  return(TRUE)
})

.rs.addFunction("get_auto_accept_terminal_allow_anything", function() {
  return(.rs.get_ai_pref("auto_accept_terminal_allow_anything", FALSE))
})

.rs.addFunction("set_auto_accept_terminal_allow_anything_action", function(enabled) {
  .rs.set_ai_pref("auto_accept_terminal_allow_anything", enabled)
  return(TRUE)
})

.rs.addFunction("get_auto_run_files_allow_anything", function() {
  return(.rs.get_ai_pref("auto_run_files_allow_anything", FALSE))
})

.rs.addFunction("set_auto_run_files_allow_anything_action", function(enabled) {
  .rs.set_ai_pref("auto_run_files_allow_anything", enabled)
  return(TRUE)
})

.rs.addFunction("get_automation_list", function(list_type) {
  items <- .rs.get_ai_pref(list_type, character(0))
  
  if (is.null(items) || length(items) == 0) {
    return(character(0))
  }
  
  if (is.list(items)) {
    items <- unlist(items)
  }
  
  return(as.character(items))
})

.rs.addFunction("set_automation_list_action", function(list_type, items) {
  if (is.null(items)) {
    items <- character(0)
  } else if (is.list(items)) {
    items <- unlist(items)
  }
  
  items <- as.character(items)
  
  # Always convert to list for proper serialization
  items <- as.list(items)
  
  .rs.set_ai_pref(list_type, items)
  return(TRUE)
})

# JSON RPC handlers for allow/deny list settings
.rs.addJsonRpcHandler("get_auto_accept_console_allow_anything", function() {
  return(.rs.get_auto_accept_console_allow_anything())
})

.rs.addJsonRpcHandler("set_auto_accept_console_allow_anything", function(enabled) {
  return(.rs.set_auto_accept_console_allow_anything_action(enabled))
})

.rs.addJsonRpcHandler("get_auto_accept_terminal_allow_anything", function() {
  return(.rs.get_auto_accept_terminal_allow_anything())
})

.rs.addJsonRpcHandler("set_auto_accept_terminal_allow_anything", function(enabled) {
  return(.rs.set_auto_accept_terminal_allow_anything_action(enabled))
})

.rs.addJsonRpcHandler("get_auto_run_files_allow_anything", function() {
  return(.rs.get_auto_run_files_allow_anything())
})

.rs.addJsonRpcHandler("set_auto_run_files_allow_anything", function(enabled) {
  return(.rs.set_auto_run_files_allow_anything_action(enabled))
})

.rs.addJsonRpcHandler("get_automation_list", function(list_type) {
  return(.rs.get_automation_list(list_type))
})

.rs.addJsonRpcHandler("set_automation_list", function(list_type, items) {
  return(.rs.set_automation_list_action(list_type, items))
})

# User rules management functions
.rs.addFunction("load_ai_rules", function() {
  rules <- .rs.get_ai_pref("user_rules", character(0))
  
  if (is.null(rules) || length(rules) == 0) {
    return(character(0))
  }
  
  if (is.list(rules)) {
    rules <- unlist(rules)
  }
  
  return(as.character(rules))
})

.rs.addFunction("save_ai_rules", function(rules) {
  if (is.null(rules)) {
    rules <- character(0)
  } else if (is.list(rules)) {
    rules <- unlist(rules)
  }
  
  rules <- as.character(rules)
  
  # Always convert to list for proper serialization
  rules <- as.list(rules)
  
  .rs.set_ai_pref("user_rules", rules)
  return(TRUE)
})

.rs.addFunction("get_user_rules", function() {
  return(.rs.load_ai_rules())
})

.rs.addFunction("add_user_rule", function(rule) {
  if (is.null(rule) || !is.character(rule) || length(rule) != 1 || nchar(rule) == 0) {
    return(list(success = FALSE, error = "Invalid rule"))
  }
  
  # Get current rules
  rules <- .rs.get_user_rules()
  
  # Add new rule
  rules <- c(rules, rule)
  
  # Save updated rules
  result <- .rs.save_ai_rules(rules)
  
  if (result) {
    return(rules)  # Return rules array directly
  } else {
    return(list(success = FALSE, error = "Failed to save rule"))
  }
})

.rs.addFunction("edit_user_rule", function(index, rule) {
  if (is.null(index) || !is.numeric(index) || index < 1) {
    return(list(success = FALSE, error = "Invalid rule index"))
  }
  
  if (is.null(rule) || !is.character(rule) || length(rule) != 1 || nchar(rule) == 0) {
    return(list(success = FALSE, error = "Invalid rule"))
  }
  
  # Get current rules
  rules <- .rs.get_user_rules()
  
  # Check if index is valid
  if (index > length(rules)) {
    return(list(success = FALSE, error = "Rule index out of range"))
  }
  
  # Update rule
  rules[index] <- rule
  
  # Save updated rules
  result <- .rs.save_ai_rules(rules)
  
  if (result) {
    return(rules)  # Return rules array directly
  } else {
    return(list(success = FALSE, error = "Failed to update rule"))
  }
})

.rs.addFunction("delete_user_rule", function(index) {
  if (is.null(index) || !is.numeric(index) || index < 1) {
    return(list(success = FALSE, error = "Invalid rule index"))
  }
  
  # Get current rules
  rules <- .rs.get_user_rules()
  
  # Check if index is valid
  if (index > length(rules)) {
    return(list(success = FALSE, error = "Rule index out of range"))
  }
  
  # Remove rule
  rules <- rules[-index]
  
  # Save updated rules
  result <- .rs.save_ai_rules(rules)
  
  if (result) {
    return(rules)  # Return rules array directly
  } else {
    return(list(success = FALSE, error = "Failed to delete rule"))
  }
})

# JSON RPC handlers for user rules
.rs.addJsonRpcHandler("get_user_rules", function() {
  return(.rs.get_user_rules())
})

.rs.addJsonRpcHandler("add_user_rule", function(rule) {
  return(.rs.add_user_rule(rule))
})

.rs.addJsonRpcHandler("edit_user_rule", function(index, rule) {
  return(.rs.edit_user_rule(index, rule))
})

.rs.addJsonRpcHandler("delete_user_rule", function(index) {
  return(.rs.delete_user_rule(index))
})

# Sign in with website flow - now using loopback server for desktop
.rs.addFunction("sign_in_with_website", function(websiteUrl) {
  # Install authentication dependencies as the very first step
  .rs.install_auth_dependencies()
  
  backend_env <- .rs.detect_backend_environment()
  
  # Start loopback server for desktop OAuth callback
  loopback_info <- .rs.start_auth_loopback_server()
  loopback_url <- paste0("http://", loopback_info$address, ":", loopback_info$port, "/auth_callback")
    
  if (backend_env == "local") {
    # Use localhost for local development
    sign_in_url <- paste0("http://localhost:3000/rao-callback?redirect_uri=", URLencode(loopback_url, reserved = TRUE))
  } else {
    # Use production website
    sign_in_url <- paste0("https://www.lotas.ai/rao-callback?redirect_uri=", URLencode(loopback_url, reserved = TRUE))
  }
  
  # Return just the URL string
  sign_in_url
})

# Start a temporary HTTP server on loopback interface for OAuth callback
.rs.addFunction("start_auth_loopback_server", function() {
  # Now load the packages
  if (!requireNamespace("httpuv", quietly = TRUE)) {
    stop("httpuv package installation failed")
  }
  if (!requireNamespace("later", quietly = TRUE)) {
    stop("later package installation failed")
  }
  
  # Try both IPv4 and IPv6 loopback as recommended by RFC 8252
  loopback_addresses <- c("127.0.0.1", "::1")
  
  for (address in loopback_addresses) {
    # Try ephemeral port range (49152-65535 as recommended by IANA)
    # Start with a random port in this range
    start_port <- sample(49152:65535, 1)
    
    for (i in 1:100) {  # Try up to 100 ports
      port <- start_port + i - 1
      if (port > 65535) port <- 49152 + (port - 65536)  # Wrap around
      
      tryCatch({
        # Create a temporary HTTP server
        server <- httpuv::startServer(address, port, list(
        call = function(req) {
          # Handle the OAuth callback
          if (req$PATH_INFO == "/auth_callback") {
            query_string <- req$QUERY_STRING
            
            # Parse query parameters
            params <- .rs.parse_query_string(query_string)
            
            if (!is.null(params$api_key) && params$api_key != "") {
              # Save the API key
              .rs.save_api_key("rao", params$api_key)
              
              # Notify the UI that authentication completed
              .rs.enqueClientEvent("ai_authentication_completed", list())
              
              # Schedule server cleanup after 3 seconds to allow response to be sent
              later::later(function() {
                tryCatch({
                  httpuv::stopServer(server)
                  if (exists(".rs.auth_server", envir = .GlobalEnv)) {
                    rm(".rs.auth_server", envir = .GlobalEnv)
                  }
                }, error = function(e) {
                  # Ignore cleanup errors
                })
              }, delay = 3)
              
              # Return success page
              success_html <- paste0(
                '<!DOCTYPE html>',
                '<html><head>',
                '<meta charset="UTF-8">',
                '<title>Authentication Successful</title></head>',
                '<body style="font-family: Arial, sans-serif; text-align: center; margin-top: 50px;">',
                '<div style="color: green; font-size: 48px; margin-bottom: 16px;">&#x2713;</div>',
                '<h2 style="color: #333; margin-bottom: 8px;">Authentication Successful</h2>',
                '<p style="color: #666;">You can now close this window and return to Rao.</p>',
                '<script>setTimeout(function(){ window.close(); }, 3000);</script>',
                '</body></html>'
              )
              
              return(list(
                status = 200L,
                headers = list("Content-Type" = "text/html"),
                body = success_html
              ))
            } else {
              # Handle error case
              error_html <- paste0(
                '<!DOCTYPE html>',
                '<html><head><title>Authentication Failed</title></head>',
                '<body style="font-family: Arial, sans-serif; text-align: center; margin-top: 50px;">',
                '<div style="color: red; font-size: 48px; margin-bottom: 16px;">✗</div>',
                '<h2 style="color: #333; margin-bottom: 8px;">Authentication Failed</h2>',
                '<p style="color: #666;">No API key received. Please try again.</p>',
                '</body></html>'
              )
              
              return(list(
                status = 400L,
                headers = list("Content-Type" = "text/html"),
                body = error_html
              ))
            }
          }
          
          # Default 404 response
          return(list(
            status = 404L,
            headers = list("Content-Type" = "text/plain"),
            body = "Not Found"
          ))
        }
        ))
        
        # Store server reference for cleanup  
        assign(".rs.auth_server", server, envir = .GlobalEnv)
        
        # Return the port number and address used
        return(list(port = port, address = address))
        
      }, error = function(e) {
        # Port in use or other error, try next port
        next
      })
    }
  }
  
  stop("Could not start OAuth callback server on any loopback interface")
})

# Install dependencies required for authentication
.rs.addFunction("install_auth_dependencies", function() {
  required_packages <- c("httpuv", "later")
  
  # Check which packages are missing
  installed <- vapply(required_packages, function(pkg) {
    location <- find.package(pkg, quiet = TRUE)
    length(location) > 0
  }, FUN.VALUE = logical(1))
  
  missing <- required_packages[!installed]
  if (length(missing) == 0) {
    return(TRUE)  # All packages already installed
  }
  
  # Ask user to install missing packages
  title <- "Install Required Packages"
  message <- paste(
    "The following packages are required for AI authentication and will be installed:",
    paste("-", missing, collapse = "\n"),
    "\nWould you like to proceed?",
    sep = "\n"
  )
  
  ok <- .rs.api.showQuestion(title, message)
  if (!ok) {
    stop("Authentication cannot proceed without required packages", call. = FALSE)
  }
  
  # Install missing packages
  cat("Installing packages:", paste(missing, collapse = ", "), "\n")
  
  tryCatch({
    utils::install.packages(missing, repos = getOption("repos"))
    
    # Verify installation
    still_missing <- vapply(missing, function(pkg) {
      location <- find.package(pkg, quiet = TRUE)
      length(location) == 0
    }, FUN.VALUE = logical(1))
    
    if (any(still_missing)) {
      failed <- missing[still_missing]
      stop(paste("Failed to install packages:", paste(failed, collapse = ", ")))
    }
    
    cat("Successfully installed all required packages\n")
    return(TRUE)
    
  }, error = function(e) {
    stop(paste("Error installing packages:", e$message), call. = FALSE)
  })
})

# Parse query string into named list
.rs.addFunction("parse_query_string", function(query_string) {
  if (is.null(query_string) || query_string == "") {
    return(list())
  }
  
  # Remove leading ? if present
  if (substr(query_string, 1, 1) == "?") {
    query_string <- substr(query_string, 2, nchar(query_string))
  }
  
  # Split by & and then by =
  pairs <- strsplit(query_string, "&")[[1]]
  result <- list()
  
  for (pair in pairs) {
    if (grepl("=", pair)) {
      parts <- strsplit(pair, "=", fixed = TRUE)[[1]]
      if (length(parts) == 2) {
        key <- URLdecode(parts[1])
        value <- URLdecode(parts[2])
        result[[key]] <- value
      }
    }
  }
  
  return(result)
})

# Clean up any existing authentication server
.rs.addFunction("cleanup_auth_server", function() {
  if (exists(".rs.auth_server", envir = .GlobalEnv)) {
    tryCatch({
      httpuv::stopServer(get(".rs.auth_server", envir = .GlobalEnv))
      rm(".rs.auth_server", envir = .GlobalEnv)
    }, error = function(e) {
      # Ignore cleanup errors
    })
  }
})

# RPC handler for cleaning up authentication server
.rs.addJsonRpcHandler("cleanup_auth_server", function() {
  .rs.cleanup_auth_server()
  return(TRUE)
})

# Console command auto-accept checking function (internal use only)
.rs.addFunction("should_auto_accept_console_command", function(r_code) {
  # Check if auto_accept_console is enabled
  auto_accept_enabled <- .rs.get_auto_accept_console()
  if (!auto_accept_enabled) {
    return(FALSE)
  }
  
  # Extract R functions from the code
  functions_in_code <- .rs.extract_r_functions(r_code)
  
  # Trim whitespace from extracted functions
  functions_in_code <- trimws(functions_in_code)
  
  # If no functions were extracted, don't auto-accept
  if (length(functions_in_code) == 0) {
    return(FALSE)
  }
  
  # Check allow_anything setting
  allow_anything <- .rs.get_auto_accept_console_allow_anything()

  
  if (allow_anything) {
    # If allow_anything is TRUE, check that none of the functions are in the deny list
    deny_list <- .rs.get_automation_list("auto_accept_console_deny_list")
    deny_list <- unlist(deny_list)  # Convert from list to character vector
    # Normalize whitespace
    deny_list <- trimws(deny_list)
    deny_list <- deny_list[nzchar(deny_list)]

    
    # Check if any function in the code is in the deny list
    for (func in functions_in_code) {
      if (func %in% deny_list) {
        return(FALSE)  # Found a function in deny list, don't auto-accept
      }
    }
    
    return(TRUE)  # No functions in deny list, auto-accept
  } else {
    # If allow_anything is FALSE, check that ALL functions are in the allow list
    allow_list <- .rs.get_automation_list("auto_accept_console_allow_list")
    allow_list <- unlist(allow_list)  # Convert from list to character vector
    # Normalize whitespace
    allow_list <- trimws(allow_list)
    allow_list <- allow_list[nzchar(allow_list)]

    
    # Check if all functions in the code are in the allow list
    for (func in functions_in_code) {
      if (!func %in% allow_list) {

        return(FALSE)  # Found a function not in allow list, don't auto-accept
      }
    }
    

    return(TRUE)  # All functions are in allow list, auto-accept
  }
})

# Run file auto-accept checking function (internal use only)
.rs.addFunction("should_auto_accept_run_file", function(filename) {
  # Check if auto_run_files is enabled
  auto_run_enabled <- .rs.get_auto_run_files()
  
  if (!auto_run_enabled) {
    return(FALSE)
  }
  
  # Get both basename and full normalized path for matching (same logic as handle_run_file)
  file_basename <- basename(filename)
  
  # Construct the full normalized path like handle_run_file does
  cwd <- getwd()
  full_path <- file.path(cwd, filename)
  normalized_path <- normalizePath(full_path, winslash = "/", mustWork = FALSE)
  
  # Check allow_anything setting
  allow_anything <- .rs.get_auto_run_files_allow_anything()
  
  if (allow_anything) {
    # If allow_anything is TRUE, check that the file is not in the deny list
    deny_list <- .rs.get_automation_list("auto_run_files_deny_list")
    deny_list <- unlist(deny_list)  # Convert from list to character vector

    
    # Expand tildes in deny list entries for comparison
    expanded_deny_list <- sapply(deny_list, function(path) {
      if (startsWith(path, "~")) {
        # Expand tilde using normalizePath
        tryCatch({
          normalizePath(path, winslash = "/", mustWork = FALSE)
        }, error = function(e) {
          path  # Return original if expansion fails
        })
      } else {
        path  # Return as-is if no tilde
      }
    }, USE.NAMES = FALSE)

    
    # Check if the file (basename, original filename, or full normalized path) is in the deny list
    if (file_basename %in% deny_list || filename %in% deny_list || normalized_path %in% deny_list ||
        file_basename %in% expanded_deny_list || filename %in% expanded_deny_list || normalized_path %in% expanded_deny_list) {
      return(FALSE)  # File is in deny list, don't auto-accept
    }
    
    return(TRUE)  # File not in deny list, auto-accept
  } else {
    # If allow_anything is FALSE, check that the file is in the allow list
    allow_list <- .rs.get_automation_list("auto_run_files_allow_list")
    allow_list <- unlist(allow_list)  # Convert from list to character vector
    
    # Expand tildes in allow list entries for comparison
    expanded_allow_list <- sapply(allow_list, function(path) {
      if (startsWith(path, "~")) {
        # Expand tilde using normalizePath
        tryCatch({
          normalizePath(path, winslash = "/", mustWork = FALSE)
        }, error = function(e) {
          path  # Return original if expansion fails
        })
      } else {
        path  # Return as-is if no tilde
      }
    }, USE.NAMES = FALSE)
    
    # Check exact matches against both original and expanded lists
    basename_match <- file_basename %in% allow_list || file_basename %in% expanded_allow_list
    filename_match <- filename %in% allow_list || filename %in% expanded_allow_list
    normalized_match <- normalized_path %in% allow_list || normalized_path %in% expanded_allow_list
    
    # Check if the file (basename, original filename, or full normalized path) is in the allow list
    if (!basename_match && !filename_match && !normalized_match) {
      return(FALSE)  # File not in allow list, don't auto-accept
    }
    
    return(TRUE)  # File is in allow list, auto-accept
  }
})

# Delete file auto-accept checking function (internal use only)
.rs.addFunction("should_auto_accept_delete_file", function(filename) {
  # Check if auto_delete_files is enabled
  auto_delete_enabled <- .rs.get_auto_delete_files()
  
  if (!auto_delete_enabled) {
    return(FALSE)
  }
  
  # If auto-delete is enabled, always auto-accept (no allow/deny lists for delete files)
  return(TRUE)
})

# Terminal command auto-accept checking function (internal use only)
.rs.addFunction("should_auto_accept_terminal_command", function(terminal_command) {
  # Check if auto_accept_terminal is enabled
  auto_accept_enabled <- .rs.get_auto_accept_terminal()
  if (!auto_accept_enabled) {
    return(FALSE)
  }
  
  # Extract terminal commands from the command
  commands_in_code <- .rs.extract_bash_functions(terminal_command)
  
  # Trim whitespace from extracted commands
  commands_in_code <- trimws(commands_in_code)
  
  # If no commands were extracted, don't auto-accept
  if (length(commands_in_code) == 0) {
    return(FALSE)
  }
  
  # Check allow_anything setting
  allow_anything <- .rs.get_auto_accept_terminal_allow_anything()
  
  if (allow_anything) {
    # If allow_anything is TRUE, check that none of the commands are in the deny list
    deny_list <- .rs.get_automation_list("auto_accept_terminal_deny_list")
    deny_list <- unlist(deny_list)  # Convert from list to character vector
    # Normalize whitespace
    deny_list <- trimws(deny_list)
    deny_list <- deny_list[nzchar(deny_list)]
    
    # Check if any command in the code is in the deny list
    for (cmd in commands_in_code) {
      if (cmd %in% deny_list) {
        return(FALSE)  # Found a command in deny list, don't auto-accept
      }
    }
    
    return(TRUE)  # No commands in deny list, auto-accept
  } else {
    # If allow_anything is FALSE, check that ALL commands are in the allow list
    allow_list <- .rs.get_automation_list("auto_accept_terminal_allow_list")
    allow_list <- unlist(allow_list)  # Convert from list to character vector
    # Normalize whitespace
    allow_list <- trimws(allow_list)
    allow_list <- allow_list[nzchar(allow_list)]
    
    # Check if all commands in the code are in the allow list
    for (cmd in commands_in_code) {
      if (!cmd %in% allow_list) {
        return(FALSE)  # Found a command not in allow list, don't auto-accept
      }
    }
    
    return(TRUE)  # All commands are in allow list, auto-accept
  }
})

# BYOK API Key Management
.rs.addJsonRpcHandler("has_byok_api_key", function(provider) {
  key_name <- paste0("ai_byok_", provider, "_api_key")
  api_key <- .rs.readUserState(key_name)
  has_key <- !is.null(api_key) && nchar(api_key) > 0
  return(has_key)
})