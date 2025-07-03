#
# SessionAiImages.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

.rs.addFunction("save_ai_image", function(imagePath) {   
   # Validate the file exists and is an image
   if (!file.exists(imagePath)) {
      stop("Image file does not exist: ", imagePath)
   }
      
   # Check if file is a supported image format
   image_extensions <- c(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".svg")
   file_ext <- tolower(tools::file_ext(imagePath))
      
   if (!paste0(".", file_ext) %in% image_extensions) {
      stop("Unsupported image format. Supported formats: png, jpg, jpeg, gif, bmp, svg")
   }
      
   # Get the current conversation index
   conversationIndex <- .rs.get_current_conversation_index()
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversationIndex))
      
   # Create directories if they don't exist
   if (!dir.exists(conversation_dir)) {
      dir.create(conversation_dir, recursive = TRUE)
   }
   
   # Create images_attached folder
   images_dir <- file.path(conversation_dir, "images_attached")
   
   if (!dir.exists(images_dir)) {
      dir.create(images_dir, recursive = TRUE)
   }
   
   # Copy the image to the images_attached folder
   image_filename <- basename(imagePath)
   destination_path <- file.path(images_dir, image_filename)
   
   # If file already exists with same name, add a number suffix
   counter <- 1
   original_filename <- image_filename
   while (file.exists(destination_path)) {
      name_parts <- strsplit(original_filename, "\\.")[[1]]
      if (length(name_parts) > 1) {
         extension <- name_parts[length(name_parts)]
         basename_part <- paste(name_parts[1:(length(name_parts)-1)], collapse = ".")
         image_filename <- paste0(basename_part, "_", counter, ".", extension)
      } else {
         image_filename <- paste0(original_filename, "_", counter)
      }
      destination_path <- file.path(images_dir, image_filename)
      counter <- counter + 1
   }
   
   # Copy the file
   copy_result <- file.copy(imagePath, destination_path)
   
   if (!copy_result) {
      stop("Failed to copy image file")
   }
   
   # Verify the file was copied
   if (!file.exists(destination_path)) {
      stop("File copy verification failed")
   }
   
   # Update the images CSV file
   csv_path <- file.path(conversation_dir, "images.csv")
   
   # Create image record
   image_record <- data.frame(
      timestamp = Sys.time(),
      message_id = "", # Will be filled when used in conversation
      file_path = imagePath,
      local_path = destination_path,
      filename = image_filename,
      stringsAsFactors = FALSE
   )
   
   # Read existing CSV or create new one
   if (file.exists(csv_path)) {
      existing_images <- tryCatch({
         read.csv(csv_path, stringsAsFactors = FALSE)
      }, error = function(e) {
         # If CSV is corrupted, create new dataframe with proper structure
         data.frame(
            timestamp = character(),
            message_id = character(),
            file_path = character(),
            local_path = character(),
            filename = character(),
            stringsAsFactors = FALSE
         )
      })
      
      # Ensure existing_images has all required columns
      required_cols <- c("timestamp", "message_id", "file_path", "local_path", "filename")
      for (col in required_cols) {
         if (!col %in% names(existing_images)) {
            existing_images[[col]] <- if (col == "timestamp") as.POSIXct(character()) else character()
         }
      }
      
      # Combine existing and new records
      all_images <- rbind(existing_images, image_record)
   } else {
      all_images <- image_record
   }
   
   # Write back to CSV
   write_result <- tryCatch({
      write.csv(all_images, file = csv_path, row.names = FALSE)
      TRUE
   }, error = function(e) {
      cat("DEBUG R SAVE IMAGE: ERROR writing CSV:", e$message, "\n")
      FALSE
   })
   
   if (!write_result) {
      stop("Failed to write CSV file")
   }
   
   # Verify CSV was written correctly
   verify_result <- tryCatch({
      verify_images <- read.csv(csv_path, stringsAsFactors = FALSE)
      TRUE
   }, error = function(e) {
      FALSE
   })
   
   return(list(success = TRUE, local_path = destination_path, filename = image_filename))
})

.rs.addFunction("list_ai_images", function() {
   conversation_index <- .rs.get_current_conversation_index()
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversation_index))
   csv_path <- file.path(conversation_dir, "images.csv")
   
   if (!file.exists(csv_path)) {
      return(character())
   }
   
   tryCatch({
      images <- read.csv(csv_path, stringsAsFactors = FALSE)
      
      if (nrow(images) > 0) {
         # Return the local paths (in images_attached folder) for display
         local_paths <- images$local_path
         return(local_paths)
      } else {
         return(character())
      }
   }, error = function(e) {
      return(character())
   })
})

.rs.addFunction("delete_ai_image", function(imagePath) {
   # Get conversation paths
   conversationIndex <- .rs.get_current_conversation_index()
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversationIndex))
   csvPath <- file.path(conversation_dir, "images.csv")
   
   # Return if file doesn't exist
   if (!file.exists(csvPath)) {
      return(list(success = FALSE, reason = "No images found"))
   }
   
   # Read the CSV file
   images <- tryCatch({
      read.csv(csvPath, stringsAsFactors = FALSE)
   }, error = function(e) {
      return(NULL)
   })
   
   if (is.null(images) || nrow(images) == 0) {
      return(list(success = FALSE, reason = "No images found"))
   }
   
   # Find the image to delete (match by local_path since that's what we display)
   image_to_delete <- images[images$local_path == imagePath, ]
   
   if (nrow(image_to_delete) == 0) {
      return(list(success = FALSE, reason = "Image not found"))
   }
   
   # Delete the actual file
   if (file.exists(imagePath)) {
      file.remove(imagePath)
   }
   
   # Remove from CSV
   remaining_images <- images[images$local_path != imagePath, ]
   write.csv(remaining_images, file = csvPath, row.names = FALSE)
   
   return(list(success = TRUE))
})

.rs.addFunction("delete_all_ai_images", function() {
   # Determine CSV path in the conversation directory
   conversationIndex <- .rs.get_current_conversation_index()
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversationIndex))
   csvPath <- file.path(conversation_dir, "images.csv")
   images_dir <- file.path(conversation_dir, "images_attached")
   
   # Return if file doesn't exist
   if (!file.exists(csvPath)) {
      return(list(success = TRUE))
   }
   
   # Read the CSV file to get image paths
   images <- tryCatch({
      read.csv(csvPath, stringsAsFactors = FALSE)
   }, error = function(e) {
      return(NULL)
   })
   
   # Delete all image files
   if (!is.null(images) && nrow(images) > 0) {
      for (i in seq_len(nrow(images))) {
         local_path <- images$local_path[i]
         if (file.exists(local_path)) {
            file.remove(local_path)
         }
      }
   }
   
   # Delete the entire images_attached directory if it exists
   if (dir.exists(images_dir)) {
      unlink(images_dir, recursive = TRUE)
   }
   
   # Create empty images dataframe
   emptyImages <- data.frame(
      timestamp = character(),
      message_id = character(),
      file_path = character(),
      local_path = character(),
      filename = character(),
      stringsAsFactors = FALSE
   )
   
   # Write empty dataframe to CSV
   write.csv(emptyImages, file = csvPath, row.names = FALSE)
   
   return(list(success = TRUE))
})

.rs.addFunction("check_image_content_duplicate", function(imagePath) {
   # Validate the file exists
   if (!file.exists(imagePath)) {
      return(FALSE)  # If file doesn't exist, it's not a duplicate
   }
   
   # Get the current conversation index
   conversationIndex <- .rs.get_current_conversation_index()
   base_ai_dir <- .rs.get_ai_base_dir()
   conversations_dir <- file.path(base_ai_dir, "conversations")
   conversation_dir <- file.path(conversations_dir, paste0("conversation_", conversationIndex))
   csvPath <- file.path(conversation_dir, "images.csv")
   
   # Return FALSE if no images CSV exists
   if (!file.exists(csvPath)) {
      return(FALSE)
   }
   
   # Read the CSV file to get currently attached images
   images <- tryCatch({
      read.csv(csvPath, stringsAsFactors = FALSE)
   }, error = function(e) {
      return(NULL)
   })
   
   # If no images or reading failed, not a duplicate
   if (is.null(images) || nrow(images) == 0) {
      return(FALSE)
   }
   
   # Calculate MD5 hash of the new image
   new_image_hash <- tryCatch({
      tools::md5sum(imagePath)
   }, error = function(e) {
      return(NULL)
   })
   
   if (is.null(new_image_hash)) {
      return(FALSE)  # If we can't get hash, assume not duplicate
   }
   
   # Check each existing image for content match
   for (i in seq_len(nrow(images))) {
      existing_path <- images$local_path[i]
      
      # Skip if existing image file doesn't exist
      if (!file.exists(existing_path)) {
         next
      }
      
      # Calculate MD5 hash of existing image
      existing_hash <- tryCatch({
         tools::md5sum(existing_path)
      }, error = function(e) {
         return(NULL)
      })
      
      # If hashes match, it's a duplicate
      if (!is.null(existing_hash) && new_image_hash == existing_hash) {
         return(TRUE)
      }
   }
   
   return(FALSE)
})

.rs.addFunction("create_temp_image_file", function(dataUrl, fileName) {
   # Parse the data URL - expected format: data:[<mediatype>][;base64],<data>
   if (!grepl("^data:", dataUrl)) {
      stop("Invalid data URL format")
   }
   
   # Extract the base64 part
   parts <- strsplit(dataUrl, ",")[[1]]
   if (length(parts) != 2) {
      stop("Invalid data URL format - missing comma separator")
   }
   
   header <- parts[1]
   data_part <- parts[2]
   
   # Determine file extension from MIME type in header
   file_ext <- "png"  # default
   if (grepl("image/jpeg", header) || grepl("image/jpg", header)) {
      file_ext <- "jpg"
   } else if (grepl("image/png", header)) {
      file_ext <- "png"
   } else if (grepl("image/gif", header)) {
      file_ext <- "gif"
   } else if (grepl("image/webp", header)) {
      file_ext <- "webp"
   }
   
   # Generate filename if not provided
   if (is.null(fileName) || fileName == "" || is.na(fileName)) {
      timestamp <- format(Sys.time(), "%Y%m%d_%H%M%S")
      fileName <- paste0("temp_image_", timestamp, ".", file_ext)
   } else {
      # Ensure proper extension
      if (!grepl("\\.", fileName)) {
         fileName <- paste0(fileName, ".", file_ext)
      }
   }
   
   # Use system temp directory instead of conversation directory
   temp_dir <- tempdir()
   
   # Create unique filename if file exists
   destination_path <- file.path(temp_dir, fileName)
   counter <- 1
   original_filename <- fileName
   while (file.exists(destination_path)) {
      name_parts <- strsplit(original_filename, "\\.")[[1]]
      if (length(name_parts) > 1) {
         extension <- name_parts[length(name_parts)]
         basename_part <- paste(name_parts[1:(length(name_parts)-1)], collapse = ".")
         fileName <- paste0(basename_part, "_", counter, ".", extension)
      } else {
         fileName <- paste0(original_filename, "_", counter)
      }
      destination_path <- file.path(temp_dir, fileName)
      counter <- counter + 1
   }
   
   # Decode base64 and save to temp file
   tryCatch({
      # Check if base64 is specified in header
      if (grepl("base64", header)) {
         # Decode base64 data
         raw_data <- base64enc::base64decode(data_part)
      } else {
         # If not base64, treat as raw data (though this is unusual for images)
         raw_data <- charToRaw(data_part)
      }
      
      # Write binary data to temp file
      writeBin(raw_data, destination_path)
      
   }, error = function(e) {
      stop(paste("Failed to save temp image:", e$message))
   })
   
   # Do NOT add to CSV file - this is just for duplicate checking
   return(destination_path)
})

# Add JSON RPC handlers
.rs.addJsonRpcHandler("save_ai_image", function(imagePath) {
   .rs.save_ai_image(imagePath)
})

.rs.addJsonRpcHandler("create_temp_image_file", function(dataUrl, fileName) {
   .rs.create_temp_image_file(dataUrl, fileName)
})

.rs.addJsonRpcHandler("list_images", function() {
   .rs.list_ai_images()
})

.rs.addJsonRpcHandler("delete_image", function(imagePath) {
   .rs.delete_ai_image(imagePath)
})

.rs.addJsonRpcHandler("delete_all_images", function() {
   .rs.delete_all_ai_images()
})

.rs.addJsonRpcHandler("check_image_content_duplicate", function(imagePath) {
   .rs.check_image_content_duplicate(imagePath)
}) 