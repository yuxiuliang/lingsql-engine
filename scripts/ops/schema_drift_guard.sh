#!/usr/bin/env bash
# LingSQL Schema 漂移门禁
# 目标：确保 Flyway 基线不可变，且 JPA 实体表被完整迁移链覆盖。
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
MIGRATION_DIR="${MIGRATION_DIR:-${ROOT_DIR}/src/main/resources/db/migration}"
EXPECTED_BASELINE_SHA256="749124fb3862fefbe366bb0b0ef861de01475b4a79845bab20a6beb2cf606f34"

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
  require_cmd sha256sum

  if [[ ! -d "${ENTITY_ROOT}" ]]; then
    log_error "实体目录不存在: ${ENTITY_ROOT}"
    exit 1
  fi
  if [[ ! -f "${SCHEMA_FILE}" ]]; then
    log_error "Schema 文件不存在: ${SCHEMA_FILE}"
    exit 1
  fi
  local baseline_sha256
  baseline_sha256="$(sed 's/\r$//' "${SCHEMA_FILE}" | sha256sum | sed -E 's/[[:space:]].*$//')"
  if [[ "${baseline_sha256}" != "${EXPECTED_BASELINE_SHA256}" ]]; then
    log_error "Flyway 已发布基线校验和发生变化；禁止修改基线，请新增更高版本迁移。"
    exit 1
  fi
  if [[ ! -d "${MIGRATION_DIR}" ]]; then
    log_error "Flyway 迁移目录不存在: ${MIGRATION_DIR}"
    exit 1
  fi

  local entity_tables migration_tables missing_migration_tables
  entity_tables="$(mktemp)"
  migration_tables="$(mktemp)"
  missing_migration_tables="$(mktemp)"
  trap "rm -f '${entity_tables}' '${migration_tables}' '${missing_migration_tables}'" EXIT

  grep -RhoE '@Table\(name[[:space:]]*=[[:space:]]*"[^"]+"\)' "${ENTITY_ROOT}" \
    | sed -E 's/.*"([^"]+)".*/\1/' \
    | sort -u > "${entity_tables}"

  grep -hoE 'CREATE TABLE `[^`]+`' "${MIGRATION_DIR}"/*.sql \
    | sed -E 's/CREATE TABLE `([^`]+)`/\1/' \
    | sort -u > "${migration_tables}"

  if [[ ! -s "${entity_tables}" ]]; then
    log_error "未提取到实体表定义，请检查源码结构。"
    exit 1
  fi
  if [[ ! -s "${migration_tables}" ]]; then
    log_error "未从 Flyway 迁移链提取到建表定义。"
    exit 1
  fi
  comm -23 "${entity_tables}" "${migration_tables}" > "${missing_migration_tables}" || true

  if [[ -s "${missing_migration_tables}" ]]; then
    log_error "检测到 Schema 漂移：以下实体表未在 Flyway 迁移链中定义"
    while IFS= read -r table_name; do
      echo "  - ${table_name}"
    done < "${missing_migration_tables}"
    exit 2
  fi
  log_info "Schema 漂移检查通过。"
}

main "$@"

