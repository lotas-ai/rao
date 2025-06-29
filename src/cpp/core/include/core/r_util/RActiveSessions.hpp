/*
 * RActiveSessions.hpp
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


#ifndef CORE_R_UTIL_ACTIVE_SESSIONS_HPP
#define CORE_R_UTIL_ACTIVE_SESSIONS_HPP

#include <boost/noncopyable.hpp>
#include <boost/thread/thread.hpp>

#include <core/Log.hpp>
#include <core/Settings.hpp>
#include <core/DateTime.hpp>

#include <core/r_util/RSessionContext.hpp>
#include <core/r_util/RProjectFile.hpp>
#include <core/r_util/RActiveSessionStorage.hpp>
#include <core/r_util/RActiveSessionsStorage.hpp>

#include <shared_core/Error.hpp>
#include <shared_core/FilePath.hpp>
#include <shared_core/SafeConvert.hpp>
#include <shared_core/json/Json.hpp>

namespace rstudio {
namespace core {
namespace r_util {

class RpcActiveSessionsStorage;

// Constants for RPCs related to session metadata ===========================
// RPC endpoint
constexpr const char * kSessionStorageRpc = "/storage/session_metadata";

// Methods for the RPC
constexpr const char * kSessionStorageReadOp = "read";
constexpr const char * kSessionStorageReadAllOp = "read_all";
constexpr const char * kSessionStorageWriteOp = "write";
constexpr const char * kSessionStorageCountOp = "count";
constexpr const char * kSessionStorageDeleteOp = "delete";
constexpr const char * kSessionStorageValidateOp = "validate";

// Fiels in the RPC bodies
constexpr const char * kSessionStorageOperationField = "operation";
constexpr const char * kSessionStorageUserIdField = "userId";
constexpr const char * kSessionStorageFieldsField = "fields";
constexpr const char * kSessionStorageIdField = "id";
constexpr const char * kSessionStorageSessionsField = "sessions";
constexpr const char * kSessionStorageCountField = "count";
constexpr const char * kSessionStorageProjectSharingField = "projectSharingEnabled";

// End RPC constants ========================================================

// Transitional states - set in the activeSession metadata when rpc requests are made to change the state
//   These will override the job launcher state.
static const std::string kActivityStateResuming = "resuming";
static const std::string kActivityStateSuspending = "suspending";
static const std::string kActivityStateShuttingDown = "shutting_down";
static const std::string kActivityStateQuitting = "quitting";

// Running states: launching -> pending -> running
// launch request received, new session url allocated
static const std::string kActivityStateLaunching = "launching";
// job status returned as Pending
static const std::string kActivityStatePending = "pending";
// job status is Running when Launching/Pending, before rsession reports running
static const std::string kActivityStateStarting = "starting";
// job status returned as Running or for R sessions, beginSession called by rsession
static const std::string kActivityStateRunning = "running";
// Session completes the suspend
static const std::string kActivityStateSaved = "saved";
// Session marks itself finished
static const std::string kActivityStateEnded = "ended";
// Request to quit/shutdown job launcher session has been received - waiting for job status "Finished" to destroy
static const std::string kActivityStateDestroyPending = "destroy_pending";

// Exited states - job/process is not running
static const std::string kActivityStateSuspended = "suspended";
static const std::string kActivityStateFailed = "failed";
static const std::string kActivityStateCanceled = "canceled";
static const std::string kActivityStateFinished = "finished";
static const std::string kActivityStateKilled = "killed";

class ActiveSession : boost::noncopyable
{
private:
   friend class ActiveSessions;
   friend class RpcActiveSessionsStorage;

   explicit ActiveSession(const std::string& id) : id_(id) 
   {
   }

   explicit ActiveSession(
      const std::string& id,
      const FilePath& scratchPath,
      std::shared_ptr<IActiveSessionStorage> storage) : id_(id), scratchPath_(scratchPath), storage_(storage)
   {
   }

public:
   static const std::string kCreated;
   static const std::string kExecuting;
   static const std::string kInitial;
   static const std::string kLastUsed;
   static const std::string kLabel;
   static const std::string kProject;
   static const std::string kSavePromptRequired;
   static const std::string kRunning;
   static const std::string kRVersion;
   static const std::string kRVersionHome;
   static const std::string kRVersionLabel;
   static const std::string kWorkingDir;
   static const std::string kActivityState;
   static const std::string kLastStateUpdated;
   static const std::string kEditor;
   static const std::string kLastResumed;
   static const std::string kSuspendTimestamp;
   static const std::string kBlockingSuspend;
   static const std::string kLaunchParameters;

   // The rsession process has exited with an exit code
   static bool isExitedState(const std::string& state)
   {
      return state == kActivityStateFailed || state == kActivityStateCanceled ||
             state == kActivityStateFinished || state == kActivityStateSuspended || state == kActivityStateKilled;
   }

   // The rsession has marked itself as saved/ended or the process is exited
   static bool isSessionEndedState(const std::string& state)
   {
      return isExitedState(state) || state == kActivityStateEnded || state == kActivityStateSaved || state == kActivityStateDestroyPending;
   }

   static bool isTransitionState(const std::string& state)
   {
      return state == kActivityStateSuspending || state == kActivityStateShuttingDown || state == kActivityStateQuitting ||
             state == kActivityStateResuming || state == kActivityStateDestroyPending;
   }

   bool empty() const
   { 
      return storage_ == nullptr;
   }

   bool emptySession() const
   {
      if (empty())
         return true;
      bool validStorage = false;
      Error error = storage_->isValid(&validStorage);
      return error || !validStorage;
   }

   std::string id() const
   {
      return id_;
   }

   const FilePath& scratchPath() const { return scratchPath_; }

   std::string readProperty(const std::string& propertyName) const
   {
      std::string value;
      if (!empty())
      {
         Error error = storage_->readProperty(propertyName, &value);
         if (error)
            LOG_ERROR(error);
      }

      return value;
   }

   void writeProperty(const std::string& propertyName, const std::string& value) const
   {
      if (!empty())
      {
         Error error = storage_->writeProperty(propertyName, value);
         if (error)
            LOG_ERROR(error);
      }
   }

   Error readProperties(const std::set<std::string>& propertyNames, std::map<std::string, std::string>* pValues) const
   {
      std::map<std::string, std::string> values;
      if (!empty())
      {
         if (!propertyNames.empty())
            return storage_->readProperties(propertyNames, pValues);
         else
         {
            // If no properties are specified, read them all
            std::set<std::string> allProperties {
               kExecuting,
               kInitial,
               kLabel,
               kLastUsed,
               kProject,
               kSavePromptRequired,
               kRunning,
               kRVersion,
               kRVersionHome,
               kRVersionLabel,
               kWorkingDir,
               kActivityState,
               kLastStateUpdated,
               kEditor,
               kLastResumed,
               kSuspendTimestamp,
               kBlockingSuspend,
               kCreated,
               kLaunchParameters
             };

            return storage_->readProperties(allProperties, pValues);
         }
      }

      return Success();
   }

   Error writeProperties(const std::map<std::string, std::string>& properties) const
   {
      if (!empty())
         return storage_->writeProperties(properties);

      return Success();
   }

   bool hasProperty(const std::string& propertyName) const
   {
      if (empty())
         return false;

      std::string value;
      Error error = storage_->readProperty(propertyName, &value);
      if (error)
         return false;
      return true;
   }

   std::string project() const
   {
      return readProperty(kProject);
   }

   std::string projectWithRetry() const
   {
      std::string res = project();
      if (!res.empty())
         return res;
      if (hasProperty(kProject))
      {
         int retryCount = 5;
         do {
            LOG_DEBUG_MESSAGE("Found empty project ... sleeping for 200 millis to validate session: " + id());
            boost::this_thread::sleep(boost::posix_time::milliseconds(200));
            res = project();
            if (!res.empty())
               break;
         } while (--retryCount > 0);

         if (res.empty())
            LOG_DEBUG_MESSAGE("Returning empty project after retries for: " + id());
         else
            LOG_DEBUG_MESSAGE("Found project after: " + std::to_string(5 - retryCount) + " retries for: " + id());
      }
      else
         LOG_DEBUG_MESSAGE("Returning empty project - no project property for session: " + id());
      return res;
   }

   void setProject(const std::string& newProject)
   {
      // If we are not changing the value (as when resuming), do not update the file as this can lead to nfs clients seeing an empty value
      // that leads to an invalid session
      if (newProject == project())
         return;
      writeProperty(kProject, newProject);
   }

   std::string workingDir() const
   {
      return readProperty(kWorkingDir);
   }

   void setWorkingDir(const std::string& workingDir)
   {
      writeProperty(kWorkingDir, workingDir);
   }

   std::string activityState() const
   {
      return readProperty(kActivityState);
   }

   void setActivityState(const std::string& activityState, bool isTransition)
   {
      if (isTransition)
         writeProperties({
            {kActivityState, activityState},
            {kLastStateUpdated, getNowAsTimestamp()}
         });
      else
         writeProperty(kActivityState, activityState);
   }

   std::string editor() const
   {
      std::string res = readProperty(kEditor);
      if (res == "") // If resuming a session saved before this field was added
         res = kWorkbenchRStudio;
      return res;
   }

   void setEditor(const std::string& editor)
   {
      writeProperty(kEditor, editor);
   }

   bool initial() const
   {
      if (!empty())
      {
         std::string value = readProperty(kInitial);

         if (!value.empty())
            return value == "1" ?  true : false;
         else
            return false;
      }
      else
      {
         // if empty, we are likely in desktop mode (as we have no specified scratch path)
         // in this default case, we want initial to be true, since every time the session
         // is started, we should start in the default working directory
         return true;
      }
   }

   void setInitial(bool initial)
   {
      std::string value = initial ? "1" : "0";
      writeProperty(kInitial, value);
   }

   void setBlockingSuspend(json::Array blocking)
   {
      if (!empty())
      {
         writeProperty(kBlockingSuspend, blocking.writeFormatted());
      }
   }

   boost::posix_time::ptime suspensionTime() const
   {
      return ptimeTimestampProperty(kSuspendTimestamp);
   }

   void setSuspensionTime(const boost::posix_time::ptime value = boost::posix_time::second_clock::universal_time())
   {
      setPtimeTimestampProperty(kSuspendTimestamp, value);
   }

   boost::posix_time::ptime lastResumed() const
   {
      return ptimeTimestampProperty(kLastResumed);
   }

   void setLastResumed(const boost::posix_time::ptime value = boost::posix_time::second_clock::universal_time())
   {
      setPtimeTimestampProperty(kLastResumed, value);
   }

   double lastUsed() const
   {
      return timestampProperty(kLastUsed);
   }

   void setLastUsed()
   {
      setTimestampProperty(kLastUsed);
   }

   double lastStateUpdated() const
   {
      return timestampProperty(kLastStateUpdated);
   }

   boost::posix_time::ptime lastStateUpdatedTime() const
   {
      return timestampToPtime(lastStateUpdated());
   }

   void setLastStateUpdated()
   {
      setTimestampProperty(kLastStateUpdated);
   }

   double created() const
   {
      return timestampProperty(kCreated);
   }

   boost::posix_time::ptime createdTime() const
   {
      return timestampToPtime(created());
   }

   void setCreated()
   {
      setTimestampProperty(kCreated);
   }

   bool executing() const
   {
      std::string value = readProperty(kExecuting);

      if (!value.empty())
         return value == "1" ? true : false;
      else
         return false;
   }

   void setExecuting(bool executing)
   {
      std::string value = executing ? "1" : "0";
      writeProperty(kExecuting, value);
   }

   bool savePromptRequired() const
   {
      std::string value = readProperty(kSavePromptRequired);

      if (!value.empty())
         return safe_convert::stringTo<bool>(value, false);
      else
         return false;
   }

   void setSavePromptRequired(bool savePromptRequired)
   {
         std::string value = safe_convert::numberToString(savePromptRequired);
         writeProperty(kSavePromptRequired, value);
   }


   bool running() const
   {
      std::string value = readProperty(kRunning);

      if (!value.empty())
         return value == "1" ? true : false;
      else
         return false;
   }

   std::string rVersion()
   {
      return readProperty(kRVersion);
   }

   std::string rVersionLabel()
   {
      return readProperty(kRVersionLabel);
   }

   std::string rVersionHome()
   {
      return readProperty(kRVersionHome);
   }

   void setRVersion(const std::string& rVersion,
                    const std::string& rVersionHome,
                    const std::string& rVersionLabel = "")
   {
         writeProperty(kRVersion, rVersion);
         writeProperty(kRVersionHome, rVersionHome);
         writeProperty(kRVersionLabel, rVersionLabel);
   }

   // historical note: this will be displayed as the session name
   std::string label()
   {
      return readProperty(kLabel);
   }

   // historical note: this will be displayed as the session name
   void setLabel(const std::string& label)
   {
      writeProperty(kLabel, label);
   }

   void beginSession(const std::string& rVersion,
                     const std::string& rVersionHome,
                     const std::string& rVersionLabel = "")
   {
      setLastUsed();
      setRunning(true);
      setRVersion(rVersion, rVersionHome, rVersionLabel);
      setActivityState(kActivityStateRunning, true);
   }

   void endSession()
   {
      setLastUsed();
      setRunning(false);
      setExecuting(false);
      std::string curState = activityState();
      if (!isSessionEndedState(curState))
      {
         LOG_DEBUG_MESSAGE("Ending session: " + id() + " changing activityState to ended from: " + curState);
         setActivityState(kActivityStateEnded, true);
      }
      else
         LOG_DEBUG_MESSAGE("Ending session: " + id() + " with previous activityState: " + curState);
   }

   uintmax_t suspendSize() const
   {
      FilePath suspendPath = scratchPath_.completePath("suspended-session-data");
      if (!suspendPath.exists())
         return 0;

      return suspendPath.getSizeRecursive();
   }

   core::Error destroy()
   {
      if (!empty())
      {
         LOG_DEBUG_MESSAGE("Removing session " + id_);
         return storage_->destroy();
      }
      else
         return Success();
   }

   bool validate() const
   {
      if (empty())
      {
         LOG_DEBUG_MESSAGE("ActiveSession validation failed on empty session");
         return false;
      }

      bool validStorage = false;
      Error storageError = storage_->isValid(&validStorage);
      if (storageError || !validStorage)
      {
         LOG_DEBUG_MESSAGE("ActiveSession validation failed - no session metadata for: " + id());
         if(storageError)
            LOG_ERROR(storageError);
         return false;
      }

      bool isRSession = editor() == kWorkbenchRStudio || editor().empty();
      if (!isRSession)
         return true;

      // ensure the properties are there but don't check properties like
      // lastUsed() or workingDir() that will appear as briefly empty as they
      // are being updated
      std::string projectVal = projectWithRetry();
      if (projectVal.empty())
      {
         LOG_DEBUG_MESSAGE("ActiveSession validation failed - project is empty");
         return false;
      }

      // validated!
      return true;
   }
   
   bool operator>(const ActiveSession& rhs) const
   {
      if (sortConditions_.executing_ == rhs.sortConditions_.executing_)
      {
         if (sortConditions_.running_ == rhs.sortConditions_.running_)
         {
            if (sortConditions_.lastUsed_ == rhs.sortConditions_.lastUsed_)
               return id() > rhs.id();

            return sortConditions_.lastUsed_ > rhs.sortConditions_.lastUsed_;
         }

         return sortConditions_.running_;
      }
      
      return sortConditions_.executing_;
   }

 private:
   struct SortConditions
   {
      SortConditions() :
         executing_(false),
         lastUsed_(0)
      {
         
      }

      bool executing_;
      bool running_;
      double lastUsed_;
   };

   void cacheSortConditions()
   {
      sortConditions_.executing_ = executing();
      sortConditions_.running_ = running();
      sortConditions_.lastUsed_ = lastUsed();
   }

   void setTimestampProperty(const std::string& property)
   {
      writeProperty(property, getNowAsTimestamp());
   }

   static std::string getNowAsTimestamp()
   {
      double now = date_time::millisecondsSinceEpoch();
      return safe_convert::numberToString(now);
   }

   static boost::posix_time::ptime timestampToPtime(double millisTime)
   {
      long time = (long) millisTime;
      return boost::posix_time::from_time_t(time / 1000) +
             boost::posix_time::millisec(time % 1000);
   }

   double timestampProperty(const std::string& property) const
   {
      std::string value = readProperty(property);

      if (!value.empty())
         return safe_convert::stringTo<double>(value, 0);
      else
         return 0;
   }

   void setPtimeTimestampProperty(const std::string& property, const boost::posix_time::ptime& time)
   {
      if (!empty())
      {
         writeProperty(property, getAsPTimestamp(time));
      }
   }

   boost::posix_time::ptime ptimeTimestampProperty(const std::string& property) const
   {
      if (!empty())
      {
         std::string value = "Value Not Read";
         try
         {
            value = readProperty(property);
            if (value.empty())
               return boost::posix_time::not_a_date_time;

            // posix_time::from_iso_extended_string can't parse not_a_date_time correctly, so handling it here
            if (value == boost::posix_time::to_iso_extended_string(boost::posix_time::not_a_date_time))
               return boost::posix_time::not_a_date_time;

            boost::posix_time::ptime retVal = boost::posix_time::from_iso_extended_string(value);

            if (retVal.is_not_a_date_time())
               return boost::posix_time::not_a_date_time;

            return retVal;
         }
         catch (std::exception const& e)
         {
            LOG_INFO_MESSAGE("Failure reading property " + property + ": " + std::string(e.what()) + ". Property contents: " + value);
         }
      }
      return boost::posix_time::not_a_date_time;
   }

   static std::string getNowAsPTimestamp()
   {
      const boost::posix_time::ptime now = boost::posix_time::second_clock::universal_time();
      return getAsPTimestamp(now);
   }


   static std::string getAsPTimestamp(const boost::posix_time::ptime& time)
   {
      return boost::posix_time::to_iso_extended_string(time);
   }

   void setRunning(bool running)
   {
         std::string value = running ?  "1" : "0";
         writeProperty(kRunning, value);
   }

private:
   std::string id_;
   FilePath scratchPath_;
   std::shared_ptr<IActiveSessionStorage> storage_;
   SortConditions sortConditions_;
};

class IActiveSessionsStorage;

class ActiveSessions : boost::noncopyable
{
public:
   explicit ActiveSessions(std::shared_ptr<IActiveSessionsStorage> storage, const FilePath& rootStoragePath);

   static FilePath storagePath(const FilePath& path)
   {
      return path.completeChildPath("sessions/active");
   }

   core::Error create(const std::string& project,
                      const std::string& working,
                      std::string* pId) const
   {
      return create(project, working, true, kWorkbenchRStudio, pId);
   }

   core::Error create(const std::string& project,
                      const std::string& working,
                      bool initial,
                      const std::string& editor,
                      std::string* pId) const;

   std::vector<boost::shared_ptr<ActiveSession> > list(bool validate,
                                                       std::vector<boost::shared_ptr<ActiveSession>>* invalidSessions = nullptr) const;

   size_t count() const;

   boost::shared_ptr<ActiveSession> get(const std::string& id) const;

   FilePath storagePath() const { return storagePath_; }

   boost::shared_ptr<ActiveSession> emptySession(const std::string& id) const;

private:
   FilePath storagePath_;
   std::shared_ptr<IActiveSessionsStorage> storage_;
};

// active session as tracked by rserver processes
// these are stored in a common location per rserver
// so that the server process can keep track of all
// active sessions, regardless of users running them
class GlobalActiveSession : boost::noncopyable
{
public:
   explicit GlobalActiveSession(const FilePath& path) : filePath_(path)
   {
      settings_.initialize(filePath_);
   }

   virtual ~GlobalActiveSession() {}

   std::string sessionId() { return settings_.get("sessionId", ""); }
   void setSessionId(const std::string& sessionId) { settings_.set("sessionId", sessionId); }

   std::string username() { return settings_.get("username", ""); }
   void setUsername(const std::string& username) { settings_.set("username", username); }

   std::string userHomeDir() { return settings_.get("userHomeDir", ""); }
   void setUserHomeDir(const std::string& userHomeDir) { settings_.set("userHomeDir", userHomeDir); }

   int sessionTimeoutKillHours() { return settings_.getInt("sessionTimeoutKillHours", 0); }
   void setSessionTimeoutKillHours(int val) { settings_.set("sessionTimeoutKillHours", val); }

   core::Error destroy() { return filePath_.removeIfExists(); }

private:
   core::Settings settings_;
   core::FilePath filePath_;
};

class GlobalActiveSessions : boost::noncopyable
{
public:
   explicit GlobalActiveSessions(const FilePath& rootPath) : rootPath_(rootPath) {}
   std::vector<boost::shared_ptr<GlobalActiveSession> > list() const;
   boost::shared_ptr<GlobalActiveSession> get(const std::string& id) const;

private:
   core::FilePath rootPath_;
};

void trackActiveSessionCount(std::shared_ptr<IActiveSessionsStorage> storage,
                             const FilePath& rootStoragePath,
                             boost::function<void(size_t)> onCountChanged);

} // namespace r_util
} // namespace core
} // namespace rstudio

#endif // CORE_R_UTIL_ACTIVE_SESSIONS_HPP
