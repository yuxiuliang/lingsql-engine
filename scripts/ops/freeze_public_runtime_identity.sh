#!/usr/bin/env bash
# 切流前从公网冻结当前活跃版本身份，供失败回滚后强校验。

set -euo pipefail
umask 077

BASE_URL="${BASE_URL:-}"
PILOT_CONTROL_TOKEN="${PILOT_CONTROL_TOKEN:-}"
PILOT_CONTROL_TENANT_ID="${PILOT_CONTROL_TENANT_ID:-sys_tenant}"
MODEL_ROUTE_TENANT_ID="${MODEL_ROUTE_TENANT_ID:-}"
PUBLIC_IDENTITY_FILE="${PUBLIC_IDENTITY_FILE:-./reports/pre_promote_public_identity.json}"

log_error() { echo "[ERROR] $*" >&2; }

main() {
  local response output_dir
  command -v curl >/dev/null 2>&1 || { log_error "缺少命令: curl"; exit 1; }
  command -v jq >/dev/null 2>&1 || { log_error "缺少命令: jq"; exit 1; }
  [[ -n "${BASE_URL}" && -n "${PILOT_CONTROL_TOKEN}" && -n "${MODEL_ROUTE_TENANT_ID}" ]] \
    || { log_error "公网地址、控制 Token 和试点租户不能为空"; exit 1; }
  response="$(curl -fsS --get --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer ${PILOT_CONTROL_TOKEN}" \
    -H "X-Tenant-Id: ${PILOT_CONTROL_TENANT_ID}" \
    --data-urlencode "modelTenantId=${MODEL_ROUTE_TENANT_ID}" \
    "${BASE_URL%/}/api/v1/admin/runtime/fingerprint")"
  jq -e '
    .code == 200 and
    (.data.commit | test("^[0-9a-f]{40}$")) and
    (.data.slot == "blue" or .data.slot == "green") and
    (.data.releaseId | test("^[A-Za-z0-9._-]{1,128}$")) and
    (.data.dataSnapshotId | type == "string" and length > 0 and . != "UNSET") and
    (.data.modelRouteFingerprint | test("^[0-9a-f]{64}$"))
  ' <<< "${response}" >/dev/null || { log_error "切流前公网运行身份不完整，拒绝提升候选"; exit 2; }
  output_dir="$(dirname "${PUBLIC_IDENTITY_FILE}")"
  mkdir -p "${output_dir}"
  jq -cS '.data | {schemaVersion:1,commit,slot,releaseId,dataSnapshotId,modelRouteFingerprint}' \
    <<< "${response}" > "${PUBLIC_IDENTITY_FILE}"
  chmod 400 "${PUBLIC_IDENTITY_FILE}"
  echo "[INFO] 已冻结切流前公网运行身份"
}

main "$@"
