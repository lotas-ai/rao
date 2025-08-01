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

echo Installing components using cmake to temporary directory...
pushd "%BUILD_DIR_ARG%"
cmake --install . --config "%CMAKE_BUILD_TYPE%" --prefix "%TEMP_INSTALL_DIR%"
if ERRORLEVEL 1 (
    echo ERROR: cmake --install failed
    popd
    goto :error
)
popd

echo Copying installed components to Electron app...
REM Copy everything from temp install to Electron app structure
if exist "%TEMP_INSTALL_DIR%" (
    xcopy /E /I /Y "%TEMP_INSTALL_DIR%\*" "%ELECTRON_DIR%\resources\app\"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy installed components
        goto :error
    )
)

REM Still need to manually copy executables to bin (cmake may not put them where Electron expects)
echo Copying executables to bin directory...
if not exist "%ELECTRON_DIR%\resources\app\bin" (
    mkdir "%ELECTRON_DIR%\resources\app\bin"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to create bin directory
        goto :error
    )
)

if not exist "%ELECTRON_DIR%\resources\app\bin\rsession.exe" (
    copy "%BUILD_DIR_ARG%\src\cpp\session\rsession.exe" "%ELECTRON_DIR%\resources\app\bin\rsession.exe"
    if ERRORLEVEL 1 (
        echo ERROR: Failed to copy rsession.exe
        goto :error
    )
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

REM R files, session modules, and session resources are now handled by cmake --install above

REM Dictionaries, MathJax, and R packages are now handled by cmake --install above

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

REM Clean up temporary install directory
if exist "%TEMP_INSTALL_DIR%" (
    rmdir /s /q "%TEMP_INSTALL_DIR%"
)

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
        echo WARNING: Tools.R not found - may indicate cmake --install issue
    )
    
    if exist "%ELECTRON_DIR%\resources\app\resources" (
        echo SUCCESS: Resources directory confirmed present
    ) else (
        echo WARNING: Resources directory not found - may indicate cmake --install issue
    )
    
    if exist "%ELECTRON_DIR%\resources\app\www" (
        echo SUCCESS: WWW directory confirmed present (GWT web interface)
    ) else (
        echo WARNING: WWW directory not found - may indicate GWT build or cmake --install issue
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
