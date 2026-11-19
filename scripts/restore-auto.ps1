# ==============================================================================
# FoodCore ERP v5.7.1 - Restauracion Automatizada MySQL 8 (Sub-Fase C Item 3)
# 1) Solicita o recibe la ruta del backup (.sql.gz o .sql) y valida parametros estrictamente
# 2) Valida existencia, integridad GZip y contenido SQL valido
# 3) Detiene servicios activos de aplicacion / tunel (salvo -SkipServiceRestart)
# 4) Restaura el volcado en MySQL via ProcessStartInfo directo y --defaults-extra-file (sin cmd.exe)
# 5) Reinicia los servicios y escribe auditoria en logs/
# ==============================================================================
param(
    [string]$BackupFile = "",
    [string]$DbName = $(if ($env:FOODCORE_DB_NAME) { $env:FOODCORE_DB_NAME } else { "foodcore" }),
    [string]$DbHost = $(if ($env:FOODCORE_DB_HOST) { $env:FOODCORE_DB_HOST } else { "127.0.0.1" }),
    [int]$DbPort = $(if ($env:FOODCORE_DB_PORT) { [int]$env:FOODCORE_DB_PORT } else { 3306 }),
    [string]$DbUser = $(if ($env:FOODCORE_MIGRATOR_USER) { $env:FOODCORE_MIGRATOR_USER } else { "foodcore_migrator" }),
    [string]$DbPass = $(if ($env:FOODCORE_MIGRATOR_PASS) { $env:FOODCORE_MIGRATOR_PASS } elseif ($env:FOODCORE_DB_PASS) { $env:FOODCORE_DB_PASS } else { "" }),
    [string]$SslMode = $(if ($env:FOODCORE_DB_SSL_MODE) { $env:FOODCORE_DB_SSL_MODE } else { "REQUIRED" }),
    [switch]$SkipServiceRestart,
    [switch]$ValidateOnly
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

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$LogDir      = Join-Path $ProjectRoot "logs"
if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

if ([string]::IsNullOrWhiteSpace($BackupFile)) {
    if ([Environment]::UserInteractive -and -not [Console]::IsInputRedirected) {
        $BackupFile = Read-Host "Ingrese la ruta completa del archivo de backup (.sql.gz o .sql)"
    } else {
        throw "Parametro -BackupFile obligatorio en modo no interactivo."
    }
}
if ([string]::IsNullOrWhiteSpace($BackupFile) -or ($BackupFile -match '[\r\n\0]') -or -not (Test-Path -Path $BackupFile -PathType Leaf)) {
    throw "Archivo de backup no encontrado o invalido: $BackupFile"
}

$ResolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
if (-not ($ResolvedBackup.EndsWith(".sql.gz", [System.StringComparison]::OrdinalIgnoreCase) -or $ResolvedBackup.EndsWith(".sql", [System.StringComparison]::OrdinalIgnoreCase))) {
    throw "Extension de archivo no permitida (solo .sql o .sql.gz): $ResolvedBackup"
}
$FileInfo = Get-Item -LiteralPath $ResolvedBackup
if ($FileInfo.Length -le 0) {
    throw "El archivo de backup esta vacio: $ResolvedBackup"
}

$Fecha   = Get-Date -Format "yyyyMMdd_HHmmss"
$LogFile = Join-Path $LogDir "restore_${Fecha}.log"
$TmpSql  = Join-Path $LogDir ("tmp_restore_" + [Guid]::NewGuid().ToString("N") + ".sql")

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
    Copy-Item -LiteralPath $ResolvedBackup -Destination $TmpSql -Force
}

$SqlPreview = Get-Content -LiteralPath $TmpSql -TotalCount 30 -ErrorAction SilentlyContinue | Out-String
if ([string]::IsNullOrWhiteSpace($SqlPreview) -or $SqlPreview -notmatch "(CREATE TABLE|INSERT INTO|MySQL dump|SET |--)") {
    Remove-Item -Force -LiteralPath $TmpSql -ErrorAction SilentlyContinue
    throw "El archivo de backup no contiene sentencias SQL validas."
}

if ($ValidateOnly) {
    Remove-Item -Force -LiteralPath $TmpSql -ErrorAction SilentlyContinue
    Write-Host "[OK] Backup validado correctamente: $ResolvedBackup"
    exit 0
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
            Remove-Item -Force -LiteralPath $tempFile -ErrorAction SilentlyContinue
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

function Resolve-MysqlExe {
    foreach ($candidate in @(
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
    )) {
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($null -eq $cmd) { $cmd = Get-Command mysql -ErrorAction SilentlyContinue }
    if ($null -ne $cmd) { return $cmd.Source }
    throw "No se encontro mysql.exe en el sistema."
}

function Invoke-MySqlRestoreDirect {
    param(
        [string]$MysqlExePath,
        [string]$DefaultsFilePath,
        [string]$HostName,
        [int]$PortNumber,
        [string]$SslModeValue,
        [string]$DatabaseName,
        [string]$SqlInputPath
    )
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $MysqlExePath
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $quotedDefaults = $DefaultsFilePath.Replace('"', '\"')
    $psi.Arguments = "`"--defaults-extra-file=$quotedDefaults`" --host=$HostName --port=$PortNumber --ssl-mode=$SslModeValue $DatabaseName"

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    if (-not $proc.Start()) {
        throw "No se pudo iniciar el proceso mysql.exe."
    }

    $stdoutTask = $proc.StandardOutput.ReadToEndAsync()
    $stderrTask = $proc.StandardError.ReadToEndAsync()
    try {
        $sqlStream = [System.IO.File]::OpenRead($SqlInputPath)
        try {
            $sqlStream.CopyTo($proc.StandardInput.BaseStream)
            $proc.StandardInput.BaseStream.Flush()
        } finally {
            $sqlStream.Dispose()
            $proc.StandardInput.Close()
        }
        $proc.WaitForExit()
        $stderrText = $stderrTask.GetAwaiter().GetResult()
        $null = $stdoutTask.GetAwaiter().GetResult()
        if ($proc.ExitCode -ne 0) {
            throw "La restauracion mysql fallo con codigo de salida $($proc.ExitCode): $stderrText"
        }
    } finally {
        $proc.Dispose()
    }
}

$ServiceRestarted = $false
$TunnelWasRunning = $false
$DefaultsFile = $null

try {
    $ResolvedPass = Resolve-RequiredDbPassword -CurrentPass $DbPass -UserName $DbUser

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
    $DefaultsFile = New-MySqlDefaultsExtraFile -UserName $DbUser -Password $ResolvedPass

    Invoke-MySqlRestoreDirect `
        -MysqlExePath $MysqlExe `
        -DefaultsFilePath $DefaultsFile `
        -HostName $DbHost `
        -PortNumber $DbPort `
        -SslModeValue $SslMode `
        -DatabaseName $DbName `
        -SqlInputPath $TmpSql

    "[$(Get-Date -Format o)] Base de datos $DbName restaurada exitosamente." | Tee-Object -FilePath $LogFile -Append
} finally {
    if ($null -ne $DefaultsFile -and (Test-Path -LiteralPath $DefaultsFile)) {
        Remove-Item -Force -LiteralPath $DefaultsFile -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $TmpSql) {
        Remove-Item -Force -LiteralPath $TmpSql -ErrorAction SilentlyContinue
    }
    if (-not $SkipServiceRestart) {
        if ($ServiceRestarted) {
            "[$(Get-Date -Format o)] Reiniciando servicio FoodCoreERP..." | Tee-Object -FilePath $LogFile -Append
            Start-Service -Name "FoodCoreERP" -ErrorAction SilentlyContinue
        }
        $TunnelScript = Join-Path $PSScriptRoot "start-tunnel.ps1"
        if ($TunnelWasRunning -and (Test-Path -LiteralPath $TunnelScript)) {
            "[$(Get-Date -Format o)] Reiniciando tunel Cloudflare..." | Tee-Object -FilePath $LogFile -Append
            Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $TunnelScript) -WindowStyle Hidden
        }
    }
}

Write-Host "[OK] Restauracion completada exitosamente desde: $ResolvedBackup"
