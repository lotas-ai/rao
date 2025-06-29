/*
 * SessionUrlPorts.hpp
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


#ifndef SESSION_URL_PORTS_HPP
#define SESSION_URL_PORTS_HPP

#include <string>

namespace rstudio {
namespace core {
   class Error;
}
}

namespace rstudio {
namespace session {
namespace url_ports {

// localUrl is a full URL that may or may not be local.
// If absolute, return a full URL, not just the portion of the path following the workbench URL.
std::string translateLocalUrl(const std::string& localUrl, bool absolute = true);

std::string mapUrlPorts(const std::string& url);

core::Error initialize();

}  // namespace url_ports
}  // namespace session
}  // namespace rstudio

#endif
