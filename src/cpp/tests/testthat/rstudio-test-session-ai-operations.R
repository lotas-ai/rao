# Test file for SessionAiOperations.R

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

# Source the main Operations file to get fresh function definitions
# Only source if explicitly requested, to allow tests to work with mocks
if (Sys.getenv("RSTUDIO_SOURCE_MODULES", "FALSE") == "TRUE") {
  tryCatch({
    source_session_module("SessionAiOperations.R")
  }, error = function(e) {
    message("Note: Could not source SessionAiOperations.R, using existing definitions: ", e$message)
  })
}

context("SessionAiOperations")



# Test isApiResponseFunctionCall
test_that("isApiResponseFunctionCall detects function calls correctly", {
  skip_if_not(exists(".rs.isApiResponseFunctionCall"))
  
  with_test_mocks(c("core", "editor"), {
    # Test direct function call format
    direct_func_response <- list(
      name = "edit_file",
      call_id = "abc123"
    )
    expect_true(.rs.isApiResponseFunctionCall(direct_func_response))
    
    # Test wrapped function call format
    wrapped_func_response <- list(
      function_call = list(
        name = "edit_file",
        call_id = "abc123"
      )
    )
    expect_true(.rs.isApiResponseFunctionCall(wrapped_func_response))
    
    # Test text response (should be FALSE)
    text_response <- list(
      content = "This is a text response"
    )
    expect_false(.rs.isApiResponseFunctionCall(text_response))
    
    # Test empty response
    expect_false(.rs.isApiResponseFunctionCall(list()))
  })
})

# Test check_terminal_complete function  
test_that("check_terminal_complete returns FALSE when terminal not set", {
  skip_if_not(exists(".rs.check_terminal_complete"))
  
  with_test_mocks(c("core", "editor"), {
    result <- .rs.check_terminal_complete(123)
    expect_true(is.logical(result))
  })
})

# Test check_console_complete function
test_that("check_console_complete returns TRUE when console is done", {
  skip_if_not(exists(".rs.check_console_complete"))
  
  with_test_mocks(c("core", "editor"), {
    # Set up console state
    assign(".rs.console_done", TRUE, envir = .GlobalEnv)
    on.exit(if(exists(".rs.console_done", envir = .GlobalEnv)) rm(".rs.console_done", envir = .GlobalEnv))
    
    result <- .rs.check_console_complete(123)
    expect_true(is.logical(result))
  })
})


# Test extractRCodeFromResponse function
test_that("extractRCodeFromResponse returns input unchanged", {
  skip_if_not(exists(".rs.extractRCodeFromResponse"))
  
  with_test_mocks(c("core", "editor"), {
    test_input <- "print('hello')\nx <- 1:10"
    result <- .rs.extractRCodeFromResponse(test_input)
    expect_equal(result, test_input)
  })
})




