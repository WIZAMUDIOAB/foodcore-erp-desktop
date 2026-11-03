#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="/mnt/c/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop"
TARGET_JAR="${PROJECT_ROOT}/target/foodcore-erp.jar"
DIST_DIR="${PROJECT_ROOT}/dist"
DEB_FILE="${DIST_DIR}/FoodCoreERP-1.0.0.deb"
BUILD_ROOT="/tmp/foodcore_deb_staging"

if [ ! -f "${TARGET_JAR}" ]; then
    echo "ERROR: No se encuentra ${TARGET_JAR}" >&2
    exit 1
fi

rm -rf "${BUILD_ROOT}"
mkdir -p "${BUILD_ROOT}/DEBIAN"
mkdir -p "${BUILD_ROOT}/opt/foodcore-erp"
mkdir -p "${BUILD_ROOT}/usr/bin"
mkdir -p "${BUILD_ROOT}/usr/share/applications"

cp "${TARGET_JAR}" "${BUILD_ROOT}/opt/foodcore-erp/foodcore-erp.jar"
chmod 644 "${BUILD_ROOT}/opt/foodcore-erp/foodcore-erp.jar"

cat << 'EOF' > "${BUILD_ROOT}/usr/bin/foodcore-erp"
#!/bin/sh
exec /usr/bin/java -jar /opt/foodcore-erp/foodcore-erp.jar "$@"
EOF
chmod 755 "${BUILD_ROOT}/usr/bin/foodcore-erp"

cat << 'EOF' > "${BUILD_ROOT}/usr/share/applications/foodcore-erp.desktop"
[Desktop Entry]
Name=FoodCore ERP
Comment=Sistema de Gestion Gastronomica
Exec=/usr/bin/foodcore-erp
Icon=foodcore-erp
Terminal=false
Type=Application
Categories=Office;Finance;
EOF
chmod 644 "${BUILD_ROOT}/usr/share/applications/foodcore-erp.desktop"

INSTALLED_SIZE=$(du -s "${BUILD_ROOT}" | awk '{print $1}')

cat << EOF > "${BUILD_ROOT}/DEBIAN/control"
Package: foodcore-erp
Version: 1.0.0
Section: utils
Priority: optional
Architecture: amd64
Maintainer: FoodCore S.A.C. <ops@foodcore.pe>
Installed-Size: ${INSTALLED_SIZE}
Description: FoodCore ERP Desktop - Sistema de Gestion Gastronomica
 Sistema ERP de mision critica para restaurantes, punto de venta (POS),
 visualizador de cocina (KDS) y control de stock e inventario.
EOF
chmod 644 "${BUILD_ROOT}/DEBIAN/control"

mkdir -p "${DIST_DIR}"
rm -f "${DEB_FILE}"

fakeroot dpkg-deb --build --root-owner-group "${BUILD_ROOT}" "${DEB_FILE}"
rm -rf "${BUILD_ROOT}"

echo "[OK] Paquete Debian generado exitosamente: ${DEB_FILE}"
