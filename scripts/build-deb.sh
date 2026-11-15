#!/usr/bin/env bash
set -euo pipefail

APP_VERSION="5.3.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_JAR="${PROJECT_ROOT}/target/foodcore-erp.jar"
DIST_DIR="${PROJECT_ROOT}/dist"
DEB_FILE="${DIST_DIR}/FoodCoreERP-${APP_VERSION}.deb"
JPACKAGE_IN="/var/tmp/foodcore_jpackage_in"
JPACKAGE_OUT="/var/tmp/foodcore_jpackage_out"

if [ ! -f "${TARGET_JAR}" ]; then
    echo "ERROR: No se encuentra ${TARGET_JAR}" >&2
    exit 1
fi

rm -rf "${JPACKAGE_IN}" "${JPACKAGE_OUT}"
mkdir -p "${JPACKAGE_IN}/scripts" "${JPACKAGE_OUT}"
cp "${TARGET_JAR}" "${JPACKAGE_IN}/foodcore-erp.jar"

if [ -d "${PROJECT_ROOT}/pwa" ]; then
    cp -r "${PROJECT_ROOT}/pwa" "${JPACKAGE_IN}/pwa"
fi
if [ -f "${PROJECT_ROOT}/keystore.jks.example" ]; then
    cp "${PROJECT_ROOT}/keystore.jks.example" "${JPACKAGE_IN}/keystore.jks.example"
fi
if [ -f "${PROJECT_ROOT}/foodcore.cer" ]; then
    cp "${PROJECT_ROOT}/foodcore.cer" "${JPACKAGE_IN}/foodcore.cer"
fi
if [ -f "${PROJECT_ROOT}/src/main/resources/application.properties" ]; then
    cp "${PROJECT_ROOT}/src/main/resources/application.properties" "${JPACKAGE_IN}/application.properties.example"
fi

for f in "${PROJECT_ROOT}/scripts"/*.sh "${PROJECT_ROOT}/scripts"/*.ps1 "${PROJECT_ROOT}/scripts"/*.sql; do
    [ -e "$f" ] && cp "$f" "${JPACKAGE_IN}/scripts/"
done

jpackage --type deb \
    --name FoodCoreERP \
    --app-version "${APP_VERSION}" \
    --vendor "FoodCore S.A.C." \
    --linux-package-name foodcoreerp \
    --linux-deb-maintainer "ops@foodcore.pe" \
    --description "FoodCore ERP Desktop v${APP_VERSION} - Sistema de Gestion Gastronomica" \
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

echo "[OK] Paquete Debian jpackage ${APP_VERSION} (con JRE embebido) generado exitosamente: ${DEB_FILE}"
