$ErrorActionPreference = "Stop"

# Foxaria Proxy start (BungeeCord)
# 1) Положи рядом файл BungeeCord.jar
# 2) При необходимости измени RAM

$Java = ""
$RamMin = "512M"
$RamMax = "1024M"
$Jar = "BungeeCord.jar"

if (Test-Path ".\jdk\bin\java.exe") {
  $Java = ".\jdk\bin\java.exe"
} elseif (Test-Path "..\.jdks\jdk-21.0.10+7\bin\java.exe") {
  $Java = "..\.jdks\jdk-21.0.10+7\bin\java.exe"
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
  $Java = (Join-Path $env:JAVA_HOME "bin\java.exe")
} else {
  $Java = "java"
}

if (!(Test-Path -Path $Jar)) {
  Write-Host "[ERROR] Не найден '$Jar' в папке proxy-bungeecord"
  Write-Host "Скачай BungeeCord jar и положи сюда под именем '$Jar'."
  Read-Host "Нажми Enter чтобы выйти" | Out-Null
  exit 1
}

& $Java "-version"
if ($LASTEXITCODE -ne 0) {
  Write-Host "[ERROR] Не удалось запустить Java."
  Write-Host "Установи Java 21+ и укажи JAVA_HOME, либо положи JDK в proxy-bungeecord\jdk"
  Read-Host "Нажми Enter чтобы выйти" | Out-Null
  exit 1
}

& $Java "-Xms$RamMin" "-Xmx$RamMax" "-jar" $Jar
Read-Host "Нажми Enter чтобы закрыть окно" | Out-Null

