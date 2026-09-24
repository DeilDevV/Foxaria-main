$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $root
$pluginsDir = Join-Path $root 'plugins'
$logsDir = Join-Path $root 'logs'
$foxariaJar = Join-Path $repoRoot 'foxaria-bootstrap\build\libs\foxaria-bootstrap-0.1.0-SNAPSHOT.jar'
$targetFoxariaJar = Join-Path $pluginsDir 'Foxaria.jar'
$foxariaDataDir = Join-Path $pluginsDir 'Foxaria'
$foxariaResourceRoot = Join-Path $repoRoot 'foxaria-bootstrap\src\main\resources'
$foxariaModulesSource = Join-Path $foxariaResourceRoot 'modules'
$foxariaModulesTarget = Join-Path $foxariaDataDir 'modules'
$paperJar = Join-Path $root 'paper-1.21.11.jar'
$serverProperties = Join-Path $root 'server.properties'
$eulaFile = Join-Path $root 'eula.txt'
$readmeFile = Join-Path $root 'README.md'

New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
New-Item -ItemType Directory -Force -Path $foxariaDataDir | Out-Null
New-Item -ItemType Directory -Force -Path $foxariaModulesTarget | Out-Null

if (-not (Test-Path -LiteralPath $foxariaJar)) {
  throw "Foxaria build artifact was not found: $foxariaJar. Run .\gradlew.bat build first."
}

Copy-Item -LiteralPath $foxariaJar -Destination $targetFoxariaJar -Force
Copy-Item -LiteralPath (Join-Path $foxariaResourceRoot 'messages.yml') -Destination (Join-Path $foxariaDataDir 'messages.yml') -Force
Get-ChildItem -LiteralPath $foxariaModulesSource -Filter *.yml | ForEach-Object {
  Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $foxariaModulesTarget $_.Name) -Force
}

@'
environment: prod
debug: false
database:
  type: mysql
  file: foxaria.db
  host: 127.0.0.1
  port: 3306
  name: foxaria
  username: foxaria
  password: Grnkbq2GS7rBj71TWmx
  pool:
    maximum-size: 10
    minimum-idle: 2
  executor-threads: 4
teleport-warmup:
  ticks: 60
combat-tag:
  duration-seconds: 15
  kill-on-logout: true
spawn-protection:
  enabled: true
  world: world
  radius: 64
  block-chorus-into-spawn: true
homes:
  default-limit: 2
tpa:
  request-expiry-seconds: 60
rtp:
  world: world
  radius: 5000
  min-distance: 200
  max-attempts: 24
nether-roof-protection:
  enabled: true
  max-y: 127.0
lag-protection:
  cleanup-interval-ticks: 1200
  item-despawn-ticks: 2400
  chunk-entity-limit: 180
economy:
  transfer-fee-percent: 2.5
'@ | Set-Content -LiteralPath (Join-Path $foxariaDataDir 'config.yml') -Encoding UTF8

function Download-File {
  param(
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $true)][string]$Destination
  )

  Write-Host "Downloading $Url"
  Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
}

function Download-Paper {
  param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$Destination
  )

  $buildsApi = "https://api.papermc.io/v2/projects/paper/versions/$Version/builds"
  $response = Invoke-RestMethod -Uri $buildsApi -Headers @{ 'User-Agent' = 'Foxaria-Production-Setup/1.0' }
  if (-not $response.builds -or $response.builds.Count -eq 0) {
    throw "No Paper builds were returned for version $Version"
  }

  $latest = $response.builds |
  Where-Object { $_.channel -eq 'default' -and $_.downloads.application.name } |
  Sort-Object build -Descending |
  Select-Object -First 1

  if (-not $latest) {
    throw "No downloadable Paper application build was found for version $Version"
  }

  $downloadUrl = "https://api.papermc.io/v2/projects/paper/versions/$Version/builds/$($latest.build)/downloads/$($latest.downloads.application.name)"
  Download-File -Url $downloadUrl -Destination $Destination
}

if (-not (Test-Path -LiteralPath $paperJar)) {
  Download-Paper -Version '1.21.11' -Destination $paperJar
}

@'
motd=Foxaria Semi-Anarchy
gamemode=survival
difficulty=hard
pvp=true
spawn-protection=0
max-players=150
online-mode=false
enable-command-block=false
allow-nether=true
view-distance=8
simulation-distance=8
level-type=minecraft:normal
white-list=false
enforce-secure-profile=true
'@ | Set-Content -LiteralPath $serverProperties -Encoding UTF8

if (-not (Test-Path -LiteralPath $eulaFile)) {
  'eula=false' | Set-Content -LiteralPath $eulaFile -Encoding ASCII
}

@'
# Foxaria Production Server Template

Эта папка подготовлена как production-шаблон под Foxaria на `Paper 1.21.11`.

## Что уже настроено

- `online-mode=true`
- `environment: prod`
- Foxaria копируется в `plugins/Foxaria.jar`
- создаётся production `plugins/Foxaria/config.yml`
- подхватываются русские `messages.yml` и `modules/*.yml`

## Что обязательно поменять перед релизом

1. В `plugins/Foxaria/config.yml` укажи реальные данные MySQL:
   - `database.host`
   - `database.port`
   - `database.name`
   - `database.username`
   - `database.password`
2. Подпиши EULA в `eula.txt`
3. Настрой `server.properties` под лимиты хоста
4. Выдай себе админ-права после первого входа:
   - `op <ник>`
   - `rank set <ник> admin`

## Важно

- Для production не оставляй SQLite
- Для публичного сервера не выключай `online-mode`
- Если используешь reverse proxy, настраивай Paper/Velocity отдельно
'@ | Set-Content -LiteralPath $readmeFile -Encoding UTF8

Write-Host ''
Write-Host 'Foxaria production template is ready.'
Write-Host "Server root: $root"
Write-Host 'Installed plugins:'
Get-ChildItem -LiteralPath $pluginsDir -File | Select-Object -ExpandProperty Name
