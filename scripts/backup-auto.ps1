# ==============================================================================
# FoodCore ERP v5.7.1 - Script de Backup Automatico MySQL 8 (Sub-Fase C Item 2)
# 1) Valida parametros y exige credenciales por variable de entorno o prompt seguro
# 2) Ejecuta mysqldump --single-transaction usando --defaults-extra-file temporal (ACL owner-only)
# 3) Comprime el volcado a formato .sql.gz
# 4) Guarda en carpeta backups/ con permisos restringidos (SYSTEM / Administrators / Owner)
# 5) Elimina backups de mas de 30 dias
# 6) Asegura ACLs en scripts/ y backups/ antes de registrar Tarea Programada diaria a las 03:00 AM
# ==============================================================================
param(
    [string]$DbName = $(if ($env:FOODCORE_DB_NAME) { $env:FOODCORE_DB_NAME } else { "foodcore" }),
    [string]$DbHost = $(if ($env:FOODCORE_DB_HOST) { $env:FOODCORE_DB_HOST } else { "127.0.0.1" }),
    [int]$DbPort = $(if ($env:FOODCORE_DB_PORT) { [int]$env:FOODCORE_DB_PORT } else { 3306 }),
    [string]$DbUser = $(if ($env:FOODCORE_MIGRATOR_USER) { $env:FOODCORE_MIGRATOR_USER } else { "foodcore_migrator" }),
    [string]$DbPass = $(if ($env:FOODCORE_MIGRATOR_PASS) { $env:FOODCORE_MIGRATOR_PASS } elseif ($env:FOODCORE_DB_PASS) { $env:FOODCORE_DB_PASS } else { "" }),
    [string]$SslMode = $(if ($env:FOODCORE_DB_SSL_MODE) { $env:FOODCORE_DB_SSL_MODE } else { "REQUIRED" }),
    [string]$BackupDir = "",
    [int]$RetentionDays = 30,
    [switch]$RegisterTask,
    [string]$TaskName = "FoodCoreERP_Backup_3AM",
    [switch]$SkipDump
)

$ErrorActionPreference = "Stop"

function Assert-SafeParameter {
    param(
        [string]$Value,
        [string]$Pattern,
        [string]$ParamName
    )
    if ([string]::IsNullOrWhiteSpace($Value) -or ($Value -notmatch $Pattern)) {
        throw "Parametro invalido para ${ParamName}: contiene caracteres no permitidos o esta vacio."
    }
}

Assert-SafeParameter -Value $DbName -Pattern '^[A-Za-z0-9_]{1,64}$' -ParamName "DbName"
Assert-SafeParameter -Value $DbHost -Pattern '^[A-Za-z0-9][A-Za-z0-9._:-]{0,252}$' -ParamName "DbHost"
Assert-SafeParameter -Value $DbUser -Pattern '^[A-Za-z0-9_.-]{1,64}$' -ParamName "DbUser"
Assert-SafeParameter -Value $TaskName -Pattern '^[A-Za-z0-9_-]{1,128}$' -ParamName "TaskName"
if ($DbPort -lt 1 -or $DbPort -gt 65535) {
    throw "Parametro invalido para DbPort: debe estar entre 1 y 65535."
}
if ($RetentionDays -lt 1 -or $RetentionDays -gt 3650) {
    throw "Parametro invalido para RetentionDays: debe estar entre 1 y 3650."
}
$AllowedSslModes = @("REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY", "PREFERRED")
$SslMode = $SslMode.Trim().ToUpperInvariant()
if ($AllowedSslModes -notcontains $SslMode) {
    throw "Parametro invalido para SslMode: '$SslMode'. Permitidos: $($AllowedSslModes -join ', ')."
}

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
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevErr
    }
}

function Resolve-RequiredDbPassword {
    param([string]$CurrentPass, [string]$UserName)
    if (-not [string]::IsNullOrEmpty($CurrentPass)) {
        return $CurrentPass
    }
    if ([Environment]::UserInteractive -and -not [Console]::IsInputRedirected) {
        $secure = Read-Host -AsSecureString "Ingrese la contrasena de MySQL para '$UserName'"
        if ($null -eq $secure -or $secure.Length -eq 0) {
            throw "La contrasena de MySQL es obligatoria."
        }
        $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try {
            return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        } finally {
            [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
    }
    throw "ERROR: Credencial de base de datos ausente. Defina FOODCORE_MIGRATOR_PASS o FOODCORE_DB_PASS en el entorno."
}

function Escape-MySqlOptionValue {
    param([string]$Raw)
    if ($Raw -match '[\r\n\0]') {
        throw "Valor de credencial invalido: contiene saltos de linea o caracter nulo."
    }
    return $Raw.Replace('\', '\\').Replace('"', '\"')
}

function New-MySqlDefaultsExtraFile {
    param([string]$UserName, [string]$Password)
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("foodcore_my_" + [Guid]::NewGuid().ToString("N") + ".cnf")
    New-Item -ItemType File -Path $tempFile -Force | Out-Null

    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & icacls $tempFile /inheritance:r /grant:r "${currentUser}:F" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Remove-Item -Force $tempFile -ErrorAction SilentlyContinue
            throw "No se pudo aplicar ACL restringida (owner-only) al archivo temporal de credenciales MySQL."
        }
    } finally {
        $ErrorActionPreference = $prevErr
    }

    $escapedUser = Escape-MySqlOptionValue -Raw $UserName
    $escapedPass = Escape-MySqlOptionValue -Raw $Password
    $lines = @(
        "[client]",
        "user=`"$escapedUser`"",
        "password=`"$escapedPass`""
    )
    [System.IO.File]::WriteAllLines($tempFile, $lines, [System.Text.UTF8Encoding]::new($false))
    return $tempFile
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
    param([string]$ScheduledTaskName, [string]$ScriptFile, [string]$ScriptsDirectory, [string]$BackupOutputDir)
    # Asegurar ACLs restrictivas en scripts/ y backups/ antes de registrar la tarea programada
    $aclScripts = Restrict-BackupPermissions -TargetPath $ScriptsDirectory
    $aclBackups = Restrict-BackupPermissions -TargetPath $BackupOutputDir
    if ($aclScripts -ne 0 -or $aclBackups -ne 0) {
        throw "No se pudieron restringir las ACLs en '$ScriptsDirectory' o '$BackupOutputDir' antes de registrar la tarea programada."
    }

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

$null = Restrict-BackupPermissions -TargetPath $BackupDir

$GzFile = $null
if (-not $SkipDump) {
    $ResolvedPass = Resolve-RequiredDbPassword -CurrentPass $DbPass -UserName $DbUser
    $Stamp      = Get-Date -Format "yyyyMMdd_HHmmss"
    $SqlRawFile = Join-Path $BackupDir "${DbName}_${Stamp}.sql"
    $GzFile     = Join-Path $BackupDir "${DbName}_${Stamp}.sql.gz"
    $MysqlDump  = Resolve-MysqlDump
    $DefaultsFile = $null

    $prevErr = $ErrorActionPreference
    try {
        $DefaultsFile = New-MySqlDefaultsExtraFile -UserName $DbUser -Password $ResolvedPass
        $ErrorActionPreference = "Continue"
        & $MysqlDump "--defaults-extra-file=$DefaultsFile" `
            "--host=$DbHost" "--port=$DbPort" "--ssl-mode=$SslMode" `
            --single-transaction --no-tablespaces --routines --triggers `
            "--result-file=$SqlRawFile" $DbName 2>&1 | Out-Null
        $dumpExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevErr
        if ($null -ne $DefaultsFile -and (Test-Path $DefaultsFile)) {
            Remove-Item -Force $DefaultsFile -ErrorAction SilentlyContinue
        }
    }

    if ($dumpExit -ne 0 -or -not (Test-Path $SqlRawFile) -or (Get-Item $SqlRawFile).Length -le 0) {
        if (Test-Path $SqlRawFile) {
            Remove-Item -Force $SqlRawFile -ErrorAction SilentlyContinue
        }
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
    $null = Restrict-BackupPermissions -TargetPath $GzFile
    Write-Host "[OK] Backup comprimido generado: $GzFile"
}

# Purga de backups con mas de N dias (por defecto 30)
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
    Register-DailyBackupTask -ScheduledTaskName $TaskName -ScriptFile $PSCommandPath -ScriptsDirectory $PSScriptRoot -BackupOutputDir $BackupDir
}

if ($null -ne $GzFile) {
    Write-Output $GzFile
}
