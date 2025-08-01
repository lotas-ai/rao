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
setlocal enabledelayedexpansion

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
        set RSTUDIO_DEPENDENCIES=!PROJECT_ROOT!\dependencies
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
    echo DEBUG: PROJECT_ROOT before setting ELECTRON_APP_DIR: !PROJECT_ROOT!
    set "ELECTRON_APP_DIR=!PROJECT_ROOT!\src\node\desktop\out\Rao-win32-x64"
    echo DEBUG: ELECTRON_APP_DIR after setting: !ELECTRON_APP_DIR!
    echo DEBUG: Looking for Electron app at: !ELECTRON_APP_DIR!
    if exist "!ELECTRON_APP_DIR!\rao.exe" (
        echo Found Electron app at: !ELECTRON_APP_DIR!
        
        REM Set ELECTRON_DIR for use throughout the script
        set "ELECTRON_DIR=!ELECTRON_APP_DIR!"
        
        REM Install components using cmake to get all configured files
        call :install_and_copy_components "%ELECTRON_APP_DIR%" "%BUILD_DIR%"
        
        REM Copy script to desktop directory where node_modules exists
        set "DESKTOP_DIR=!PROJECT_ROOT!\src\node\desktop"
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
        echo WARNING: Electron app directory not found at !ELECTRON_APP_DIR!
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

:install_and_copy_components
set "ELECTRON_DIR=%~1"
set "BUILD_DIR_ARG=%~2"

REM Create temporary install directory
set "TEMP_INSTALL_DIR=%BUILD_DIR_ARG%\temp_install"
if exist "%TEMP_INSTALL_DIR%" (
    rmdir /s /q "%TEMP_INSTALL_DIR%"
)
mkdir "%TEMP_INSTALL_DIR%"

echo Installing components using cmake...
pushd "%BUILD_DIR_ARG%"
cmake --install . --config "%CMAKE_BUILD_TYPE%" --prefix "%TEMP_INSTALL_DIR%" >nul 2>nul
if ERRORLEVEL 1 (
    echo ERROR: cmake --install failed
    popd
    goto :error
)
popd
REM Copy everything from temp install to Electron app structure
if exist "%TEMP_INSTALL_DIR%" (
    xcopy /E /I /Y "%TEMP_INSTALL_DIR%\*" "%ELECTRON_DIR%\resources\app\" >nul
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy installed components
        goto :error
    )
)

REM Copy core R files (not installed by cmake on Windows due to RSTUDIO_SESSION_WIN32 exclusion)
set "SOURCE_R_DIR=%PROJECT_ROOT%\src\cpp\r\R"
if exist "%SOURCE_R_DIR%" (
    if not exist "%ELECTRON_DIR%\resources\app\R" (
        mkdir "%ELECTRON_DIR%\resources\app\R"
    )
    xcopy /E /I /Y "%SOURCE_R_DIR%\*" "%ELECTRON_DIR%\resources\app\R\" >nul
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy core R files
        goto :error
    )
) else (
    echo ERROR: Core R files not found at %SOURCE_R_DIR%
    goto :error
)

REM R files, session modules, and session resources are now handled by cmake --install above

REM Dictionaries, MathJax, and R packages are now handled by cmake --install above

REM Add file type icons to Rao.exe (like make-package.bat does)
if exist "%ELECTRON_DIR%\rao.exe" (
    if defined REZH (
        echo Adding file type icons...
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\RProject.ico" -mask ICONGROUP,2,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\RSource.ico" -mask ICONGROUP,3,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\CSS.ico" -mask ICONGROUP,4,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\HTML.ico" -mask ICONGROUP,5,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\JS.ico" -mask ICONGROUP,6,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\Markdown.ico" -mask ICONGROUP,7,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\QuartoMarkdown.ico" -mask ICONGROUP,8,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\RData.ico" -mask ICONGROUP,9,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\RDoc.ico" -mask ICONGROUP,10,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\RHTML.ico" -mask ICONGROUP,11,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\RMarkdown.ico" -mask ICONGROUP,12,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\RPresentation.ico" -mask ICONGROUP,13,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\RSweave.ico" -mask ICONGROUP,14,1033 >nul
        "%REZH%" -open "%ELECTRON_DIR%\rao.exe" -save "%ELECTRON_DIR%\rao.exe" -action addoverwrite -resource "%ELECTRON_SOURCE_DIR%\resources\icons\RTex.ico" -mask ICONGROUP,15,1033 >nul
    ) else (
        echo WARNING: REZH not found, skipping icon embedding
    )
)



REM Clean up temporary install directory
if exist "%TEMP_INSTALL_DIR%" (
    rmdir /s /q "%TEMP_INSTALL_DIR%"
)

REM Final verification of key components
if exist "%ELECTRON_DIR%\resources\app\R" (
    if not exist "%ELECTRON_DIR%\resources\app\R\Tools.R" (
        echo ERROR: Tools.R not found - cmake --install failed to copy R files
        goto :error
    )
    
    if not exist "%ELECTRON_DIR%\resources\app\resources" (
        echo ERROR: Resources directory not found - cmake --install failed
        goto :error
    )
    
    if not exist "%ELECTRON_DIR%\resources\app\www" (
        echo ERROR: WWW directory not found - GWT build or cmake --install failed
        goto :error
    )
    

) else (
    echo ERROR: R directory missing before packaging
    goto :error
)

goto :eof

:error
echo ERROR: Failed to package RStudio!
exit /b 1
