#!/usr/bin/env bash
# LingSQL MySQL 恢复脚本
# 用法:
#   MYSQL_USER=root MYSQL_PASSWORD=xxx RESTORE_CONFIRM_DATABASE=lingsql_engine_db \
#   ./scripts/ops/mysql_restore.sh ./backups/mysql/20260227_120000/lingsql_engine_db.sql.gz lingsql_engine_db

set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
BACKUP_FILE="${1:-}"
TARGET_DB="${2:-}"
RESTORE_CONFIRM_DATABASE="${RESTORE_CONFIRM_DATABASE:-}"
RESTORE_REQUIRE_CHECKSUM="${RESTORE_REQUIRE_CHECKSUM:-true}"

if [[ -z "${MYSQL_USER}" || -z "${MYSQL_PASSWORD}" ]]; then
  echo "[ERROR] MYSQL_USER / MYSQL_PASSWORD 不能为空"
  exit 1
fi

if [[ -z "${BACKUP_FILE}" || -z "${TARGET_DB}" ]]; then
  echo "[ERROR] 参数不足"
  echo "用法: ./scripts/ops/mysql_restore.sh <backup.sql.gz|backup.sql> <target_db>"
  exit 1
fi
if ! [[ "${TARGET_DB}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "[ERROR] 目标数据库名只能包含字母、数字和下划线: ${TARGET_DB}"
  exit 1
fi
if [[ "${RESTORE_CONFIRM_DATABASE}" != "${TARGET_DB}" ]]; then
  echo "[ERROR] 必须将 RESTORE_CONFIRM_DATABASE 设置为目标数据库名，明确确认恢复覆盖范围。"
  exit 1
fi
if [[ "${RESTORE_REQUIRE_CHECKSUM}" != "true" && "${RESTORE_REQUIRE_CHECKSUM}" != "false" ]]; then
  echo "[ERROR] RESTORE_REQUIRE_CHECKSUM 只能是 true 或 false"
  exit 1
fi

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "[ERROR] 备份文件不存在: ${BACKUP_FILE}"
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "[ERROR] 未找到 mysql 客户端，请先安装"
  exit 1
fi
for required_cmd in sha256sum gzip grep; do
  if ! command -v "${required_cmd}" >/dev/null 2>&1; then
    echo "[ERROR] 未找到命令: ${required_cmd}"
    exit 1
  fi
done

checksum_file="${BACKUP_FILE}.sha256"
if [[ ! -f "${checksum_file}" ]]; then
  if [[ "${RESTORE_REQUIRE_CHECKSUM}" == "true" ]]; then
    echo "[ERROR] 缺少校验文件: ${checksum_file}"
    exit 1
  fi
  echo "[WARN] 已显式关闭 SHA-256 强制校验，仅允许用于受控的历史备份迁移。"
else
  backup_dir="$(cd "$(dirname "${BACKUP_FILE}")" && pwd -P)"
  backup_name="$(basename "${BACKUP_FILE}")"
  checksum_name="$(basename "${checksum_file}")"
  if ! (cd "${backup_dir}" && sha256sum -c "${checksum_name}" | grep -F -- "${backup_name}: OK" >/dev/null); then
    echo "[ERROR] 备份 SHA-256 校验失败，拒绝恢复。"
    exit 1
  fi
fi

if [[ "${BACKUP_FILE}" == *.gz ]] && ! gzip -t "${BACKUP_FILE}"; then
  echo "[ERROR] gzip 完整性检查失败，拒绝恢复。"
  exit 1
fi

echo "[WARN] 即将恢复到数据库: ${TARGET_DB}"
echo "[WARN] 文件: ${BACKUP_FILE}"
echo "[INFO] SHA-256 与目标数据库确认已通过。"

MYSQL_PWD="${MYSQL_PASSWORD}" mysql \
  -h "${MYSQL_HOST}" \
  -P "${MYSQL_PORT}" \
  -u "${MYSQL_USER}" \
  -e "CREATE DATABASE IF NOT EXISTS \`${TARGET_DB}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

if [[ "${BACKUP_FILE}" == *.gz ]]; then
  gzip -dc "${BACKUP_FILE}" | MYSQL_PWD="${MYSQL_PASSWORD}" mysql \
    -h "${MYSQL_HOST}" \
    -P "${MYSQL_PORT}" \
    -u "${MYSQL_USER}" \
    "${TARGET_DB}"
else
  MYSQL_PWD="${MYSQL_PASSWORD}" mysql \
    -h "${MYSQL_HOST}" \
    -P "${MYSQL_PORT}" \
    -u "${MYSQL_USER}" \
    "${TARGET_DB}" < "${BACKUP_FILE}"
fi

echo "[INFO] 恢复完成: ${TARGET_DB}"
