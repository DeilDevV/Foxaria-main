# Сборка Foxaria и выкладка в эту папку test-server (один запуск — потом только стартуй сервер).
# Требуется JDK 21 (или 17+) и JAVA_HOME, либо установка Temurin в стандартный путь ниже.
$ErrorActionPreference = "Stop"
$TestServerRoot = $PSScriptRoot
$FoxariaRoot = Split-Path $TestServerRoot -Parent

function Find-Jdk21 {
    $roots = @(
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Java",
        "${env:ProgramFiles(x86)}\Eclipse Adoptium"
    )
    foreach ($r in $roots) {
        if (-not (Test-Path $r)) { continue }
        Get-ChildItem $r -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match 'jdk-2[1-9]|jdk-17' } |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName "bin\java.exe" } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
    }
    return $null
}

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $javaExe = Find-Jdk21
    if ($javaExe) {
        $env:JAVA_HOME = Split-Path (Split-Path $javaExe) -Parent
        Write-Host "JAVA_HOME -> $env:JAVA_HOME" -ForegroundColor Cyan
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Host @"

Не найден JDK 17+. Сделай один раз:
  1) Установи Eclipse Temurin 21 LTS: https://adoptium.net/
  2) В PowerShell (или в свойствах системы -> Переменные среды) задай:
     JAVA_HOME = C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot
  3) Запусти этот скрипт снова.

Никакого отдельного .exe для Foxaria нет — только Java + Gradle.
"@ -ForegroundColor Yellow
    exit 1
}

Set-Location $FoxariaRoot
Write-Host "Сборка shadowJar..." -ForegroundColor Cyan
& "$FoxariaRoot\gradlew.bat" ":foxaria-bootstrap:shadowJar" --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$jar = Get-ChildItem "$FoxariaRoot\foxaria-bootstrap\build\libs\foxaria-bootstrap-*-SNAPSHOT.jar" |
    Where-Object { $_.Name -notmatch "sources" } |
    Select-Object -First 1
if (-not $jar) {
    Write-Host "JAR не найден в foxaria-bootstrap\build\libs\" -ForegroundColor Red
    exit 1
}

$destJar = Join-Path $TestServerRoot "plugins\Foxaria.jar"
Copy-Item -LiteralPath $jar.FullName -Destination $destJar -Force
Write-Host "Скопировано: $destJar" -ForegroundColor Green

# Модули на диске перекрывают встроенные — синхронизируем yaml из исходников репозитория
$modSrc = "$FoxariaRoot\foxaria-bootstrap\src\main\resources\modules"
$modDest = Join-Path $TestServerRoot "plugins\Foxaria\modules"
if (Test-Path $modSrc) {
    New-Item -ItemType Directory -Force -Path $modDest | Out-Null
    Copy-Item "$modSrc\*.yml" -Destination $modDest -Force
    Write-Host "Модули: $modDest" -ForegroundColor Green
}

$questsTier = "$FoxariaRoot\modules\quests.yml"
if (Test-Path $questsTier) {
    Copy-Item -LiteralPath $questsTier -Destination (Join-Path $modDest "quests.yml") -Force
    Write-Host "quests.yml (ступени): из modules\quests.yml" -ForegroundColor Green
}

Write-Host "`nГотово. Запускай сервер как обычно." -ForegroundColor Cyan
