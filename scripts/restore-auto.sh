#!/usr/bin/env bash
# ==============================================================================
# FoodCore ERP v5.7.1 - Restauracion Automatizada MySQL 8 (Linux / Sub-Fase C)
# 1) Solicita o recibe la ruta del backup (.sql.gz o .sql) y valida parametros
# 2) Valida existencia e integridad GZip del archivo
# 3) Detiene servicios activos (foodcore-erp)
# 4) Restaura el volcado en MySQL via --defaults-extra-file temporal (0600, trap cleanup)
# 5) Reinicia los servicios
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${PROJECT_ROOT}/logs"
mkdir -p "${LOG_DIR}"

BACKUP_FILE="${1:-}"
if [ -z "${BACKUP_FILE}" ]; then
    if [ -t 0 ]; then
        read -r -p "Ingrese la ruta del archivo de backup (.sql.gz o .sql): " BACKUP_FILE
    else
        echo "ERROR: Debe especificar la ruta del archivo de backup." >&2
        exit 1
    fi
fi

if [ ! -f "${BACKUP_FILE}" ] || [ ! -s "${BACKUP_FILE}" ]; then
    echo "ERROR: Backup inexistente o vacio: ${BACKUP_FILE}" >&2
    exit 1
fi
case "${BACKUP_FILE}" in
    *.sql.gz|*.sql) ;;
    *) echo "ERROR: Extension de backup no permitida (solo .sql o .sql.gz): ${BACKUP_FILE}" >&2; exit 1 ;;
esac

DB_NAME="${FOODCORE_DB_NAME:-foodcore}"
DB_HOST="${FOODCORE_DB_HOST:-127.0.0.1}"
DB_PORT="${FOODCORE_DB_PORT:-3306}"
DB_USER="${FOODCORE_MIGRATOR_USER:-foodcore_migrator}"
DB_PASS="${FOODCORE_MIGRATOR_PASS:-${FOODCORE_DB_PASS:-}}"
SSL_MODE="${FOODCORE_DB_SSL_MODE:-REQUIRED}"
LOG_FILE="${LOG_DIR}/restore_$(date +%Y%m%d_%H%M%S).log"

validate_param() {
    local val="$1" pattern="$2" name="$3"
    if [[ -z "${val}" || ! "${val}" =~ ${pattern} ]]; then
        echo "ERROR: Parametro invalido para ${name}." >&2
        exit 1
    fi
}

validate_param "${DB_NAME}" '^[A-Za-z0-9_]{1,64}$' "DB_NAME"
validate_param "${DB_HOST}" '^[A-Za-z0-9][A-Za-z0-9._:-]{0,252}$' "DB_HOST"
validate_param "${DB_PORT}" '^[0-9]{1,5}$' "DB_PORT"
validate_param "${DB_USER}" '^[A-Za-z0-9_.-]{1,64}$' "DB_USER"
if [ "${DB_PORT}" -lt 1 ] || [ "${DB_PORT}" -gt 65535 ]; then
    echo "ERROR: DB_PORT fuera de rango (1..65535)." >&2
    exit 1
fi
case "${SSL_MODE^^}" in
    REQUIRED|VERIFY_CA|VERIFY_IDENTITY|PREFERRED) SSL_MODE="${SSL_MODE^^}" ;;
    *) echo "ERROR: FOODCORE_DB_SSL_MODE invalido: ${SSL_MODE}" >&2; exit 1 ;;
esac

if [[ "${BACKUP_FILE}" == *.gz ]]; then
    gzip -t "${BACKUP_FILE}"
fi

if [ -z "${DB_PASS}" ]; then
    if [ -t 0 ]; then
        read -r -s -p "Ingrese la contrasena de MySQL para '${DB_USER}': " DB_PASS
        echo ""
    fi
fi
if [ -z "${DB_PASS}" ]; then
    echo "ERROR: Defina FOODCORE_MIGRATOR_PASS o FOODCORE_DB_PASS en el entorno." >&2
    exit 1
fi
if [[ "${DB_PASS}" == *$'\n'* || "${DB_PASS}" == *$'\r'* ]]; then
    echo "ERROR: La contrasena contiene caracteres de control no permitidos." >&2
    exit 1
fi

DEFAULTS_FILE=""
SERVICE_STOPPED=false

cleanup_all() {
    if [ -n "${DEFAULTS_FILE}" ] && [ -f "${DEFAULTS_FILE}" ]; then
        rm -f "${DEFAULTS_FILE}"
    fi
    if [ "${SERVICE_STOPPED}" = true ] && command -v systemctl >/dev/null 2>&1; then
        echo "[$(date -Iseconds)] Reiniciando servicio foodcore-erp..." | tee -a "${LOG_FILE}"
        systemctl start foodcore-erp || true
    fi
}
trap cleanup_all EXIT INT TERM

escape_mysql_val() {
    local v="$1"
    v="${v//\\/\\\\}"
    v="${v//\"/\\\"}"
    printf '%s' "${v}"
}

OLD_UMASK="$(umask)"
umask 077
DEFAULTS_FILE="$(mktemp "${TMPDIR:-/tmp}/foodcore_mysql_XXXXXX.cnf")"
umask "${OLD_UMASK}"
chmod 600 "${DEFAULTS_FILE}"
{
    printf '[client]\n'
    printf 'user="%s"\n' "$(escape_mysql_val "${DB_USER}")"
    printf 'password="%s"\n' "$(escape_mysql_val "${DB_PASS}")"
} > "${DEFAULTS_FILE}"

if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet foodcore-erp 2>/dev/null; then
    echo "[$(date -Iseconds)] Deteniendo servicio foodcore-erp..." | tee -a "${LOG_FILE}"
    systemctl stop foodcore-erp
    SERVICE_STOPPED=true
fi

echo "[$(date -Iseconds)] Restaurando ${BACKUP_FILE} en ${DB_NAME}..." | tee -a "${LOG_FILE}"
if [[ "${BACKUP_FILE}" == *.gz ]]; then
    gunzip -c "${BACKUP_FILE}" | mysql --defaults-extra-file="${DEFAULTS_FILE}" \
        --host="${DB_HOST}" --port="${DB_PORT}" --ssl-mode="${SSL_MODE}" "${DB_NAME}"
else
    mysql --defaults-extra-file="${DEFAULTS_FILE}" \
        --host="${DB_HOST}" --port="${DB_PORT}" --ssl-mode="${SSL_MODE}" "${DB_NAME}" < "${BACKUP_FILE}"
fi

echo "[$(date -Iseconds)] Restauracion completada exitosamente." | tee -a "${LOG_FILE}"
