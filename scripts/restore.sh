#!/usr/bin/env bash
# ==============================================================================
# FoodCore ERP v5.7.1 - Restore Automatizado MySQL 8.0 (SPEC-FOODCORE-v1.0 §17.3, R-16)
# ==============================================================================
set -euo pipefail

BACKUP_FILE="${1:?Uso: ./scripts/restore.sh <ruta_backup.sql.gz|ruta_backup.sql> [db_name]}"
DB_NAME="${2:-${FOODCORE_DB_NAME:-foodcore}}"
DB_HOST="${FOODCORE_DB_HOST:-127.0.0.1}"
DB_PORT="${FOODCORE_DB_PORT:-3306}"
DB_USER="${FOODCORE_MIGRATOR_USER:-foodcore_migrator}"
DB_PASS="${FOODCORE_MIGRATOR_PASS:-${FOODCORE_DB_PASS:-}}"
SSL_MODE="${FOODCORE_DB_SSL_MODE:-REQUIRED}"

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

if [ ! -f "${BACKUP_FILE}" ] || [ ! -s "${BACKUP_FILE}" ]; then
    echo "ERROR: Backup inexistente o vacio: ${BACKUP_FILE}" >&2
    exit 1
fi
case "${BACKUP_FILE}" in
    *.sql.gz) gzip -t "${BACKUP_FILE}" ;;
    *.sql) ;;
    *) echo "ERROR: Extension no permitida (solo .sql o .sql.gz): ${BACKUP_FILE}" >&2; exit 1 ;;
esac

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
cleanup() {
    if [ -n "${DEFAULTS_FILE}" ] && [ -f "${DEFAULTS_FILE}" ]; then
        rm -f "${DEFAULTS_FILE}"
    fi
}
trap cleanup EXIT INT TERM

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

FECHA="$(date +%Y%m%d)"
mkdir -p logs
LOG_FILE="logs/restore_${FECHA}.log"

echo "[$(date -Iseconds)] Iniciando restauracion de ${BACKUP_FILE} en ${DB_NAME}..." | tee -a "${LOG_FILE}"
if [[ "${BACKUP_FILE}" == *.gz ]]; then
    gunzip -c "${BACKUP_FILE}" | mysql --defaults-extra-file="${DEFAULTS_FILE}" \
        --host="${DB_HOST}" --port="${DB_PORT}" --ssl-mode="${SSL_MODE}" "${DB_NAME}"
else
    mysql --defaults-extra-file="${DEFAULTS_FILE}" \
        --host="${DB_HOST}" --port="${DB_PORT}" --ssl-mode="${SSL_MODE}" "${DB_NAME}" < "${BACKUP_FILE}"
fi
echo "[$(date -Iseconds)] Restauracion completada exitosamente." | tee -a "${LOG_FILE}"
