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
echo Script location: %~dp0
echo.

echo STEP 1: Variable Expansion Analysis
echo ====================================
echo Testing PACKAGE_DIR variable expansion...
echo Raw PACKAGE_DIR: [%PACKAGE_DIR%]
echo Script location (~dp0): [%~dp0]
echo.

echo Checking if PACKAGE_DIR ends with backslash...
set "LAST_CHAR=%PACKAGE_DIR:~-1%"
echo Last character: [%LAST_CHAR%]
if "%LAST_CHAR%" == "\" (
    echo ✓ PACKAGE_DIR ends with backslash
) else (
    echo ✗ PACKAGE_DIR does not end with backslash
)
echo.

echo STEP 2: Path Construction Testing
echo ==================================
echo Testing each component of the Electron app path...

set "PATH_COMPONENT_1=%PACKAGE_DIR%.."
set "PATH_COMPONENT_2=%PACKAGE_DIR%..\.."
set "PATH_COMPONENT_3=%PACKAGE_DIR%..\..\src"
set "PATH_COMPONENT_4=%PACKAGE_DIR%..\..\src\node"
set "PATH_COMPONENT_5=%PACKAGE_DIR%..\..\src\node\desktop"
set "PATH_COMPONENT_6=%PACKAGE_DIR%..\..\src\node\desktop\out"
set "ELECTRON_APP_DIR_TEST=%PACKAGE_DIR%..\..\src\node\desktop\out\Rao-win32-x64"

echo Component 1: %PATH_COMPONENT_1%
if exist "%PATH_COMPONENT_1%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo Component 2: %PATH_COMPONENT_2%
if exist "%PATH_COMPONENT_2%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo Component 3: %PATH_COMPONENT_3%
if exist "%PATH_COMPONENT_3%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo Component 4: %PATH_COMPONENT_4%
if exist "%PATH_COMPONENT_4%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo Component 5: %PATH_COMPONENT_5%
if exist "%PATH_COMPONENT_5%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo Component 6: %PATH_COMPONENT_6%
if exist "%PATH_COMPONENT_6%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo Final path: %ELECTRON_APP_DIR_TEST%
if exist "%ELECTRON_APP_DIR_TEST%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)
echo.

echo STEP 3: Testing Squirrel.Windows Section Logic
echo ================================================

echo Testing NOSQUIRREL environment variable...
if not defined NOSQUIRREL (
    echo NOSQUIRREL is not defined - will proceed with Squirrel package generation
    
    echo Creating Squirrel.Windows packages for auto-updates...
    
    REM Find the Electron app directory exactly like original script
    set "ELECTRON_APP_DIR=%PACKAGE_DIR%..\..\src\node\desktop\out\Rao-win32-x64"
    
    echo ELECTRON_APP_DIR is set to: %ELECTRON_APP_DIR%
    echo Comparing with working path: %ELECTRON_APP_DIR_TEST%
    
    if exist "%ELECTRON_APP_DIR%" (
        echo Found Electron app at: %ELECTRON_APP_DIR%
        echo The Squirrel section logic appears to be working correctly
        
    ) else (
        echo WARNING: Electron app directory not found at %ELECTRON_APP_DIR%
        echo Skipping Squirrel package generation
    )
    
) else (
    echo NOSQUIRREL is defined - Squirrel package generation will be skipped
)

echo.
echo =================================================================
echo DIAGNOSTIC COMPLETE
echo =================================================================

endlocal
pause