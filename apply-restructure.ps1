<#
.SYNOPSIS
  Foxaria — реструктуризация репозитория (модули -> modules/, серверы -> servers/).
  ЗАПУСК: из корня клонa Foxaria-main:  powershell -ExecutionPolicy Bypass -File <путь>\apply-restructure.ps1
  Работает через git mv — история файлов сохраняется. Идемпотентен.
#>
$ErrorActionPreference = 'Stop'
$Kit = $PSScriptRoot

if (-not (Test-Path 'settings.gradle') -or -not (Test-Path 'build.gradle')) {
  throw 'Запусти скрипт ИЗ КОРНЯ репозитория Foxaria-main (рядом должны быть settings.gradle и build.gradle).'
}
if (-not (Test-Path '.git')) { throw 'Папка .git не найдена — нужен именно git-клон, чтобы сохранить историю.' }

function Move-IfExists([string]$Src, [string]$Dst) {
  if (Test-Path $Dst) { Write-Host "  skip (уже есть): $Dst" -ForegroundColor DarkGray; return }
  if (-not (Test-Path $Src)) { Write-Host "  skip (не найдено): $Src" -ForegroundColor DarkGray; return }
  $parent = Split-Path $Dst -Parent
  if ($parent -and -not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
  git mv $Src $Dst | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "git mv не удался: $Src -> $Dst (закрой IDEA/блокирующие процессы и повтори)" }
  Write-Host "  $Src  ->  $Dst" -ForegroundColor Green
}

Write-Host '[1/4] Переношу папки (git mv)...' -ForegroundColor Cyan
Move-IfExists 'modules\quests.yml' 'config\gameplay\quests.yml'
Get-ChildItem -Directory -Filter 'foxaria-*' | ForEach-Object { Move-IfExists $_.Name "modules\$($_.Name)" }
Move-IfExists 'auth-server' 'servers\auth'
Move-IfExists 'lobby-server' 'servers\lobby'
Move-IfExists 'test-server' 'servers\game'
Move-IfExists 'proxy-bungeecord' 'servers\proxy'
Move-IfExists 'production-server-template' 'servers\production-template'
Move-IfExists 'SITE-FOXARIA' 'web\site'
Move-IfExists 'deploy.sh' 'deploy\deploy.sh'
Move-IfExists 'ecosystem.config.js' 'deploy\ecosystem.config.js'

Write-Host '[2/4] Удаляю мусор .playwright-mcp...' -ForegroundColor Cyan
if (Test-Path '.playwright-mcp') {
  git rm -r -q --cached .playwright-mcp 2>$null
  Remove-Item -Recurse -Force .playwright-mcp
}

Write-Host '[3/4] Обновляю файлы конфигурации (37 шт.)...' -ForegroundColor Cyan
Copy-Item -Recurse -Force "$Kit\files\*" .
Copy-Item -Force "$Kit\files\.gitignore" . -ErrorAction SilentlyContinue
Copy-Item -Force "$Kit\files\.gitattributes" . -ErrorAction SilentlyContinue

Write-Host '[4/4] Готово. Что дальше:' -ForegroundColor Cyan
Write-Host ''
Write-Host '  1) git status        -- посмотреть переименования'
Write-Host '  2) gradlew.bat :foxaria-bootstrap:shadowJar   -- проверить сборку'
Write-Host '  3) up.cmd            -- сборка + перезапуск всех серверов'
Write-Host ''
Write-Host 'Сборка кладёт jar-ы автоматически в servers/*/plugins (как и раньше).' -ForegroundColor Yellow
