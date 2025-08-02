@echo off
REM make-dist-packages.bat
REM
REM Copyright (C) 2025 by Lotas Inc.
REM
REM This program is licensed to you under the terms of version 3 of the
REM GNU Affero General Public License. This program is distributed WITHOUT
REM ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
REM MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
REM AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.

setlocal EnableDelayedExpansion
set "PACKAGE_DIR=%CD%"

if not exist c:\rstudio-tools\dependencies (
    set RSTUDIO_DEPENDENCIES=%PACKAGE_DIR%\..\..\dependencies
) else (
    set RSTUDIO_DEPENDENCIES=c:\rstudio-tools\dependencies
)

call %RSTUDIO_DEPENDENCIES%\tools\rstudio-tools.cmd

%RUN% normalize-path "%PACKAGE_DIR%\..\..\src\node\desktop" ELECTRON_SOURCE_DIR

REM Check if we're in the right directory
if not exist "build\Rao-*.exe" (
    echo Error: No Rao installer found in build directory
    echo Please run make-package.bat first
    exit /b 1
)

REM Get the version from the installer filename
for %%f in (build\Rao-*.exe) do (
    set "INSTALLER_FILE=%%f"
    set "INSTALLER_NAME=%%~nf"
    set "VERSION=!INSTALLER_NAME:Rao-=!"
)

echo Creating Squirrel.Windows packages for version !VERSION!

REM Create output directory for packages
set "PACKAGES_DIR=!PACKAGE_DIR!\packages"
if not exist "!PACKAGES_DIR!" mkdir "!PACKAGES_DIR!"

REM Find node tools (same as make-package.bat)
set NODE_DIR=%RSTUDIO_DEPENDENCIES%\common\node\%RSTUDIO_NODE_VERSION%
set NODE=%NODE_DIR%\node.exe
if not exist %NODE% (
    echo node.exe not found at %NODE_DIR%; exiting
    exit /b 1
)
echo Using node: %NODE%

set NPM=%NODE_DIR%\npm
if not exist %NPM% (
    echo npm not found at %NPM%; exiting
    exit /b 1
)
echo Using npm: %NPM%

set NPX=%NODE_DIR%\npx
if not exist %NPX% (
    echo npx not found at %NPX%; exiting
    exit /b 1
)
echo Using npx: %NPX%

REM Put node on the path
set PATH=%NODE_DIR%;%PATH%

REM Check if npm packages are already installed to save time
set NPM_INSTALLED=0
pushd %ELECTRON_SOURCE_DIR%

REM Check if node_modules exists and has the required packages
if exist node_modules\@electron-forge\maker-squirrel (
    echo Dependencies already installed, skipping npm install...
    set NPM_INSTALLED=1
) else (
    echo Installing npm dependencies...
    REM Set NODE env var for npm scripts instead of modifying PATH
    set "NODE=%NODE_DIR%\node.exe"
    
    if exist package-lock.json (
        echo Found package-lock.json, using npm ci for reproducible install...
        call %NPM% ci
    ) else (
        echo Using npm install...
        call %NPM% install
    )
    
    if ERRORLEVEL 1 (
        echo Error: Failed to install npm dependencies
        popd
        exit /b 1
    )
    set NPM_INSTALLED=1
)
popd

REM Run CMake install to ensure all binaries are in the CPack directory
echo Running CMake install to prepare binaries...
pushd %PACKAGE_DIR%\build
cmake --install . --config Release
if ERRORLEVEL 1 (
    echo Error: CMake install failed
    popd
    exit /b 1
)
popd

REM Create Squirrel.Windows packages using electron-forge
echo Creating Squirrel.Windows packages...

REM Switch to the desktop directory to run electron-forge
echo Running @electron-forge/maker-squirrel...
pushd %ELECTRON_SOURCE_DIR%

REM Set PKG_TEMP_DIR environment variable for electron-forge
set PKG_TEMP_DIR=C:\rsbuild
echo Using PKG_TEMP_DIR: %PKG_TEMP_DIR%

REM Run electron-forge make to create the Squirrel.Windows packages
echo Running electron-forge make...
call %NPX% electron-forge make --platform=win32 --arch=x64

REM Copy the generated files to our packages directory
if exist "out\make\squirrel.windows\x64" (
    echo Copying generated files to %PACKAGES_DIR%...
    xcopy "out\make\squirrel.windows\x64\*.nupkg" "%PACKAGES_DIR%\" /Y
    xcopy "out\make\squirrel.windows\x64\RELEASES" "%PACKAGES_DIR%\" /Y
    if exist "out\make\squirrel.windows\x64\*.exe" (
        xcopy "out\make\squirrel.windows\x64\*.exe" "%PACKAGES_DIR%\" /Y
    )
    echo Files copied successfully.
) else (
    echo Warning: No output directory found at out\make\squirrel.windows\x64
    echo Checking for other output directories...
    dir "out\make\" /b /ad
)

popd

if ERRORLEVEL 1 (
    echo Error: Failed to create Squirrel.Windows packages
    exit /b 1
)

echo.
echo Squirrel.Windows packages created in %PACKAGES_DIR%
echo.
echo Files created:
if exist "%PACKAGES_DIR%\*.nupkg" (
    dir "%PACKAGES_DIR%\*.nupkg" /b
) else (
    echo No .nupkg files found
)
if exist "%PACKAGES_DIR%\RELEASES" (
    echo RELEASES file created
) else (
    echo No RELEASES file found
)
echo.
echo These packages can be used for auto-updates with the update-electron-app system.
echo.

endlocal 