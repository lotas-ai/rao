# SessionAiContext.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

.rs.setVar("context_items", list())

# Function to get categorized environment variables like RStudio's Environment pane
.rs.addFunction("get_categorized_environment_variables", function(env = globalenv(), include_hidden = FALSE) {
   
   # Initialize categories based on RStudio's classification
   categories <- list(
      Data = list(),      # data.frame, matrix, list, S4, etc.
      Function = list(),  # functions
      Value = list()      # simple values (numeric, character, etc.)
   )
   
   # Get all object names from the environment
   obj_names <- tryCatch({
      ls(env, all.names = include_hidden)
   }, error = function(e) {
      return(character(0))
   })
   
   if (length(obj_names) == 0) {
      return(categories)
   }
   
   # Define data classes (from RStudio's DATA_CLASSES)
   data_classes <- c("matrix", "data.frame", "cast_df", "xts", "DataFrame")
   
   # Define hierarchical classes (from RStudio's HIERARCHICAL_CLASSES)  
   hierarchical_classes <- c("list", "environment", "S4")
   
   # Process each object
   for (obj_name in obj_names) {
      tryCatch({
         # Get object description using SessionEnvironment.R functions
         obj_info <- .rs.describeObject(env, obj_name, computeSize = FALSE)
         
         if (is.null(obj_info)) {
            next
         }
         
         # Get the object itself to determine category
         obj <- get(obj_name, env)
         obj_classes <- class(obj)
         obj_type <- obj_info$type
         
         # Determine category based on RStudio's logic
         category <- "Value"  # default
         
         # Check if it's a data object (tabular or hierarchical)
         is_data <- FALSE
         is_tabular <- any(obj_classes %in% data_classes) || 
                      (!is.null(obj_info$is_data) && obj_info$is_data)
         is_hierarchical <- any(obj_classes %in% hierarchical_classes) && !is_tabular
         
         if (is_tabular || is_hierarchical) {
            category <- "Data"
         } else if (.rs.isFunction(obj) || obj_type == "function" || obj_type == "functionWithTrace") {
            category <- "Function"
         } else {
            category <- "Value"
         }
         
         # Create description using SessionEnvironment.R functions
         description <- ""
         if (!is.null(obj_info$description) && nchar(obj_info$description) > 0) {
            description <- obj_info$description
         } else if (!is.null(obj_info$value) && obj_info$value != "NO_VALUE") {
            description <- obj_info$value
         } else {
            # Fallback to valueDescription
            description <- .rs.valueDescription(obj)
         }
         
         # Add to appropriate category
         categories[[category]][[length(categories[[category]]) + 1]] <- list(
            name = obj_name,
            description = description
         )
         
      }, error = function(e) {
         # If there's an error describing the object, add it to Value category with error description
         categories[["Value"]][[length(categories[["Value"]]) + 1]] <- list(
            name = obj_name,
            description = paste("Error:", e$message)
         )
      })
   }
   
   return(categories)
})

.rs.addFunction("browse_for_file", function() {
   result <- .Call("rs_openFileDialog",
         3L,
         "Select File or Directory",
         "Select",
         getwd(),
         "",
         TRUE,
         PACKAGE = "(embedding)")
   
   if (!is.null(result) && (file.exists(result) || .rs.is_file_open_in_editor(result))) {
      result <- path.expand(result)
      
      context_items <- .rs.getVar("context_items")
      
      paths <- sapply(context_items, function(item) path.expand(item$path))
      if (!result %in% paths) {
         # Check if this is a directory (only works for disk files)
         is_directory <- FALSE
         if (file.exists(result)) {
            is_directory <- file.info(result)$isdir
         }
      
         context_items[[length(context_items) + 1]] <- list(
            path = result,
            name = basename(result),
            type = if(is_directory) "directory" else "file",
            timestamp = Sys.time()
         )
         .rs.setVar("context_items", context_items)
         
         # Index for symbols if file exists on disk or is open in editor
         if (file.exists(result) || .rs.is_file_open_in_editor(result)) {
            tryCatch({
               # First, quickly build the symbol index framework for the working directory
               .rs.build_symbol_index_quick()
               
               # Then index the specific file/directory to ensure it's included
               .rs.index_specific_symbol(result)
            }, error = function(e) {
            })
         }
      }
      
      return(result)
   } else {
      return(NULL)
   }
})

.rs.addFunction("add_context_item", function(path) {
   if (is.null(path) || !is.character(path) || length(path) == 0 || nchar(path) == 0) {
      return(FALSE)
   }
   
   path <- path.expand(path)
   
   context_items <- .rs.getVar("context_items")
   if (!is.list(context_items)) {
      context_items <- list()
      .rs.setVar("context_items", context_items)
   }

   # Check for exact duplicate: same path AND no line numbers (both should be null)
   for (i in seq_along(context_items)) {
      item <- context_items[[i]]
      if (!is.null(item$path) && path.expand(item$path) == path && 
          is.null(item$start_line) && is.null(item$end_line)) {
         # Exact duplicate found: same path with no line numbers
         return(TRUE)
      }
   }
   
   # Check if this is a directory (only works for disk files)
   is_directory <- FALSE
   if (file.exists(path)) {
      is_directory <- file.info(path)$isdir
   }
   
   # For context items, use intelligent naming that handles __UNSAVED__ patterns
   # Get all existing context paths for duplicate detection
   all_context_paths <- sapply(context_items, function(item) item$path)
   all_context_paths <- c(all_context_paths, path)  # Include current path
   
   new_item <- list(
      path = path,
      name = .rs.get_unique_display_name(path, all_context_paths),
      type = if(is_directory) "directory" else "file",
      timestamp = Sys.time()
   )
   
   context_items[[length(context_items) + 1]] <- new_item
   
   .rs.setVar("context_items", context_items)
   
   # Index for symbols if file exists on disk or is open in editor
   if (file.exists(path) || .rs.is_file_open_in_editor(path)) {
      tryCatch({
         # First, quickly build the symbol index framework for the working directory
         .rs.build_symbol_index_quick()
         
         # Then index the specific file/directory to ensure it's included
         .rs.index_specific_symbol(path)
      }, error = function(e) {
      })
   }
   
   return(TRUE)
})

.rs.addFunction("get_context_items", function(expand_directories = FALSE) {
   # Always ensure no duplicates before returning
   .rs.cleanup_context_items()
   
   context_items <- .rs.getVar("context_items")
   
   if (length(context_items) == 0) {
     return(character(0))
   }
   
   # Collect all context paths for duplicate detection
   all_context_paths <- sapply(context_items, function(item) item$path)
   
   all_paths <- character(0)
   for (i in 1:length(context_items)) {
      item <- context_items[[i]]
      if (!is.null(item$path)) {
         normalized_path <- path.expand(item$path)
         
         # If this item has line numbers, use the stored name which includes line numbers
         if (!is.null(item$start_line) && !is.null(item$end_line)) {
            # Use item$name which contains line numbers like "filename.R (10-20)"
            display_path <- paste0(normalized_path, "|", item$name)
         } else {
            # For regular files, use intelligent naming for display
            display_name <- .rs.get_unique_display_name(normalized_path, all_context_paths)
            display_path <- normalized_path
         }
         
         all_paths <- c(all_paths, display_path)
         
         if (expand_directories && !is.null(item$type) && 
             item$type == "directory" && dir.exists(normalized_path)) {
            
            dir_files <- list.files(normalized_path, full.names = TRUE, recursive = FALSE)
            
            if (length(dir_files) > 0) {
               all_paths <- c(all_paths, dir_files)
            }
         }
      }
   }
   
   all_paths <- unique(all_paths)
   if (!is.character(all_paths)) {
     all_paths <- as.character(all_paths)
   }
   
   return(all_paths)
})

.rs.addFunction("remove_context_item", function(path_or_unique_id) {
   context_items <- .rs.getVar("context_items")
   if (length(context_items) == 0) {
      return(FALSE)
   }
   
   # Don't expand the path if it contains | since the part after | is not a path
   if (grepl("\\|", path_or_unique_id)) {
      # This is a unique ID with display name (path|display_name)
      # Use a more robust split approach
      pipe_pos <- regexpr("\\|", path_or_unique_id)
      if (pipe_pos > 0) {
         target_path <- path.expand(substr(path_or_unique_id, 1, pipe_pos - 1))
         target_display_name <- substr(path_or_unique_id, pipe_pos + 1, nchar(path_or_unique_id))
      } else {
         # Fallback - treat as regular path
         target_path <- path.expand(path_or_unique_id)
         target_display_name <- NULL
      }
      
      # Find the item that matches both path and display name
      for (i in seq_along(context_items)) {
         item <- context_items[[i]]
         if (!is.null(item$path) && path.expand(item$path) == target_path &&
             !is.null(item$name) && !is.null(target_display_name) && item$name == target_display_name) {
            context_items <- context_items[-i]
            .rs.setVar("context_items", context_items)
            return(TRUE)
         }
      }
   } else {
      # This is just a path - remove only the item without line numbers
      target_path <- path.expand(path_or_unique_id)
      
      for (i in seq_along(context_items)) {
         item <- context_items[[i]]
         if (!is.null(item$path) && path.expand(item$path) == target_path &&
             is.null(item$start_line) && is.null(item$end_line)) {
            context_items <- context_items[-i]
            .rs.setVar("context_items", context_items)
            return(TRUE)
         }
      }
   }
   
   return(FALSE)
})

.rs.addFunction("clear_context_items", function() {
   context_items <- .rs.getVar("context_items")
   .rs.setVar("context_items", list())
   return(NULL)
})

.rs.addFunction("cleanup_context_items", function() {
   context_items <- .rs.getVar("context_items")
   if (length(context_items) == 0) {
      return(NULL)
   }
   
   seen_unique_ids <- c()
   cleaned_items <- list()
   
   for (i in 1:length(context_items)) {
      item <- context_items[[i]]
      if (!is.null(item$path)) {
         normalized_path <- path.expand(item$path)
         
         # Create unique identifier that distinguishes between regular files and files with line numbers
         if (!is.null(item$start_line) && !is.null(item$end_line)) {
            # For files with line numbers, include line range in unique ID
            unique_id <- paste0(normalized_path, "|", item$start_line, "-", item$end_line)
         } else {
            # For regular files, just use the path but mark it as regular
            unique_id <- paste0(normalized_path, "|regular")
         }
         
         if (!(unique_id %in% seen_unique_ids)) {
            seen_unique_ids <- c(seen_unique_ids, unique_id)
            
            item$path <- normalized_path
            cleaned_items[[length(cleaned_items) + 1]] <- item
         }
      }
   }
   
   .rs.setVar("context_items", cleaned_items)
   
   return(length(cleaned_items))
})

.rs.cleanup_context_items()
.rs.addJsonRpcHandler("browse_for_file", function() {
   return(.rs.browse_for_file())
})

.rs.addJsonRpcHandler("add_context_item", function(path) {
   if (!is.character(path)) {
      return(FALSE)
   }

   result <- .rs.add_context_item(path)
   
   context_items <- .rs.getVar("context_items")   
   return(result)
})

.rs.addJsonRpcHandler("get_context_items", function() {
   result <- .rs.get_context_items(expand_directories = FALSE)   
   
   return(result)
})

.rs.addJsonRpcHandler("remove_context_item", function(path_or_unique_id) {
   return(.rs.remove_context_item(path_or_unique_id))
})

.rs.addJsonRpcHandler("clear_context_items", function() {
   return(.rs.clear_context_items())
})

.rs.addFunction("get_tab_file_path", function(tab_id) {
   if (is.null(tab_id) || !is.character(tab_id) || length(tab_id) == 0 || nchar(tab_id) == 0) {
      return("")
   }
   
   path <- ""
   tryCatch({
      # Use getAllOpenDocuments to find the document with matching ID
      result <- .rs.api.getAllOpenDocuments(includeContents = FALSE)
      
      if (!is.null(result) && length(result) > 0) {
         for (i in 1:length(result)) {
            doc <- result[[i]]
            if (!is.null(doc) && !is.null(doc$id)) {
               if (doc$id == tab_id) {
                  if (!is.null(doc$path) && is.character(doc$path) && nzchar(doc$path)) {
                     # Document has a saved path - use it
                     path <- doc$path
                     break
                  } else {
                     # Unsaved document - generate __UNSAVED_ path using same logic as symbol indexing
                     # Use tempName property if available, otherwise fallback to "Untitled"
                     temp_name <- NULL
                     if (!is.null(doc$properties) && !is.null(doc$properties$tempName)) {
                        temp_name <- doc$properties$tempName
                     }
                     
                     if (!is.null(temp_name) && nzchar(temp_name)) {
                        if (nzchar(doc$id)) {
                           path <- paste0("__UNSAVED_", substr(doc$id, 1, 4), "__/", temp_name)
                        } else {
                           path <- paste0("__UNSAVED__/", temp_name)
                        }
                     } else {
                        if (nzchar(doc$id)) {
                           path <- paste0("__UNSAVED_", substr(doc$id, 1, 4), "__/Untitled")
                        } else {
                           path <- "__UNSAVED__/Untitled"
                        }
                     }
                     break
                  }
               }
            }
         }
      }
   }, error = function(e) {
      cat("DEBUG: Error in get_tab_file_path:", e$message, "\n")
      return("")
   })
   
   return(path)
})

.rs.addJsonRpcHandler("get_tab_file_path", function(tab_id) {
   result <- .rs.get_tab_file_path(tab_id)
   
   return(result)
})

.rs.addFunction("add_context_lines", function(path, start_line, end_line) {   
   if (is.null(path) || !is.character(path) || length(path) == 0 || nchar(path) == 0) {
      return(FALSE)
   }
   
   path <- path.expand(path)
   
   # Check if file exists (disk or open editor) and is not a directory
   file_exists_disk <- file.exists(path)
   file_open_editor <- .rs.is_file_open_in_editor(path)
   
   if (!file_exists_disk && !file_open_editor) {
      return(FALSE)
   }
   if (file_exists_disk && file.info(path)$isdir) {
      return(FALSE)
   }
   
   start_line <- as.integer(start_line)
   end_line <- as.integer(end_line)
   
   if (is.na(start_line) || is.na(end_line) || start_line < 1 || end_line < start_line) {
      return(FALSE)
   }
   
   # Validate line numbers against actual content using existing helper
   file_content <- .rs.get_effective_file_content(path)
   if (!is.null(file_content)) {
      lines <- strsplit(file_content, "\n", fixed = TRUE)[[1]]
      total_lines <- length(lines)
      if (start_line > total_lines || end_line > total_lines) {
         return(FALSE)
      }
   }
   
   context_items <- .rs.getVar("context_items")
   
   if (is.null(context_items)) {
      context_items <- list()
   }
   
   file_base_name <- basename(path)
   if (start_line == end_line) {
      item_description <- sprintf("%s (%d)", file_base_name, start_line)
   } else {
      item_description <- sprintf("%s (%d-%d)", file_base_name, start_line, end_line)
   }
   
   # Check for exact duplicate
   for (i in seq_along(context_items)) {
      item <- context_items[[i]]
      if (!is.null(item$path) && path.expand(item$path) == path && 
          !is.null(item$start_line) && !is.null(item$end_line) && 
          item$start_line == start_line && item$end_line == end_line) {
         return(TRUE)
      }
   }
   
   new_item <- list(
      path = path,
      name = item_description,
      type = "file",
      start_line = start_line,
      end_line = end_line,
      timestamp = Sys.time()
   )
   
   context_items[[length(context_items) + 1]] <- new_item
   .rs.setVar("context_items", context_items)
   
   # Index for symbols if file exists on disk or is open in editor
   if (file.exists(path) || .rs.is_file_open_in_editor(path)) {
      tryCatch({
         # First, quickly build the symbol index framework for the working directory
         .rs.build_symbol_index_quick()
         
         # Then index the specific file to ensure it's included
         .rs.index_specific_symbol(path)
      }, error = function(e) {
      })
   }
   
   return(TRUE)
})

.rs.addJsonRpcHandler("add_context_lines", function(path, start_line, end_line) {
   if (!is.character(path)) {
      return(FALSE)
   }
   
   if (!is.numeric(start_line) && !is.integer(start_line)) {
      return(FALSE)
   }
   
   if (!is.numeric(end_line) && !is.integer(end_line)) {
      return(FALSE)
   }
   
   result <- .rs.add_context_lines(path, start_line, end_line)
   return(result)
})

.rs.addJsonRpcHandler("get_categorized_environment_variables", function(include_hidden = FALSE) {
   return(.rs.get_categorized_environment_variables(globalenv(), include_hidden))
})