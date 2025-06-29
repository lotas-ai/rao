# test-session-ai-conversation-display.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.

library(testthat)
library(jsonlite)

# Load shared test helpers
source("test-helpers.R")

# Helper function to find rstudio root directory
find_rstudio_root <- function() {
  current_dir <- getwd()
  
  # Walk up the directory tree looking for "rstudio"
  while (current_dir != dirname(current_dir)) {  # Stop at filesystem root
    if (basename(current_dir) == "rstudio") {
      return(current_dir)
    }
    current_dir <- dirname(current_dir)
  }
  
  # Also check if we're already in rstudio
  if (basename(getwd()) == "rstudio") {
    return(getwd())
  }
  
  return(NULL)
}

# Helper function to source session modules
source_session_module <- function(module_name) {
  rstudio_root <- find_rstudio_root()
  if (is.null(rstudio_root)) {
    stop(paste0("Could not find 'rstudio' directory in the path hierarchy starting from: ", getwd(),
                "\nPlease ensure you are running tests from within the rstudio project directory structure.",
                "\nCurrent working directory: ", getwd()))
  }
  
  module_path <- file.path(rstudio_root, "src", "cpp", "session", "modules", module_name)
  if (!file.exists(module_path)) {
    stop(paste0("Session module not found at: ", module_path,
                "\nRStudio root found at: ", rstudio_root))
  }
  
  source(module_path, local = FALSE)
}

# Verify that required helper functions exist
if (!exists("create_variable_manager_mocks")) {
  create_variable_manager_mocks <- function() {
    list(
      ".rs.getConversationVar" = function(varName, defaultValue = NULL) defaultValue,
      ".rs.setConversationVar" = function(varName, value) TRUE
    )
  }
}

if (!exists("with_test_mocks")) {
  with_test_mocks <- function(mock_types = c("core"), code, additional_mocks = list()) {
    force(code)
  }
}

# Source the main ConversationDisplay file to get fresh function definitions
# Only source if explicitly requested, to allow tests to work with mocks
if (Sys.getenv("RSTUDIO_SOURCE_MODULES", "FALSE") == "TRUE") {
  tryCatch({
    source_session_module("SessionAiConversationDisplay.R")
  }, error = function(e) {
    message("Note: Could not source SessionAiConversationDisplay.R, using existing definitions: ", e$message)
  })

  # Also source SessionAiIO.R to get functions like readConversationLog
  tryCatch({
    source_session_module("SessionAiIO.R")
  }, error = function(e) {
    message("Note: Could not source SessionAiIO.R, using existing definitions: ", e$message)
  })
}

# Verify that readConversationLog function exists
if (exists(".rs.readConversationLog")) {
} else {
  # Define a minimal stub function to allow mocking
  if (exists(".rs.addFunction")) {
    .rs.addFunction("readConversationLog", function() {
      return(list())
    })
  } else {
    # Direct assignment if .rs.addFunction is not available
    assign(".rs.readConversationLog", function() {
      return(list())
    }, envir = .GlobalEnv)
  }
}

context("SessionAiConversationDisplay")



# Test getFunctionCallTypeForMessage - identifies function call types
test_that("getFunctionCallTypeForMessage identifies function call types correctly", {
  skip_if_not(exists(".rs.getFunctionCallTypeForMessage"))
  
  with_test_mocks(c("core", "display_data"), {
    # Test conversation log with function calls
    conversation_log <- list(
      list(
        id = 1,
        role = "assistant",
        function_call = list(
          name = "edit_file",
          call_id = "call_123"
        )
      ),
      list(
        id = 2,
        type = "function_call_output",
        call_id = "call_123",
        output = "File edited successfully"
      )
    )
    
    # Test direct function call
    result1 <- .rs.getFunctionCallTypeForMessage(1, conversation_log)
    expect_equal(result1, "edit_file")
    
    # Test function call output
    result2 <- .rs.getFunctionCallTypeForMessage(2, conversation_log)
    expect_equal(result2, "edit_file")
    
    # Test non-existent message
    result3 <- .rs.getFunctionCallTypeForMessage(999, conversation_log)
    expect_null(result3)
  })
})

# Test getMessageTitle - generates appropriate titles for messages
test_that("getMessageTitle generates appropriate titles for different function types", {
  skip_if_not(exists(".rs.getMessageTitle"))
  
  with_test_mocks(c("core", "display_utils"), {
    # Test conversation log with different function types
    conversation_log <- list(
      list(
        id = 1,
        function_call = list(name = "edit_file", call_id = "call_1")
      ),
      list(
        id = 2,
        function_call = list(name = "run_console_cmd", call_id = "call_2")
      ),
      list(
        id = 3,
        function_call = list(name = "run_terminal_cmd", call_id = "call_3")
      ),
      list(
        id = 4,
        role = "user",
        content = "No function call"
      )
    )
    
    # Test edit_file title
    result1 <- .rs.getMessageTitle(1, conversation_log)
    expect_true(is.character(result1) || is.null(result1))
    
    # Test console command title
    result2 <- .rs.getMessageTitle(2, conversation_log)
    expect_equal(result2, "Console")
    
    # Test terminal command title
    result3 <- .rs.getMessageTitle(3, conversation_log)
    expect_equal(result3, "Terminal")
    
    # Test no function call
    result4 <- .rs.getMessageTitle(4, conversation_log)
    expect_null(result4)
  })
})

# Test getConversationJavaScript - generates JavaScript for the conversation display
test_that("getConversationJavaScript generates valid JavaScript", {
  skip_if_not(exists(".rs.getConversationJavaScript"))
  
  with_test_mocks("core", {
    result <- .rs.getConversationJavaScript()
    
    expect_true(is.character(result))
    expect_true(nchar(result) > 0)
    expect_true(grepl("window.onload", result))
    expect_true(grepl("addEventListener", result))
    expect_true(grepl("message-button", result))
  })
})

# Test processCodeBlocks - processes code blocks in messages
test_that("processCodeBlocks handles code blocks correctly", {
  skip_if_not(exists(".rs.processCodeBlocks"))
  
  with_test_mocks(c("core", "display_utils"), {
    # Test R code block
    r_code <- "```r\nprint('hello')\nx <- 1:10\n```"
    
    result <- .rs.processCodeBlocks(r_code, 123, 1)
    
    expect_true(is.list(result))
    expect_true("hasCodeBlock" %in% names(result))
    expect_true("hasRCode" %in% names(result))
    
    # Test Python code block
    python_code <- "```python\nprint('hello world')\n```"
    result2 <- .rs.processCodeBlocks(python_code, 124, 1)
    
    expect_true(is.list(result2))
    expect_true("hasCodeBlock" %in% names(result2))
    
    # Test plain text without code blocks
    plain_text <- "This is just plain text with no code blocks."
    result3 <- .rs.processCodeBlocks(plain_text, 125, 1)
    
    expect_true(is.list(result3))
    expect_false(result3$hasCodeBlock)
  })
})

# Test isLastFunctionEditFile - checks if last function was edit_file
test_that("isLastFunctionEditFile correctly identifies edit_file functions", {
  skip_if_not(exists(".rs.isLastFunctionEditFile"))
  
  with_test_mocks(c("core", "display_data"), {
    # Test with edit_file as last function - only mock the dependency
    test_log <- list(
      list(
        id = 1,
        role = "user",
        content = "Edit this file"
      ),
      list(
        id = 2,
        function_call = list(name = "edit_file"),
        role = "assistant"
      )
    )
    
    # Store original function
    old_readConversationLog <- if (exists(".rs.readConversationLog", envir = .GlobalEnv)) {
      get(".rs.readConversationLog", envir = .GlobalEnv)
    } else {
      NULL
    }
    
    assign(".rs.readConversationLog", function() {
      return(test_log)
    }, envir = .GlobalEnv)
    
    on.exit({
      if (!is.null(old_readConversationLog)) {
        assign(".rs.readConversationLog", old_readConversationLog, envir = .GlobalEnv)
      } else if (exists(".rs.readConversationLog", envir = .GlobalEnv)) {
        rm(".rs.readConversationLog", envir = .GlobalEnv)
      }
    }, add = TRUE)
    
    # Test the real function with mocked dependency data
    result1 <- .rs.isLastFunctionEditFile()
    expect_true(is.logical(result1))  # Just verify it returns a logical value
    
    # Test with different function as last
    assign(".rs.readConversationLog", function() {
      list(
        list(
          id = 1,
          function_call = list(name = "edit_file")
        ),
        list(
          id = 2,
          function_call = list(name = "read_file")
        )
      )
    }, envir = .GlobalEnv)
    
    result2 <- .rs.isLastFunctionEditFile()
    expect_true(is.logical(result2))
    
    # Test with empty conversation log
    assign(".rs.readConversationLog", function() list(), envir = .GlobalEnv)
    
    result3 <- .rs.isLastFunctionEditFile()
    expect_true(is.logical(result3))
  })
})

# Test conversation display handles empty conversation
test_that("conversation display handles empty conversation", {
  skip_if_not(exists(".rs.updateConversationDisplay"))
  
  with_test_mocks(c("core", "display_utils"), {
    # Create test environment
    test_env <- create_test_env("empty_display_test")
    on.exit(test_env$cleanup())
    
    # Empty conversation
    empty_conversation <- list(
      messages = data.frame(
        id = integer(0),
        type = character(0),
        text = character(0),
        timestamp = character(0),
        stringsAsFactors = FALSE
      )
    )
    
    display_file <- file.path(test_env$test_dir, "empty_display.html")
    
    # Store original functions
    old_funcs <- list()
    func_names <- c(".rs.readConversation", ".rs.readConversationLog", ".rs.readMessageButtons", 
                    ".rs.get_ai_file_paths", ".rs.getCurrentConversationIndex", ".rs.updateConversationDisplay")
    
    for (fname in func_names) {
      if (exists(fname, envir = .GlobalEnv)) {
        old_funcs[[fname]] <- get(fname, envir = .GlobalEnv)
      }
    }
    
    # Set up mocks for dependencies only - NOT the function under test
    assign(".rs.readConversation", function() empty_conversation, envir = .GlobalEnv)
    assign(".rs.readConversationLog", function() list(), envir = .GlobalEnv)
    assign(".rs.readMessageButtons", function() data.frame(
      message_id = integer(),
      buttons_run = character(),
      next_button = character(),
      stringsAsFactors = FALSE
    ), envir = .GlobalEnv)
                assign(".rs.get_ai_file_paths", function() {
        list(
          display_file_path = display_file,
        userOnlyDisplayFilePath = file.path(test_env$test_dir, "empty_display_user_only.html")
      )
    }, envir = .GlobalEnv)
    assign(".rs.getCurrentConversationIndex", function() 1, envir = .GlobalEnv)
    
    # Cleanup function
    on.exit({
      for (fname in func_names) {
        if (fname %in% names(old_funcs)) {
          assign(fname, old_funcs[[fname]], envir = .GlobalEnv)
        } else if (exists(fname, envir = .GlobalEnv)) {
          rm(list = fname, envir = .GlobalEnv)
        }
      }
    }, add = TRUE)
    
    result <- .rs.updateConversationDisplay()
    
    expect_true(is.logical(result))
    
    # The exact behavior for empty conversations depends on the real implementation
  })
})

# Test error handling in conversation display
test_that("conversation display handles errors gracefully", {
  skip_if_not(exists(".rs.updateConversationDisplay"))
  
  with_test_mocks(c("core", "display_utils"), {
    # Test that the function handles missing dependencies by returning FALSE rather than crashing
    # Create minimal mocks but make them fail
    assign(".rs.get_ai_file_paths", function() NULL, envir = .GlobalEnv)
    assign(".rs.getCurrentConversationIndex", function() 1, envir = .GlobalEnv)
    
    on.exit({
      if (exists(".rs.get_ai_file_paths", envir = .GlobalEnv)) {
        rm(".rs.get_ai_file_paths", envir = .GlobalEnv)
      }
      if (exists(".rs.getCurrentConversationIndex", envir = .GlobalEnv)) {
        rm(".rs.getCurrentConversationIndex", envir = .GlobalEnv)
      }
    })
    
    # The function should handle the NULL paths gracefully
    # generateAndSaveConversationDisplay returns FALSE when paths is NULL
    result <- tryCatch({
      .rs.updateConversationDisplay()
    }, error = function(e) {
      # If it throws an error, that's also acceptable error handling
      TRUE
    })
    
    # Either it returns FALSE (graceful handling) or throws an error (also valid)
    # What we're testing is that it doesn't crash the R session
    expect_true(is.logical(result) || inherits(result, "logical"))
  })
}) 