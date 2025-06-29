# SessionAiAttachments.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

.rs.addFunction("save_ai_attachment", function(filePath) {
   # Check if Anthropic is the active provider
   active_provider <- .rs.get_active_provider()
   
   # Get conversation paths and setup
   paths <- .rs.get_ai_file_paths()
   conversationIndex <- .rs.get_current_conversation_index()
   csvPath <- paths$attachments_csv_path
   
   # Create directory if it doesn't exist
   if (!dir.exists(dirname(csvPath))) {
      dir.create(dirname(csvPath), recursive = TRUE)
   }
   
   # Get current timestamp
   timestamp <- format(Sys.time(), "%Y-%m-%d %H:%M:%S")
   messageId <- .rs.get_next_message_id()
   
   if (active_provider == "anthropic") {
      # Use Anthropic backend route (no API key needed)
      return(.rs.save_anthropic_attachment(filePath, csvPath, timestamp, messageId))
   } else {
      # Get Rao API key for backend authentication
      api_key <- .rs.get_api_key("rao")
      if (is.null(api_key)) {
         return(list(success = FALSE, reason = "Rao API key not found"))
      }
      
      # Use existing OpenAI backend route
      return(.rs.save_attachment_via_backend(filePath, api_key, csvPath, timestamp, messageId, conversationIndex))
   }
})

# Simple Anthropic attachment upload via backend (no API key needed)
.rs.addFunction("save_anthropic_attachment", function(filePath, csvPath, timestamp, messageId) {
   tryCatch({
      # Get backend configuration
      config <- .rs.get_backend_config()
      
      # Get Rao API key for backend authentication
      api_key <- .rs.get_api_key("rao")
      if (is.null(api_key)) {
         stop("Rao API key not found")
      }
      
      # Upload file to Anthropic via backend
      if (!file.exists(filePath)) {
         stop("File not found: ", filePath)
      }
      
      # Create multipart form data for file upload
      tryCatch({
         # Create the request with error handling
         request <- httr2::request(paste0(config$url, "/attachments/upload-anthropic"))
         
         # Add multipart body and API key as form parameter for authentication
         request <- httr2::req_body_multipart(request, 
                                             file = curl::form_file(filePath),
                                             api_key = api_key)
         
         # Allow error responses to be captured
         request <- httr2::req_error(request, is_error = function(resp) FALSE)
         
         # Perform the request
         response <- httr2::req_perform(request)
         status <- httr2::resp_status(response)
         
         # Check if we got an error status
         if (status >= 400) {
            error_body <- httr2::resp_body_string(response)
            stop("Backend returned error: ", error_body)
         }
         
         # Parse response
         uploadResponse <- httr2::resp_body_json(response)
         
      }, error = function(e) {
         stop("HTTP request failed: ", e$message)
      })
      
      if (!uploadResponse$success) {
         stop("Backend upload error: ", uploadResponse$error)
      }
      
      file_id <- uploadResponse$id
      
      # Create or append to CSV file with header if needed
      if (!file.exists(csvPath)) {
         write.csv(
            data.frame(
               timestamp = character(),
               message_id = character(),
               file_path = character(),
               file_id = character(),
               vector_store_id = character(),
               stringsAsFactors = FALSE
            ),
            file = csvPath,
            row.names = FALSE
         )
      }
      
      # Read existing CSV
      attachments <- read.csv(csvPath, stringsAsFactors = FALSE)
      
      # Handle existing file replacement (delete old file from Anthropic)
      if (nrow(attachments) > 0 && filePath %in% attachments$file_path) {
         old_file_id <- attachments$file_id[attachments$file_path == filePath]
         
         # Delete the old file via backend
         if (!is.null(old_file_id) && old_file_id != "") {
            tryCatch({
               deleteResponse <- httr2::resp_body_json(
                   httr2::req_perform(
                       httr2::req_method(
                           httr2::request(paste0(config$url, "/attachments/anthropic-files/", old_file_id)),
                           "DELETE"
                       )
                   )
               )
            }, error = function(e) {
               # Ignore errors when deleting old file
            })
         }
         
         # Remove existing entry
         attachments <- attachments[attachments$file_path != filePath, , drop = FALSE]
      }
      
      # Add new row (no vector_store_id for Anthropic)
      newRow <- data.frame(
         timestamp = timestamp,
         message_id = messageId,
         file_path = filePath,
         file_id = file_id,
         vector_store_id = "", # Anthropic doesn't use vector stores
         stringsAsFactors = FALSE
      )
      
      # Append to data frame
      attachments <- rbind(attachments, newRow)
      
      # Write back to CSV
      write.csv(attachments, file = csvPath, row.names = FALSE)
      
      return(list(success = TRUE))
      
   }, error = function(e) {
      stop("Backend Anthropic attachment error: ", e$message)
   })
})

# Backend route for saving attachments (OpenAI)
.rs.addFunction("save_attachment_via_backend", function(filePath, api_key, csvPath, timestamp, messageId, conversationIndex) {
   tryCatch({
      # Get backend configuration
      config <- .rs.get_backend_config()
      
      # Upload file to OpenAI via backend
      if (!file.exists(filePath)) {
         stop("File not found: ", filePath)
      }
      # Create multipart form data for file upload to OpenAI
      uploadResponse <- httr2::resp_body_json(
          httr2::req_perform(
              httr2::req_body_multipart(
                  httr2::request(paste0(config$url, "/attachments/upload-openai")),
                  purpose = "assistants",
                  file = curl::form_file(filePath),
                  api_key = api_key
              )
          )
      )
      
      if (!uploadResponse$success) {
         stop("Backend upload error: ", uploadResponse$error)
      }
      
      file_id <- uploadResponse$id
      vector_store_id <- ""
      
      # Create or append to CSV file with header if needed
      if (!file.exists(csvPath)) {
         write.csv(
            data.frame(
               timestamp = character(),
               message_id = character(),
               file_path = character(),
               file_id = character(),
               vector_store_id = character(),
               stringsAsFactors = FALSE
            ),
            file = csvPath,
            row.names = FALSE
         )
      }
      
      # Read existing CSV
      attachments <- read.csv(csvPath, stringsAsFactors = FALSE)
      
      # Check if any attachments exist already and if we need to create a vector store
      if (nrow(attachments) == 0 || all(attachments$vector_store_id == "")) {
         # Create vector store via backend
         vectorStoreResponse <- httr2::resp_body_json(
             httr2::req_perform(
                 httr2::req_body_json(
                     httr2::req_headers(
                         httr2::request(paste0(config$url, "/attachments/vector-stores")),
                         "Content-Type" = "application/json"
                     ),
                     data = list(
                        name = paste0("Conversation ", conversationIndex, " Files"),
                        api_key = api_key
                     )
                 )
             )
         )
         
         if (vectorStoreResponse$success) {
            vector_store_id <- vectorStoreResponse$id
         } else {
            cat("Failed to create vector store:", vectorStoreResponse$error, "\n")
         }
      } else {
         # Use existing vector store
         vector_store_id <- attachments$vector_store_id[1]
      }
      
      # Add file to vector store if we have one
      if (vector_store_id != "") {
         addFileResponse <- httr2::resp_body_json(
             httr2::req_perform(
                 httr2::req_body_json(
                     httr2::req_headers(
                         httr2::request(paste0(config$url, "/attachments/vector-stores/", vector_store_id, "/files")),
                         "Content-Type" = "application/json"
                     ),
                     data = list(
                        file_id = file_id,
                        api_key = api_key
                     )
                 )
             )
         )
         
         if (addFileResponse$success) {
         } else {
            cat("Failed to add file to vector store:", addFileResponse$error, "\n")
         }
      }
      
      # Handle existing file replacement
      if (nrow(attachments) > 0 && filePath %in% attachments$file_path) {
         old_file_id <- attachments$file_id[attachments$file_path == filePath]
         
         # Delete the old OpenAI file via backend
         if (!is.null(old_file_id) && old_file_id != "") {
            tryCatch({
               deleteResponse <- httr2::resp_body_json(
                   httr2::req_perform(
                       httr2::req_method(
                           httr2::req_url_query(
                               httr2::request(paste0(config$url, "/attachments/openai-files/", old_file_id)),
                               api_key = api_key
                           ),
                           "DELETE"
                       )
                   )
               )
               
               if (deleteResponse$success) {
                  cat("Old OpenAI file deleted successfully\n")
               } else {
                  cat("Failed to delete old OpenAI file:", deleteResponse$message, "\n")
               }
            }, error = function(e) {
               # Ignore errors when deleting old file
            })
         }
         
         # Remove existing entry
         attachments <- attachments[attachments$file_path != filePath, , drop = FALSE]
      }
      
      # Add new row
      newRow <- data.frame(
         timestamp = timestamp,
         message_id = messageId,
         file_path = filePath,
         file_id = file_id,
         vector_store_id = vector_store_id,
         stringsAsFactors = FALSE
      )
      
      # Append to data frame
      attachments <- rbind(attachments, newRow)
      
      # Write back to CSV
      write.csv(attachments, file = csvPath, row.names = FALSE)
      
      return(list(success = TRUE))
      
   }, error = function(e) {
      stop("Backend attachment error: ", e$message)
   })
})

.rs.addFunction("list_ai_attachments", function() {
   conversation_index <- .rs.get_current_conversation_index()
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversation_index))
   csv_path <- file.path(conversation_dir, "attachments.csv")
   
   if (!file.exists(csv_path)) {
      return(character())
   }
   
   tryCatch({
      attachments <- read.csv(csv_path, stringsAsFactors = FALSE)
      
      if (nrow(attachments) > 0) {
         file_paths <- attachments$file_path
         return(file_paths)
      } else {
         return(character())
      }
   }, error = function(e) {
      return(character())
   })
})

.rs.addFunction("delete_anthropic_attachment", function(file_id) {
   tryCatch({
      # Get backend configuration
      config <- .rs.get_backend_config()
      
      # Get Rao API key for backend authentication
      api_key <- .rs.get_api_key("rao")
      if (is.null(api_key)) {
         stop("Rao API key not found")
      }
      
      # Delete file via backend with API key authentication (following OpenAI pattern)
      deleteResponse <- httr2::resp_body_json(
          httr2::req_perform(
              httr2::req_method(
                  httr2::req_url_query(
                      httr2::request(paste0(config$url, "/attachments/anthropic-files/", file_id)),
                      api_key = api_key
                  ),
                  "DELETE"
              )
          )
      )
      
      return(deleteResponse$success)
      
   }, error = function(e) {
      return(FALSE)
   })
})

.rs.addFunction("delete_ai_attachment", function(filePath) {
   active_provider <- .rs.get_active_provider()
   
   # Get conversation paths
   conversationIndex <- .rs.get_current_conversation_index()
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversationIndex))
   csvPath <- file.path(conversation_dir, "attachments.csv")
   
   # Return if file doesn't exist
   if (!file.exists(csvPath)) {
      return(list(success = FALSE, reason = "No attachments found"))
   }
   
   # Read the CSV file
   attachments <- tryCatch({
      read.csv(csvPath, stringsAsFactors = FALSE)
   }, error = function(e) {
      return(NULL)
   })
   
   if (is.null(attachments) || nrow(attachments) == 0) {
      return(list(success = FALSE, reason = "No attachments found"))
   }
   
   # Get the file_id for the file to delete
   file_id <- attachments$file_id[attachments$file_path == filePath]
   
   if (length(file_id) == 0) {
      return(list(success = FALSE, reason = "File not found in attachments"))
   }
   
   # Delete the file from appropriate API if file_id exists
   if (!is.null(file_id) && length(file_id) > 0 && file_id != "") {
      if (active_provider == "anthropic") {
         success <- .rs.delete_anthropic_attachment(file_id)
      } else {
         # Get Rao API key for backend authentication  
         api_key <- .rs.get_api_key("rao")
         if (!is.null(api_key)) {
            # Use existing OpenAI delete logic (now using unified response format)
            tryCatch({
               config <- .rs.get_backend_config()
               deleteResponse <- httr2::resp_body_json(
                   httr2::req_perform(
                       httr2::req_method(
                           httr2::req_url_query(
                               httr2::request(paste0(config$url, "/attachments/openai-files/", file_id)),
                               api_key = api_key
                           ),
                           "DELETE"
                       )
                   )
               )
               
               success <- deleteResponse$success
               if (!success) {
               }
            }, error = function(e) {
               # Ignore errors when deleting old file
            })
         }
      }
   }
   
   # Filter out the specified file path
   newAttachments <- attachments[attachments$file_path != filePath, , drop = FALSE]
   
   # Write back to CSV
   write.csv(newAttachments, file = csvPath, row.names = FALSE)
   
   return(list(success = TRUE))
})

.rs.addFunction("delete_all_ai_attachments", function() {
   active_provider <- .rs.get_active_provider()
   
   # Determine CSV path in the conversation directory
   conversationIndex <- .rs.get_current_conversation_index()
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversationIndex))
   csvPath <- file.path(conversation_dir, "attachments.csv")
   
   # Return if file doesn't exist
   if (!file.exists(csvPath)) {
      return(list(success = TRUE))
   }
   
   # Read the CSV file to get file IDs and vector store ID
   attachments <- tryCatch({
      read.csv(csvPath, stringsAsFactors = FALSE)
   }, error = function(e) {
      return(NULL)
   })
   
   if (!is.null(attachments) && nrow(attachments) > 0) {
      config <- .rs.get_backend_config()
      
      # Delete each file via appropriate backend
      for (i in 1:nrow(attachments)) {
         file_id <- attachments$file_id[i]
         if (!is.null(file_id) && file_id != "") {
            if (active_provider == "anthropic") {
               tryCatch({
                  success <- .rs.delete_anthropic_attachment(file_id)
               }, error = function(e) {
                  # Ignore errors when deleting Anthropic file
               })
            } else {
               # Get Rao API key for backend authentication
               api_key <- .rs.get_api_key("rao")
               if (!is.null(api_key)) {
                  tryCatch({
                     deleteResponse <- httr2::resp_body_json(
                         httr2::req_perform(
                             httr2::req_method(
                                 httr2::req_url_query(
                                     httr2::request(paste0(config$url, "/attachments/openai-files/", file_id)),
                                     api_key = api_key
                                 ),
                                 "DELETE"
                             )
                         )
                     )
                     
                     if (!deleteResponse$success) {
                     }
                  }, error = function(e) {
                     # Ignore errors when deleting OpenAI file
                  })
               }
            }
         }
      }
      
      # For OpenAI, also delete vector store
      if (active_provider != "anthropic") {
         vector_store_id <- attachments$vector_store_id[1]
         if (!is.null(vector_store_id) && vector_store_id != "") {
            api_key <- .rs.get_api_key("rao")
            if (!is.null(api_key)) {
               tryCatch({
                  deleteVsResponse <- httr2::resp_body_json(
                      httr2::req_perform(
                          httr2::req_method(
                              httr2::req_url_query(
                                  httr2::request(paste0(config$url, "/attachments/vector-stores/", vector_store_id)),
                                  api_key = api_key
                              ),
                              "DELETE"
                          )
                      )
                  )
                  
                  if (!deleteVsResponse$success) {
                     cat("Failed to delete vector store", vector_store_id, ":", deleteVsResponse$error, "\n")
                  }
               }, error = function(e) {
                  # Ignore errors when deleting vector store
               })
            }
         }
      }
   }
   
   # Create empty attachments dataframe with the new columns
   emptyAttachments <- data.frame(
      timestamp = character(),
      message_id = character(),
      file_path = character(),
      file_id = character(),
      vector_store_id = character(),
      stringsAsFactors = FALSE
   )
   
   # Write empty dataframe to CSV
   write.csv(emptyAttachments, file = csvPath, row.names = FALSE)
   
   return(list(success = TRUE))
})

# Add JSON RPC handlers
.rs.addJsonRpcHandler("save_ai_attachment", function(filePath) {
   .rs.save_ai_attachment(filePath)
})

.rs.addJsonRpcHandler("list_attachments", function() {
   .rs.list_ai_attachments()
})

.rs.addJsonRpcHandler("delete_attachment", function(filePath) {
   .rs.delete_ai_attachment(filePath)
})

.rs.addJsonRpcHandler("delete_all_attachments", function() {
   .rs.delete_all_ai_attachments()
})