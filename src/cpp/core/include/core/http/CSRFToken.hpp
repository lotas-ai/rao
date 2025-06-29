/*
 * CSRFToken.hpp
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

#ifndef CORE_HTTP_CSRF_TOKEN_HPP
#define CORE_HTTP_CSRF_TOKEN_HPP

#include <string>
#include <boost/optional.hpp>
#include <boost/date_time/posix_time/posix_time.hpp>

#include <core/http/Cookie.hpp>

namespace rstudio {
namespace core {
namespace http {

class Request;
class Response;

// Adds a CSRF (cross site request forgery) cookie. This is simply a cookie with
// a random value (token). Returns the input or the generated token (if empty)
std::string setCSRFTokenCookie(const Request& request,
      const boost::optional<boost::posix_time::time_duration>& expiresFromNow,
      const std::string& token,
      const std::string& path,
      bool secure,
      core::http::Cookie::SameSite sameSite,
      core::http::Response* pResponse);

std::string getCSRFTokenCookie(const Request& request);
std::string getCSRFTokenHeader(const Request& request);

// Validates an HTTP POST request by ensuring that the submitted fields include
// a valid CSRF token.
bool validateCSRFForm(const Request& request,
                      Response* response);

// Validates any other HTTP request by ensuring that the CSRF HTTP header matches the accompanying
// token cookie.
bool validateCSRFHeaders(const Request& request);



} // namespace http
} // namespace core
} // namespace rstudio

#endif // CORE_HTTP_CSRF_TOKEN_HPP
