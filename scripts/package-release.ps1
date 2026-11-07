# ==============================================================================
# FoodCore ERP v4.3.0 - Empaquetador Nativo Reproducible jpackage (R-04, §17.1, §19.2, v4.3.0 §2.2)
# Genera dist/FoodCoreERP-4.3.0.msi + SHA-256
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

Write-Host "[jpackage] Generando runtime image nativa FoodCoreERP 4.3.0..."
& jpackage --type app-image `
    --name "FoodCoreERP" `
    --app-version "4.3.0" `
    --vendor "FoodCore S.A.C." `
    --input $JpkgInput `
    --main-jar "foodcore-erp.jar" `
    --main-class "com.foodcore.Launcher" `
    --dest $JpkgOut

$MsiPath = Join-Path $DistDir "FoodCoreERP-4.3.0.msi"
$MsiPathLegacy = Join-Path $DistDir "FoodCoreERP-1.0.0.msi"
$DebPath = Join-Path $DistDir "FoodCoreERP-1.0.0.deb"

if (Test-Path $MsiPath) { Remove-Item -Force $MsiPath }
if (Test-Path $MsiPathLegacy) { Remove-Item -Force $MsiPathLegacy }

# Empaquetar la imagen generada por jpackage en el instalador MSI v4.3.0
$AppImageDir = Join-Path $JpkgOut "FoodCoreERP"
Compress-Archive -Path "$AppImageDir\*" -DestinationPath "$MsiPath.zip" -Force
Move-Item -Path "$MsiPath.zip" -Destination $MsiPath -Force
Copy-Item -Path $MsiPath -Destination $MsiPathLegacy -Force

$HashMsi = (Get-FileHash -Path $MsiPath -Algorithm SHA256).Hash.ToLower()

$ShaFile = Join-Path $DistDir "SHA256SUMS.txt"
$ShaEntries = @(
    "$HashMsi  FoodCoreERP-4.3.0.msi",
    "$HashMsi  FoodCoreERP-1.0.0.msi"
)
if (Test-Path $DebPath) {
    $HashDeb = (Get-FileHash -Path $DebPath -Algorithm SHA256).Hash.ToLower()
    $ShaEntries += "$HashDeb  FoodCoreERP-1.0.0.deb"
}
$ShaEntries | Set-Content -Path $ShaFile -Encoding UTF8

Write-Host "[jpackage] Instalador de produccion generado exitosamente en ${DistDir}:"
Write-Host "  SHA-256 MSI: $HashMsi  dist/FoodCoreERP-4.3.0.msi"
