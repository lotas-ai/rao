# SessionAiParse.R
#
# Copyright (C) 2025 by Lotas Inc.
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

# Extract function calls from R code
.rs.addFunction("extract_r_functions", function(r_code) {
   tryCatch({
      if (is.null(r_code) || is.na(r_code) || nchar(trimws(r_code)) == 0) {
         return(character(0))
      }
      
      # Trim triple backticks with optional language specifiers
      # Remove leading ```[optional language]
      trimmed_code <- gsub("^```[a-zA-Z]*\\s*\\n?", "", r_code, perl = TRUE)
      # Remove trailing ```
      trimmed_code <- gsub("\\n?```\\s*$", "", trimmed_code, perl = TRUE)
      # Remove any remaining ``` lines
      trimmed_code <- gsub("```\\n", "", trimmed_code, perl = TRUE)
      trimmed_code <- trimws(trimmed_code)
      
      if (nchar(trimmed_code) == 0) {
         return(character(0))
      }
      
      expr <- parse(text = trimmed_code, keep.source = TRUE)
      if (length(expr) == 0) {
         return(character(0))
      }
      
      parse_data <- getParseData(expr)
      if (is.null(parse_data) || nrow(parse_data) == 0) {
         return(character(0))
      }
      
      # Extract function calls and assignment operators
      function_tokens <- c("SYMBOL_FUNCTION_CALL", "SPECIAL", "LEFT_ASSIGN", "RIGHT_ASSIGN", "EQ_ASSIGN")
      functions <- parse_data[parse_data$token %in% function_tokens, ]
      
      function_names <- functions$text
      function_names <- function_names[!is.na(function_names) & nchar(function_names) > 0]
      function_names <- unique(function_names)
      
      return(sort(function_names))
      
   }, error = function(e) {
      return(character(0))
   })
})

# Extract function/command calls from bash code using official POSIX grammar
.rs.addFunction("extract_bash_functions", function(bash_code) {
   tryCatch({
      if (is.null(bash_code) || is.na(bash_code) || nchar(trimws(bash_code)) == 0) {
         return(character(0))
      }
      
      # Trim triple backticks with optional language specifiers  
      # Remove leading ```[optional language]
      trimmed_code <- gsub("^```[a-zA-Z]*\\s*\\n?", "", bash_code, perl = TRUE)
      # Remove trailing ```
      trimmed_code <- gsub("\\n?```\\s*$", "", trimmed_code, perl = TRUE)
      # Remove any remaining ``` lines
      trimmed_code <- gsub("```\\n", "", trimmed_code, perl = TRUE)
      trimmed_code <- trimws(trimmed_code)
      
      if (nchar(trimmed_code) == 0) {
         return(character(0))
      }
      
      # Use official POSIX shell grammar-based parser
      return(.rs.parse_shell_commands(trimmed_code))
      
   }, error = function(e) {
      return(character(0))
   })
})

# Parse shell commands using official POSIX grammar
.rs.addFunction("parse_shell_commands", function(bash_code) {
   tryCatch({
      # Tokenize the input according to POSIX shell grammar
      tokens <- .rs.tokenize_shell_input(bash_code)
      
      # Parse tokens to identify simple commands
      commands <- .rs.extract_simple_commands(tokens)
      
      # Return unique sorted command names
      return(sort(unique(commands)))
      
   }, error = function(e) {
      return(character(0))
   })
})

# Tokenize shell input according to POSIX lexical grammar
.rs.addFunction("tokenize_shell_input", function(input) {
   # Remove comments and handle line continuations
   cleaned_input <- .rs.clean_shell_input(input)
   
   # Split into tokens based on POSIX token recognition rules
   tokens <- .rs.split_into_tokens(cleaned_input)
   
   return(tokens)
})

# Clean shell input: remove comments, handle line continuations
.rs.addFunction("clean_shell_input", function(input) {
   # Split into lines
   lines <- strsplit(input, "\n", fixed = TRUE)[[1]]
   cleaned_lines <- character(0)
   
   for (line in lines) {
      # Handle line continuations (backslash-newline)
      while (grepl("\\\\\\s*$", line)) {
         line <- gsub("\\\\\\s*$", "", line)
         # In real implementation, would get next line
      }
      
      # Remove comments (# to end of line, but not in quotes)
      # Simplified: just remove comments not in quotes
      line <- .rs.remove_comments_from_line(line)
      
      if (nchar(trimws(line)) > 0) {
         cleaned_lines <- c(cleaned_lines, line)
      }
   }
   
   return(paste(cleaned_lines, collapse = "\n"))
})

# Remove comments from a single line (simplified)
.rs.addFunction("remove_comments_from_line", function(line) {
   # This is simplified - real implementation would track quote state
   # For now, just remove # comments that appear to be outside quotes
   
   # Don't remove # if it's inside single quotes
   if (grepl("'[^']*#[^']*'", line)) {
      return(line)  # Has # inside quotes, don't modify
   }
   
   # Remove comment from # to end of line if not quoted
   result <- gsub("#.*$", "", line)
   return(result)
})

# Split cleaned input into tokens based on POSIX rules
.rs.addFunction("split_into_tokens", function(input) {
   tokens <- character(0)
   
   # Split on whitespace first (simplified tokenization)
   words <- unlist(strsplit(input, "\\s+", perl = TRUE))
   words <- words[nchar(words) > 0]
   
   # Further process each word for operators and special characters
   for (word in words) {
      word_tokens <- .rs.process_word_for_tokens(word)
      tokens <- c(tokens, word_tokens)
   }
   
   return(tokens)
})

# Process a word to extract tokens (handle operators, etc.)
.rs.addFunction("process_word_for_tokens", function(word) {
   # Handle shell operators and special characters
   # This is simplified - real implementation would be more complex
   
   # Split on shell operators while preserving them
   operators <- c("\\|\\|", "&&", ">>", "<<", "\\|&", "\\|", "&", ";", "\\(", "\\)", "<", ">")
   
   tokens <- word
   for (op in operators) {
      new_tokens <- character(0)
      for (token in tokens) {
         # Split on operator and keep the operator
         parts <- strsplit(token, paste0("(", op, ")"), perl = TRUE)[[1]]
         if (length(parts) > 1) {
            # Reconstruct with operators
            reconstructed <- character(0)
            for (i in seq_along(parts)) {
               if (nchar(parts[i]) > 0) {
                  reconstructed <- c(reconstructed, parts[i])
               }
               if (i < length(parts)) {
                  # Add the operator back
                  op_clean <- gsub("\\\\", "", op)
                  reconstructed <- c(reconstructed, op_clean)
               }
            }
            new_tokens <- c(new_tokens, reconstructed)
         } else {
            new_tokens <- c(new_tokens, token)
         }
      }
      tokens <- new_tokens
   }
   
   return(tokens[nchar(tokens) > 0])
})

# Extract simple commands from tokens according to POSIX grammar
.rs.addFunction("extract_simple_commands", function(tokens) {
   commands <- character(0)
   i <- 1
   
   while (i <= length(tokens)) {
      token <- tokens[i]
      
      # Skip operators and control structures
      if (token %in% c("||", "&&", "|", "&", ";", "(", ")", "<", ">", ">>", "<<")) {
         i <- i + 1
         next
      }
      
      # Skip assignments (word=value)
      if (grepl("^[a-zA-Z_][a-zA-Z0-9_]*=", token)) {
         i <- i + 1
         next
      }
      
      # Skip control flow keywords
      if (token %in% c("if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", 
                       "case", "esac", "function", "select", "time", "coproc", "[[", "]]")) {
         i <- i + 1
         next
      }
      
      # This should be a command name - extract it
      # Remove any path components to get just the command
      command_name <- basename(token)
      
      # Only include if it looks like a valid command name (POSIX identifier)
      if (grepl("^[a-zA-Z_][a-zA-Z0-9_.-]*$", command_name)) {
         commands <- c(commands, command_name)
      }
      
      # Skip forward past arguments until next separator
      i <- .rs.skip_command_arguments(tokens, i + 1)
   }
   
   return(commands)
})

# Skip past command arguments to find next command
.rs.addFunction("skip_command_arguments", function(tokens, start_index) {
   i <- start_index
   while (i <= length(tokens)) {
      token <- tokens[i]
      # Stop at command separators
      if (token %in% c("||", "&&", "|", "&", ";", "(", ")", "\n")) {
         break
      }
      i <- i + 1
   }
   return(i)
})

# JSON RPC handlers for function extraction
.rs.addJsonRpcHandler("extract_r_functions", function(r_code) {
   return(.rs.extract_r_functions(r_code))
})

.rs.addJsonRpcHandler("extract_bash_functions", function(bash_code) {
   return(.rs.extract_bash_functions(bash_code))
})