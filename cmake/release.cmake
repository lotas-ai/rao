#
# release.cmake
#
# Copyright (C) 2022 by Posit Software, PBC
#
# Unless you have received this program directly from Posit Software pursuant
# to the terms of a commercial license agreement with Posit Software, then
# this program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

file(STRINGS "${RSTUDIO_PROJECT_ROOT}/version/RELEASE" RSTUDIO_RELEASE_NAME LIMIT_COUNT 1)
string(STRIP "${RSTUDIO_RELEASE_NAME}" RSTUDIO_RELEASE_NAME)
set(RSTUDIO_RELEASE_NAME "${RSTUDIO_RELEASE_NAME}" CACHE STRING "Rao release name")

# First we try and get the build type from the environment variable RSTUDIO_VERSION_SUFFIX, which
# should match the format "-[build type]+" (e.g. "-daily+"). If the build type is not set there, fall
# back to reading the BUILDTYPE file in the version directory.
string(REGEX MATCH "-[a-zA-Z]+\\+" RSTUDIO_BUILD_TYPE "$ENV{RSTUDIO_VERSION_SUFFIX}")
string(REGEX REPLACE "-|\\+" "" RSTUDIO_BUILD_TYPE "${RSTUDIO_BUILD_TYPE}")
string(STRIP "${RSTUDIO_BUILD_TYPE}" RSTUDIO_BUILD_TYPE)
if(NOT RSTUDIO_BUILD_TYPE STREQUAL "")
  # Capitalize first letter of Build Type
  string(SUBSTRING "${RSTUDIO_BUILD_TYPE}" 0 1 RSTUDIO_BUILD_TYPE_FIRST_CHAR)
  string(TOUPPER "${RSTUDIO_BUILD_TYPE_FIRST_CHAR}" RSTUDIO_BUILD_TYPE_FIRST_CHAR)
  string(SUBSTRING "${RSTUDIO_BUILD_TYPE}" 1 -1 RSTUDIO_BUILD_TYPE_REMAINING)
  string(CONCAT RSTUDIO_BUILD_TYPE "${RSTUDIO_BUILD_TYPE_FIRST_CHAR}" "${RSTUDIO_BUILD_TYPE_REMAINING}")
else()
  file(STRINGS "${RSTUDIO_PROJECT_ROOT}/version/BUILDTYPE" RSTUDIO_BUILD_TYPE LIMIT_COUNT 1)
endif()
string(STRIP "${RSTUDIO_BUILD_TYPE}" RSTUDIO_BUILD_TYPE)
