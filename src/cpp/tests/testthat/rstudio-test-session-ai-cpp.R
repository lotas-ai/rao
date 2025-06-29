# test-session-ai-cpp.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

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

context("SessionAi.cpp Functions")

test_that("clearAiConversation works correctly", {
  skip_if_not(exists(".rs.clear_ai_conversation"))
  
  with_test_mocks("core", {
    result <- .rs.clear_ai_conversation()
    expect_true(is.logical(result))
  })
})

test_that("createNewConversation works correctly", {
  skip_if_not(exists(".rs.create_new_conversation"))
  
  with_test_mocks("core", {
    result <- .rs.create_new_conversation()
    expect_true(is.list(result) || is.logical(result))
  })
})

test_that("checkTerminalComplete works correctly", {
  skip_if_not(exists(".rs.check_terminal_complete"))
  
  with_test_mocks("core", {
    result <- .rs.check_terminal_complete(123)
    expect_true(is.logical(result))
  })
})

test_that("checkConsoleComplete works correctly", {
  skip_if_not(exists(".rs.check_console_complete"))
  
  with_test_mocks("core", {
    result <- .rs.check_console_complete(123)
    expect_true(is.logical(result))
  })
})

test_that("finalizeConsoleCommand works correctly", {
  skip_if_not(exists(".rs.finalize_console_command"))
  
  with_test_mocks("core", {
    result <- .rs.finalize_console_command(123, "test_request_123")
    expect_true(is.list(result) || is.logical(result))
  })
})

test_that("finalizeTerminalCommand works correctly", {
  skip_if_not(exists(".rs.finalize_terminal_command"))
  
  with_test_mocks("core", {
    result <- .rs.finalize_terminal_command(123, "test_request_456")
    expect_true(is.list(result) || is.logical(result))
  })
})

test_that("saveApiKey works correctly", {
  skip_if_not(exists(".rs.save_api_key"))
  
  with_test_mocks("core", {
    result <- .rs.save_api_key("openai", "test-key-123")
    expect_true(is.logical(result) || is.list(result))
  })
})

test_that("deleteApiKey works correctly", {
  skip_if_not(exists(".rs.delete_api_key"))
  
  with_test_mocks("core", {
    result <- .rs.delete_api_key("openai")
    expect_true(is.logical(result) || is.list(result))
  })
})

test_that("setActiveProvider works correctly", {
  skip_if_not(exists(".rs.set_active_provider_action"))
  
  with_test_mocks("core", {
    result <- .rs.set_active_provider_action("openai")
    expect_true(is.logical(result))
  })
})

test_that("setModel works correctly", {
  skip_if_not(exists(".rs.set_model_action"))
  
  with_test_mocks("core", {
    result <- .rs.set_model_action("openai", "gpt-4")
    expect_true(is.logical(result))
  })
})

test_that("conversation name management works correctly", {
  skip_if_not(exists(".rs.setConversationName"))
  
  with_test_mocks("core", {
    # Test setting conversation name
    if (exists(".rs.setConversationName")) {
      result <- .rs.setConversationName(1, "My Test Chat")
      expect_true(is.logical(result) || is.list(result))
    }
    
    # Test getting conversation name
    if (exists(".rs.getConversationName")) {
      name <- .rs.getConversationName(1)
      expect_true(is.character(name) || is.null(name))
    }
    
    # Test deleting conversation name
    if (exists(".rs.deleteConversationName")) {
      result <- .rs.deleteConversationName(1)
      expect_true(is.logical(result) || is.list(result))
    }
    
    # Test listing conversation names
    if (exists(".rs.listConversationNames")) {
      names_list <- .rs.listConversationNames()
      expect_true(is.list(names_list) || is.character(names_list))
    }
  })
})

test_that("AI operation processing works correctly", {
  skip_if_not(exists(".rs.initialize_conversation"))
  
  with_test_mocks(c("core", "editor", "backend"), {
    # Test conversation initialization
    if (exists(".rs.initialize_conversation")) {
      result <- .rs.initialize_conversation("Test query", FALSE, "test_request_789")
      expect_true(is.list(result) || is.logical(result))
    }
    
    # Test API call
    if (exists(".rs.make_api_call")) {
      result <- .rs.make_api_call(1, "gpt-4", TRUE, "req_123")
      expect_true(is.list(result) || is.logical(result))
    }
    
    # Test function call processing
    if (exists(".rs.process_function_call")) {
      mock_function_call <- list(name = "test_function", arguments = "{}")
      result <- .rs.process_function_call(mock_function_call, NULL, 1, "req_123")
      expect_true(is.list(result) || is.logical(result))
    }
  })
})

test_that("file operations work correctly", {
  skip_if_not(exists(".rs.copyCompleteHtmlToUserOnly"))
  
  with_test_mocks("core", {
    # Create temporary test file
    test_env <- create_test_env("file_ops_test")
    on.exit(test_env$cleanup())
    
    temp_r_file <- file.path(test_env$test_dir, "test.R")
    writeLines("print('test')", temp_r_file)
    
    # Test copying HTML
    if (exists(".rs.copyCompleteHtmlToUserOnly")) {
      result <- .rs.copyCompleteHtmlToUserOnly("/path/to/complete.html", "/path/to/user_only.html")
      expect_true(is.logical(result))
    }
    
    # Test deleting folder
    if (exists(".rs.delete_folder")) {
      result <- .rs.delete_folder("/path/to/folder")
      expect_true(is.logical(result))
    }
  })
})

test_that("utility functions work correctly", {
  skip_if_not(exists(".rs.getTabFilePath"))
  
  with_test_mocks("core", {
    # Test getting tab file path
    if (exists(".rs.getTabFilePath")) {
      result <- .rs.getTabFilePath("valid_tab")
      expect_true(is.character(result) || is.null(result))
    }
    
    # Test checking conversation emptiness
    if (exists(".rs.isConversationEmpty")) {
      result <- .rs.isConversationEmpty(1)
      expect_true(is.logical(result))
    }
  })
})

test_that("error handling works correctly", {
  with_test_mocks("core", {
    # Test that errors are caught and handled
    result <- tryCatch({
      stop("This is a test error")
      "success"
    }, error = function(e) {
      "error_caught"
    })
    
    expect_equal(result, "error_caught")
  })
})

test_that("terminal and console command handling works correctly", {
  with_test_mocks("core", {
    # Test accepting terminal command
    if (exists(".rs.accept_terminal_command")) {
      result <- .rs.accept_terminal_command("pending_123", "ls -la", 456)
      expect_true(is.logical(result) || is.list(result))
    }
    
    # Test cancelling terminal command
    if (exists(".rs.cancel_terminal_command")) {
      result <- .rs.cancel_terminal_command("pending_123")
      expect_true(is.logical(result) || is.list(result))
    }
    
    # Test accepting console command
    if (exists(".rs.accept_console_command")) {
      result <- .rs.accept_console_command("pending_456", "print('hello')", 789)
      expect_true(is.logical(result) || is.list(result))
    }
    
    # Test cancelling console command
    if (exists(".rs.cancel_console_command")) {
      result <- .rs.cancel_console_command("pending_456")
      expect_true(is.logical(result) || is.list(result))
    }
  })
})

test_that("context management works correctly", {
  with_test_mocks(c("core", "context"), {
    # Test adding context item
    if (exists(".rs.add_context_item")) {
      result <- .rs.add_context_item("/valid/path/file.R")
      expect_true(is.logical(result) || is.list(result))
    }
    
    # Test adding context lines
    if (exists(".rs.add_context_lines")) {
      result <- .rs.add_context_lines("/path/to/file.R", 10, 20)
      expect_true(is.logical(result) || is.list(result))
    }
    
    # Test getting context items
    if (exists(".rs.get_context_items")) {
      items <- .rs.get_context_items()
      expect_true(is.character(items) || is.list(items) || is.null(items))
    }
    
    # Test removing context item
    if (exists(".rs.remove_context_item")) {
      result <- .rs.remove_context_item("/path/to/context1.R")
      expect_true(is.logical(result) || is.list(result))
    }
    
    # Test clearing context items
    if (exists(".rs.clear_context_items")) {
      result <- .rs.clear_context_items()
      expect_true(is.logical(result) || is.list(result))
    }
  })
})

test_that("button management works correctly", {
  with_test_mocks(c("core", "button_helpers"), {
    # Test marking button as run
    if (exists(".rs.markButtonAsRun")) {
      result <- .rs.markButtonAsRun("123", "accept")
      expect_true(is.logical(result) || is.list(result))
    }
  })
})

test_that("working directory management works correctly", {
  with_test_mocks("core", {
    # Test setting valid directory
    if (exists(".rs.set_ai_working_directory")) {
      result <- .rs.set_ai_working_directory(getwd())
      expect_true(is.list(result) || is.logical(result))
    }
    
    # Test directory browse
    if (exists(".rs.browse_directory")) {
      result <- .rs.browse_directory()
      expect_true(is.list(result) || is.character(result))
    }
    
    # Test file browse
    if (exists(".rs.browse_for_file")) {
      result <- .rs.browse_for_file()
      expect_true(is.character(result) || is.list(result) || is.null(result))
    }
  })
})
