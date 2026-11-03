#!/usr/bin/env bash
# ==============================================================================
# FoodCore ERP v1.0 - Restore Automatizado MySQL 8.0 (SPEC-FOODCORE-v1.0 §17.3, R-16)
# ==============================================================================
set -euo pipefail

BACKUP_FILE="${1:?Uso: ./scripts/restore.sh <ruta_backup.sql.gz>}"
DB_NAME="${2:-foodcore}"
DB_USER="${FOODCORE_MIGRATOR_USER:-foodcore_migrator}"
DB_PASS="${FOODCORE_MIGRATOR_PASS:-FoodCoreMigrator#2026!}"
FECHA="$(date +%Y%m%d)"
mkdir -p logs
LOG_FILE="logs/restore_${FECHA}.log"

echo "[$(date -Iseconds)] Iniciando restauracion de ${BACKUP_FILE} en ${DB_NAME}..." | tee -a "${LOG_FILE}"
gunzip -c "${BACKUP_FILE}" | mysql -h 127.0.0.1 -u "${DB_USER}" -p"${DB_PASS}" "${DB_NAME}"
echo "[$(date -Iseconds)] Restauracion completada exitosamente." | tee -a "${LOG_FILE}"
