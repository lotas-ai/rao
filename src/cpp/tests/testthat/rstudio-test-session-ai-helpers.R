# Tests for SessionAiHelpers.R

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

# Source the main Helpers file to get fresh function definitions
# Only source if explicitly requested, to allow tests to work with mocks
if (Sys.getenv("RSTUDIO_SOURCE_MODULES", "FALSE") == "TRUE") {
  tryCatch({
    source_session_module("SessionAiHelpers.R")
  }, error = function(e) {
    message("Note: Could not source SessionAiHelpers.R, using existing definitions: ", e$message)
  })
}

context("SessionAiHelpers")





test_that("jsontostr converts objects to JSON correctly", {
  skip_if_not(exists(".rs.jsontostr"))
  
  with_test_mocks("core", {
    # Test simple object
    obj1 <- list(name = "test", value = 123)
    result1 <- .rs.jsontostr(obj1)
    expect_true(is.character(result1))
    expect_true(grepl("test", result1))
    expect_true(grepl("123", result1))
    
    # Test complex object
    obj2 <- list(
      items = c("a", "b", "c"),
      nested = list(x = 1, y = 2),
      flag = TRUE
    )
    result2 <- .rs.jsontostr(obj2)
    expect_true(is.character(result2))
    expect_true(grepl("items", result2))
    expect_true(grepl("nested", result2))
    
    # Test empty object
    obj3 <- list()
    result3 <- .rs.jsontostr(obj3)
    expect_true(is.character(result3))
  })
})

test_that("extractRCodeFromResponse returns response unchanged", {
  skip_if_not(exists(".rs.extractRCodeFromResponse"))
  
  with_test_mocks("core", {
    # This function currently just returns the response as-is
    expect_equal(.rs.extractRCodeFromResponse("test code", 123), "test code")
    expect_equal(.rs.extractRCodeFromResponse("", 456), "")
    expect_equal(.rs.extractRCodeFromResponse(NULL, 789), NULL)
  })
})





test_that("limitOutputText truncates output correctly", {
  skip_if_not(exists(".rs.limitOutputText"))
  
  with_test_mocks("core", {
    # Test short output (should remain unchanged)
    short_output <- c("line1", "line2", "line3")
    result1 <- .rs.limitOutputText(short_output)
    expect_equal(as.vector(result1), short_output)
    
    # Test long lines (should be truncated)
    long_line <- paste(rep("a", 250), collapse = "")
    result2 <- .rs.limitOutputText(long_line)
    expect_true(nchar(result2) < nchar(long_line))
    expect_true(grepl("\\.\\.\\.$", result2))
    
    # Test many lines
    many_lines <- rep(paste(rep("x", 200), collapse = ""), 30)
    result3 <- .rs.limitOutputText(many_lines)
    expect_true(length(result3) <= 21)
    expect_true(any(grepl("truncated", result3)))
  })
})

# Test removed: was overwriting the function being tested (.rs.createNewConversation)



# checkForDuplicateCode function removed - AI doesn't generate duplicate lines 