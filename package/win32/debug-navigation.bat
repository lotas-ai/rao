::
:: debug-navigation.bat
::
:: Focused debugging script to test Squirrel.Windows section logic
::
@echo off
setlocal

echo =================================================================
echo SQUIRREL.WINDOWS SECTION DIAGNOSTIC SCRIPT
echo =================================================================
echo.

:: Initialize the same variables as make-dist-packages.bat
if "%PACKAGE_DIR%" == "" set PACKAGE_DIR=%~dp0
if "%BUILD_DIR%" == "" set BUILD_DIR=build
if "%CMAKE_BUILD_TYPE%" == "" set CMAKE_BUILD_TYPE=RelWithDebInfo
if "%CMAKE_BUILD_TYPE%" == "Debug" set BUILD_DIR=build-debug

echo Initial Setup:
echo ==============
echo PACKAGE_DIR=%PACKAGE_DIR%
echo BUILD_DIR=%BUILD_DIR%
echo CMAKE_BUILD_TYPE=%CMAKE_BUILD_TYPE%
echo Current directory: %CD%
echo.

echo Testing Squirrel.Windows Section Logic (from make-dist-packages.bat)
echo =====================================================================

echo Testing NOSQUIRREL environment variable...
if not defined NOSQUIRREL (
    echo ✓ NOSQUIRREL is not defined - will proceed with Squirrel package generation
    
    echo Creating Squirrel.Windows packages for auto-updates...
    
    REM Find the Electron app directory (should be in the build output)
    set "ELECTRON_APP_DIR=%PACKAGE_DIR%..\..\src\node\desktop\out\Rao-win32-x64"
    
    echo Testing ELECTRON_APP_DIR path: %ELECTRON_APP_DIR%
    
    if exist "%ELECTRON_APP_DIR%" (
        echo ✓ Found Electron app at: %ELECTRON_APP_DIR%
        echo ✓ The Squirrel.Windows section logic appears to be working correctly
        
    ) else (
        echo ✗ WARNING: Electron app directory not found at %ELECTRON_APP_DIR%
        echo ✗ Skipping Squirrel package generation
        echo This matches the warning condition in the original script
    )
    
) else (
    echo ✓ NOSQUIRREL is defined - Squirrel package generation will be skipped
    echo   (This is normal if NOSQUIRREL environment variable is set)
)

echo.
echo =================================================================
echo DIAGNOSTIC COMPLETE
echo =================================================================

endlocal
pause