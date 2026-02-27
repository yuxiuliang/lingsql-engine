#!/usr/bin/env bash
# LingSQL MySQL 备份脚本
# 用法:
#   MYSQL_USER=backup MYSQL_PASSWORD=xxx MYSQL_DATABASES="lingsql_engine_db" ./scripts/ops/mysql_backup.sh

set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MYSQL_DATABASES="${MYSQL_DATABASES:-lingsql_engine_db}"
BACKUP_DIR="${BACKUP_DIR:-./backups/mysql}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

if [[ -z "${MYSQL_USER}" || -z "${MYSQL_PASSWORD}" ]]; then
  echo "[ERROR] MYSQL_USER / MYSQL_PASSWORD 不能为空"
  exit 1
fi

if ! command -v mysqldump >/dev/null 2>&1; then
  echo "[ERROR] 未找到 mysqldump，请先安装 MySQL 客户端工具"
  exit 1
fi

timestamp="$(date +%Y%m%d_%H%M%S)"
run_dir="${BACKUP_DIR}/${timestamp}"
mkdir -p "${run_dir}"

IFS=',' read -r -a db_array <<< "${MYSQL_DATABASES}"
for raw_db in "${db_array[@]}"; do
  db="$(echo "${raw_db}" | xargs)"
  if [[ -z "${db}" ]]; then
    continue
  fi
  backup_file="${run_dir}/${db}.sql.gz"
  echo "[INFO] 开始备份数据库: ${db}"
  MYSQL_PWD="${MYSQL_PASSWORD}" mysqldump \
    -h "${MYSQL_HOST}" \
    -P "${MYSQL_PORT}" \
    -u "${MYSQL_USER}" \
    --single-transaction \
    --set-gtid-purged=OFF \
    --routines \
    --triggers \
    --events \
    "${db}" | gzip > "${backup_file}"
  sha256sum "${backup_file}" > "${backup_file}.sha256"
done

meta_file="${run_dir}/backup.meta"
{
  echo "timestamp=${timestamp}"
  echo "host=${MYSQL_HOST}"
  echo "port=${MYSQL_PORT}"
  echo "databases=${MYSQL_DATABASES}"
} > "${meta_file}"

echo "[INFO] 清理 ${RETENTION_DAYS} 天前的历史备份"
find "${BACKUP_DIR}" -mindepth 1 -maxdepth 1 -type d -mtime "+${RETENTION_DAYS}" -exec rm -rf {} \;

echo "[INFO] 备份完成: ${run_dir}"

