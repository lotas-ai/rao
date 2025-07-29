::
:: make-auto-update-files.bat
::
:: Copyright (C) 2025 by Lotas
::
:: Creates Windows auto-update files (.nupkg and RELEASES) compatible with update-electron-app
::
@echo off
setlocal

if "%1" == "--help" goto :showhelp
if "%1" == "-h" goto :showhelp
if "%1" == "help" goto :showhelp
if "%1" == "/?" goto :showhelp

REM Get parameters from calling script or use defaults
if "%PACKAGE_DIR%" == "" set PACKAGE_DIR=%~dp0
if "%BUILD_DIR%" == "" set BUILD_DIR=build
if "%CMAKE_BUILD_TYPE%" == "" set CMAKE_BUILD_TYPE=RelWithDebInfo
if "%CMAKE_BUILD_TYPE%" == "Debug" set BUILD_DIR=build-debug
if "%RSTUDIO_VERSION_FULL%" == "" set RSTUDIO_VERSION_FULL=9999.99.9-dev+1

echo Creating Windows auto-update files...
echo DEBUG: PACKAGE_DIR=%PACKAGE_DIR%
echo DEBUG: BUILD_DIR=%BUILD_DIR%
echo DEBUG: VERSION=%RSTUDIO_VERSION_FULL%

REM Find the Electron app directory
set "ELECTRON_APP_DIR=%BUILD_DIR%\out\Rao-win32-x64"

if not exist "%ELECTRON_APP_DIR%" (
    echo ERROR: Electron app directory not found at "%ELECTRON_APP_DIR%"
    echo Make sure the main build completed successfully first.
    goto :error
)

echo Found Electron app at: %ELECTRON_APP_DIR%

REM Change to desktop directory for npm operations
pushd "%PACKAGE_DIR%\..\..\src\node\desktop"

REM Install electron-installer-windows if not present
if not exist "node_modules\electron-installer-windows" (
    echo Installing electron-installer-windows...
    call npm install electron-installer-windows --save-dev
)

REM Create auto-update packages using electron-installer-windows
echo Creating auto-update packages...
node -e "
const installer = require('electron-installer-windows');
const path = require('path');

const options = {
  src: '%ELECTRON_APP_DIR%',
  dest: '%BUILD_DIR%',
  name: 'rao',
  productName: 'Rao', 
  version: '%RSTUDIO_VERSION_FULL%',
  description: 'Rao',
  authors: ['Lotas'],
  exe: 'rao.exe',
  icon: path.join('%ELECTRON_APP_DIR%', 'resources', 'app', 'resources', 'icons', 'Rao.ico'),
  noMsi: true,
  remoteReleases: 'https://lotas-downloads.s3.us-east-2.amazonaws.com/win32/x64'
};

console.log('Creating Windows installer with options:', JSON.stringify(options, null, 2));

installer(options).then(() => {
  console.log('Windows auto-update files created successfully');
}).catch((err) => {
  console.error('Windows auto-update creation failed:', err);
  process.exit(1);
});
"

if errorlevel 1 (
    echo ERROR: Failed to create auto-update files
    popd
    goto :error
)

popd

echo Windows auto-update files created successfully in %BUILD_DIR%
goto :EOF

:showhelp
echo.
echo make-auto-update-files
echo.
echo. Creates Windows auto-update files (.nupkg and RELEASES) for Squirrel.Windows
echo. 
echo. Environment variables:
echo.     PACKAGE_DIR      - Package directory (default: current directory)
echo.     BUILD_DIR        - Build directory (default: build) 
echo.     RSTUDIO_VERSION_FULL - Version string (default: 9999.99.9-dev+1)
echo.
echo. Must be run after the main Electron app has been built.
echo.
exit /b 0

:error
echo ERROR: Failed to create Windows auto-update files!
exit /b 1 