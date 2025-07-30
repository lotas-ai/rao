::
:: zip-to-squirrel.bat
::
:: Copyright (C) 2025 by Lotas
::
:: Creates Windows auto-update files (.nupkg and RELEASES) from a ZIP file
::
@echo off
setlocal enabledelayedexpansion

if "%1" == "--help" goto :showhelp
if "%1" == "-h" goto :showhelp
if "%1" == "help" goto :showhelp
if "%1" == "/?" goto :showhelp

REM Get parameters
set "ZIP_FILE=%~1"
set "OUTPUT_DIR=%~2"
set "VERSION=%~3"

REM Set defaults and convert to absolute paths
if "%ZIP_FILE%" == "" (
    echo ERROR: ZIP file path is required
    goto :showhelp
)
if "%OUTPUT_DIR%" == "" set "OUTPUT_DIR=%~dp0build"
if "%VERSION%" == "" set "VERSION=0.2.9"

REM Convert to absolute paths
for %%i in ("%ZIP_FILE%") do set "ZIP_FILE=%%~fi"
for %%i in ("%OUTPUT_DIR%") do set "OUTPUT_DIR=%%~fi"

REM Validate inputs
if not exist "%ZIP_FILE%" (
    echo ERROR: ZIP file not found at "%ZIP_FILE%"
    goto :error
)

echo Creating squirrel files from ZIP...
echo ZIP_FILE: %ZIP_FILE%
echo OUTPUT_DIR: %OUTPUT_DIR%
echo VERSION: %VERSION%

REM Create output directory if it doesn't exist
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

REM Create temporary extraction directory
set "TEMP_DIR=%OUTPUT_DIR%\temp_extract_%RANDOM%"
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"
mkdir "%TEMP_DIR%"

REM Extract ZIP file
echo Extracting ZIP file...
powershell -command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%TEMP_DIR%' -Force"
if errorlevel 1 (
    echo ERROR: Failed to extract ZIP file
    goto :cleanup
)

REM Find the Electron app directory and executable
set "ELECTRON_APP_DIR="
set "EXE_NAME="

REM Check if exe is directly in the extracted root
if exist "%TEMP_DIR%\rao.exe" (
    set "ELECTRON_APP_DIR=%TEMP_DIR%"
    set "EXE_NAME=rao.exe"
    goto :found_app
)

REM Check for other possible exe names in root
for %%F in ("%TEMP_DIR%\Rao*.exe") do (
    set "ELECTRON_APP_DIR=%TEMP_DIR%"
    set "EXE_NAME=%%~nxF"
    goto :found_app
)

if exist "%TEMP_DIR%\RStudio.exe" (
    set "ELECTRON_APP_DIR=%TEMP_DIR%"
    set "EXE_NAME=RStudio.exe"
    goto :found_app
)

REM If not in root, check first level subdirectories
for /d %%D in ("%TEMP_DIR%\*") do (
    if exist "%%D\rao.exe" (
        set "ELECTRON_APP_DIR=%%D"
        set "EXE_NAME=rao.exe"
        goto :found_app
    )
    for %%F in ("%%D\Rao*.exe") do (
        set "ELECTRON_APP_DIR=%%D"
        set "EXE_NAME=%%~nxF"
        goto :found_app
    )
    if exist "%%D\RStudio.exe" (
        set "ELECTRON_APP_DIR=%%D"
        set "EXE_NAME=RStudio.exe"
        goto :found_app
    )
)

echo ERROR: Could not find any recognizable executable in extracted ZIP file
echo Looking for: rao.exe, Rao*.exe, or RStudio.exe
goto :cleanup

:found_app
echo Found Electron app at: %ELECTRON_APP_DIR%
echo Found executable: %EXE_NAME%

REM Verify the Electron app structure
if not exist "%ELECTRON_APP_DIR%\resources\app\package.json" (
    echo ERROR: Invalid Electron app structure - missing resources\app\package.json
    echo App directory: %ELECTRON_APP_DIR%
    echo Contents:
    dir "%ELECTRON_APP_DIR%" /b
    goto :cleanup
)

echo Electron app structure verified

REM Change to desktop directory for npm operations
set "DESKTOP_DIR=%~dp0..\..\src\node\desktop"
pushd "%DESKTOP_DIR%"

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

REM Create the Node.js script file for creating auto-update packages
set "SCRIPT_FILE=%OUTPUT_DIR%\create_installer.js"
echo const installer = require('electron-installer-windows'); > "%SCRIPT_FILE%"
echo const path = require('path'); >> "%SCRIPT_FILE%"
echo const fs = require('fs'); >> "%SCRIPT_FILE%"
echo. >> "%SCRIPT_FILE%"
echo const srcPath = String.raw`%ELECTRON_APP_DIR%`; >> "%SCRIPT_FILE%"
echo const destPath = String.raw`%OUTPUT_DIR%`; >> "%SCRIPT_FILE%"
echo. >> "%SCRIPT_FILE%"
echo console.log('Creating Windows installer...'); >> "%SCRIPT_FILE%"
echo console.log('Source:', srcPath); >> "%SCRIPT_FILE%"
echo console.log('Destination:', destPath); >> "%SCRIPT_FILE%"
echo console.log('Executable:', '%EXE_NAME%'); >> "%SCRIPT_FILE%"
echo. >> "%SCRIPT_FILE%"
echo const options = { >> "%SCRIPT_FILE%"
echo   src: srcPath, >> "%SCRIPT_FILE%"
echo   dest: destPath, >> "%SCRIPT_FILE%"
echo   name: 'rao', >> "%SCRIPT_FILE%"
echo   productName: 'Rao', >> "%SCRIPT_FILE%"
echo   version: '%VERSION%', >> "%SCRIPT_FILE%"
echo   description: 'Rao', >> "%SCRIPT_FILE%"
echo   authors: ['Lotas'], >> "%SCRIPT_FILE%"
echo   exe: '%EXE_NAME%', >> "%SCRIPT_FILE%"
echo   noMsi: true, >> "%SCRIPT_FILE%"
echo   remoteReleases: 'https://lotas-downloads.s3.us-east-2.amazonaws.com/win32/x64' >> "%SCRIPT_FILE%"
echo }; >> "%SCRIPT_FILE%"
echo. >> "%SCRIPT_FILE%"
echo installer(options).then(() =^> { >> "%SCRIPT_FILE%"
echo   console.log('Windows auto-update files created successfully'); >> "%SCRIPT_FILE%"
echo   process.exit(0); >> "%SCRIPT_FILE%"
echo }).catch((err) =^> { >> "%SCRIPT_FILE%"
echo   console.error('Windows auto-update creation failed:', err.message); >> "%SCRIPT_FILE%"
echo   console.error('Full error:', err); >> "%SCRIPT_FILE%"
echo   process.exit(1); >> "%SCRIPT_FILE%"
echo }); >> "%SCRIPT_FILE%"

REM Run the installer script
echo Creating auto-update packages...
node "%SCRIPT_FILE%"
set "NODE_EXIT_CODE=%ERRORLEVEL%"

REM Clean up the script file
if exist "%SCRIPT_FILE%" del "%SCRIPT_FILE%"

popd

if %NODE_EXIT_CODE% neq 0 (
    echo ERROR: Failed to create auto-update files
    goto :cleanup
)

:cleanup
REM Clean up temporary directory
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"

if %NODE_EXIT_CODE% neq 0 goto :error

echo.
echo SUCCESS: Squirrel files created in %OUTPUT_DIR%
exit /b 0

:showhelp
echo.
echo zip-to-squirrel ZIP_FILE [OUTPUT_DIR] [VERSION]
echo.
echo     ZIP_FILE     - Path to the ZIP file containing the Electron app
echo     OUTPUT_DIR   - Directory to output squirrel files (default: build)
echo     VERSION      - Version string (default: 0.2.9)
echo.
echo Examples:
echo     zip-to-squirrel "build\Rao-0.2.9.zip"
echo     zip-to-squirrel "build\Rao-0.2.9.zip" "build" "0.2.9"
echo.
exit /b 0

:error
echo ERROR: Failed to create squirrel files!
exit /b 1 