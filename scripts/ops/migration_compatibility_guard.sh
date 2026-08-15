#!/usr/bin/env bash
# 只允许自动发布执行可证明向前兼容的 pending Flyway 迁移，并生成校验摘要。

set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCANNER="${SCRIPT_DIR}/migration_sql_compatibility_scan.mjs"
MIGRATION_DIR="${MIGRATION_DIRECTORY:-$(cd "${SCRIPT_DIR}/../../src/main/resources/db/migration" && pwd -P)}"
MYSQL_HOST="${MIGRATION_MYSQL_HOST:-}"
MYSQL_PORT="${MIGRATION_MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MIGRATION_MYSQL_DATABASE:-}"
MYSQL_USER="${MIGRATION_MYSQL_USER:-}"
MYSQL_PASSWORD="${MIGRATION_MYSQL_PASSWORD:-}"
REPORT_FILE="${MIGRATION_COMPATIBILITY_REPORT_FILE:-}"

log_error() { echo "[ERROR] $*" >&2; }
require_value() { [[ -n "$2" ]] || { log_error "$1 不能为空"; exit 1; }; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || { log_error "缺少命令: $1"; exit 1; }; }

version_greater_than() {
  local candidate="$1" current="$2" latest
  [[ "${candidate}" != "${current}" ]] || return 1
  latest="$(printf '%s\n%s\n' "${current}" "${candidate}" | sort -V | tail -n 1)"
  [[ "${latest}" == "${candidate}" ]]
}

read_current_version() {
  MYSQL_PWD="${MYSQL_PASSWORD}" mysql -N -B \
    -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" "${MYSQL_DATABASE}" \
    -e "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"
}

main() {
  local current_version expected_post_version manifest_file report_tmp pending_json pending_count summary_sha
  local migration_file base_name raw_version migration_version file_sha
  local -a pending_files=()

  for command_name in mysql node sha256sum sort tail find grep sed jq mktemp; do require_cmd "${command_name}"; done
  require_value "MIGRATION_MYSQL_HOST" "${MYSQL_HOST}"
  require_value "MIGRATION_MYSQL_DATABASE" "${MYSQL_DATABASE}"
  require_value "MIGRATION_MYSQL_USER" "${MYSQL_USER}"
  require_value "MIGRATION_MYSQL_PASSWORD" "${MYSQL_PASSWORD}"
  require_value "MIGRATION_COMPATIBILITY_REPORT_FILE" "${REPORT_FILE}"
  [[ "${MYSQL_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]] || { log_error "MIGRATION_MYSQL_DATABASE 不是安全标识符"; exit 1; }
  [[ "${MYSQL_PORT}" =~ ^[1-9][0-9]{0,4}$ ]] && (( MYSQL_PORT <= 65535 )) || { log_error "MIGRATION_MYSQL_PORT 非法"; exit 1; }
  [[ -d "${MIGRATION_DIR}" ]] || { log_error "Flyway 迁移目录不存在: ${MIGRATION_DIR}"; exit 1; }
  [[ -f "${SCANNER}" ]] || { log_error "迁移 SQL 扫描器不存在: ${SCANNER}"; exit 1; }

  current_version="$(read_current_version | tr -d '[:space:]')"
  [[ "${current_version}" =~ ^[0-9]+([.][0-9]+)*$ ]] || {
    log_error "无法读取已纳管 Flyway 当前版本: ${current_version:-<empty>}"
    exit 2
  }
  expected_post_version="${current_version}"

  if find "${MIGRATION_DIR}" -maxdepth 1 -type f -name 'R__*.sql' -print -quit | grep -q .; then
    log_error "标准自动发布不接受无法仅按版本判定 pending 状态的 repeatable migration（R__*.sql）"
    exit 2
  fi

  while IFS= read -r migration_file; do
    base_name="$(basename "${migration_file}")"
    if [[ "${base_name}" =~ ^V([0-9]+(_[0-9]+)*)__.+[.]sql$ ]]; then
      raw_version="${BASH_REMATCH[1]}"
      migration_version="${raw_version//_/.}"
      if version_greater_than "${migration_version}" "${current_version}"; then
        pending_files+=("${migration_file}")
        if version_greater_than "${migration_version}" "${expected_post_version}"; then
          expected_post_version="${migration_version}"
        fi
      fi
    fi
  done < <(find "${MIGRATION_DIR}" -maxdepth 1 -type f -name 'V*.sql' -print | sort -V)

  if (( ${#pending_files[@]} > 0 )); then
    node "${SCANNER}" "${pending_files[@]}"
  else
    echo "[INFO] 当前没有 pending Flyway 版本迁移"
  fi

  mkdir -p -- "$(dirname "${REPORT_FILE}")"
  manifest_file="$(mktemp "$(dirname "${REPORT_FILE}")/.migration-manifest.XXXXXX")"
  report_tmp="$(mktemp "$(dirname "${REPORT_FILE}")/.migration-compatibility.XXXXXX")"
  trap 'rm -f -- "${manifest_file:-}" "${report_tmp:-}"' EXIT
  for migration_file in "${pending_files[@]}"; do
    base_name="$(basename "${migration_file}")"
    raw_version="${base_name#V}"
    raw_version="${raw_version%%__*}"
    migration_version="${raw_version//_/.}"
    file_sha="$(sha256sum "${migration_file}" | awk '{print $1}')"
    printf '%s\t%s\t%s\n' "${migration_version}" "${base_name}" "${file_sha}" >> "${manifest_file}"
  done
  summary_sha="$(sha256sum "${manifest_file}" | awk '{print $1}')"
  pending_json="$(jq -Rsc '
    split("\n") | map(select(length > 0) | split("\t") |
      {version:.[0],file:.[1],sha256:.[2]})
  ' "${manifest_file}")"
  pending_count="$(jq 'length' <<< "${pending_json}")"
  jq -cnS \
    --arg createdAt "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
    --arg currentVersion "${current_version}" --arg expectedPostVersion "${expected_post_version}" \
    --arg checksumSummary "${summary_sha}" --argjson pending "${pending_json}" \
    '{schemaVersion:1,createdAt:$createdAt,currentVersion:$currentVersion,expectedPostVersion:$expectedPostVersion,pendingMigrationCount:($pending|length),pendingMigrationChecksumSha256:$checksumSummary,pendingMigrations:$pending,compatibilityConclusion:"PASS"}' \
    > "${report_tmp}"
  chmod 400 "${report_tmp}"
  [[ ! -e "${REPORT_FILE}" && ! -L "${REPORT_FILE}" ]] || { log_error "迁移兼容性报告已存在，拒绝覆盖: ${REPORT_FILE}"; exit 2; }
  ln "${report_tmp}" "${REPORT_FILE}" || { log_error "无法以不可覆盖方式发布迁移兼容性报告"; exit 2; }
  rm -f -- "${report_tmp}" "${manifest_file}"
  trap - EXIT
  echo "[INFO] 迁移兼容性门禁通过: current=${current_version}, pending=${pending_count}, expected=${expected_post_version}"
}

main "$@"
