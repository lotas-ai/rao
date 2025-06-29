# SessionAiButtons.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

.rs.addFunction("get_message_buttons_path", function() {
   paths <- .rs.get_ai_file_paths()
   return(paths$buttons_csv_path)
})

.rs.addFunction("get_buttons_file_path", function() {
   paths <- .rs.get_ai_file_paths()
   return(paths$buttons_csv_path)
})

.rs.addFunction("read_message_buttons", function() {
   buttons_path <- .rs.get_buttons_file_path()
   
   if (!file.exists(buttons_path)) {
      # Return empty data frame with correct structure  
      return(data.frame(
         message_id = integer(),
         buttons_run = logical(),
         stringsAsFactors = FALSE
      ))
   }
   
   df <- read.csv(buttons_path, stringsAsFactors = FALSE)
   
   # Ensure the data frame has the correct columns
   if (!"message_id" %in% names(df)) {
      df$message_id <- integer(0)
   }
   if (!"buttons_run" %in% names(df)) {
      df$buttons_run <- logical(0)
   }
   
   # Ensure message_id is integer
   df$message_id <- as.integer(df$message_id)
   
   # Ensure buttons_run is logical
   df$buttons_run <- as.logical(df$buttons_run)
   df$buttons_run[is.na(df$buttons_run)] <- FALSE
   
   return(df)
})

.rs.addFunction("write_message_buttons", function(buttons) {
   if (nrow(buttons) == 0) {
      # Create empty data frame with correct structure
      buttons <- data.frame(
         message_id = integer(),
         buttons_run = logical(),
         stringsAsFactors = FALSE
      )
   }
   
   buttons_path <- .rs.get_buttons_file_path()
   write.csv(buttons, buttons_path, row.names = FALSE)
})

# Mark a button as run (clicked) - simplified version
.rs.addFunction("mark_button_as_run", function(message_id, button_type) {
   buttons <- .rs.read_message_buttons()
   
   # Ensure message_id is integer
   message_id <- as.integer(message_id)
   
   # Find existing row or create new one
   idx <- which(buttons$message_id == message_id)
   
   if (length(idx) > 0) {
      # Update existing row - mark buttons as run
      buttons$buttons_run[idx] <- TRUE
   } else {
      # Create new row - buttons are immediately marked as run
      new_row <- data.frame(
         message_id = message_id,
         buttons_run = TRUE,
         stringsAsFactors = FALSE
      )
      buttons <- rbind(buttons, new_row)
   }
   
   .rs.write_message_buttons(buttons)
   return(TRUE)
})

# Clear all message buttons after a certain message ID (for conversation revert)
.rs.addFunction("clear_message_buttons_after", function(message_id) {
   buttons <- .rs.read_message_buttons()
   
   if (nrow(buttons) > 0) {
      # Convert message_id to integer for comparison
      message_id <- as.integer(message_id)
      buttons <- buttons[buttons$message_id < message_id, , drop = FALSE]
      
      .rs.write_message_buttons(buttons)
   }
   
   return(TRUE)
})

# Check if buttons should be hidden for a restored widget based on CSV state
.rs.addFunction("should_hide_buttons_for_restored_widget", function(message_id) {
   buttons <- .rs.read_message_buttons()
   
   if (nrow(buttons) == 0) {
      # No button data exists - don't hide buttons (they haven't been clicked yet)
      return(FALSE)
   }
   
   # Convert message_id to integer for consistency
   message_id <- as.integer(message_id)
   
   # Find the row for this message
   button_row <- buttons[buttons$message_id == message_id, , drop = FALSE]
   
   if (nrow(button_row) == 0) {
      # No button data for this message - don't hide buttons (they haven't been clicked yet)
      return(FALSE)
   }
   
   # Return TRUE if buttons were already run (should be hidden)
   should_hide <- button_row$buttons_run
   return(should_hide)
})