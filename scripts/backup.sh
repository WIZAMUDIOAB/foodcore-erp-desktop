#!/usr/bin/env bash
# ==============================================================================
# FoodCore ERP v1.0 - Backup Automatizado MySQL 8.0 (SPEC-FOODCORE-v1.0 §17.3, R-16)
# ==============================================================================
set -euo pipefail

DB_NAME="${1:-foodcore}"
DB_USER="${FOODCORE_MIGRATOR_USER:-foodcore_migrator}"
DB_PASS="${FOODCORE_MIGRATOR_PASS:-FoodCoreMigrator#2026!}"
FECHA="$(date +%Y%m%d)"
BACKUP_DIR="backups"
mkdir -p "${BACKUP_DIR}"
BACKUP_FILE="${BACKUP_DIR}/foodcore_${FECHA}.sql.gz"

mysqldump -h 127.0.0.1 -u "${DB_USER}" -p"${DB_PASS}" \
    --single-transaction --no-tablespaces --routines --triggers "${DB_NAME}" | gzip > "${BACKUP_FILE}"

echo "[OK] Backup generado: ${BACKUP_FILE}"
