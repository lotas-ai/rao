# Test file for SessionAiIO.R

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

# Aggressively clean up any lingering mocks from previous test runs that might interfere
# Remove all .rs.* functions first
all_rs_funcs <- ls(envir = .GlobalEnv, pattern = "^\\.rs\\.")
if (length(all_rs_funcs) > 0) {
  rm(list = all_rs_funcs, envir = .GlobalEnv)
}

# Source the main IO file to get fresh function definitions
# Only source if explicitly requested, to allow tests to work with mocks
if (Sys.getenv("RSTUDIO_SOURCE_MODULES", "FALSE") == "TRUE") {
  tryCatch({
    source_session_module("SessionAiIO.R")
  }, error = function(e) {
    stop(paste0("Failed to source SessionAiIO.R: ", e$message))
  })
}

context("SessionAiIO")

test_that("get_ai_file_paths returns correct directory structure", {
  skip_if_not(exists(".rs.get_ai_file_paths"))
  
  # Create test environment
  test_env <- create_test_env("ai_paths_test")
  on.exit(test_env$cleanup())
  
  # Use local_mocked_bindings to properly mock .libPaths like other tests
  local_mocked_bindings(
    ".libPaths" = function(...) {
      if (missing(...) || length(list(...)) == 0) {
        return(c(test_env$test_dir))
      } else {
        return(c(test_env$test_dir))
      }
    },
    .package = "base"
  )
  
  # Use the existing function but with test environment
  with_test_mocks("core", {
    result <- .rs.get_ai_file_paths()
    
    expect_true(is.list(result))
    
    # Check for the essential paths that the function should return
    essential_paths <- c("ai_dir", "json_file_path", "conversation_log_path", 
                        "display_file_path", "script_history_path")
    expect_true(all(essential_paths %in% names(result)))
    
    # Check that all essential paths are character strings and not NULL
    path_checks <- c(
      !is.null(result$ai_dir) && is.character(result$ai_dir),
      !is.null(result$json_file_path) && is.character(result$json_file_path),
      !is.null(result$conversation_log_path) && is.character(result$conversation_log_path),
      !is.null(result$display_file_path) && is.character(result$display_file_path),
      !is.null(result$script_history_path) && is.character(result$script_history_path)
    )
    expect_true(all(path_checks))
    
    # Check that the ai_dir directory is created
    expect_true(dir.exists(result$ai_dir))
  }, list(
    ".rs.getCurrentConversationIndex" = function() 1
  ))
})

test_that("safeTextCompare handles different comparison modes correctly", {
  skip_if_not(exists(".rs.safeTextCompare"))
  
  with_test_mocks("core", {
    # Test startsWith mode
    expect_true(.rs.safeTextCompare("hello world", "hello", "startsWith"))
    expect_false(.rs.safeTextCompare("hello world", "world", "startsWith"))
    
    # Test endsWith mode
    expect_true(.rs.safeTextCompare("hello world", "world", "endsWith"))
    expect_false(.rs.safeTextCompare("hello world", "hello", "endsWith"))
    
    # Test contains mode
    expect_true(.rs.safeTextCompare("hello world", "lo wo", "contains"))
    expect_false(.rs.safeTextCompare("hello world", "xyz", "contains"))
    
    # Test with code blocks
    code_text <- "```r\nprint('hello')\n```"
    expect_true(.rs.safeTextCompare(code_text, "print('hello')", "contains"))
    
    # Test null/empty inputs
    expect_false(.rs.safeTextCompare(NULL, "test", "contains"))
    expect_false(.rs.safeTextCompare("test", NULL, "contains"))
    expect_false(.rs.safeTextCompare("", "test", "contains"))
    expect_false(.rs.safeTextCompare("test", "", "contains"))
  })
})

test_that("detectCodeReplacement identifies code replacements correctly", {
  skip_if_not(exists(".rs.detectCodeReplacement"))
  
  with_test_mocks("core", {
    previous_content <- "line1\nline2\nline3\nline4\nline5"
    new_content <- "line1\nline2\nmodified_line3\nline4\nline5"
    
    result <- .rs.detectCodeReplacement(previous_content, new_content)
    
    expect_true(result$isReplacement)
    expect_true(result$matchPercentage > 0.5)
    expect_true(!is.null(result$firstMatchLine))
    expect_true(!is.null(result$lastMatchLine))
    
    # Test with completely different content
    different_content <- "totally\ndifferent\ncontent"
    result2 <- .rs.detectCodeReplacement(previous_content, different_content)
    
    expect_false(result2$isReplacement)
    
    # Test with empty content
    result3 <- .rs.detectCodeReplacement("", new_content)
    expect_false(result3$isReplacement)
  })
})

test_that("copyCompleteHtmlToUserOnly removes AI-specific elements", {
  skip_if_not(exists(".rs.copyCompleteHtmlToUserOnly"))
  
  # Create test environment
  test_env <- create_test_env("copy_html_test")
  on.exit(test_env$cleanup())
  
  # Create test HTML with AI elements
  test_html <- '<div>Content</div>
<div class="ai-button-container">
  <button>AI Button</button>
</div>
<div class="interpret-buttons">
  <button>Interpret</button>
</div>
<div class="run-file-buttons">
  <button>Run File</button>
</div>
<button class="message-button">Message Button</button>
<div>More content</div>'
  
  complete_path <- file.path(test_env$test_dir, "complete.html")
  user_only_path <- file.path(test_env$test_dir, "user_only.html")
  
  writeLines(test_html, complete_path)
  
  result <- .rs.copyCompleteHtmlToUserOnly(complete_path, user_only_path)
  
  expect_true(result)
  expect_true(file.exists(user_only_path))
  
  # Read the result and verify AI elements are removed
  result_html <- paste(readLines(user_only_path, warn = FALSE), collapse = "\n")
  
  expect_false(grepl("ai-button-container", result_html))
  expect_false(grepl("interpret-buttons", result_html))
  expect_false(grepl("run-file-buttons", result_html))
  expect_false(grepl("message-button", result_html))
  expect_true(grepl("<div>Content</div>", result_html))
  expect_true(grepl("<div>More content</div>", result_html))
})

test_that("recordFileCreation records file creation correctly", {
  skip_if_not(exists(".rs.recordFileCreation"))
  
  # Create test environment
  test_env <- create_test_env("record_file_test")
  on.exit(test_env$cleanup())
  
  # Create a test file
  test_file <- file.path(test_env$test_dir, "test_script.R")
  writeLines(c("# Test script", "print('Hello')"), test_file)
  
  # Mock functions in global environment
  old_getCurrentConversationIndex <- if(exists(".rs.getCurrentConversationIndex")) get(".rs.getCurrentConversationIndex") else NULL
  old_getVar <- if(exists(".rs.getVar")) get(".rs.getVar") else NULL
  old_read_file_changes_log <- if(exists(".rs.read_file_changes_log")) get(".rs.read_file_changes_log") else NULL
  old_write_file_changes_log <- if(exists(".rs.write_file_changes_log")) get(".rs.write_file_changes_log") else NULL
  
  assign(".rs.getCurrentConversationIndex", function() 1, envir = .GlobalEnv)
  assign(".rs.getVar", function(x) if (x == "messageIdCounter") 123 else NULL, envir = .GlobalEnv)
  assign(".rs.read_file_changes_log", function() {
    list(changes = list())
  }, envir = .GlobalEnv)
  assign(".rs.write_file_changes_log", function(x) TRUE, envir = .GlobalEnv)
  
  on.exit({
    if(!is.null(old_getCurrentConversationIndex)) {
      assign(".rs.getCurrentConversationIndex", old_getCurrentConversationIndex, envir = .GlobalEnv)
    } else if(exists(".rs.getCurrentConversationIndex", envir = .GlobalEnv)) {
      rm(".rs.getCurrentConversationIndex", envir = .GlobalEnv)
    }
    if(!is.null(old_getVar)) {
      assign(".rs.getVar", old_getVar, envir = .GlobalEnv)
    } else if(exists(".rs.getVar", envir = .GlobalEnv)) {
      rm(".rs.getVar", envir = .GlobalEnv)
    }
    if(!is.null(old_read_file_changes_log)) {
      assign(".rs.read_file_changes_log", old_read_file_changes_log, envir = .GlobalEnv)
    } else if(exists(".rs.read_file_changes_log", envir = .GlobalEnv)) {
      rm(".rs.read_file_changes_log", envir = .GlobalEnv)
    }
    if(!is.null(old_write_file_changes_log)) {
      assign(".rs.write_file_changes_log", old_write_file_changes_log, envir = .GlobalEnv)
    } else if(exists(".rs.write_file_changes_log", envir = .GlobalEnv)) {
      rm(".rs.write_file_changes_log", envir = .GlobalEnv)
    }
  }, add = TRUE)
  
  result <- .rs.recordFileCreation(test_file)
  
  expect_true(result)
})

test_that("recordFileCreation returns FALSE for non-existent file", {
  skip_if_not(exists(".rs.recordFileCreation"))
  
  result <- .rs.recordFileCreation("non_existent_file.R")
  expect_false(result)
})

test_that("delete_folder returns FALSE for non-existent directory", {
  skip_if_not(exists(".rs.delete_folder"))
  
  # Create test environment
  test_env <- create_test_env("delete_nonexistent_test")
  on.exit(test_env$cleanup())
  
  # Use local_mocked_bindings to properly mock .libPaths like other tests
  local_mocked_bindings(
    ".libPaths" = function() c(test_env$test_dir),
    .package = "base"
  )
  
  result <- .rs.delete_folder("non_existent_folder")
  
  expect_false(result)
})
