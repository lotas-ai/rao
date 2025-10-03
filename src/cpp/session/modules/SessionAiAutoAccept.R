# SessionAiAutoAccept.R
#
# Copyright (C) 2025 by Lotas Inc.
#
# Auto-accept tracking system for AI-proposed file edits
# Note: The actual tracking, diff computation, and accept/reject operations
# are now handled in Java (AutoAcceptTracker, LineDiffComputer, AutoAcceptSectionComputer)
# for better performance. This file only handles the initial tracking event.

.rs.addFunction("track_auto_accept_edit", function(file_path, conversation_index) {  
  normalized_path <- .rs.normalize_file_path(file_path)
  file_exists <- file.exists(normalized_path)
  
  accepted_content <- if (file_exists) {
    content <- .rs.get_effective_file_content(normalized_path)
    content
  } else {
    ""
  }
    
  # Send client event to Java with normalized path
  tracking_data <- list(
    filePath = normalized_path,
    acceptedContent = accepted_content,
    conversationIndex = as.character(conversation_index)
  )
  
  .rs.enqueClientEvent("track_auto_accept_edit", tracking_data)
  
  return(TRUE)
})

.rs.addFunction("get_auto_accept_edits", function() {
  .rs.get_ai_pref("auto_accept_edits", FALSE)
})

.rs.addFunction("accept_and_clear_all_auto_accept_tracking", function() {
  # When switching conversations, accept all currently tracked edits
  # by clearing the Java-side tracker (this treats current state as accepted)
  .rs.enqueClientEvent("accept_and_clear_auto_accept_tracking", list())
  return(TRUE)
})
