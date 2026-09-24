@echo off
setlocal
rem Скрипт лежит в servers\game — корень репозитория на два уровня выше.
set "REPO=%~dp0..\.."
set "TS=%~dp0"
cd /d "%REPO%"

rem Если нет JAVA_HOME — берём portable JDK из репозитория (.jdks\jdk-21*)
if not defined JAVA_HOME (
  for /d %%J in ("%REPO%\.jdks\jdk-21*") do (
    set "JAVA_HOME=%%~fJ"
    goto :jdk_ok
  )
)
:jdk_ok
if defined JAVA_HOME (
  echo Using JAVA_HOME=%JAVA_HOME%
  set "PATH=%JAVA_HOME%\bin;%PATH%"
) else (
  echo WARNING: JAVA_HOME not set and no %REPO%\.jdks\jdk-21* — need JDK 17+ for Gradle.
)

echo Building Foxaria shadow JAR...
call gradlew.bat :foxaria-bootstrap:shadowJar --no-daemon
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

rem buildDir модулей вынесен в корневой .gradle-build (см. корневой build.gradle)
set "JAR=%REPO%\.gradle-build\foxaria-bootstrap\libs\foxaria-bootstrap-0.1.0-SNAPSHOT.jar"
set "PL=%TS%plugins"
set "FD=%PL%\Foxaria"
set "MD=%FD%\modules"
set "RES=%REPO%\modules\foxaria-bootstrap\src\main\resources"

if not exist "%MD%" mkdir "%MD%"
copy /Y "%JAR%" "%PL%\Foxaria.jar"
copy /Y "%RES%\messages.yml" "%FD%\messages.yml"
copy /Y "%RES%\config.yml" "%FD%\config.yml"
copy /Y "%RES%\modules\*.yml" "%MD%\"

echo.
echo OK: Foxaria.jar + config + messages + modules\*.yml copied to servers\game\plugins
exit /b 0
