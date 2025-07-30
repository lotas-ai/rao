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
echo DEBUG: ZIP_FILE=%ZIP_FILE%
echo DEBUG: OUTPUT_DIR=%OUTPUT_DIR%
echo DEBUG: VERSION=%VERSION%

REM Create output directory if it doesn't exist
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

REM Create temporary extraction directory
set "TEMP_DIR=%OUTPUT_DIR%\temp_extract"
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"
mkdir "%TEMP_DIR%"

REM Extract ZIP file using tar
echo Extracting ZIP file...
tar -xf "%ZIP_FILE%" -C "%TEMP_DIR%"
if errorlevel 1 (
    echo ERROR: Failed to extract ZIP file
    goto :cleanup
)

REM Find the Electron app directory in extracted files
REM First check if rao.exe is directly in the extracted root
if exist "%TEMP_DIR%\rao.exe" (
    set "ELECTRON_APP_DIR=%TEMP_DIR%"
    goto :found_app
)

REM If not in root, check subdirectories
for /d %%D in ("%TEMP_DIR%\*") do (
    if exist "%%D\rao.exe" (
        set "ELECTRON_APP_DIR=%%D"
        goto :found_app
    )
)

echo ERROR: Could not find rao.exe in extracted ZIP file
goto :cleanup

:found_app
echo Found Electron app at: %ELECTRON_APP_DIR%

REM Verify the Electron app structure
if not exist "%ELECTRON_APP_DIR%\resources\app\package.json" (
    echo ERROR: Invalid Electron app structure - missing resources\app\package.json
    goto :cleanup
)

echo Electron app structure verified

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

REM Create auto-update packages using electron-installer-windows (no remote sync for first run)
echo Creating auto-update packages...
node -e "const installer = require('electron-installer-windows'); const options = { src: '%ELECTRON_APP_DIR%', dest: '%OUTPUT_DIR%', name: 'rao', productName: 'Rao', version: '%VERSION%', description: 'Rao', authors: ['Lotas'], exe: 'rao.exe', noMsi: true }; console.log('Creating Squirrel packages...'); installer(options).then(() => { console.log('SUCCESS: Squirrel packages created successfully'); }).catch((err) => { console.error('ERROR:', err.message); process.exit(1); });"

if errorlevel 1 (
    echo ERROR: Failed to create auto-update files
    popd
    goto :cleanup
)

popd

:cleanup
REM Clean up temporary directory
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"

echo.
echo SUCCESS: Squirrel files created successfully in %OUTPUT_DIR%
echo Files created:
echo   - rao-%VERSION%-full.nupkg
echo   - rao-%VERSION%-setup.exe  
echo   - RELEASES
echo.
echo NOTE: Upload these files to your release server before enabling auto-updates
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
echo     zip-to-squirrel "build\Rao-0.2.9.zip"
echo     zip-to-squirrel "build\Rao-0.2.9.zip" "build" "0.2.9"
echo.
exit /b 0

:error
echo ERROR: Failed to create squirrel files!
exit /b 1 