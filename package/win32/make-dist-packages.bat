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
        
        REM Copy RStudio binaries into Electron app if they don't already exist
        call :copy_binaries "%ELECTRON_APP_DIR%" "%BUILD_DIR%"
        
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

REM Always copy R directory to Electron app (outside the rsession.exe conditional but inside the function where ELECTRON_DIR is defined)
REM Copy from source directory since Windows build doesn't install R files to build dir
set "SOURCE_R_DIR=%PROJECT_ROOT%\src\cpp\r\R"
echo DEBUG: About to copy R files...
echo DEBUG: SOURCE_R_DIR=%SOURCE_R_DIR%
echo DEBUG: ELECTRON_DIR=%ELECTRON_DIR%
echo DEBUG: TARGET_R_DIR=%ELECTRON_DIR%\resources\app\R\

if exist "%SOURCE_R_DIR%" (
    echo Copying core R files from source to Electron app...
    echo   FROM: %SOURCE_R_DIR%
    echo   TO: %ELECTRON_DIR%\resources\app\R\
    
    if not exist "%ELECTRON_DIR%\resources\app\R" (
        echo Creating R directory: %ELECTRON_DIR%\resources\app\R
        mkdir "%ELECTRON_DIR%\resources\app\R"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to create R directory
            goto :error
        )
    )
    
    echo Running xcopy command: xcopy /E /I /Y "%SOURCE_R_DIR%\*" "%ELECTRON_DIR%\resources\app\R\"
    xcopy /E /I /Y "%SOURCE_R_DIR%\*" "%ELECTRON_DIR%\resources\app\R\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy core R files from %SOURCE_R_DIR%
        goto :error
    )
    
    echo Verifying R files were copied...
    if exist "%ELECTRON_DIR%\resources\app\R\Tools.R" (
        echo SUCCESS: Tools.R found at %ELECTRON_DIR%\resources\app\R\Tools.R
    ) else (
        echo ERROR: Tools.R NOT found at %ELECTRON_DIR%\resources\app\R\Tools.R
        echo Contents of R directory:
        dir "%ELECTRON_DIR%\resources\app\R\"
        goto :error
    )
    echo Successfully copied core R files
) else (
    echo ERROR: Source R directory not found at %SOURCE_R_DIR%
    goto :error
)

REM Also copy session module R files from build directory if they exist (preserving modules subdirectory)
if exist "%BUILD_DIR_ARG%\src\cpp\session\modules\R" (
    echo Copying session module R files to Electron app...
    if not exist "%ELECTRON_DIR%\resources\app\R\modules" (
        mkdir "%ELECTRON_DIR%\resources\app\R\modules"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to create R\modules directory
            goto :error
        )
    )
    xcopy /E /I /Y "%BUILD_DIR_ARG%\src\cpp\session\modules\R\*" "%ELECTRON_DIR%\resources\app\R\modules\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy session module R files from %BUILD_DIR_ARG%\src\cpp\session\modules\R
        goto :error
    )
    echo Successfully copied session module R files
) else (
    echo WARNING: Session module R directory not found at %BUILD_DIR_ARG%\src\cpp\session\modules\R
    echo This may be OK if they don't exist in this build
)

REM Copy session resources directory (themes, help_resources, etc.)
set "SESSION_RESOURCES_DIR=%PROJECT_ROOT%\src\cpp\session\resources"
if exist "%SESSION_RESOURCES_DIR%" (
    echo Copying session resources to Electron app...
    echo   FROM: %SESSION_RESOURCES_DIR%
    echo   TO: %ELECTRON_DIR%\resources\app\resources\
    if not exist "%ELECTRON_DIR%\resources\app\resources" (
        mkdir "%ELECTRON_DIR%\resources\app\resources"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to create resources directory
            goto :error
        )
    )
    xcopy /E /I /Y "%SESSION_RESOURCES_DIR%\*" "%ELECTRON_DIR%\resources\app\resources\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy session resources from %SESSION_RESOURCES_DIR%
        goto :error
    )
    echo Successfully copied session resources
) else (
    echo ERROR: Session resources directory not found at %SESSION_RESOURCES_DIR%
    goto :error
)

REM Copy external dependencies that are skipped on Windows builds
echo Copying external dependencies to Electron app...

REM Copy hunspell dictionaries
set "DICTIONARIES_DIR=%RSTUDIO_DEPENDENCIES%\common\dictionaries"
if exist "%DICTIONARIES_DIR%" (
    echo   Copying dictionaries from %DICTIONARIES_DIR%
    xcopy /E /I /Y "%DICTIONARIES_DIR%" "%ELECTRON_DIR%\resources\app\resources\dictionaries\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy dictionaries
        goto :error
    )
) else (
    echo   WARNING: Dictionaries not found at %DICTIONARIES_DIR%
)

REM Copy MathJax
set "MATHJAX_DIR=%RSTUDIO_DEPENDENCIES%\common\mathjax-27"
if exist "%MATHJAX_DIR%" (
    echo   Copying MathJax from %MATHJAX_DIR%
    xcopy /E /I /Y "%MATHJAX_DIR%" "%ELECTRON_DIR%\resources\app\resources\mathjax-27\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy MathJax
        goto :error
    )
) else (
    echo   WARNING: MathJax not found at %MATHJAX_DIR%
)

REM Quarto is copied to bin/ directory separately on Windows

REM Copy embedded R packages
set "PACKAGES_DIR=%RSTUDIO_DEPENDENCIES%\common"
if exist "%PACKAGES_DIR%" (
    echo   Copying R packages from %PACKAGES_DIR%
    if not exist "%ELECTRON_DIR%\resources\app\R\packages" (
        mkdir "%ELECTRON_DIR%\resources\app\R\packages"
    )
    for %%f in ("%PACKAGES_DIR%\*.tar.gz") do (
        copy "%%f" "%ELECTRON_DIR%\resources\app\R\packages\"
    )
) else (
    echo   WARNING: R packages directory not found at %PACKAGES_DIR%
)

echo Successfully copied external dependencies

REM Copy external tools to bin directory (Windows-specific behavior)
echo Copying external tools to bin directory...

REM Copy Quarto to bin (Windows behavior - goes to bin/ not resources/)
set "QUARTO_BIN_DIR=%RSTUDIO_DEPENDENCIES%\common\quarto"
if exist "%QUARTO_BIN_DIR%" (
    echo   Copying Quarto to bin from %QUARTO_BIN_DIR%
    xcopy /E /I /Y "%QUARTO_BIN_DIR%" "%ELECTRON_DIR%\resources\app\bin\quarto\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy Quarto to bin
        goto :error
    )
) else (
    echo   WARNING: Quarto bin directory not found at %QUARTO_BIN_DIR%
)

REM Copy Copilot Language Server to bin
set "COPILOT_DIR=%RSTUDIO_DEPENDENCIES%\common\copilot-language-server"
if exist "%COPILOT_DIR%" (
    echo   Copying Copilot Language Server to bin from %COPILOT_DIR%
    xcopy /E /I /Y "%COPILOT_DIR%" "%ELECTRON_DIR%\resources\app\bin\copilot-language-server\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy Copilot Language Server
        goto :error
    )
) else (
    echo   WARNING: Copilot Language Server not found at %COPILOT_DIR%
)

REM Copy Ripgrep to bin
set "RIPGREP_DIR=%RSTUDIO_DEPENDENCIES%\common\ripgrep"
if exist "%RIPGREP_DIR%" (
    echo   Copying Ripgrep to bin from %RIPGREP_DIR%
    xcopy /E /I /Y "%RIPGREP_DIR%\*" "%ELECTRON_DIR%\resources\app\bin\ripgrep\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy Ripgrep
        goto :error
    )
) else (
    echo   WARNING: Ripgrep not found at %RIPGREP_DIR%
)

REM Copy libclang
set "LIBCLANG_DIR=%RSTUDIO_DEPENDENCIES%\common\libclang"
if exist "%LIBCLANG_DIR%" (
    echo   Copying libclang from %LIBCLANG_DIR%
    if exist "%LIBCLANG_DIR%\x86" (
        xcopy /E /I /Y "%LIBCLANG_DIR%\x86\*" "%ELECTRON_DIR%\resources\app\bin\rsclang\x86\"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to copy libclang x86
            goto :error
        )
    )
    if exist "%LIBCLANG_DIR%\x86_64" (
        xcopy /E /I /Y "%LIBCLANG_DIR%\x86_64\*" "%ELECTRON_DIR%\resources\app\bin\rsclang\x86_64\"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to copy libclang x86_64
            goto :error
        )
    )
) else (
    echo   WARNING: libclang not found at %LIBCLANG_DIR%
)

REM Copy postback tools
if exist "%BUILD_DIR_ARG%\src\cpp\session\postback\rpostback.exe" (
    echo   Copying rpostback.exe
    copy "%BUILD_DIR_ARG%\src\cpp\session\postback\rpostback.exe" "%ELECTRON_DIR%\resources\app\bin\rpostback.exe"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy rpostback.exe
        goto :error
    )
) else (
    echo   WARNING: rpostback.exe not found
)

REM Copy winpty DLLs (Windows terminal support)
set "WINPTY_DIR=%RSTUDIO_DEPENDENCIES%\common\winpty"
if exist "%WINPTY_DIR%" (
    echo   Copying winpty DLLs from %WINPTY_DIR%
    if exist "%WINPTY_DIR%\x64\winpty.dll" (
        copy "%WINPTY_DIR%\x64\winpty.dll" "%ELECTRON_DIR%\resources\app\bin\winpty.dll"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to copy winpty.dll
            goto :error
        )
    )
    if exist "%WINPTY_DIR%\x64\winpty-agent.exe" (
        copy "%WINPTY_DIR%\x64\winpty-agent.exe" "%ELECTRON_DIR%\resources\app\bin\winpty-agent.exe"
        if ERRORLEVEL 1 (
            echo ERROR: Failed to copy winpty-agent.exe
            goto :error
        )
    )
) else (
    echo   WARNING: winpty directory not found at %WINPTY_DIR%
)

REM Copy Windows-specific GNU tools (essential for R functionality)
echo Copying Windows GNU tools...

set "WIN_DEPS_DIR=%RSTUDIO_DEPENDENCIES%\windows"
if not exist "%WIN_DEPS_DIR%" (
    set "WIN_DEPS_DIR=%RSTUDIO_DEPENDENCIES%\common\windows"
)

REM Copy gnudiff
if exist "%WIN_DEPS_DIR%\gnudiff" (
    echo   Copying gnudiff from %WIN_DEPS_DIR%\gnudiff
    xcopy /E /I /Y "%WIN_DEPS_DIR%\gnudiff" "%ELECTRON_DIR%\resources\app\bin\gnudiff\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy gnudiff
        goto :error
    )
) else (
    echo   WARNING: gnudiff not found at %WIN_DEPS_DIR%\gnudiff
)

REM Copy gnugrep
if exist "%WIN_DEPS_DIR%\gnugrep" (
    echo   Copying gnugrep from %WIN_DEPS_DIR%\gnugrep
    xcopy /E /I /Y "%WIN_DEPS_DIR%\gnugrep\3.0" "%ELECTRON_DIR%\resources\app\bin\gnugrep\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy gnugrep
        goto :error
    )
) else (
    echo   WARNING: gnugrep not found at %WIN_DEPS_DIR%\gnugrep
)

REM Copy SumatraPDF
if exist "%WIN_DEPS_DIR%\sumatra\3.1.2\SumatraPDF.exe" (
    echo   Copying SumatraPDF from %WIN_DEPS_DIR%\sumatra
    if not exist "%ELECTRON_DIR%\resources\app\bin\sumatra" (
        mkdir "%ELECTRON_DIR%\resources\app\bin\sumatra"
    )
    copy "%WIN_DEPS_DIR%\sumatra\3.1.2\SumatraPDF.exe" "%ELECTRON_DIR%\resources\app\bin\sumatra\SumatraPDF.exe"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy SumatraPDF.exe
        goto :error
    )
    
    REM Copy SumatraPDF config
    if exist "%SESSION_RESOURCES_DIR%\sumatrapdfrestrict.ini" (
        copy "%SESSION_RESOURCES_DIR%\sumatrapdfrestrict.ini" "%ELECTRON_DIR%\resources\app\bin\sumatra\sumatrapdfrestrict.ini"
    )
) else (
    echo   WARNING: SumatraPDF not found at %WIN_DEPS_DIR%\sumatra\3.1.2\SumatraPDF.exe
)

REM Copy winutils
if exist "%WIN_DEPS_DIR%\winutils\1.0" (
    echo   Copying winutils from %WIN_DEPS_DIR%\winutils
    if not exist "%ELECTRON_DIR%\resources\app\bin\winutils" (
        mkdir "%ELECTRON_DIR%\resources\app\bin\winutils"
    )
    if exist "%WIN_DEPS_DIR%\winutils\1.0\winutils.exe" (
        copy "%WIN_DEPS_DIR%\winutils\1.0\winutils.exe" "%ELECTRON_DIR%\resources\app\bin\winutils\winutils.exe"
    )
    if exist "%WIN_DEPS_DIR%\winutils\1.0\x64\winutils.exe" (
        if not exist "%ELECTRON_DIR%\resources\app\bin\winutils\x64" (
            mkdir "%ELECTRON_DIR%\resources\app\bin\winutils\x64"
        )
        copy "%WIN_DEPS_DIR%\winutils\1.0\x64\winutils.exe" "%ELECTRON_DIR%\resources\app\bin\winutils\x64\winutils.exe"
    )
) else (
    echo   WARNING: winutils not found at %WIN_DEPS_DIR%\winutils\1.0
)

REM Copy CITATION file (built during CMake configure)
if exist "%BUILD_DIR_ARG%\src\cpp\session\CITATION" (
    echo   Copying CITATION file
    copy "%BUILD_DIR_ARG%\src\cpp\session\CITATION" "%ELECTRON_DIR%\resources\app\resources\CITATION"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy CITATION file
        goto :error
    )
) else (
    echo   WARNING: CITATION file not found
)

REM Copy Crashpad tools (crash reporting - also skipped on Windows builds)
set "CRASHPAD_DIR=%RSTUDIO_DEPENDENCIES%\common\crashpad"
if exist "%CRASHPAD_DIR%" (
    echo   Copying Crashpad tools from %CRASHPAD_DIR%
    if exist "%CRASHPAD_DIR%\crashpad_handler.exe" (
        copy "%CRASHPAD_DIR%\crashpad_handler.exe" "%ELECTRON_DIR%\resources\app\bin\crashpad_handler.exe"
    )
    if exist "%CRASHPAD_DIR%\crashpad_handler.com" (
        copy "%CRASHPAD_DIR%\crashpad_handler.com" "%ELECTRON_DIR%\resources\app\bin\crashpad_handler.com"
    )
    if exist "%CRASHPAD_DIR%\crashpad_http_upload.exe" (
        copy "%CRASHPAD_DIR%\crashpad_http_upload.exe" "%ELECTRON_DIR%\resources\app\bin\crashpad_http_upload.exe"
    )
) else (
    echo   WARNING: Crashpad tools not found at %CRASHPAD_DIR%
)

REM Copy consoleio binary (if it exists and is needed)
if exist "%BUILD_DIR_ARG%\src\cpp\session\consoleio\consoleio.exe" (
    echo   Copying consoleio.exe
    copy "%BUILD_DIR_ARG%\src\cpp\session\consoleio\consoleio.exe" "%ELECTRON_DIR%\resources\app\bin\consoleio.exe"
) else (
    echo   INFO: consoleio.exe not found (may not be built)
)

echo Successfully copied Windows GNU tools and additional resources
echo Successfully copied external tools

REM Final verification of Electron app directory contents
echo.
echo DEBUG: Final verification of Electron app directory
echo DEBUG: Checking contents of %ELECTRON_DIR%\resources\app\
dir "%ELECTRON_DIR%\resources\app\"
echo.
if exist "%ELECTRON_DIR%\resources\app\R" (
    echo DEBUG: R directory exists, contents:
    dir "%ELECTRON_DIR%\resources\app\R\"
    if exist "%ELECTRON_DIR%\resources\app\R\Tools.R" (
        echo SUCCESS: Tools.R confirmed present before packaging
    ) else (
        echo ERROR: Tools.R missing before packaging
        goto :error
    )
    
    if exist "%ELECTRON_DIR%\resources\app\R\modules\ModuleTools.R" (
        echo SUCCESS: ModuleTools.R confirmed present in modules subdirectory
    ) else (
        echo ERROR: ModuleTools.R missing from modules subdirectory
        if exist "%ELECTRON_DIR%\resources\app\R\modules" (
            echo DEBUG: modules directory exists, contents:
            dir "%ELECTRON_DIR%\resources\app\R\modules\"
        ) else (
            echo ERROR: modules directory does not exist
        )
        goto :error
    )
    
    if exist "%ELECTRON_DIR%\resources\app\resources\themes\compile-themes.R" (
        echo SUCCESS: compile-themes.R confirmed present in resources/themes
    ) else (
        echo ERROR: compile-themes.R missing from resources/themes
        if exist "%ELECTRON_DIR%\resources\app\resources\themes" (
            echo DEBUG: themes directory exists, contents:
            dir "%ELECTRON_DIR%\resources\app\resources\themes\"
        ) else (
            echo ERROR: resources/themes directory does not exist
        )
        goto :error
    )
    
    REM Check for external dependencies
    if exist "%ELECTRON_DIR%\resources\app\resources\dictionaries" (
        echo SUCCESS: Dictionaries directory confirmed present
    ) else (
        echo WARNING: Dictionaries directory not found (optional)
    )
    
    if exist "%ELECTRON_DIR%\resources\app\resources\mathjax-27" (
        echo SUCCESS: MathJax confirmed present
    ) else (
        echo WARNING: MathJax not found (optional)
    )
    
    if exist "%ELECTRON_DIR%\resources\app\R\packages" (
        echo SUCCESS: R packages directory confirmed present
    ) else (
        echo WARNING: R packages directory not found (optional)
    )
    
    REM Check for external tools in bin directory
    if exist "%ELECTRON_DIR%\resources\app\bin\quarto" (
        echo SUCCESS: Quarto confirmed present in bin
    ) else (
        echo WARNING: Quarto not found in bin (optional)
    )
    
    if exist "%ELECTRON_DIR%\resources\app\bin\ripgrep" (
        echo SUCCESS: Ripgrep confirmed present in bin
    ) else (
        echo WARNING: Ripgrep not found in bin (optional)
    )
    
    if exist "%ELECTRON_DIR%\resources\app\bin\rsclang" (
        echo SUCCESS: libclang confirmed present in bin
    ) else (
        echo WARNING: libclang not found in bin (optional)
    )
    
    if exist "%ELECTRON_DIR%\resources\app\bin\rpostback.exe" (
        echo SUCCESS: rpostback.exe confirmed present in bin
    ) else (
        echo WARNING: rpostback.exe not found in bin (optional)
    )
    
    if exist "%ELECTRON_DIR%\resources\app\bin\winpty.dll" (
        echo SUCCESS: winpty.dll confirmed present in bin
    ) else (
        echo WARNING: winpty.dll not found in bin (optional)
    )
    
    REM Check for Windows GNU tools
    if exist "%ELECTRON_DIR%\resources\app\bin\gnudiff" (
        echo SUCCESS: gnudiff confirmed present in bin
    ) else (
        echo WARNING: gnudiff not found in bin (optional)
    )
    
    if exist "%ELECTRON_DIR%\resources\app\bin\gnugrep" (
        echo SUCCESS: gnugrep confirmed present in bin
    ) else (
        echo WARNING: gnugrep not found in bin (optional)
    )
    
    if exist "%ELECTRON_DIR%\resources\app\bin\sumatra\SumatraPDF.exe" (
        echo SUCCESS: SumatraPDF confirmed present in bin
    ) else (
        echo WARNING: SumatraPDF not found in bin (optional)
    )
    
    if exist "%ELECTRON_DIR%\resources\app\bin\winutils" (
        echo SUCCESS: winutils confirmed present in bin
    ) else (
        echo WARNING: winutils not found in bin (optional)
    )
) else (
    echo ERROR: R directory missing before packaging
    goto :error
)
echo.

goto :eof

:error
echo ERROR: Failed to package RStudio!
exit /b 1
