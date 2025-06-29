/*
 * SessionSessionMetadataRpcOverlay.cpp
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

#include <string>
#include <set>
#include <map>
#include <vector>

#include <shared_core/Error.hpp>


using namespace rstudio::core;

namespace rstudio {
namespace server {
namespace session_metadata {
namespace overlay {

Error handleGlobalReadAll(
   const std::set<std::string>& fields,
   std::vector<std::map<std::string, std::string>>* pValues)
{
   // This will never be reached in this code, client should get an unauthorized error first.
   return Success();
}

} // namespace overlay
} // namespace session_metadata
} // namespace server
} // namespace rstudio
