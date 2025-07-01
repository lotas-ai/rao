# SessionSymbolIndex.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

.rs.addFunction("build_symbol_index", function(dir = getwd()) {
  # Validate directory
  if (!dir.exists(dir))
    stop("Directory does not exist: ", dir)
  
  # Make sure path is absolute
  dir <- normalizePath(dir, mustWork = TRUE)
  
  # Call C++ implementation
  invisible(.Call("rs_buildSymbolIndex", dir))
})

.rs.addFunction("find_symbol", function(name) {
  # Validate
  if (!is.character(name) || length(name) != 1)
    stop("Symbol name must be a single character string")
  
  # Clean up hashtags in search string (for headers)
  if (grepl("#", name)) {
    name <- gsub("^\\s*#+\\s*|\\s*#+\\s*$", "", name)
  }
  
  # Trim leading and trailing whitespace
  name <- trimws(name)
  
  # Call C++ implementation (case-insensitive search already handled in C++)
  result <- .Call("rs_findSymbol", name)
  
  # Return the result with a nice class
  class(result) <- c("rs_symbols", class(result))
  return(result)
})

# Function to find symbols that may contain spaces
.rs.addFunction("find_symbols_with_spaces", function(text) {
  # Get all symbols
  all_symbols <- .Call("rs_getAllSymbols")
  matches <- list()
  
  # Check for exact matches with spaces in symbol names
  for (symbol in all_symbols) {
    if (grepl("\\s", symbol$name) && grepl(symbol$name, text, fixed = TRUE)) {
      matches[[length(matches) + 1]] <- symbol
    }
  }
    
  # Return the result with a nice class if any matches found
  if (length(matches) > 0) {
    class(matches) <- c("rs_symbols", class(matches))
    return(matches)
  } else {
    return(NULL)
  }
})

# Function to search for symbols in text
.rs.addFunction("search_symbols_in_text", function(text) {
  # First search for symbols with spaces
  space_matches <- .rs.find_symbols_with_spaces(text)
  
  # Extract words from text to search as individual symbols
  words <- unlist(strsplit(text, "\\s+"))
  
  # Clean up any words that start with # (like headers)
  words <- gsub("^#+\\s*|\\s*#+$", "", words)
  
  # Remove empty strings and duplicates
  words <- unique(words[nzchar(words)])
  
  # Search for each word
  word_matches <- list()
  for (word in words) {
    if (nchar(word) > 2) {  # Only search for words with at least 3 characters
      matches <- .Call("rs_findSymbol", word)
      word_matches <- c(word_matches, matches)
    }
  }
  
  # Combine results, remove duplicates, and limit
  all_matches <- c(space_matches, word_matches)
  
  # Remove duplicates by checking file path and name
  unique_matches <- list()
  seen <- character(0)
  
  for (match in all_matches) {
    key <- paste0(match$name, "|", match$file, "|", match$line_start)
    if (!(key %in% seen)) {
      unique_matches[[length(unique_matches) + 1]] <- match
      seen <- c(seen, key)
    }
  }
  
  # Return the result with a nice class
  if (length(unique_matches) > 0) {
    class(unique_matches) <- c("rs_symbols", class(unique_matches))
    return(unique_matches)
  } else {
    return(NULL)
  }
})

.rs.addFunction("get_all_symbols", function() {
  # Call C++ implementation
  result <- .Call("rs_getAllSymbols")
  
  # Return the result with a nice class
  class(result) <- c("rs_symbols", class(result))
  return(result)
})

.rs.addFunction("get_symbols_for_file", function(file_path) {
  # Validate
  if (!is.character(file_path) || length(file_path) != 1)
    stop("File path must be a single character string")
  
  # Call C++ implementation
  result <- .Call("rs_getSymbolsForFile", file_path)
  
  # Return the result with a nice class
  class(result) <- c("rs_symbols", class(result))
  return(result)
})

# Function to index a specific symbol (file or directory)
.rs.addFunction("index_specific_symbol", function(path) {
  # Validate
  if (!is.character(path) || length(path) != 1)
    stop("Path must be a single character string")
  
  # Make sure path is absolute
  path <- normalizePath(path, mustWork = TRUE)
  
  # Call C++ implementation
  invisible(.Call("rs_indexSpecificSymbol", path))
})

# Function to ensure the symbol index is built before AI search
.rs.addFunction("ensure_symbol_index_for_ai_search", function(dir = getwd()) {
  # Make sure path is absolute
  dir <- normalizePath(dir, mustWork = TRUE)
  
  # Build or update the symbol index
  tryCatch({
    .rs.build_symbol_index(dir)
    return(TRUE)
  }, error = function(e) {
    warning("Failed to build symbol index: ", e$message)
    return(FALSE)
  })
})

# Function to remove the entire symbol index for the current working directory
.rs.addFunction("remove_symbol_index", function() {
  # Call C++ implementation to remove both in-memory and stored index data
  invisible(.Call("rs_removeSymbolIndex"))
})

# Function to quickly build symbol index framework without actual indexing
.rs.addFunction("build_symbol_index_quick", function(dir = getwd()) {
  # Validate directory
  if (!dir.exists(dir))
    stop("Directory does not exist: ", dir)
  
  # Make sure path is absolute
  dir <- normalizePath(dir, mustWork = TRUE)
  
  # Call C++ implementation to create framework only
  invisible(.Call("rs_buildSymbolIndexQuick", dir))
}) 