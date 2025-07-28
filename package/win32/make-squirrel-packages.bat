::
:: make-squirrel-packages.bat
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
setlocal EnableDelayedExpansion

if "%1" == "--help" goto :showhelp
if "%1" == "-h" goto :showhelp
if "%1" == "help" goto :showhelp
if "%1" == "/?" goto :showhelp

if "%PACKAGE_DIR%" == "" set PACKAGE_DIR=%~dp0
if "%BUILD_DIR%" == "" set BUILD_DIR=build
if "%CMAKE_BUILD_TYPE%" == "" set CMAKE_BUILD_TYPE=RelWithDebInfo
if "%CMAKE_BUILD_TYPE%" == "Debug" set BUILD_DIR=build-debug

echo Creating Squirrel.Windows packages for auto-updates...
echo BUILD_DIR: %BUILD_DIR%

if not exist %BUILD_DIR% (
    echo ERROR: Build directory not found at "%BUILD_DIR%"
    echo Run make-package.bat first to create the build files
    goto :error
)

pushd %BUILD_DIR%
set "BUILD_DIR=%CD%"

REM Find the Electron app directory - try ZIP extraction first
set "ELECTRON_APP_DIR="
set "TEMP_DIR=%BUILD_DIR%\temp-electron-app"

REM Extract the ZIP file to get the Electron app
for %%f in ("%BUILD_DIR%\*.zip") do (
    echo Found ZIP file: %%f
    mkdir "!TEMP_DIR!" 2>NUL
    7z x "%%f" "-o!TEMP_DIR!" -y >NUL
    goto :check_extraction
)

:check_extraction
REM Check if extraction was successful
if exist "%TEMP_DIR%\rao.exe" (
    set "ELECTRON_APP_DIR=%TEMP_DIR%"
    echo Found Electron app at: %ELECTRON_APP_DIR%
) else (
    echo ERROR: Could not find rao.exe in extracted ZIP
    echo Make sure you have a ZIP file in the build directory
    goto :error
)

if exist "%ELECTRON_APP_DIR%\rao.exe" (
    REM Use electron-winstaller to create Squirrel packages
    pushd "%PACKAGE_DIR%\..\..\src\node\desktop"
    
    REM Find Node.js path dynamically  
    set "NODE_PATH="
    if exist "..\..\..\dependencies\common\node\22.13.1\node.exe" (
        set "NODE_PATH=..\..\..\dependencies\common\node\22.13.1\node.exe"
    ) else (
        REM Try to find node in PATH
        where node >nul 2>&1
        if not ERRORLEVEL 1 (
            set "NODE_PATH=node"
        ) else (
            echo ERROR: Node.js not found
            echo Please ensure Node.js is installed and in PATH
            popd
            goto :error
        )
    )
    
    echo Using Node.js at: !NODE_PATH!
    
    REM Build version string from environment variables
    set "VERSION_FULL="
    if defined RSTUDIO_VERSION_MAJOR (
        set "VERSION_FULL=!RSTUDIO_VERSION_MAJOR!"
        if defined RSTUDIO_VERSION_MINOR (
            set "VERSION_FULL=!RSTUDIO_VERSION_MAJOR!.!RSTUDIO_VERSION_MINOR!"
            if defined RSTUDIO_VERSION_PATCH (
                set "VERSION_FULL=!RSTUDIO_VERSION_MAJOR!.!RSTUDIO_VERSION_MINOR!.!RSTUDIO_VERSION_PATCH!"
                if defined RSTUDIO_VERSION_SUFFIX (
                    set "VERSION_FULL=!RSTUDIO_VERSION_MAJOR!.!RSTUDIO_VERSION_MINOR!.!RSTUDIO_VERSION_PATCH!!RSTUDIO_VERSION_SUFFIX!"
                )
            )
        )
    )
    
    if defined VERSION_FULL (
        echo Using version: !VERSION_FULL!
        REM Create Squirrel packages using separate script with version
        "!NODE_PATH!" "create-squirrel-packages.js" "%ELECTRON_APP_DIR%" "%BUILD_DIR%\squirrel" "!VERSION_FULL!"
    ) else (
        echo No version information found, using default from package.json
        REM Create Squirrel packages using separate script without version
        "!NODE_PATH!" "create-squirrel-packages.js" "%ELECTRON_APP_DIR%" "%BUILD_DIR%\squirrel"
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
        ) else (
            echo Note: Setup.exe not found in squirrel directory
        )
        echo Squirrel auto-update files created successfully!
        echo.
        echo Generated files:
        dir "%BUILD_DIR%\*.nupkg"
        if exist "%BUILD_DIR%\RELEASES" echo RELEASES file created
        if exist "%BUILD_DIR%\RaoSetup-Squirrel.exe" echo RaoSetup-Squirrel.exe created
    )
    
    REM Cleanup temp directory
    if exist "%TEMP_DIR%" (
        rmdir /s /q "%TEMP_DIR%" 2>NUL
    )
) else (
    echo ERROR: Electron app directory not found
    goto :error
)

popd
endlocal
echo.
echo Squirrel package generation completed successfully!
goto :EOF

:showhelp
echo.
echo make-squirrel-packages
echo.
echo. Creates Squirrel.Windows auto-update packages (.nupkg, RELEASES, Setup.exe)
echo. from existing build files created by make-package.bat
echo.
echo  Must be invoked from the "package\win32" folder after running make-package.bat
echo.
echo  Prerequisites:
echo    - ZIP file must exist in build directory (created by make-package.bat)
echo    - Node.js and electron-winstaller must be available
echo.
exit /b 0

:error
echo ERROR: Failed to create Squirrel packages!
popd
endlocal
exit /b 1 