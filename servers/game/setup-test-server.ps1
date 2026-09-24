$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $root
$pluginsDir = Join-Path $root 'plugins'
$logsDir = Join-Path $root 'logs'
$foxariaJar = Join-Path $repoRoot '..\modules\foxaria-bootstrap\build\libs\foxaria-bootstrap-0.1.0-SNAPSHOT.jar'
$targetFoxariaJar = Join-Path $pluginsDir 'Foxaria.jar'
$foxariaDataDir = Join-Path $pluginsDir 'Foxaria'
$foxariaResourceRoot = Join-Path $repoRoot '..\modules\foxaria-bootstrap\src\main\resources'
$foxariaModulesSource = Join-Path $foxariaResourceRoot 'modules'
$foxariaModulesTarget = Join-Path $foxariaDataDir 'modules'
$paperJar = Join-Path $root 'paper-1.21.11.jar'
$legacyPurpurJar = Join-Path $root 'purpur-1.21.11.jar'
$serverProperties = Join-Path $root 'server.properties'
$eulaFile = Join-Path $root 'eula.txt'

New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
New-Item -ItemType Directory -Force -Path $foxariaDataDir | Out-Null
New-Item -ItemType Directory -Force -Path $foxariaModulesTarget | Out-Null

if (-not (Test-Path -LiteralPath $foxariaJar)) {
    throw "Foxaria build artifact was not found: $foxariaJar. Run .\gradlew.bat build first."
}

Copy-Item -LiteralPath $foxariaJar -Destination $targetFoxariaJar -Force
Copy-Item -LiteralPath (Join-Path $foxariaResourceRoot 'messages.yml') -Destination (Join-Path $foxariaDataDir 'messages.yml') -Force
Copy-Item -LiteralPath (Join-Path $foxariaResourceRoot 'config.yml') -Destination (Join-Path $foxariaDataDir 'config.yml') -Force
Get-ChildItem -LiteralPath $foxariaModulesSource -Filter *.yml | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $foxariaModulesTarget $_.Name) -Force
}

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
    $response = Invoke-RestMethod -Uri $buildsApi -Headers @{ 'User-Agent' = 'Foxaria-Test-Setup/1.0' }
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

if (Test-Path -LiteralPath $legacyPurpurJar) {
    Remove-Item -LiteralPath $legacyPurpurJar -Force
}

if (-not (Test-Path -LiteralPath $paperJar)) {
    Download-Paper -Version '1.21.11' -Destination $paperJar
}

$externalTargets = @(
    (Join-Path $pluginsDir 'LuckPerms-Bukkit.jar'),
    (Join-Path $pluginsDir 'GrimAC-Bukkit.jar'),
    (Join-Path $pluginsDir 'LuckPerms'),
    (Join-Path $pluginsDir 'GrimAC'),
    (Join-Path $pluginsDir '.paper-remapped')
)
foreach ($target in $externalTargets) {
    if (Test-Path -LiteralPath $target) {
        Remove-Item -LiteralPath $target -Recurse -Force
    }
}

if (-not (Test-Path -LiteralPath $serverProperties)) {
@'
motd=Foxaria Test Server
gamemode=survival
difficulty=hard
pvp=true
spawn-protection=0
max-players=20
online-mode=true
enable-command-block=false
allow-nether=true
view-distance=10
simulation-distance=10
level-type=minecraft:normal
'@ | Set-Content -LiteralPath $serverProperties -Encoding UTF8
}

if (-not (Test-Path -LiteralPath $eulaFile)) {
    'eula=false' | Set-Content -LiteralPath $eulaFile -Encoding ASCII
}

Write-Host ''
Write-Host 'Foxaria test server is ready.'
Write-Host "Server root: $root"
Write-Host 'Installed plugins:'
Get-ChildItem -LiteralPath $pluginsDir -File | Select-Object -ExpandProperty Name
