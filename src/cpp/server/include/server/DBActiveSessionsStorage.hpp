/*
 * DBActiveSessionsStorage.hpp
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

#ifndef DB_ACTIVE_SESSIONS_STORAGE_HPP
#define DB_ACTIVE_SESSIONS_STORAGE_HPP

#include <core/r_util/RActiveSessions.hpp>
#include <core/r_util/RActiveSessionsStorage.hpp>

#include <shared_core/Error.hpp>
#include <shared_core/FilePath.hpp>
#include <shared_core/system/User.hpp>

namespace rstudio {
namespace server {
namespace storage {

class DBActiveSessionsStorage : public core::r_util::IActiveSessionsStorage
{
public:
   explicit DBActiveSessionsStorage(const core::system::User& user);
   ~DBActiveSessionsStorage() = default;
   
   std::vector< std::string > listSessionIds() const override;
   size_t getSessionCount() const override;
   std::shared_ptr<core::r_util::IActiveSessionStorage> getSessionStorage(const std::string& id) const override;
   core::Error hasSessionId(const std::string& sessionId, bool* pHasSessionId) const override;
private:
   const core::system::User user_;
};

} // namespace storage
} // namespace server
} // namespace rstudio

#endif
