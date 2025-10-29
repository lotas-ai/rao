#
# SessionAiTelemetry.R
#
# Copyright (C) 2025 by Lotas Inc.
#

.rs.addFunction("telemetry.getOrCreateUserId", function() {
  user_id <- .rs.readUserState("telemetry_user_id")
  
  if (is.null(user_id) || user_id == "") {
    user_id <- .rs.createUUID()
    .rs.writeUserState("telemetry_user_id", user_id)
  }
  
  return(user_id)
})

.rs.addFunction("telemetry.detectOperatingSystem", function() {
  if (.Platform$OS.type == "windows") {
    return("Windows")
  } else if (Sys.info()["sysname"] == "Darwin") {
    return("macOS")
  } else if (Sys.info()["sysname"] == "Linux") {
    return("Linux")
  } else {
    return(Sys.info()["sysname"])
  }
})
