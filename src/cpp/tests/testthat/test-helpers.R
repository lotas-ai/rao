#
# test-helpers.R
#
# Shared test utilities and mocking functions to prevent environment pollution
# across test files. All test files should use these utilities instead of
# directly manipulating .GlobalEnv.
#

library(testthat)

# Centralized mock storage - isolated from global environment
.test_mocks <- new.env(parent = emptyenv())

# Core .rs function mocks that multiple test files need - ONLY HELPER FUNCTIONS
create_core_rs_mocks <- function() {
  list(
    ".rs.scalar" = function(x) { 
      class(x) <- 'rs.scalar'
      x 
    },
    ".rs.getVar" = function(name) {
      if (exists(name, envir = .test_mocks)) {
        get(name, envir = .test_mocks)
      } else {
        # Return appropriate default values for common variables
        if (name == "ai_in_error" || name == "ai_cancelled") {
          return(FALSE)
        }
        return(NULL)
      }
    },
    ".rs.setVar" = function(name, value) {
      assign(name, value, envir = .test_mocks)
      invisible(TRUE)
    },
    ".rs.hasVar" = function(name) {
      exists(name, envir = .test_mocks)
    },
    ".rs.addFunction" = function(name, fn) {
      assign(name, fn, envir = .GlobalEnv)
      invisible(NULL)
    },
    # Mock the conversation variable manager functions directly
    ".rs.initializeConversationVariableCache" = function() {
      .rs.setVar("conversationVariableCache", new.env(parent = emptyenv()))
      .rs.setVar("currentCachedConversationId", NULL)
      return(TRUE)
    },
    ".rs.initializeConversationDefaultsInCache" = function() {
      .rs.initializeConversationVariableCache()
      cache <- .rs.getVar("conversationVariableCache")
      assign("active_api_request_id", NULL, envir = cache)
      assign("ai_cancelled", FALSE, envir = cache)
      assign("function_call_depth", 0, envir = cache)
      assign("last_function_was_edit_file", FALSE, envir = cache)
      assign("ai_in_error", FALSE, envir = cache)
      assign("contextItems", list(), envir = cache)
      return(TRUE)
    },
    ".rs.getConversationSpecificVariables" = function() {
      c("active_api_request_id", "ai_cancelled", "function_call_depth", 
        "last_function_was_edit_file", "ai_in_error", "contextItems")
    },
    ".rs.getConversationVar" = function(varName, defaultValue = NULL) {
      conv_vars <- .rs.getConversationSpecificVariables()
      if (!varName %in% conv_vars) {
        return(.rs.getVar(varName))
      }
      
      if (!.rs.hasVar("conversationVariableCache")) {
        .rs.initializeConversationDefaultsInCache()
      }
      
      cache <- .rs.getVar("conversationVariableCache")
      if (exists(varName, envir = cache)) {
        return(get(varName, envir = cache))
      }
      
      if (!is.null(defaultValue)) return(defaultValue)
      if (varName == "ai_cancelled") return(FALSE)
      if (varName == "function_call_depth") return(0)
      if (varName == "last_function_was_edit_file") return(FALSE)
      if (varName == "ai_in_error") return(FALSE)
      if (varName == "contextItems") return(list())
      return(NULL)
    },
    ".rs.setConversationVar" = function(varName, value) {
      conv_vars <- .rs.getConversationSpecificVariables()
      if (!varName %in% conv_vars) {
        .rs.setVar(varName, value)
        return(TRUE)
      }
      
      if (!.rs.hasVar("conversationVariableCache")) {
        .rs.initializeConversationDefaultsInCache()
      }
      
      cache <- .rs.getVar("conversationVariableCache")
      assign(varName, value, envir = cache)
      return(TRUE)
    },
    ".rs.ensureConversationVariablesLoaded" = function(conversation_id) {
      .rs.initializeConversationVariableCache()
      .rs.setVar("currentCachedConversationId", conversation_id)
      .rs.initializeConversationDefaultsInCache()
      return(TRUE)
    },
    ".rs.saveConversationVariablesToFile" = function(conversation_id) {
      .rs.initializeConversationVariableCache()
      baseDir <- file.path(.rs.get_ai_base_dir(), paste0("conversation_", conversation_id))
      dir.create(baseDir, recursive = TRUE, showWarnings = FALSE)
      varsFile <- file.path(baseDir, "conversation_vars.rds")
      cache <- .rs.getVar("conversationVariableCache")
      varsValues <- extract_cache_vars(cache)
      saveRDS(varsValues, file = varsFile)
      return(TRUE)
    },
    ".rs.loadConversationVariablesFromFile" = function(conversation_id) {
      .rs.initializeConversationVariableCache()
      baseDir <- file.path(.rs.get_ai_base_dir(), paste0("conversation_", conversation_id))
      varsFile <- file.path(baseDir, "conversation_vars.rds")
      
      if (!file.exists(varsFile)) {
        .rs.initializeConversationDefaultsInCache()
        return(TRUE)
      }
      
      tryCatch({
        varsValues <- readRDS(varsFile)
        cache <- .rs.getVar("conversationVariableCache")
        for (varName in names(varsValues)) {
          assign(varName, varsValues[[varName]], envir = cache)
        }
      }, error = function(e) {
        warning("Failed to load conversation variables: ", e$message)
        .rs.initializeConversationDefaultsInCache()
      })
      return(TRUE)
    },
    ".rs.clearConversationVariables" = function() {
      .rs.initializeConversationVariableCache()
      cache <- .rs.getVar("conversationVariableCache")
      rm(list = ls(cache), envir = cache)
      .rs.initializeConversationDefaultsInCache()
      return(TRUE)
    },
    ".rs.completeDeferredConversationInit" = function() {
      if (.rs.hasVar("currentConversationIndex")) {
        conversation_id <- .rs.getVar("currentConversationIndex")
        .rs.ensureConversationVariablesLoaded(conversation_id)
      } else {
        .rs.setVar("currentConversationIndex", 1)
        .rs.ensureConversationVariablesLoaded(1)
      }
      return(TRUE)
    },
    ".rs.storeConversationVariables" = function(conversation_id) {
      .rs.saveConversationVariablesToFile(conversation_id)
    },
    ".rs.loadConversationVariables" = function(conversation_id) {
      .rs.loadConversationVariablesFromFile(conversation_id)
    },
    ".rs.initializeConversationDefaults" = function() {
      .rs.initializeConversationDefaultsInCache()
    },
    ".rs.getCurrentConversationIndex" = function() {
      # Always return 1 for tests - simple and reliable
      return(1L)
    },
    ".rs.setCurrentConversationIndex" = function(index) {
      if (!is.numeric(index) || index < 1) {
        stop("Conversation index must be a positive integer")
      }
             assign("currentConversationIndex", as.integer(index), envir = .test_mocks)
       return(TRUE)
     },
     ".rs.getNextMessageId" = function() {
       current <- if (exists("messageIdCounter", envir = .test_mocks)) {
         get("messageIdCounter", envir = .test_mocks)
       } else {
         0
       }
       assign("messageIdCounter", current + 1, envir = .test_mocks)
       return(as.integer(current + 1))
          },
     ".rs.findHighestConversationIndex" = function() {
       # Mock that looks for conversation dirs in test environment
       baseAiDir <- .rs.get_ai_base_dir()
       
       if (!dir.exists(baseAiDir)) {
         return(1)
       }
       
       allDirs <- list.dirs(baseAiDir, full.names = FALSE, recursive = FALSE)
       conversationDirs <- grep("^conversation_[0-9]+$", allDirs, value = TRUE)
       
       if (length(conversationDirs) == 0) {
         return(1)
       }
       
       indices <- as.integer(gsub("conversation_", "", conversationDirs))
       return(max(indices))
     },
     ".rs.getConversationTokens" = function() {
       return(100)  # Mock token count
     },
    ".rs.enqueClientEvent" = function(type, data) invisible(NULL),
    ".rs.updateConversationDisplay" = function() invisible(TRUE),
    ".rs.checkMessageForSymbols" = function(conversation) {
      # Mock function that returns relevant symbols found in conversation
      return(c("data_file", "analysis", "plot"))
    },
    ".rs.getOpenSourceDocuments" = function() {
      # Mock function that returns open source documents
      # Check if getSourceEditorContext is mocked to return an error/NULL/empty
      tryCatch({
        context <- .rs.api.getSourceEditorContext()
        if (is.null(context) || is.null(context$path) || nzchar(context$path) == 0 || 
            is.null(context$contents) || nzchar(context$contents) == 0) {
          return(list())
        }
        return(list(context))
      }, error = function(e) {
        return(list())
      })
    },
    # Mock terminal and console command functions to avoid warnings in tests
    ".rs.accept_terminal_command" = function(pendingId, script, messageId) {
      list(success = TRUE, message = "Terminal command accepted for testing")
    },
    ".rs.cancel_terminal_command" = function(pendingId) {
      return(TRUE)
    },
    ".rs.accept_console_command" = function(pendingId, script, messageId) {
      list(success = TRUE, message = "Console command accepted for testing")
    },
    ".rs.cancel_console_command" = function(pendingId) {
      return(TRUE)
    },
    # Mock tab and document-related functions
    ".rs.getTabFilePath" = function(tabId) {
      # Return a mock file path or NULL for testing
      if (tabId == "valid_tab") {
        return("/mock/path/to/file.R")
      }
      return(NULL)
    },
    ".rs.isConversationEmpty" = function(conversation_index) {
      # Look for actual conversation data first 
      if (exists("conversation_log_storage", envir = .test_mocks)) {
        log <- get("conversation_log_storage", envir = .test_mocks)
        if (length(log) >= 2) {
          return(FALSE)  # Has messages, not empty
        }
      }
      return(TRUE)  # Default to empty
    },
    # Mock file operations
    ".rs.copyCompleteHtmlToUserOnly" = function(completePath, userOnlyPath) {
      return(TRUE)
    },
    ".rs.delete_folder" = function(folderPath) {
      return(TRUE)
    },
    # Mock conversation log functions to prevent JSON corruption
    ".rs.readConversationLog" = function() {
      if (exists("conversation_log_storage", envir = .test_mocks)) {
        return(get("conversation_log_storage", envir = .test_mocks))
      }
      # Return a minimal empty conversation log
      return(list())
    },
    ".rs.writeConversationLog" = function(conversationLog) {
      # Ensure all related_to fields are numeric to prevent JSON corruption
      for (i in seq_along(conversationLog)) {
        if (!is.null(conversationLog[[i]]$related_to)) {
          # Convert list() or other non-numeric values to 0
          if (!is.numeric(conversationLog[[i]]$related_to) || length(conversationLog[[i]]$related_to) == 0) {
            conversationLog[[i]]$related_to <- 0
          }
        }
      }
      assign("conversation_log_storage", conversationLog, envir = .test_mocks)
      return(TRUE)
    },

    # Mock additional conversation functions - use proper createAiOperationResult structure
    ".rs.initialize_conversation" = function(query, request_id) {
      list(
        status = "success",
        timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
        data = list(
          conversation_index = 1,
          user_message_id = 100
        )
      )
    },
    ".rs.make_api_call" = function(conversation_index, model = NULL, preserve_symbols = TRUE, request_id) {
      list(
        status = "success",
        timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
        data = list(
          response = "Mock API response",
          conversation_index = if(is.null(conversation_index)) 1 else conversation_index
        )
      )
    },
    ".rs.process_function_call" = function(functionCall, relatedTo, requestId) {
      list(
        status = "success",
        timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
        data = list(message = "Mock function call processed")
      )
    },
    ".rs.process_single_function_call" = function(functionCall, relatedTo, requestId) {
      list(
        status = "success",
        timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
        data = list(message = "Mock single function call processed")
      )
    },
    ".rs.accept_edit_file_command" = function(edited_code, message_id, request_id) {
      return(TRUE)
    },
    # Mock createAiOperationResult function
    ".rs.createAiOperationResult" = function(status, data = NULL, error = NULL, functionCall = NULL) {
      result <- list(
        status = status,
        timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S")
      )
      
      if (!is.null(data)) {
        result$data <- data
      }
      
      if (!is.null(error)) {
        result$error <- error
      }
      
      if (!is.null(functionCall)) {
        result$function_call <- functionCall
      }
      
      return(result)
    },
    # Mock finalize functions that should return proper createAiOperationResult structure
    ".rs.finalize_console_command" = function(messageId, request_id) {
      list(
        status = "continue_and_display",
        timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
        data = list(
          message = "Console command finalized - returning control to main orchestrator",
          related_to_id = 122,
          conversation_index = 1
        )
      )
    },
    ".rs.finalize_terminal_command" = function(messageId, request_id) {
      list(
        status = "done",
        timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
        data = list(
          message = "Terminal command finalized - returning control to main orchestrator",
          related_to_id = 788,
          conversation_index = 1
        )
      )
    },
    # Mock working directory management functions
    ".rs.set_ai_working_directory" = function(directory) {
      list(success = TRUE, directory = directory)
    },
    ".rs.browse_directory" = function() {
      list(success = TRUE, directory = getwd())
    },
    ".rs.browse_for_file" = function() {
      return("/mock/path/to/file.R")
    },
    # Conversation name management functions - create basic storage instead of simple mocks
    ".rs.setConversationName" = function(conversation_index, name) {
      paths <- .rs.get_ai_file_paths()
      htmlDir <- dirname(paths$conversation_log_path)
      
      names_file <- file.path(htmlDir, "conversation_names.csv")
      
      names_df <- if (file.exists(names_file)) {
        read.csv(names_file, stringsAsFactors = FALSE)
      } else {
        data.frame(conversation_id = integer(0), name = character(0), stringsAsFactors = FALSE)
      }
      
      idx <- which(names_df$conversation_id == conversation_index)
      if (length(idx) > 0) {
        names_df$name[idx] <- name
      } else {
        names_df <- rbind(names_df, data.frame(
          conversation_id = conversation_index,
          name = name,
          stringsAsFactors = FALSE
        ))
      }
      
      write.csv(names_df, names_file, row.names = FALSE)
      return(TRUE)
    },
    ".rs.getConversationName" = function(conversation_index) {
      paths <- .rs.get_ai_file_paths()
      htmlDir <- dirname(paths$conversation_log_path)
      names_file <- file.path(htmlDir, "conversation_names.csv")
      
      if (!file.exists(names_file)) {
        return(paste0("Conversation ", conversation_index))
      }
      
      names_df <- read.csv(names_file, stringsAsFactors = FALSE)
      idx <- which(names_df$conversation_id == conversation_index)
      if (length(idx) > 0) {
        return(names_df$name[idx])
      }
      
      return(paste0("Conversation ", conversation_index))
    },
    ".rs.deleteConversationName" = function(conversation_index) {
      paths <- .rs.get_ai_file_paths()
      htmlDir <- dirname(paths$conversation_log_path)
      names_file <- file.path(htmlDir, "conversation_names.csv")
      
      if (!file.exists(names_file)) {
        return(TRUE)
      }
      
      names_df <- read.csv(names_file, stringsAsFactors = FALSE)
      idx <- which(names_df$conversation_id == conversation_index)
      if (length(idx) > 0) {
        names_df <- names_df[-idx, , drop = FALSE]
        write.csv(names_df, names_file, row.names = FALSE)
      }
      
      return(TRUE)
    },
    ".rs.listConversationNames" = function() {
      if (!exists("conversation_names_storage", envir = .test_mocks)) {
        assign("conversation_names_storage", data.frame(
          conversation_id = integer(),
          name = character(),
          stringsAsFactors = FALSE
        ), envir = .test_mocks)
      }
      
      return(get("conversation_names_storage", envir = .test_mocks))
    },
    ".rs.readConversationNames" = function() {
      if (!exists("conversation_names_storage", envir = .test_mocks)) {
        assign("conversation_names_storage", data.frame(
          conversation_id = integer(),
          name = character(),
          stringsAsFactors = FALSE
        ), envir = .test_mocks)
      }
      
      return(get("conversation_names_storage", envir = .test_mocks))
    },
    ".rs.getConversationNamesPath" = function() {
             test_dir <- file.path(tempdir(), "test_conversation_names")
       dir.create(test_dir, recursive = TRUE, showWarnings = FALSE)
       return(file.path(test_dir, "conversation_names.csv"))
     },
           # Additional conversation management functions - NEVER check packages in tests
      ".rs.checkRequiredPackages" = function() {
        return(invisible(TRUE))  # Always succeed, never check actual packages
      },
     ".rs.storeConversationVariables" = function(conversationId) {
       return(TRUE)
     },
     ".rs.loadConversationVariables" = function(conversationId) {
       return(TRUE)
     },
     ".rs.setCurrentConversationIndex" = function(index) {
       assign("currentConversationIndex", index, envir = .test_mocks)
       return(TRUE)
     },
     ".rs.readMessageButtons" = function() {
       return(data.frame(
         message_id = integer(),
         buttons_run = character(),
         next_button = character(),
         on_deck_button = character(),
         stringsAsFactors = FALSE
       ))
     },
    # Mock button management functions
    ".rs.markButtonAsRun" = function(messageId, buttonType) {
      return(TRUE)
    },
    # Add missing helper functions
    # Note: .rs.limitOutputText mock removed to allow real function testing
    ".rs.getFileNameForMessageId" = function(messageId, forDisplay = FALSE) {
      return(paste0("test_file_", messageId, ".R"))
    },
    ".rs.promoteOnDeckButton" = function(messageId) {
      return(TRUE)
    },
    ".rs.writeMessageButtons" = function(buttons) {
      return(TRUE)
    }
  )
}

# Backend communication mocks - for backend integration, NOT for testing backend functions themselves
create_backend_mocks <- function() {
  list(
    ".rs.getBackendConfig" = function() {
      list(
        url = "https://api.lotas.ai",
        environment = "production"
      )
    },
    ".rs.generateBackendAuth" = function(provider) {
      list(api_key = "test-api-key")
    },
    ".rs.checkBackendHealth" = function() TRUE,
    ".rs.sendBackendQuery" = function(requestType, conversation, ...) {
      switch(requestType,
        "ai_api_call" = list(response = "Backend processed the AI API call"),
        "generate_conversation_name" = "Generated Conversation Name",
        "function_call_response" = list(response = "Backend processed the function output"),
        list(response = "Backend processed the request")
      )
    },
    ".rs.backendGenerateConversationName" = function(messages) {
      return("Generated Conversation Name")
    }
  )
}

# Source editor mocks - for editor integration
create_editor_mocks <- function() {
  list(
    ".rs.api.getSourceEditorContext" = function() {
      list(
        id = "doc1",
        path = "/path/to/test.R",
        contents = "x <- 1\ny <- 2"
      )
    }
  )
}

# File system mocks
create_filesystem_mocks <- function() {
  # Create a consistent test directory that persists across calls
  test_lib_path <- file.path(tempdir(), "test_lib")
  dir.create(test_lib_path, recursive = TRUE, showWarnings = FALSE)
  
  list(
    ".libPaths" = function() test_lib_path
  )
}

# Helper function to extract variables from cache environment
extract_cache_vars <- function(cache) {
  varNames <- ls(cache)
  varsValues <- list()
  for (varName in varNames) {
    varsValues[[varName]] <- get(varName, envir = cache)
  }
  return(varsValues)
}

# Variable manager mocks - completely mock the conversation variable system
create_variable_manager_mocks <- function() {
  
  list(
    ".rs.initializeConversationVariableCache" = function() {
      .rs.setVar("conversationVariableCache", new.env(parent = emptyenv()))
      .rs.setVar("currentCachedConversationId", NULL)
      return(TRUE)
    },
    ".rs.initializeConversationDefaultsInCache" = function() {
      .rs.initializeConversationVariableCache()
      cache <- .rs.getVar("conversationVariableCache")
      assign("active_api_request_id", NULL, envir = cache)
      assign("ai_cancelled", FALSE, envir = cache)
      assign("function_call_depth", 0, envir = cache)
      assign("last_function_was_edit_file", FALSE, envir = cache)
      assign("ai_in_error", FALSE, envir = cache)
      assign("contextItems", list(), envir = cache)
      return(TRUE)
    },
    ".rs.getConversationSpecificVariables" = function() {
      c("active_api_request_id", "ai_cancelled", "function_call_depth", 
        "last_function_was_edit_file", "ai_in_error", "contextItems")
    },
    ".rs.getConversationVar" = function(varName, defaultValue = NULL) {
      conv_vars <- .rs.getConversationSpecificVariables()
      if (!varName %in% conv_vars) {
        return(.rs.getVar(varName))
      }
      
      if (!.rs.hasVar("conversationVariableCache")) {
        .rs.initializeConversationDefaultsInCache()
      }
      
      cache <- .rs.getVar("conversationVariableCache")
      if (exists(varName, envir = cache)) {
        return(get(varName, envir = cache))
      }
      
      if (!is.null(defaultValue)) return(defaultValue)
      if (varName == "ai_cancelled") return(FALSE)
      if (varName == "function_call_depth") return(0)
      if (varName == "last_function_was_edit_file") return(FALSE)
      if (varName == "ai_in_error") return(FALSE)
      if (varName == "contextItems") return(list())
      return(NULL)
    },
    ".rs.setConversationVar" = function(varName, value) {
      conv_vars <- .rs.getConversationSpecificVariables()
      if (!varName %in% conv_vars) {
        .rs.setVar(varName, value)
        return(TRUE)
      }
      
      if (!.rs.hasVar("conversationVariableCache")) {
        .rs.initializeConversationDefaultsInCache()
      }
      
      cache <- .rs.getVar("conversationVariableCache")
      assign(varName, value, envir = cache)
      return(TRUE)
    },
    ".rs.ensureConversationVariablesLoaded" = function(conversation_id) {
      .rs.initializeConversationVariableCache()
      .rs.setVar("currentCachedConversationId", conversation_id)
      .rs.initializeConversationDefaultsInCache()
      return(TRUE)
    },
    ".rs.saveConversationVariablesToFile" = function(conversation_id) {
      .rs.initializeConversationVariableCache()
      baseDir <- file.path(.rs.get_ai_base_dir(), paste0("conversation_", conversation_id))
      dir.create(baseDir, recursive = TRUE, showWarnings = FALSE)
      varsFile <- file.path(baseDir, "conversation_vars.rds")
      cache <- .rs.getVar("conversationVariableCache")
      varsValues <- extract_cache_vars(cache)
      saveRDS(varsValues, file = varsFile)
      return(TRUE)
    },
    ".rs.loadConversationVariablesFromFile" = function(conversation_id) {
      .rs.initializeConversationVariableCache()
      baseDir <- file.path(.rs.get_ai_base_dir(), paste0("conversation_", conversation_id))
      varsFile <- file.path(baseDir, "conversation_vars.rds")
      
      if (!file.exists(varsFile)) {
        .rs.initializeConversationDefaultsInCache()
        return(TRUE)
      }
      
      tryCatch({
        varsValues <- readRDS(varsFile)
        cache <- .rs.getVar("conversationVariableCache")
        for (varName in names(varsValues)) {
          assign(varName, varsValues[[varName]], envir = cache)
        }
      }, error = function(e) {
        warning("Failed to load conversation variables: ", e$message)
        .rs.initializeConversationDefaultsInCache()
      })
      return(TRUE)
    },
    ".rs.clearConversationVariables" = function() {
      .rs.initializeConversationVariableCache()
      cache <- .rs.getVar("conversationVariableCache")
      rm(list = ls(cache), envir = cache)
      .rs.initializeConversationDefaultsInCache()
      return(TRUE)
    },
    ".rs.completeDeferredConversationInit" = function() {
      if (.rs.hasVar("currentConversationIndex")) {
        conversation_id <- .rs.getVar("currentConversationIndex")
        .rs.ensureConversationVariablesLoaded(conversation_id)
      } else {
        .rs.setVar("currentConversationIndex", 1)
        .rs.ensureConversationVariablesLoaded(1)
      }
      return(TRUE)
    },
    ".rs.storeConversationVariables" = function(conversation_id) {
      .rs.saveConversationVariablesToFile(conversation_id)
    },
    ".rs.loadConversationVariables" = function(conversation_id) {
      .rs.loadConversationVariablesFromFile(conversation_id)
    },
    ".rs.initializeConversationDefaults" = function() {
      .rs.initializeConversationDefaultsInCache()
    }
  )
}

# Symbol index mocks - for symbol indexing tests
create_symbol_index_mocks <- function() {
  list(
    # Mock the R wrapper functions directly
    ".rs.buildSymbolIndex" = function(dir = getwd()) {
      # Validate directory
      if (!dir.exists(dir))
        stop("Directory does not exist: ", dir)
      
      # Mock successful symbol index build
      return(TRUE)
    },
    ".rs.findSymbol" = function(name) {
      # Validate
      if (!is.character(name) || length(name) != 1)
        stop("Symbol name must be a single character string")
      
      # Clean up hashtags in search string (for headers)
      if (grepl("#", name)) {
        name <- gsub("^\\s*#+\\s*|\\s*#+\\s*$", "", name)
      }
      
             # Trim leading and trailing whitespace
       name <- trimws(name)
       
       # Handle type filters like "symbol_name (type)"
       type_filter <- NULL
       if (grepl("\\([^)]+\\)$", name)) {
         # Extract type filter
         type_match <- regmatches(name, regexpr("\\(([^)]+)\\)$", name))
         type_filter <- gsub("[()]", "", type_match)
         # Remove type filter from name
         name <- trimws(gsub("\\s*\\([^)]+\\)$", "", name))
       }
       
       # Return mock symbol results based on the search term
       if (grepl("^(test_function|my_function|calculate_sum|setup_data|analyze_data|func1|func2|func3|simple_func|complex_func|multiline_func|data_processing|process_data|clean_dataset)$", name, ignore.case = TRUE)) {
         # Determine signature based on function name
         signature <- "function()"
         parents <- ""
         
         if (grepl("analyze_data|setup_data", name, ignore.case = TRUE)) {
           parents <- "analysis"
         }
         
         if (grepl("my_function", name, ignore.case = TRUE)) {
           signature <- "function(a, b = 10)"
         } else if (grepl("calculate_sum", name, ignore.case = TRUE)) {
           signature <- "function(x, y)"
         } else if (grepl("complex_func", name, ignore.case = TRUE)) {
           signature <- "function(x, y = 10, z = NULL, ...)"
         } else if (grepl("multiline_func", name, ignore.case = TRUE)) {
           signature <- "function(data, method = 'default', options = list())"
         } else if (grepl("setup_data", name, ignore.case = TRUE)) {
           signature <- "function()"
         } else if (grepl("analyze_data", name, ignore.case = TRUE)) {
           signature <- "function(df)"
         }
         
         result <- list(
           list(
             name = name,
             type = "function",
             file = "/test/sample.R",
             line_start = 1,
             line_end = 3,
             parents = parents,
             signature = signature
           )
                  )
       } else if (name == "test") {
         # Special handling for "test" symbol which can be both function and header
         result <- list()
         
         # Add function result if no type filter or if function filter
         if (is.null(type_filter) || type_filter == "function") {
           result[[length(result) + 1]] <- list(
             name = name,
             type = "function",
             file = "/test/test.R",
             line_start = 1,
             line_end = 1,
             parents = "",
             signature = "function()"
           )
         }
         
         # Add header result if no type filter or if header filter
         if (is.null(type_filter) || type_filter == "header") {
           result[[length(result) + 1]] <- list(
             name = name,
             type = "header1",
             file = "/test/docs.md",
             line_start = 1,
             line_end = 1,
             parents = ""
           )
         }
       } else if (grepl("Introduction|Data Analysis|Statistical|Results", name, ignore.case = TRUE)) {
        header_type <- if (grepl("Introduction|Results", name)) "header1" else "header2"
        result <- list(
          list(
            name = name,
            type = header_type,
            file = "/test/test.md",
            line_start = 1,
            line_end = 1,
            parents = ""
          )
        )
      } else if (grepl("TestClass|users|setup_environment", name, ignore.case = TRUE)) {
        # Handle different file types
        file_type <- if (grepl("TestClass", name)) "class" else if (grepl("users", name)) "table" else "function"
        result <- list(
          list(
            name = name,
            type = file_type,
            file = "/test/mixed.file",
            line_start = 1,
            line_end = 3,
            parents = ""
          )
        )
      } else if (grepl("\\.(png|jpg|gif)$", name, ignore.case = TRUE)) {
        result <- list(
          list(
            name = name,
            type = "image",
            file = paste0("/test/", name),
            line_start = 1,
            line_end = 1,
            parents = ""
          )
        )
      } else if (grepl("\\.(exe|bin)$", name, ignore.case = TRUE)) {
        result <- list(
          list(
            name = name,
            type = "binary",
            file = paste0("/test/", name),
            line_start = 1,
            line_end = 1,
            parents = ""
          )
        )
      } else if (grepl("\\.(txt|md)$", name, ignore.case = TRUE)) {
        result <- list(
          list(
            name = name,
            type = "file",
            file = paste0("/test/", name),
            line_start = 1,
            line_end = 1,
            parents = ""
          )
        )
      } else if (grepl("subdir", name, ignore.case = TRUE)) {
        result <- list(
          list(
            name = name,
            type = "directory",
            file = paste0("/test/", name),
            line_start = 1,
            line_end = 1,
            parents = ""
          )
        )
      } else {
        result <- list() # No matches found
      }
      
      # Return the result with a nice class
      class(result) <- c("rs_symbols", class(result))
      return(result)
    },
    ".rs.getAllSymbols" = function() {
      # Return a list of all mock symbols
      result <- list(
        list(name = "test_function", type = "function", file = "/test/sample.R", line_start = 1, line_end = 3, parents = "", signature = "function()"),
        list(name = "func1", type = "function", file = "/test/symbols.R", line_start = 1, line_end = 1, parents = "", signature = "function()"),
        list(name = "func2", type = "function", file = "/test/symbols.R", line_start = 2, line_end = 2, parents = "", signature = "function()"),
        list(name = "func3", type = "function", file = "/test/symbols.R", line_start = 3, line_end = 3, parents = "", signature = "function()"),
        list(name = "Introduction", type = "header1", file = "/test/test.md", line_start = 1, line_end = 1, parents = "")
      )
      
      # Return the result with a nice class
      class(result) <- c("rs_symbols", class(result))
      return(result)
    },
    ".rs.searchSymbolsInText" = function(text) {
      # Simple mock that finds some symbols based on text content
      matches <- list()
      
      if (grepl("data_processing|process|analysis", text, ignore.case = TRUE)) {
        matches[[length(matches) + 1]] <- list(
          name = "data_processing",
          type = "function",
          file = "/test/functions.R",
          line_start = 1,
          line_end = 1,
          parents = ""
        )
      }
      
      if (grepl("Data Processing|Guide", text, ignore.case = TRUE)) {
        matches[[length(matches) + 1]] <- list(
          name = "Data Processing Guide",
          type = "header1",
          file = "/test/docs.md",
          line_start = 1,
          line_end = 1,
          parents = ""
        )
      }
      
      # Return the result with a nice class
      if (length(matches) > 0) {
        class(matches) <- c("rs_symbols", class(matches))
        return(matches)
      } else {
        return(NULL)
      }
    },
    ".rs.ensureSymbolIndexForAISearch" = function(dir = getwd()) {
      # Make sure path is absolute
      if (!dir.exists(dir)) {
        return(FALSE)
      }
      
      dir <- normalizePath(dir, mustWork = TRUE)
      
      # Mock successful index building
      return(TRUE)
    },
    ".Call" = function(name, ...) {
      if (name == "rs_buildSymbolIndex") {
        # Mock successful symbol index build
        return(TRUE)
      } else if (name == "rs_findSymbol") {
        symbol_name <- list(...)[[1]]
        
        # Handle type filters like "symbol_name (type)"
        type_filter <- NULL
        if (grepl("\\([^)]+\\)$", symbol_name)) {
          # Extract type filter
          type_match <- regmatches(symbol_name, regexpr("\\(([^)]+)\\)$", symbol_name))
          type_filter <- gsub("[()]", "", type_match)
          # Remove type filter from name
          symbol_name <- trimws(gsub("\\s*\\([^)]+\\)$", "", symbol_name))
        }
        
        # Return mock symbol results based on the search term
        if (grepl("^(test_function|my_function|calculate_sum|setup_data|analyze_data|func1|func2|func3|simple_func|complex_func|multiline_func|data_processing|process_data|clean_dataset)$", symbol_name, ignore.case = TRUE)) {
          # Determine signature based on function name
          signature <- "function()"
          parents <- ""
          
          if (grepl("analyze_data|setup_data", symbol_name, ignore.case = TRUE)) {
            parents <- "analysis"
          }
          
          if (grepl("my_function", symbol_name, ignore.case = TRUE)) {
            signature <- "function(a, b = 10)"
          } else if (grepl("calculate_sum", symbol_name, ignore.case = TRUE)) {
            signature <- "function(x, y)"
          } else if (grepl("complex_func", symbol_name, ignore.case = TRUE)) {
            signature <- "function(x, y = 10, z = NULL, ...)"
          } else if (grepl("multiline_func", symbol_name, ignore.case = TRUE)) {
            signature <- "function(data, method = 'default', options = list())"
          } else if (grepl("setup_data", symbol_name, ignore.case = TRUE)) {
            signature <- "function()"
          } else if (grepl("analyze_data", symbol_name, ignore.case = TRUE)) {
            signature <- "function(df)"
          }
          
          return(list(
            list(
              name = symbol_name,
              type = "function",
              file = "/test/sample.R",
              line_start = 1,
              line_end = 3,
              parents = parents,
              signature = signature
            )
          ))
        } else if (symbol_name == "test") {
          # Special handling for "test" symbol which can be both function and header
          result <- list()
          
          # Add function result if no type filter or if function filter
          if (is.null(type_filter) || type_filter == "function") {
            result[[length(result) + 1]] <- list(
              name = symbol_name,
              type = "function",
              file = "/test/test.R",
              line_start = 1,
              line_end = 1,
              parents = "",
              signature = "function()"
            )
          }
          
          # Add header result if no type filter or if header filter
          if (is.null(type_filter) || type_filter == "header") {
            result[[length(result) + 1]] <- list(
              name = symbol_name,
              type = "header1",
              file = "/test/docs.md",
              line_start = 1,
              line_end = 1,
              parents = ""
            )
          }
          
          return(result)
        } else if (grepl("Introduction|Data Analysis|Statistical|Results", symbol_name, ignore.case = TRUE)) {
          header_type <- if (grepl("Introduction|Results", symbol_name)) "header1" else "header2"
          return(list(
            list(
              name = symbol_name,
              type = header_type,
              file = "/test/test.md",
              line_start = 1,
              line_end = 1,
              parents = ""
            )
          ))
        } else if (grepl("TestClass|users|setup_environment", symbol_name, ignore.case = TRUE)) {
          # Handle different file types
          file_type <- if (grepl("TestClass", symbol_name)) "class" else if (grepl("users", symbol_name)) "table" else "function"
          return(list(
            list(
              name = symbol_name,
              type = file_type,
              file = "/test/mixed.file",
              line_start = 1,
              line_end = 3,
              parents = ""
            )
          ))
        } else if (grepl("\\.(png|jpg|gif)$", symbol_name, ignore.case = TRUE)) {
          return(list(
            list(
              name = symbol_name,
              type = "image",
              file = paste0("/test/", symbol_name),
              line_start = 1,
              line_end = 1,
              parents = ""
            )
          ))
        } else if (grepl("\\.(exe|bin)$", symbol_name, ignore.case = TRUE)) {
          return(list(
            list(
              name = symbol_name,
              type = "binary",
              file = paste0("/test/", symbol_name),
              line_start = 1,
              line_end = 1,
              parents = ""
            )
          ))
        } else if (grepl("\\.(txt|md)$", symbol_name, ignore.case = TRUE)) {
          return(list(
            list(
              name = symbol_name,
              type = "file",
              file = paste0("/test/", symbol_name),
              line_start = 1,
              line_end = 1,
              parents = ""
            )
          ))
        } else if (grepl("subdir", symbol_name, ignore.case = TRUE)) {
          return(list(
            list(
              name = symbol_name,
              type = "directory",
              file = paste0("/test/", symbol_name),
              line_start = 1,
              line_end = 1,
              parents = ""
            )
          ))
        } else {
          return(list()) # No matches found
        }
      } else if (name == "rs_getAllSymbols") {
        # Return a list of all mock symbols
        return(list(
          list(name = "test_function", type = "function", file = "/test/sample.R", line_start = 1, line_end = 3, parents = "", signature = "function()"),
          list(name = "func1", type = "function", file = "/test/symbols.R", line_start = 1, line_end = 1, parents = "", signature = "function()"),
          list(name = "func2", type = "function", file = "/test/symbols.R", line_start = 2, line_end = 2, parents = "", signature = "function()"),
          list(name = "func3", type = "function", file = "/test/symbols.R", line_start = 3, line_end = 3, parents = "", signature = "function()"),
          list(name = "Introduction", type = "header1", file = "/test/test.md", line_start = 1, line_end = 1, parents = "")
        ))
      } else {
        stop("Unknown C++ function: ", name)
      }
    }
  )
}

# Attachment backend mocks - for attachment upload/storage
create_attachment_backend_mocks <- function() {
  list(
    ".rs.saveAttachmentViaBackend" = function(...) {
      list(success = TRUE, file_id = "test-file-id")
    }
  )
}

# Button helper mocks
create_button_helper_mocks <- function() {
  list(
    ".rs.getFileNameForMessageId" = function(messageId) {
      paste0("test_file_", messageId, ".R")
    }
  )
}

# Context management mocks
create_context_mocks <- function() {
  list(
    ".rs.add_context_item" = function(path) {
      # Mock the behavior of adding a context item
      if (is.null(path) || !is.character(path) || length(path) == 0 || nchar(path) == 0) {
        return(FALSE)
      }
      return(TRUE)
    },
    ".rs.add_context_lines" = function(path, startLine, endLine) {
      # Mock the behavior of adding context lines
      if (is.null(path) || !is.character(path) || length(path) == 0 || nchar(path) == 0) {
        return(FALSE)
      }
      return(TRUE)
    },
    ".rs.get_context_items" = function() {
      # Mock returning some context items
      return(c("/path/to/context1.R", "/path/to/context2.R"))
    },
    ".rs.remove_context_item" = function(path) {
      # Mock the behavior of removing a context item
      return(TRUE)
    },
    ".rs.clear_context_items" = function() {
      # Mock the behavior of clearing context items - return TRUE instead of NULL
      return(TRUE)
    }
  )
}

# Test conversation data mocks - for display tests that need sample conversation data
create_test_conversation_mocks <- function() {
  list(
    # Only provide display-related conversation data, NOT the actual IO functions
    ".rs.getTestConversationData" = function() {
      list(
        messages = data.frame(
          id = c(1, 2, 3),
          type = c("user", "assistant", "user"),
          text = c("Hello", "Hi there!", "How are you?"),
          timestamp = c("2025-01-01 10:00:00", "2025-01-01 10:00:01", "2025-01-01 10:00:02"),
          stringsAsFactors = FALSE
        )
      )
    },
    ".rs.getTestConversationLog" = function() {
      list(
        list(id = 1, role = "user", content = "Hello"),
        list(id = 2, role = "assistant", content = "Hi there!"),
        list(id = 3, role = "user", content = "How are you?")
      )
    }
  )
}

# Isolated conversation display mocks - only for display tests that need actual readConversation override
create_conversation_display_mocks <- function() {
  list(

    ".rs.readConversationLog" = function() {
      list(
        list(id = 1, role = "user", content = "Hello"),
        list(id = 2, role = "assistant", content = "Hi there!"),
        list(id = 3, role = "user", content = "How are you?")
      )
    }
  )
}

# Display utility mocks - these functions don't exist in the real code but are expected by tests
create_display_utility_mocks <- function() {
  list(
    ".rs.escapeHtml" = function(text) {
      if (is.null(text)) return("")
      # Simple HTML escaping
      text <- gsub("&", "&amp;", text, fixed = TRUE)
      text <- gsub("<", "&lt;", text, fixed = TRUE)
      text <- gsub(">", "&gt;", text, fixed = TRUE)
      text <- gsub("\"", "&quot;", text, fixed = TRUE)
      text <- gsub("'", "&#39;", text, fixed = TRUE)
      return(text)
    },
    ".rs.formatTimestamp" = function(timestamp) {
      if (is.null(timestamp)) return("")
      return(format(as.POSIXct(timestamp), "%Y-%m-%d %H:%M:%S"))
    },
    ".rs.renderMessage" = function(message) {
      if (is.null(message)) return("")
      return(paste0('<div class="message">', message, '</div>'))
    },
    ".rs.addMessageButtons" = function(messageId, buttons) {
      return(TRUE)
    },
    ".rs.generateFullHtml" = function(content) {
      return(paste0('<!DOCTYPE html><html><body>', content, '</body></html>'))
    },
    ".rs.getConversationDisplay" = function() {
      return('<div class="conversation">Mock conversation display</div>')
    },
    ".rs.displayConversationList" = function() {
      return(list(success = TRUE))
    },
    ".rs.formatMessageForDisplay" = function(message) {
      if (is.null(message)) return("")
      return(paste0('<p>', message, '</p>'))
    },
    ".rs.getFunctionCallTypeForMessage" = function(messageId, conversationLog = NULL) {
      # Mock function to identify function call types
      if (is.null(conversationLog)) return(NULL)
      for (entry in conversationLog) {
        if (!is.null(entry$id) && entry$id == messageId) {
          if (!is.null(entry$function_call) && !is.null(entry$function_call$name)) {
            return(entry$function_call$name)
          }
        }
      }
      return(NULL)
    }
  )
}

# Display data mocks - for providing test data to display functions
  create_display_data_mocks <- function() {
    list(
      # Only provide mocks when specifically needed for display tests
      # Don't override the actual IO functions unless explicitly requested
      ".rs.get_ai_file_paths" = function() {
        test_dir <- file.path(tempdir(), "test_ai_display", "conversation_1", "html")
        dir.create(test_dir, recursive = TRUE, showWarnings = FALSE)
        list(
          ai_dir = dirname(dirname(test_dir)),
          json_file_path = file.path(test_dir, "conversation.json"),
          conversation_log_path = file.path(test_dir, "conversation_log.json"),
          display_file_path = file.path(test_dir, "conversation_display.html"),
          script_history_path = file.path(test_dir, "script_history.tsv"),
          buttons_csv_path = file.path(test_dir, "message_buttons.csv"),
          attachments_path = file.path(test_dir, "attachments.csv")
        )
      },
      ".rs.readMessageButtons" = function() {
      data.frame(
        message_id = integer(),
        buttons_run = character(),
        next_button = character(),
        on_deck_button = character(),
        stringsAsFactors = FALSE
      )
    },
    ".rs.computeLineDiff" = function(oldLines, newLines, isFromEditFile = FALSE) {
      # Simple diff implementation for testing
      if (length(newLines) > length(oldLines)) {
        # More new lines = addition
        added <- length(newLines) - length(oldLines)
        return(list(
          diff = list(
            list(type = "added", content = "new line")
          ),
          added = added,
          deleted = 0
        ))
      } else if (length(oldLines) > length(newLines)) {
        # Fewer new lines = deletion
        deleted <- length(oldLines) - length(newLines)
        return(list(
          diff = list(
            list(type = "deleted", content = "old line")
          ),
          added = 0,
          deleted = deleted
        ))
      } else {
        # Same length = no change
        return(list(
          diff = list(
            list(type = "unchanged", content = "line")
          ),
          added = 0,
          deleted = 0
        ))
      }
    }
  )
}

# Helper function to set up mocks - handles functions that don't exist in packages
with_test_mocks <- function(mock_types = c("core"), code, additional_mocks = list()) {
  # IMMEDIATELY mock checkRequiredPackages to prevent dependency checks
  assign(".rs.checkRequiredPackages", function() invisible(TRUE), envir = .GlobalEnv)
  
  all_mocks <- list()
  
  if ("core" %in% mock_types) {
    all_mocks <- c(all_mocks, create_core_rs_mocks(), create_filesystem_mocks(), create_variable_manager_mocks())
  }
  if ("backend" %in% mock_types) {
    all_mocks <- c(all_mocks, create_backend_mocks())
  }
  if ("editor" %in% mock_types) {
    all_mocks <- c(all_mocks, create_editor_mocks())
  }
  if ("filesystem" %in% mock_types) {
    all_mocks <- c(all_mocks, create_filesystem_mocks())
  }
  if ("attachment_backend" %in% mock_types) {
    all_mocks <- c(all_mocks, create_attachment_backend_mocks())
  }
  if ("button_helpers" %in% mock_types) {
    all_mocks <- c(all_mocks, create_button_helper_mocks())
  }
  if ("context" %in% mock_types) {
    all_mocks <- c(all_mocks, create_context_mocks())
  }
  if ("display_data" %in% mock_types) {
    all_mocks <- c(all_mocks, create_display_data_mocks())
  }
  if ("display_utils" %in% mock_types) {
    all_mocks <- c(all_mocks, create_display_utility_mocks())
  }
  if ("test_conversation" %in% mock_types) {
    all_mocks <- c(all_mocks, create_test_conversation_mocks())
  }
  if ("conversation_display" %in% mock_types) {
    all_mocks <- c(all_mocks, create_conversation_display_mocks())
  }
  if ("symbol_index" %in% mock_types) {
    all_mocks <- c(all_mocks, create_symbol_index_mocks())
  }
  
  # Legacy support - map old names to new ones
  if ("api" %in% mock_types) {
    all_mocks <- c(all_mocks, create_editor_mocks())
  }
  if ("attachments" %in% mock_types) {
    all_mocks <- c(all_mocks, create_attachment_backend_mocks())
  }
  if ("buttons" %in% mock_types) {
    all_mocks <- c(all_mocks, create_button_helper_mocks())
  }
  if ("display" %in% mock_types) {
    all_mocks <- c(all_mocks, create_display_data_mocks())
  }
  if ("conversation" %in% mock_types) {
    all_mocks <- c(all_mocks, create_display_data_mocks(), create_filesystem_mocks())
  }
  
  # Store original values for cleanup
  original_values <- list()
  for (name in names(all_mocks)) {
    if (exists(name, envir = .GlobalEnv)) {
      original_values[[name]] <- get(name, envir = .GlobalEnv)
    }
  }
  
  # Add additional mocks (these override any existing mocks with the same name)
  # Use proper list merging to ensure additional mocks override existing ones
  if (length(additional_mocks) > 0) {
    for (name in names(additional_mocks)) {
      all_mocks[[name]] <- additional_mocks[[name]]
    }
  }
  
  # Clear the test mocks environment and reinitialize
  rm(list = ls(envir = .test_mocks), envir = .test_mocks)
  reset_test_vars()
  
  # Ensure conversation cache is explicitly cleared for each test
  if (exists("conversationVariableCache", envir = .test_mocks)) {
    rm("conversationVariableCache", envir = .test_mocks)
  }
  
  # Set up the mocks in global environment
  for (name in names(all_mocks)) {
    assign(name, all_mocks[[name]], envir = .GlobalEnv)
  }
  
  # Use on.exit to ensure cleanup happens even if test fails
  on.exit({
    # Restore original values or remove if didn't exist before
    for (name in names(all_mocks)) {
      if (name %in% names(original_values)) {
        assign(name, original_values[[name]], envir = .GlobalEnv)
      } else {
        if (exists(name, envir = .GlobalEnv)) {
          rm(list = name, envir = .GlobalEnv)
        }
      }
    }
  })
  
  # Execute the test code
  force(code)
}

# Helper to create isolated test directories
create_test_env <- function(prefix = "test") {
  test_timestamp <- as.integer(Sys.time())
  test_dir <- file.path(tempdir(), paste0(prefix, "_", test_timestamp))
  dir.create(test_dir, recursive = TRUE)
  
  list(
    test_dir = test_dir,
    cleanup = function() {
      unlink(test_dir, recursive = TRUE)
    }
  )
}

# Helper to reset test variables
reset_test_vars <- function() {
  rm(list = ls(envir = .test_mocks), envir = .test_mocks)
  
  # Set default test variables
  assign("currentConversationIndex", 1, envir = .test_mocks)
  assign("messageIdCounter", 0, envir = .test_mocks)
  assign("backend_environment", "production", envir = .test_mocks)
  assign("backend_server_url", "https://api.lotas.ai", envir = .test_mocks)
  
  # Initialize currentCachedConversationId to NULL to ensure proper logic flow
  assign("currentCachedConversationId", NULL, envir = .test_mocks)
}

# Helper to clean up lingering global mocks
cleanup_global_mocks <- function() {
  # Remove any .rs.* functions that might be lingering from previous test runs
  global_vars <- ls(envir = .GlobalEnv)
  rs_functions <- global_vars[grepl("^\\.rs\\.", global_vars)]
  
  if (length(rs_functions) > 0) {
    cat("Cleaning up lingering global mocks:", paste(rs_functions, collapse = ", "), "\n")
    rm(list = rs_functions, envir = .GlobalEnv)
  }
  
  # Reset test variables
  reset_test_vars()
}

# Initialize test environment on load
reset_test_vars() 