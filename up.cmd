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

  set "BOOT_JAR=%ROOT%\foxaria-bootstrap\build\libs\foxaria-bootstrap-0.1.0-SNAPSHOT.jar"
  set "PROXY_JAR=%ROOT%\foxaria-proxy-bungee\build\libs\foxaria-proxy-bungee-0.1.0-SNAPSHOT.jar"
  set "HUB_JAR=%ROOT%\foxaria-hub-guard\build\libs\foxaria-hub-guard-0.1.0-SNAPSHOT.jar"
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
  copy /Y "!BOOT_JAR!" "%ROOT%\test-server\plugins\Foxaria.jar" >nul
  copy /Y "!PROXY_JAR!" "%ROOT%\proxy-bungeecord\plugins\FoxariaProxy.jar" >nul
  if not exist "%ROOT%\proxy-bungeecord\plugins\FoxariaProxy" mkdir "%ROOT%\proxy-bungeecord\plugins\FoxariaProxy" >nul 2>nul
  copy /Y "%ROOT%\foxaria-proxy-bungee\src\main\resources\config.yml" "%ROOT%\proxy-bungeecord\plugins\FoxariaProxy\config.yml" >nul
  if not exist "%ROOT%\lobby-server\plugins" mkdir "%ROOT%\lobby-server\plugins" >nul 2>nul
  if not exist "%ROOT%\auth-server\plugins" mkdir "%ROOT%\auth-server\plugins" >nul 2>nul

  rem Keep server plugins isolated: test keeps Foxaria, lobby/auth keep HubGuard only
  del /Q "%ROOT%\lobby-server\plugins\*.jar" >nul 2>nul
  del /Q "%ROOT%\auth-server\plugins\*.jar" >nul 2>nul
  copy /Y "!HUB_JAR!" "%ROOT%\lobby-server\plugins\FoxariaHubGuard.jar" >nul
  copy /Y "!HUB_JAR!" "%ROOT%\auth-server\plugins\FoxariaHubGuard.jar" >nul

  if not exist "%ROOT%\lobby-server\plugins\FoxariaHubGuard" mkdir "%ROOT%\lobby-server\plugins\FoxariaHubGuard" >nul 2>nul
  if not exist "%ROOT%\auth-server\plugins\FoxariaHubGuard" mkdir "%ROOT%\auth-server\plugins\FoxariaHubGuard" >nul 2>nul
  copy /Y "%ROOT%\foxaria-hub-guard\src\main\resources\config.yml" "%ROOT%\lobby-server\plugins\FoxariaHubGuard\config.yml" >nul
  copy /Y "%ROOT%\foxaria-hub-guard\src\main\resources\config.yml" "%ROOT%\auth-server\plugins\FoxariaHubGuard\config.yml" >nul
  powershell -NoProfile -Command ^
    "(Get-Content '%ROOT%\auth-server\plugins\FoxariaHubGuard\config.yml') -replace '^mode:\s*lobby$','mode: auth' | Set-Content '%ROOT%\auth-server\plugins\FoxariaHubGuard\config.yml' -Encoding UTF8"

  if not exist "%ROOT%\test-server\plugins\Foxaria\modules" mkdir "%ROOT%\test-server\plugins\Foxaria\modules" >nul 2>nul
  copy /Y "%ROOT%\foxaria-bootstrap\src\main\resources\messages.yml" "%ROOT%\test-server\plugins\Foxaria\messages.yml" >nul
  copy /Y "%ROOT%\foxaria-bootstrap\src\main\resources\config.yml" "%ROOT%\test-server\plugins\Foxaria\config.yml" >nul
  copy /Y "%ROOT%\foxaria-bootstrap\src\main\resources\modules\*.yml" "%ROOT%\test-server\plugins\Foxaria\modules\" >nul
  copy /Y "%ROOT%\foxaria-modern-furnace\src\main\resources\modules\modern-furnace.yml" "%ROOT%\test-server\plugins\Foxaria\modules\modern-furnace.yml" >nul

  rem Keep proxy-aware player flow on game backend
  powershell -NoProfile -Command ^
    "(Get-Content '%ROOT%\test-server\plugins\Foxaria\modules\player-flow.yml') -replace '^proxy-auth-handled:\s*false$','proxy-auth-handled: true' | Set-Content '%ROOT%\test-server\plugins\Foxaria\modules\player-flow.yml' -Encoding UTF8"
) else (
  echo.
  echo [1/5] Build skipped ^(--no-build^)
)

echo [3/5] Ensuring backend Paper jars...
if not exist "%ROOT%\auth-server\paper.jar" (
  if exist "%ROOT%\test-server\paper-1.21.11.jar" (
    copy /Y "%ROOT%\test-server\paper-1.21.11.jar" "%ROOT%\auth-server\paper.jar" >nul
  )
)
if not exist "%ROOT%\auth-server\auth_void" (
  if exist "%ROOT%\test-server\auth_void" (
    xcopy /E /I /Y "%ROOT%\test-server\auth_void" "%ROOT%\auth-server\auth_void" >nul
  )
)
if not exist "%ROOT%\lobby-server\paper.jar" (
  if exist "%ROOT%\test-server\paper-1.21.11.jar" (
    copy /Y "%ROOT%\test-server\paper-1.21.11.jar" "%ROOT%\lobby-server\paper.jar" >nul
  )
)

echo [4/5] Stopping existing listeners (25565-25568)...
for %%S in (auth-server lobby-server test-server proxy-bungeecord) do (
  powershell -NoProfile -Command ^
    "$name='%%S'; $root='%ROOT%'; Get-CimInstance Win32_Process | Where-Object {($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -like ('*' + $root + '\' + $name + '*')} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
)
for %%P in (25565 25566 25567 25568) do call :kill_port %%P

echo [5/5] Starting servers...
start "FOXARIA-AUTH" /D "%ROOT%\auth-server" cmd /c start-auth.bat
start "FOXARIA-LOBBY" /D "%ROOT%\lobby-server" cmd /c start-lobby.bat
start "FOXARIA-GAME" /D "%ROOT%\test-server" cmd /c start-test-server.bat
timeout /t 2 >nul
start "FOXARIA-PROXY" /D "%ROOT%\proxy-bungeecord" cmd /c start-proxy.bat

echo.
echo Done. Started: auth, lobby, test-server, proxy.
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

