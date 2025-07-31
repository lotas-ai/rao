# RStudio-based update checking has been removed from Rao
# Use Electron auto-updater and manual S3-based updates instead
downloadUpdateInfo <- function(version, os, manual, secure, method) {
  # Return no update available to disable this legacy update path
  cat("update_version=none\n")
  cat("download_url=\n") 
  cat("release_notes=Legacy update system disabled. Use Help > Check for Updates or automatic updates.\n")
}

