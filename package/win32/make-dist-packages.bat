::
:: make-dist-packages.bat
::
:: Copyright (C) 2025 by Posit Software, PBC
::
:: Unless you have received this program directly from Posit Software pursuant
:: to the terms of a commercial license agreement with Posit Software, then
:: this program is licensed to you under the terms of version 3 of the
:: GNU Affero General Public License. This program is distributed WITHOUT
:: ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
:: MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
:: AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
::
@echo off
setlocal

if "%1" == "--help" goto :showhelp
if "%1" == "-h" goto :showhelp
if "%1" == "help" goto :showhelp
if "%1" == "/?" goto :showhelp

if "%PACKAGE_DIR%" == "" set PACKAGE_DIR=%~dp0
if "%BUILD_DIR%" == "" set BUILD_DIR=build
if "%CMAKE_BUILD_TYPE%" == "" set CMAKE_BUILD_TYPE=RelWithDebInfo
if "%CMAKE_BUILD_TYPE%" == "Debug" set BUILD_DIR=build-debug
if "%PKG_TEMP_DIR%" == "" set PKG_TEMP_DIR=C:/rsbuild
REM Get project root by going up two directories from package\win32 and getting absolute path
pushd "%PACKAGE_DIR%"
cd ..\..
set "PROJECT_ROOT=%CD%"
popd

REM Set up dependencies path if not already set
if not defined RSTUDIO_DEPENDENCIES (
    if not exist c:\rstudio-tools\dependencies (
        set RSTUDIO_DEPENDENCIES=%PROJECT_ROOT%\dependencies
    ) else (
        set RSTUDIO_DEPENDENCIES=c:\rstudio-tools\dependencies
    )
)

REM Call rstudio-tools if needed to get RSTUDIO_NODE_VERSION
if not defined RSTUDIO_NODE_VERSION (
    call %RSTUDIO_DEPENDENCIES%\tools\rstudio-tools.cmd
)

REM Find node tools.
set NODE_DIR=%RSTUDIO_DEPENDENCIES%\common\node\%RSTUDIO_NODE_VERSION%
set NODE=%NODE_DIR%\node.exe
if not exist %NODE% (
    echo node.exe not found at %NODE_DIR%; exiting
    endlocal
    exit /b 1
)
echo Using node: %NODE%

echo DEBUG: make-dist-packages.bat using following values:
echo DEBUG:     PACKAGE_DIR=%PACKAGE_DIR%
echo DEBUG:     PROJECT_ROOT=%PROJECT_ROOT%
echo DEBUG:     BUILD_DIR=%BUILD_DIR%
echo DEBUG:     CMAKE_BUILD_TYPE=%CMAKE_BUILD_TYPE%
echo DEBUG:     PKG_TEMP_DIR=%PKG_TEMP_DIR%

if not exist %BUILD_DIR% (
    echo ERROR: Build directory not found at "%BUILD_DIR%"
    goto :error
)

pushd %BUILD_DIR%
set "BUILD_DIR=%CD%"

if not defined QUICK (
    echo Creating NSIS setup package...
    cpack -C "%CMAKE_BUILD_TYPE%" -G NSIS
    REM emit NSIS error output if present
    if exist "%PKG_TEMP_DIR%\_CPack_Packages\win64\NSIS\NSISOutput.log" type "%PKG_TEMP_DIR%\_CPack_Packages\win64\NSIS\NSISOutput.log"
    if not defined RSTUDIO_DOCKER_DEVELOPMENT_BUILD (
        move "%PKG_TEMP_DIR%\*.exe" "%BUILD_DIR%"
    )
)

if not defined NOZIP (
    if "%CMAKE_BUILD_TYPE%" == "RelWithDebInfo" (
        echo Creating ZIP package...
        cpack -C "%CMAKE_BUILD_TYPE%" -G ZIP
        if not defined RSTUDIO_DOCKER_DEVELOPMENT_BUILD (
            move "%PKG_TEMP_DIR%\*.zip" "%BUILD_DIR%"
        )
    )
)

REM Generate Squirrel.Windows packages for auto-updates
if not defined NOSQUIRREL (
    echo Creating Squirrel.Windows packages for auto-updates...
    
    REM Find the Electron app directory
    echo DEBUG: PROJECT_ROOT before setting ELECTRON_APP_DIR: %PROJECT_ROOT%
    set "ELECTRON_APP_DIR=%PROJECT_ROOT%\src\node\desktop\out\Rao-win32-x64"
    echo DEBUG: ELECTRON_APP_DIR after setting: %ELECTRON_APP_DIR%
    echo DEBUG: Looking for Electron app at: %ELECTRON_APP_DIR%
    if exist "%ELECTRON_APP_DIR%\rao.exe" (
        echo Found Electron app at: %ELECTRON_APP_DIR%
        
        REM Copy RStudio binaries into Electron app if they don't already exist
        call :copy_binaries "%ELECTRON_APP_DIR%" "%BUILD_DIR%"
        
        REM Copy script to desktop directory where node_modules exists
        set "DESKTOP_DIR=%PROJECT_ROOT%\src\node\desktop"
        copy "%PACKAGE_DIR%create-squirrel-packages.js" "%DESKTOP_DIR%\create-squirrel-packages.js" >nul
        
        REM Change to desktop directory
        pushd "%DESKTOP_DIR%"
        
        REM Verify we're in the right directory
        echo Current directory: %CD%
        
        REM Clean up any existing squirrel build to force regeneration
        if exist "build\squirrel" (
            echo DEBUG: Removing existing squirrel directory to force regeneration...
            rmdir /s /q "build\squirrel"
        )
        
        REM BUILD_DIR is already absolute, just use it directly
        REM Run the script from the desktop directory
        echo DEBUG: About to run Node.js script...
        echo DEBUG: NODE=%NODE%
        echo DEBUG: Current directory: %CD%
        echo DEBUG: BUILD_DIR=%BUILD_DIR%
        echo DEBUG: Command: "%NODE%" create-squirrel-packages.js "%BUILD_DIR%"
        "%NODE%" create-squirrel-packages.js "%BUILD_DIR%"
        echo DEBUG: Node.js script exit code: %ERRORLEVEL%
        if ERRORLEVEL 1 (
            REM Clean up temporary file
            del create-squirrel-packages.js
            popd
            echo ERROR: Squirrel package creation failed
            goto :error
        )
        
        REM Clean up temporary file
        del create-squirrel-packages.js
        
        REM Copy the squirrel output from desktop build to package build
        if exist "build\squirrel" (
            echo Copying squirrel packages to package build directory...
            echo DEBUG: Source: build\squirrel
            echo DEBUG: Destination: %BUILD_DIR%\squirrel
            dir "build\squirrel"
            xcopy /E /I /Y "build\squirrel" "%BUILD_DIR%\squirrel"
            if ERRORLEVEL 1 (
                echo ERROR: Failed to copy squirrel packages from build\squirrel to %BUILD_DIR%\squirrel
                del create-squirrel-packages.js
                popd
                goto :error
            )
            echo DEBUG: Copy result: %ERRORLEVEL%
        ) else (
            echo DEBUG: build\squirrel directory does not exist
            dir build
        )
        
        popd
        
        REM Move Squirrel files to build directory
        if exist "%BUILD_DIR%\squirrel" (
            echo Moving Squirrel packages to build directory...
            echo DEBUG: Contents of %BUILD_DIR%\squirrel before move:
            dir "%BUILD_DIR%\squirrel"
            
            move "%BUILD_DIR%\squirrel\*.nupkg" "%BUILD_DIR%\"
            if ERRORLEVEL 1 (
                echo ERROR: Failed to move .nupkg files
                goto :error
            )
            
            move "%BUILD_DIR%\squirrel\RELEASES" "%BUILD_DIR%\"
            if ERRORLEVEL 1 (
                echo ERROR: Failed to move RELEASES file
                goto :error
            )
            
            if exist "%BUILD_DIR%\squirrel\RaoSetup.exe" (
                move "%BUILD_DIR%\squirrel\RaoSetup.exe" "%BUILD_DIR%\RaoSetup-Squirrel.exe"
                if ERRORLEVEL 1 (
                    echo ERROR: Failed to move RaoSetup.exe
                    goto :error
                )
            ) else if exist "%BUILD_DIR%\squirrel\Setup.exe" (
                move "%BUILD_DIR%\squirrel\Setup.exe" "%BUILD_DIR%\RaoSetup-Squirrel.exe"
                if ERRORLEVEL 1 (
                    echo ERROR: Failed to move Setup.exe
                    goto :error
                )
            ) else (
                echo ERROR: Neither RaoSetup.exe nor Setup.exe found in squirrel directory
                goto :error
            )
            echo DEBUG: Final contents of %BUILD_DIR%:
            dir "%BUILD_DIR%\*.nupkg" "%BUILD_DIR%\RELEASES" "%BUILD_DIR%\RaoSetup-Squirrel.exe" 2>nul
            echo Squirrel auto-update files created successfully
        ) else (
            echo ERROR: Squirrel directory was not created at %BUILD_DIR%\squirrel
            goto :error
        )
    ) else (
        popd
        echo WARNING: Electron app directory not found at %ELECTRON_APP_DIR%
        echo Skipping Squirrel package generation
    )
)

:skip_squirrel

popd

endlocal
goto :EOF

:showhelp
echo.
echo make-dist-packages
echo.
echo. Produces the RStudio setup package, zip file (installerless), and Squirrel.Windows 
echo. auto-update packages using already-built binaries.
echo.
echo  Must be invoked from the "package\win32" folder (in the cloned RStudio repository).
echo  Use "set NOSQUIRREL=1" to skip Squirrel.Windows package generation.
echo.
exit /b 0

:copy_binaries
set "ELECTRON_DIR=%~1"
set "BUILD_DIR_ARG=%~2"
if not exist "%ELECTRON_DIR%\resources\app\bin\rsession.exe" (
    echo Copying RStudio binaries to Electron app...
    echo   FROM: %BUILD_DIR_ARG%\src\cpp\session\rsession.exe
    echo   TO: %ELECTRON_DIR%\resources\app\bin\rsession.exe
    if not exist "%ELECTRON_DIR%\resources\app\bin" (
        mkdir "%ELECTRON_DIR%\resources\app\bin"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to create bin directory
            goto :error
        )
    )
    
    copy "%BUILD_DIR_ARG%\src\cpp\session\rsession.exe" "%ELECTRON_DIR%\resources\app\bin\rsession.exe"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy rsession.exe
        goto :error
    )
    
    if exist "%BUILD_DIR_ARG%\src\cpp\server\rserver.exe" (
        copy "%BUILD_DIR_ARG%\src\cpp\server\rserver.exe" "%ELECTRON_DIR%\resources\app\bin\rserver.exe"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to copy rserver.exe
            goto :error
        )
    )
    
    if exist "%BUILD_DIR_ARG%\src\cpp\diagnostics\diagnostics.exe" (
        copy "%BUILD_DIR_ARG%\src\cpp\diagnostics\diagnostics.exe" "%ELECTRON_DIR%\resources\app\bin\diagnostics.exe"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to copy diagnostics.exe
            goto :error
        )
    )
    
)

REM Always copy R directory to Electron app (outside the rsession.exe conditional)
REM Copy from source directory since Windows build doesn't install R files to build dir
set "SOURCE_R_DIR=%PROJECT_ROOT%\src\cpp\r\R"
if exist "%SOURCE_R_DIR%" (
    echo Copying core R files from source to Electron app...
    echo   FROM: %SOURCE_R_DIR%
    echo   TO: %ELECTRON_DIR%\resources\app\R\
    if not exist "%ELECTRON_DIR%\resources\app\R" (
        mkdir "%ELECTRON_DIR%\resources\app\R"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to create R directory
            goto :error
        )
    )
    
    xcopy /E /I /Y "%SOURCE_R_DIR%\*" "%ELECTRON_DIR%\resources\app\R\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy core R files from %SOURCE_R_DIR%
        goto :error
    )
    echo Successfully copied core R files
) else (
    echo ERROR: Source R directory not found at %SOURCE_R_DIR%
    goto :error
)

REM Also copy session module R files from build directory if they exist
if exist "%BUILD_DIR_ARG%\src\cpp\session\modules\R" (
    echo Copying session module R files to Electron app...
    xcopy /E /I /Y "%BUILD_DIR_ARG%\src\cpp\session\modules\R\*" "%ELECTRON_DIR%\resources\app\R\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy session module R files from %BUILD_DIR_ARG%\src\cpp\session\modules\R
        goto :error
    )
    echo Successfully copied session module R files
) else (
    echo WARNING: Session module R directory not found at %BUILD_DIR_ARG%\src\cpp\session\modules\R
    echo This may be OK if they don't exist in this build
)
goto :eof

:error
echo ERROR: Failed to package RStudio!
exit /b 1
