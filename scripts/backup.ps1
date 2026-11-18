# ==============================================================================
# FoodCore ERP v5.7.1 - Backup Automatizado MySQL 8.0 (SPEC-FOODCORE-v1.0 §17.3, R-16)
# Ejecuta mysqldump con --defaults-extra-file temporal (ACL owner-only) y comprime .sql.gz
# ==============================================================================
param(
    [string]$DbName = $(if ($env:FOODCORE_DB_NAME) { $env:FOODCORE_DB_NAME } else { "foodcore" }),
    [string]$DbHost = $(if ($env:FOODCORE_DB_HOST) { $env:FOODCORE_DB_HOST } else { "127.0.0.1" }),
    [int]$DbPort = $(if ($env:FOODCORE_DB_PORT) { [int]$env:FOODCORE_DB_PORT } else { 3306 }),
    [string]$DbUser = $(if ($env:FOODCORE_MIGRATOR_USER) { $env:FOODCORE_MIGRATOR_USER } else { "foodcore_migrator" }),
    [string]$DbPass = $(if ($env:FOODCORE_MIGRATOR_PASS) { $env:FOODCORE_MIGRATOR_PASS } elseif ($env:FOODCORE_DB_PASS) { $env:FOODCORE_DB_PASS } else { "" }),
    [string]$SslMode = $(if ($env:FOODCORE_DB_SSL_MODE) { $env:FOODCORE_DB_SSL_MODE } else { "REQUIRED" })
)
$ErrorActionPreference = "Stop"

function Assert-SafeParameter {
    param([string]$Value, [string]$Pattern, [string]$ParamName)
    if ([string]::IsNullOrWhiteSpace($Value) -or ($Value -notmatch $Pattern)) {
        throw "Parametro invalido para ${ParamName}: contiene caracteres no permitidos o esta vacio."
    }
}

Assert-SafeParameter -Value $DbName -Pattern '^[A-Za-z0-9_]{1,64}$' -ParamName "DbName"
Assert-SafeParameter -Value $DbHost -Pattern '^[A-Za-z0-9][A-Za-z0-9._:-]{0,252}$' -ParamName "DbHost"
Assert-SafeParameter -Value $DbUser -Pattern '^[A-Za-z0-9_.-]{1,64}$' -ParamName "DbUser"
if ($DbPort -lt 1 -or $DbPort -gt 65535) {
    throw "Parametro invalido para DbPort: debe estar entre 1 y 65535."
}
$AllowedSslModes = @("REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY", "PREFERRED")
$SslMode = $SslMode.Trim().ToUpperInvariant()
if ($AllowedSslModes -notcontains $SslMode) {
    throw "Parametro invalido para SslMode: '$SslMode'."
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

function Restrict-PathPermissions {
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

function New-MySqlDefaultsExtraFile {
    param([string]$UserName, [string]$Password)
    if ($UserName -match '[\r\n\0]' -or $Password -match '[\r\n\0]') {
        throw "Credencial invalida: contiene caracteres de control."
    }
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("foodcore_my_" + [Guid]::NewGuid().ToString("N") + ".cnf")
    New-Item -ItemType File -Path $tempFile -Force | Out-Null
    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & icacls $tempFile /inheritance:r /grant:r "${currentUser}:F" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Remove-Item -Force $tempFile -ErrorAction SilentlyContinue
            throw "No se pudo aplicar ACL owner-only al archivo temporal de credenciales."
        }
    } finally {
        $ErrorActionPreference = $prevErr
    }
    $u = $UserName.Replace('\', '\\').Replace('"', '\"')
    $p = $Password.Replace('\', '\\').Replace('"', '\"')
    [System.IO.File]::WriteAllLines($tempFile, @("[client]", "user=`"$u`"", "password=`"$p`""), [System.Text.UTF8Encoding]::new($false))
    return $tempFile
}

function Resolve-MysqlDump {
    foreach ($candidate in @(
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
    )) {
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command mysqldump.exe -ErrorAction SilentlyContinue
    if ($null -eq $cmd) { $cmd = Get-Command mysqldump -ErrorAction SilentlyContinue }
    if ($null -ne $cmd) { return $cmd.Source }
    throw "No se encontro mysqldump.exe en el sistema."
}

$ResolvedPass = Resolve-RequiredDbPassword -CurrentPass $DbPass -UserName $DbUser
$ProjectRoot  = Split-Path -Parent $PSScriptRoot
$BackupDir    = Join-Path $ProjectRoot "backups"
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}
Restrict-PathPermissions -TargetPath $BackupDir

$Fecha        = Get-Date -Format "yyyyMMdd"
$SqlRawFile   = Join-Path $BackupDir "${DbName}_${Fecha}.sql"
$GzFile       = Join-Path $BackupDir "${DbName}_${Fecha}.sql.gz"
$MysqlDumpExe = Resolve-MysqlDump
$DefaultsFile = $null

$prevErr = $ErrorActionPreference
try {
    $DefaultsFile = New-MySqlDefaultsExtraFile -UserName $DbUser -Password $ResolvedPass
    $ErrorActionPreference = "Continue"
    & $MysqlDumpExe "--defaults-extra-file=$DefaultsFile" `
        "--host=$DbHost" "--port=$DbPort" "--ssl-mode=$SslMode" `
        --single-transaction --no-tablespaces --routines --triggers `
        "--result-file=$SqlRawFile" $DbName
    $dumpExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $prevErr
    if ($null -ne $DefaultsFile -and (Test-Path $DefaultsFile)) {
        Remove-Item -Force $DefaultsFile -ErrorAction SilentlyContinue
    }
}

if ($dumpExit -ne 0 -or -not (Test-Path $SqlRawFile) -or (Get-Item $SqlRawFile).Length -le 0) {
    Remove-Item -Force $SqlRawFile -ErrorAction SilentlyContinue
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
Restrict-PathPermissions -TargetPath $GzFile

Write-Host "[OK] Backup generado: $GzFile"
