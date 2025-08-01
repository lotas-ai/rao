::
:: make-dist-packages.bat
::
:: Copyright (C) 2025 by Posit Software, PBC
::
:: Unless you have received this program directly from Posit Software pursuant
:: to the terms of a commercial license agreement with Posit Software, then
:: this program is licensed to you under the terms of version 3 of the
:: GNU Affero General Public License. This program is distributed WITHOUT
:: ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
:: MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
:: AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
::
@echo off
setlocal

if "%1" == "--help" goto :showhelp
if "%1" == "-h" goto :showhelp
if "%1" == "help" goto :showhelp
if "%1" == "/?" goto :showhelp

if "%PACKAGE_DIR%" == "" set PACKAGE_DIR=%~dp0
if "%BUILD_DIR%" == "" set BUILD_DIR=build
if "%CMAKE_BUILD_TYPE%" == "" set CMAKE_BUILD_TYPE=RelWithDebInfo
if "%CMAKE_BUILD_TYPE%" == "Debug" set BUILD_DIR=build-debug
if "%PKG_TEMP_DIR%" == "" set PKG_TEMP_DIR=C:/rsbuild

echo DEBUG: make-dist-packages.bat using following values:
echo DEBUG:     PACKAGE_DIR=%PACKAGE_DIR%
echo DEBUG:     BUILD_DIR=%BUILD_DIR%
echo DEBUG:     CMAKE_BUILD_TYPE=%CMAKE_BUILD_TYPE%
echo DEBUG:     PKG_TEMP_DIR=%PKG_TEMP_DIR%

if not exist %BUILD_DIR% (
    echo ERROR: Build directory not found at "%BUILD_DIR%"
    goto :error
)

pushd %BUILD_DIR%
set "BUILD_DIR=%CD%"

if not defined QUICK (
    echo Creating NSIS setup package...
    cpack -C "%CMAKE_BUILD_TYPE%" -G NSIS
    REM emit NSIS error output if present
    if exist "%PKG_TEMP_DIR%\_CPack_Packages\win64\NSIS\NSISOutput.log" type "%PKG_TEMP_DIR%\_CPack_Packages\win64\NSIS\NSISOutput.log"
    if not defined RSTUDIO_DOCKER_DEVELOPMENT_BUILD (
        move "%PKG_TEMP_DIR%\*.exe" "%BUILD_DIR%"
    )
)

if not defined NOZIP (
    if "%CMAKE_BUILD_TYPE%" == "RelWithDebInfo" (
        echo Creating ZIP package...
        cpack -C "%CMAKE_BUILD_TYPE%" -G ZIP
        if not defined RSTUDIO_DOCKER_DEVELOPMENT_BUILD (
            move "%PKG_TEMP_DIR%\*.zip" "%BUILD_DIR%"
        )
    )
)

REM Generate Squirrel.Windows packages for auto-updates
if not defined NOSQUIRREL (
    echo Creating Squirrel.Windows packages for auto-updates...
    
    REM Find the Electron app directory (should be in the build output)
    echo DEBUG: PACKAGE_DIR = %PACKAGE_DIR%
    echo DEBUG: Current directory before navigation: %CD%
    
    REM Build the desktop path manually
    set "DESKTOP_PATH=%PACKAGE_DIR%\..\..\src\node\desktop"
    echo DEBUG: Trying to navigate to: %DESKTOP_PATH%
    
    REM Test if the path exists before trying to navigate
    if exist "%DESKTOP_PATH%" (
        echo DEBUG: Desktop path exists, attempting navigation
        pushd "%DESKTOP_PATH%"
        echo DEBUG: Desktop base directory after pushd: %CD%
        
        REM Check if out directory exists using relative path
        if exist "out" (
            echo DEBUG: Out directory exists
            if exist "out\Rao-win32-x64" (
                set "ELECTRON_APP_DIR=%CD%\out\Rao-win32-x64"
                echo DEBUG: Found Electron app at: %ELECTRON_APP_DIR%
            ) else (
                echo DEBUG: Out directory exists but Rao-win32-x64 not found
                echo DEBUG: Contents of out directory:
                dir /b "out"
                set "ELECTRON_APP_DIR="
            )
        ) else (
            echo DEBUG: Out directory does not exist
            set "ELECTRON_APP_DIR="
        )
        
        popd
    ) else (
        echo DEBUG: Desktop path does not exist: %DESKTOP_PATH%
        echo DEBUG: Let's check what exists step by step...
        if exist "%PACKAGE_DIR%\..\.." (
            echo DEBUG: rao root exists
            if exist "%PACKAGE_DIR%\..\..\src" (
                echo DEBUG: src exists
                if exist "%PACKAGE_DIR%\..\..\src\node" (
                    echo DEBUG: node exists  
                    dir /b "%PACKAGE_DIR%\..\..\src\node"
                ) else (
                    echo DEBUG: node does not exist
                )
            ) else (
                echo DEBUG: src does not exist
            )
        ) else (
            echo DEBUG: rao root does not exist
        )
        set "ELECTRON_APP_DIR="
    )
    
    echo DEBUG: Final ELECTRON_APP_DIR = %ELECTRON_APP_DIR%
    if exist "%ELECTRON_APP_DIR%" (
        echo Found Electron app at: %ELECTRON_APP_DIR%
        
        REM Use existing create-squirrel-packages.js script
        node "%PACKAGE_DIR%create-squirrel-packages.js" "%BUILD_DIR%"
        
        REM Move Squirrel files to build directory
        if exist "%BUILD_DIR%\squirrel" (
            echo Moving Squirrel packages to build directory...
            move "%BUILD_DIR%\squirrel\*.nupkg" "%BUILD_DIR%\"
            move "%BUILD_DIR%\squirrel\RELEASES" "%BUILD_DIR%\"
            move "%BUILD_DIR%\squirrel\Setup.exe" "%BUILD_DIR%\RaoSetup-Squirrel.exe"
            echo Squirrel auto-update files created successfully
        )
    ) else (
        echo WARNING: Electron app directory not found at %ELECTRON_APP_DIR%
        echo Skipping Squirrel package generation
    )
)

popd

endlocal
goto :EOF

:showhelp
echo.
echo make-dist-packages
echo.
echo. Produces the RStudio setup package, zip file (installerless), and Squirrel.Windows 
echo. auto-update packages using already-built binaries.
echo.
echo  Must be invoked from the "package\win32" folder (in the cloned RStudio repository).
echo  Use "set NOSQUIRREL=1" to skip Squirrel.Windows package generation.
echo.
exit /b 0

:error
echo ERROR: Failed to package RStudio!
exit /b 1
