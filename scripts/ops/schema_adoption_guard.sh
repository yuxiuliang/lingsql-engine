#!/usr/bin/env bash
# LingSQL 未纳管数据库接管守卫。
# 仅允许空库、已由 Flyway 管理的库，或与冻结合并基线完全一致的现有库继续。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
BASELINE_FILE="${BASELINE_FILE:-${ROOT_DIR}/src/main/resources/db/migration/B20260815_02__lingsql_control_schema.sql}"
REFERENCE_DATABASE=""
TEMP_DIR=""

log_info() {
  echo "[INFO] $*" >&2
}

log_error() {
  echo "[ERROR] $*" >&2
}

load_env() {
  if [[ -f "${ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
  fi
  : "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD 不能为空}"
  : "${MYSQL_DATABASE:?MYSQL_DATABASE 不能为空}"
  if ! [[ "${MYSQL_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]]; then
    log_error "MYSQL_DATABASE 只能包含字母、数字和下划线。"
    exit 1
  fi
  if [[ ! -f "${BASELINE_FILE}" ]]; then
    log_error "未找到 Flyway 合并基线: ${BASELINE_FILE}"
    exit 1
  fi
}

compose() {
  local args=(-f "${COMPOSE_FILE}")
  if [[ -f "${ENV_FILE}" ]]; then
    args+=(--env-file "${ENV_FILE}")
  fi
  docker compose "${args[@]}" "$@"
}

mysql_query() {
  local database="$1"
  local query="$2"
  compose exec -T mysql mysql -N -B -uroot "-p${MYSQL_ROOT_PASSWORD}" "${database}" -e "${query}"
}

write_fingerprint() {
  local database="$1"
  local output_file="$2"
  {
    mysql_query "${database}" \
      "SELECT CONCAT('T|', TABLE_NAME, '|', ENGINE, '|', TABLE_COLLATION, '|', HEX(COALESCE(TABLE_COMMENT, '')))
         FROM information_schema.TABLES
        WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME LIKE 't_lingsql_engine\\_%'
        ORDER BY TABLE_NAME;"
    mysql_query "${database}" \
      "SELECT CONCAT('C|', TABLE_NAME, '|', LPAD(ORDINAL_POSITION, 4, '0'), '|', COLUMN_NAME, '|', COLUMN_TYPE,
                     '|', IS_NULLABLE, '|', HEX(COALESCE(COLUMN_DEFAULT, '<NULL>')), '|', EXTRA,
                     '|', HEX(COALESCE(GENERATION_EXPRESSION, '')), '|', COALESCE(CHARACTER_SET_NAME, ''),
                     '|', COALESCE(COLLATION_NAME, ''), '|', HEX(COALESCE(COLUMN_COMMENT, '')))
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME LIKE 't_lingsql_engine\\_%'
        ORDER BY TABLE_NAME, ORDINAL_POSITION;"
    mysql_query "${database}" \
      "SELECT CONCAT('I|', TABLE_NAME, '|', INDEX_NAME, '|', NON_UNIQUE, '|', LPAD(SEQ_IN_INDEX, 4, '0'),
                     '|', COALESCE(COLUMN_NAME, ''), '|', COALESCE(SUB_PART, ''), '|', INDEX_TYPE,
                     '|', COALESCE(COLLATION, ''), '|', COALESCE(NULLABLE, ''), '|', HEX(COALESCE(EXPRESSION, '')))
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME LIKE 't_lingsql_engine\\_%'
        ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;"
  } | tr -d '\r' > "${output_file}"
}

cleanup() {
  if [[ -n "${REFERENCE_DATABASE}" ]]; then
    mysql_query mysql "DROP DATABASE IF EXISTS \`${REFERENCE_DATABASE}\`;" >/dev/null 2>&1 || true
  fi
  if [[ -n "${TEMP_DIR}" ]]; then
    rm -rf "${TEMP_DIR}"
  fi
}

main() {
  command -v docker >/dev/null 2>&1 || { log_error "缺少命令: docker"; exit 1; }
  command -v diff >/dev/null 2>&1 || { log_error "缺少命令: diff"; exit 1; }
  load_env

  local history_count table_count actual_file reference_file
  history_count="$(mysql_query "${MYSQL_DATABASE}" \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${MYSQL_DATABASE}' AND TABLE_NAME='flyway_schema_history';")"
  if [[ "${history_count}" -eq 1 ]]; then
    echo "MANAGED"
    return 0
  fi

  table_count="$(mysql_query "${MYSQL_DATABASE}" \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${MYSQL_DATABASE}' AND TABLE_NAME LIKE 't_lingsql_engine\\_%';")"
  if [[ "${table_count}" -eq 0 ]]; then
    echo "EMPTY"
    return 0
  fi

  REFERENCE_DATABASE="lingsql_fingerprint_${RANDOM}_$$"
  TEMP_DIR="$(mktemp -d)"
  actual_file="${TEMP_DIR}/actual.tsv"
  reference_file="${TEMP_DIR}/reference.tsv"
  trap cleanup EXIT

  log_info "检测到未纳管现有库，开始执行完整结构指纹比对。"
  mysql_query mysql \
    "CREATE DATABASE \`${REFERENCE_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" >/dev/null
  compose exec -T mysql mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" "${REFERENCE_DATABASE}" < "${BASELINE_FILE}"
  write_fingerprint "${MYSQL_DATABASE}" "${actual_file}"
  write_fingerprint "${REFERENCE_DATABASE}" "${reference_file}"

  if ! diff -u "${reference_file}" "${actual_file}" >&2; then
    log_error "现有库与冻结合并基线不一致，拒绝自动 baseline。请先备份并按旧库收敛流程处理。"
    exit 2
  fi
  log_info "现有库完整结构指纹与冻结合并基线一致。"
  echo "CURRENT"
}

main "$@"
