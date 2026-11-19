# ==============================================================================
# FoodCore ERP v5.7.1 - Empaquetador Nativo Reproducible jpackage (GA)
# Incluye: fat-JAR (todos los modulos), pwa/, keystore.jks.example, scripts/
# (excluyendo seed_full_operational.sql) y application.properties.example.
# Excluye intencionalmente certificados de laboratorio (foodcore.cer) y semillas
# operacionales; cada despliegue debe generar su propio TLS con keystore.jks.example.
# Auto-detecta WiX Toolset:
#   - Si WiX esta instalado: genera instalador nativo dist/FoodCoreERP-5.7.0.msi
#   - Si WiX NO esta instalado: genera dist/FoodCoreERP-5.7.0-windows-portable.zip
#     (NUNCA renombra un .zip a .msi)
# ==============================================================================
param()
$ErrorActionPreference = "Stop"

$AppVersion  = "5.7.0"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$TargetDir   = Join-Path $ProjectRoot "target"
$DistDir     = Join-Path $ProjectRoot "dist"
$JpkgInput   = Join-Path $TargetDir "jpackage-input"
$JpkgOut     = Join-Path $TargetDir "jpackage-image"

if (-not (Test-Path $DistDir)) {
    New-Item -ItemType Directory -Path $DistDir -Force | Out-Null
}
if (Test-Path $JpkgInput) {
    Remove-Item -Recurse -Force $JpkgInput
}
if (Test-Path $JpkgOut) {
    Remove-Item -Recurse -Force $JpkgOut
}
New-Item -ItemType Directory -Path $JpkgInput -Force | Out-Null
New-Item -ItemType Directory -Path $JpkgOut -Force | Out-Null

$MainJar = Join-Path $TargetDir "foodcore-erp.jar"
if (-not (Test-Path $MainJar)) {
    throw "No se encontro el artefacto $MainJar. Ejecute mvn package primero."
}
Copy-Item $MainJar -Destination (Join-Path $JpkgInput "foodcore-erp.jar") -Force

# Copiar recursos requeridos al staging (jpackage-input)
# Excluir foodcore.cer (certificado autofirmado de laboratorio) y seed_full_operational.sql
$PwaSrc = Join-Path $ProjectRoot "pwa"
if (Test-Path $PwaSrc) {
    $PwaDest = Join-Path $JpkgInput "pwa"
    Copy-Item $PwaSrc -Destination $PwaDest -Recurse -Force
    Get-ChildItem -Path $PwaDest -Recurse -Filter "*.cer" -File -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue
}

$KeystoreExampleSrc = Join-Path $ProjectRoot "keystore.jks.example"
if (Test-Path $KeystoreExampleSrc) {
    Copy-Item $KeystoreExampleSrc -Destination (Join-Path $JpkgInput "keystore.jks.example") -Force
}

$AppPropsSrc = Join-Path $ProjectRoot "src\main\resources\application.properties"
if (Test-Path $AppPropsSrc) {
    Copy-Item $AppPropsSrc -Destination (Join-Path $JpkgInput "application.properties.example") -Force
}

$ExcludedScripts = @("seed_full_operational.sql")
$ScriptsSrc = Join-Path $ProjectRoot "scripts"
if (Test-Path $ScriptsSrc) {
    $ScriptsDestInput = Join-Path $JpkgInput "scripts"
    New-Item -ItemType Directory -Path $ScriptsDestInput -Force | Out-Null
    Get-ChildItem -Path $ScriptsSrc -File | Where-Object {
        ($_.Extension -in ".ps1", ".sh", ".bat", ".sql") -and ($ExcludedScripts -notcontains $_.Name)
    } | ForEach-Object {
        Copy-Item $_.FullName -Destination (Join-Path $ScriptsDestInput $_.Name) -Force
    }
}

function Test-WixToolsetInstalled {
    # WiX v4+ expone wix.exe en PATH
    if ($null -ne (Get-Command wix.exe -ErrorAction SilentlyContinue)) {
        return $true
    }
    # WiX v3.x expone candle.exe y light.exe en PATH o en Program Files (x86)\WiX Toolset*
    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    $light  = Get-Command light.exe -ErrorAction SilentlyContinue
    if ($null -ne $candle -and $null -ne $light) {
        return $true
    }
    $wixDirs = @(
        "${env:ProgramFiles(x86)}\WiX Toolset*",
        "${env:ProgramFiles}\WiX Toolset*"
    )
    foreach ($pattern in $wixDirs) {
        $matches = Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue
        foreach ($dir in $matches) {
            $binDir = Join-Path $dir.FullName "bin"
            if ((Test-Path (Join-Path $binDir "candle.exe")) -and (Test-Path (Join-Path $binDir "light.exe"))) {
                return $true
            }
            if (Test-Path (Join-Path $binDir "wix.exe")) {
                return $true
            }
        }
    }
    return $false
}

$MsiPath         = Join-Path $DistDir "FoodCoreERP-$AppVersion.msi"
$PortableZipPath = Join-Path $DistDir "FoodCoreERP-$AppVersion-windows-portable.zip"
$DebPath         = Join-Path $DistDir "FoodCoreERP-$AppVersion.deb"
$HasWix          = Test-WixToolsetInstalled
$ShaEntries      = @()

if ($HasWix) {
    if (Test-Path $MsiPath) { Remove-Item -Force $MsiPath }
    Write-Host "[jpackage] WiX Toolset detectado. Generando instalador nativo MSI FoodCoreERP $AppVersion..."
    & jpackage --type msi `
        --name "FoodCoreERP" `
        --app-version $AppVersion `
        --vendor "FoodCore S.A.C." `
        --input $JpkgInput `
        --main-jar "foodcore-erp.jar" `
        --main-class "com.foodcore.Launcher" `
        --win-menu `
        --win-shortcut `
        --dest $DistDir

    if ($LASTEXITCODE -ne 0) {
        throw "jpackage --type msi fallo con codigo de salida $LASTEXITCODE"
    }
    if (-not (Test-Path $MsiPath)) {
        $generatedMsi = Get-ChildItem -Path $DistDir -Filter "FoodCoreERP-*.msi" -File | Select-Object -First 1
        if ($null -ne $generatedMsi -and $generatedMsi.FullName -ne $MsiPath) {
            Move-Item -Path $generatedMsi.FullName -Destination $MsiPath -Force
        }
    }
    if (-not (Test-Path $MsiPath)) {
        throw "No se genero el instalador MSI esperado en $MsiPath"
    }
    $HashMsi = (Get-FileHash -Path $MsiPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $ShaEntries += "$HashMsi  FoodCoreERP-$AppVersion.msi"
    Write-Host "[jpackage] Instalador nativo MSI generado exitosamente: $MsiPath"
} else {
    Write-Warning "[jpackage] WiX Toolset (wix.exe o candle.exe + light.exe) NO esta instalado en este equipo."
    Write-Warning "[jpackage] No se generara un archivo .msi falso. Se generara el paquete portable honesto: FoodCoreERP-$AppVersion-windows-portable.zip"
    Write-Warning "[jpackage] Para construir dist/FoodCoreERP-$AppVersion.msi nativo, instale WiX Toolset y vuelva a ejecutar package-release.ps1."

    if (Test-Path $PortableZipPath) { Remove-Item -Force $PortableZipPath }
    Write-Host "[jpackage] Generando runtime image nativa FoodCoreERP $AppVersion (app-image)..."
    & jpackage --type app-image `
        --name "FoodCoreERP" `
        --app-version $AppVersion `
        --vendor "FoodCore S.A.C." `
        --input $JpkgInput `
        --main-jar "foodcore-erp.jar" `
        --main-class "com.foodcore.Launcher" `
        --dest $JpkgOut

    if ($LASTEXITCODE -ne 0) {
        throw "jpackage --type app-image fallo con codigo de salida $LASTEXITCODE"
    }

    $AppImageDir = Join-Path $JpkgOut "FoodCoreERP"
    if (-not (Test-Path $AppImageDir)) {
        throw "No se genero la imagen esperada en $AppImageDir"
    }

    # Replicar carpetas auxiliares en la raiz de la imagen portable (sin foodcore.cer ni seed_full_operational.sql)
    if (Test-Path (Join-Path $JpkgInput "pwa")) {
        Copy-Item (Join-Path $JpkgInput "pwa") -Destination (Join-Path $AppImageDir "pwa") -Recurse -Force
    }
    if (Test-Path $KeystoreExampleSrc) {
        Copy-Item $KeystoreExampleSrc -Destination (Join-Path $AppImageDir "keystore.jks.example") -Force
    }
    if (Test-Path $AppPropsSrc) {
        Copy-Item $AppPropsSrc -Destination (Join-Path $AppImageDir "application.properties.example") -Force
    }
    $ScriptsDestImage = Join-Path $AppImageDir "scripts"
    if (Test-Path (Join-Path $JpkgInput "scripts")) {
        Copy-Item (Join-Path $JpkgInput "scripts") -Destination $ScriptsDestImage -Recurse -Force
    }

    Compress-Archive -Path "$AppImageDir\*" -DestinationPath $PortableZipPath -Force
    $HashZip = (Get-FileHash -Path $PortableZipPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $ShaEntries += "$HashZip  FoodCoreERP-$AppVersion-windows-portable.zip"
    Write-Host "[jpackage] Paquete Windows portable generado exitosamente: $PortableZipPath"
}

# Generar tambien el paquete .deb nativo via WSL Debian si esta disponible
if (Get-Command wsl -ErrorAction SilentlyContinue) {
    $WslHasJpackage = $false
    try {
        & wsl -d Debian --exec which jpackage | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $WslHasJpackage = $true
        }
    } catch {
        $WslHasJpackage = $false
    }
    if ($WslHasJpackage) {
        Write-Host "[jpackage] Generando paquete Linux .deb via WSL Debian..."
        & wsl -d Debian --cd "$ProjectRoot" --exec bash scripts/build-deb.sh
        if ($LASTEXITCODE -ne 0) {
            throw "build-deb.sh en WSL Debian fallo con codigo de salida $LASTEXITCODE"
        }
    }
}

if (Test-Path $DebPath) {
    $HashDeb = (Get-FileHash -Path $DebPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $ShaEntries += "$HashDeb  FoodCoreERP-$AppVersion.deb"
}

$ShaFile = Join-Path $DistDir "SHA256SUMS.txt"
[System.IO.File]::WriteAllLines($ShaFile, $ShaEntries, [System.Text.UTF8Encoding]::new($false))

Write-Host "[jpackage] Artefactos de distribucion generados en ${DistDir}:"
foreach ($entry in $ShaEntries) {
    Write-Host "  SHA-256: $entry"
}
