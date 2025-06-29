/*
 * DBActiveSessionStorage.cpp
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

#include <server/DBActiveSessionStorage.hpp>

#include <core/Database.hpp>
#include <core/r_util/RActiveSessions.hpp>
#include <shared_core/SafeConvert.hpp>
#include <server_core/ServerDatabase.hpp>

#include <numeric>

using namespace rstudio::core;
using namespace rstudio::core::r_util;
using namespace rstudio::server_core::database;

namespace rstudio {
namespace server {
namespace storage {

namespace {

// This is the column name of the foreign key between the active_session_metadata
// And the licensed user table. The only column that isn't a string
const std::string kUserId = "user_id";

// Constants for the table and column names
const std::string kTableName = "active_session_metadata";
const std::string kSessionIdColumnName = "session_id";

static const std::string kEditorColumnName = "workbench";

inline const std::string& columnName(const std::string& propertyName)
{
   if (propertyName == ActiveSession::kEditor)
      return kEditorColumnName;

   return propertyName;
}

inline const std::string& propertyName(const std::string& columnName)
{
   if (columnName == kEditorColumnName)
      return ActiveSession::kEditor;

   return columnName;
}

std::string getKeyString(const std::map<std::string, std::string>& sourceMap)
{
   std::string keys = std::accumulate(
      ++sourceMap.begin(),
      sourceMap.end(),
      columnName(sourceMap.begin()->first),
      [](std::string a, std::pair<std::string, std::string> b)
      {
         return a + ", " + columnName(b.first);
      });
   return keys;
}

std::string getValueString(const std::map<std::string, std::string>& sourceMap)
{
   std::string values = std::accumulate(
      ++sourceMap.begin(),
      sourceMap.end(),
      "'" + sourceMap.begin()->second + "'",
      [](std::string a, std::pair<std::string, std::string> b) {
         std::string str{a};
         if (b.first == kUserId)
         {
            a += ", " + b.second;
         }
         else
         {
            a += ", '" + b.second + "'";
         }
         return a;
      });
   return values;

}

std::string getUpdateStringAndValues(const std::map<std::string, std::string>& sourceMap,
                                     std::vector<std::string>* pNames, std::vector<std::string>* pValues)
{
   std::string firstPropName(sourceMap.begin()->first);
   (*pNames).push_back(firstPropName);
   (*pValues).push_back(std::string(sourceMap.begin()->second));

   std::string setValuesString = std::accumulate(
      ++sourceMap.begin(),
      sourceMap.end(),
      columnName(firstPropName) + " = :" + columnName(firstPropName) + " ",
      [pNames, pValues](std::string a, std::pair<std::string, std::string> iter)
      {         
         (*pNames).push_back(std::string(iter.first));
         (*pValues).push_back(std::string(iter.second));
         return a + ", " + columnName(iter.first) + " = " + ":" + columnName(iter.first) + " ";
      });
   return setValuesString;
}

std::string getColumnNameList(const std::set<std::string>& colNames)
{
   std::string cols = std::accumulate(
      ++colNames.begin(),
      colNames.end(), 
      columnName(*(colNames.begin())), [](std::string a, std::string b)
      {
         return a + ", " + columnName(b);
      });
   return cols;
}

void populateMapWithRow(database::RowsetIterator iter, std::map<std::string, std::string> *pTargetMap)
{
   for(size_t i=0; i < iter->size(); i++)
   {
      std::string key = iter->get_properties(i).get_name();

      if (key == kUserId)
         pTargetMap->emplace(key, std::to_string(iter->get<int>(key)));
      else
         pTargetMap->emplace(propertyName(key),
               iter->get<std::string>(key, ""));
   }
}

Error getSessionCount(boost::shared_ptr<database::IConnection> connection, std::string sessionId, int* pCount)
{
   database::Query query = connection->query("SELECT COUNT(*) FROM " + kTableName + " WHERE " + kSessionIdColumnName + " = :id")
      .withInput(sessionId)
      .withOutput(*pCount);

   Error error = connection->execute(query);

   if (error)
      return Error("DatabaseException", errc::DBError, "Error while retrieving session count for [ session:" + sessionId + " ]", error, ERROR_LOCATION);

   return Success();
}

} // anonymous namespace

Error getConn(boost::shared_ptr<database::IConnection>* connection) {
   bool success = server_core::database::getConnection(boost::posix_time::milliseconds(500), connection);

   if (!success)
   {
      return Error("FailedToAcquireConnection", errc::ConnectionFailed, "Failed to acquire a connection in 500 milliseconds.", ERROR_LOCATION);
   }

   return Success();
}

Error DBActiveSessionStorage::getConnectionOrOverride(boost::shared_ptr<database::IConnection>* connection)
{
   if (overrideConnection_ == nullptr)
      return getConn(connection);
   else
   {
      *connection = overrideConnection_;
      return Success();
   }
}

DBActiveSessionStorage::DBActiveSessionStorage(const std::string& sessionId, const system::User& user) :
   sessionId_(sessionId),
   user_(user)
{
}

DBActiveSessionStorage::DBActiveSessionStorage(const std::string& sessionId, const system::User& user, boost::shared_ptr<core::database::IConnection> overrideConnection) :
   sessionId_(sessionId),
   user_(user),
   overrideConnection_(overrideConnection)
{
}

Error DBActiveSessionStorage::readProperty(const std::string& name, std::string* pValue)
{
   static const std::string empty;

   *pValue = "";
   boost::shared_ptr<database::IConnection> connection;
   Error error = getConnectionOrOverride(&connection);
   
   if (error)
      return error;

   std::string queryStr = "SELECT ";
   queryStr
      .append(columnName(name))
      .append(" FROM ")
      .append(kTableName)
      .append(" WHERE ")
      .append(kSessionIdColumnName)
      .append(" = :id");

   database::Query query = connection->query(queryStr)
      .withInput(sessionId_);

   database::Rowset rowset;
   error = connection->execute(query, rowset);

   if (error)
      return Error("DatabaseException", errc::DBError, "Database exception during property read [ session:" + sessionId_ + " property:" + name + " ]", error, ERROR_LOCATION);

   auto iter = rowset.begin();

   if (iter == rowset.end())
      return Error("Session does not exist", errc::SessionNotFound, ERROR_LOCATION);

   if (name != kUserId)
      *pValue = iter->get<std::string>(0, "");
   else
      *pValue = std::to_string(iter->get<int>(0));

   // Sanity check number of returned rows, by using the pk in the where clause we should only get 1 row
   if (++iter != rowset.end())
   {
      int count = 1;
      while (iter++ != rowset.end())
         ++count;
      return Error("Too many sessions returned", errc::TooManySessionsReturned, "Expected only one session returned, found " + std::to_string(count) + "[ session:" + sessionId_ + " ]", ERROR_LOCATION);
   }

   return Success();
}

Error DBActiveSessionStorage::readProperties(const std::set<std::string>& names, std::map<std::string, std::string>* pValues)
{
   pValues->clear();
   boost::shared_ptr<database::IConnection> connection;
   Error error = getConnectionOrOverride(&connection);

   if (error)
      return error;
   
   std::string namesString = getColumnNameList(names);
   database::Query query = connection->query("SELECT " + namesString + " FROM " + kTableName + " WHERE " + kSessionIdColumnName + "=:id")
      .withInput(sessionId_);

   database::Rowset rowset;
   error = connection->execute(query, rowset);

   if (error)
      return Error("DatabaseException", errc::DBError, "Database exception during proprerties read [ session:" + sessionId_ + " properties:" + namesString + " ]", error, ERROR_LOCATION);

   database::RowsetIterator iter = rowset.begin();
   if (iter == rowset.end())
      return Error("Session does not exist", errc::SessionNotFound, ERROR_LOCATION);

   populateMapWithRow(iter, pValues);

   // Sanity check number of returned rows, by using the pk in the where clause we should only get 1 row
   if (++iter != rowset.end())
   {
      int count = 1;
      while (iter++ != rowset.end())
         ++count;
      return Error("Too many sessions returned", errc::TooManySessionsReturned, "Expected only one session returned, found " + std::to_string(count) + "[ session:" + sessionId_ + " ]", ERROR_LOCATION);
   }

   return error;
}

Error DBActiveSessionStorage::readProperties(std::map<std::string, std::string>* pValues)
{
   // Normally we avoid using * in select lists to avoid unexpected names, 
   // or orders of columns. However in this case we explicitly want all columns,
   // and our readProperties uses the populateMapWithRow which discovers the
   // column names, so new or unexpected column names will not cause issues.
   
   std::set<std::string> all{"*"};
   return readProperties(all, pValues);
}

Error DBActiveSessionStorage::writeProperty(const std::string& name, const std::string& value)
{
   boost::shared_ptr<database::IConnection> connection;
   Error error = getConnectionOrOverride(&connection);

   if (error)
      return error;

   database::Query query = connection->query("UPDATE " + kTableName + " SET " + columnName(name) + " = :value WHERE " + kSessionIdColumnName + " = :id")
      .withInput(value)
      .withInput(sessionId_);

   error = connection->execute(query);

   if (error)
      return Error("DatabaseException", errc::DBError, "Database error while updating session metadata [ session: " + sessionId_ + " property: " + name + " ]", error, ERROR_LOCATION);

   return error;
}

Error DBActiveSessionStorage::writeProperties(const std::map<std::string, std::string>& properties)
{
   LOG_DEBUG_MESSAGE("Writing session properties: " + sessionId_);
   boost::shared_ptr<database::IConnection> connection;
   Error error = getConnectionOrOverride(&connection);

   if (error)
      return error;

   database::Query query = connection->query("SELECT * FROM " + kTableName + " WHERE " + kSessionIdColumnName + " = :id")
      .withInput(sessionId_);
   database::Rowset rowset;

   if (error)
      return error;

   error = connection->execute(query, rowset);

   if (error)
      return Error("DatabaseException", errc::DBError, "Error while checking for existing row for upsert [ session:" + sessionId_ + " properties:" + getKeyString(properties) + " ]", error, ERROR_LOCATION);
   

   database::RowsetIterator iter = rowset.begin();
   if (iter != rowset.end())
   {
      // Sanity check number of returned rows, by using the pk in the where clause we should only get 1 row
      if (++iter != rowset.end())
      {
         int count = 1;
         while (iter++ != rowset.end())
            ++count;
         return Error("Too many sessions returned", errc::TooManySessionsReturned, "Expected only one session returned, found " + std::to_string(count) + "[ session:" + sessionId_ + " ]", ERROR_LOCATION);
      }

      std::vector<std::string> propNames, propValues;

      std::string queryStr = "UPDATE " + kTableName + " SET " + getUpdateStringAndValues(properties, &propNames, &propValues) + " WHERE session_id = :session_id";

      database::Query updateQuery = connection->query(queryStr);
      for (unsigned int i = 0; i < propValues.size(); i++)
         updateQuery.withInput(propValues[i], propNames[i]);
      updateQuery.withInput(sessionId_, "session_id");
      
      error = connection->execute(updateQuery);

      if (error)
         return Error("DatabaseException", errc::DBError, "Error while updating properties [ session:" + sessionId_ + " properties:" + getKeyString(properties) + " ]", error, ERROR_LOCATION);
   }
   else
   {
      // First update, ensure the user_id FK gets inserted. No value necessary since it will do a select.
      std::map<std::string, std::string> propsCopy(properties);
      propsCopy[kUserId] = "(SELECT id FROM licensed_users WHERE user_name='" + user_.getUsername() + "' AND user_id=" + std::to_string(user_.getUserId()) +")";

      std::string queryStr = "INSERT INTO " +
            kTableName +
            " (" +
            kSessionIdColumnName +
            ", " +
            getKeyString(propsCopy) +
            ") VALUES (:id, " +
            getValueString(propsCopy) +
            ")";

      LOG_DEBUG_MESSAGE("Insert Session query: " + queryStr);
      database::Query insertQuery = connection->query(queryStr)
         .withInput(sessionId_);

      error = connection->execute(insertQuery);

      if (error)
         return Error("DatabaseException", errc::DBError, "Error while updating properties [ session:" + sessionId_ + " properties:" + getKeyString(properties) + " ]", error, ERROR_LOCATION);
   }

   return error;
}

Error DBActiveSessionStorage::destroy()
{
   LOG_DEBUG_MESSAGE("Removing active session for: " + sessionId_ + " from database");

   boost::shared_ptr<database::IConnection> connection;
   Error error = getConnectionOrOverride(&connection);

   if (error)
      return error;

   database::Query query = connection->query("DELETE FROM " + kTableName + " WHERE " + kSessionIdColumnName + " = :id")
      .withInput(sessionId_);

   error = connection->execute(query);

   if (error)
      return Error("DatabaseException", errc::DBError, "Error while deleting session metadata [ session:" + sessionId_ + " ]", error, ERROR_LOCATION);

   if (!query.getAffectedRows())
      LOG_DEBUG_MESSAGE("Failed to delete active session from database - no rows removed for: " + sessionId_);
      
   return error;
}

Error DBActiveSessionStorage::isValid(bool* pValue)
{
   *pValue = false;

   boost::shared_ptr<database::IConnection> connection;
   Error error = getConnectionOrOverride(&connection);
   int count;

   if (error)
      return error;

   error = getSessionCount(connection, sessionId_, &count);
   if (error)
      return error;

   // ensure one and only one
   if (count > 1)
      return Error("Too Many Sessions Returned", errc::TooManySessionsReturned, "Expected only one session returned, found " + std::to_string(count) + "[ session:" + sessionId_ + " ]", ERROR_LOCATION);
   else
      if (count == 1)
         *pValue = true;
   return Success();
}

} // Namespace storage
} // Namespace server
} // Namespace rstudio
