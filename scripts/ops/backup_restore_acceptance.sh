#!/usr/bin/env bash
# LingSQL 商用备份恢复抽查。仅允许恢复到全新的 lingsql_restore_verify_ 前缀数据库。
# 必填环境变量见 docs/标准化交付部署手册.md，任何凭据、备份或指纹证据缺失都会失败。

set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_SCRIPT="${SCRIPT_DIR}/mysql_backup.sh"
RESTORE_SCRIPT="${SCRIPT_DIR}/mysql_restore.sh"
VALIDATION_DB_PREFIX="lingsql_restore_verify_"

MYSQL_HOST="${BACKUP_RESTORE_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${BACKUP_RESTORE_MYSQL_PORT:-3306}"
SOURCE_DB="${BACKUP_RESTORE_SOURCE_DB:-}"
TARGET_DB="${BACKUP_RESTORE_TARGET_DB:-}"
BACKUP_USER="${BACKUP_RESTORE_BACKUP_USER:-}"
BACKUP_PASSWORD="${BACKUP_RESTORE_BACKUP_PASSWORD:-}"
RESTORE_USER="${BACKUP_RESTORE_RESTORE_USER:-}"
RESTORE_PASSWORD="${BACKUP_RESTORE_RESTORE_PASSWORD:-}"
BACKUP_ROOT_INPUT="${BACKUP_RESTORE_BACKUP_DIR:-}"
WORK_ROOT_INPUT="${BACKUP_RESTORE_WORK_ROOT:-}"
SAMPLE_QUERY="${BACKUP_RESTORE_SAMPLE_QUERY:-}"
REPORT_FILE="${BACKUP_RESTORE_REPORT_FILE:-./reports/backup_restore_acceptance.md}"

target_may_exist=false
work_dir=""

log_info() {
  echo "[INFO] $*"
}

log_error() {
  echo "[ERROR] $*" >&2
}

require_value() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    log_error "${name} 不能为空"
    exit 1
  fi
}

require_cmd() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    log_error "缺少命令: ${command_name}"
    exit 1
  fi
}

validate_identifier() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[A-Za-z0-9_]+$ ]] || [[ "${#value}" -gt 64 ]]; then
    log_error "${name} 必须是长度不超过 64 的 MySQL 标识符: ${value}"
    exit 1
  fi
}

resolve_safe_root() {
  local name="$1"
  local input="$2"
  require_value "${name}" "${input}"
  if [[ "${input}" != /* ]]; then
    log_error "${name} 必须是显式绝对路径: ${input}"
    exit 1
  fi
  mkdir -p -- "${input}"
  local resolved
  resolved="$(cd "${input}" && pwd -P)"
  if [[ -z "${resolved}" || "${resolved}" == "/" ]]; then
    log_error "${name} 解析为不安全路径: ${resolved:-<empty>}"
    exit 1
  fi
  printf '%s' "${resolved}"
}

validate_sample_query() {
  local normalized
  normalized="$(printf '%s' "${SAMPLE_QUERY}" | tr '\r\n\t' '   ' | xargs)"
  require_value "BACKUP_RESTORE_SAMPLE_QUERY" "${normalized}"
  if ! [[ "${normalized}" =~ ^([Ss][Ee][Ll][Ee][Cc][Tt])[[:space:]] ]]; then
    log_error "样例指纹查询必须以 SELECT 开头"
    exit 1
  fi
  if [[ "${normalized}" == *";"* ]]; then
    log_error "样例指纹查询只能包含一条无分号 SELECT"
    exit 1
  fi
  local upper_query="${normalized^^}"
  local forbidden_regex='(^|[[:space:]])(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|LOAD|INTO|OUTFILE|DUMPFILE|CALL|DO|SET|SLEEP|BENCHMARK|LOAD_FILE)([[:space:]()]|$)'
  if [[ "${upper_query}" == *"/*"* || "${upper_query}" == *"--"* || "${upper_query}" == *"#"* \
      || "${upper_query}" =~ ${forbidden_regex} ]]; then
    log_error "样例指纹查询包含禁止的写入或管理关键字"
    exit 1
  fi
  SAMPLE_QUERY="${normalized}"
}

mysql_query() {
  local user="$1"
  local password="$2"
  local database="$3"
  local query="$4"
  local database_args=()
  if [[ -n "${database}" ]]; then
    database_args=("${database}")
  fi
  MYSQL_PWD="${password}" mysql -N -B \
    -h "${MYSQL_HOST}" \
    -P "${MYSQL_PORT}" \
    -u "${user}" \
    "${database_args[@]}" \
    -e "${query}"
}

database_exists() {
  local database="$1"
  local count
  count="$(mysql_query "${RESTORE_USER}" "${RESTORE_PASSWORD}" "" \
    "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '${database}'")"
  [[ "${count}" == "1" ]]
}

structure_fingerprint() {
  local user="$1"
  local password="$2"
  local database="$3"
  local table_count
  table_count="$(mysql_query "${user}" "${password}" "" \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '${database}' AND TABLE_TYPE = 'BASE TABLE'")"
  if ! [[ "${table_count}" =~ ^[0-9]+$ ]] || [[ "${table_count}" -le 0 ]]; then
    log_error "数据库 ${database} 没有可验收的基础表"
    return 1
  fi
  {
    mysql_query "${user}" "${password}" "" \
      "SELECT CONCAT('T|', TABLE_NAME, '|', TABLE_TYPE, '|', COALESCE(ENGINE, ''), '|', COALESCE(TABLE_COLLATION, '')) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '${database}' ORDER BY TABLE_NAME"
    mysql_query "${user}" "${password}" "" \
      "SELECT CONCAT('C|', TABLE_NAME, '|', ORDINAL_POSITION, '|', COLUMN_NAME, '|', COLUMN_TYPE, '|', IS_NULLABLE, '|', COALESCE(COLUMN_DEFAULT, '<NULL>'), '|', EXTRA, '|', COALESCE(GENERATION_EXPRESSION, ''), '|', COALESCE(COLLATION_NAME, '')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '${database}' ORDER BY TABLE_NAME, ORDINAL_POSITION"
    mysql_query "${user}" "${password}" "" \
      "SELECT CONCAT('I|', TABLE_NAME, '|', INDEX_NAME, '|', SEQ_IN_INDEX, '|', COALESCE(COLUMN_NAME, ''), '|', NON_UNIQUE, '|', COALESCE(SUB_PART, 0), '|', COALESCE(INDEX_TYPE, '')) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '${database}' ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX"
    mysql_query "${user}" "${password}" "" \
      "SELECT CONCAT('K|', TABLE_NAME, '|', CONSTRAINT_NAME, '|', CONSTRAINT_TYPE) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = '${database}' ORDER BY TABLE_NAME, CONSTRAINT_NAME"
    mysql_query "${user}" "${password}" "" \
      "SELECT CONCAT('F|', TABLE_NAME, '|', CONSTRAINT_NAME, '|', ORDINAL_POSITION, '|', COLUMN_NAME, '|', COALESCE(REFERENCED_TABLE_NAME, ''), '|', COALESCE(REFERENCED_COLUMN_NAME, '')) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = '${database}' ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION"
    mysql_query "${user}" "${password}" "" \
      "SELECT CONCAT('R|', TRIGGER_NAME, '|', EVENT_MANIPULATION, '|', EVENT_OBJECT_TABLE, '|', ACTION_TIMING, '|', ACTION_STATEMENT) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = '${database}' ORDER BY TRIGGER_NAME"
    mysql_query "${user}" "${password}" "" \
      "SELECT CONCAT('P|', ROUTINE_NAME, '|', ROUTINE_TYPE, '|', COALESCE(DTD_IDENTIFIER, ''), '|', COALESCE(ROUTINE_DEFINITION, ''), '|', SQL_MODE) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = '${database}' ORDER BY ROUTINE_NAME, ROUTINE_TYPE"
    mysql_query "${user}" "${password}" "" \
      "SELECT CONCAT('E|', EVENT_NAME, '|', STATUS, '|', EVENT_TYPE, '|', COALESCE(EXECUTE_AT, ''), '|', COALESCE(INTERVAL_VALUE, ''), '|', COALESCE(INTERVAL_FIELD, ''), '|', EVENT_DEFINITION) FROM information_schema.EVENTS WHERE EVENT_SCHEMA = '${database}' ORDER BY EVENT_NAME"
  } | LC_ALL=C sort | sha256sum | awk '{print $1}'
}

sample_fingerprint() {
  local user="$1"
  local password="$2"
  local database="$3"
  local output
  output="$(mysql_query "${user}" "${password}" "${database}" "${SAMPLE_QUERY}")"
  if [[ -z "${output}" ]]; then
    log_error "数据库 ${database} 的样例指纹查询返回为空"
    return 1
  fi
  printf '%s' "${output}" | sha256sum | awk '{print $1}'
}

drop_validation_database() {
  if [[ "${target_may_exist}" != "true" ]]; then
    return 0
  fi
  if [[ "${TARGET_DB}" != "${VALIDATION_DB_PREFIX}"* || "${TARGET_DB}" == "${SOURCE_DB}" ]]; then
    log_error "拒绝清理非验证目标数据库: ${TARGET_DB}"
    return 1
  fi
  log_info "清理隔离恢复目标数据库: ${TARGET_DB}"
  mysql_query "${RESTORE_USER}" "${RESTORE_PASSWORD}" "" \
    "DROP DATABASE IF EXISTS \`${TARGET_DB}\`" >/dev/null
  target_may_exist=false
}

cleanup_work_dir() {
  if [[ -z "${work_dir}" || ! -d "${work_dir}" ]]; then
    return 0
  fi
  local resolved_work resolved_root base_name
  resolved_work="$(cd "${work_dir}" && pwd -P)"
  resolved_root="$(cd "${WORK_ROOT_INPUT}" && pwd -P)"
  base_name="$(basename "${resolved_work}")"
  if [[ "${resolved_work}" != "${resolved_root}"/* || "${base_name}" != run_${VALIDATION_DB_PREFIX}* ]]; then
    log_error "拒绝清理不在验证前缀绝对路径下的目录: ${resolved_work}"
    return 1
  fi
  rm -rf -- "${resolved_work}"
  work_dir=""
}

cleanup() {
  local exit_code=$?
  trap - EXIT
  set +e
  drop_validation_database
  local database_cleanup_code=$?
  cleanup_work_dir
  local work_cleanup_code=$?
  if [[ "${database_cleanup_code}" -ne 0 || "${work_cleanup_code}" -ne 0 ]]; then
    exit_code=1
  fi
  exit "${exit_code}"
}

main() {
  for command_name in mysql mysqldump gzip sha256sum awk sort xargs sed tail wc tr basename dirname mkdir rm; do
    require_cmd "${command_name}"
  done
  if [[ ! -x "${BACKUP_SCRIPT}" || ! -x "${RESTORE_SCRIPT}" ]]; then
    log_error "备份或恢复脚本不存在/不可执行"
    exit 1
  fi

  require_value "BACKUP_RESTORE_SOURCE_DB" "${SOURCE_DB}"
  require_value "BACKUP_RESTORE_TARGET_DB" "${TARGET_DB}"
  require_value "BACKUP_RESTORE_BACKUP_USER" "${BACKUP_USER}"
  require_value "BACKUP_RESTORE_BACKUP_PASSWORD" "${BACKUP_PASSWORD}"
  require_value "BACKUP_RESTORE_RESTORE_USER" "${RESTORE_USER}"
  require_value "BACKUP_RESTORE_RESTORE_PASSWORD" "${RESTORE_PASSWORD}"
  validate_identifier "源数据库名" "${SOURCE_DB}"
  validate_identifier "隔离目标数据库名" "${TARGET_DB}"
  if [[ "${SOURCE_DB}" == "${VALIDATION_DB_PREFIX}"* ]]; then
    log_error "源数据库不能使用验证数据库前缀"
    exit 1
  fi
  if [[ "${TARGET_DB}" != "${VALIDATION_DB_PREFIX}"* || "${TARGET_DB}" == "${SOURCE_DB}" ]]; then
    log_error "恢复目标必须是独立的 ${VALIDATION_DB_PREFIX} 前缀数据库"
    exit 1
  fi
  validate_sample_query

  local backup_root work_root backup_run_root
  backup_root="$(resolve_safe_root "BACKUP_RESTORE_BACKUP_DIR" "${BACKUP_ROOT_INPUT}")"
  work_root="$(resolve_safe_root "BACKUP_RESTORE_WORK_ROOT" "${WORK_ROOT_INPUT}")"
  WORK_ROOT_INPUT="${work_root}"
  backup_run_root="${backup_root}/${TARGET_DB}"
  if [[ -e "${backup_run_root}" ]]; then
    log_error "本次验证备份目录已存在，拒绝覆盖: ${backup_run_root}"
    exit 1
  fi
  mkdir -m 700 -- "${backup_run_root}"
  work_dir="${work_root}/run_${TARGET_DB}"
  if [[ -e "${work_dir}" ]]; then
    log_error "本次验证工作目录已存在，拒绝覆盖: ${work_dir}"
    exit 1
  fi
  mkdir -m 700 -- "${work_dir}"
  trap cleanup EXIT

  if database_exists "${TARGET_DB}"; then
    log_error "隔离目标数据库已存在，拒绝覆盖: ${TARGET_DB}"
    exit 1
  fi

  log_info "计算备份前源库结构与样例指纹"
  local source_structure_before source_sample_before
  source_structure_before="$(structure_fingerprint "${BACKUP_USER}" "${BACKUP_PASSWORD}" "${SOURCE_DB}")"
  source_sample_before="$(sample_fingerprint "${BACKUP_USER}" "${BACKUP_PASSWORD}" "${SOURCE_DB}")"

  log_info "创建本次真实备份"
  local backup_output backup_dir backup_file checksum_file backup_sha backup_size
  backup_output="$(MYSQL_HOST="${MYSQL_HOST}" \
    MYSQL_PORT="${MYSQL_PORT}" \
    MYSQL_USER="${BACKUP_USER}" \
    MYSQL_PASSWORD="${BACKUP_PASSWORD}" \
    MYSQL_DATABASES="${SOURCE_DB}" \
    BACKUP_DIR="${backup_run_root}" \
    RETENTION_DAYS=36500 \
    "${BACKUP_SCRIPT}")"
  printf '%s\n' "${backup_output}"
  backup_dir="$(printf '%s\n' "${backup_output}" | sed -n 's/^\[INFO\] 备份完成: //p' | tail -n 1)"
  require_value "备份输出目录" "${backup_dir}"
  backup_dir="$(cd "${backup_dir}" && pwd -P)"
  if [[ "${backup_dir}" != "${backup_run_root}"/* ]]; then
    log_error "备份输出不在本次绝对目标目录内: ${backup_dir}"
    exit 1
  fi
  backup_file="${backup_dir}/${SOURCE_DB}.sql.gz"
  checksum_file="${backup_file}.sha256"
  if [[ ! -s "${backup_file}" || ! -s "${checksum_file}" ]]; then
    log_error "缺少本次真实备份或 SHA-256 证据"
    exit 1
  fi
  if ! (cd "${backup_dir}" && sha256sum -c "$(basename "${checksum_file}")" >/dev/null); then
    log_error "本次真实备份 SHA-256 校验失败"
    exit 1
  fi
  backup_sha="$(sha256sum "${backup_file}" | awk '{print $1}')"
  backup_size="$(wc -c < "${backup_file}" | xargs)"
  if ! [[ "${backup_size}" =~ ^[0-9]+$ ]] || [[ "${backup_size}" -le 0 ]]; then
    log_error "本次真实备份文件大小无效"
    exit 1
  fi

  log_info "确认备份窗口内源库指纹未变化"
  local source_structure_after source_sample_after
  source_structure_after="$(structure_fingerprint "${BACKUP_USER}" "${BACKUP_PASSWORD}" "${SOURCE_DB}")"
  source_sample_after="$(sample_fingerprint "${BACKUP_USER}" "${BACKUP_PASSWORD}" "${SOURCE_DB}")"
  if [[ "${source_structure_before}" != "${source_structure_after}" \
      || "${source_sample_before}" != "${source_sample_after}" ]]; then
    log_error "备份窗口内源库结构或样例数据发生变化，无法形成可复核证据"
    exit 1
  fi

  log_info "恢复到显式隔离目标数据库: ${TARGET_DB}"
  target_may_exist=true
  MYSQL_HOST="${MYSQL_HOST}" \
  MYSQL_PORT="${MYSQL_PORT}" \
  MYSQL_USER="${RESTORE_USER}" \
  MYSQL_PASSWORD="${RESTORE_PASSWORD}" \
  RESTORE_CONFIRM_DATABASE="${TARGET_DB}" \
  RESTORE_REQUIRE_CHECKSUM=true \
    "${RESTORE_SCRIPT}" "${backup_file}" "${TARGET_DB}"

  local target_structure target_sample
  target_structure="$(structure_fingerprint "${RESTORE_USER}" "${RESTORE_PASSWORD}" "${TARGET_DB}")"
  target_sample="$(sample_fingerprint "${RESTORE_USER}" "${RESTORE_PASSWORD}" "${TARGET_DB}")"
  if [[ "${source_structure_after}" != "${target_structure}" ]]; then
    log_error "恢复后的结构指纹与源库不一致"
    exit 1
  fi
  if [[ "${source_sample_after}" != "${target_sample}" ]]; then
    log_error "恢复后的样例数据指纹与源库不一致"
    exit 1
  fi

  # PASS 报告只能在目标库和临时工作目录均安全清理后生成。
  drop_validation_database
  cleanup_work_dir

  mkdir -p -- "$(dirname "${REPORT_FILE}")"
  cat > "${REPORT_FILE}" <<EOF
# LingSQL 备份恢复抽查报告

- 执行时间: $(date -Iseconds)
- 源数据库: ${SOURCE_DB}
- 隔离恢复目标: ${TARGET_DB}
- 备份文件: $(basename "${backup_file}")
- 备份字节数: ${backup_size}
- 备份 SHA-256: ${backup_sha}
- 结构指纹: ${target_structure}
- 样例数据指纹: ${target_sample}
- 源库窗口稳定: true
- 目标库预先存在: false
- SHA-256 校验: PASS
- 隔离恢复: PASS
- 结构一致: PASS
- 样例数据一致: PASS
- 验证目标安全清理: PASS
- 验收结论: PASS

## 验证边界

本报告仅记录不可逆摘要，不包含数据库密码、SQL 结果或客户明细。真实备份保留在受保护的 self-hosted UAT 备份目录；隔离目标数据库在脚本退出时按固定验证前缀清理。
EOF
  log_info "备份恢复抽查通过，报告已写入: ${REPORT_FILE}"
}

main "$@"
