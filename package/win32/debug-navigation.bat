::
:: debug-navigation.bat
::
:: Comprehensive debugging script to test navigation to Electron app directory
:: This script tests each step of the navigation process with detailed output
::
@echo off
setlocal enabledelayedexpansion

echo =================================================================
echo ELECTRON APP DIRECTORY NAVIGATION DIAGNOSTIC SCRIPT
echo =================================================================
echo.

:: Initialize the same variables as make-dist-packages.bat
if "%PACKAGE_DIR%" == "" set PACKAGE_DIR=%~dp0
if "%BUILD_DIR%" == "" set BUILD_DIR=build
if "%CMAKE_BUILD_TYPE%" == "" set CMAKE_BUILD_TYPE=RelWithDebInfo
if "%CMAKE_BUILD_TYPE%" == "Debug" set BUILD_DIR=build-debug

echo STEP 1: Initial Variable Setup
echo ==============================
echo PACKAGE_DIR=%PACKAGE_DIR%
echo BUILD_DIR=%BUILD_DIR%
echo CMAKE_BUILD_TYPE=%CMAKE_BUILD_TYPE%
echo Current directory: %CD%
echo Script location: %~dp0
echo Script full path: %~dpnx0
echo.

:: Test PACKAGE_DIR path construction
echo STEP 2: PACKAGE_DIR Analysis
echo =============================
echo Raw PACKAGE_DIR: [%PACKAGE_DIR%]
echo Length of PACKAGE_DIR: 
for /f %%i in ('echo "%PACKAGE_DIR%" ^| find /c /v ""') do echo   %%i characters
echo Last character check:
set "LAST_CHAR=%PACKAGE_DIR:~-1%"
echo   Last character: [%LAST_CHAR%]
if "%LAST_CHAR%" == "\" (
    echo   ✓ PACKAGE_DIR ends with backslash
    set "PACKAGE_DIR_NORMALIZED=%PACKAGE_DIR%"
) else (
    echo   ✗ PACKAGE_DIR does not end with backslash - adding one
    set "PACKAGE_DIR_NORMALIZED=%PACKAGE_DIR%\"
)
echo Normalized PACKAGE_DIR: [%PACKAGE_DIR_NORMALIZED%]
echo.

:: Test relative path construction step by step
echo STEP 3: Path Construction Analysis
echo ==================================
set "REL_PATH_1=%PACKAGE_DIR_NORMALIZED%.."
set "REL_PATH_2=%PACKAGE_DIR_NORMALIZED%..\..\"
set "REL_PATH_3=%PACKAGE_DIR_NORMALIZED%..\..\src"
set "REL_PATH_4=%PACKAGE_DIR_NORMALIZED%..\..\src\node"
set "REL_PATH_5=%PACKAGE_DIR_NORMALIZED%..\..\src\node\desktop"
set "REL_PATH_6=%PACKAGE_DIR_NORMALIZED%..\..\src\node\desktop\out"
set "ELECTRON_APP_DIR=%PACKAGE_DIR_NORMALIZED%..\..\src\node\desktop\out\Rao-win32-x64"

echo Testing each path component:
echo   Step 1: %REL_PATH_1%
if exist "%REL_PATH_1%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo   Step 2: %REL_PATH_2%
if exist "%REL_PATH_2%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo   Step 3: %REL_PATH_3%
if exist "%REL_PATH_3%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo   Step 4: %REL_PATH_4%
if exist "%REL_PATH_4%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo   Step 5: %REL_PATH_5%
if exist "%REL_PATH_5%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo   Step 6: %REL_PATH_6%
if exist "%REL_PATH_6%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)

echo   Final: %ELECTRON_APP_DIR%
if exist "%ELECTRON_APP_DIR%" (echo   ✓ EXISTS) else (echo   ✗ MISSING)
echo.

:: Test absolute path resolution
echo STEP 4: Absolute Path Resolution
echo =================================
echo Attempting to resolve absolute paths...

pushd "%PACKAGE_DIR_NORMALIZED%" 2>nul
if errorlevel 1 (
    echo ✗ Failed to navigate to PACKAGE_DIR
) else (
    echo ✓ Successfully navigated to PACKAGE_DIR
    echo   Current absolute path: %CD%
    
    echo Testing relative navigation:
    if exist ".." (
        pushd ".." 2>nul
        if errorlevel 1 (
            echo   ✗ Failed to navigate to parent directory (..)
        ) else (
            echo   ✓ Successfully navigated to parent (..)
            echo     Current path: %CD%
            
            if exist ".." (
                pushd ".." 2>nul
                if errorlevel 1 (
                    echo   ✗ Failed to navigate to grandparent directory (..\..)
                ) else (
                    echo   ✓ Successfully navigated to grandparent (..\..)
                    echo     Current path: %CD%
                    set "ROOT_DIR=%CD%"
                    
                    :: Test each subdirectory from root
                    if exist "src" (
                        echo     ✓ src directory exists
                        if exist "src\node" (
                            echo     ✓ src\node directory exists
                            if exist "src\node\desktop" (
                                echo     ✓ src\node\desktop directory exists
                                if exist "src\node\desktop\out" (
                                    echo     ✓ src\node\desktop\out directory exists
                                    if exist "src\node\desktop\out\Rao-win32-x64" (
                                        echo     ✓ src\node\desktop\out\Rao-win32-x64 directory exists
                                        set "RESOLVED_ELECTRON_DIR=%CD%\src\node\desktop\out\Rao-win32-x64"
                                    ) else (
                                        echo     ✗ src\node\desktop\out\Rao-win32-x64 directory MISSING
                                    )
                                ) else (
                                    echo     ✗ src\node\desktop\out directory MISSING
                                )
                            ) else (
                                echo     ✗ src\node\desktop directory MISSING
                            )
                        ) else (
                            echo     ✗ src\node directory MISSING
                        )
                    ) else (
                        echo     ✗ src directory MISSING
                    )
                    popd
                )
                popd
            ) else (
                echo   ✗ Grandparent directory (..) does not exist
            )
        )
        popd
    ) else (
        echo   ✗ Parent directory (..) does not exist
    )
    popd
)
echo.

:: Alternative path search
echo STEP 5: Alternative Path Search
echo ================================
echo Searching for Electron directories from current location...

:: Search from script directory
pushd "%~dp0" 2>nul
set "SEARCH_ROOT=%CD%"
echo Starting search from: %SEARCH_ROOT%

:: Look for any Rao-related directories
echo.
echo Searching for directories containing 'Rao':
for /f "delims=" %%a in ('dir /s /b /ad "*Rao*" 2^>nul') do (
    echo   Found: %%a
)

echo.
echo Searching for directories containing 'win32':
for /f "delims=" %%a in ('dir /s /b /ad "*win32*" 2^>nul') do (
    echo   Found: %%a
)

echo.
echo Searching for directories containing 'x64':
for /f "delims=" %%a in ('dir /s /b /ad "*x64*" 2^>nul') do (
    echo   Found: %%a
)

echo.
echo Searching for 'out' directories:
for /f "delims=" %%a in ('dir /s /b /ad "out" 2^>nul') do (
    echo   Found: %%a
)

popd
echo.

:: Final summary
echo STEP 6: Summary and Recommendations
echo ====================================
echo Original path construction: %ELECTRON_APP_DIR%
if exist "%ELECTRON_APP_DIR%" (
    echo ✓ ELECTRON_APP_DIR exists and is accessible
    echo   Resolved to: %ELECTRON_APP_DIR%
) else (
    echo ✗ ELECTRON_APP_DIR does not exist or is not accessible
    
    if defined RESOLVED_ELECTRON_DIR (
        echo Suggested alternative path: %RESOLVED_ELECTRON_DIR%
        if exist "%RESOLVED_ELECTRON_DIR%" (
            echo ✓ Alternative path is valid
        ) else (
            echo ✗ Alternative path is also invalid
        )
    )
    
    echo.
    echo Possible issues:
    echo   1. Build hasn't been completed yet
    echo   2. Electron app directory name has changed
    echo   3. Directory structure is different than expected
    echo   4. Path construction logic needs adjustment
    
    echo.
    echo To fix, consider:
    echo   1. Verify the Electron build completed successfully
    echo   2. Check actual directory structure under src/node/desktop/out/
    echo   3. Update the path construction in make-dist-packages.bat
    echo   4. Use absolute paths instead of relative paths
)

echo.
echo =================================================================
echo DIAGNOSTIC COMPLETE
echo =================================================================

endlocal
pause