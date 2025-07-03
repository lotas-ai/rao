/*
 * SessionAi.cpp
 *
 * Copyright (C) 2025 by William Nickols
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

#include "SessionAi.hpp"

#include <algorithm>
#include <gsl/gsl-lite.hpp>

#include <boost/regex.hpp>
#include <boost/function.hpp>
#include <boost/format.hpp>
#include <boost/range/iterator_range.hpp>
#include <boost/algorithm/string/regex.hpp>
#include <boost/algorithm/string/replace.hpp>
#include <boost/algorithm/string.hpp>
#include <boost/iostreams/filter/aggregate.hpp>

#include <shared_core/Error.hpp>
#include <shared_core/FilePath.hpp>

#include <core/Algorithm.hpp>
#include <core/Exec.hpp>
#include <core/Log.hpp>

#include <core/http/Request.hpp>
#include <core/http/Response.hpp>
#include <core/http/URL.hpp>
#include <core/FileSerializer.hpp>
#include <core/system/Process.hpp>
#include <core/system/ShellUtils.hpp>
#include <core/r_util/RPackageInfo.hpp>

#define R_INTERNAL_FUNCTIONS
#include <r/RInternal.hpp>
#include <r/RSexp.hpp>
#include <r/RExec.hpp>
#include <r/RFunctionHook.hpp>
#include <r/ROptions.hpp>
#include <r/RUtil.hpp>
#include <r/RRoutines.hpp>
#include <r/session/RSessionUtils.hpp>
#include <r/RJson.hpp>

#include <session/SessionModuleContext.hpp>
#include <session/SessionPersistentState.hpp>
#include <session/SessionConsoleProcessSocket.hpp>
#include <session/SessionSourceDatabase.hpp>

#include <session/worker_safe/session/SessionClientEvent.hpp>

#include <session/prefs/UserPrefs.hpp>

#include "session-config.h"

#ifdef RSTUDIO_SERVER
#include <server_core/UrlPorts.hpp>
#endif

// protect R against windows TRUE/FALSE defines
#undef TRUE
#undef FALSE

using namespace rstudio::core;
using namespace boost::placeholders;

namespace rstudio {
namespace session {
namespace modules { 
namespace ai {

namespace {   

using rstudio::session::console_process::processSocket;

// save computed ai url prefix for comparison in rAiUrlHandler
const char * const kAiLocation = "/ai";

// javascript callbacks to inject into page
const char * const kJsCallbacks = R"EOF(
<script type="text/javascript">

   if (window.parent.aiNavigated)
      window.parent.aiNavigated(document, window);

   if (window.parent.aiKeydown)
      window.onkeydown = function(e) { window.parent.aiKeydown(e); }

   if (window.parent.aiMousedown)
      window.onmousedown = function(e) { window.parent.aiMousedown(e); }

   if (window.parent.aiMouseover)
      window.onmouseover = function(e) { window.parent.aiMouseover(e); }

   if (window.parent.aiMouseout)
      window.onmouseout = function(e) { window.parent.aiMouseout(e); }

   if (window.parent.aiClick)
      window.onclick = function(e) { window.parent.aiClick(e); } 

   if (window.parent.aiAcceptEditFileCommand)
      window.aiAcceptEditFileCommand = function(edited_code) { window.parent.aiAcceptEditFileCommand(edited_code); }
      
   if (window.parent.aiSaveApiKey)
      window.aiSaveApiKey = function(provider, key) { window.parent.aiSaveApiKey(provider, key); }
      
   if (window.parent.aiDeleteApiKey)
      window.aiDeleteApiKey = function(provider) { window.parent.aiDeleteApiKey(provider); }
      
   if (window.parent.aiSetActiveProvider)
      window.aiSetActiveProvider = function(provider) { window.parent.aiSetActiveProvider(provider); }

   if (window.parent.aiSetModel)
      window.aiSetModel = function(provider, model) { window.parent.aiSetModel(provider, model); }

   if (window.parent.aiSetWorkingDirectory)
      window.aiSetWorkingDirectory = function(dir) { window.parent.aiSetWorkingDirectory(dir); }

   window.addEventListener("load", function(event) {

      // https://github.com/rstudio/rmarkdown/blob/de02c926371fdadc4d92f08e1ad7b77db069be49/inst/rmarkdown/templates/html_vignette/resources/vignette.css#L187-L201
      var classMap = {
         "at": "ace_keyword ace_operator",
         "ch": "ace_string",
         "co": "ace_comment",
         "cf": "ace_keyword",
         "cn": "ace_constant ace_language",
         "dt": "ace_identifier",
         "dv": "ace_constant ace_numeric",
         "er": "ace_keyword ace_operator",
         "fu": "ace_identifier",
         "kw": "ace_keyword",
         "ot": "ace_keyword ace_operator",
         "sc": "ace_keyword ace_operator",
         "st": "ace_string",
      };

      var els = document.querySelectorAll(".sourceCode span");
      for (el of els)
         el.className = classMap[el.className] || el.className;

   });

</script>
)EOF";


// Filter for HTML content
class AiContentsFilter : public boost::iostreams::aggregate_filter<char>
{
public:
   typedef std::vector<char> Characters;

   explicit AiContentsFilter(const http::Request& request)
   {
      request_uri_ = request.uri();
   }

   void do_filter(const Characters& src, Characters& dest)
   {
      std::string base_url = http::URL::uncomplete(
            request_uri_,
            kAiLocation);

      // copy from src to dest
      dest = src;
      
      // fixup hard-coded hrefs
      boost::algorithm::replace_all(dest, "href=\"/", "href=\"" + base_url + "/");
      boost::algorithm::replace_all(dest, "href='/", "href='" + base_url + "/");
      
      // fixup hard-coded src=
      boost::algorithm::replace_all(dest, "src=\"/", "src=\"" + base_url + "/");
      boost::algorithm::replace_all(dest, "src='/", "src='" + base_url + "/");
      
      // add classes to headers
      boost::regex re_header("<h3>Arguments</h3>");
      std::string re_format("<h3 class=\"r-arguments-title\">Arguments</h3>");
      boost::algorithm::replace_all_regex(dest, re_header, re_format);
      
      // append javascript callbacks
      std::string js(kJsCallbacks);
      std::copy(js.begin(), js.end(), std::back_inserter(dest));
   }
   
private:
   std::string request_uri_;
};

// Get the AI base directory using the new helper function
FilePath getAiBaseDirectory()
{
   std::string path;
   Error error = r::exec::evaluateString(".rs.get_ai_base_dir()", &path);
   if (error)
   {
      LOG_ERROR(error);
      return FilePath();
   }
   return FilePath(path);
}

class AiFontSizeFilter : public boost::iostreams::aggregate_filter<char>
{
public:
   typedef std::vector<char> Characters;

   void do_filter(const Characters& src, Characters& dest)
   {
      std::string css_value(src.begin(), src.end());
      css_value.append("body, td {\n   font-size:");
      css_value.append(safe_convert::numberToString(prefs::userPrefs().helpFontSizePoints()));
      css_value.append("pt;\n}");
      std::copy(css_value.begin(), css_value.end(), std::back_inserter(dest));
   }
};

template <typename Filter>
void setDynamicContentResponse(const std::string& content,
                                  const http::Request& request,
                                  const Filter& filter,
                                  http::Response* p_response)
{
   // always attempt gzip
   if (request.acceptsEncoding(http::kGzipEncoding))
      p_response->setContentEncoding(http::kGzipEncoding);
   
   // if the response doesn't already have Cache-Control then send an eTag back
   // and force revalidation (not for desktop mode since it doesn't handle
   // eTag-based caching)
   if (!p_response->containsHeader("Cache-Control") &&
       options().programMode() == kSessionProgramModeServer)
   {
      // force cache revalidation since this is dynamic content
      p_response->setCacheWithRevalidationHeaders();

      // set as cacheable content (uses eTag/If-None-Match)
      Error error = p_response->setCacheableBody(content, request, filter);
      if (error)
      {
         p_response->setError(http::status::InternalServerError,
                             error.getMessage());
      }
   }
   // otherwise just leave it alone
   else
   {
      p_response->setBody(content, filter);
   }
}

void handleAiRequest(const http::Request& request, http::Response* p_response)
{
   // Get the requested path
   std::string path = http::util::pathAfterPrefix(request, kAiLocation);
   
   if (boost::algorithm::ends_with(path, ".html") && path.find("doc/html/") != std::string::npos)
   {
      // Extract the filename
      std::string filename = boost::algorithm::replace_all_copy(path, "doc/html/", "");
      
      // Remove leading slash if present
      if (!filename.empty() && filename[0] == '/')
         filename = filename.substr(1);
      
      // Build the full path to the file using the new AI base directory
      FilePath aiDocDir = getAiBaseDirectory();
      
      // Make sure the directory exists
      if (!aiDocDir.exists())
      {
         Error error = aiDocDir.ensureDirectory();
         if (error)
         {
            LOG_ERROR(error);
            p_response->setError(http::status::InternalServerError, "Failed to create AI directory");
            return;
         }
      }
      
      FilePath filePath = aiDocDir.completeChildPath(filename);
      
      // Serve the file if it exists
      if (filePath.exists())
      {
         // Set content type and encoding for proper HTML handling
         p_response->setContentType("text/html; charset=UTF-8");
         
         // Read the file content
         std::string content;
         Error error = core::readStringFromFile(filePath, &content);
         if (error)
         {
            LOG_ERROR(error);
            p_response->setError(http::status::InternalServerError, "Failed to read file content");
            return;
         }
         
         // Set the response body
         p_response->setBody(content, AiContentsFilter(request));
         return;
      }
   }

   // server custom css file if necessary
   if (boost::algorithm::ends_with(path, "/R.css"))
   {
      core::FilePath cssFile = options().rResourcesPath().completeChildPath("R.css");
      if (cssFile.exists())
      {
         // ignoring the filter parameter here because the only other possible filter 
         // is AiContentsFilter which is for html
         p_response->setFile(cssFile, request, AiFontSizeFilter());
         return;
      }
   }
   
   // For any other AI requests, delegate to the R implementation
   // Create the R call
   r::sexp::Protect rp;
   SEXP httpd_sexp;
   
   // Call the R httpd function with the path
   r::exec::RFunction httpd("tools:::httpd");
   httpd.addParam(path);
   httpd.addParam(R_NilValue);  // query
   httpd.addParam(R_NilValue);  // postBody
   
   Error error = httpd.call(&httpd_sexp, &rp);
   
   // Handle errors
   if (error)
   {
      p_response->setError(http::status::InternalServerError, error.getMessage());
      return;
   }
   
   // Process the response if it's a valid R list
   if (TYPEOF(httpd_sexp) == VECSXP && r::sexp::length(httpd_sexp) >= 4)
   {
      // Extract response components
      std::string payload;
      if (TYPEOF(VECTOR_ELT(httpd_sexp, 0)) == STRSXP)
         payload = CHAR(STRING_ELT(VECTOR_ELT(httpd_sexp, 0), 0));
      
      std::string content_type;
      if (TYPEOF(VECTOR_ELT(httpd_sexp, 1)) == STRSXP)
         content_type = CHAR(STRING_ELT(VECTOR_ELT(httpd_sexp, 1), 0));
      
      int status = r::sexp::asInteger(VECTOR_ELT(httpd_sexp, 3));
      
      // Set response
      p_response->setStatusCode(status);
      p_response->setContentType(content_type);
      p_response->setBody(payload);
   }
}





Error createNewConversation(const json::JsonRpcRequest& request,
                          json::JsonRpcResponse* p_response)
{
   // Create a variable to hold the result
   SEXP result_sexp;
   r::sexp::Protect rp;
   
   // Call the R function and get the result
   Error error = r::exec::RFunction(".rs.create_new_conversation").call(&result_sexp, &rp);
   if (error)
      return error;
   
   // Convert the R result to JSON and set it in the response
   json::Value result_json;
   error = r::json::jsonValueFromList(result_sexp, &result_json);
   if (error)
      return error;
   
   p_response->setResult(result_json);
   return Success();
}

Error aiAcceptEditFileCommand(const json::JsonRpcRequest& request,
                     json::JsonRpcResponse* p_response,
                     const std::string& edited_code,
                     const std::string& message_id,
                     const std::string& request_id)
{

   // Call the R function and capture result
   SEXP result_sexp;
   r::sexp::Protect rp;
   Error error = r::exec::RFunction(".rs.accept_edit_file_command")
         .addParam(edited_code)
         .addParam(message_id)
         .addParam(request_id)  // request_id
         .call(&result_sexp, &rp);

   if (error) {
      LOG_ERROR(error);
      return error;
   }

   // Check if R function returned a result object with status information
   if (result_sexp != R_NilValue) {
      json::Value result_json;
      Error json_error = r::json::jsonValueFromList(result_sexp, &result_json);
      if (!json_error) {
         p_response->setResult(result_json);
      }
   }

   return Success();
}

Error getFileNameForMessageId(const json::JsonRpcRequest& request,
                             json::JsonRpcResponse* p_response,
                             const std::string& message_id)
{
   // Create a variable to hold the result
   std::string filename;
   
   // Call the R function and get the result
   Error error = r::exec::RFunction(".rs.get_file_name_for_message_id")
         .addParam(message_id)
         .call(&filename);
   
   if (error)
      LOG_ERROR(error);
   
   // Set the result in the response
   p_response->setResult(filename);
   return Success();
}

// Function to check if the .rs.terminal_done flag exists in the global environment
Error checkTerminalComplete(const json::JsonRpcRequest& request,
                          json::JsonRpcResponse* p_response)
{
   // Extract message_id from request parameters
   int message_id = 0;
   Error error = json::readParam(request.params, 0, &message_id);
   if (error)
      return error;

   // Call the R function via JSON-RPC to check if terminal execution is complete
   bool is_complete = false;
   error = r::exec::RFunction(".rs.check_terminal_complete")
         .addParam(message_id)
         .call(&is_complete);

   if (error)
   {
      LOG_ERROR(error);
      p_response->setResult(false);
      return Success();
   }

   p_response->setResult(is_complete);
   return Success();
}

// Function to clear the .rs.terminal_done flag from the global environment
Error clearTerminalDoneFlag(const json::JsonRpcRequest& request,
                           json::JsonRpcResponse* p_response)
{
   Error error = r::exec::RFunction(".rs.remove_from_global_env")
         .addParam(".rs.terminal_done")
         .call();

   if (error)
      LOG_ERROR(error);

   return Success();
}

// Function to clear the .rs.console_done flag from the global environment
Error clearConsoleDoneFlag(const json::JsonRpcRequest& request,
                           json::JsonRpcResponse* p_response)
{
   Error error = r::exec::RFunction(".rs.remove_from_global_env")
         .addParam(".rs.console_done")
         .call();

   if (error)
      LOG_ERROR(error);

   return Success();
}

// Function to finalize console command execution after polling determines it's complete
Error finalizeConsoleCommand(const json::JsonRpcRequest& request,
                           json::JsonRpcResponse* p_response)
{
   // Extract message_id and request_id from request parameters
   int message_id = 0;
   Error error = json::readParam(request.params, 0, &message_id);
   if (error) {
      std::cerr << "ERROR: Error reading message_id parameter: " << error.getSummary() << std::endl;
      return error;
   }

   std::string request_id;
   error = json::readParam(request.params, 1, &request_id);
   if (error) {
      std::cerr << "ERROR: Error reading request_id parameter: " << error.getSummary() << std::endl;
      // Try with empty request_id if parameter is missing
      request_id = "";
   }

   std::string console_output;
   error = json::readParam(request.params, 2, &console_output);
   if (error) {
      // Console output parameter is optional, default to empty string
      console_output = "";
   }

   // Call the R function to finalize the console command and capture result
   SEXP result_sexp;
   r::sexp::Protect rp;
   error = r::exec::RFunction(".rs.finalize_console_command")
         .addParam(message_id)
         .addParam(request_id)
         .addParam(console_output)
         .call(&result_sexp, &rp);

   if (error)
   {
      std::cerr << "ERROR: Error calling R function: " << error.getSummary() << std::endl;
      LOG_ERROR(error);
      return error;
   }

   // Check if R function returned a result object with status information
   if (!r::sexp::isNull(result_sexp))
   {
      // Try to extract the result as JSON and pass it to the response
      json::Value result_json;
      error = r::json::jsonValueFromObject(result_sexp, &result_json);
      if (!error && result_json.isObject())
      {
         json::Object result_obj = result_json.getObject();
         // Set the response to include the R function result
         p_response->setResult(result_obj);
      }
      else if (error)
      {
         std::cerr << "ERROR: Failed to convert R result to JSON in finalizeConsoleCommand" << std::endl;
         std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
         std::cerr << "ERROR: SEXP type info: " << TYPEOF(result_sexp) << std::endl;
      }
   } else {
      std::cerr << "ERROR: R function returned NULL result" << std::endl;
   }

   return Success();
}

Error finalizeTerminalCommand(const json::JsonRpcRequest& request,
                            json::JsonRpcResponse* p_response)
{
   // Extract message_id and request_id from request parameters
   int message_id = 0;
   Error error = json::readParam(request.params, 0, &message_id);
   if (error) {
      std::cerr << "ERROR: Error reading message_id parameter: " << error.getSummary() << std::endl;
      return error;
   }

   std::string request_id;
   error = json::readParam(request.params, 1, &request_id);
   if (error) {
      std::cerr << "ERROR: Error reading request_id parameter: " << error.getSummary() << std::endl;
      // Try with empty request_id if parameter is missing
      request_id = "";
   }

   // Call the R function to finalize the terminal command and capture result
   SEXP result_sexp;
   r::sexp::Protect rp;
   error = r::exec::RFunction(".rs.finalize_terminal_command")
         .addParam(message_id)
         .addParam(request_id)
         .call(&result_sexp, &rp);

   if (error)
   {
      std::cerr << "ERROR: Error calling R function: " << error.getSummary() << std::endl;
      LOG_ERROR(error);
      return error;
   }

   // Check if R function returned a result object with status information
   if (!r::sexp::isNull(result_sexp))
   {      
      // Try to extract the result as JSON and pass it to the response
      json::Value result_json;
      error = r::json::jsonValueFromObject(result_sexp, &result_json);
      if (!error && result_json.isObject())
      {
      json::Object result_obj = result_json.getObject();
      
      // Set the response to include the R function result
      p_response->setResult(result_obj);
      
      if (result_obj.hasMember("status"))
      {
         json::Value status_value = result_obj["status"];
         if (status_value.isString()) {
            std::string status = status_value.getString();
         } else if (status_value.isArray() && status_value.getArray().getSize() > 0) {
            json::Array status_array = status_value.getArray();
            json::Value first_status = status_array[0];
            if (first_status.isString()) {
               std::string status = first_status.getString();
            }
         }
      }
      }
      else if (error)
      {
         std::cerr << "ERROR: Failed to convert R result to JSON in finalizeTerminalCommand" << std::endl;
         std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
         std::cerr << "ERROR: SEXP type info: " << TYPEOF(result_sexp) << std::endl;
      }
   }

   return Success();
}

Error addConsoleOutputToAiConversation(const json::JsonRpcRequest& request,
                                     json::JsonRpcResponse* p_response,
                                     const int message_id)
{
   SEXP result_sexp;
   r::sexp::Protect rp;
   
   Error error = r::exec::RFunction(".rs.add_console_output_to_conversation")
         .addParam(message_id)
         .call(&result_sexp, &rp);

   if (error)
   {
      LOG_ERROR(error);
      return error;
   }
   
   // Set the result (has_error) in the response
   bool has_error = false;
   if (TYPEOF(result_sexp) == LGLSXP)
      has_error = Rf_asLogical(result_sexp) == TRUE;
      
   p_response->setResult(has_error);
   return Success();
}

Error addTerminalOutputToAiConversation(const json::JsonRpcRequest& request,
                                     json::JsonRpcResponse* p_response,
                                     const int message_id)
{
   SEXP result_sexp;
   r::sexp::Protect rp;
   
   Error error = r::exec::RFunction(".rs.add_terminal_output_to_conversation")
         .addParam(message_id)
         .call(&result_sexp, &rp);

   if (error)
   {
      LOG_ERROR(error);
      return error;
   }
   
   // Set the result (has_error) in the response
   bool has_error = false;
   if (TYPEOF(result_sexp) == LGLSXP)
      has_error = Rf_asLogical(result_sexp) == TRUE;
      
   p_response->setResult(has_error);
   return Success();
}

Error revertAiMessage(const json::JsonRpcRequest& request,
                    json::JsonRpcResponse* p_response,
                    int message_id)
{
   Error error = r::exec::RFunction(".rs.revert_ai_message")
         .addParam(message_id)
         .call();

   if (error)
      LOG_ERROR(error);

   return Success();
}

Error deleteFolder(const json::JsonRpcRequest& request,
                  json::JsonRpcResponse* p_response,
                  const std::string& path)
{
   Error error = r::exec::RFunction(".rs.delete_folder")
         .addParam(path)
         .call();

   if (error)
      LOG_ERROR(error);

   return Success();
}

// Implementation of RPC methods
Error saveApiKey(const json::JsonRpcRequest& request,
                json::JsonRpcResponse* p_response,
                const std::string& provider,
                const std::string& key)
{
   Error error = r::exec::RFunction(".rs.save_api_key")
         .addParam(provider)
         .addParam(key)
         .call();

   if (error)
      LOG_ERROR(error);

   return Success();
}

Error deleteApiKey(const json::JsonRpcRequest& request,
                  json::JsonRpcResponse* p_response,
                  const std::string& provider)
{
   Error error = r::exec::RFunction(".rs.delete_api_key")
         .addParam(provider)
         .call();

   if (error)
      LOG_ERROR(error);

   return Success();
}

Error setActiveProvider(const json::JsonRpcRequest& request,
                        json::JsonRpcResponse* p_response,
                        const std::string& provider)
{
   // Call the R handler for setting active provider
   bool success = false;
   Error error = r::exec::RFunction(".rs.set_active_provider_action")
         .addParam(provider)
         .call(&success);
         
   if (error)
      LOG_ERROR(error);
   
   p_response->setResult(success);     
   return error;
}

Error setModel(const json::JsonRpcRequest& request,
               json::JsonRpcResponse* p_response,
               const std::string& provider,
               const std::string& model)
{
   // Call the R handler for setting model
   bool success = false;
   Error error = r::exec::RFunction(".rs.set_model_action")
         .addParam(provider)
         .addParam(model)
         .call(&success);
         
   if (error)
      LOG_ERROR(error);
   
   p_response->setResult(success);
   return error;
}

// Function to get a conversation name by ID
Error getConversationName(const json::JsonRpcRequest& request,
                         json::JsonRpcResponse* p_response,
                         int conversation_id)
{
   std::string name;
   Error error = r::exec::RFunction(".rs.get_conversation_name")
         .addParam(conversation_id)
         .call(&name);

   if (error)
      LOG_ERROR(error);
   
   p_response->setResult(name);
   return Success();
}

// Function to set a conversation name
Error setConversationName(const json::JsonRpcRequest& request,
                         json::JsonRpcResponse* p_response,
                         int conversation_id,
                         const std::string& name)
{   
   Error error = r::exec::RFunction(".rs.set_conversation_name")
         .addParam(conversation_id)
         .addParam(name)
         .call();

   if (error)
   {
      LOG_ERROR(error);
   }

   return Success();
}

// Function to delete a conversation name
Error deleteConversationName(const json::JsonRpcRequest& request,
                           json::JsonRpcResponse* p_response,
                           int conversation_id)
{
   Error error = r::exec::RFunction(".rs.delete_conversation_name")
         .addParam(conversation_id)
         .call();

   if (error)
      LOG_ERROR(error);

   return Success();
}

// Function to list all conversation names
Error listConversationNames(const json::JsonRpcRequest& request,
                          json::JsonRpcResponse* p_response)
{
   SEXP result_sexp;
   r::sexp::Protect rp;
   
   // Call the R function and get the result
   Error error = r::exec::RFunction(".rs.list_conversation_names").call(&result_sexp, &rp);
   if (error)
      return error;
   
   // Convert the R result to JSON and set it in the response
   json::Value resultJson;
   error = r::json::jsonValueFromList(result_sexp, &resultJson);
   if (error)
      return error;
   
   p_response->setResult(resultJson);
   return Success();
}

// Function to check if we should prompt for a conversation name
Error shouldPromptForName(const json::JsonRpcRequest& request,
                        json::JsonRpcResponse* p_response)
{
   // Call R to check if we should prompt for name
   bool shouldPrompt = false;
   Error error = r::exec::RFunction(".rs.ai.should_prompt_for_name").call(&shouldPrompt);
   if (error)
      return error;
   
   p_response->setResult(shouldPrompt);
   return Success();
}

Error generateConversationName(const json::JsonRpcRequest& request,
                             json::JsonRpcResponse* p_response,
                             int conversation_id)
{
   // Call R to generate a name for the conversation using the OpenAI API
   std::string generated_name;
   Error error = r::exec::RFunction(".rs.ai.generate_conversation_name", conversation_id).call(&generated_name);
   if (error)
      return error;
   p_response->setResult(generated_name);
   return Success();
}

// Function to get conversation log data for streaming panel
Error getConversationLog(const json::JsonRpcRequest& request,
                        json::JsonRpcResponse* p_response,
                        int conversation_id)
{
   r::sexp::Protect rProtect;
   SEXP result_sexp;
   Error error = r::exec::RFunction(".rs.get_conversation_log")
         .addParam(conversation_id)
         .call(&result_sexp, &rProtect);

   if (error)
   {
      LOG_ERROR(error);
      return error;
   }

   // Convert R result to JSON
   json::Value resultJson;
   error = r::json::jsonValueFromObject(result_sexp, &resultJson);
   if (error)
   {
      LOG_ERROR(error);
      return error;
   }

   p_response->setResult(resultJson);
   return Success();
}

Error saveAiAttachment(const json::JsonRpcRequest& request,
                      json::JsonRpcResponse* p_response,
                      const std::string& file_path)
{
   // Call R function to save attachment information
   Error error = r::exec::RFunction(".rs.save_ai_attachment", 
                                    file_path).call();
   if (error)
      return error;
   
   return Success();
}

// Add server-side implementation for listing attachments
Error listAttachments(const json::JsonRpcRequest& request,
                     json::JsonRpcResponse* p_response)
{
   // Call the R function to list attachments
   SEXP result_sexp;
   r::sexp::Protect rp;
   
   // Call the R function and get the result
   Error error = r::exec::RFunction(".rs.list_ai_attachments").call(&result_sexp, &rp);
   if (error) {
      LOG_ERROR(error);
      return error;
   }
         
   // Convert the R result to a JSON array of strings
   json::Array attachmentsArray;
   
   if (TYPEOF(result_sexp) == STRSXP) {
      // Handle character vector
      int n = Rf_length(result_sexp);
      
      for (int i = 0; i < n; i++) {
         std::string path = r::sexp::asString(STRING_ELT(result_sexp, i));
         attachmentsArray.push_back(path);
      }
   }
   
   p_response->setResult(attachmentsArray);
   return Success();
}

// Add server-side implementation for deleting a specific attachment
Error deleteAttachment(const json::JsonRpcRequest& request,
                      json::JsonRpcResponse* p_response,
                      const std::string& file_path)
{
   // Call the R function to delete the attachment
   Error error = r::exec::RFunction(".rs.delete_ai_attachment")
         .addParam(file_path)
         .call();
   
   if (error)
      LOG_ERROR(error);
   
   return Success();
}

// Add server-side implementation for deleting all attachments
Error deleteAllAttachments(const json::JsonRpcRequest& request,
                         json::JsonRpcResponse* p_response)
{
   // Call the R function to delete all attachments
   Error error = r::exec::RFunction(".rs.delete_all_ai_attachments").call();
   
   if (error)
      LOG_ERROR(error);
   
   return Success();
}

// Add server-side implementation for cleaning up conversation attachments
Error cleanupConversationAttachments(const json::JsonRpcRequest& request,
                                   json::JsonRpcResponse* p_response,
                                   int conversation_id)
{

   
   // Call the R function to cleanup conversation attachments
   Error error = r::exec::RFunction(".rs.cleanup_conversation_attachments", conversation_id).call();
   
   if (error)
   {
      LOG_ERROR(error);
      return error;
   }
   
   return Success();
}

// Add server-side implementation for saving AI images
Error saveAiImage(const json::JsonRpcRequest& request,
                 json::JsonRpcResponse* p_response,
                 const std::string& image_path)
{
   // Call R function to save image information
   Error error = r::exec::RFunction(".rs.save_ai_image", 
                                    image_path).call();
   if (error)
      return error;
   
   return Success();
}

// Add server-side implementation for saving image data URLs
Error createTempImageFile(const json::JsonRpcRequest& request,
                         json::JsonRpcResponse* p_response,
                         const std::string& data_url,
                         const std::string& file_name)
{
   // Call R function to create temporary image file
   SEXP result_sexp;
   r::sexp::Protect rp;
   
   Error error = r::exec::RFunction(".rs.create_temp_image_file")
         .addParam(data_url)
         .addParam(file_name)
         .call(&result_sexp, &rp);
   
   if (error) {
      LOG_ERROR(error);
      return error;
   }
   
   // Convert the R result to a string (the temp file path)
   std::string temp_path;
   if (TYPEOF(result_sexp) == STRSXP && r::sexp::length(result_sexp) > 0) {
      temp_path = r::sexp::asString(STRING_ELT(result_sexp, 0));
   }
   
   p_response->setResult(temp_path);
   return Success();
}

// Add server-side implementation for listing images
Error listImages(const json::JsonRpcRequest& request,
               json::JsonRpcResponse* p_response)
{
   // Call the R function to list images
   SEXP result_sexp;
   r::sexp::Protect rp;
   
   // Call the R function and get the result
   Error error = r::exec::RFunction(".rs.list_ai_images").call(&result_sexp, &rp);
   if (error) {
      LOG_ERROR(error);
      return error;
   }
         
   // Convert the R result to a JSON array of strings
   json::Array imagesArray;
   
   if (TYPEOF(result_sexp) == STRSXP) {
      // Handle character vector
      int n = Rf_length(result_sexp);
      
      for (int i = 0; i < n; i++) {
         std::string path = r::sexp::asString(STRING_ELT(result_sexp, i));
         imagesArray.push_back(path);
      }
   }
   
   p_response->setResult(imagesArray);
   return Success();
}

// Add server-side implementation for deleting a specific image
Error deleteImage(const json::JsonRpcRequest& request,
                 json::JsonRpcResponse* p_response,
                 const std::string& image_path)
{
   // Call the R function to delete the image
   Error error = r::exec::RFunction(".rs.delete_ai_image")
         .addParam(image_path)
         .call();
   
   if (error)
      LOG_ERROR(error);
   
   return Success();
}

// Add server-side implementation for deleting all images
Error deleteAllImages(const json::JsonRpcRequest& request,
                    json::JsonRpcResponse* p_response)
{
   // Call the R function to delete all images
   Error error = r::exec::RFunction(".rs.delete_all_ai_images").call();
   
   if (error)
      LOG_ERROR(error);
   
   return Success();
}

// Add server-side implementation for checking image content duplicates
Error checkImageContentDuplicate(const json::JsonRpcRequest& request,
                                json::JsonRpcResponse* p_response,
                                const std::string& image_path)
{
   // Call the R function to check for duplicate image content
   SEXP result_sexp;
   r::sexp::Protect rp;
   
   Error error = r::exec::RFunction(".rs.check_image_content_duplicate")
         .addParam(image_path)
         .call(&result_sexp, &rp);
   
   if (error) {
      LOG_ERROR(error);
      return error;
   }
   
   // Convert the R result to a boolean
   bool isDuplicate = false;
   if (TYPEOF(result_sexp) == LGLSXP) {
      isDuplicate = Rf_asLogical(result_sexp) == TRUE;
   }
   
   p_response->setResult(isDuplicate);
   return Success();
}

Error markButtonAsRun(const json::JsonRpcRequest& request,
                     json::JsonRpcResponse* p_response,
                     const std::string& message_id,
                     const std::string& button_type)
{
   Error error = r::exec::RFunction(".rs.mark_button_as_run")
         .addParam(message_id)
         .addParam(button_type)
         .call();

   if (error)
      LOG_ERROR(error);

   // Return true if there was no error
   p_response->setResult(!error);
   return Success();
}

Error runAcceptedCode(const json::JsonRpcRequest& request,
                     json::JsonRpcResponse* p_response,
                     const std::string& filename,
                     const int message_id)
{
   Error error = r::exec::RFunction(".rs.run_accepted_code")
         .addParam(filename)
         .addParam(message_id)
         .call();

   if (error)
      LOG_ERROR(error);

   return Success();
}



// Function to check if a conversation is empty
Error isConversationEmpty(const json::JsonRpcRequest& request,
                         json::JsonRpcResponse* p_response,
                         int conversation_id)
{
   // Call R to check if the conversation is empty
   bool isEmpty = false;
   Error error = r::exec::RFunction(".rs.is_conversation_empty")
         .addParam(conversation_id)
         .call(&isEmpty);
         
   if (error)
      LOG_ERROR(error);
   
   p_response->setResult(isEmpty);
   return Success();
}

// Get the file path for a tab from its ID
Error getTabFilePath(const json::JsonRpcRequest& request,
                    json::JsonRpcResponse* p_response,
                    const std::string& tab_id)
{
   // Call the R function to get the tab file path
   std::string file_path;
   Error error;
   
   // Use safe call to avoid throwing errors if the result is not a string
   try
   {
      r::sexp::Protect rProtect;
      SEXP result_sexp;
      error = r::exec::RFunction(".rs.get_tab_file_path")
            .addParam(tab_id)
            .call(&result_sexp, &rProtect);
      
      if (!error)
      {
         // Safely extract string even if result_sexp is not a character vector
         if (TYPEOF(result_sexp) == STRSXP && r::sexp::length(result_sexp) > 0)
         {
            file_path = r::sexp::asString(STRING_ELT(result_sexp, 0));
         }
         else
         {
            // If the result is not a string, default to empty string
            file_path = "";
         }
      }
   }
   catch(...)
   {
      // Catch any unexpected errors and return empty string
      file_path = "";
      LOG_ERROR(Error(boost::system::errc::invalid_argument, 
                     "Failed to get tab file path for " + tab_id, 
                     ERROR_LOCATION));
   }
   
   p_response->setResult(file_path);
   return Success();
}

Error setAiWorkingDirectory(const json::JsonRpcRequest& request,
                         json::JsonRpcResponse* p_response,
                         const std::string& dir)
{
   // Call R function to set working directory
   r::sexp::Protect rp;
   SEXP result_sexp;
   Error error = r::exec::RFunction(".rs.set_ai_working_directory")
         .addParam(dir)
         .call(&result_sexp, &rp);

   if (error)
   {
      LOG_ERROR(error);
      return error;
   }

   // Extract success from R response
   bool success = false;
   
   if (TYPEOF(result_sexp) == VECSXP && r::sexp::length(result_sexp) >= 1)
   {
      // Get success value
      SEXP successSEXP = VECTOR_ELT(result_sexp, 0);
      if (TYPEOF(successSEXP) == LGLSXP)
         success = r::sexp::asLogical(successSEXP);
   }

   // Just set the result, don't throw an error
   p_response->setResult(success);
   return Success();
}

Error browseDirectory(const json::JsonRpcRequest& request,
                     json::JsonRpcResponse* p_response)
{
   // Call R function to browse directory
   r::sexp::Protect rp;
   SEXP result_sexp;
   Error error = r::exec::RFunction(".rs.browse_directory").call(&result_sexp, &rp);
   
   if (error)
   {
      LOG_ERROR(error);
      return error;
   }
   
   // Extract the directory and success from the list result
   bool success = false;
   std::string directory;
   std::string error_msg;
   
   if (TYPEOF(result_sexp) == VECSXP && r::sexp::length(result_sexp) >= 1)
   {
      // Get success value
      SEXP success_sexp = VECTOR_ELT(result_sexp, 0);
      if (TYPEOF(success_sexp) == LGLSXP)
         success = r::sexp::asLogical(success_sexp);
      
      if (success && r::sexp::length(result_sexp) >= 2)
      {
         // Get directory value if success
         SEXP directory_sexp = VECTOR_ELT(result_sexp, 1);
         if (TYPEOF(directory_sexp) == STRSXP && r::sexp::length(directory_sexp) > 0)
            directory = r::sexp::asString(directory_sexp);
      }
      else if (!success && r::sexp::length(result_sexp) >= 2)
      {
         // Get error message if !success
         SEXP error_sexp = VECTOR_ELT(result_sexp, 1);
         if (TYPEOF(error_sexp) == STRSXP && r::sexp::length(error_sexp) > 0)
            error_msg = r::sexp::asString(error_sexp);
      }
   }
   
   // Create JSON object with the results
   json::Object result_json;
   result_json["success"] = success;
   
   if (success)
      result_json["directory"] = directory;
   else if (!error_msg.empty())
      result_json["error"] = error_msg;
   
   p_response->setResult(result_json);
   return Success();
}

Error browseForFile(const json::JsonRpcRequest& request,
                     json::JsonRpcResponse* p_response)
{
   // Call R function to browse for file
   r::sexp::Protect rp;
   SEXP result_sexp;
   Error error = r::exec::RFunction(".rs.browse_for_file").call(&result_sexp, &rp);
   
   if (error)
   {
      LOG_ERROR(error);
      return error;
   }
   
   // Process the result and convert to a FileSystemItem
   if (TYPEOF(result_sexp) == STRSXP && r::sexp::length(result_sexp) > 0)
   {
      std::string path = r::sexp::asString(result_sexp);
      if (!path.empty())
      {
         // Expand the path if it starts with ~ (home directory)
         if (path.length() > 0 && path[0] == '~')
         {
            // Use R to expand the path
            std::string expanded_path;
            Error expand_error = r::exec::RFunction("path.expand")
               .addParam(path)
               .call(&expanded_path);
               
            if (!expand_error)
            {
               path = expanded_path;
            }
         }
         
         // Create a FileSystemItem from the selected file path
         core::FilePath file_path = core::FilePath(path);
                  
         // Return the FileSystemItem even if the file doesn't exist locally
         // This allows using remote files that might be accessible in R but not directly to RStudio
         p_response->setResult(module_context::createFileSystemItem(file_path));
         return Success();
      }
   }
   
   // Return null if no file was selected or an error occurred
   p_response->setResult(core::json::Value());
   return Success();
}

} // anonymous namespace

// Context item handler functions
Error addContextItem(const json::JsonRpcRequest& request,
                   json::JsonRpcResponse* p_response)
{
   std::string path;
   Error error = json::readParam(request.params, 0, &path);
   if (error)
      return error;
   
   r::sexp::Protect r_protect;
   SEXP result;
   error = r::exec::RFunction(".rs.add_context_item", path).call(&result, &r_protect);
   if (error)
      return error;
   
   bool success = r::sexp::asLogical(result);
   p_response->setResult(success);
   
   return Success();
}

Error addContextLines(const json::JsonRpcRequest& request,
                     json::JsonRpcResponse* p_response)
{
   std::string path;
   int start_line, end_line;
   Error error = json::readParams(request.params, &path, &start_line, &end_line);
   if (error)
      return error;
   
   r::sexp::Protect r_protect;
   SEXP result;
   error = r::exec::RFunction(".rs.add_context_lines")
         .addParam(path)
         .addParam(start_line)
         .addParam(end_line)
         .call(&result, &r_protect);
   if (error)
      return error;
   
   bool success = r::sexp::asLogical(result);
   p_response->setResult(success);
   
   return Success();
}

Error getContextItems(const json::JsonRpcRequest& request,
                    json::JsonRpcResponse* p_response)
{
   r::sexp::Protect r_protect;
   SEXP result;
   Error error = r::exec::RFunction(".rs.get_context_items").call(&result, &r_protect);
   if (error)
      return error;
   
   // Check the type of result - if it's a character vector, convert it to a JSON array
   if (TYPEOF(result) == STRSXP)
   {
      // Convert directly to a JSON array
      core::json::Array json_array;
      int len = r::sexp::length(result);
      for (int i = 0; i < len; i++)
      {
         std::string path = r::sexp::asString(STRING_ELT(result, i));
         json_array.push_back(path);
      }
      p_response->setResult(json_array);
      return Success();
   }
   
   // Otherwise use standard conversion
   json::Value result_json;
   error = r::json::jsonValueFromList(result, &result_json);
   if (error)
      return error;
   
   p_response->setResult(result_json);
   
   return Success();
}

Error removeContextItem(const json::JsonRpcRequest& request,
                      json::JsonRpcResponse* p_response)
{
   std::string path;
   Error error = json::readParam(request.params, 0, &path);
   if (error)
      return error;
   
   r::sexp::Protect r_protect;
   SEXP result;
   error = r::exec::RFunction(".rs.remove_context_item", path).call(&result, &r_protect);
   if (error)
      return error;
   
   bool success = r::sexp::asLogical(result);
   p_response->setResult(success);
   
   return Success();
}

Error clearContextItems(const json::JsonRpcRequest& request,
                       json::JsonRpcResponse* p_response)
{
   r::sexp::Protect r_protect;
   SEXP result;
   Error error = r::exec::RFunction(".rs.clear_context_items").call(&result, &r_protect);
   if (error)
      return error;
   
   p_response->setResult(json::Value());
   
   return Success();
}
   

Error getCurrentConversationIndex(const json::JsonRpcRequest& request,
                                json::JsonRpcResponse* p_response)
{
   // Call the R function to get current conversation index
   SEXP result_sexp;
   r::sexp::Protect rp;
   Error error = r::exec::RFunction(".rs.get_current_conversation_index").call(&result_sexp, &rp);
   if (error)
   {
      LOG_ERROR(error);
      p_response->setResult(0);
      return Success();
   }
   
   // Extract integer from SEXP safely
   int current_index = 0;
   if (TYPEOF(result_sexp) == INTSXP && r::sexp::length(result_sexp) > 0)
   {
      current_index = INTEGER(result_sexp)[0];
   }
   else if (TYPEOF(result_sexp) == REALSXP && r::sexp::length(result_sexp) > 0)
   {
      current_index = (int)REAL(result_sexp)[0];
   }
   else if (TYPEOF(result_sexp) == LGLSXP && r::sexp::length(result_sexp) > 0)
   {
      current_index = LOGICAL(result_sexp)[0] ? 1 : 0;
   }
   else
   {
      // Try to convert using r::sexp::asInteger if it's a different type
      try 
      {
         current_index = r::sexp::asInteger(result_sexp);
      }
      catch(...)
      {
         LOG_ERROR_MESSAGE("Unable to convert R result to integer in getCurrentConversationIndex");
         current_index = 0;
      }
   }
   
   p_response->setResult(current_index);
   return Success();
}

Error getDiffDataForEditFile(const json::JsonRpcRequest& request,
                            json::JsonRpcResponse* p_response,
                            const std::string& message_id)
{
   // Call R function to get diff data for edit file
   SEXP result_sexp;
   r::sexp::Protect rp;
   Error error = r::exec::RFunction(".rs.get_diff_data_for_edit_file", message_id).call(&result_sexp, &rp);
   if (error)
   {
      LOG_ERROR(error);
      // Return empty diff result on error
      json::Object emptyResult;
      emptyResult["diff"] = json::Array();
      p_response->setResult(emptyResult);
      return Success();
   }
   
   // Convert R result to JSON
   json::Value jsonResult;
   error = r::json::jsonValueFromObject(result_sexp, &jsonResult);
   if (error)
   {
      LOG_ERROR(error);
      // Return empty diff result on conversion error
      json::Object emptyResult;
      emptyResult["diff"] = json::Array();
      p_response->setResult(emptyResult);
      return Success();
   }
   
   p_response->setResult(jsonResult);
   return Success();
}

Error getTerminalWebsocketPort(const json::JsonRpcRequest& request,
                         json::JsonRpcResponse* p_response)
{
   // First ensure the WebSocket server is running
   Error error = processSocket().ensureServerRunning();
   if (error)
   {
      LOG_ERROR(error);
      p_response->setResult(0);
      return Success();
   }
   
   // Get the terminal WebSocket port from the ConsoleProcessSocket
   int port = processSocket().port();
   p_response->setResult(port);
   return Success();
}

// Transform a WebSocket port into a channel ID using the port token
Error get_websocket_channel_id(const json::JsonRpcRequest& request,
                         json::JsonRpcResponse* p_response)
{
   int port = 0;
   Error error = json::readParam(request.params, 0, &port);
   if (error)
      return error;
   
   if (port <= 0)
   {
      p_response->setResult("");
      return Success();
   }
   
#ifdef RSTUDIO_SERVER
   // On server, transform the port using the port token
   std::string channel_id = server_core::transformPort(
      persistentState().portToken(), port);
   p_response->setResult(channel_id);
#else
   // On desktop, just return the port as a string
   p_response->setResult(safe_convert::numberToString(port));
#endif
   
   return Success();
}

// Match text against currently open source editor documents
Error matchTextInOpenDocuments(const json::JsonRpcRequest& request,
                               json::JsonRpcResponse* p_response)
{
   std::string search_text;
   Error error = json::readParam(request.params, 0, &search_text);
   if (error)
      return error;
      
   // Remove leading/trailing whitespace and normalize line endings
   boost::algorithm::trim(search_text);
   boost::algorithm::replace_all(search_text, "\r\n", "\n");
   boost::algorithm::replace_all(search_text, "\r", "\n");
   
   // Check if the text contains a line break or matches a complete line from any open document
   // This ensures paste events from files are meaningful content, not just short fragments
   bool has_line_break = search_text.find('\n') != std::string::npos;
   bool is_complete_line = false;
   
   if (!has_line_break) {
      // Check if the text matches a complete line from any open document
      std::vector<boost::shared_ptr<source_database::SourceDocument>> docs;
      Error doc_error = source_database::list(&docs);
      if (!doc_error) {
         for (size_t i = 0; i < docs.size() && !is_complete_line; i++) {
            std::string contents = docs[i]->contents();
            if (!contents.empty()) {
               // Normalize line endings
               boost::algorithm::replace_all(contents, "\r\n", "\n");
               boost::algorithm::replace_all(contents, "\r", "\n");
               
               // Split into lines and check each one
               std::vector<std::string> lines;
               boost::algorithm::split(lines, contents, boost::is_any_of("\n"));
               
               for (const std::string& line : lines) {
                  std::string trimmed_line = boost::algorithm::trim_copy(line);
                  std::string trimmed_search = boost::algorithm::trim_copy(search_text);
                  if (!trimmed_line.empty() && trimmed_line == trimmed_search) {
                     is_complete_line = true;
                     break;
                  }
               }
            }
         }
      }
   }
   
   bool has_line = has_line_break || is_complete_line;
   
   if (!has_line)
   {
      json::Object result;
      result["match"] = false;
      p_response->setResult(result);
      return Success();
   }
   
   // First try to directly check for open documents using the source database
   std::vector<boost::shared_ptr<source_database::SourceDocument>> direct_docs;
   error = source_database::list(&direct_docs);
   if (error)
   {
      LOG_ERROR(error);
   }
   else
   {
      // If we have documents, try to search them directly
      for (size_t i = 0; i < direct_docs.size(); i++)
      {
         boost::shared_ptr<source_database::SourceDocument> p_doc = direct_docs[i];
         std::string doc_id = p_doc->id();
         std::string file_path = p_doc->path();
         std::string contents = p_doc->contents();
         
         // Skip documents without content
         if (contents.empty())
            continue;
            
         // Normalize contents line endings
         boost::algorithm::replace_all(contents, "\r\n", "\n");
         boost::algorithm::replace_all(contents, "\r", "\n");
         
         // Look for exact match
         size_t pos = contents.find(search_text);
         if (pos != std::string::npos)
         {
            // Found a match - now determine line numbers
            std::vector<std::string> lines;
            boost::algorithm::split(lines, contents, boost::is_any_of("\n"));
            
            // Find which line contains the start of the match
            int current_pos = 0;
            int start_line = 1;
            int end_line = 1;
            
            for (size_t line_num = 0; line_num < lines.size(); line_num++)
            {
               int line_length = lines[line_num].length() + 1; // +1 for newline
               
               if (current_pos <= (int)pos && (int)pos < current_pos + line_length)
               {
                  start_line = line_num + 1; // Convert to 1-based
                  break;
               }
               current_pos += line_length;
            }
            
            // Find end line
            size_t match_end = pos + search_text.length();
            current_pos = 0;
            
            for (size_t line_num = 0; line_num < lines.size(); line_num++)
            {
               int line_length = lines[line_num].length() + 1; // +1 for newline
               
               if (current_pos <= (int)match_end && (int)match_end <= current_pos + line_length)
               {
                  end_line = line_num + 1; // Convert to 1-based
                  break;
               }
               current_pos += line_length;
            }
            
            // For unsaved documents, we need to provide a usable path identifier
            // Use the same pattern as the symbol index system
            std::string effective_file_path = file_path;
            if (file_path.empty() && !doc_id.empty()) {
               // Get tempName from document properties
               std::string tempName = p_doc->getProperty("tempName");
               if (!tempName.empty()) {
                  effective_file_path = "__UNSAVED_" + doc_id.substr(0, 4) + "__/" + tempName;
               } else {
                  effective_file_path = "__UNSAVED_" + doc_id.substr(0, 4) + "__/Untitled";
               }
            }
            
            // Return the match result
            json::Object result;
            result["match"] = true;
            result["filePath"] = effective_file_path;  // Use effective path
            result["startLine"] = start_line;  // Changed from start_line to startLine
            result["endLine"] = end_line;  // Changed from end_line to endLine
            result["docId"] = doc_id;  // Changed from doc_id to docId
            p_response->setResult(result);
            return Success();
         }
      }
   }
   
   // Fall back to the R-based method if direct search didn't find anything
   
   // Try direct R call to .rs.api.getSourceEditorContext() first
   SEXP context_sexp;
   r::sexp::Protect context_protect;
   Error context_error = r::exec::RFunction(".rs.api.getSourceEditorContext").call(&context_sexp, &context_protect);
   
   // Try to get document context via module_context
   SEXP get_open_documents_sexp;
   r::sexp::Protect rp;
   error = r::exec::RFunction(".rs.get_open_source_documents").call(&get_open_documents_sexp, &rp);
   if (error)
   {
      LOG_ERROR(error);
      json::Object result;
      result["match"] = false;
      p_response->setResult(result);
      return Success();
   }
   
   if (TYPEOF(get_open_documents_sexp) != VECSXP)
   {
      json::Object result;
      result["match"] = false;
      p_response->setResult(result);
      return Success();
   }
   
   int num_docs = LENGTH(get_open_documents_sexp);
   
   // Iterate through open documents
   for (int i = 0; i < num_docs; i++)
   {
      SEXP doc_sexp = VECTOR_ELT(get_open_documents_sexp, i);
      if (TYPEOF(doc_sexp) != VECSXP)
      {
         continue;
      }
         
      // Extract document info
      std::string doc_id, file_path, contents;
      
      SEXP id_sexp = Rf_getAttrib(doc_sexp, Rf_install("id"));
      if (TYPEOF(id_sexp) == STRSXP && LENGTH(id_sexp) > 0)
         doc_id = CHAR(STRING_ELT(id_sexp, 0));
         
      SEXP path_sexp = Rf_getAttrib(doc_sexp, Rf_install("path"));
      if (TYPEOF(path_sexp) == STRSXP && LENGTH(path_sexp) > 0)
         file_path = CHAR(STRING_ELT(path_sexp, 0));
         
      SEXP contents_sexp = Rf_getAttrib(doc_sexp, Rf_install("contents"));
      if (TYPEOF(contents_sexp) == STRSXP && LENGTH(contents_sexp) > 0)
         contents = CHAR(STRING_ELT(contents_sexp, 0));
      
      if (file_path.empty() || contents.empty())
      {
         continue;
      }
      
      // Normalize contents line endings
      boost::algorithm::replace_all(contents, "\r\n", "\n");
      boost::algorithm::replace_all(contents, "\r", "\n");
      
      // Look for exact match
      size_t pos = contents.find(search_text);
      if (pos != std::string::npos)
      {
         // Found a match - now determine line numbers
         std::vector<std::string> lines;
         boost::algorithm::split(lines, contents, boost::is_any_of("\n"));
         
         // Find which line contains the start of the match
         int current_pos = 0;
         int start_line = 1;
         int end_line = 1;
         
         for (size_t line_num = 0; line_num < lines.size(); line_num++)
         {
            int line_length = lines[line_num].length() + 1; // +1 for newline
            
            if (current_pos <= (int)pos && (int)pos < current_pos + line_length)
            {
               start_line = line_num + 1; // Convert to 1-based
               break;
            }
            current_pos += line_length;
         }
         
         // Find end line
         size_t match_end = pos + search_text.length();
         current_pos = 0;
         
         for (size_t line_num = 0; line_num < lines.size(); line_num++)
         {
            int line_length = lines[line_num].length() + 1; // +1 for newline
            
            if (current_pos <= (int)match_end && (int)match_end <= current_pos + line_length)
            {
               end_line = line_num + 1; // Convert to 1-based
               break;
            }
            current_pos += line_length;
         }
         
         // Return the match result
         json::Object result;
         result["match"] = true;
         result["filePath"] = file_path;  // Changed from file_path to filePath
         result["startLine"] = start_line;  // Changed from start_line to startLine
         result["endLine"] = end_line;  // Changed from end_line to endLine
         result["docId"] = doc_id;  // Changed from doc_id to docId
         p_response->setResult(result);
         return Success();
      }
   }
   
   // No match found
   json::Object result;
   result["match"] = false;
   p_response->setResult(result);
   return Success();
}

// Get all currently open source editor documents with their full information
Error getAllOpenDocuments(const json::JsonRpcRequest& request,
                         json::JsonRpcResponse* p_response)
{
   // Get all open documents using the source database
   std::vector<boost::shared_ptr<source_database::SourceDocument>> docs;
   Error error = source_database::list(&docs);
   if (error)
   {
      LOG_ERROR(error);
      json::Array empty_array;
      p_response->setResult(empty_array);
      return Success();
   }

   // Build JSON array of document information
   json::Array document_array;
   
   for (size_t i = 0; i < docs.size(); i++)
   {
      boost::shared_ptr<source_database::SourceDocument> p_doc = docs[i];
      
      // Skip documents without content or path (but include untitled documents)
      if (p_doc->contents().empty() && p_doc->path().empty())
         continue;
         
      json::Object doc_info;
      doc_info["id"] = p_doc->id();
      doc_info["path"] = p_doc->path();
      doc_info["type"] = p_doc->type();
      doc_info["contents"] = p_doc->contents();
      doc_info["encoding"] = p_doc->encoding();
      doc_info["dirty"] = p_doc->dirty();
      doc_info["created"] = p_doc->created();
      doc_info["sourceOnSave"] = p_doc->sourceOnSave();
      doc_info["relativeOrder"] = p_doc->relativeOrder();
      doc_info["folds"] = p_doc->folds();
      doc_info["collabServer"] = p_doc->collabServer();
      doc_info["isUntitled"] = p_doc->isUntitled();
      doc_info["lastContentUpdate"] = static_cast<double>(p_doc->lastContentUpdate());
      doc_info["lastKnownWriteTime"] = static_cast<double>(p_doc->lastKnownWriteTime());
      
      // Add properties object
      doc_info["properties"] = p_doc->properties();
      
      document_array.push_back(doc_info);
   }
   
   p_response->setResult(document_array);
   return Success();
}

// Get open document content by path
Error getOpenDocumentContent(const json::JsonRpcRequest& request,
                            json::JsonRpcResponse* p_response)
{
   std::string file_path;
   Error error = json::readParam(request.params, 0, &file_path);
   if (error)
      return error;

   // Get all open documents using the source database
   std::vector<boost::shared_ptr<source_database::SourceDocument>> docs;
   error = source_database::list(&docs);
   if (error)
   {
      LOG_ERROR(error);
      p_response->setResult(json::Value());
      return Success();
   }

   // Normalize the input path for comparison
   FilePath inputPath = module_context::resolveAliasedPath(file_path);
   std::string normalized_input = inputPath.getAbsolutePath();

   // Search for document with matching path or tempName
   for (size_t i = 0; i < docs.size(); i++)
   {
      boost::shared_ptr<source_database::SourceDocument> p_doc = docs[i];
      
      bool matches = false;
      
      // First check if document has a saved path and it matches
      if (!p_doc->path().empty())
      {
         FilePath docPath = module_context::resolveAliasedPath(p_doc->path());
         std::string normalized_doc = docPath.getAbsolutePath();
         
         if (normalized_input == normalized_doc)
         {
            matches = true;
         }
      }
      // If no path match, check for tempName match (for unsaved documents)
      else
      {
         std::string tempName = p_doc->getProperty("tempName");
         if (!tempName.empty())
         {
            // For tempName matching, use prefix patterns from symbol index:
            // 1. "__UNSAVED__/" + tempName
            // 2. "__UNSAVED_" + id + "__/" + tempName
            
            std::string unsavedPathPattern1 = "__UNSAVED__/" + tempName;
            std::string unsavedPathPattern2;
            if (!p_doc->id().empty())
            {
               unsavedPathPattern2 = "__UNSAVED_" + p_doc->id().substr(0, 4) + "__/" + tempName;
            }
            
            // Check various matching patterns
            if (file_path == tempName ||                          // Direct tempName match
                file_path == unsavedPathPattern1 ||               // Symbol index pattern 1
                (!unsavedPathPattern2.empty() && file_path == unsavedPathPattern2)) // Symbol index pattern 2
            {
               matches = true;
            }
         }
      }
      
      if (matches)
      {
         json::Object result;
         result["found"] = true;
         result["content"] = p_doc->contents();
         result["dirty"] = p_doc->dirty();
         result["id"] = p_doc->id();
         p_response->setResult(result);
         return Success();
      }
   }

   // Document not found
   json::Object result;
   result["found"] = false;
   p_response->setResult(result);
   return Success();
}

// Check if file is open in editor
Error isFileOpenInEditor(const json::JsonRpcRequest& request,
                        json::JsonRpcResponse* p_response)
{
   std::string file_path;
   Error error = json::readParam(request.params, 0, &file_path);
   if (error)
      return error;

   // Get all open documents using the source database
   std::vector<boost::shared_ptr<source_database::SourceDocument>> docs;
   error = source_database::list(&docs);
   if (error)
   {
      LOG_ERROR(error);
      p_response->setResult(false);
      return Success();
   }

   // Normalize the input path for comparison
   FilePath inputPath = module_context::resolveAliasedPath(file_path);
   std::string normalized_input = inputPath.getAbsolutePath();

   // Search for document with matching path or tempName
   for (size_t i = 0; i < docs.size(); i++)
   {
      boost::shared_ptr<source_database::SourceDocument> p_doc = docs[i];
      
      bool matches = false;
      
      // First check if document has a saved path and it matches
      if (!p_doc->path().empty())
      {
         FilePath docPath = module_context::resolveAliasedPath(p_doc->path());
         std::string normalized_doc = docPath.getAbsolutePath();
         
         if (normalized_input == normalized_doc)
         {
            matches = true;
         }
      }
      // If no path match, check for tempName match (for unsaved documents)
      else
      {
         std::string tempName = p_doc->getProperty("tempName");
         if (!tempName.empty())
         {
            // For tempName matching, use prefix patterns from symbol index:
            // 1. "__UNSAVED__/" + tempName
            // 2. "__UNSAVED_" + id + "__/" + tempName
            
            std::string unsavedPathPattern1 = "__UNSAVED__/" + tempName;
            std::string unsavedPathPattern2;
            if (!p_doc->id().empty())
            {
               unsavedPathPattern2 = "__UNSAVED_" + p_doc->id().substr(0, 4) + "__/" + tempName;
            }
            
            // Check various matching patterns
            if (file_path == tempName ||                          // Direct tempName match
                file_path == unsavedPathPattern1 ||               // Symbol index pattern 1
                (!unsavedPathPattern2.empty() && file_path == unsavedPathPattern2)) // Symbol index pattern 2
            {
               matches = true;
            }
         }
      }
      
      if (matches)
      {
         p_response->setResult(true);
         return Success();
      }
   }

   // Document not found
   p_response->setResult(false);
   return Success();
}

// Update open document content
Error updateOpenDocumentContent(const json::JsonRpcRequest& request,
                               json::JsonRpcResponse* p_response)
{
   std::string file_path;
   std::string new_content;
   bool mark_clean = true; // Default to true for backwards compatibility
   Error error = json::readParams(request.params, &file_path, &new_content, &mark_clean);
   if (error)
   {
      // Try with just two parameters for backwards compatibility
      error = json::readParams(request.params, &file_path, &new_content);
      if (error)
         return error;
      mark_clean = true; // Default behavior
   }

   // Get all open documents using the source database
   std::vector<boost::shared_ptr<source_database::SourceDocument>> docs;
   error = source_database::list(&docs);
   if (error)
   {
      LOG_ERROR(error);
      p_response->setResult(false);
      return Success();
   }

   // Normalize the input path for comparison
   FilePath inputPath = module_context::resolveAliasedPath(file_path);
   std::string normalizedInputPath = inputPath.getAbsolutePath();

   // Find the matching document by path or tempName
   std::string targetDocId;
   for (const auto& doc : docs)
   {
      bool matches = false;
      
      // First check if document has a saved path and it matches
      if (!doc->path().empty())
      {
         FilePath docPath = module_context::resolveAliasedPath(doc->path());
         std::string normalizedDocPath = docPath.getAbsolutePath();
         
         if (normalizedDocPath == normalizedInputPath)
         {
            matches = true;
         }
      }
      // If no path match, check for tempName match (for unsaved documents)
      else
      {
         std::string tempName = doc->getProperty("tempName");
         if (!tempName.empty())
         {
            // For tempName matching, use prefix patterns from symbol index:
            // 1. "__UNSAVED__/" + tempName
            // 2. "__UNSAVED_" + id + "__/" + tempName
            
            std::string unsavedPathPattern1 = "__UNSAVED__/" + tempName;
            std::string unsavedPathPattern2;
            if (!doc->id().empty())
            {
               unsavedPathPattern2 = "__UNSAVED_" + doc->id().substr(0, 4) + "__/" + tempName;
            }
            
            // Check various matching patterns
            if (file_path == tempName ||                          // Direct tempName match
                file_path == unsavedPathPattern1 ||               // Symbol index pattern 1
                (!unsavedPathPattern2.empty() && file_path == unsavedPathPattern2)) // Symbol index pattern 2
            {
               matches = true;
            }
         }
      }
      
      if (matches)
      {
         targetDocId = doc->id();
         break;
      }
   }

   if (targetDocId.empty())
   {
      // Document not found in open documents
      p_response->setResult(false);
      return Success();
   }

   // Re-fetch the document from the source database using the current ID
   // This ensures we have the most up-to-date document object
   boost::shared_ptr<source_database::SourceDocument> targetDoc(new source_database::SourceDocument());
   error = source_database::get(targetDocId, targetDoc);
            if (error)
            {
               LOG_ERROR(error);
               p_response->setResult(false);
               return Success();
            }
            
   // Verify this is still the same file (double-check path or tempName matches)
   bool verificationMatches = false;
   
   if (!targetDoc->path().empty())
   {
      FilePath docPath = module_context::resolveAliasedPath(targetDoc->path());
      std::string normalizedDocPath = docPath.getAbsolutePath();
      
      if (normalizedDocPath == normalizedInputPath)
      {
         verificationMatches = true;
      }
   }
   else
   {
      // For unsaved documents, verify tempName matches
      std::string tempName = targetDoc->getProperty("tempName");
      if (!tempName.empty())
      {
         // Use the same prefix matching logic as above
         std::string unsavedPathPattern1 = "__UNSAVED__/" + tempName;
         std::string unsavedPathPattern2;
         if (!targetDoc->id().empty())
         {
            unsavedPathPattern2 = "__UNSAVED_" + targetDoc->id().substr(0, 4) + "__/" + tempName;
         }
         
         if (file_path == tempName ||
             file_path == unsavedPathPattern1 ||
             (!unsavedPathPattern2.empty() && file_path == unsavedPathPattern2))
         {
            verificationMatches = true;
         }
      }
   }
   
   if (!verificationMatches)
   {
      // No match - document might have changed
      p_response->setResult(false);
      return Success();
   }

   // Update the document content in the source database
   targetDoc->setContents(new_content);
   targetDoc->setDirty(!mark_clean); // Mark as clean/dirty based on parameter
   
   error = source_database::put(targetDoc);
   if (error)
   {
      std::cerr << "DEBUG updateOpenDocumentContent: source_database::put failed - " + error.getSummary() << std::endl;
      p_response->setResult(false);
      return Success();
   }

   // Update the lastKnownWriteTime to match the new file timestamp to prevent external edit dialog
   targetDoc->updateLastKnownWriteTime();
   
   // Fire the document updated signal for other listeners
   source_database::events().onDocUpdated(targetDoc);
   
   // Send client event to directly update the ACE editor content
   json::Object eventData;
   eventData["document_id"] = targetDoc->id();
   eventData["file_path"] = file_path;
   eventData["content"] = new_content;
   eventData["mark_clean"] = mark_clean;
   
   ClientEvent refreshEvent(client_events::kRefreshDocumentContent, eventData);
   module_context::enqueClientEvent(refreshEvent);

   p_response->setResult(true);
   return Success();
}

// Function to accept a pending terminal command
Error acceptTerminalCommand(const json::JsonRpcRequest& request,
                           json::JsonRpcResponse* p_response,
                           const std::string& message_id,
                           const std::string& script,
                           const std::string& request_id)
{

   // Call the R function to handle accepting the terminal command
   SEXP result;
   r::sexp::Protect protect;
   Error error = r::exec::RFunction(".rs.accept_terminal_command")
      .addParam(message_id)
      .addParam(script)
      .addParam(request_id)
      .call(&result, &protect);
   
   if (error)
   {
      std::cerr << "DEBUG: [C++] acceptTerminalCommand R function call failed: " << error.getSummary() << std::endl;
      return error;
   }
   
   // The R function handles the execution and completion
   // Just return success here
   return Success();
}

// Function to cancel a pending terminal command  
Error cancelTerminalCommand(const json::JsonRpcRequest& request,
                           json::JsonRpcResponse* p_response,
                           const std::string& message_id,
                           const std::string& request_id)
{

   // Call the R function to handle cancelling the terminal command
   SEXP result;
   r::sexp::Protect protect;
   Error error = r::exec::RFunction(".rs.cancel_terminal_command")
      .addParam(message_id)
      .addParam(request_id)
      .call(&result, &protect);
   
   if (error)
      return error;
      
   // The R function handles the completion
   // Just return success here
   return Success();
}

// Function to accept a pending console command
Error acceptConsoleCommand(const json::JsonRpcRequest& request,
                          json::JsonRpcResponse* p_response,
                          const std::string& message_id,
                          const std::string& script,
                          const std::string& request_id)
{
   // Call the R function to handle accepting the console command
   SEXP result;
   r::sexp::Protect protect;
   Error error = r::exec::RFunction(".rs.accept_console_command")
      .addParam(message_id)
      .addParam(script)
      .addParam(request_id)
      .call(&result, &protect);
   
   if (error)
      return error;
      
   // The R function handles the execution and completion
   // Just return success here  
   return Success();
}

// Function to cancel a pending console command
Error cancelConsoleCommand(const json::JsonRpcRequest& request,
                          json::JsonRpcResponse* p_response,
                          const std::string& message_id,
                          const std::string& request_id)
{
   // Call the R function to handle cancelling the console command
   SEXP result;
   r::sexp::Protect protect;
   Error error = r::exec::RFunction(".rs.cancel_console_command")
      .addParam(message_id)
      .addParam(request_id)
      .call(&result, &protect);
   
   if (error)
      return error;
      
   // The R function handles the completion
   // Just return success here
   return Success();
}

// Function to cancel a pending edit file command
Error cancelEditFileCommand(const json::JsonRpcRequest& request,
                           json::JsonRpcResponse* p_response,
                           const std::string& message_id,
                           const std::string& request_id)
{

   // Call the R function and capture result
   SEXP result_sexp;
   r::sexp::Protect rp;
   Error error = r::exec::RFunction(".rs.cancel_edit_file_command")
      .addParam(message_id)
      .addParam(request_id)
      .call(&result_sexp, &rp);
   
   if (error) {
      LOG_ERROR(error);
      return error;
   }
   
   // Check if R function returned a result object with status information
   if (result_sexp != R_NilValue) {
      json::Value result_json;
      Error json_error = r::json::jsonValueFromList(result_sexp, &result_json);
      if (!json_error) {
         p_response->setResult(result_json);
      }
   }
   
   return Success();
}

// New function to orchestrate AI operation flow
// This follows the established pattern from other functions in this file
Error processAiOperation(const json::JsonRpcRequest& request,
                        json::JsonRpcResponse* p_response)
{
   // The request params is an array with a single object containing all parameters
   json::Object params;
   Error error = json::readParam(request.params, 0, &params);
   if (error)
      return error;
   
   // Extract operation_type from the params object
   std::string operation_type;
   error = json::readObject(params, "operation_type", operation_type);
   if (error)
      return error;
   
   if (operation_type == "initialize_conversation")
   {
      // Initialize a conversation and return conversation data 
      SEXP result_sexp;
      r::sexp::Protect rp;
      
      r::exec::RFunction init_call(".rs.initialize_conversation");
      
      // Check for query parameter (required for this operation)
      if (params.find("query") != params.end())
      {
         std::string query;
         error = json::readObject(params, "query", query);
         if (error)
         {
            std::cerr << "ERROR: Failed to read query parameter" << std::endl;
            std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
            std::cerr << "ERROR: Raw query value: " << params["query"].writeFormatted() << std::endl;
            std::cerr << "ERROR: query type: " << params["query"].getType() << std::endl;
            return error;
         }
         init_call.addParam(query);
      }
      else
      {
         std::cerr << "ERROR: Missing required query parameter" << std::endl;
         std::cerr << "ERROR: Available params: " << params.writeFormatted() << std::endl;
         return Error(json::errc::ParamMissing, ERROR_LOCATION);
      }
      
      // Add optional parameters using R_NilValue for missing ones
      if (params.find("request_id") != params.end())
      {
         std::string request_id;
         error = json::readObject(params, "request_id", request_id);
         if (!error) {
            init_call.addParam(request_id);
         }
      }
      
      // Call R function
      error = init_call.call(&result_sexp, &rp);
      if (error)
      {
         std::cerr << "ERROR: R function call failed" << std::endl;
         std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
         return error;
      }
      
      // Convert R result to JSON (following established pattern)
      json::Value result;
      error = r::json::jsonValueFromObject(result_sexp, &result);
      if (error)
      {
         std::cerr << "ERROR: Failed to convert R result to JSON in initialize_conversation" << std::endl;
         std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
         std::cerr << "ERROR: SEXP type info: " << TYPEOF(result_sexp) << std::endl;
         return error;
      }
      
      p_response->setResult(result);
      return Success();
   }
   else if (operation_type == "make_api_call")
   {
      // Make an API call using consolidated ai_operation system
      SEXP result_sexp;
      r::sexp::Protect rp;
      
      r::exec::RFunction api_call(".rs.ai_operation");
      
      // First parameter: operation_type
      api_call.addParam("make_api_call");
      
      // Add named parameters for ai_operation function
      // ai_operation(operation_type, query=NULL, conversation_index=NULL, request_id=NULL, 
      //              function_call=NULL, api_response=NULL, related_to_id=NULL,
      //              model=NULL, preserve_symbols=TRUE, is_continue=FALSE)
      
      // Parameter 2: query (NULL for api_call operation)
      api_call.addParam(R_NilValue);
      
      // Parameter 3: request_id
      if (params.find("request_id") != params.end())
      {
         std::string request_id;
         error = json::readObject(params, "request_id", request_id);
         if (error)
         {
            std::cerr << "ERROR: Failed to read request_id parameter" << std::endl;
            std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
            std::cerr << "ERROR: Raw request_id value: " << params["request_id"].writeFormatted() << std::endl;
            std::cerr << "ERROR: request_id type: " << params["request_id"].getType() << std::endl;
            return error;
         }
         api_call.addParam(request_id);
      }
      else
      {
         api_call.addParam(R_NilValue);
      }
      
      // Parameter 4: function_call (NULL for api_call operation)
      api_call.addParam(R_NilValue);
      
      // Parameter 5: api_response (NULL for api_call operation)
      api_call.addParam(R_NilValue);
      
      // Parameter 6: related_to_id (REQUIRED)
      if (params.find("related_to_id") != params.end())
      {
         int related_to_id;
         error = json::readObject(params, "related_to_id", related_to_id);
         if (error)
         {
            std::cerr << "ERROR: Failed to read related_to_id parameter as integer" << std::endl;
            std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
            std::cerr << "ERROR: Raw related_to_id value: " << params["related_to_id"].writeFormatted() << std::endl;
            std::cerr << "ERROR: related_to_id type: " << params["related_to_id"].getType() << std::endl;
            return error;
         }
         api_call.addParam(related_to_id);
      }
      else
      {
         std::cerr << "ERROR: Missing required related_to_id parameter for make_api_call" << std::endl;
         std::cerr << "ERROR: Available params: " << params.writeFormatted() << std::endl;
         return Error(json::errc::ParamMissing, ERROR_LOCATION);
      }
      
      // Parameter 7: model
      if (params.find("model") != params.end())
      {
         std::string model;
         error = json::readObject(params, "model", model);
         if (error)
         {
            std::cerr << "ERROR: Failed to read model parameter" << std::endl;
            std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
            std::cerr << "ERROR: Raw model value: " << params["model"].writeFormatted() << std::endl;
            std::cerr << "ERROR: model type: " << params["model"].getType() << std::endl;
            return error;
         }
         api_call.addParam(model);
      }
      else
      {
         api_call.addParam(R_NilValue);
      }
      
      // Parameter 8: preserve_symbols
      if (params.find("preserve_symbols") != params.end())
      {
         bool preserve_symbols;
         error = json::readObject(params, "preserve_symbols", preserve_symbols);
         if (error)
         {
            std::cerr << "ERROR: Failed to read preserve_symbols parameter" << std::endl;
            std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
            std::cerr << "ERROR: Raw preserve_symbols value: " << params["preserve_symbols"].writeFormatted() << std::endl;
            std::cerr << "ERROR: preserve_symbols type: " << params["preserve_symbols"].getType() << std::endl;
            return error;
         }
         api_call.addParam(preserve_symbols);
      }
      else
      {
         api_call.addParam(true);
      }
      
      // Parameter 9: is_continue (FALSE for api_call operation)
      api_call.addParam(false);
      
      // Call R function
      error = api_call.call(&result_sexp, &rp);
      if (error)
      {
         std::cerr << "ERROR: R function call failed" << std::endl;
         std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
         return error;
      }
      
      // Convert R result to JSON (following established pattern)
      json::Value result;
      
      error = r::json::jsonValueFromObject(result_sexp, &result);
      if (error)
      {
         std::cerr << "ERROR: Failed to convert R result to JSON in make_api_call" << std::endl;
         std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
         std::cerr << "ERROR: SEXP type info: " << TYPEOF(result_sexp) << std::endl;
         return error;
      }
      
      p_response->setResult(result);
      return Success();
   }
   else if (operation_type == "process_function_call")
   {
      // Process a single function call using consolidated ai_operation system
      SEXP result_sexp;
      r::sexp::Protect rp;
      
      r::exec::RFunction process_call(".rs.ai_operation");
      
      // First parameter: operation_type
      process_call.addParam("function_call");
      
      // Add parameters for ai_operation function
      // ai_operation(operation_type, query=NULL, conversation_index=NULL, request_id=NULL, 
      //              function_call=NULL, api_response=NULL, related_to_id=NULL,
      //              model=NULL, preserve_symbols=TRUE, is_continue=FALSE)
      
      // Parameter 2: query (NULL for function_call operation)
      process_call.addParam(R_NilValue);
      
      // Parameter 3: request_id
      if (params.find("request_id") != params.end())
      {
         std::string request_id;
         error = json::readObject(params, "request_id", request_id);
         if (error)
         {
            std::cerr << "ERROR: Failed to read request_id parameter" << std::endl;
            std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
            std::cerr << "ERROR: Raw request_id value: " << params["request_id"].writeFormatted() << std::endl;
            std::cerr << "ERROR: request_id type: " << params["request_id"].getType() << std::endl;
            return error;
         }
         process_call.addParam(request_id);
      }
      else
      {
         process_call.addParam(R_NilValue);
      }
      
      // Parameter 4: function_call (REQUIRED for this operation)
      if (params.find("function_call") != params.end())
      {         
         // function_call is already a JSON object, access it directly (following api_response pattern)
         json::Value function_call = params["function_call"];
         
         SEXP function_call_sexp = r::sexp::create(function_call, &rp);
         process_call.addParam(function_call_sexp);
      }
      else
      {
         std::cerr << "ERROR: Missing required function_call parameter" << std::endl;
         std::cerr << "ERROR: Available params: " << params.writeFormatted() << std::endl;
         return Error(json::errc::ParamMissing, ERROR_LOCATION);
      }
      
      // Parameter 5: api_response (NULL for function_call operation)
      process_call.addParam(R_NilValue);
      
      // Parameter 6: related_to_id (REQUIRED)
      if (params.find("related_to_id") != params.end())
      {
         int related_to_id;
         error = json::readObject(params, "related_to_id", related_to_id);
         if (error)
         {
            std::cerr << "ERROR: Failed to read related_to_id parameter as integer" << std::endl;
            std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
            std::cerr << "ERROR: Raw related_to_id value: " << params["related_to_id"].writeFormatted() << std::endl;
            std::cerr << "ERROR: related_to_id type: " << params["related_to_id"].getType() << std::endl;
            return error;
         }
         process_call.addParam(related_to_id);
      }
      else
      {
         std::cerr << "ERROR: Missing required related_to_id parameter for process_function_call" << std::endl;
         std::cerr << "ERROR: Available params: " << params.writeFormatted() << std::endl;
         return Error(json::errc::ParamMissing, ERROR_LOCATION);
      }
      
      // Parameters 7-9: model, preserve_symbols, is_continue (use defaults)
      process_call.addParam(R_NilValue);  // model
      process_call.addParam(true);        // preserve_symbols  
      process_call.addParam(false);       // is_continue
      
      // Call R function
      error = process_call.call(&result_sexp, &rp);
      if (error)
      {
         std::cerr << "ERROR: R function call failed" << std::endl;
         std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
         return error;
      }
      
      // Convert R result to JSON (following established pattern)
      json::Value result;
      error = r::json::jsonValueFromObject(result_sexp, &result);  
      if (error)
      {
         std::cerr << "ERROR: Failed to convert R result to JSON in process_function_call" << std::endl;
         std::cerr << "ERROR: Full error details: " << error.getSummary() << std::endl;
         std::cerr << "ERROR: SEXP type info: " << TYPEOF(result_sexp) << std::endl;
         return error;
      }
      
      p_response->setResult(result);
      return Success();
   }

   
   return core::Error(json::errc::ParamInvalid, ERROR_LOCATION);
}

void initEnvironment()
{
   // environment variable to initialize
   const char * const kRStudioRipgrep = "RSTUDIO_RIPGREP";
   
   // set RSTUDIO_RIPGREP (leave existing value alone)
   std::string rstudioRipgrep = core::system::getenv(kRStudioRipgrep);
   if (rstudioRipgrep.empty())
      rstudioRipgrep = session::options().ripgrepPath().getAbsolutePath();
   
   r::exec::RFunction sysSetenv("Sys.setenv");
   sysSetenv.addParam(kRStudioRipgrep, rstudioRipgrep);

   // call Sys.setenv
   Error error = sysSetenv.call();
   if (error)
      LOG_ERROR(error);
}

Error initialize()
{
   using boost::bind;
   using core::http::UriHandler;
   using namespace module_context;
   using namespace rstudio::r::function_hook;
   
   ExecBlock initBlock;
   initBlock.addFunctions()
      (bind(module_context::registerRpcMethod, "clear_console_done_flag", clearConsoleDoneFlag))
      (bind(module_context::registerRpcMethod, "finalize_console_command", finalizeConsoleCommand))
      (bind(module_context::registerRpcMethod, "finalize_terminal_command", finalizeTerminalCommand))
      (bind(module_context::registerRpcMethod, "check_terminal_complete", checkTerminalComplete))
      (bind(module_context::registerRpcMethod, "clear_terminal_done_flag", clearTerminalDoneFlag))
      (bind(module_context::registerRpcMethod, "get_terminal_websocket_port", getTerminalWebsocketPort))
      (bind(module_context::registerRpcMethod, "get_websocket_channel_id", get_websocket_channel_id))
      (bind(module_context::registerRpcMethod, "get_tab_file_path", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string tab_id;
                  Error error = json::readParam(request.params, 0, &tab_id);
                  if (error)
                     return error;
                  return getTabFilePath(request, p_response, tab_id);
               })))
      (bind(module_context::registerRpcMethod, "set_ai_working_directory", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string dir;
                  Error error = json::readParam(request.params, 0, &dir);
                  if (error)
                     return error;
                  return setAiWorkingDirectory(request, p_response, dir);
               })))
      (bind(module_context::registerRpcMethod, "browse_directory", browseDirectory))
      (bind(module_context::registerRpcMethod, "browse_for_file", browseForFile))
      (bind(module_context::registerRpcMethod, "add_context_item", addContextItem))
      (bind(module_context::registerRpcMethod, "add_context_lines", addContextLines))
      (bind(module_context::registerRpcMethod, "get_context_items", getContextItems))
      (bind(module_context::registerRpcMethod, "get_current_conversation_index", getCurrentConversationIndex))
      (bind(module_context::registerRpcMethod, "get_open_document_content", getOpenDocumentContent))
      (bind(module_context::registerRpcMethod, "is_file_open_in_editor", isFileOpenInEditor))
      (bind(module_context::registerRpcMethod, "update_open_document_content", updateOpenDocumentContent))
      (bind(module_context::registerRpcMethod, "get_all_open_documents", getAllOpenDocuments))
      (bind(module_context::registerRpcMethod, "get_diff_data_for_edit_file", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string message_id;
                  Error error = json::readParam(request.params, 0, &message_id);
                  if (error)
                     return error;
                  return getDiffDataForEditFile(request, p_response, message_id);
               })))
      (bind(module_context::registerRpcMethod, "remove_context_item", removeContextItem))
      (bind(module_context::registerRpcMethod, "clear_context_items", clearContextItems))
      (bind(module_context::registerRpcMethod, "add_terminal_output_to_conversation", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  int message_id;
                  Error error = json::readParam(request.params, 0, &message_id);
                  if (error)
                     return error;
                  return addTerminalOutputToAiConversation(request, p_response, message_id);
               })))
      (bind(module_context::registerRpcMethod, "add_console_output_to_conversation", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  int message_id;
                  Error error = json::readParam(request.params, 0, &message_id);
                  if (error)
                     return error;
                  return addConsoleOutputToAiConversation(request, p_response, message_id);
               })))
      (bind(module_context::registerRpcMethod, "create_new_conversation", createNewConversation))
      (bind(module_context::registerRpcMethod, "list_attachments", listAttachments))
      (bind(module_context::registerRpcMethod, "delete_attachment", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string file_path;
                  Error error = json::readParam(request.params, 0, &file_path);
                  if (error)
                     return error;
                  return deleteAttachment(request, p_response, file_path);
               })))
      (bind(module_context::registerRpcMethod, "delete_all_attachments", deleteAllAttachments))
      (bind(module_context::registerRpcMethod, "cleanup_conversation_attachments", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  int conversation_id;
                  Error error = json::readParam(request.params, 0, &conversation_id);
                  if (error)
                     return error;
                  return cleanupConversationAttachments(request, p_response, conversation_id);
               })))
      (bind(module_context::registerRpcMethod, "save_ai_attachment", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string file_path;
                  Error error = json::readParam(request.params, 0, &file_path);
                  if (error)
                     return error;
                  return saveAiAttachment(request, p_response, file_path);
               })))
      (bind(module_context::registerRpcMethod, "save_ai_image", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string image_path;
                  Error error = json::readParam(request.params, 0, &image_path);
                  if (error)
                     return error;
                  return saveAiImage(request, p_response, image_path);
               })))
      (bind(module_context::registerRpcMethod, "create_temp_image_file", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string data_url, file_name;
                  Error error = json::readParams(request.params, &data_url, &file_name);
                  if (error)
                     return error;
                  return createTempImageFile(request, p_response, data_url, file_name);
               })))
      (bind(module_context::registerRpcMethod, "list_images", listImages))
      (bind(module_context::registerRpcMethod, "delete_image", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string image_path;
                  Error error = json::readParam(request.params, 0, &image_path);
                  if (error)
                     return error;
                  return deleteImage(request, p_response, image_path);
               })))
      (bind(module_context::registerRpcMethod, "delete_all_images", deleteAllImages))
      (bind(module_context::registerRpcMethod, "check_image_content_duplicate", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string image_path;
                  Error error = json::readParam(request.params, 0, &image_path);
                  if (error)
                     return error;
                  return checkImageContentDuplicate(request, p_response, image_path);
               })))
      (bind(module_context::registerRpcMethod, "delete_folder", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string path;
                  Error error = json::readParam(request.params, 0, &path);
                  if (error)
                     return error;
                  return deleteFolder(request, p_response, path);
               })))
      (bind(module_context::registerRpcMethod, "revert_ai_message", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  int message_id;
                  Error error = json::readParam(request.params, 0, &message_id);
                  if (error)
                     return error;
                  return revertAiMessage(request, p_response, message_id);
               })))
      (bind(module_context::registerRpcMethod, "accept_edit_file_command", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string edited_code;
                  std::string message_id;
                  std::string request_id;
                  Error error = json::readParams(request.params, &edited_code, &message_id, &request_id);
                  if (error)
                     return error;
                  return aiAcceptEditFileCommand(request, p_response, edited_code, message_id, request_id);
               })))
      (bind(module_context::registerRpcMethod, "save_api_key", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string provider, key;
                  Error error = json::readParams(request.params, &provider, &key);
                  if (error)
                     return error;
                  return saveApiKey(request, p_response, provider, key);
               })))
      (bind(module_context::registerRpcMethod, "delete_api_key", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string provider;
                  Error error = json::readParam(request.params, 0, &provider);
                  if (error)
                     return error;
                  return deleteApiKey(request, p_response, provider);
               })))
      (bind(module_context::registerRpcMethod, "set_active_provider",
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string provider;
                  Error error = json::readParam(request.params, 0, &provider);
                  if (error)
                     return error;
                  return setActiveProvider(request, p_response, provider);
               })))
      (bind(module_context::registerRpcMethod, "set_model",
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string provider, model;
                  Error error = json::readParams(request.params, &provider, &model);
                  if (error)
                     return error;
                  return setModel(request, p_response, provider, model);
               })))
      (bind(module_context::registerRpcMethod, "get_conversation_name", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  int conversation_id;
                  Error error = json::readParam(request.params, 0, &conversation_id);
                  if (error)
                     return error;
                  return getConversationName(request, p_response, conversation_id);
               })))
      (bind(module_context::registerRpcMethod, "set_conversation_name", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  int conversation_id;
                  std::string name;
                  Error error = json::readParams(request.params, &conversation_id, &name);
                  if (error)
                     return error;
                  return setConversationName(request, p_response, conversation_id, name);
               })))
      (bind(module_context::registerRpcMethod, "delete_conversation_name", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  int conversation_id;
                  Error error = json::readParam(request.params, 0, &conversation_id);
                  if (error)
                     return error;
                  return deleteConversationName(request, p_response, conversation_id);
               })))
      (bind(module_context::registerRpcMethod, "list_conversation_names", listConversationNames))
      (bind(module_context::registerRpcMethod, "should_prompt_for_name", shouldPromptForName))
      (bind(module_context::registerRpcMethod, "generate_conversation_name", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  int conversation_id;
                  Error error = json::readParam(request.params, 0, &conversation_id);
                  if (error)
                     return error;
                  return generateConversationName(request, p_response, conversation_id);
               })))
      (bind(module_context::registerRpcMethod, "get_conversation_log", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  int conversation_id;
                  Error error = json::readParam(request.params, 0, &conversation_id);
                  if (error)
                     return error;
                  return getConversationLog(request, p_response, conversation_id);
               })))
      (bind(module_context::registerRpcMethod, "mark_button_as_run", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string message_id, button_type;
                  Error error = json::readParams(request.params, &message_id, &button_type);
                  if (error)
                     return error;
                  return markButtonAsRun(request, p_response, message_id, button_type);
               })))
      (bind(module_context::registerRpcMethod, "get_file_name_for_message_id", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string message_id;
                  Error error = json::readParam(request.params, 0, &message_id);
                  if (error)
                     return error;
                  return getFileNameForMessageId(request, p_response, message_id);
               })))


      (bind(module_context::registerRpcMethod, "run_accepted_code", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string filename;
                  int message_id;
                  Error error = json::readParams(request.params, &filename, &message_id);
                  if (error)
                     return error;
                  return runAcceptedCode(request, p_response, filename, message_id);
               })))

      (bind(module_context::registerRpcMethod, "is_conversation_empty", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  int conversation_id;
                  Error error = json::readParam(request.params, 0, &conversation_id);
                  if (error)
                     return error;
                  return isConversationEmpty(request, p_response, conversation_id);
               })))
      (bind(module_context::registerRpcMethod, "accept_terminal_command", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string message_id, script, request_id;
                  Error error = json::readParams(request.params, &message_id, &script, &request_id);
                  if (error)
                     return error;
                  return acceptTerminalCommand(request, p_response, message_id, script, request_id);
               })))
      (bind(module_context::registerRpcMethod, "cancel_terminal_command", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string message_id, request_id;
                  Error error = json::readParams(request.params, &message_id, &request_id);
                  if (error)
                     return error;
                  return cancelTerminalCommand(request, p_response, message_id, request_id);
               })))
      (bind(module_context::registerRpcMethod, "accept_console_command", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string message_id, script, request_id;
                  Error error = json::readParams(request.params, &message_id, &script, &request_id);
                  if (error)
                     return error;
                  return acceptConsoleCommand(request, p_response, message_id, script, request_id);
               })))
      (bind(module_context::registerRpcMethod, "cancel_console_command", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string message_id, request_id;
                  Error error = json::readParams(request.params, &message_id, &request_id);
                  if (error)
                     return error;
                  return cancelConsoleCommand(request, p_response, message_id, request_id);
               })))
      (bind(module_context::registerRpcMethod, "cancel_edit_file_command", 
            boost::function<core::Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
               [](const json::JsonRpcRequest& request, json::JsonRpcResponse* p_response) {
                  std::string message_id;
                  std::string request_id;
                  Error error = json::readParams(request.params, &message_id, &request_id);
                  if (error)
                     return error;
                  return cancelEditFileCommand(request, p_response, message_id, request_id);
               })))
      (bind(module_context::registerRpcMethod, "match_text_in_open_documents", matchTextInOpenDocuments))
      (bind(module_context::registerRpcMethod, "process_ai_operation", processAiOperation))
      // REMOVED: save_streaming_response registration - no longer needed since backend handles saving directly
      (bind(registerUriHandler, kAiLocation, handleAiRequest));
   
   Error error = initBlock.execute();
   if (error)
      return error;

   // Source R files in specific order to ensure proper dependency loading
   ExecBlock sourceBlock;
   sourceBlock.addFunctions()
      (bind(sourceModuleRFile, "SessionAiHelpers.R"))    // first helper functions
      (bind(sourceModuleRFile, "SessionAiAPI.R"))        // then API functions 
      (bind(sourceModuleRFile, "SessionAiKeyManagement.R")) // then key management
      (bind(sourceModuleRFile, "SessionAiButtons.R"))
      (bind(sourceModuleRFile, "SessionAiConversationDisplay.R"))
      (bind(sourceModuleRFile, "SessionAiIO.R"))
      (bind(sourceModuleRFile, "SessionAiVariableManager.R")) // then variable management
      (bind(sourceModuleRFile, "SessionAiOperations.R"))    // then operations that use helpers
      (bind(sourceModuleRFile, "SessionAiConversationHandlers.R"))
      (bind(sourceModuleRFile, "SessionAiSearch.R"))
      (bind(sourceModuleRFile, "SessionAiAttachments.R"))
      (bind(sourceModuleRFile, "SessionAiImages.R"))
      (bind(sourceModuleRFile, "SessionAiContext.R"))
      (bind(sourceModuleRFile, "SessionAiBackendComms.R"));    // then attachment functions
   
   error = sourceBlock.execute();
   if (error)
      return error;

   // Initialize environment variables
   initEnvironment();

   return Success();
}

} // namespace ai
} // namespace modules
} // namespace session
} // namespace rstudio
