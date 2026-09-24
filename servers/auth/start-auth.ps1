$ErrorActionPreference = "Stop"

# Foxaria Auth backend start (Paper)
# 1) Положи Paper jar под именем paper.jar
# 2) При необходимости измени RAM

$Java = ""
$RamMin = "1G"
$RamMax = "2G"
$Jar = "paper.jar"

if (Test-Path ".\jdk\bin\java.exe") {
  $Java = ".\jdk\bin\java.exe"
} elseif (Test-Path "..\..\.jdks\jdk-21.0.10+7\bin\java.exe") {
  $Java = "..\..\.jdks\jdk-21.0.10+7\bin\java.exe"
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
  $Java = (Join-Path $env:JAVA_HOME "bin\java.exe")
} else {
  $Java = "java"
}

if (!(Test-Path -Path $Jar)) {
  Write-Host "[ERROR] Не найден '$Jar' в папке servers\auth"
  Write-Host "Положи сюда Paper jar под именем '$Jar'."
  Read-Host "Нажми Enter чтобы выйти" | Out-Null
  exit 1
}

& $Java "-version"
if ($LASTEXITCODE -ne 0) {
  Write-Host "[ERROR] Не удалось запустить Java."
  Write-Host "Установи Java 21+ и укажи JAVA_HOME, либо положи JDK в servers\auth\jdk"
  Read-Host "Нажми Enter чтобы выйти" | Out-Null
  exit 1
}

& $Java "-Xms$RamMin" "-Xmx$RamMax" "-jar" $Jar "--nogui"
Read-Host "Нажми Enter чтобы закрыть окно" | Out-Null

