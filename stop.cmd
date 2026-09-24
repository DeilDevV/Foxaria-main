@echo off
setlocal EnableExtensions

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

for %%S in (auth lobby game proxy) do (
  powershell -NoProfile -Command "$name='%%S'; $root='%ROOT%\servers'; Get-CimInstance Win32_Process | Where-Object {($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -like ('*' + $root + '\' + $name + '*')} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
)

for %%P in (25565 25566 25567 25568) do (
  for /f "tokens=5" %%K in ('netstat -ano ^| findstr /R /C:":%%P .*LISTENING"') do (
    taskkill /F /PID %%K >nul 2>nul
  )
)

echo Foxaria servers stopped.
