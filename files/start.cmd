@echo off
setlocal
rem Быстрый запуск всех серверов БЕЗ пересборки плагинов.
rem Полная сборка + запуск: up.cmd   |   Остановка: stop.cmd
call "%~dp0up.cmd" --no-build %*
