@echo off
echo Checking build output structure...
echo.

set BUILD_DIR=%~dp0build

echo Looking in: %BUILD_DIR%
echo.

echo Checking for resources/app/bin:
if exist "%BUILD_DIR%\resources\app\bin" (
    echo [✓] Found resources\app\bin
    echo Contents:
    dir /B "%BUILD_DIR%\resources\app\bin"
) else (
    echo [✗] resources\app\bin NOT FOUND
)

echo.
echo Checking for rsession.exe in other locations:
echo.

if exist "%BUILD_DIR%\bin\rsession.exe" (
    echo [✓] Found in build\bin
)

if exist "%BUILD_DIR%\rsession.exe" (
    echo [✓] Found in build root
)

echo.
echo Checking package directory:
if exist "%BUILD_DIR%\package" (
    echo [✓] Package directory exists
    dir /S /B "%BUILD_DIR%\package\*.exe" | findstr "rsession"
)

pause