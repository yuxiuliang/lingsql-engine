#!/usr/bin/env bash
# LingSQL MySQL 备份脚本
# 用法:
#   MYSQL_USER=backup MYSQL_PASSWORD=xxx MYSQL_DATABASES="lingsql_engine_db" ./scripts/ops/mysql_backup.sh

set -euo pipefail
umask 077

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MYSQL_DATABASES="${MYSQL_DATABASES:-lingsql_engine_db}"
BACKUP_DIR="${BACKUP_DIR:-./backups/mysql}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

validate_identifier() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "[ERROR] ${name} 只能包含字母、数字和下划线: ${value}" >&2
    exit 1
  fi
}

if [[ -z "${MYSQL_USER}" || -z "${MYSQL_PASSWORD}" ]]; then
  echo "[ERROR] MYSQL_USER / MYSQL_PASSWORD 不能为空"
  exit 1
fi
if ! [[ "${RETENTION_DAYS}" =~ ^[0-9]+$ ]]; then
  echo "[ERROR] RETENTION_DAYS 必须是非负整数"
  exit 1
fi

if ! command -v mysqldump >/dev/null 2>&1; then
  echo "[ERROR] 未找到 mysqldump，请先安装 MySQL 客户端工具"
  exit 1
fi
for required_cmd in gzip sha256sum find; do
  if ! command -v "${required_cmd}" >/dev/null 2>&1; then
    echo "[ERROR] 未找到命令: ${required_cmd}" >&2
    exit 1
  fi
done

timestamp="$(date +%Y%m%d_%H%M%S)"
mkdir -p "${BACKUP_DIR}"
backup_root="$(cd "${BACKUP_DIR}" && pwd -P)"
if [[ -z "${backup_root}" || "${backup_root}" == "/" ]]; then
  echo "[ERROR] BACKUP_DIR 解析为不安全路径: ${backup_root:-<empty>}" >&2
  exit 1
fi
run_dir="${backup_root}/${timestamp}"
mkdir -m 700 "${run_dir}"

IFS=',' read -r -a db_array <<< "${MYSQL_DATABASES}"
for raw_db in "${db_array[@]}"; do
  db="$(echo "${raw_db}" | xargs)"
  if [[ -z "${db}" ]]; then
    continue
  fi
  validate_identifier "数据库名" "${db}"
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
  if [[ ! -s "${backup_file}" ]] || ! gzip -t "${backup_file}"; then
    echo "[ERROR] 备份文件为空或 gzip 完整性检查失败: ${backup_file}" >&2
    exit 1
  fi
  (cd "${run_dir}" && sha256sum "${db}.sql.gz" > "${db}.sql.gz.sha256")
  (cd "${run_dir}" && sha256sum -c "${db}.sql.gz.sha256" >/dev/null)
done

meta_file="${run_dir}/backup.meta"
{
  echo "timestamp=${timestamp}"
  echo "host=${MYSQL_HOST}"
  echo "port=${MYSQL_PORT}"
  echo "databases=${MYSQL_DATABASES}"
} > "${meta_file}"

echo "[INFO] 清理 ${RETENTION_DAYS} 天前的历史备份"
find "${backup_root}" -regextype posix-extended -mindepth 1 -maxdepth 1 -type d \
  -regex '.*/[0-9]{8}_[0-9]{6}' -mtime "+${RETENTION_DAYS}" -exec rm -rf -- {} +

echo "[INFO] 备份完成: ${run_dir}"
