#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="/mnt/c/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop"
TARGET_JAR="${PROJECT_ROOT}/target/foodcore-erp.jar"
DIST_DIR="${PROJECT_ROOT}/dist"
DEB_FILE="${DIST_DIR}/FoodCoreERP-1.0.0.deb"
JPACKAGE_IN="/var/tmp/foodcore_jpackage_in"
JPACKAGE_OUT="/var/tmp/foodcore_jpackage_out"

if [ ! -f "${TARGET_JAR}" ]; then
    echo "ERROR: No se encuentra ${TARGET_JAR}" >&2
    exit 1
fi

rm -rf "${JPACKAGE_IN}" "${JPACKAGE_OUT}"
mkdir -p "${JPACKAGE_IN}" "${JPACKAGE_OUT}"
cp "${TARGET_JAR}" "${JPACKAGE_IN}/foodcore-erp.jar"

jpackage --type deb \
    --name FoodCoreERP \
    --app-version 1.0.0 \
    --vendor "FoodCore S.A.C." \
    --linux-package-name foodcoreerp \
    --linux-deb-maintainer "ops@foodcore.pe" \
    --description "FoodCore ERP Desktop - Sistema de Gestion Gastronomica" \
    --jlink-options "--strip-debug --no-man-pages --no-header-files" \
    --input "${JPACKAGE_IN}" \
    --main-jar foodcore-erp.jar \
    --main-class com.foodcore.Launcher \
    --dest "${JPACKAGE_OUT}"

mkdir -p "${DIST_DIR}"
rm -f "${DEB_FILE}"
GENERATED_DEB=$(find "${JPACKAGE_OUT}" -maxdepth 1 -name "*.deb" | head -n 1)
cp "${GENERATED_DEB}" "${DEB_FILE}"
rm -rf "${JPACKAGE_IN}" "${JPACKAGE_OUT}"

echo "[OK] Paquete Debian jpackage (con JRE embebido) generado exitosamente: ${DEB_FILE}"
