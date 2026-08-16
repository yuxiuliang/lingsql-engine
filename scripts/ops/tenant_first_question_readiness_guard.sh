#!/usr/bin/env bash
# 从指定入口校验首问五项事实，并生成不含凭据的摘要。

set -euo pipefail
umask 077

BASE_URL="${BASE_URL:-}"
PILOT_CONTROL_TOKEN="${PILOT_CONTROL_TOKEN:-}"
PILOT_CONTROL_TENANT_ID="${PILOT_CONTROL_TENANT_ID:-sys_tenant}"
MODEL_ROUTE_TENANT_ID="${MODEL_ROUTE_TENANT_ID:-}"
READINESS_REPORT_FILE="${READINESS_REPORT_FILE:-./reports/tenant_first_question_readiness.json}"

log_error() { echo "[ERROR] $*" >&2; }
require_value() { [[ -n "$2" ]] || { log_error "$1 不能为空"; exit 1; }; }

main() {
  local tenant_encoded response report_dir checked_at
  command -v curl >/dev/null 2>&1 || { log_error "缺少命令: curl"; exit 1; }
  command -v jq >/dev/null 2>&1 || { log_error "缺少命令: jq"; exit 1; }
  require_value "BASE_URL" "${BASE_URL}"
  require_value "PILOT_CONTROL_TOKEN" "${PILOT_CONTROL_TOKEN}"
  require_value "MODEL_ROUTE_TENANT_ID" "${MODEL_ROUTE_TENANT_ID}"
  tenant_encoded="$(jq -rn --arg value "${MODEL_ROUTE_TENANT_ID}" '$value|@uri')"
  response="$(curl -fsS --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer ${PILOT_CONTROL_TOKEN}" \
    -H "X-Tenant-Id: ${PILOT_CONTROL_TENANT_ID}" \
    "${BASE_URL%/}/api/v1/admin/tenant-readiness/${tenant_encoded}/first-question")"
  jq -e '
    .code == 200 and
    .data.ready == true and
    .data.status == "READY_TO_ASK" and
    (.data.failureReasons | length) == 0 and
    ([.data.checks[].code] | sort) == (["BILLING_AVAILABLE","DATASOURCE_SCHEMA_READY","MODEL_ROUTE_READY","TENANT_ACTIVE","TENANT_ADMIN_LOGIN"] | sort) and
    (.data.checks | length) == 5 and
    all(.data.checks[]; .passed == true)
  ' <<< "${response}" >/dev/null || {
    log_error "试点租户首问五项事实未全部通过"
    jq -r '.data.failureReasons[]? // empty' <<< "${response}" >&2
    exit 2
  }
  report_dir="$(dirname "${READINESS_REPORT_FILE}")"
  mkdir -p "${report_dir}"
  checked_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  jq -cS --arg checkedAt "${checked_at}" '
    .data | {
      schemaVersion: 1,
      checkedAt: $checkedAt,
      status,
      ready,
      checks: [.checks[] | {code, passed}]
    }
  ' <<< "${response}" > "${READINESS_REPORT_FILE}"
  chmod 400 "${READINESS_REPORT_FILE}"
  echo "[INFO] 试点租户首问五项事实校验通过"
}

main "$@"
