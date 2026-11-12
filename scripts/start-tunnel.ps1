$ErrorActionPreference = 'Continue'
$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'

Set-Location -Path $PSScriptRoot

.\cloudflared.exe tunnel --url http://localhost:8080 --no-autoupdate > tunnel.log 2>&1
