@echo off
echo.
echo  ======================================
echo   FOXARIA SITE - Запуск
echo  ======================================
echo.

echo  Останавливаем старые процессы...
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr ":3001 " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr ":5173 " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>&1
timeout /t 1 /nobreak >nul

echo  Запуск Backend (порт 3001)...
start "Foxaria API" cmd /k "cd /d %~dp0backend && node server.js"
timeout /t 3 /nobreak >nul

echo  Запуск Frontend (порт 5173)...
start "Foxaria Frontend" cmd /k "cd /d %~dp0frontend && npm run dev"

echo.
echo  Подождите 5 секунд, открываем браузер...
timeout /t 5 /nobreak >nul
start "" http://localhost:5173

echo.
echo  Сайт: http://localhost:5173
echo  API:  http://localhost:3001
echo.
