/*
 * TcpIpAsyncServer.hpp
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

#ifndef CORE_HTTP_TCP_IP_ASYNC_SERVER_HPP
#define CORE_HTTP_TCP_IP_ASYNC_SERVER_HPP
   
#include <boost/asio/ip/tcp.hpp>

#include <core/http/AsyncServerImpl.hpp>
#include <core/http/TcpIpSocketUtils.hpp>

namespace rstudio {
namespace core {
namespace http {

class TcpIpAsyncServer : public AsyncServerImpl<boost::asio::ip::tcp>
{
public:
   TcpIpAsyncServer(const std::string& serverName,
                    const std::string& baseUri = std::string(),
                    bool disableOriginCheck = true,
                    const std::vector<boost::regex> allowedOrigins = std::vector<boost::regex>(),
                    const Headers& additionalHeaders = Headers(),
                    int statsMonitorSeconds = 0,
                   const boost::shared_ptr<AsyncServerStatsProvider> statsProvider = boost::shared_ptr<AsyncServerStatsProvider>())
      : AsyncServerImpl<boost::asio::ip::tcp>(serverName, baseUri, disableOriginCheck, allowedOrigins, additionalHeaders, statsMonitorSeconds, statsProvider)
   {
   }
   
public:
   Error init(const std::string& address, const std::string& port)
   {
      return initTcpIpAcceptor(acceptorService(), address, port);
   }
};

} // namespace http
} // namespace core
} // namespace rstudio

#endif // CORE_HTTP_TCP_IP_ASYNC_SERVER_HPP


