@echo off
echo ============================================
echo DIAGNOSING SQUIRREL PACKAGE SETUP
echo ============================================
echo.

set PACKAGE_DIR=%~dp0
echo Script directory: %PACKAGE_DIR%
echo.

echo Checking desktop directory structure:
echo -------------------------------------
set DESKTOP_DIR=%PACKAGE_DIR%..\..\src\node\desktop
echo Desktop directory: %DESKTOP_DIR%

if exist "%DESKTOP_DIR%" (
    echo [✓] Desktop directory exists
    
    pushd "%DESKTOP_DIR%"
    echo Current directory: %CD%
    
    echo.
    echo Checking for node_modules:
    if exist "node_modules" (
        echo [✓] node_modules exists
        
        echo.
        echo Checking for electron-winstaller:
        if exist "node_modules\electron-winstaller" (
            echo [✓] electron-winstaller exists
            dir "node_modules\electron-winstaller" | findstr /i "package.json"
        ) else (
            echo [✗] electron-winstaller NOT FOUND in node_modules
        )
        
        echo.
        echo Listing first 10 modules in node_modules:
        dir /B "node_modules" | findstr /n "^" | findstr "^[1-9]:"
        
    ) else (
        echo [✗] node_modules NOT FOUND
        
        echo.
        echo Checking for package.json:
        if exist "package.json" (
            echo [✓] package.json exists
            type package.json | findstr "electron-winstaller"
        ) else (
            echo [✗] package.json NOT FOUND
        )
    )
    
    echo.
    echo Checking for out directory:
    if exist "out" (
        echo [✓] out directory exists
        dir /B "out" 2>nul
    ) else (
        echo [✗] out directory NOT FOUND
    )
    
    popd
) else (
    echo [✗] Desktop directory NOT FOUND
)

echo.
echo Checking build directories:
echo ---------------------------
set BUILD_DIR=%PACKAGE_DIR%..\..\build
if exist "%BUILD_DIR%" (
    echo [✓] Build directory exists at %BUILD_DIR%
    pushd "%BUILD_DIR%"
    
    echo.
    echo Checking for desktop-build directories:
    dir /B /AD | findstr "desktop"
    
    popd
) else (
    echo [✗] Build directory NOT FOUND at %BUILD_DIR%
)

echo.
echo Checking for alternate node_modules locations:
echo ----------------------------------------------
echo Checking %PACKAGE_DIR%node_modules:
if exist "%PACKAGE_DIR%node_modules" (
    echo [✓] Found node_modules in package\win32
    if exist "%PACKAGE_DIR%node_modules\electron-winstaller" (
        echo [✓] electron-winstaller found here
    )
) else (
    echo [✗] No node_modules in package\win32
)

echo.
echo Checking %PACKAGE_DIR%..\node_modules:
if exist "%PACKAGE_DIR%..\node_modules" (
    echo [✓] Found node_modules in package
    if exist "%PACKAGE_DIR%..\node_modules\electron-winstaller" (
        echo [✓] electron-winstaller found here
    )
) else (
    echo [✗] No node_modules in package
)

echo.
echo Checking current Node.js module resolution:
echo -------------------------------------------
where node >nul 2>&1
if %ERRORLEVEL% == 0 (
    echo Node.js is on PATH
    node -e "console.log('NODE_PATH:', process.env.NODE_PATH || '(not set)')"
    node -e "console.log('Module paths:', module.paths)"
) else (
    echo Node.js is NOT on PATH
)

echo.
echo ============================================
echo DIAGNOSIS COMPLETE
echo ============================================
pause