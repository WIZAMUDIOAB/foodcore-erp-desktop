param(
    [int]$Port = $(if ($env:FOODCORE_PUBLIC_MENU_PORT -and $env:FOODCORE_PUBLIC_MENU_PORT -match '^\d+$') { [int]$env:FOODCORE_PUBLIC_MENU_PORT } else { 8081 })
)

$ErrorActionPreference = 'Continue'
$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'

if ($Port -lt 1 -or $Port -gt 65535) {
    Write-Error "Puerto invalido para el conector publico de menu QR: $Port"
    exit 1
}

Set-Location -Path $PSScriptRoot

$cloudflaredExe = Join-Path $PSScriptRoot 'cloudflared.exe'
if (-not (Test-Path -LiteralPath $cloudflaredExe -PathType Leaf)) {
    Write-Error "No se encontro cloudflared.exe en $PSScriptRoot"
    exit 1
}

$targetUrl = "http://127.0.0.1:$Port"
& $cloudflaredExe tunnel --url $targetUrl --no-autoupdate > tunnel.log 2>&1
