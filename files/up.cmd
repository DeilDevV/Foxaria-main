@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Foxaria one-shot update + start script
rem Usage:
rem   up.cmd            -> update jars + restart all servers
rem   up.cmd --no-build -> only restart all servers

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
cd /d "%ROOT%"

set "DO_BUILD=1"
if /I "%~1"=="--no-build" set "DO_BUILD=0"

rem --- Структура репозитория ---
set "GAME=%ROOT%\servers\game"
set "PROXY=%ROOT%\servers\proxy"
set "LOBBY=%ROOT%\servers\lobby"
set "AUTH=%ROOT%\servers\auth"
set "MODS=%ROOT%\modules"
set "OUT=%ROOT%\.gradle-build"

echo.
echo === FOXARIA UP ===
echo Root: %ROOT%

call :resolve_java
if errorlevel 1 exit /b 1

if "%DO_BUILD%"=="1" (
  echo.
  echo [1/5] Building Foxaria jars...
  call gradlew.bat :foxaria-bootstrap:shadowJar :foxaria-proxy-bungee:shadowJar :foxaria-hub-guard:shadowJar --no-daemon -q
  if errorlevel 1 (
    echo [ERROR] Gradle build failed.
    exit /b 1
  )

  set "BOOT_JAR=%OUT%\foxaria-bootstrap\libs\foxaria-bootstrap-0.1.0-SNAPSHOT.jar"
  set "PROXY_JAR=%OUT%\foxaria-proxy-bungee\libs\foxaria-proxy-bungee-0.1.0-SNAPSHOT.jar"
  set "HUB_JAR=%OUT%\foxaria-hub-guard\libs\foxaria-hub-guard-0.1.0-SNAPSHOT.jar"
  if not exist "!BOOT_JAR!" (
    echo [ERROR] Bootstrap jar not found: !BOOT_JAR!
    exit /b 1
  )
  if not exist "!PROXY_JAR!" (
    echo [ERROR] Proxy jar not found: !PROXY_JAR!
    exit /b 1
  )
  if not exist "!HUB_JAR!" (
    echo [ERROR] HubGuard jar not found: !HUB_JAR!
    exit /b 1
  )

  echo [2/5] Deploying jars/config...
  copy /Y "!BOOT_JAR!" "%GAME%\plugins\Foxaria.jar" >nul
  copy /Y "!PROXY_JAR!" "%PROXY%\plugins\FoxariaProxy.jar" >nul
  if not exist "%PROXY%\plugins\FoxariaProxy" mkdir "%PROXY%\plugins\FoxariaProxy" >nul 2>nul
  copy /Y "%MODS%\foxaria-proxy-bungee\src\main\resources\config.yml" "%PROXY%\plugins\FoxariaProxy\config.yml" >nul
  if not exist "%LOBBY%\plugins" mkdir "%LOBBY%\plugins" >nul 2>nul
  if not exist "%AUTH%\plugins" mkdir "%AUTH%\plugins" >nul 2>nul

  rem Keep server plugins isolated: game keeps Foxaria, lobby/auth keep HubGuard only
  del /Q "%LOBBY%\plugins\*.jar" >nul 2>nul
  del /Q "%AUTH%\plugins\*.jar" >nul 2>nul
  copy /Y "!HUB_JAR!" "%LOBBY%\plugins\FoxariaHubGuard.jar" >nul
  copy /Y "!HUB_JAR!" "%AUTH%\plugins\FoxariaHubGuard.jar" >nul

  if not exist "%LOBBY%\plugins\FoxariaHubGuard" mkdir "%LOBBY%\plugins\FoxariaHubGuard" >nul 2>nul
  if not exist "%AUTH%\plugins\FoxariaHubGuard" mkdir "%AUTH%\plugins\FoxariaHubGuard" >nul 2>nul
  copy /Y "%MODS%\foxaria-hub-guard\src\main\resources\config.yml" "%LOBBY%\plugins\FoxariaHubGuard\config.yml" >nul
  copy /Y "%MODS%\foxaria-hub-guard\src\main\resources\config.yml" "%AUTH%\plugins\FoxariaHubGuard\config.yml" >nul
  powershell -NoProfile -Command ^
    "(Get-Content '%AUTH%\plugins\FoxariaHubGuard\config.yml') -replace '^mode:\s*lobby$','mode: auth' | Set-Content '%AUTH%\plugins\FoxariaHubGuard\config.yml' -Encoding UTF8"

  if not exist "%GAME%\plugins\Foxaria\modules" mkdir "%GAME%\plugins\Foxaria\modules" >nul 2>nul
  copy /Y "%MODS%\foxaria-bootstrap\src\main\resources\messages.yml" "%GAME%\plugins\Foxaria\messages.yml" >nul
  copy /Y "%MODS%\foxaria-bootstrap\src\main\resources\config.yml" "%GAME%\plugins\Foxaria\config.yml" >nul
  copy /Y "%MODS%\foxaria-bootstrap\src\main\resources\modules\*.yml" "%GAME%\plugins\Foxaria\modules\" >nul
  copy /Y "%MODS%\foxaria-modern-furnace\src\main\resources\modules\modern-furnace.yml" "%GAME%\plugins\Foxaria\modules\modern-furnace.yml" >nul

  rem Keep proxy-aware player flow on game backend
  powershell -NoProfile -Command ^
    "(Get-Content '%GAME%\plugins\Foxaria\modules\player-flow.yml') -replace '^proxy-auth-handled:\s*false$','proxy-auth-handled: true' | Set-Content '%GAME%\plugins\Foxaria\modules\player-flow.yml' -Encoding UTF8"
) else (
  echo.
  echo [1/5] Build skipped ^(--no-build^)
)

echo [3/5] Ensuring backend Paper jars...
if not exist "%AUTH%\paper.jar" (
  if exist "%GAME%\paper-1.21.11.jar" (
    copy /Y "%GAME%\paper-1.21.11.jar" "%AUTH%\paper.jar" >nul
  )
)
if not exist "%AUTH%\auth_void" (
  if exist "%GAME%\auth_void" (
    xcopy /E /I /Y "%GAME%\auth_void" "%AUTH%\auth_void" >nul
  )
)
if not exist "%LOBBY%\paper.jar" (
  if exist "%GAME%\paper-1.21.11.jar" (
    copy /Y "%GAME%\paper-1.21.11.jar" "%LOBBY%\paper.jar" >nul
  )
)

echo [4/5] Stopping old server processes (ports 25565-25568)...
for %%S in (auth lobby game proxy) do (
  powershell -NoProfile -Command ^
    "$name='%%S'; $root='%ROOT%\servers'; Get-CimInstance Win32_Process | Where-Object {($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -like ('*' + $root + '\' + $name + '*')} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
)
for %%P in (25565 25566 25567 25568) do call :kill_port %%P

echo [5/5] Starting servers...
start "FOXARIA-AUTH" /D "%AUTH%" cmd /c start-auth.bat
start "FOXARIA-LOBBY" /D "%LOBBY%" cmd /c start-lobby.bat
start "FOXARIA-GAME" /D "%GAME%" cmd /c start-test-server.bat
timeout /t 2 >nul
start "FOXARIA-PROXY" /D "%PROXY%" cmd /c start-proxy.bat

echo.
echo Done. Started: auth, lobby, game, proxy.
echo If needed: run "up.cmd --no-build" for quick restart only.
exit /b 0

:resolve_java
set "JAVA_EXE="
if exist "%ROOT%\.jdks\jdk-21.0.10+7\bin\java.exe" set "JAVA_EXE=%ROOT%\.jdks\jdk-21.0.10+7\bin\java.exe"
if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
  where java >nul 2>nul && (
    for /f "delims=" %%J in ('where java 2^>nul') do (
      set "JAVA_EXE=%%J"
      goto :java_found
    )
  )
)
:java_found
if not defined JAVA_EXE (
  echo [ERROR] Java not found. Install JDK 21 or set JAVA_HOME.
  exit /b 1
)
for %%I in ("%JAVA_EXE%") do set "JAVA_HOME=%%~dpI.."
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME=%JAVA_HOME%
exit /b 0

:kill_port
set "PORT=%~1"
for /f "tokens=5" %%K in ('netstat -ano ^| findstr /R /C:":%PORT% .*LISTENING"') do (
  echo   - Killing PID %%K on port %PORT%
  taskkill /F /PID %%K >nul 2>nul
)
exit /b 0
