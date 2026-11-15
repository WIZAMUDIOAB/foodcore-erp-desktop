#!/usr/bin/env bash
# ==============================================================================
# FoodCore ERP v5.3.0 - Script de Backup Automático MySQL 8 (Linux / Sub-Fase C)
# 1) Ejecuta mysqldump --single-transaction de la BD foodcore
# 2) Comprime el volcado en formato .sql.gz
# 3) Guarda en carpeta backups/ con permisos restringidos (chmod 700 / 600)
# 4) Elimina backups con más de 30 días de antigüedad
# 5) Con --install-cron registra la tarea diaria a las 03:00 AM
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${FOODCORE_BACKUP_DIR:-${PROJECT_ROOT}/backups}"
LOG_DIR="${PROJECT_ROOT}/logs"

DB_NAME="${FOODCORE_DB_NAME:-foodcore}"
DB_HOST="${FOODCORE_DB_HOST:-127.0.0.1}"
DB_PORT="${FOODCORE_DB_PORT:-3306}"
DB_USER="${FOODCORE_MIGRATOR_USER:-foodcore_migrator}"
DB_PASS="${FOODCORE_MIGRATOR_PASS:-FoodCoreMigrator123!}"
RETENTION_DAYS="${FOODCORE_BACKUP_RETENTION_DAYS:-30}"
INSTALL_CRON=false

for arg in "$@"; do
    case "$arg" in
        --install-cron) INSTALL_CRON=true ;;
        *) DB_NAME="$arg" ;;
    esac
done

mkdir -p "${BACKUP_DIR}" "${LOG_DIR}"
chmod 700 "${BACKUP_DIR}"

STAMP="$(date +%Y%m%d_%H%M%S)"
GZ_FILE="${BACKUP_DIR}/${DB_NAME}_${STAMP}.sql.gz"

export MYSQL_PWD="${DB_PASS}"
mysqldump -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" \
    --single-transaction --no-tablespaces --routines --triggers "${DB_NAME}" | gzip -9 > "${GZ_FILE}"
unset MYSQL_PWD

chmod 600 "${GZ_FILE}"
echo "[OK] Backup generado: ${GZ_FILE}"

# Borrar backups de más de 30 días
find "${BACKUP_DIR}" -maxdepth 1 -type f \( -name "*.sql.gz" -o -name "*.sql" \) -mtime "+${RETENTION_DAYS}" -delete
echo "[OK] Purga de backups > ${RETENTION_DAYS} dias completada."

if [ "${INSTALL_CRON}" = true ]; then
    CRON_LINE="0 3 * * * /bin/bash ${SCRIPT_DIR}/backup-auto.sh >> ${LOG_DIR}/backup-cron.log 2>&1"
    (crontab -l 2>/dev/null | grep -v "backup-auto.sh" || true; echo "${CRON_LINE}") | crontab -
    echo "[OK] Cron registrado a las 03:00 AM: ${CRON_LINE}"
fi
