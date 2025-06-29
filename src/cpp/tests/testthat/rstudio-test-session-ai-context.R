#
# test-session-ai-context.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#
#

# Test file for SessionAiContext.R

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

# Source the main Context file to get fresh function definitions
# Only source if explicitly requested, to allow tests to work with mocks
if (Sys.getenv("RSTUDIO_SOURCE_MODULES", "FALSE") == "TRUE") {
  tryCatch({
    source_session_module("SessionAiContext.R")
  }, error = function(e) {
    message("Note: Could not source SessionAiContext.R, using existing definitions: ", e$message)
  })
}

context("SessionAiContext")

test_that("getContextItems returns current conversation context", {
  skip_if_not(exists(".rs.getContextItems"))
  
  with_test_mocks("core", {
    test_storage <- new.env()
    
    # Set up test context
    test_context <- list(
      list(
        id = "ctx_1",
        type = "file",
        path = "/path/to/script.R",
        content = "print('hello')"
      ),
      list(
        id = "ctx_2",
        type = "selection",
        content = "selected code snippet",
        line_start = 5,
        line_end = 10
      )
    )
    assign("contextItems", test_context, envir = test_storage)
    
    local_mocked_bindings(
      ".rs.getVar" = function(varName) {
        if (exists(varName, envir = test_storage)) {
          get(varName, envir = test_storage)
        } else {
          NULL
        }
      },
      .package = .GlobalEnv
    )
    
    result <- .rs.getContextItems()
    
    expect_true(is.list(result))
    expect_equal(length(result), 2)
    expect_equal(result[[1]]$id, "ctx_1")
    expect_equal(result[[1]]$type, "file")
    expect_equal(result[[2]]$content, "selected code snippet")
  })
})
