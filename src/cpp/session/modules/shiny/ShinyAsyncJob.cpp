/*
 * ShinyAsyncJob.cpp
 *
 * Copyright (C) 2022 by Posit Software, PBC
 *
 * Unless you have received this program directly from Posit Software pursuant
 * to the terms of a commercial license agreement with Posit Software, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

#define kShinyAppStarted "Shiny started; listening on URL: " 

#include "ShinyAsyncJob.hpp"
#include "../SessionShinyViewer.hpp"

#include <session/jobs/JobsApi.hpp>

#include <session/SessionUrlPorts.hpp>
#include <session/SessionModuleContext.hpp>

#include <shared_core/SafeConvert.hpp>

using namespace rstudio::core;

namespace rstudio {
namespace session {
namespace modules { 
namespace shiny {

ShinyAsyncJob::ShinyAsyncJob(const std::string& name, 
      const FilePath& path,
      const std::string& viewerType,
      const std::string& runCmd):
   AsyncRJob(name),
   path_(path),
   viewerType_(viewerType),
   runCmd_(runCmd)
{
}

void ShinyAsyncJob::start()
{
   // set a status before we start R
   jobs::setJobStatus(job_, "Starting");

   // create the string to send to R
   std::string cmd(
         "options(shiny.launch.browser = function(url) { "
         "   cat(\"" kShinyAppStarted "\", url)"
         "}); " +
         runCmd_);

   // start the R process
   core::system::Options environment;
   environment.push_back(std::make_pair("RSTUDIO_CHILD_PROCESS_PANE", "job"));
   
   async_r::AsyncRProcess::start(cmd.c_str(), environment, path_.getParent(), 
         async_r::AsyncRProcessOptions::R_PROCESS_NO_RDATA);

   // echo the command we submitted to R
   onStdout("=> " + runCmd_ + "\n\n");
}

core::Error ShinyAsyncJob::replay()
{
   Error error;
   if (!job_)
   {
      error = systemError(boost::system::errc::no_child_process, ERROR_LOCATION);
      error.addProperty("name", name_);
      error.addProperty("description", "Shiny background job is not running yet and cannot "
            "be replayed.");
      return error;
   }
   if (!job_->complete())
   {
      error = systemError(boost::system::errc::operation_in_progress, ERROR_LOCATION);
      error.addProperty("id", job_->id());
      error.addProperty("name", name_);
      error.addProperty("description", "You must stop the Shiny background job before attempting "
            "to replay it.");
      return error;
   }

   // reset the underlying job
   error = reset();
   if (error)
   {
      return error;
   }

   // return job to idle state
   jobs::setJobStatus(job_, "Restarting");
   jobs::setJobState(job_, jobs::JobState::JobIdle);

   // job is now reset, run the Shiny app again
   start();

   return Success();
}

void ShinyAsyncJob::enqueueStateEvent(const std::string& state)
{
   json::Object dataJson;
   dataJson["url"] = url_ports::mapUrlPorts(url_);
   dataJson["path"] = module_context::createAliasedPath(path_);
   dataJson["state"] = state;
   dataJson["viewer"] = viewerType_;
   dataJson["options"] = shiny_viewer::SHINY_VIEWER_OPTIONS_NONE;
   dataJson["id"] = job_->id();
   ClientEvent event(client_events::kShinyViewer, dataJson);
   module_context::enqueClientEvent(event);
}

void ShinyAsyncJob::onStdout(const std::string& output)
{
   size_t pos = output.find(kShinyAppStarted);
   if (pos != std::string::npos)
   {
      // extract the URL, which is the portion of the output string following the start token
      url_ = output.substr(pos + std::string(kShinyAppStarted).size() + 1);

      // create an event to let the client know to start viewing the running application
      enqueueStateEvent("started");

      // set the job state so the Jobs tab will show the app 
      jobs::setJobStatus(job_, "Running");
      setJobState(job_, jobs::JobState::JobRunning);

      // no need to echo this to the user
      return;
   }

   // forward output to base class so it can be emitted to the client
   AsyncRJob::onStdout(output);
}

void ShinyAsyncJob::onCompleted(int exitStatus)
{
   if (exitStatus == 0)
   {
      setJobState(job_, jobs::JobState::JobSucceeded);
      onStdout("\nShiny application finished running.\n\n");
   }
   else
   {
      if (cancelled_)
      {
         // typically the only way Shiny applications exit is by being stopped, so don't treat that
         // as a failure
         setJobState(job_, jobs::JobState::JobSucceeded);
         onStdout("\nShiny application successfully stopped.\n\n");
      }
      else
      {
         setJobState(job_, jobs::JobState::JobFailed);
         onStdout("\nShiny application failed (exit status " +
               safe_convert::numberToString(exitStatus) + ").\n\n");
      }
   }

   AsyncRJob::onCompleted(exitStatus);
}
                      
} // namespace shiny
} // namespace modules
} // namespace session
} // namespace rstudio


