/*
 * SessionSymbolIndex.hpp
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

#ifndef SESSION_SYMBOL_INDEX_HPP
#define SESSION_SYMBOL_INDEX_HPP

namespace rstudio {
namespace core {
   class Error;
}
}
 
namespace rstudio {
namespace session {
namespace modules { 
namespace symbol_index {
   
core::Error initialize();
                       
} // namespace symbol_index
} // namespace modules
} // namespace session
} // namespace rstudio

#endif // SESSION_SYMBOL_INDEX_HPP 