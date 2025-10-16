#
# SessionAiLocalBackend.R
#
# Copyright (C) 2025 by Lotas Inc.
#

# ============================================================================
# BYOK (Bring Your Own Key) Local Backend Proxy
# ============================================================================

# Initialize local backend environment
if (!.rs.hasVar("local_backend_env")) {
   .rs.setVar("local_backend_env", new.env(parent = emptyenv()))
}

.rs.addFunction("ai.startLocalBackendProxy", function() {
   # Ensure required packages are installed (including processx)
   .rs.check_required_packages()
   
   local_backend <- .rs.getVar("local_backend_env")
   
   if (!is.null(local_backend$proxy_url)) {
      return(local_backend$proxy_url)
   }
   
   # Get the local backend path from environment variable (like ripgrep)
   backend_base_dir <- Sys.getenv("RSTUDIO_LOCAL_BACKEND")
   
   if (backend_base_dir == "" || !dir.exists(backend_base_dir)) {
      stop("Local backend binaries not found at: ", backend_base_dir)
   }
   
   # The binaries are in a subdirectory (like ripgrep's version directory)
   backend_bin_dir <- file.path(backend_base_dir, "rao-local-backend")
   if (!dir.exists(backend_bin_dir)) {
      # Fallback: check if binaries are directly in the base directory
      backend_bin_dir <- backend_base_dir
   }
   
   # Determine platform-specific executable name
   if (.Platform$OS.type == "unix" && grepl("darwin", R.version$os)) {
      # macOS: Check for arm64 first, then x64
      if (R.version$arch == "aarch64" || R.version$arch == "arm64") {
         backend_exe <- file.path(backend_bin_dir, "rao-local-backend-macos-arm64")
      } else {
         backend_exe <- file.path(backend_bin_dir, "rao-local-backend-macos-x64")
      }
   } else if (.Platform$OS.type == "windows") {
      backend_exe <- file.path(backend_bin_dir, "rao-local-backend-win-x64.exe")
   } else {
      # Linux
      backend_exe <- file.path(backend_bin_dir, "rao-local-backend-linux-x64")
   }
   
   if (!file.exists(backend_exe)) {
      stop(
         "Local backend executable not found at: ", backend_exe, "\n",
         "BYOK requires a properly built Rao installation.\n",
         "Available files in ", backend_bin_dir, ":\n",
         paste(list.files(backend_bin_dir), collapse = "\n")
      )
   }
   
   # Start the standalone executable (no Node.js needed!)
   # Match Copilot's process options: exitWithParent, proper cleanup
   proc <- processx::process$new(
      backend_exe,
      stdin = "|",              # Provide stdin so process.stdin.resume() works in Node.js
      stdout = "|",
      stderr = "|",
      cleanup = TRUE,           # Kill on R exit (like Copilot's exitWithParent)
      cleanup_tree = TRUE       # Kill child processes too
   )
   
   # Wait for the proxy URL to be printed (matches vscode pattern)
   timeout <- 10
   start_time <- Sys.time()
   proxy_url <- NULL
   
   while (is.null(proxy_url) && as.numeric(Sys.time() - start_time) < timeout) {
      if (proc$is_alive()) {
         output <- proc$read_output_lines()
         for (line in output) {
            if (startsWith(line, "PROXY_URL:")) {
               proxy_url <- substring(line, 11)
               break
            }
         }
      } else {
         err <- proc$read_error()
         stop("Local backend process died: ", err)
      }
      Sys.sleep(0.1)
   }
   
   if (is.null(proxy_url)) {
      all_output <- proc$read_all_output()
      all_error <- proc$read_all_error()
      proc$kill()
      stop("Failed to start local backend proxy within timeout. Output: ", all_output, " Error: ", all_error)
   }
   
   local_backend <- .rs.getVar("local_backend_env")
   local_backend$proxy_url <- proxy_url
   local_backend$proxy_process <- proc
   local_backend$proxy_pid <- proc$get_pid()
   
   # Register finalizer for cleanup (like Copilot's onExit callback)
   # This ensures the process is killed when the environment is garbage collected
   reg.finalizer(local_backend, function(e) {
      if (!is.null(e$proxy_process) && e$proxy_process$is_alive()) {
         e$proxy_process$kill()
      }
   }, onexit = TRUE)
   
   return(proxy_url)
})

.rs.addFunction("ai.stopLocalBackendProxy", function() {
   if (!.rs.hasVar("local_backend_env")) {
      return(FALSE)
   }
   
   local_backend <- .rs.getVar("local_backend_env")
   
   # Kill the process if it's running (like Copilot's stopAgent)
   if (!is.null(local_backend$proxy_process)) {
      if (local_backend$proxy_process$is_alive()) {
         local_backend$proxy_process$kill()
      }
      local_backend$proxy_process <- NULL
      local_backend$proxy_pid <- NULL
   }
   local_backend$proxy_url <- NULL
   
   return(TRUE)
})

.rs.addFunction("ai.getLocalBackendProxyUrl", function() {
   if (!.rs.hasVar("local_backend_env")) {
      return("")
   }
   
   local_backend <- .rs.getVar("local_backend_env")
   
   # Verify the process is still alive (like Copilot checks s_agentPid)
   if (!is.null(local_backend$proxy_process)) {
      if (!local_backend$proxy_process$is_alive()) {
         # Process died, clear state
         local_backend$proxy_url <- NULL
         local_backend$proxy_process <- NULL
         local_backend$proxy_pid <- NULL
         return("")
      }
   }
   
   return(local_backend$proxy_url %||% "")
})

# ============================================================================
# BYOK Settings Management (mirrors vscode's isBYOKEnabled)
# ============================================================================

.rs.addFunction("ai.isBYOKEnabled", function(provider) {
   # Check AI setting - must match Java preference name
   # Only check the enabled flag, not whether key exists
   # This allows models to be available even if key isn't set yet
   # Note: get_ai_pref automatically adds "ai_" prefix, so we don't include it here
   setting_name <- paste0("byok_", provider, "_enabled")
   enabled <- .rs.get_ai_pref(setting_name, FALSE)
   
   if (is.null(enabled)) {
      return(FALSE)
   }
   
   return(enabled)
})

# ============================================================================
# Secure API Key Storage (exact same mechanism as main rao API key)
# ============================================================================

.rs.addFunction("ai.getBYOKApiKey", function(provider) {
   # Check in-memory key first (for immediate use after setting)
   mem_var <- paste0(".rs.byok_", provider, "_key")
   stored_key <- if (exists(mem_var, envir = .GlobalEnv)) get(mem_var, envir = .GlobalEnv) else NULL
   if (!is.null(stored_key)) {
      return(stored_key)
   }
   
   # Check persistent storage - must match Java UserState name
   key_name <- paste0("ai_byok_", provider, "_api_key")
   persistent_key <- .rs.readUserState(key_name)
   if (!is.null(persistent_key) && nchar(persistent_key) > 0) {
      # Load into memory for performance and return
      assign(mem_var, persistent_key, envir = .GlobalEnv)
      return(persistent_key)
   }
   
   return(NULL)
})

.rs.addFunction("ai.setBYOKApiKey", function(provider, api_key) {
   key_name <- paste0("ai_byok_", provider, "_api_key")
   
   # Store persistently using the same infrastructure as main API key
   .rs.writeUserState(key_name, api_key)
   
   # Also set in memory for immediate use
   mem_var <- paste0(".rs.byok_", provider, "_key")
   assign(mem_var, api_key, envir = .GlobalEnv)
})

.rs.addFunction("ai.setBYOKEnabled", function(provider, enabled) {
   # Save the enabled state using the established pattern - must match Java preference name
   # Note: set_ai_pref automatically adds "ai_" prefix, so we don't include it here
   setting_name <- paste0("byok_", provider, "_enabled")
   .rs.set_ai_pref(setting_name, enabled)
})

.rs.addFunction("ai.clearBYOKApiKey", function(provider) {
   key_name <- paste0("ai_byok_", provider, "_api_key")
   
   # Clear from persistent storage
   .rs.writeUserState(key_name, "")
   
   # Clear from memory (set to NULL like rao key does)
   mem_var <- paste0(".rs.byok_", provider, "_key")
   assign(mem_var, NULL, envir = .GlobalEnv)
})

.rs.addFunction("ai.hasBYOKApiKey", function(provider) {
   # Check if API key exists (same logic as in isBYOKEnabled)
   api_key <- .rs.ai.getBYOKApiKey(provider)
   return(!is.null(api_key) && nchar(api_key) > 0)
})

# ============================================================================
# Local Backend URL Provider (for routing in SessionAiAPI.R)
# ============================================================================

.rs.addFunction("ai.getLocalBackendUrl", function() {
   # Ensure proxy is started
   proxy_url <- .rs.ai.getLocalBackendProxyUrl()
   
   if (is.null(proxy_url) || proxy_url == "") {
      proxy_url <- .rs.ai.startLocalBackendProxy()
   }
   
   # Verify proxy is actually running
   local_backend <- .rs.getVar("local_backend_env")
   if (!is.null(local_backend$proxy_process)) {
      is_alive <- local_backend$proxy_process$is_alive()
      if (!is_alive) {
         local_backend$proxy_url <- NULL
         proxy_url <- .rs.ai.startLocalBackendProxy()
      }
   }
   
   return(proxy_url)
})

# ============================================================================
# BYOK Request Builder (adds BYOK keys to request)
# ============================================================================

.rs.addFunction("ai.addBYOKKeysToRequest", function(request_data, provider) {
   # Get API key
   api_key <- .rs.ai.getBYOKApiKey(provider)
   if (is.null(api_key) || api_key == "") {
      stop("BYOK API key not found for provider: ", provider)
   }
   
   # Add byok_keys field to request
   if (is.null(request_data$byok_keys)) {
      request_data$byok_keys <- list()
   }
   
   # Add API key to request (matches vscode pattern)
   if (provider == "sagemaker") {
      # For SageMaker, parse AWS credentials JSON
      tryCatch({
         request_data$byok_keys$aws <- jsonlite::fromJSON(api_key)
         
         # Also get SageMaker config from AI settings
         sagemaker_endpoint <- .rs.get_ai_state("sagemaker_endpoint", "")
         sagemaker_region <- .rs.get_ai_state("sagemaker_region", "us-east-1")
         
         request_data$byok_keys$sagemaker <- list(
            endpointName = sagemaker_endpoint,
            region = sagemaker_region
         )
      }, error = function(e) {
         stop("Invalid AWS credentials format: ", e$message)
      })
   } else {
      request_data$byok_keys[[provider]] <- api_key
   }
   
   return(request_data)
})

# ============================================================================
# BYOK Provider Check (determines if request should use local backend)
# ============================================================================

.rs.addFunction("ai.shouldUseBYOK", function(provider, model) {
   # Check if BYOK is enabled for this provider
   # This is called before each API request to determine routing
   
   # If provider is already set, just check if BYOK is enabled for it
   if (!is.null(provider) && provider != "") {
      result <- .rs.ai.isBYOKEnabled(provider)
      return(result)
   }
   
   # If provider not set, try to map from model
   if (!is.null(model) && model != "") {
      if (grepl("^claude-", model)) {
         provider <- "anthropic"
      } else if (grepl("^gpt-", model) || grepl("^o1-", model)) {
         provider <- "openai"
      } else if (grepl("^sagemaker:", model)) {
         provider <- "sagemaker"
      } else if (grepl("sagemaker", model, ignore.case = TRUE)) {
         provider <- "sagemaker"
      } else {
         return(FALSE)
      }
      
      result <- .rs.ai.isBYOKEnabled(provider)
      return(result)
   }
   
   # Neither provider nor model is set
   return(FALSE)
})

# ============================================================================
# SageMaker Configuration Management
# ============================================================================

.rs.addFunction("ai.setSageMakerEndpoint", function(endpoint) {
   .rs.set_ai_state("sagemaker_endpoint", endpoint)
   return(TRUE)
})

.rs.addFunction("ai.getSageMakerEndpoint", function() {
   endpoint <- .rs.get_ai_state("sagemaker_endpoint", "")
   return(endpoint)
})

.rs.addFunction("ai.setSageMakerRegion", function(region) {
   .rs.set_ai_state("sagemaker_region", region)
   return(TRUE)
})

.rs.addFunction("ai.getSageMakerRegion", function() {
   region <- .rs.get_ai_state("sagemaker_region", "us-east-1")
   return(region)
})

# JSON RPC handlers for SageMaker configuration
.rs.addJsonRpcHandler("set_sagemaker_endpoint", function(endpoint) {
   return(.rs.ai.setSageMakerEndpoint(endpoint))
})

.rs.addJsonRpcHandler("get_sagemaker_endpoint", function() {
   return(.rs.ai.getSageMakerEndpoint())
})

.rs.addJsonRpcHandler("set_sagemaker_region", function(region) {
   return(.rs.ai.setSageMakerRegion(region))
})

.rs.addJsonRpcHandler("get_sagemaker_region", function() {
   return(.rs.ai.getSageMakerRegion())
})

