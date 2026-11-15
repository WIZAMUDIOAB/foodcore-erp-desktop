# ==============================================================================
# FoodCore ERP v5.3.0 - Script de Backup Automático MySQL 8 (Sub-Fase C Ítem 2)
# 1) Ejecuta mysqldump --single-transaction de la BD foodcore
# 2) Comprime el volcado a formato .sql.gz
# 3) Guarda en carpeta backups/ con permisos restringidos (SYSTEM / Administrators)
# 4) Elimina backups de más de 30 días
# 5) Registra/verifica Tarea Programada diaria a las 03:00 AM
# ==============================================================================
param(
    [string]$DbName = $(if ($env:FOODCORE_DB_NAME) { $env:FOODCORE_DB_NAME } else { "foodcore" }),
    [string]$DbHost = $(if ($env:FOODCORE_DB_HOST) { $env:FOODCORE_DB_HOST } else { "127.0.0.1" }),
    [int]$DbPort = $(if ($env:FOODCORE_DB_PORT) { [int]$env:FOODCORE_DB_PORT } else { 3306 }),
    [string]$DbUser = $(if ($env:FOODCORE_MIGRATOR_USER) { $env:FOODCORE_MIGRATOR_USER } else { "foodcore_migrator" }),
    [string]$DbPass = $(if ($env:FOODCORE_MIGRATOR_PASS) { $env:FOODCORE_MIGRATOR_PASS } else { "FoodCoreMigrator123!" }),
    [string]$BackupDir = "",
    [int]$RetentionDays = 30,
    [switch]$RegisterTask,
    [string]$TaskName = "FoodCoreERP_Backup_3AM",
    [switch]$SkipDump
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($BackupDir)) {
    $BackupDir = Join-Path $ProjectRoot "backups"
}
$LogDir = Join-Path $ProjectRoot "logs"

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}
if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

function Restrict-BackupPermissions {
    param([string]$TargetPath)
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $isContainer = Test-Path -Path $TargetPath -PathType Container
    $permSuffix = if ($isContainer) { "(OI)(CI)F" } else { "F" }

    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & icacls $TargetPath /inheritance:r /grant:r `
            "*S-1-5-18:$permSuffix" `
            "*S-1-5-32-544:$permSuffix" `
            "${currentUser}:$permSuffix" | Out-Null
    } finally {
        $ErrorActionPreference = $prevErr
    }
}

function Resolve-MysqlDump {
    $candidates = @(
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    $cmd = Get-Command mysqldump.exe -ErrorAction SilentlyContinue
    if ($null -eq $cmd) {
        $cmd = Get-Command mysqldump -ErrorAction SilentlyContinue
    }
    if ($null -ne $cmd) {
        return $cmd.Source
    }
    throw "No se encontro mysqldump.exe en el sistema."
}

function Register-DailyBackupTask {
    param([string]$ScheduledTaskName, [string]$ScriptFile)
    $taskAction = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$ScriptFile`""
    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $principal = New-Object Security.Principal.WindowsPrincipal([System.Security.Principal.WindowsIdentity]::GetCurrent())
        $isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
        if ($isAdmin) {
            & schtasks /Create /SC DAILY /TN $ScheduledTaskName /TR $taskAction /ST 03:00 /RU SYSTEM /F | Out-Null
        } else {
            & schtasks /Create /SC DAILY /TN $ScheduledTaskName /TR $taskAction /ST 03:00 /F | Out-Null
        }
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[OK] Tarea programada '$ScheduledTaskName' registrada diariamente a las 03:00 AM."
        } else {
            Write-Warning "No se pudo registrar '$ScheduledTaskName' automaticamente (exit code $LASTEXITCODE)."
        }
    } finally {
        $ErrorActionPreference = $prevErr
    }
}

Restrict-BackupPermissions -TargetPath $BackupDir

$GzFile = $null
if (-not $SkipDump) {
    $Stamp      = Get-Date -Format "yyyyMMdd_HHmmss"
    $SqlRawFile = Join-Path $BackupDir "${DbName}_${Stamp}.sql"
    $GzFile     = Join-Path $BackupDir "${DbName}_${Stamp}.sql.gz"
    $MysqlDump  = Resolve-MysqlDump

    $prevPwd = $env:MYSQL_PWD
    $prevErr = $ErrorActionPreference
    $env:MYSQL_PWD = $DbPass
    $ErrorActionPreference = "Continue"
    try {
        & $MysqlDump -h $DbHost -P $DbPort -u $DbUser `
            --single-transaction --no-tablespaces --routines --triggers $DbName `
            --result-file="$SqlRawFile" 2>&1 | Out-Null
        $dumpExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevErr
        if ($null -eq $prevPwd) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $prevPwd
        }
    }

    if ($dumpExit -ne 0 -or -not (Test-Path $SqlRawFile) -or (Get-Item $SqlRawFile).Length -le 0) {
        throw "mysqldump fallo con codigo de salida $dumpExit"
    }

    $inStream  = [System.IO.File]::OpenRead($SqlRawFile)
    $outStream = [System.IO.File]::Create($GzFile)
    $gzip      = New-Object System.IO.Compression.GzipStream($outStream, [System.IO.Compression.CompressionMode]::Compress)
    try {
        $inStream.CopyTo($gzip)
    } finally {
        $gzip.Dispose()
        $outStream.Dispose()
        $inStream.Dispose()
    }
    Remove-Item -Force $SqlRawFile
    Restrict-BackupPermissions -TargetPath $GzFile
    Write-Host "[OK] Backup comprimido generado: $GzFile"
}

# Purga de backups con más de N días (por defecto 30)
$CutoffDate = (Get-Date).AddDays(-$RetentionDays)
$PurgedCount = 0
Get-ChildItem -Path $BackupDir -File | Where-Object {
    ($_.Name -like "*.sql.gz" -or $_.Name -like "*.sql") -and $_.LastWriteTime -lt $CutoffDate
} | ForEach-Object {
    Remove-Item -Path $_.FullName -Force
    $PurgedCount++
}
Write-Host "[OK] Purga de retencion (>$RetentionDays dias): $PurgedCount archivo(s) eliminado(s)."

if ($RegisterTask) {
    Register-DailyBackupTask -ScheduledTaskName $TaskName -ScriptFile $PSCommandPath
}

if ($null -ne $GzFile) {
    Write-Output $GzFile
}
