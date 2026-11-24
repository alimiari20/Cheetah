@echo off
setlocal EnableExtensions
REM Run on a Windows 10/11 PC with a JDK 17+ that includes jpackage.
REM Produces release\CheetahLudo\CheetahLudo.exe plus its own Java runtime.
cd /d "%~dp0"
echo ===========================================
echo   BUILD CHEETAH LUDO WINDOWS APP (.EXE)
echo ===========================================
echo.
if not exist "dist\CheetahLudo.jar" (
    echo ERROR: dist\CheetahLudo.jar is missing. Extract all files from the ZIP.
    pause
    exit /b 1
)
set "JPACKAGE_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE_EXE=%JAVA_HOME%\bin\jpackage.exe"
if not defined JPACKAGE_EXE (
    where jpackage.exe >nul 2>&1
    if not errorlevel 1 set "JPACKAGE_EXE=jpackage.exe"
)
if not defined JPACKAGE_EXE (
    echo ERROR: jpackage was not found.
    echo Install a FULL Java JDK 17+ ^(such as JDK 21^), then set JAVA_HOME.
    echo Download: https://adoptium.net/
    pause
    exit /b 1
)
if exist "release\CheetahLudo" (
    echo A previous release exists at release\CheetahLudo.
    echo Rename/delete that folder if you want to rebuild it.
    pause
    exit /b 1
)
if not exist release mkdir release
"%JPACKAGE_EXE%" --type app-image --name CheetahLudo --input "dist" --main-jar "CheetahLudo.jar" --dest "release" --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 (
    echo ERROR: Packaging failed. Check the output above.
    pause
    exit /b 1
)
echo.
echo SUCCESS! You can launch:
echo   release\CheetahLudo\CheetahLudo.exe
echo.
echo Copy the ENTIRE release\CheetahLudo folder to another Windows PC.
echo Its Java runtime is included, so the receiving PC does not need Java.
echo Note: This script must run on Windows. An EXE cannot be built on a Mac using jpackage.
pause
exit /b 0
