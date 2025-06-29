/*
 * SessionHistoryArchive.hpp
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

#ifndef SESSION_HISTORY_ARCHIVE_HPP
#define SESSION_HISTORY_ARCHIVE_HPP

#include <sstream>
#include <string>
#include <vector>

#include <boost/utility.hpp>

namespace rstudio {
namespace core {
   class Error;
   class FilePath;
}
}
 
namespace rstudio {
namespace session {
namespace modules { 
namespace history {
   
struct HistoryEntry
{
   HistoryEntry() : index(0), timestamp(0) {}
   HistoryEntry(int index, double timestamp, const std::string& command)
      : index(index), timestamp(timestamp), command(command)
   {
   }
   int index;
   double timestamp;
   std::string command;
};

class HistoryArchive;
HistoryArchive& historyArchive();

class HistoryArchive : boost::noncopyable
{
private:
   HistoryArchive();
   friend HistoryArchive& historyArchive();

public:
   static void migrateRhistoryIfNecessary();

public:
   core::Error add(const std::string& command);
   const std::vector<HistoryEntry>& entries();

private:
   mutable time_t entryCacheLastWriteTime_;
   mutable std::vector<HistoryEntry> entries_;
   
   mutable std::stringstream buffer_;
   mutable bool flushScheduled_;
   void flush();
   
private:
   void onShutdown();
};
                       
} // namespace history
} // namespace modules
} // namespace session
} // namespace rstudio

#endif // SESSION_HISTORY_ARCHIVE_HPP
