#!/usr/bin/env bash
# LingSQL Schema 漂移门禁
# 目标：确保 JPA 实体表定义与 Flyway 合并基线同步，避免生产环境 validate 启动失败。
#
# 用法：
#   ./scripts/ops/schema_drift_guard.sh
#
# 可选环境变量：
#   ENTITY_ROOT=./src/main/java
#   SCHEMA_FILE=./src/main/resources/db/migration/B20260815_02__lingsql_control_schema.sql

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENTITY_ROOT="${ENTITY_ROOT:-${ROOT_DIR}/src/main/java}"
SCHEMA_FILE="${SCHEMA_FILE:-${ROOT_DIR}/src/main/resources/db/migration/B20260815_02__lingsql_control_schema.sql}"
LEGACY_SCHEMA_FILE="${LEGACY_SCHEMA_FILE:-${ROOT_DIR}/sql/mysql.sql}"
MIGRATION_DIR="${MIGRATION_DIR:-${ROOT_DIR}/src/main/resources/db/migration}"

log_info() {
  echo "[INFO] $*"
}

log_error() {
  echo "[ERROR] $*" >&2
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    log_error "缺少命令: ${cmd}"
    exit 1
  fi
}

main() {
  require_cmd grep
  require_cmd sed
  require_cmd sort
  require_cmd comm
  require_cmd cmp

  if [[ ! -d "${ENTITY_ROOT}" ]]; then
    log_error "实体目录不存在: ${ENTITY_ROOT}"
    exit 1
  fi
  if [[ ! -f "${SCHEMA_FILE}" ]]; then
    log_error "Schema 文件不存在: ${SCHEMA_FILE}"
    exit 1
  fi
  if ! cmp -s <(sed 's/\r$//' "${SCHEMA_FILE}") <(sed 's/\r$//' "${LEGACY_SCHEMA_FILE}"); then
    log_error "Flyway 合并基线与 sql/mysql.sql 不一致；已发布基线禁止修改，请新增更高版本迁移。"
    exit 1
  fi
  if [[ ! -d "${MIGRATION_DIR}" ]]; then
    log_error "Flyway 迁移目录不存在: ${MIGRATION_DIR}"
    exit 1
  fi

  local entity_tables schema_tables missing_tables
  entity_tables="$(mktemp)"
  schema_tables="$(mktemp)"
  missing_tables="$(mktemp)"
  trap "rm -f '${entity_tables}' '${schema_tables}' '${missing_tables}'" EXIT

  grep -RhoE '@Table\(name[[:space:]]*=[[:space:]]*"[^"]+"\)' "${ENTITY_ROOT}" \
    | sed -E 's/.*"([^"]+)".*/\1/' \
    | sort -u > "${entity_tables}"

  grep -hoE 'CREATE TABLE `[^`]+`' "${MIGRATION_DIR}"/*.sql \
    | sed -E 's/CREATE TABLE `([^`]+)`/\1/' \
    | sort -u > "${schema_tables}"

  if [[ ! -s "${entity_tables}" ]]; then
    log_error "未提取到实体表定义，请检查源码结构。"
    exit 1
  fi
  if [[ ! -s "${schema_tables}" ]]; then
    log_error "未提取到 SQL 建表定义，请检查 ${SCHEMA_FILE}。"
    exit 1
  fi

  comm -23 "${entity_tables}" "${schema_tables}" > "${missing_tables}" || true

  if [[ -s "${missing_tables}" ]]; then
    log_error "检测到 Schema 漂移：以下实体表未在 SQL 初始化脚本中定义"
    while IFS= read -r table_name; do
      echo "  - ${table_name}"
    done < "${missing_tables}"
    exit 2
  fi

  log_info "Schema 漂移检查通过。"
}

main "$@"

