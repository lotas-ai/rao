# SessionAiVariableManager.R
#
# Copyright (C) 2025 by William Nickols
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.

.rs.addFunction("initialize_conversation_variable_cache", function() {
  if (!.rs.hasVar("conversation_variable_cache")) {
    .rs.setVar("conversation_variable_cache", new.env(parent = emptyenv()))
  }
  if (!.rs.hasVar("current_cached_conversation_id")) {
    .rs.setVar("current_cached_conversation_id", NULL)
  }
  return(TRUE)
})

tryCatch({
  .rs.initialize_conversation_variable_cache()
}, error = function(e) {
})

.rs.addFunction("get_conversation_specific_variables", function() {
  c(
    "active_api_request_id",
    "ai_cancelled",
    ".rs.console_done",
    ".rs.console_output",
    ".rs.console_message_id",
    ".rs.terminal_id",
    ".rs.terminal_output",
    ".rs.terminal_exit_code",
    ".rs.terminal_done",
    ".rs.terminal_message_id",
    ".rs.tracking_plots",
    ".rs.previous_plots", 
    ".rs.previous_device",
    ".rs.previous_plot_record",
    ".rs.plot_info",
    "function_call_depth",
    "last_function_was_edit_file",
    "ai_in_error",
    "context_items",
    "assistant_message_count"
  )
})

.rs.addFunction("store_conversation_variables", function(conversation_id) {
  .rs.save_conversation_variables_to_file(conversation_id)
})

.rs.addFunction("load_conversation_variables", function(conversation_id) {
  .rs.ensure_conversation_variables_loaded(conversation_id)
})

.rs.addFunction("initialize_conversation_defaults", function() {
  .rs.initialize_conversation_defaults_in_cache()
})

.rs.addFunction("clear_conversation_variables", function() {
  .rs.initialize_conversation_variable_cache()
  
  conversation_cache <- .rs.getVar("conversation_variable_cache")
  rm(list = ls(conversation_cache), envir = conversation_cache)
  
  .rs.initialize_conversation_defaults_in_cache()
  
  return(TRUE)
})

.rs.addFunction("ensure_conversation_variables_loaded", function(conversation_id) {
  if (is.null(conversation_id)) {
    conversation_id <- .rs.get_current_conversation_index()
  }
  
  .rs.initialize_conversation_variable_cache()
  
  current_cached_id <- .rs.getVar("current_cached_conversation_id")
  
  if (!is.null(current_cached_id) && current_cached_id == conversation_id) {
    return(TRUE)
  }
  
  if (!is.null(current_cached_id) && current_cached_id != conversation_id) {
    .rs.save_conversation_variables_to_file(current_cached_id)
  }
  
  conversation_cache <- .rs.getVar("conversation_variable_cache")
  rm(list = ls(conversation_cache), envir = conversation_cache)
  .rs.setVar("current_cached_conversation_id", conversation_id)
  
  .rs.load_conversation_variables_from_file(conversation_id)
  
  return(TRUE)
})

.rs.addFunction("get_conversation_var", function(var_name, default_value = NULL) {
  if (!var_name %in% .rs.get_conversation_specific_variables()) {
    return(.rs.getVar(var_name))
  }
  
  conversation_id <- .rs.get_current_conversation_index()
  .rs.ensure_conversation_variables_loaded(conversation_id)
  
  conversation_cache <- .rs.getVar("conversation_variable_cache")
  if (exists(var_name, envir = conversation_cache)) {
    return(get(var_name, envir = conversation_cache))
  }
  
  if (!is.null(default_value)) {
    return(default_value)
  }
  if (var_name == "ai_cancelled") return(FALSE)
  if (var_name == "function_call_depth") return(0)
  if (var_name == "last_function_was_edit_file") return(FALSE)
  if (var_name == "ai_in_error") return(FALSE)
  if (var_name == "assistant_message_count") return(0)
  if (var_name == "context_items") return(list())
  
  return(NULL)
})

.rs.addFunction("complete_deferred_conversation_init", function() {
  if (.rs.hasVar("current_conversation_index")) {
    conversation_id <- .rs.getVar("current_conversation_index")
    .rs.ensure_conversation_variables_loaded(conversation_id)
  } else {
    .rs.setVar("current_conversation_index", 1)
    .rs.ensure_conversation_variables_loaded(1)
  }
  
  return(TRUE)
})

.rs.addFunction("set_conversation_var", function(var_name, value) {
  if (!var_name %in% .rs.get_conversation_specific_variables()) {
    .rs.setVar(var_name, value)
    return(TRUE)
  }
  
  conversation_id <- .rs.get_current_conversation_index()
  .rs.ensure_conversation_variables_loaded(conversation_id)
  
  conversation_cache <- .rs.getVar("conversation_variable_cache")
  assign(var_name, value, envir = conversation_cache)
  
  return(TRUE)
})

.rs.addFunction("save_conversation_variables_to_file", function(conversation_id) {
  .rs.initialize_conversation_variable_cache()
  
  base_ai_dir <- .rs.get_ai_base_dir()
  conversations_dir <- file.path(base_ai_dir, "conversations")
  base_dir <- file.path(conversations_dir, paste0("conversation_", conversation_id))
  dir.create(base_dir, recursive = TRUE, showWarnings = FALSE)
  
  vars_file <- file.path(base_dir, "conversation_vars.rds")
  
  conversation_cache <- .rs.getVar("conversation_variable_cache")
  vars_values <- as.list(conversation_cache)
  
  saveRDS(vars_values, file = vars_file)
  
  return(TRUE)
})

.rs.addFunction("load_conversation_variables_from_file", function(conversation_id) {
  .rs.initialize_conversation_variable_cache()
  
  base_ai_dir <- .rs.get_ai_base_dir()
  conversations_dir <- file.path(base_ai_dir, "conversations")
  base_dir <- file.path(conversations_dir, paste0("conversation_", conversation_id))
  vars_file <- file.path(base_dir, "conversation_vars.rds")
  
  if (!file.exists(vars_file)) {
    .rs.initialize_conversation_defaults_in_cache()
    return(TRUE)
  }
  
  tryCatch({
    vars_values <- readRDS(vars_file)
    conversation_cache <- .rs.getVar("conversation_variable_cache")
    
    for (var_name in names(vars_values)) {
      assign(var_name, vars_values[[var_name]], envir = conversation_cache)
    }
  }, error = function(e) {
    warning("Failed to load conversation variables: ", e$message)
    .rs.initialize_conversation_defaults_in_cache()
  })
  
  return(TRUE)
})

.rs.addFunction("initialize_conversation_defaults_in_cache", function() {
  .rs.initialize_conversation_variable_cache()
  
  conversation_cache <- .rs.getVar("conversation_variable_cache")
  
  # Set default values for conversation-specific variables
  assign("ai_cancelled", FALSE, envir = conversation_cache)
  assign("function_call_depth", 0, envir = conversation_cache)
  assign("last_function_was_edit_file", FALSE, envir = conversation_cache)
  assign("ai_in_error", FALSE, envir = conversation_cache)
  assign("context_items", list(), envir = conversation_cache)
  assign("assistant_message_count", 0, envir = conversation_cache)
  
  return(TRUE)
})

