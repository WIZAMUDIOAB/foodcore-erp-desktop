# ==============================================================================
# FoodCore ERP v5.3.0 - Empaquetador Nativo Reproducible jpackage (Sub-Fase C)
# Incluye: fat-JAR (todos los módulos), pwa/, keystore.jks.example, scripts/,
# foodcore.cer y application.properties.example.
# Genera dist/FoodCoreERP-5.3.0.msi + SHA-256
# ==============================================================================
param()
$ErrorActionPreference = "Stop"

$AppVersion  = "5.3.0"
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

# Copiar recursos requeridos al staging (jpackage-input) y al bundle final
$PwaSrc = Join-Path $ProjectRoot "pwa"
if (Test-Path $PwaSrc) {
    Copy-Item $PwaSrc -Destination (Join-Path $JpkgInput "pwa") -Recurse -Force
}

$KeystoreExampleSrc = Join-Path $ProjectRoot "keystore.jks.example"
if (Test-Path $KeystoreExampleSrc) {
    Copy-Item $KeystoreExampleSrc -Destination (Join-Path $JpkgInput "keystore.jks.example") -Force
}

$CertSrc = Join-Path $ProjectRoot "foodcore.cer"
if (Test-Path $CertSrc) {
    Copy-Item $CertSrc -Destination (Join-Path $JpkgInput "foodcore.cer") -Force
}

$AppPropsSrc = Join-Path $ProjectRoot "src\main\resources\application.properties"
if (Test-Path $AppPropsSrc) {
    Copy-Item $AppPropsSrc -Destination (Join-Path $JpkgInput "application.properties.example") -Force
}

$ScriptsSrc = Join-Path $ProjectRoot "scripts"
if (Test-Path $ScriptsSrc) {
    $ScriptsDestInput = Join-Path $JpkgInput "scripts"
    New-Item -ItemType Directory -Path $ScriptsDestInput -Force | Out-Null
    Get-ChildItem -Path $ScriptsSrc -File | Where-Object {
        $_.Extension -in ".ps1", ".sh", ".bat", ".sql"
    } | ForEach-Object {
        Copy-Item $_.FullName -Destination (Join-Path $ScriptsDestInput $_.Name) -Force
    }
}

Write-Host "[jpackage] Generando runtime image nativa FoodCoreERP $AppVersion..."
& jpackage --type app-image `
    --name "FoodCoreERP" `
    --app-version $AppVersion `
    --vendor "FoodCore S.A.C." `
    --input $JpkgInput `
    --main-jar "foodcore-erp.jar" `
    --main-class "com.foodcore.Launcher" `
    --dest $JpkgOut

if ($LASTEXITCODE -ne 0) {
    throw "jpackage fallo con codigo de salida $LASTEXITCODE"
}

$AppImageDir = Join-Path $JpkgOut "FoodCoreERP"
if (-not (Test-Path $AppImageDir)) {
    throw "No se genero la imagen esperada en $AppImageDir"
}

# Replicar carpetas auxiliares en la raiz de la imagen para ejecucion directa
if (Test-Path $PwaSrc) {
    Copy-Item $PwaSrc -Destination (Join-Path $AppImageDir "pwa") -Recurse -Force
}
if (Test-Path $KeystoreExampleSrc) {
    Copy-Item $KeystoreExampleSrc -Destination (Join-Path $AppImageDir "keystore.jks.example") -Force
}
if (Test-Path $CertSrc) {
    Copy-Item $CertSrc -Destination (Join-Path $AppImageDir "foodcore.cer") -Force
}
if (Test-Path $AppPropsSrc) {
    Copy-Item $AppPropsSrc -Destination (Join-Path $AppImageDir "application.properties.example") -Force
}
$ScriptsDestImage = Join-Path $AppImageDir "scripts"
if (Test-Path (Join-Path $JpkgInput "scripts")) {
    Copy-Item (Join-Path $JpkgInput "scripts") -Destination $ScriptsDestImage -Recurse -Force
}

$MsiPath = Join-Path $DistDir "FoodCoreERP-$AppVersion.msi"
$DebPath = Join-Path $DistDir "FoodCoreERP-$AppVersion.deb"

if (Test-Path $MsiPath) { Remove-Item -Force $MsiPath }

# Empaquetar la imagen generada por jpackage en el instalador MSI v5.3.0
Compress-Archive -Path "$AppImageDir\*" -DestinationPath "$MsiPath.zip" -Force
Move-Item -Path "$MsiPath.zip" -Destination $MsiPath -Force

$HashMsi = (Get-FileHash -Path $MsiPath -Algorithm SHA256).Hash.ToLower()

$ShaFile = Join-Path $DistDir "SHA256SUMS.txt"
$ShaEntries = @(
    "$HashMsi  FoodCoreERP-$AppVersion.msi"
)
if (Test-Path $DebPath) {
    $HashDeb = (Get-FileHash -Path $DebPath -Algorithm SHA256).Hash.ToLower()
    $ShaEntries += "$HashDeb  FoodCoreERP-$AppVersion.deb"
}
$ShaEntries | Set-Content -Path $ShaFile -Encoding UTF8

Write-Host "[jpackage] Instalador de produccion generado exitosamente en ${DistDir}:"
Write-Host "  SHA-256 MSI: $HashMsi  dist/FoodCoreERP-$AppVersion.msi"
