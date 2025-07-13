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
  if (!file.exists(settings_path)) {
    return(list(
      selected_model = "claude-sonnet-4-20250514",
      working_directory = NULL,
      temperature = 0.5
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
    
    return(settings)
  }, error = function(e) {
    warning("Failed to load AI settings: ", e$message, ". Using defaults.")
    return(list(
      selected_model = "claude-sonnet-4-20250514",
      working_directory = NULL,
      temperature = 0.5
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
  return(settings[[key]] %||% default_value)
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