# ==============================================================================
# FoodCore ERP v5.8.0 - Generador de Keystore TLS Unico por Sede del Cliente
# Genera un keystore PKCS12 (keystore.jks) con SAN DNS + IP LAN + 127.0.0.1,
# exporta el certificado publico foodcore.cer para tablets PWA y aplica ACL
# restrictiva owner-only sobre keystore.jks (nunca reutiliza llaves de lab).
# ==============================================================================
param(
    [string]$HostName = $env:COMPUTERNAME,
    [string]$LanIp = "",
    [string]$OutputDir = "",
    [int]$ValidityDays = 825,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

function Assert-SafeToken {
    param([string]$Value, [string]$Pattern, [string]$ParamName)
    if ([string]::IsNullOrWhiteSpace($Value) -or ($Value -notmatch $Pattern)) {
        throw "Parametro invalido para ${ParamName}: '$Value'."
    }
}

if ([string]::IsNullOrWhiteSpace($HostName)) {
    $HostName = "foodcore-server"
}
Assert-SafeToken -Value $HostName -Pattern '^[A-Za-z0-9][A-Za-z0-9.-]{0,62}$' -ParamName "HostName"

if ($ValidityDays -lt 1 -or $ValidityDays -gt 3650) {
    throw "ValidityDays debe estar entre 1 y 3650."
}

if ([string]::IsNullOrWhiteSpace($LanIp)) {
    try {
        $detected = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object {
                $_.IPAddress -notlike "127.*" -and
                $_.IPAddress -notlike "169.254.*" -and
                $_.InterfaceAlias -notmatch "Loopback|vEthernet|WSL|Docker"
            } |
            Sort-Object -Property InterfaceMetric |
            Select-Object -First 1 -ExpandProperty IPAddress
        if (-not [string]::IsNullOrWhiteSpace($detected)) {
            $LanIp = $detected
        }
    } catch {
        $LanIp = ""
    }
}
if (-not [string]::IsNullOrWhiteSpace($LanIp)) {
    [System.Net.IPAddress]$parsedIp = $null
    if (-not [System.Net.IPAddress]::TryParse($LanIp.Trim(), [ref]$parsedIp) -or
        $parsedIp.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
        throw "LanIp debe ser una direccion IPv4 valida (recibido: '$LanIp')."
    }
    $LanIp = $parsedIp.ToString()
}

$ProjectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $ProjectRoot "certs"
}
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}
$ResolvedOutDir = (Resolve-Path $OutputDir).Path
$KeystorePath = Join-Path $ResolvedOutDir "keystore.jks"
$CertPath     = Join-Path $ResolvedOutDir "foodcore.cer"

if ((Test-Path $KeystorePath) -and (-not $Force)) {
    throw "Ya existe '$KeystorePath'. Use -Force si desea regenerarlo explicitamente."
}

$KeytoolCmd = Get-Command keytool.exe -ErrorAction SilentlyContinue
if (-not $KeytoolCmd -and $env:JAVA_HOME) {
    $Candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (Test-Path $Candidate) {
        $KeytoolCmd = Get-Command $Candidate -ErrorAction SilentlyContinue
    }
}
if (-not $KeytoolCmd) {
    throw "No se encontro keytool.exe en PATH ni en JAVA_HOME."
}
$KeytoolExe = $KeytoolCmd.Source

$Pass = $env:FOODCORE_KEYSTORE_PASS
if ([string]::IsNullOrWhiteSpace($Pass)) {
    $Secure = Read-Host -Prompt "Ingrese contrasena fuerte para el nuevo keystore TLS (minimo 12 caracteres)" -AsSecureString
    $Bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try {
        $Pass = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($Bstr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Bstr)
    }
}
if ([string]::IsNullOrWhiteSpace($Pass) -or $Pass.Length -lt 12) {
    throw "La contrasena del keystore debe tener al menos 12 caracteres."
}

$SanEntries = [System.Collections.Generic.List[string]]::new()
$SanEntries.Add("DNS:$HostName")
$SanEntries.Add("DNS:localhost")
if (-not [string]::IsNullOrWhiteSpace($LanIp)) {
    $SanEntries.Add("IP:$LanIp")
}
$SanEntries.Add("IP:127.0.0.1")
$SanExt = "SAN=" + (($SanEntries | Select-Object -Unique) -join ",")
$DName  = "CN=$HostName, OU=POS-LAN, O=Fast Food S.A.C., L=Lima, C=PE"

$TempEnvName = "FC_KS_GEN_PASS_" + ([Guid]::NewGuid().ToString("N"))
[System.Environment]::SetEnvironmentVariable($TempEnvName, $Pass, "Process")

try {
    if ($Force -and (Test-Path $KeystorePath)) {
        Remove-Item -Force $KeystorePath
    }
    if ($Force -and (Test-Path $CertPath)) {
        Remove-Item -Force $CertPath
    }

    & $KeytoolExe -genkeypair -noprompt `
        -alias "foodcore" `
        -keyalg "RSA" `
        -keysize "2048" `
        -storetype "PKCS12" `
        -validity "$ValidityDays" `
        -keystore $KeystorePath `
        -storepass:env $TempEnvName `
        -keypass:env $TempEnvName `
        -dname $DName `
        -ext $SanExt

    if ($LASTEXITCODE -ne 0 -or (-not (Test-Path $KeystorePath))) {
        throw "Error ejecutando keytool -genkeypair (codigo $LASTEXITCODE)."
    }

    & $KeytoolExe -exportcert -noprompt `
        -alias "foodcore" `
        -keystore $KeystorePath `
        -storepass:env $TempEnvName `
        -file $CertPath

    if ($LASTEXITCODE -ne 0 -or (-not (Test-Path $CertPath))) {
        throw "Error ejecutando keytool -exportcert (codigo $LASTEXITCODE)."
    }

    # Proteger keystore.jks con ACL restrictiva (solo SYSTEM, Administrators y propietario)
    $CurrentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $KeystorePath /inheritance:r /grant:r "*S-1-5-18:(F)" "*S-1-5-32-544:(F)" "${CurrentUser}:(F)" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo aplicar ACL restrictiva sobre $KeystorePath."
    }

    Write-Host "[OK] Keystore TLS unico generado en: $KeystorePath"
    Write-Host "[OK] Certificado publico PWA exportado en: $CertPath"
    Write-Host "[OK] SAN configurado: $SanExt"
    Write-Host "Exporte antes de iniciar FoodCore ERP:"
    Write-Host "  `$env:FOODCORE_KEYSTORE_PATH = '$KeystorePath'"
    Write-Host "  `$env:FOODCORE_CERT_PATH     = '$CertPath'"
} finally {
    [System.Environment]::SetEnvironmentVariable($TempEnvName, $null, "Process")
    $Pass = $null
}
