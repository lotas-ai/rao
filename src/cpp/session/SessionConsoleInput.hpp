/*
 * SessionConsoleInput.hpp
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

#ifndef SESSION_CONSOLE_INPUT_HPP
#define SESSION_CONSOLE_INPUT_HPP

#include <shared_core/json/Json.hpp>
#include <core/json/JsonRpc.hpp>

namespace rstudio {
namespace r {
namespace session {
struct RConsoleInput;
}
}

namespace session {
namespace console_input {

void clearConsoleInputBuffer();
bool executing();
void updateSessionExecuting();
core::Error extractConsoleInput(const core::json::JsonRpcRequest& request);
void reissueLastConsolePrompt();
void addToConsoleInputBuffer(
      const rstudio::r::session::RConsoleInput& consoleInput);

void consolePrompt(const std::string& prompt, bool addToHistory);
void consoleInput(const std::string& input);

bool rConsoleRead(const std::string& prompt,
                  bool addToHistory,
                  r::session::RConsoleInput* pConsoleInput);

} // namespace console_input
} // namespace session
} // namespace rstudio

#endif

