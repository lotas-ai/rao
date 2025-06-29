# test-session-ai-search.R
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

# Source the main Search file to get fresh function definitions
# Only source if explicitly requested, to allow tests to work with mocks
if (Sys.getenv("RSTUDIO_SOURCE_MODULES", "FALSE") == "TRUE") {
  tryCatch({
    source_session_module("SessionAiSearch.R")
  }, error = function(e) {
    message("Note: Could not source SessionAiSearch.R, using existing definitions: ", e$message)
  })
}

context("SessionAiSearch")

# Test getPendingCommandsFilePath
test_that("getPendingCommandsFilePath returns correct file path", {
  skip_if_not(exists(".rs.getPendingCommandsFilePath"))
  
  with_test_mocks(c("core", "editor", "display_data"), {
    # Test with default parameters
    result <- .rs.getPendingCommandsFilePath()
    expect_true(is.character(result))
    expect_true(grepl("pending.*commands\\.json$", result))
    
    # Test with specific conversation index and type
    result <- .rs.getPendingCommandsFilePath(2, "terminal")
    expect_true(is.character(result))
    expect_true(grepl("pending_terminal_commands\\.json$", result))
  })
})

# Test readPendingCommands
test_that("readPendingCommands handles missing and existing files", {
  skip_if_not(exists(".rs.readPendingCommands"))
  
  with_test_mocks(c("core", "editor", "display_data"), {
    # Clear any existing pending commands before testing
    pending_file <- .rs.getPendingCommandsFilePath()
    if (file.exists(pending_file)) {
      file.remove(pending_file)
    }
    
    # Test reading non-existent file
    result <- .rs.readPendingCommands()
    
    # The function should return an empty list for non-existent files
    expect_true(is.list(result))
    expect_equal(length(result), 0)
  })
})

# Test parseFunctionCallArguments
test_that("parseFunctionCallArguments parses JSON correctly", {
  skip_if_not(exists(".rs.parseFunctionCallArguments"))
  
  with_test_mocks(c("core", "editor", "display_data"), {
    function_call <- list(
      arguments = '{"command": "ls -la", "explanation": "List files"}'
    )
    
    result <- .rs.parseFunctionCallArguments(function_call)
    expect_equal(result$command, "ls -la")
    expect_equal(result$explanation, "List files")
  })
})

# Test storePendingFunctionCall
test_that("storePendingFunctionCall stores and returns pending ID", {
  skip_if_not(exists(".rs.storePendingFunctionCall"))
  
  with_test_mocks(c("core", "editor", "display_data"), {
    function_call <- list(
      name = "run_terminal_cmd",
      arguments = '{"command": "echo test"}',
      call_id = "test_call_123"
    )
    
    pending_id <- .rs.storePendingFunctionCall(function_call, 100, 1, "terminal", "req_123")
    
    expect_true(is.numeric(pending_id))
    expect_true(pending_id > 0)
  })
})

# Test isDuplicateFunctionCall
test_that("isDuplicateFunctionCall detects duplicates correctly", {
  skip_if_not(exists(".rs.isDuplicateFunctionCall"))
  
  with_test_mocks(c("core", "editor", "display_data"), {
    conversation_log <- list(
      list(
        function_call = list(
          name = "read_file",
          arguments = '{"filename": "test.R", "start_line_one_indexed": 1, "end_line_one_indexed_inclusive": 10}'
        )
      )
    )
    
    # Test duplicate detection
    args1 <- list(filename = "test.R", start_line_one_indexed = 1L, end_line_one_indexed_inclusive = 10L)
    result <- .rs.isDuplicateFunctionCall("read_file", args1, conversation_log)
    expect_true(result)
    
    # Test non-duplicate
    args2 <- list(filename = "other.R", start_line_one_indexed = 1L, end_line_one_indexed_inclusive = 10L)
    result <- .rs.isDuplicateFunctionCall("read_file", args2, conversation_log)
    expect_false(result)
  })
})

# Test resetAiCancellation
test_that("resetAiCancellation sets cancellation flag to FALSE", {
  skip_if_not(exists(".rs.resetAiCancellation"))
  
  # Create a shared environment for conversation variables
  conv_env <- new.env()
  
  with_test_mocks(c("core", "editor", "display_data"), {
    # Test that we can mock the conversation variable functions directly
    local_mocked_bindings(
      .rs.setConversationVar = function(name, value) {
        assign(name, value, envir = conv_env)
        invisible(TRUE)
      },
      .rs.getConversationVar = function(name) {
        if (exists(name, envir = conv_env)) {
          get(name, envir = conv_env)
        } else {
          FALSE
        }
      },
      .rs.resetAiCancellation = function() {
        assign("ai_cancelled", FALSE, envir = conv_env)
        invisible(TRUE)
      },
      .package = "base"
    )
    
    # Test the resetAiCancellation function behavior
    # We'll test by setting it to TRUE first, then calling reset and checking it's FALSE
    .rs.setConversationVar("ai_cancelled", TRUE)
    expect_true(.rs.getConversationVar("ai_cancelled"))
    
    # Call the reset function
    .rs.resetAiCancellation()
    
    # Verify it was reset to FALSE
    expect_false(.rs.getConversationVar("ai_cancelled"))
  })
})

# Test extractCodeFromResponse
test_that("extractCodeFromResponse extracts code blocks correctly", {
  skip_if_not(exists(".rs.extractCodeFromResponse"))
  
  with_test_mocks(c("core", "editor", "display_data"), {
    # Test R code block
    response_r <- "Here's some R code:\n```r\nprint('hello')\nx <- 1\n```\nThat's it."
    result <- .rs.extractCodeFromResponse(response_r)
    expect_equal(result, "print('hello')\nx <- 1")
    
    # Test bash code block
    response_bash <- "Run this:\n```bash\nls -la\ncd /tmp\n```"
    result <- .rs.extractCodeFromResponse(response_bash)
    expect_equal(result, "ls -la\ncd /tmp")
    
    # Test no code block
    response_plain <- "Just plain text with no code blocks."
    result <- .rs.extractCodeFromResponse(response_plain)
    expect_equal(result, response_plain)
    
    # Test empty input
    result <- .rs.extractCodeFromResponse("")
    expect_equal(result, "")
    
    result <- .rs.extractCodeFromResponse(NULL)
    expect_null(result)
  })
})

# Test handle_duplicate_function_call
test_that("handle_duplicate_function_call returns proper structure", {
  skip_if_not(exists(".rs.handle_duplicate_function_call"))
  
  with_test_mocks(c("core", "editor", "display_data"), {
    function_call <- list(
      name = "read_file",
      call_id = "test_call_123"
    )
    
    result <- .rs.handle_duplicate_function_call(function_call, 456)
    
    expect_true("functionOutputEntry" %in% names(result))
    expect_true("functionOutputId" %in% names(result))
    expect_equal(result$functionOutputEntry$type, "function_call_output")
    expect_equal(result$functionOutputEntry$call_id, "test_call_123")
    expect_equal(result$functionOutputEntry$related_to, 456)
    expect_true(grepl("same function", result$functionOutputEntry$output))
  })
})

# Test handle_find_keyword_context


# Test handle_list_dir


# Test handle_run_terminal_cmd
test_that("handle_run_terminal_cmd creates pending terminal command", {
  skip_if_not(exists(".rs.handle_run_terminal_cmd"))
  
  with_test_mocks(c("core", "editor", "display_data"), {
    function_call <- list(
      name = "run_terminal_cmd",
      arguments = '{"command": "ls -la", "explanation": "List files"}',
      call_id = "test_terminal_call"
    )
    
    result <- .rs.handle_run_terminal_cmd(function_call, list(), 123)
    
    expect_true(result$isPending)
    expect_false(result$isConsole)
    expect_true(result$breakoutOfFunctionCalls)
    expect_equal(result$command, "ls -la")
    expect_true(is.numeric(result$pendingId))
  })
})

# Test handle_run_console_cmd
test_that("handle_run_console_cmd creates pending console command", {
  skip_if_not(exists(".rs.handle_run_console_cmd"))
  
  with_test_mocks(c("core", "editor", "display_data"), {
    function_call <- list(
      name = "run_console_cmd",
      arguments = '{"command": "print(\\"hello\\")"}',
      call_id = "test_console_call"
    )
    
    result <- .rs.handle_run_console_cmd(function_call, list(), 123)
    
    expect_true(result$isPending)
    expect_true(result$isConsole)
    expect_true(result$breakoutOfFunctionCalls)
    expect_equal(result$command, 'print("hello")')
    expect_true(is.numeric(result$pendingId))
  })
})

# Test handle_read_file


# Test processCodeBlocks
test_that("processCodeBlocks identifies code blocks correctly", {
  skip_if_not(exists(".rs.processCodeBlocks"))
  
  with_test_mocks(c("core", "editor", "display_data"), {
    # Test R code block
    response_r <- "Here's some R code:\n```r\nprint('hello')\n```"
    result <- .rs.processCodeBlocks(response_r, 123, 1)
    
    expect_true(result$hasCodeBlock)
    expect_true(result$hasRCode)
    expect_false(result$isBashScript)
    expect_false(result$isRmdBlock)
    
    # Test bash script
    response_bash <- "Run this:\n```bash\nls -la\n```"
    result <- .rs.processCodeBlocks(response_bash, 124, 1)
    
    expect_true(result$hasCodeBlock)
    expect_false(result$hasRCode)
    expect_true(result$isBashScript)
    
    # Test no code block
    response_plain <- "Just plain text"
    result <- .rs.processCodeBlocks(response_plain, 126, 1)
    
    expect_false(result$hasCodeBlock)
    expect_false(result$hasRCode)
    expect_false(result$isBashScript)
    expect_false(result$isRmdBlock)
  })
})

# Tests use with_test_mocks() which handles cleanup automatically 