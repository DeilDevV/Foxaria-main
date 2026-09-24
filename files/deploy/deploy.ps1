# Foxaria — Деплой на удалённый Windows сервер
# Запускается автоматически Claude когда ты вписываешь данные в server-config.json

param(
    [string]$ConfigPath = "$PSScriptRoot\server-config.json"
)

$config = Get-Content $ConfigPath | ConvertFrom-Json
$host_ip  = $config.host
$username = $config.username
$password = $config.password
$port     = $config.port
$site_dir = $config.remote.site_dir
$mc_dir   = $config.remote.minecraft_dir

$plink = "C:\Program Files\PuTTY\plink.exe"
$pscp  = "C:\Program Files\PuTTY\pscp.exe"

function Remote($cmd) {
    & $plink -ssh "$username@$host_ip" -pw $password -P $port -batch $cmd
}

function Upload($local, $remote) {
    & $pscp -pw $password -P $port -r $local "${username}@${host_ip}:${remote}"
}

Write-Host "[1/6] Проверка подключения..." -ForegroundColor Cyan
Remote "echo Connected OK"

Write-Host "[2/6] Создание папок на сервере..." -ForegroundColor Cyan
Remote "powershell -command `"New-Item -ItemType Directory -Force -Path '$site_dir','$mc_dir'`""

Write-Host "[3/6] Установка Node.js (если нет)..." -ForegroundColor Cyan
Remote "powershell -command `"if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Write-Host 'Node.js не найден — установи вручную v20 LTS' } else { node --version }`""

Write-Host "[4/6] Загрузка файлов сайта..." -ForegroundColor Cyan
Upload "$PSScriptRoot\..\web\site\*" $site_dir

Write-Host "[5/6] Установка зависимостей и сборка на сервере..." -ForegroundColor Cyan
Remote "powershell -command `"cd '$site_dir'; npm install; npm run build`""

Write-Host "[6/6] Запуск backend как Windows сервис..." -ForegroundColor Cyan
Remote "powershell -command `"
    cd '$site_dir/backend'
    if (-not (Get-Command pm2 -ErrorAction SilentlyContinue)) { npm install -g pm2 }
    pm2 stop foxaria-backend 2>`$null
    pm2 start server.js --name foxaria-backend
    pm2 save
    pm2 startup
`""

Write-Host "`n=== Готово! Сайт запущен на сервере ===" -ForegroundColor Green
Remote "powershell -command `"pm2 status`""
