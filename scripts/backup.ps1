# ==============================================================================
# FoodCore ERP v1.0 - Backup Automatizado MySQL 8.0 (SPEC-FOODCORE-v1.0 §17.3, R-16)
# Ejecuta: mysqldump --single-transaction --routines foodcore | gzip > backups/foodcore_YYYYMMDD.sql.gz
# ==============================================================================
param(
    [string]$DbName = "foodcore",
    [string]$DbUser = $(if ($env:FOODCORE_MIGRATOR_USER) { $env:FOODCORE_MIGRATOR_USER } else { "foodcore_migrator" }),
    [string]$DbPass = $(if ($env:FOODCORE_MIGRATOR_PASS) { $env:FOODCORE_MIGRATOR_PASS } else { "FoodCoreMigrator#2026!" })
)
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BackupDir   = Join-Path $ProjectRoot "backups"
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$Fecha      = Get-Date -Format "yyyyMMdd"
$SqlRawFile = Join-Path $BackupDir "foodcore_${Fecha}.sql"
$GzFile     = Join-Path $BackupDir "foodcore_${Fecha}.sql.gz"

$MysqlDumpExe = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe"
if (-not (Test-Path $MysqlDumpExe)) {
    $MysqlDumpExe = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
}

& $MysqlDumpExe -h 127.0.0.1 -u $DbUser "-p$DbPass" `
    --single-transaction --no-tablespaces --routines --triggers $DbName `
    --result-file="$SqlRawFile"

if ($LASTEXITCODE -ne 0) {
    throw "mysqldump fallo con codigo de salida $LASTEXITCODE"
}

# Comprimir con GZip (.sql.gz)
$inStream  = [System.IO.File]::OpenRead($SqlRawFile)
$outStream = [System.IO.File]::Create($GzFile)
$gzip      = New-Object System.IO.Compression.GzipStream($outStream, [System.IO.Compression.CompressionMode]::Compress)
$inStream.CopyTo($gzip)
$gzip.Close()
$outStream.Close()
$inStream.Close()
Remove-Item -Force $SqlRawFile

Write-Host "[OK] Backup generado: $GzFile"
