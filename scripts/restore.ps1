# ==============================================================================
# FoodCore ERP v1.0 - Restore Automatizado MySQL 8.0 (SPEC-FOODCORE-v1.0 §17.3, R-16)
# ==============================================================================
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$DbName = "foodcore",
    [string]$DbUser = $(if ($env:FOODCORE_MIGRATOR_USER) { $env:FOODCORE_MIGRATOR_USER } else { "foodcore_migrator" }),
    [string]$DbPass = $(if ($env:FOODCORE_MIGRATOR_PASS) { $env:FOODCORE_MIGRATOR_PASS } else { "FoodCoreMigrator#2026!" })
)
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$LogDir      = Join-Path $ProjectRoot "logs"
if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

$Fecha   = Get-Date -Format "yyyyMMdd"
$LogFile = Join-Path $LogDir "restore_${Fecha}.log"

$MysqlExe = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
if (-not (Test-Path $MysqlExe)) {
    $MysqlExe = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
}

$TmpSql = Join-Path $LogDir "tmp_restore_${Fecha}.sql"

# Descomprimir .sql.gz
$inStream  = [System.IO.File]::OpenRead((Resolve-Path $BackupFile).Path)
$gzip      = New-Object System.IO.Compression.GzipStream($inStream, [System.IO.Compression.CompressionMode]::Decompress)
$outStream = [System.IO.File]::Create($TmpSql)
$gzip.CopyTo($outStream)
$outStream.Close()
$gzip.Close()
$inStream.Close()

$Timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ssK"
"[$Timestamp] Restaurando $BackupFile sobre base de datos $DbName..." | Tee-Object -FilePath $LogFile -Append

cmd.exe /c "`"$MysqlExe`" -h 127.0.0.1 -u $DbUser -p`"$DbPass`" $DbName < `"$TmpSql`""
if ($LASTEXITCODE -ne 0) {
    Remove-Item -Force $TmpSql -ErrorAction SilentlyContinue
    throw "Restauracion mysql fallo con codigo $LASTEXITCODE"
}

Remove-Item -Force $TmpSql -ErrorAction SilentlyContinue
$TimestampEnd = Get-Date -Format "yyyy-MM-ddTHH:mm:ssK"
"[$TimestampEnd] Restauracion de $BackupFile completada exitosamente." | Tee-Object -FilePath $LogFile -Append
