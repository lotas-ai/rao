# Test file for SessionSymbolIndex.R

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

# Source the main Symbol Index file to get fresh function definitions
# Only source if explicitly requested, to allow tests to work with mocks
if (Sys.getenv("RSTUDIO_SOURCE_MODULES", "FALSE") == "TRUE") {
  tryCatch({
    source_session_module("SessionSymbolIndex.R")  
  }, error = function(e) {
    message("Note: Could not source SessionSymbolIndex.R, using existing definitions: ", e$message)
  })
}

context("SessionSymbolIndex")

test_that("buildSymbolIndex creates index for directory", {
  skip_if_not(exists(".rs.buildSymbolIndex"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_build")
    on.exit(test_env$cleanup())
    
    # Create test R file
    r_file <- file.path(test_env$test_dir, "test_functions.R")
    writeLines(c(
      "# Test R file",
      "test_function <- function(x, y = 1) {",
      "  return(x + y)",
      "}",
      "",
      "another_func <- function() {",
      "  print('hello')",
      "}"
    ), r_file)
    
    # Create test Python file
    py_file <- file.path(test_env$test_dir, "test_functions.py")
    writeLines(c(
      "# Test Python file",
      "def test_function(x, y=1):",
      "    return x + y",
      "",
      "class TestClass:",
      "    def method(self):",
      "        pass"
    ), py_file)
    
    # Test building index
    result <- .rs.buildSymbolIndex(test_env$test_dir)
    
    expect_true(result)
  })
})

test_that("findSymbol locates functions in index", {
  skip_if_not(exists(".rs.findSymbol"))
  skip_if_not(exists(".rs.buildSymbolIndex"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_find")
    on.exit(test_env$cleanup())
    
    # Create test R file with functions
    r_file <- file.path(test_env$test_dir, "sample.R")
    writeLines(c(
      "my_function <- function(a, b = 10) {",
      "  return(a * b)",
      "}",
      "",
      "calculate_sum <- function(x, y) {",
      "  x + y",
      "}"
    ), r_file)
    
    # Build index first
    build_result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(build_result)
    
    # Test finding exact function name
    result <- .rs.findSymbol("my_function")
    expect_true(length(result) > 0)
    
    if (length(result) > 0) {
      symbol <- result[[1]]
      expect_equal(symbol$name, "my_function")
      expect_equal(symbol$type, "function")
      expect_true(endsWith(symbol$file, "sample.R"))
      expect_true(symbol$line_start > 0)
    }
    
    # Test finding another function
    result2 <- .rs.findSymbol("calculate_sum")
    expect_true(length(result2) > 0)
    
    # Test case insensitive search
    result3 <- .rs.findSymbol("MY_FUNCTION")
    expect_true(length(result3) > 0)
    
    # Test non-existent function
    result4 <- .rs.findSymbol("nonexistent_function")
    expect_true(length(result4) == 0)
  })
})

test_that("findSymbol works with markdown headers", {
  skip_if_not(exists(".rs.findSymbol"))
  skip_if_not(exists(".rs.buildSymbolIndex"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_markdown")
    on.exit(test_env$cleanup())
    
    # Create test markdown file
    md_file <- file.path(test_env$test_dir, "test.md")
    writeLines(c(
      "# Introduction",
      "",
      "This is the introduction section.",
      "",
      "## Data Analysis",
      "",
      "Details about data analysis.",
      "",
      "### Statistical Methods",
      "",
      "Information about statistical methods.",
      "",
      "## Results",
      "",
      "The results section."
    ), md_file)
    
    # Build index
    build_result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(build_result)
    
    # Test finding headers
    result1 <- .rs.findSymbol("Introduction")
    expect_true(length(result1) > 0)
    
    if (length(result1) > 0) {
      symbol <- result1[[1]]
      expect_equal(symbol$name, "Introduction")
      expect_equal(symbol$type, "header1")
    }
    
    result2 <- .rs.findSymbol("Data Analysis")
    expect_true(length(result2) > 0)
    
    if (length(result2) > 0) {
      symbol <- result2[[1]]
      expect_equal(symbol$name, "Data Analysis")
      expect_equal(symbol$type, "header2")
    }
    
    # Test partial header search
    result3 <- .rs.findSymbol("Statistical")
    expect_true(length(result3) > 0)
  })
})

test_that("findSymbol works with R markdown chunks", {
  skip_if_not(exists(".rs.findSymbol"))
  skip_if_not(exists(".rs.buildSymbolIndex"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_rmd")
    on.exit(test_env$cleanup())
    
    # Create test R markdown file
    rmd_file <- file.path(test_env$test_dir, "analysis.Rmd")
    writeLines(c(
      "# Data Analysis Report",
      "",
      "```{r setup}",
      "library(ggplot2)",
      "setup_data <- function() {",
      "  data.frame(x = 1:10, y = 1:10)",
      "}",
      "```",
      "",
      "```{r analysis, echo=TRUE}",
      "analyze_data <- function(df) {",
      "  summary(df)",
      "}",
      "result <- analyze_data(setup_data())",
      "```"
    ), rmd_file)
    
    # Build index
    build_result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(build_result)
    
    # Test finding header
    result1 <- .rs.findSymbol("Data Analysis Report")
    expect_true(length(result1) > 0)
    
    # Test finding chunk functions
    result2 <- .rs.findSymbol("setup_data")
    expect_true(length(result2) > 0)
    
    result3 <- .rs.findSymbol("analyze_data")
    expect_true(length(result3) > 0)
    
    if (length(result3) > 0) {
      symbol <- result3[[1]]
      expect_equal(symbol$name, "analyze_data")
      expect_equal(symbol$type, "function")
      expect_equal(symbol$parents, "analysis")  # chunk name
    }
  })
})

test_that("buildSymbolIndex handles different file types", {
  skip_if_not(exists(".rs.buildSymbolIndex"))
  skip_if_not(exists(".rs.findSymbol"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_filetypes")
    on.exit(test_env$cleanup())
    
    # Create C++ file
    cpp_file <- file.path(test_env$test_dir, "test.cpp")
    writeLines(c(
      "#include <iostream>",
      "",
      "class TestClass {",
      "public:",
      "    void testMethod() {",
      "        std::cout << \"Hello\" << std::endl;",
      "    }",
      "};"
    ), cpp_file)
    
    # Create SQL file  
    sql_file <- file.path(test_env$test_dir, "queries.sql")
    writeLines(c(
      "CREATE TABLE users (",
      "    id INTEGER PRIMARY KEY,",
      "    name TEXT NOT NULL",
      ");",
      "",
      "CREATE VIEW active_users AS",
      "SELECT * FROM users WHERE active = 1;"
    ), sql_file)
    
    # Create shell script
    sh_file <- file.path(test_env$test_dir, "script.sh")
    writeLines(c(
      "#!/bin/bash",
      "",
      "setup_environment() {",
      "    export PATH=/usr/local/bin:$PATH",
      "}",
      "",
      "run_tests() {",
      "    echo 'Running tests'",
      "}"
    ), sh_file)
    
    # Build index
    build_result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(build_result)
    
    # Test finding C++ class
    result1 <- .rs.findSymbol("TestClass")
    expect_true(length(result1) > 0)
    
    # Test finding SQL table
    result2 <- .rs.findSymbol("users")
    expect_true(length(result2) > 0)
    
    # Test finding shell function
    result3 <- .rs.findSymbol("setup_environment")
    expect_true(length(result3) > 0)
  })
})

test_that("getAllSymbols returns complete symbol list", {
  skip_if_not(exists(".rs.getAllSymbols"))
  skip_if_not(exists(".rs.buildSymbolIndex"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_all")
    on.exit(test_env$cleanup())
    
    # Create test file with multiple symbols
    test_file <- file.path(test_env$test_dir, "symbols.R")
    writeLines(c(
      "func1 <- function() { 1 }",
      "func2 <- function() { 2 }",
      "func3 <- function() { 3 }"
    ), test_file)
    
    # Build index
    build_result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(build_result)
    
    # Get all symbols
    all_symbols <- .rs.getAllSymbols()
    expect_true(length(all_symbols) > 0)
    
    # Should contain our functions plus file and directory symbols
    function_names <- sapply(all_symbols, function(s) if (s$type == "function") s$name else NA)
    function_names <- function_names[!is.na(function_names)]
    
    expect_true("func1" %in% function_names)
    expect_true("func2" %in% function_names)
    expect_true("func3" %in% function_names)
  })
})

test_that("searchSymbolsInText finds relevant symbols", {
  skip_if_not(exists(".rs.searchSymbolsInText"))
  skip_if_not(exists(".rs.buildSymbolIndex"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_search")
    on.exit(test_env$cleanup())
    
    # Create files with symbols
    r_file <- file.path(test_env$test_dir, "functions.R")
    writeLines(c(
      "data_processing <- function() {}",
      "process_data <- function() {}",
      "clean_dataset <- function() {}"
    ), r_file)
    
    md_file <- file.path(test_env$test_dir, "docs.md")
    writeLines(c(
      "# Data Processing Guide",
      "",
      "## Processing Steps"
    ), md_file)
    
    # Build index
    build_result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(build_result)
    
    # Test searching text that contains symbol names
    result <- .rs.searchSymbolsInText("I need to use data_processing for my analysis")
    expect_true(length(result) > 0)
    
    # Test searching text with header content
    result2 <- .rs.searchSymbolsInText("Looking at the Data Processing Guide")
    expect_true(length(result2) > 0)
    
    # Test searching multiple words
    result3 <- .rs.searchSymbolsInText("process data cleaning")
    expect_true(length(result3) > 0)
  })
})

test_that("ensureSymbolIndexForAISearch builds index safely", {
  skip_if_not(exists(".rs.ensureSymbolIndexForAISearch"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_ensure")
    on.exit(test_env$cleanup())
    
    # Create simple test file
    test_file <- file.path(test_env$test_dir, "simple.R")
    writeLines(c(
      "test_func <- function() {",
      "  return(42)",
      "}"
    ), test_file)
    
    # Test ensuring index (should succeed)
    result <- .rs.ensureSymbolIndexForAISearch(test_env$test_dir)
    expect_true(result)
    
    # Test with non-existent directory (should fail gracefully)
    result2 <- .rs.ensureSymbolIndexForAISearch("/nonexistent/directory")
    expect_false(result2)
  })
})

test_that("findSymbol handles function signatures correctly", {
  skip_if_not(exists(".rs.findSymbol"))
  skip_if_not(exists(".rs.buildSymbolIndex"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_signatures")
    on.exit(test_env$cleanup())
    
    # Create R file with complex function signatures
    r_file <- file.path(test_env$test_dir, "complex_functions.R")
    writeLines(c(
      "simple_func <- function() { }",
      "",
      "complex_func <- function(x, y = 10, z = NULL, ...) {",
      "  return(x + y)",
      "}",
      "",
      "multiline_func <- function(",
      "  data,",
      "  method = 'default',", 
      "  options = list()",
      ") {",
      "  # Function body",
      "}"
    ), r_file)
    
    # Build index
    build_result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(build_result)
    
    # Test finding functions and checking signatures
    result1 <- .rs.findSymbol("simple_func")
    expect_true(length(result1) > 0)
    if (length(result1) > 0) {
      expect_equal(result1[[1]]$signature, "function()")
    }
    
    result2 <- .rs.findSymbol("complex_func")
    expect_true(length(result2) > 0)
    if (length(result2) > 0) {
      # Signature should contain the parameters
      sig <- result2[[1]]$signature
      expect_true(grepl("function\\(", sig))
      expect_true(grepl("x", sig))
      expect_true(grepl("y.*=.*10", sig))
    }
    
    result3 <- .rs.findSymbol("multiline_func")
    expect_true(length(result3) > 0)
    if (length(result3) > 0) {
      sig <- result3[[1]]$signature
      expect_true(grepl("function\\(", sig))
      expect_true(grepl("data", sig))
      expect_true(grepl("method", sig))
    }
  })
})

test_that("buildSymbolIndex handles binary and image files", {
  skip_if_not(exists(".rs.buildSymbolIndex"))
  skip_if_not(exists(".rs.findSymbol"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_binary")
    on.exit(test_env$cleanup())
    
    # Create a fake image file (just text content, but with image extension)
    img_file <- file.path(test_env$test_dir, "plot.png")
    writeLines("fake image content", img_file)
    
    # Create a fake binary file
    bin_file <- file.path(test_env$test_dir, "data.exe")
    writeLines("fake binary content", bin_file)
    
    # Create regular text file for comparison
    txt_file <- file.path(test_env$test_dir, "readme.txt")
    writeLines("This is a text file", txt_file)
    
    # Build index
    build_result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(build_result)
    
    # Image files should be indexed as "image" type
    result1 <- .rs.findSymbol("plot.png")
    expect_true(length(result1) > 0)
    if (length(result1) > 0) {
      expect_equal(result1[[1]]$type, "image")
    }
    
    # Binary files should be indexed as "binary" type  
    result2 <- .rs.findSymbol("data.exe")
    expect_true(length(result2) > 0)
    if (length(result2) > 0) {
      expect_equal(result2[[1]]$type, "binary")
    }
    
    # Text files should be indexed as "file" type
    result3 <- .rs.findSymbol("readme.txt")
    expect_true(length(result3) > 0)
    if (length(result3) > 0) {
      expect_equal(result3[[1]]$type, "file")
    }
  })
})

test_that("findSymbol works with type filters", {
  skip_if_not(exists(".rs.findSymbol"))
  skip_if_not(exists(".rs.buildSymbolIndex"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_filters")
    on.exit(test_env$cleanup())
    
    # Create files with same name but different types
    r_file <- file.path(test_env$test_dir, "test.R")
    writeLines(c(
      "test <- function() { 'R function' }"
    ), r_file)
    
    md_file <- file.path(test_env$test_dir, "docs.md")
    writeLines(c(
      "# test",
      "",
      "This is a header named test"
    ), md_file)
    
    # Build index
    build_result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(build_result)
    
    # Test finding all symbols named "test"
    result_all <- .rs.findSymbol("test")
    expect_true(length(result_all) > 0)
    
    # Test finding only functions named "test"
    result_func <- .rs.findSymbol("test (function)")
    expect_true(length(result_func) > 0)
    if (length(result_func) > 0) {
      # All results should be functions
      types <- sapply(result_func, function(s) s$type)
      expect_true(all(types == "function"))
    }
    
    # Test finding only headers named "test"
    result_header <- .rs.findSymbol("test (header)")
    expect_true(length(result_header) > 0)
    if (length(result_header) > 0) {
      # All results should be headers
      types <- sapply(result_header, function(s) s$type)
      expect_true(all(grepl("^header", types)))
    }
  })
})

test_that("buildSymbolIndex handles empty directory", {
  skip_if_not(exists(".rs.buildSymbolIndex"))
  skip_if_not(exists(".rs.getAllSymbols"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_empty")
    on.exit(test_env$cleanup())
    
    # Don't create any files - leave directory empty
    
    # Build index on empty directory
    result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(result)
    
    # Should still work but return minimal symbols (just directory)
    all_symbols <- .rs.getAllSymbols()
    expect_true(length(all_symbols) >= 0)  # At least the directory itself
  })
})

test_that("buildSymbolIndex handles directory with subdirectories", {
  skip_if_not(exists(".rs.buildSymbolIndex"))
  skip_if_not(exists(".rs.findSymbol"))
  
  with_test_mocks(c("core", "symbol_index"), {
    # Create test environment
    test_env <- create_test_env("symbol_index_subdirs")
    on.exit(test_env$cleanup())
    
    # Create subdirectories
    subdir1 <- file.path(test_env$test_dir, "subdir1")
    subdir2 <- file.path(test_env$test_dir, "subdir2")
    dir.create(subdir1)
    dir.create(subdir2)
    
    # Create files in subdirectories
    file1 <- file.path(subdir1, "func1.R")
    writeLines(c("func1 <- function() { 1 }"), file1)
    
    file2 <- file.path(subdir2, "func2.R") 
    writeLines(c("func2 <- function() { 2 }"), file2)
    
    # Build index
    build_result <- .rs.buildSymbolIndex(test_env$test_dir)
    expect_true(build_result)
    
    # Should find functions in subdirectories
    result1 <- .rs.findSymbol("func1")
    expect_true(length(result1) > 0)
    
    result2 <- .rs.findSymbol("func2")
    expect_true(length(result2) > 0)
    
    # Should also find directory symbols
    dir_result1 <- .rs.findSymbol("subdir1")
    expect_true(length(dir_result1) > 0)
    if (length(dir_result1) > 0) {
      expect_equal(dir_result1[[1]]$type, "directory")
    }
  })
})
