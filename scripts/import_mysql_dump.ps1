param(
    [Parameter(Mandatory = $true)]
    [string]$Host,

    [int]$Port = 3306,

    [Parameter(Mandatory = $true)]
    [string]$Database,

    [Parameter(Mandatory = $true)]
    [string]$User,

    [Parameter(Mandatory = $true)]
    [string]$Password,

    [string]$DumpPath = ".\scripts\generated\foxaria_mysql_dump.sql",

    [string]$MysqlPath = "mysql"
)

$resolvedDump = Resolve-Path -LiteralPath $DumpPath -ErrorAction Stop

if (-not (Get-Command $MysqlPath -ErrorAction SilentlyContinue)) {
    throw "MySQL client '$MysqlPath' not found. Install MySQL client or pass -MysqlPath with the full path to mysql.exe."
}

Write-Host "Importing dump '$resolvedDump' into $Database on $Host`:$Port ..."

$env:MYSQL_PWD = $Password
try {
    Get-Content -LiteralPath $resolvedDump | & $MysqlPath `
        --host=$Host `
        --port=$Port `
        --user=$User `
        --default-character-set=utf8mb4 `
        $Database

    if ($LASTEXITCODE -ne 0) {
        throw "mysql exited with code $LASTEXITCODE"
    }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Host "Import completed successfully."
