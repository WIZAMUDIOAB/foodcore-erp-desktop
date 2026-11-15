# ==============================================================================
# FoodCore ERP v5.3.0 - Restauración Automatizada MySQL 8 (Sub-Fase C Ítem 3)
# 1) Solicita o recibe la ruta del backup (.sql.gz o .sql)
# 2) Valida existencia, integridad GZip y contenido SQL válido
# 3) Detiene servicios activos de aplicación / túnel (salvo -SkipServiceRestart)
# 4) Restaura el volcado en MySQL
# 5) Reinicia los servicios y escribe auditoría en logs/
# ==============================================================================
param(
    [string]$BackupFile = "",
    [string]$DbName = $(if ($env:FOODCORE_DB_NAME) { $env:FOODCORE_DB_NAME } else { "foodcore" }),
    [string]$DbHost = $(if ($env:FOODCORE_DB_HOST) { $env:FOODCORE_DB_HOST } else { "127.0.0.1" }),
    [int]$DbPort = $(if ($env:FOODCORE_DB_PORT) { [int]$env:FOODCORE_DB_PORT } else { 3306 }),
    [string]$DbUser = $(if ($env:FOODCORE_MIGRATOR_USER) { $env:FOODCORE_MIGRATOR_USER } else { "foodcore_migrator" }),
    [string]$DbPass = $(if ($env:FOODCORE_MIGRATOR_PASS) { $env:FOODCORE_MIGRATOR_PASS } else { "FoodCoreMigrator123!" }),
    [switch]$SkipServiceRestart,
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$LogDir      = Join-Path $ProjectRoot "logs"
if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

if ([string]::IsNullOrWhiteSpace($BackupFile)) {
    $BackupFile = Read-Host "Ingrese la ruta completa del archivo de backup (.sql.gz o .sql)"
}
if ([string]::IsNullOrWhiteSpace($BackupFile) -or -not (Test-Path $BackupFile)) {
    throw "Archivo de backup no encontrado: $BackupFile"
}

$ResolvedBackup = (Resolve-Path $BackupFile).Path
$FileInfo = Get-Item $ResolvedBackup
if ($FileInfo.Length -le 0) {
    throw "El archivo de backup esta vacio: $ResolvedBackup"
}

$Fecha   = Get-Date -Format "yyyyMMdd_HHmmss"
$LogFile = Join-Path $LogDir "restore_${Fecha}.log"
$TmpSql  = Join-Path $LogDir "tmp_restore_${Fecha}.sql"

# Validar y preparar el archivo SQL
if ($ResolvedBackup.EndsWith(".gz", [System.StringComparison]::OrdinalIgnoreCase)) {
    $header = New-Object byte[] 2
    $fs = [System.IO.File]::OpenRead($ResolvedBackup)
    try {
        $read = $fs.Read($header, 0, 2)
    } finally {
        $fs.Dispose()
    }
    if ($read -lt 2 -or $header[0] -ne 0x1F -or $header[1] -ne 0x8B) {
        throw "Archivo .gz invalido o corrupto (cabecera GZip incorrecta): $ResolvedBackup"
    }

    $inStream  = [System.IO.File]::OpenRead($ResolvedBackup)
    $gzip      = New-Object System.IO.Compression.GzipStream($inStream, [System.IO.Compression.CompressionMode]::Decompress)
    $outStream = [System.IO.File]::Create($TmpSql)
    try {
        $gzip.CopyTo($outStream)
    } finally {
        $outStream.Dispose()
        $gzip.Dispose()
        $inStream.Dispose()
    }
} else {
    Copy-Item -Path $ResolvedBackup -Destination $TmpSql -Force
}

$SqlPreview = Get-Content -Path $TmpSql -TotalCount 30 -ErrorAction SilentlyContinue | Out-String
if ([string]::IsNullOrWhiteSpace($SqlPreview) -or $SqlPreview -notmatch "(CREATE TABLE|INSERT INTO|MySQL dump|SET |--)") {
    Remove-Item -Force $TmpSql -ErrorAction SilentlyContinue
    throw "El archivo de backup no contiene sentencias SQL validas."
}

if ($ValidateOnly) {
    Remove-Item -Force $TmpSql -ErrorAction SilentlyContinue
    Write-Host "[OK] Backup validado correctamente: $ResolvedBackup"
    exit 0
}

function Resolve-MysqlExe {
    $candidates = @(
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($null -eq $cmd) {
        $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    }
    if ($null -ne $cmd) {
        return $cmd.Source
    }
    throw "No se encontro mysql.exe en el sistema."
}

$ServiceRestarted = $false
$TunnelWasRunning = $false

try {
    if (-not $SkipServiceRestart) {
        $svc = Get-Service -Name "FoodCoreERP" -ErrorAction SilentlyContinue
        if ($null -ne $svc -and $svc.Status -eq "Running") {
            "[$(Get-Date -Format o)] Deteniendo servicio FoodCoreERP..." | Tee-Object -FilePath $LogFile -Append
            Stop-Service -Name "FoodCoreERP" -Force
            $ServiceRestarted = $true
        }
        $cloudflaredProcs = Get-Process -Name "cloudflared" -ErrorAction SilentlyContinue
        if ($null -ne $cloudflaredProcs) {
            "[$(Get-Date -Format o)] Deteniendo tunel Cloudflare..." | Tee-Object -FilePath $LogFile -Append
            $cloudflaredProcs | Stop-Process -Force
            $TunnelWasRunning = $true
        }
    }

    "[$(Get-Date -Format o)] Restaurando $ResolvedBackup en base de datos $DbName..." | Tee-Object -FilePath $LogFile -Append
    $MysqlExe = Resolve-MysqlExe

    $prevPwd = $env:MYSQL_PWD
    $prevErr = $ErrorActionPreference
    $env:MYSQL_PWD = $DbPass
    $ErrorActionPreference = "Continue"
    try {
        cmd.exe /c "`"$MysqlExe`" -h $DbHost -P $DbPort -u $DbUser $DbName < `"$TmpSql`""
        $restoreExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevErr
        if ($null -eq $prevPwd) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $prevPwd
        }
    }

    if ($restoreExit -ne 0) {
        throw "La restauracion mysql fallo con codigo de salida $restoreExit"
    }

    "[$(Get-Date -Format o)] Base de datos $DbName restaurada exitosamente." | Tee-Object -FilePath $LogFile -Append
} finally {
    Remove-Item -Force $TmpSql -ErrorAction SilentlyContinue
    if (-not $SkipServiceRestart) {
        if ($ServiceRestarted) {
            "[$(Get-Date -Format o)] Reiniciando servicio FoodCoreERP..." | Tee-Object -FilePath $LogFile -Append
            Start-Service -Name "FoodCoreERP" -ErrorAction SilentlyContinue
        }
        $TunnelScript = Join-Path $PSScriptRoot "start-tunnel.ps1"
        if ($TunnelWasRunning -and (Test-Path $TunnelScript)) {
            "[$(Get-Date -Format o)] Reiniciando tunel Cloudflare..." | Tee-Object -FilePath $LogFile -Append
            Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$TunnelScript`"" -WindowStyle Hidden
        }
    }
}

Write-Host "[OK] Restauracion completada exitosamente desde: $ResolvedBackup"
