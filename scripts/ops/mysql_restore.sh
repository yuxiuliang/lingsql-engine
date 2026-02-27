#!/usr/bin/env bash
# LingSQL MySQL 恢复脚本
# 用法:
#   MYSQL_USER=root MYSQL_PASSWORD=xxx ./scripts/ops/mysql_restore.sh ./backups/mysql/20260227_120000/lingsql_engine_db.sql.gz lingsql_engine_db

set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
BACKUP_FILE="${1:-}"
TARGET_DB="${2:-}"

if [[ -z "${MYSQL_USER}" || -z "${MYSQL_PASSWORD}" ]]; then
  echo "[ERROR] MYSQL_USER / MYSQL_PASSWORD 不能为空"
  exit 1
fi

if [[ -z "${BACKUP_FILE}" || -z "${TARGET_DB}" ]]; then
  echo "[ERROR] 参数不足"
  echo "用法: ./scripts/ops/mysql_restore.sh <backup.sql.gz|backup.sql> <target_db>"
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

echo "[WARN] 即将恢复到数据库: ${TARGET_DB}"
echo "[WARN] 文件: ${BACKUP_FILE}"

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

