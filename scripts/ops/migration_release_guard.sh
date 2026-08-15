#!/usr/bin/env bash
# 在候选启动前执行受控迁移，校验 Hook 收据，并确认旧版本仍兼容迁移后结构。

set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MYSQL_HOST="${MIGRATION_MYSQL_HOST:-}"
MYSQL_PORT="${MIGRATION_MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MIGRATION_MYSQL_DATABASE:-}"
MYSQL_USER="${MIGRATION_MYSQL_USER:-}"
MYSQL_PASSWORD="${MIGRATION_MYSQL_PASSWORD:-}"
MIGRATION_HOOK="${LINGSQL_MIGRATION_HOOK:-}"
COMPATIBILITY_REPORT="${MIGRATION_COMPATIBILITY_REPORT_FILE:-}"
BACKUP_REPORT="${MIGRATION_BACKUP_REPORT_FILE:-}"
RECEIPT_FILE="${MIGRATION_RECEIPT_FILE:-}"
OLD_VERSION_REPORT="${MIGRATION_OLD_VERSION_SMOKE_REPORT_FILE:-}"
BASE_URL="${BASE_URL:-}"
CONTROL_TOKEN="${PILOT_CONTROL_TOKEN:-}"
CONTROL_TENANT="${PILOT_CONTROL_TENANT_ID:-sys_tenant}"
EXPECTED_COMMIT="${EXPECTED_RELEASE_COMMIT:-}"
RELEASE_TAG="${RELEASE_TAG:-}"

log_error() { echo "[ERROR] $*" >&2; }
require_value() { [[ -n "$2" ]] || { log_error "$1 不能为空"; exit 1; }; }
require_file() { [[ -s "$2" && -f "$2" && ! -L "$2" ]] || { log_error "$1 不存在、为空或不是普通文件: $2"; exit 1; }; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || { log_error "缺少命令: $1"; exit 1; }; }
sha256_file() { sha256sum "$1" | awk '{print $1}'; }

read_current_version() {
  MYSQL_PWD="${MYSQL_PASSWORD}" mysql -N -B \
    -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" "${MYSQL_DATABASE}" \
    -e "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1" \
    | tr -d '[:space:]'
}

runtime_fingerprint() {
  curl -fsS --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer ${CONTROL_TOKEN}" \
    -H "X-Tenant-Id: ${CONTROL_TENANT}" \
    "${BASE_URL%/}/api/v1/admin/runtime/fingerprint"
}

publish_old_version_report() {
  local old_commit="$1" post_version="$2" smoke_code="$3" runtime_code="$4" post_runtime="$5"
  local report_tmp
  mkdir -p -- "$(dirname "${OLD_VERSION_REPORT}")"
  report_tmp="$(mktemp "$(dirname "${OLD_VERSION_REPORT}")/.old-version-smoke.XXXXXX")"
  jq -cnS \
    --arg createdAt "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" --arg activeCommit "${old_commit}" \
    --arg schemaVersion "${post_version}" --argjson smokeExitCode "${smoke_code}" \
    --argjson runtimeExitCode "${runtime_code}" --argjson runtime "${post_runtime}" \
    '{schemaVersion:1,createdAt:$createdAt,activeCommit:$activeCommit,migratedSchemaVersion:$schemaVersion,releaseSmokeExitCode:$smokeExitCode,runtimeVerificationExitCode:$runtimeExitCode,runtimeFingerprint:$runtime,compatibilityConclusion:(if $smokeExitCode == 0 and $runtimeExitCode == 0 then "PASS" else "FAIL" end)}' \
    > "${report_tmp}"
  chmod 400 "${report_tmp}"
  [[ ! -e "${OLD_VERSION_REPORT}" && ! -L "${OLD_VERSION_REPORT}" ]] || {
    rm -f -- "${report_tmp}"
    log_error "旧版本兼容性报告已存在，拒绝覆盖: ${OLD_VERSION_REPORT}"
    return 1
  }
  ln "${report_tmp}" "${OLD_VERSION_REPORT}" || { rm -f -- "${report_tmp}"; log_error "无法发布旧版本兼容性报告"; return 1; }
  rm -f -- "${report_tmp}"
}

main() {
  local pre_version expected_post checksum_summary compatibility_sha backup_sha backup_source backup_conclusion
  local pre_runtime old_commit pre_runtime_schema hook_draft hook_code post_version receipt_tmp receipt_sha
  local smoke_code post_runtime runtime_code post_commit post_schema

  for command_name in mysql curl jq sha256sum sed node mktemp ln chmod; do require_cmd "${command_name}"; done
  require_value "MIGRATION_MYSQL_HOST" "${MYSQL_HOST}"
  require_value "MIGRATION_MYSQL_DATABASE" "${MYSQL_DATABASE}"
  require_value "MIGRATION_MYSQL_USER" "${MYSQL_USER}"
  require_value "MIGRATION_MYSQL_PASSWORD" "${MYSQL_PASSWORD}"
  require_value "LINGSQL_MIGRATION_HOOK" "${MIGRATION_HOOK}"
  require_value "MIGRATION_RECEIPT_FILE" "${RECEIPT_FILE}"
  require_value "MIGRATION_OLD_VERSION_SMOKE_REPORT_FILE" "${OLD_VERSION_REPORT}"
  require_value "BASE_URL" "${BASE_URL}"
  require_value "PILOT_CONTROL_TOKEN" "${CONTROL_TOKEN}"
  require_value "EXPECTED_RELEASE_COMMIT" "${EXPECTED_COMMIT}"
  require_value "RELEASE_TAG" "${RELEASE_TAG}"
  [[ -x "${MIGRATION_HOOK}" ]] || { log_error "迁移 Hook 不存在或不可执行: ${MIGRATION_HOOK}"; exit 1; }
  [[ "${MYSQL_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]] || { log_error "MIGRATION_MYSQL_DATABASE 不是安全标识符"; exit 1; }
  [[ "${EXPECTED_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || { log_error "EXPECTED_RELEASE_COMMIT 必须为40位小写 Git SHA"; exit 1; }
  [[ "${RELEASE_TAG}" =~ ^[A-Za-z0-9._-]+$ ]] || { log_error "RELEASE_TAG 非法"; exit 1; }
  require_file "迁移兼容性报告" "${COMPATIBILITY_REPORT}"
  require_file "迁移前备份恢复报告" "${BACKUP_REPORT}"
  [[ ! -e "${RECEIPT_FILE}" && ! -L "${RECEIPT_FILE}" ]] || { log_error "迁移收据已存在，拒绝覆盖: ${RECEIPT_FILE}"; exit 2; }
  [[ ! -e "${OLD_VERSION_REPORT}" && ! -L "${OLD_VERSION_REPORT}" ]] || { log_error "旧版本兼容性报告已存在，拒绝覆盖"; exit 2; }

  jq -e '.schemaVersion == 1 and .compatibilityConclusion == "PASS" and (.currentVersion | type == "string") and (.expectedPostVersion | type == "string") and (.pendingMigrationChecksumSha256 | test("^[0-9a-f]{64}$"))' \
    "${COMPATIBILITY_REPORT}" >/dev/null || { log_error "迁移兼容性报告格式或结论非法"; exit 2; }
  pre_version="$(jq -r '.currentVersion' "${COMPATIBILITY_REPORT}")"
  expected_post="$(jq -r '.expectedPostVersion' "${COMPATIBILITY_REPORT}")"
  checksum_summary="$(jq -r '.pendingMigrationChecksumSha256' "${COMPATIBILITY_REPORT}")"
  compatibility_sha="$(sha256_file "${COMPATIBILITY_REPORT}")"
  [[ "$(read_current_version)" == "${pre_version}" ]] || { log_error "兼容性扫描后数据库版本已变化"; exit 2; }

  backup_source="$(sed -n 's/^- 源数据库: //p' "${BACKUP_REPORT}" | tail -n 1)"
  backup_sha="$(sed -n 's/^- 备份 SHA-256: //p' "${BACKUP_REPORT}" | tail -n 1)"
  backup_conclusion="$(sed -n 's/^- 验收结论: //p' "${BACKUP_REPORT}" | tail -n 1)"
  [[ "${backup_source}" == "${MYSQL_DATABASE}" ]] || { log_error "迁移前备份不是目标控制库"; exit 2; }
  [[ "${backup_sha}" =~ ^[0-9a-f]{64}$ && "${backup_conclusion}" == "PASS" ]] || { log_error "迁移前真实备份恢复证据不完整"; exit 2; }

  pre_runtime="$(runtime_fingerprint)"
  [[ "$(jq -r '.code // 0' <<< "${pre_runtime}")" == "200" ]] || { log_error "迁移前无法读取在线旧版本指纹"; exit 2; }
  old_commit="$(jq -r '.data.commit // empty' <<< "${pre_runtime}")"
  pre_runtime_schema="$(jq -r '.data.schemaVersion // empty' <<< "${pre_runtime}")"
  [[ "${old_commit}" =~ ^[0-9a-f]{40}$ ]] || { log_error "迁移前在线版本没有可审计 Git SHA"; exit 2; }
  [[ "${pre_runtime_schema}" == "${pre_version}" ]] || { log_error "在线旧版本 Schema 与控制库不一致"; exit 2; }

  mkdir -p -- "$(dirname "${RECEIPT_FILE}")"
  hook_draft="${RECEIPT_FILE}.hook-draft.$$"
  [[ ! -e "${hook_draft}" && ! -L "${hook_draft}" ]] || { log_error "迁移 Hook 草稿路径冲突"; exit 2; }
  trap 'rm -f -- "${hook_draft:-}" "${receipt_tmp:-}"' EXIT
  set +e
  MIGRATION_HOOK_RECEIPT_FILE="${hook_draft}" \
  MIGRATION_PRE_VERSION="${pre_version}" MIGRATION_EXPECTED_POST_VERSION="${expected_post}" \
  MIGRATION_PENDING_CHECKSUM_SHA256="${checksum_summary}" MIGRATION_COMPATIBILITY_REPORT_SHA256="${compatibility_sha}" \
  MIGRATION_BACKUP_SHA256="${backup_sha}" MIGRATION_BACKUP_RESTORE_CONCLUSION="${backup_conclusion}" \
    "${MIGRATION_HOOK}" "${EXPECTED_COMMIT}" "${RELEASE_TAG}" "${hook_draft}"
  hook_code=$?
  set -e

  post_version="$(read_current_version)"
  set +e
  BASE_URL="${BASE_URL}" ACCESS_TOKEN="${CONTROL_TOKEN}" bash "${SCRIPT_DIR}/release_smoke_test.sh"
  smoke_code=$?
  post_runtime="$(runtime_fingerprint 2>/dev/null)"
  if [[ "$(jq -r '.code // 0' <<< "${post_runtime}" 2>/dev/null)" == "200" ]]; then
    post_commit="$(jq -r '.data.commit // empty' <<< "${post_runtime}")"
    post_schema="$(jq -r '.data.schemaVersion // empty' <<< "${post_runtime}")"
    if [[ "${post_commit}" == "${old_commit}" && "${post_schema}" == "${post_version}" ]]; then runtime_code=0; else runtime_code=2; fi
  else
    post_runtime='{}'; runtime_code=2
  fi
  set -e
  publish_old_version_report "${old_commit}" "${post_version}" "${smoke_code}" "${runtime_code}" "${post_runtime}"

  [[ "${hook_code}" -eq 0 ]] || { log_error "迁移 Hook 执行失败，退出码=${hook_code}"; exit 2; }
  [[ "${post_version}" == "${expected_post}" ]] || { log_error "迁移后 Flyway 版本不符合预期: expected=${expected_post}, actual=${post_version}"; exit 2; }
  [[ "${smoke_code}" -eq 0 && "${runtime_code}" -eq 0 ]] || { log_error "迁移后在线旧版本兼容性验证失败"; exit 2; }
  require_file "迁移 Hook 收据草稿" "${hook_draft}"
  jq -e \
    --arg commit "${EXPECTED_COMMIT}" --arg releaseTag "${RELEASE_TAG}" \
    --arg pre "${pre_version}" --arg post "${post_version}" --arg checksum "${checksum_summary}" \
    --arg compatibility "${compatibility_sha}" --arg backup "${backup_sha}" \
    '.schemaVersion == 1 and (.createdAt | type == "string" and test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T")) and
     .releaseCommit == $commit and .releaseTag == $releaseTag and .preMigrationVersion == $pre and
     .postMigrationVersion == $post and .pendingMigrationChecksumSha256 == $checksum and
     .compatibilityReportSha256 == $compatibility and .backupSha256 == $backup and
     .backupRestoreConclusion == "PASS" and .hookConclusion == "PASS"' \
    "${hook_draft}" >/dev/null || { log_error "迁移 Hook 收据缺字段、与独立证据不一致或结论非 PASS"; exit 2; }

  receipt_tmp="$(mktemp "$(dirname "${RECEIPT_FILE}")/.migration-receipt.XXXXXX")"
  jq -cS . "${hook_draft}" > "${receipt_tmp}"
  chmod 400 "${receipt_tmp}"
  ln "${receipt_tmp}" "${RECEIPT_FILE}" || { log_error "无法以不可覆盖方式发布迁移收据"; exit 2; }
  receipt_sha="$(sha256_file "${RECEIPT_FILE}")"
  rm -f -- "${hook_draft}" "${receipt_tmp}"
  trap - EXIT
  echo "[INFO] 迁移与旧版本兼容性门禁通过: pre=${pre_version}, post=${post_version}, receiptSha256=${receipt_sha}"
}

main "$@"
