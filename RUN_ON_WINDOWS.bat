@echo off
setlocal EnableExtensions
REM Cheetah Ludo - Windows (10/11) portable Java launcher.
REM This file can be started by double-clicking it in File Explorer.
cd /d "%~dp0"
if errorlevel 1 (
    echo ERROR: Cannot access the extracted project folder.
    pause
    exit /b 1
)
echo =========================================
echo   CHEETAH LUDO - WINDOWS DESKTOP GAME
echo =========================================
echo.
set "JAR=%~dp0dist\CheetahLudo.jar"
if not exist "%JAR%" (
    echo ERROR: The game JAR was not found:
    echo   "%JAR%"
    echo Extract ALL files from the ZIP, then retry.
    pause
    exit /b 1
)
REM Prefer JAVA_HOME, then java on PATH. The bundled JAR requires Java 17+.
set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    where java.exe >nul 2>&1
    if not errorlevel 1 set "JAVA_EXE=java.exe"
)
if not defined JAVA_EXE (
    echo ERROR: Java was not found on PATH or in JAVA_HOME.
    echo Install Java 17 or later, then retry, or run Main.java inside Eclipse.
    echo Download: https://adoptium.net/
    pause
    exit /b 1
)
"%JAVA_EXE%" -version
if errorlevel 1 (
    echo ERROR: Java failed to launch. Check your JAVA_HOME environment variable.
    pause
    exit /b 1
)
echo.
echo Checking game resources...
"%JAVA_EXE%" -jar "%JAR%" --check
if errorlevel 1 (
    echo ERROR: Startup check failed. Send a screenshot of this window.
    pause
    exit /b 1
)
echo.
echo Opening the Ludo game GUI now. The game opens in its OWN window.
"%JAVA_EXE%" -jar "%JAR%"
if errorlevel 1 (
    echo ERROR: Game stopped unexpectedly. Send the output above.
    pause
    exit /b 1
)
echo Game closed normally.
pause
exit /b 0
