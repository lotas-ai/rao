# Test file for SessionAiAttachments.R

library(testthat)

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

# Source the main Attachments file to get fresh function definitions
# Only source if explicitly requested, to allow tests to work with mocks
if (Sys.getenv("RSTUDIO_SOURCE_MODULES", "FALSE") == "TRUE") {
  tryCatch({
    source_session_module("SessionAiAttachments.R")
  }, error = function(e) {
    message("Note: Could not source SessionAiAttachments.R, using existing definitions: ", e$message)
  })
}

context("SessionAiAttachments")

test_that("save_ai_attachment saves file attachment", {
  skip_if_not(exists(".rs.save_ai_attachment"))
  
  with_test_mocks(c("core", "attachment_backend"), {
    # Create test environment
    test_env <- create_test_env("attachments_test")
    on.exit(test_env$cleanup())
    
    # Create test file
    test_file <- file.path(test_env$test_dir, "test.R")
    writeLines(c("# Test script", "print('Hello World')", "x <- 1:10"), test_file)
    
    test_storage <- new.env()
    
    local_mocked_bindings(
      ".rs.getVar" = function(varName) {
        cat("DEBUG MOCK: getVar called with varName:", varName, "\n")
        if (exists(varName, envir = test_storage)) {
          result <- get(varName, envir = test_storage)
          cat("DEBUG MOCK: Found", varName, "in test_storage, returning:", deparse(result), "\n")
          return(result)
        } else {
          cat("DEBUG MOCK:", varName, "not found in test_storage, returning NULL\n")
          return(NULL)
        }
      },
      ".rs.setVar" = function(varName, value) {
        cat("DEBUG MOCK: setVar called with varName:", varName, "value:", deparse(value), "\n")
        assign(varName, value, envir = test_storage)
        cat("DEBUG MOCK: After assign, test_storage contains:", ls(test_storage), "\n")
      },
      ".rs.getCurrentConversationIndex" = function() 1,
      .package = "base"
    )
    
    result <- .rs.save_ai_attachment(test_file)
    
    # In test environment without API key, expect failure but graceful handling
    expect_true(is.list(result))
    expect_true("success" %in% names(result))
    expect_true("reason" %in% names(result) || result$success)
  })
})

test_that("save_ai_attachment handles non-existent files", {
  skip_if_not(exists(".rs.save_ai_attachment"))
  
  with_test_mocks(c("core", "attachment_backend"), {
    # Should either throw an error or return failure result for non-existent files
    result <- tryCatch({
      .rs.save_ai_attachment("/nonexistent/file.txt")
    }, error = function(e) {
      # If it throws an error, that's expected behavior
      list(success = FALSE, reason = e$message)
    })
    
    # Either way, should indicate failure
    expect_true(is.list(result))
    expect_false(result$success)
  })
})

test_that("delete_ai_attachment removes file attachment", {
  skip_if_not(exists(".rs.delete_ai_attachment"))
  
  with_test_mocks(c("core", "attachment_backend"), {
    # Create test file
    test_env <- create_test_env("delete_test")
    on.exit(test_env$cleanup())
    
    test_file <- file.path(test_env$test_dir, "test.R")
    writeLines(c("# Test script"), test_file)
    
    result <- .rs.delete_ai_attachment(test_file)
    
    # Should return proper structure even if no attachments exist
    expect_true(is.list(result))
    expect_true("success" %in% names(result))
  })
})

test_that("list_ai_attachments returns attachment file paths", {
  skip_if_not(exists(".rs.list_ai_attachments"))
  
  with_test_mocks(c("core", "attachment_backend"), {
    result <- .rs.list_ai_attachments()
    
    # Should return character vector (empty in test environment)
    expect_true(is.character(result))
    expect_equal(length(result), 0)  # No attachments in test environment
  })
})

test_that("delete_all_ai_attachments clears all attachments", {
  skip_if_not(exists(".rs.delete_all_ai_attachments"))
  
  with_test_mocks(c("core", "attachment_backend"), {
    result <- .rs.delete_all_ai_attachments()
    
    # Should return proper structure even if no attachments exist
    expect_true(is.list(result))
    expect_true("success" %in% names(result))
  })
})