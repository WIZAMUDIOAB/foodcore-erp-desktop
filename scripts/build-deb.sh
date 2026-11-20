#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
APP_VERSION="$(sed -n 's|.*<version>\([0-9][0-9.]*\)\(-[^<]*\)\?</version>.*|\1|p' "${PROJECT_ROOT}/pom.xml" | head -n 1)"
if [ -z "${APP_VERSION}" ]; then
    echo "ERROR: No se pudo resolver la version desde ${PROJECT_ROOT}/pom.xml" >&2
    exit 1
fi
TARGET_JAR="${PROJECT_ROOT}/target/foodcore-erp.jar"
DIST_DIR="${PROJECT_ROOT}/dist"
DEB_FILE="${DIST_DIR}/FoodCoreERP-${APP_VERSION}.deb"

if [ ! -f "${TARGET_JAR}" ]; then
    echo "ERROR: No se encuentra ${TARGET_JAR}" >&2
    exit 1
fi

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/foodcore_jpackage.XXXXXX")"
cleanup() {
    rm -rf "${WORK_DIR}"
}
trap cleanup EXIT INT TERM

JPACKAGE_IN="${WORK_DIR}/in"
JPACKAGE_OUT="${WORK_DIR}/out"
mkdir -p "${JPACKAGE_IN}/scripts" "${JPACKAGE_OUT}"
cp "${TARGET_JAR}" "${JPACKAGE_IN}/foodcore-erp.jar"

if [ -d "${PROJECT_ROOT}/pwa" ]; then
    cp -r "${PROJECT_ROOT}/pwa" "${JPACKAGE_IN}/pwa"
    find "${JPACKAGE_IN}/pwa" -type f -name "*.cer" -delete
fi
if [ -f "${PROJECT_ROOT}/keystore.jks.example" ]; then
    cp "${PROJECT_ROOT}/keystore.jks.example" "${JPACKAGE_IN}/keystore.jks.example"
fi
if [ -f "${PROJECT_ROOT}/src/main/resources/application.properties" ]; then
    cp "${PROJECT_ROOT}/src/main/resources/application.properties" "${JPACKAGE_IN}/application.properties.example"
fi

# Copiar scripts operativos excluyendo semillas de prueba/demo y certificados de laboratorio
for f in "${PROJECT_ROOT}/scripts"/*.sh "${PROJECT_ROOT}/scripts"/*.ps1 "${PROJECT_ROOT}/scripts"/*.sql; do
    [ -e "$f" ] || continue
    base_name="$(basename "$f")"
    if [ "${base_name}" = "seed_full_operational.sql" ]; then
        continue
    fi
    cp "$f" "${JPACKAGE_IN}/scripts/"
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
GENERATED_DEB="$(find "${JPACKAGE_OUT}" -maxdepth 1 -name "*.deb" | head -n 1)"
cp "${GENERATED_DEB}" "${DEB_FILE}"

echo "[OK] Paquete Debian jpackage ${APP_VERSION} (con JRE embebido) generado exitosamente: ${DEB_FILE}"
