@echo off
setlocal

set "ROOT=%~dp0"
cd /d "%ROOT%"

if not exist "%ROOT%setup-production-server.ps1" (
  echo setup-production-server.ps1 not found.
  pause
  exit /b 1
)

echo Preparing Foxaria production template...
powershell -ExecutionPolicy Bypass -File "%ROOT%setup-production-server.ps1"
if errorlevel 1 (
  echo Production setup failed.
  pause
  exit /b 1
)

findstr /C:"eula=true" "%ROOT%eula.txt" >nul 2>nul
if errorlevel 1 (
  echo.
  echo Mojang EULA acceptance is required before first launch.
  set /p FOXARIA_EULA=Type AGREE to accept the Mojang EULA and continue: 
  set "FOXARIA_EULA=%FOXARIA_EULA:"=%"
  for /f "tokens=* delims= " %%A in ("%FOXARIA_EULA%") do set "FOXARIA_EULA=%%~A"
  if /I not "%FOXARIA_EULA%"=="AGREE" (
    echo EULA not accepted. Startup aborted.
    pause
    exit /b 1
  )
  > "%ROOT%eula.txt" echo eula=true
)

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JDK 21 was not found at:
  echo %JAVA_HOME%
  pause
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Starting Foxaria production template...
java -Xms4G -Xmx8G -Dfile.encoding=UTF-8 -jar paper-1.21.11.jar nogui

echo.
echo Server stopped.
pause
