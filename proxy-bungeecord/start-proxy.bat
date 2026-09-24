@echo off
setlocal

REM Foxaria Proxy start (BungeeCord)
REM 1) Положи рядом файл BungeeCord.jar
REM 2) При необходимости измени RAM

set JAVA_EXE=
set RAM_MIN=512M
set RAM_MAX=1024M
set JAR=BungeeCord.jar

if exist "%~dp0jdk\bin\java.exe" set JAVA_EXE=%~dp0jdk\bin\java.exe
if "%JAVA_EXE%"=="" if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if "%JAVA_EXE%"=="" if exist "%~dp0..\.jdks\jdk-21.0.10+7\bin\java.exe" set JAVA_EXE=%~dp0..\.jdks\jdk-21.0.10+7\bin\java.exe
if "%JAVA_EXE%"=="" set JAVA_EXE=java

if not exist "%JAR%" (
  echo [ERROR] Не найден "%JAR%" в папке proxy-bungeecord
  echo Скачай BungeeCord jar и положи сюда под именем "%JAR%".
  pause
  exit /b 1
)

"%JAVA_EXE%" -version
if errorlevel 1 (
  echo [ERROR] Не удалось запустить Java.
  echo Установи Java 21+ и укажи JAVA_HOME, либо положи JDK в proxy-bungeecord\jdk
  pause
  exit /b 1
)

REM Avoid "Address already in use" on proxy port 25565
for /f "delims=" %%P in ('powershell -NoProfile -Command "$p=(Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess); if($p){$p}"') do (
  echo [INFO] Port 25565 already in use by PID %%P. Stopping old process...
  taskkill /F /PID %%P >nul 2>nul
  timeout /t 1 >nul
)

"%JAVA_EXE%" -Xms%RAM_MIN% -Xmx%RAM_MAX% -jar "%JAR%"
if errorlevel 1 (
  echo [ERROR] Proxy stopped with error.
)
pause

