# SessionAiSettings.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

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
  
  default_model <- "claude-sonnet-4-20250514"
  .rs.set_selected_model(default_model)
})

.rs.addFunction("get_provider_from_model", function(model) {
  # OpenAI models
  openai_models <- c("gpt-4.1") # "o4-mini", "o3")
  
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
    return(c("claude-sonnet-4-20250514", "gpt-4.1")) # "o4-mini", "o3"))
  } else if (provider == "openai") {
    return(c("gpt-4.1")) # "o4-mini", "o3"))
  } else if (provider == "anthropic") {
    return(c("claude-sonnet-4-20250514"))
  }
  return(c())
})

.rs.addFunction("get_model_display_names", function() {
  models <- .rs.get_available_models()
  display_names <- c(
    "claude-sonnet-4-20250514 (Superior coding and analysis - recommended)",
    "gpt-4.1 (Quick coding and analysis)"
    # "o4-mini (Fast reasoning and coding)",
    # "o3 (Advanced reasoning - slower)"
  )
  names(display_names) <- models
  return(display_names)
})

.rs.addFunction("get_selected_model", function() {
  model <- if (exists(".rs.ai_selected_model", envir = .GlobalEnv)) get(".rs.ai_selected_model", envir = .GlobalEnv) else NULL
  
  if (is.null(model)) {
    # Try to load from persistent settings
    model <- .rs.get_ai_setting("selected_model", "claude-sonnet-4-20250514")
  }
  return(model)
})

.rs.addFunction("set_selected_model", function(model) {  
  assign(".rs.ai_selected_model", model, envir = .GlobalEnv)
  
  # Save selected model to persistent settings
  .rs.update_ai_setting("selected_model", model)
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
.rs.addFunction("get_ai_settings_path", function() {
  # Get the path to the AI settings file
  base_ai_dir <- .rs.get_ai_base_dir()
  settings_path <- file.path(base_ai_dir, "ai_settings.json")
  return(settings_path)
})

.rs.addFunction("load_ai_settings", function() {
  # Load AI settings from persistent storage
  settings_path <- .rs.get_ai_settings_path()
  
  # Create default settings if file doesn't exist
  # Set to improve only here when it's loaded for the first time
  if (!file.exists(settings_path)) {
    return(list(
      selected_model = "claude-sonnet-4-20250514",
      working_directory = NULL,
      temperature = 0.5,
      security_mode = "improve",
      web_search_enabled = FALSE,
      auto_accept_edits = FALSE,
      auto_accept_console = FALSE,
      auto_accept_terminal = FALSE,
      auto_run_files = FALSE,
      auto_delete_files = FALSE,
      auto_accept_console_allow_anything = FALSE,
      auto_accept_terminal_allow_anything = FALSE,
      auto_run_files_allow_anything = FALSE,
      auto_accept_console_allow_list = character(0),
      auto_accept_console_deny_list = character(0),
      auto_accept_terminal_allow_list = character(0),
      auto_accept_terminal_deny_list = character(0),
      auto_run_files_allow_list = character(0),
      auto_run_files_deny_list = character(0)
    ))
  }
  
  tryCatch({
    # Read settings from file
    settings_json <- readLines(settings_path, warn = FALSE)
    settings <- jsonlite::fromJSON(paste(settings_json, collapse = ""), simplifyVector = FALSE)
    
    # Ensure we have the required fields
    if (is.null(settings$selected_model)) {
      settings$selected_model <- "claude-sonnet-4-20250514"
    }
    if (is.null(settings$working_directory)) {
      settings$working_directory <- NULL
    }
    if (is.null(settings$temperature)) {
      settings$temperature <- 0.5
    }
    if (is.null(settings$security_mode)) {
      settings$security_mode <- "secure"
    }
    if (is.null(settings$web_search_enabled)) {
      settings$web_search_enabled <- FALSE
    }
    if (is.null(settings$auto_accept_edits)) {
      settings$auto_accept_edits <- FALSE
    }
    if (is.null(settings$auto_accept_console)) {
      settings$auto_accept_console <- FALSE
    }
    if (is.null(settings$auto_accept_terminal)) {
      settings$auto_accept_terminal <- FALSE
    }
    if (is.null(settings$auto_run_files)) {
      settings$auto_run_files <- FALSE
    }
    if (is.null(settings$auto_delete_files)) {
      settings$auto_delete_files <- FALSE
    }
    if (is.null(settings$auto_accept_console_allow_anything)) {
      settings$auto_accept_console_allow_anything <- FALSE
    }
    if (is.null(settings$auto_accept_terminal_allow_anything)) {
      settings$auto_accept_terminal_allow_anything <- FALSE
    }
    if (is.null(settings$auto_run_files_allow_anything)) {
      settings$auto_run_files_allow_anything <- FALSE
    }
    if (is.null(settings$auto_accept_console_allow_list)) {
      settings$auto_accept_console_allow_list <- list()
    }
    if (is.null(settings$auto_accept_console_deny_list)) {
      settings$auto_accept_console_deny_list <- list()
    }
    if (is.null(settings$auto_accept_terminal_allow_list)) {
      settings$auto_accept_terminal_allow_list <- list()
    }
    if (is.null(settings$auto_accept_terminal_deny_list)) {
      settings$auto_accept_terminal_deny_list <- list()
    }
    if (is.null(settings$auto_run_files_allow_list)) {
      settings$auto_run_files_allow_list <- list()
    }
    if (is.null(settings$auto_run_files_deny_list)) {
      settings$auto_run_files_deny_list <- list()
    }
    
    return(settings)
  }, error = function(e) {
    warning("Failed to load AI settings: ", e$message, ". Using defaults.")
    return(list(
      selected_model = "claude-sonnet-4-20250514",
      working_directory = NULL,
      temperature = 0.5,
      security_mode = "secure",
      web_search_enabled = FALSE,
      auto_accept_edits = FALSE,
      auto_accept_console = FALSE,
      auto_accept_terminal = FALSE,
      auto_run_files = FALSE,
      auto_delete_files = FALSE,
      auto_accept_console_allow_anything = FALSE,
      auto_accept_terminal_allow_anything = FALSE,
      auto_run_files_allow_anything = FALSE,
      auto_accept_console_allow_list = character(0),
      auto_accept_console_deny_list = character(0),
      auto_accept_terminal_allow_list = character(0),
      auto_accept_terminal_deny_list = character(0),
      auto_run_files_allow_list = character(0),
      auto_run_files_deny_list = character(0)
    ))
  })
})

.rs.addFunction("save_ai_settings", function(settings) {
  # Save AI settings to persistent storage
  settings_path <- .rs.get_ai_settings_path()
  
  # Ensure directory exists
  dir.create(dirname(settings_path), recursive = TRUE, showWarnings = FALSE)
  
  tryCatch({
    # Write settings to file
    settings_json <- jsonlite::toJSON(settings, pretty = TRUE, auto_unbox = TRUE)
    writeLines(settings_json, settings_path)
    return(TRUE)
  }, error = function(e) {
    warning("Failed to save AI settings: ", e$message)
    return(FALSE)
  })
})

.rs.addFunction("initialize_ai_settings", function() {
  # Initialize AI settings system and load persisted settings
  settings <- .rs.load_ai_settings()
  
  # Apply settings to current session
  if (!is.null(settings$selected_model)) {
    .rs.set_selected_model(settings$selected_model)
  }
  
  if (!is.null(settings$working_directory) && dir.exists(settings$working_directory)) {
    tryCatch({
      setwd(settings$working_directory)
    }, error = function(e) {
      # Silently ignore directory change errors
    })
  }
  
  return(settings)
})

.rs.addFunction("update_ai_setting", function(key, value) {
  # Update a specific setting and save to disk
  settings <- .rs.load_ai_settings()
  settings[[key]] <- value
  .rs.save_ai_settings(settings)
  return(TRUE)
})

.rs.addFunction("get_ai_setting", function(key, default_value = NULL) {
  # Get a specific setting value
  settings <- .rs.load_ai_settings()
  return(if (is.null(settings[[key]])) default_value else settings[[key]])
})

# Temperature management functions
.rs.addFunction("get_temperature", function() {
  temperature <- .rs.get_ai_setting("temperature", 0.5)
  return(as.numeric(temperature))
})

.rs.addFunction("set_temperature_action", function(temperature) {  
  if (is.null(temperature) || !is.numeric(temperature) || temperature < 0.0 || temperature > 1.0) {
    return(FALSE)
  }
  
  # Save temperature to persistent settings
  result <- .rs.update_ai_setting("temperature", temperature)  
  return(result)
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
  security_mode <- .rs.get_ai_setting("security_mode", "secure")
  return(security_mode)
})

.rs.addFunction("set_security_mode_action", function(mode) {  
  if (is.null(mode) || !is.character(mode) || !mode %in% c("secure", "improve")) {
    return(FALSE)
  }
  
  # Save security mode to persistent settings
  result <- .rs.update_ai_setting("security_mode", mode)  
  return(result)
})

.rs.addFunction("get_web_search_enabled", function() {
  web_search_enabled <- .rs.get_ai_setting("web_search_enabled", FALSE)
  return(as.logical(web_search_enabled))
})

.rs.addFunction("set_web_search_enabled_action", function(enabled) {  
  if (is.null(enabled)) {
    return(FALSE)
  }
  
  # Convert to logical and save to persistent settings
  enabled <- as.logical(enabled)
  result <- .rs.update_ai_setting("web_search_enabled", enabled)  
  return(result)
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

# Automation settings management functions
.rs.addFunction("get_auto_accept_edits", function() {
  auto_accept_edits <- .rs.get_ai_setting("auto_accept_edits", FALSE)
  return(as.logical(auto_accept_edits))
})

.rs.addFunction("set_auto_accept_edits_action", function(enabled) {  
  if (is.null(enabled)) {
    return(FALSE)
  }
  
  # Convert to logical and save to persistent settings
  enabled <- as.logical(enabled)
  result <- .rs.update_ai_setting("auto_accept_edits", enabled)  
  return(result)
})

.rs.addFunction("get_auto_accept_console", function() {
  auto_accept_console <- .rs.get_ai_setting("auto_accept_console", FALSE)
  return(as.logical(auto_accept_console))
})

.rs.addFunction("set_auto_accept_console_action", function(enabled) {  
  if (is.null(enabled)) {
    return(FALSE)
  }
  
  # Convert to logical and save to persistent settings
  enabled <- as.logical(enabled)
  result <- .rs.update_ai_setting("auto_accept_console", enabled)  
  return(result)
})

.rs.addFunction("get_auto_accept_terminal", function() {
  auto_accept_terminal <- .rs.get_ai_setting("auto_accept_terminal", FALSE)
  return(as.logical(auto_accept_terminal))
})

.rs.addFunction("set_auto_accept_terminal_action", function(enabled) {  
  if (is.null(enabled)) {
    return(FALSE)
  }
  
  # Convert to logical and save to persistent settings
  enabled <- as.logical(enabled)
  result <- .rs.update_ai_setting("auto_accept_terminal", enabled)  
  return(result)
})

.rs.addFunction("get_auto_run_files", function() {
  auto_run_files <- .rs.get_ai_setting("auto_run_files", FALSE)
  return(as.logical(auto_run_files))
})

.rs.addFunction("set_auto_run_files_action", function(enabled) {  
  if (is.null(enabled)) {
    return(FALSE)
  }
  
  # Convert to logical and save to persistent settings
  enabled <- as.logical(enabled)
  result <- .rs.update_ai_setting("auto_run_files", enabled)  
  return(result)
})

.rs.addFunction("get_auto_delete_files", function() {
  auto_delete_files <- .rs.get_ai_setting("auto_delete_files", FALSE)
  return(as.logical(auto_delete_files))
})

.rs.addFunction("set_auto_delete_files_action", function(enabled) {  
  if (is.null(enabled)) {
    return(FALSE)
  }
  
  # Convert to logical and save to persistent settings
  enabled <- as.logical(enabled)
  result <- .rs.update_ai_setting("auto_delete_files", enabled)  
  return(result)
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
  allow_anything <- .rs.get_ai_setting("auto_accept_console_allow_anything", FALSE)
  return(as.logical(allow_anything))
})

.rs.addFunction("set_auto_accept_console_allow_anything_action", function(enabled) {  
  if (is.null(enabled)) {
    return(FALSE)
  }
  
  # Convert to logical and save to persistent settings
  enabled <- as.logical(enabled)
  result <- .rs.update_ai_setting("auto_accept_console_allow_anything", enabled)  
  return(result)
})

.rs.addFunction("get_auto_accept_terminal_allow_anything", function() {
  allow_anything <- .rs.get_ai_setting("auto_accept_terminal_allow_anything", FALSE)
  return(as.logical(allow_anything))
})

.rs.addFunction("set_auto_accept_terminal_allow_anything_action", function(enabled) {  
  if (is.null(enabled)) {
    return(FALSE)
  }
  
  # Convert to logical and save to persistent settings
  enabled <- as.logical(enabled)
  result <- .rs.update_ai_setting("auto_accept_terminal_allow_anything", enabled)  
  return(result)
})

.rs.addFunction("get_auto_run_files_allow_anything", function() {
  allow_anything <- .rs.get_ai_setting("auto_run_files_allow_anything", FALSE)
  return(as.logical(allow_anything))
})

.rs.addFunction("set_auto_run_files_allow_anything_action", function(enabled) {  
  if (is.null(enabled)) {
    return(FALSE)
  }
  
  # Convert to logical and save to persistent settings
  enabled <- as.logical(enabled)
  result <- .rs.update_ai_setting("auto_run_files_allow_anything", enabled)  
  return(result)
})

.rs.addFunction("get_automation_list", function(list_type) {
  if (is.null(list_type) || !is.character(list_type)) {
    return(list())
  }
  
  # list_type should now be correct (e.g., "auto_accept_console_allow_list")
  list_items <- .rs.get_ai_setting(list_type, character(0))  # Default to empty character vector
  
  # Ensure we always return character vectors consistently, regardless of JSON storage format
  if (is.list(list_items)) {
    # Convert list back to character vector (handles multi-item case from JSON)
    list_items <- as.character(unlist(list_items))
  } else if (is.character(list_items)) {
    # Already a character vector (single-item case), keep as-is
    list_items <- list_items
  } else {
    # Fallback to empty character vector
    list_items <- character(0)
  }
  
  # Convert to list for JSON response
  list_items <- as.list(list_items)
  
  return(list_items)
})

.rs.addFunction("set_automation_list_action", function(list_type, items) {
  
  if (is.null(list_type) || !is.character(list_type)) {
    return(FALSE)
  }
  
  if (is.null(items)) {
    items <- list()
  }
  
  # list_type should now be correct (e.g., "auto_accept_console_allow_list")
  # C++ json::Array is passed as a list directly to R - no JSON string conversion needed
  if (is.list(items)) {
    # Convert list elements to character vector
    items <- as.character(unlist(items))
  } else if (!is.character(items)) {
    # Fallback: convert to character
    items <- as.character(items)
  }
  
  # Remove any empty strings
  items <- items[nzchar(items)]
  
  # Remove duplicates while preserving order
  if (length(items) > 0) {
    items <- items[!duplicated(items)]
  }
  
  # Save as character vector (not list) to avoid nested structure
  # Empty vector for no items, character vector for items
  if (length(items) == 0) {
    items <- character(0)  # Empty character vector instead of list()
  }
  # items is already a character vector from the conversion above
  
  # Save as a list to ensure consistent JSON array format (prevents auto_unbox issues)
  # This ensures both single and multi-item lists are stored as JSON arrays
  items_list <- as.list(items)
  
  result <- .rs.update_ai_setting(list_type, items_list)
  return(result)
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
.rs.addFunction("get_ai_rules_path", function() {
  # Get the path to the AI rules file
  base_ai_dir <- .rs.get_ai_base_dir()
  rules_path <- file.path(base_ai_dir, "ai_rules.json")
  return(rules_path)
})

.rs.addFunction("load_ai_rules", function() {
  # Load AI rules from persistent storage
  rules_path <- .rs.get_ai_rules_path()
  
  # Create default rules if file doesn't exist
  if (!file.exists(rules_path)) {
    return(character(0))  # Return empty character vector
  }
  
  tryCatch({
    # Read rules from file
    rules_json <- readLines(rules_path, warn = FALSE)
    rules <- jsonlite::fromJSON(paste(rules_json, collapse = ""), simplifyVector = TRUE)
    
    # Ensure we have a character vector
    if (is.null(rules) || !is.character(rules)) {
      rules <- character(0)
    }
    
    return(rules)
  }, error = function(e) {
    warning("Failed to load AI rules: ", e$message, ". Using empty rules.")
    return(character(0))
  })
})

.rs.addFunction("save_ai_rules", function(rules) {
  # Save AI rules to persistent storage
  rules_path <- .rs.get_ai_rules_path()
  
  # Ensure directory exists
  dir.create(dirname(rules_path), recursive = TRUE, showWarnings = FALSE)
  
  tryCatch({
    # Ensure rules is a character vector
    if (is.null(rules) || (!is.character(rules) && !is.list(rules))) {
      rules <- character(0)
    }
    
    # Convert to character vector if it's a list
    if (is.list(rules)) {
      rules <- unlist(rules, use.names = FALSE)
      rules <- as.character(rules)
    }
    
    # Write rules to file as JSON array
    rules_json <- jsonlite::toJSON(rules, auto_unbox = FALSE)
    writeLines(rules_json, rules_path)
    return(TRUE)
  }, error = function(e) {
    warning("Failed to save AI rules: ", e$message)
    return(FALSE)
  })
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