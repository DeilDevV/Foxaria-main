@echo off
setlocal

REM Foxaria Lobby backend start (Paper)
REM 1) Положи Paper jar под именем paper.jar
REM 2) При необходимости измени RAM

set JAVA_EXE=
set RAM_MIN=1G
set RAM_MAX=2G
set JAR=paper.jar

if exist "%~dp0jdk\bin\java.exe" set JAVA_EXE=%~dp0jdk\bin\java.exe
if "%JAVA_EXE%"=="" if exist "%~dp0..\.jdks\jdk-21.0.10+7\bin\java.exe" set JAVA_EXE=%~dp0..\.jdks\jdk-21.0.10+7\bin\java.exe
if "%JAVA_EXE%"=="" if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if "%JAVA_EXE%"=="" set JAVA_EXE=java

if not exist "%JAR%" (
  echo [ERROR] Не найден "%JAR%" в папке lobby-server
  echo Положи сюда Paper jar под именем "%JAR%".
  pause
  exit /b 1
)

"%JAVA_EXE%" -version
if errorlevel 1 (
  echo [ERROR] Не удалось запустить Java.
  echo Установи Java 21+ и укажи JAVA_HOME, либо положи JDK в lobby-server\jdk
  pause
  exit /b 1
)

"%JAVA_EXE%" -Xms%RAM_MIN% -Xmx%RAM_MAX% -jar "%JAR%" --nogui
pause

