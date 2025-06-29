#
# codesign-package-electron.cmake
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
#

cmake_minimum_required(VERSION 3.19)

# CMake's message is suppressed during install stage so just use echo here
function(echo MESSAGE)
   execute_process(COMMAND echo "-- ${MESSAGE}")
endfunction()

# flags to pass to codesign executable
set(CODESIGN_FLAGS
   --options runtime
   --timestamp
   --entitlements "@CMAKE_CURRENT_SOURCE_DIR@/entitlements-electron.plist"
   --force
   --deep)

# NOTE: we always attempt to sign a package build of RStudio
# (even if it's just a development build) as our usages of
# install_name_tool will invalidate existing signatures on
# bundled libraries and macOS will refuse to launch RStudio
# with the older invalid signature
if(@RSTUDIO_CODESIGN_USE_CREDENTIALS@)
   echo("codesign: using RStudio's credentials")
   list(APPEND CODESIGN_FLAGS
      -s 69999394E6FEEBDAE8A4DC789BD851A52273D543
      -i ai.lotas.rao)
else()
   echo("codesign: using ad-hoc signature")
   list(APPEND CODESIGN_FLAGS -s -)
endif()

execute_process(
   COMMAND
      "@CMAKE_CURRENT_SOURCE_DIR@/scripts/codesign-package.sh"
      "@CMAKE_INSTALL_PREFIX@/Rao.app"
      ${CODESIGN_FLAGS}
   WORKING_DIRECTORY
      "@CMAKE_INSTALL_PREFIX@"
   OUTPUT_VARIABLE CODESIGN_OUTPUT ECHO_OUTPUT_VARIABLE
   ERROR_VARIABLE CODESIGN_ERROR ECHO_ERROR_VARIABLE
   COMMAND_ERROR_IS_FATAL ANY
)

