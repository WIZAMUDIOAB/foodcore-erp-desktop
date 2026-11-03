# ==============================================================================
# FoodCore ERP v1.0 - Empaquetador Nativo Reproducible jpackage (R-04, §17.1, §19.2)
# Genera dist/FoodCoreERP-1.0.0.msi y dist/FoodCoreERP-1.0.0.deb + SHA-256
# ==============================================================================
param()
$ErrorActionPreference = "Stop"

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

Write-Host "[jpackage] Generando runtime image nativa FoodCoreERP 1.0.0..."
& jpackage --type app-image `
    --name "FoodCoreERP" `
    --app-version "1.0.0" `
    --vendor "FoodCore S.A.C." `
    --input $JpkgInput `
    --main-jar "foodcore-erp.jar" `
    --main-class "com.foodcore.Launcher" `
    --dest $JpkgOut

$MsiPath = Join-Path $DistDir "FoodCoreERP-1.0.0.msi"
$DebPath = Join-Path $DistDir "FoodCoreERP-1.0.0.deb"

if (Test-Path $MsiPath) { Remove-Item -Force $MsiPath }
if (Test-Path $DebPath) { Remove-Item -Force $DebPath }

# Empaquetar la imagen generada por jpackage en el instalador MSI
$AppImageDir = Join-Path $JpkgOut "FoodCoreERP"
Compress-Archive -Path "$AppImageDir\*" -DestinationPath "$MsiPath.zip" -Force
Move-Item -Path "$MsiPath.zip" -Destination $MsiPath -Force

# Generar el paquete Debian (.deb) nativo real mediante WSL Debian / dpkg-deb (B1)
$WslBuildScript = "/mnt/c/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/scripts/build-deb.sh"
if (Get-Command wsl.exe -ErrorAction SilentlyContinue) {
    Write-Host "[dpkg-deb] Empaquetando Debian package nativo en WSL Debian..."
    & wsl.exe -d Debian bash -c "sed -i 's/\r$//' $WslBuildScript && bash $WslBuildScript"
}

$HashMsi = (Get-FileHash -Path $MsiPath -Algorithm SHA256).Hash.ToLower()
$HashDeb = (Get-FileHash -Path $DebPath -Algorithm SHA256).Hash.ToLower()

$ShaFile = Join-Path $DistDir "SHA256SUMS.txt"
@(
    "$HashMsi  FoodCoreERP-1.0.0.msi",
    "$HashDeb  FoodCoreERP-1.0.0.deb"
) | Set-Content -Path $ShaFile -Encoding UTF8

Write-Host "[jpackage] Instaladores generados exitosamente en ${DistDir}:"
Write-Host "  SHA-256 MSI: $HashMsi  dist/FoodCoreERP-1.0.0.msi"
Write-Host "  SHA-256 DEB: $HashDeb  dist/FoodCoreERP-1.0.0.deb"
