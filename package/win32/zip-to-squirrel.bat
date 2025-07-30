::
:: zip-to-squirrel.bat
::
:: Copyright (C) 2025 by Lotas
::
:: Creates Windows auto-update files (.nupkg and RELEASES) from a ZIP file
::
@echo off
setlocal

if "%1" == "--help" goto :showhelp
if "%1" == "-h" goto :showhelp
if "%1" == "help" goto :showhelp
if "%1" == "/?" goto :showhelp

REM Get parameters
set "ZIP_FILE=%~1"
set "OUTPUT_DIR=%~2"
set "VERSION=%~3"

REM Set defaults
if "%ZIP_FILE%" == "" (
    echo ERROR: ZIP file path is required
    goto :showhelp
)
if "%OUTPUT_DIR%" == "" set "OUTPUT_DIR=%~dp0build"
if "%VERSION%" == "" set "VERSION=0.2.9"

REM Validate inputs
if not exist "%ZIP_FILE%" (
    echo ERROR: ZIP file not found at "%ZIP_FILE%"
    goto :error
)

echo Creating squirrel files from ZIP...
echo DEBUG: ZIP_FILE=%ZIP_FILE%
echo DEBUG: OUTPUT_DIR=%OUTPUT_DIR%
echo DEBUG: VERSION=%VERSION%

REM Create output directory if it doesn't exist
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

REM Create temporary extraction directory
set "TEMP_DIR=%OUTPUT_DIR%\temp_extract"
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"
mkdir "%TEMP_DIR%"

REM Extract ZIP file
echo Extracting ZIP file...
powershell -command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%TEMP_DIR%' -Force"
if errorlevel 1 (
    echo ERROR: Failed to extract ZIP file
    goto :cleanup
)

REM Find the Electron app directory in extracted files
for /d %%D in ("%TEMP_DIR%\*") do (
    REM Look for various possible executable names
    if exist "%%D\rao.exe" (
        set "ELECTRON_APP_DIR=%%D"
        set "EXE_NAME=rao.exe"
        goto :found_app
    )
    if exist "%%D\Rao-*.exe" (
        set "ELECTRON_APP_DIR=%%D"
        REM Find the actual exe name
        for %%F in ("%%D\Rao-*.exe") do set "EXE_NAME=%%~nxF"
        goto :found_app
    )
    if exist "%%D\RStudio.exe" (
        set "ELECTRON_APP_DIR=%%D"
        set "EXE_NAME=RStudio.exe"
        goto :found_app
    )
)

echo ERROR: Could not find any recognizable executable (.exe) in extracted ZIP file
echo Looking for: rao.exe, Rao-*.exe, or RStudio.exe
goto :cleanup

:found_app
echo Found Electron app at: %ELECTRON_APP_DIR%
echo Found executable: %EXE_NAME%

REM Change to desktop directory for npm operations
pushd "%~dp0..\..\src\node\desktop"

REM Install electron-installer-windows if not present
if not exist "node_modules\electron-installer-windows" (
    echo Installing electron-installer-windows...
    call npm install electron-installer-windows --save-dev
    if errorlevel 1 (
        echo ERROR: Failed to install electron-installer-windows
        popd
        goto :cleanup
    )
)

REM Create auto-update packages using electron-installer-windows
echo Creating auto-update packages...
node -e "
const installer = require('electron-installer-windows');
const path = require('path');

const options = {
  src: '%ELECTRON_APP_DIR%',
  dest: '%OUTPUT_DIR%',
  name: 'rao',
  productName: 'Rao', 
  version: '%VERSION%',
  description: 'Rao',
  authors: ['Lotas'],
  exe: '%EXE_NAME%',
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
    goto :cleanup
)

popd

:cleanup
REM Clean up temporary directory
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"

echo Squirrel files created successfully in %OUTPUT_DIR%
goto :EOF

:showhelp
echo.
echo zip-to-squirrel ZIP_FILE [OUTPUT_DIR] [VERSION]
echo.
echo     ZIP_FILE     - Path to the ZIP file containing the Electron app
echo     OUTPUT_DIR   - Directory to output squirrel files (default: build)
echo     VERSION      - Version string (default: 0.2.9)
echo.
echo Examples:
echo     zip-to-squirrel "C:\rsbuild\Rao-0.2.9.zip"
echo     zip-to-squirrel "C:\rsbuild\Rao-0.2.9.zip" "C:\Users\willnickols\rao\package\win32\build" "0.2.9"
echo.
exit /b 0

:error
echo ERROR: Failed to create squirrel files!
exit /b 1 