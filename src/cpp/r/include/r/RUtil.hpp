/*
 * RUtil.hpp
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

#ifndef R_UTIL_HPP
#define R_UTIL_HPP

#define R_INTERNAL_FUNCTIONS

#include <string>
#include <r/RInternal.hpp>

#ifdef _WIN32
# include <core/system/Win32RuntimeLibrary.hpp>
# define R_ERRNO MSVC_ERRNO
#else
# define R_ERRNO errno
#endif

namespace rstudio {
namespace core {
   class FilePath;
   class Error;
}
}

namespace rstudio {
namespace r {
namespace util {

// NOTE: On Windows, environment variables set via core::system::setenv()
// won't be visible in the R session.
//
// These routines should be preferred when getting and
// setting environment variables in the R session.
void setenv(const std::string& key, const std::string& value);
std::string getenv(const std::string& key);

void appendToSystemPath(const core::FilePath& path);
void appendToSystemPath(const std::string& path);
void prependToSystemPath(const core::FilePath& path);
void prependToSystemPath(const std::string& path);

template <typename T>
void addToSystemPath(const T& path, bool prepend)
{
   prepend ? prependToSystemPath(path) : appendToSystemPath(path);
}

std::string expandFileName(const std::string& name);
   
std::string fixPath(const std::string& path);

bool hasRequiredVersion(const std::string& version);

bool hasExactVersion(const std::string& version);

bool hasCapability(const std::string& capability);

std::string rconsole2utf8(const std::string& encoded);

core::Error nativeToUtf8(const std::string& value,
                         bool allowSubstitution,
                         std::string* pResult);

core::Error utf8ToNative(const std::string& value,
                         bool allowSubstitution,
                         std::string* pResult);

core::Error iconvstr(const std::string& value,
                     const std::string& from,
                     const std::string& to,
                     bool allowSubstitution,
                     std::string* result);


bool isRKeyword(const std::string& name);
bool isWindowsOnlyFunction(const std::string& name);

// Is package attached to search path?
bool isPackageAttached(const std::string& packageName);

void synchronizeLocale();

void str(SEXP objectSEXP);

} // namespace util   
} // namespace r
} // namespace rstudio


#endif // R_UTIL_HPP 

