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

REM Set up dependencies path if not already set
if not defined RSTUDIO_DEPENDENCIES (
    if not exist c:\rstudio-tools\dependencies (
        set RSTUDIO_DEPENDENCIES=%PACKAGE_DIR%..\..\dependencies
    ) else (
        set RSTUDIO_DEPENDENCIES=c:\rstudio-tools\dependencies
    )
)

REM Call rstudio-tools if needed to get RSTUDIO_NODE_VERSION
if not defined RSTUDIO_NODE_VERSION (
    call %RSTUDIO_DEPENDENCIES%\tools\rstudio-tools.cmd
)

REM Find node tools.
set NODE_DIR=%RSTUDIO_DEPENDENCIES%\common\node\%RSTUDIO_NODE_VERSION%
set NODE=%NODE_DIR%\node.exe
if not exist %NODE% (
    echo node.exe not found at %NODE_DIR%; exiting
    endlocal
    exit /b 1
)
echo Using node: %NODE%

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
    
    REM Find the Electron app directory using absolute path
    set "ELECTRON_APP_DIR=%PACKAGE_DIR%..\..\src\node\desktop\out\Rao-win32-x64"
    if exist "%ELECTRON_APP_DIR%\rao.exe" (
        echo Found Electron app at: %ELECTRON_APP_DIR%
        
        REM Always copy RStudio binaries into Electron app (create directory structure)
        echo Copying RStudio binaries to Electron app...
        if not exist "%ELECTRON_APP_DIR%\resources\app\bin" mkdir "%ELECTRON_APP_DIR%\resources\app\bin"
        copy "%BUILD_DIR%\src\cpp\session\rsession.exe" "%ELECTRON_APP_DIR%\resources\app\bin\rsession.exe"
        if exist "%BUILD_DIR%\src\cpp\server\rserver.exe" (
            copy "%BUILD_DIR%\src\cpp\server\rserver.exe" "%ELECTRON_APP_DIR%\resources\app\bin\rserver.exe"
        )
        if exist "%BUILD_DIR%\src\cpp\diagnostics\diagnostics.exe" (
            copy "%BUILD_DIR%\src\cpp\diagnostics\diagnostics.exe" "%ELECTRON_APP_DIR%\resources\app\bin\diagnostics.exe"
        )
        
        REM Copy script to desktop directory where node_modules exists
        set "DESKTOP_DIR=%PACKAGE_DIR%..\..\src\node\desktop"
        copy "%PACKAGE_DIR%create-squirrel-packages.js" "%DESKTOP_DIR%\create-squirrel-packages-temp.js" >nul
        
        REM Change to desktop directory
        pushd "%DESKTOP_DIR%"
        
        REM Verify we're in the right directory
        echo Current directory: %CD%
        
        REM BUILD_DIR is already absolute, just use it directly
        REM Run the script from the desktop directory
        "%NODE%" create-squirrel-packages-temp.js "%BUILD_DIR%"
        if ERRORLEVEL 1 (
            REM Clean up temporary file
            del create-squirrel-packages-temp.js
            popd
            echo ERROR: Squirrel package creation failed
            goto :error
        )
        
        REM Clean up temporary file
        del create-squirrel-packages-temp.js
        
        REM Copy the squirrel output from desktop build to package build
        if exist "build\squirrel" (
            echo Copying squirrel packages to package build directory...
            xcopy /E /I /Y "build\squirrel" "%BUILD_DIR%\squirrel"
        )
        
        popd
        
        REM Move Squirrel files to build directory
        if exist "%BUILD_DIR%\squirrel" (
            echo Moving Squirrel packages to build directory...
            move "%BUILD_DIR%\squirrel\*.nupkg" "%BUILD_DIR%\"
            move "%BUILD_DIR%\squirrel\RELEASES" "%BUILD_DIR%\"
            if exist "%BUILD_DIR%\squirrel\RaoSetup.exe" (
                move "%BUILD_DIR%\squirrel\RaoSetup.exe" "%BUILD_DIR%\RaoSetup-Squirrel.exe"
            ) else if exist "%BUILD_DIR%\squirrel\Setup.exe" (
                move "%BUILD_DIR%\squirrel\Setup.exe" "%BUILD_DIR%\RaoSetup-Squirrel.exe"
            )
            echo Squirrel auto-update files created successfully
        ) else (
            echo ERROR: Squirrel directory was not created at %BUILD_DIR%\squirrel
            goto :error
        )
    ) else (
        popd
        echo WARNING: Electron app directory not found at %PACKAGE_DIR%..\..\src\node\desktop\out\Rao-win32-x64
        echo Skipping Squirrel package generation
    )
)

:skip_squirrel

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
