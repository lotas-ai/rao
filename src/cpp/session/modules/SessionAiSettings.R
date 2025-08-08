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
  openai_models <- c("gpt-5-mini")
  
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
    return(c("claude-sonnet-4-20250514", "gpt-5-mini"))
  } else if (provider == "openai") {
    return(c("gpt-5-mini"))
  } else if (provider == "anthropic") {
    return(c("claude-sonnet-4-20250514"))
  }
  return(c())
})

.rs.addFunction("get_model_display_names", function() {
  models <- .rs.get_available_models()
  display_names <- c(
    "claude-sonnet-4-20250514 (Superior coding and analysis - recommended)",
    "gpt-5-mini (Reasoning tier)"
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
      web_search_enabled = FALSE
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
    
    return(settings)
  }, error = function(e) {
    warning("Failed to load AI settings: ", e$message, ". Using defaults.")
    return(list(
      selected_model = "claude-sonnet-4-20250514",
      working_directory = NULL,
      temperature = 0.5,
      security_mode = "secure",
      web_search_enabled = FALSE
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