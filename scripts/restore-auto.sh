#!/usr/bin/env bash
# ==============================================================================
# FoodCore ERP v5.3.0 - Restauración Automatizada MySQL 8 (Linux / Sub-Fase C)
# 1) Solicita o recibe la ruta del backup (.sql.gz o .sql)
# 2) Valida existencia e integridad del archivo
# 3) Detiene servicios activos (foodcore-erp / cloudflared)
# 4) Restaura el volcado en MySQL
# 5) Reinicia los servicios
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${PROJECT_ROOT}/logs"
mkdir -p "${LOG_DIR}"

BACKUP_FILE="${1:-}"
if [ -z "${BACKUP_FILE}" ]; then
    read -r -p "Ingrese la ruta del archivo de backup (.sql.gz o .sql): " BACKUP_FILE
fi

if [ ! -f "${BACKUP_FILE}" ] || [ ! -s "${BACKUP_FILE}" ]; then
    echo "ERROR: Backup inexistente o vacio: ${BACKUP_FILE}" >&2
    exit 1
fi

DB_NAME="${FOODCORE_DB_NAME:-foodcore}"
DB_HOST="${FOODCORE_DB_HOST:-127.0.0.1}"
DB_PORT="${FOODCORE_DB_PORT:-3306}"
DB_USER="${FOODCORE_MIGRATOR_USER:-foodcore_migrator}"
DB_PASS="${FOODCORE_MIGRATOR_PASS:-FoodCoreMigrator123!}"
LOG_FILE="${LOG_DIR}/restore_$(date +%Y%m%d_%H%M%S).log"

if [[ "${BACKUP_FILE}" == *.gz ]]; then
    gzip -t "${BACKUP_FILE}"
fi

SERVICE_STOPPED=false
if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet foodcore-erp 2>/dev/null; then
    echo "[$(date -Iseconds)] Deteniendo servicio foodcore-erp..." | tee -a "${LOG_FILE}"
    systemctl stop foodcore-erp
    SERVICE_STOPPED=true
fi

cleanup_restart() {
    if [ "${SERVICE_STOPPED}" = true ] && command -v systemctl >/dev/null 2>&1; then
        echo "[$(date -Iseconds)] Reiniciando servicio foodcore-erp..." | tee -a "${LOG_FILE}"
        systemctl start foodcore-erp || true
    fi
}
trap cleanup_restart EXIT

echo "[$(date -Iseconds)] Restaurando ${BACKUP_FILE} en ${DB_NAME}..." | tee -a "${LOG_FILE}"
export MYSQL_PWD="${DB_PASS}"
if [[ "${BACKUP_FILE}" == *.gz ]]; then
    gunzip -c "${BACKUP_FILE}" | mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" "${DB_NAME}"
else
    mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" "${DB_NAME}" < "${BACKUP_FILE}"
fi
unset MYSQL_PWD

echo "[$(date -Iseconds)] Restauracion completada exitosamente." | tee -a "${LOG_FILE}"
