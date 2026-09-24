@echo off
setlocal

set "ROOT=%~dp0"
cd /d "%ROOT%"

if not exist "%ROOT%setup-test-server.ps1" (
  echo setup-test-server.ps1 not found.
  pause
  exit /b 1
)

echo Preparing Foxaria test server...
powershell -ExecutionPolicy Bypass -File "%ROOT%setup-test-server.ps1"
if errorlevel 1 (
  echo Server setup failed.
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

set "FOXARIA_JAVA="

set "REPO_ROOT=%ROOT%..\.."

rem Portable JDK рядом с репозиторием: .jdks (update-foxaria.cmd / Gradle) или .tools\jdk-21
for /d %%J in ("%REPO_ROOT%\.jdks\jdk-21*") do (
  if exist "%%~fJ\bin\java.exe" set "FOXARIA_JAVA=%%~fJ\bin\java.exe"
)
if not defined FOXARIA_JAVA (
  for /d %%J in ("%REPO_ROOT%\.tools\jdk-21\jdk-*") do (
    if exist "%%~fJ\bin\java.exe" set "FOXARIA_JAVA=%%~fJ\bin\java.exe"
  )
)

rem Затем JAVA_HOME из окружения (если локального JDK ещё нет).
if not defined FOXARIA_JAVA (
  if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "FOXARIA_JAVA=%JAVA_HOME%\bin\java.exe"
)

rem Типичная установка Eclipse Temurin 21.
if not defined FOXARIA_JAVA (
  for /d %%J in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do (
    if exist "%%~fJ\bin\java.exe" set "FOXARIA_JAVA=%%~fJ\bin\java.exe"
  )
)

rem Последний шанс: java из PATH (должна быть 21+ для Paper 1.21.11).
if not defined FOXARIA_JAVA (
  where java >nul 2>nul && (
    for /f "delims=" %%W in ('where java 2^>nul') do (
      set "FOXARIA_JAVA=%%W"
      goto :java_from_path
    )
  )
)
:java_from_path

if not defined FOXARIA_JAVA (
  echo JDK 21 не найден. Установи Temurin 21, задай JAVA_HOME, либо положи JDK в:
  echo   %ROOT%..\..\.tools\jdk-21\jdk-*  ^(как после скачивания JDK для Gradle^)
  pause
  exit /b 1
)

if not exist "%FOXARIA_JAVA%" (
  echo java.exe не найден: %FOXARIA_JAVA%
  pause
  exit /b 1
)

for %%I in ("%FOXARIA_JAVA%") do set "JAVA_HOME=%%~dpI.."
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Starting Foxaria test server...
java -Xms2G -Xmx4G -Dfile.encoding=UTF-8 -jar paper-1.21.11.jar nogui

echo.
echo Server stopped.
pause
