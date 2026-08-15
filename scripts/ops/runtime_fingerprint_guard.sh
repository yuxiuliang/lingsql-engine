#!/usr/bin/env bash
# 校验门禁请求实际命中的运行实例是否为指定候选制品。

set -euo pipefail

BASE_URL="${BASE_URL:-}"
PILOT_CONTROL_TOKEN="${PILOT_CONTROL_TOKEN:-}"
PILOT_CONTROL_TENANT_ID="${PILOT_CONTROL_TENANT_ID:-sys_tenant}"
EXPECTED_RELEASE_COMMIT="${EXPECTED_RELEASE_COMMIT:-}"
EXPECTED_DATA_SNAPSHOT_ID="${EXPECTED_DATA_SNAPSHOT_ID:-}"
EXPECTED_MODEL_ROUTE_FINGERPRINT="${EXPECTED_MODEL_ROUTE_FINGERPRINT:-}"
RUNTIME_FINGERPRINT_REPORT_FILE="${RUNTIME_FINGERPRINT_REPORT_FILE:-./reports/runtime_fingerprint.md}"

log_error() { echo "[ERROR] $*" >&2; }
require_value() { [[ -n "$2" ]] || { log_error "$1 不能为空"; exit 1; }; }

main() {
  local response actual_commit actual_snapshot actual_model_route schema_version model_count report_dir
  command -v curl >/dev/null 2>&1 || { log_error "缺少命令: curl"; exit 1; }
  command -v jq >/dev/null 2>&1 || { log_error "缺少命令: jq"; exit 1; }
  require_value "BASE_URL" "${BASE_URL}"
  require_value "PILOT_CONTROL_TOKEN" "${PILOT_CONTROL_TOKEN}"
  require_value "EXPECTED_RELEASE_COMMIT" "${EXPECTED_RELEASE_COMMIT}"

  response="$(curl -fsS --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer ${PILOT_CONTROL_TOKEN}" \
    -H "X-Tenant-Id: ${PILOT_CONTROL_TENANT_ID}" \
    "${BASE_URL%/}/api/v1/admin/runtime/fingerprint")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || { log_error "读取运行指纹失败"; exit 2; }
  actual_commit="$(jq -r '.data.commit // empty' <<< "${response}")"
  actual_snapshot="$(jq -r '.data.dataSnapshotId // empty' <<< "${response}")"
  actual_model_route="$(jq -r '.data.modelRouteFingerprint // empty' <<< "${response}")"
  schema_version="$(jq -r '.data.schemaVersion // empty' <<< "${response}")"
  model_count="$(jq -r '(.data.activeModels // []) | length' <<< "${response}")"

  [[ "${actual_commit}" == "${EXPECTED_RELEASE_COMMIT}" ]] || {
    log_error "候选提交不匹配: expected=${EXPECTED_RELEASE_COMMIT}, actual=${actual_commit:-<empty>}"
    exit 2
  }
  [[ -n "${schema_version}" && "${schema_version}" != "UNMANAGED" ]] || { log_error "Flyway Schema 未纳管"; exit 2; }
  [[ "${model_count}" -gt 0 ]] || { log_error "候选实例没有激活模型"; exit 2; }
  if [[ -n "${EXPECTED_DATA_SNAPSHOT_ID}" && "${actual_snapshot}" != "${EXPECTED_DATA_SNAPSHOT_ID}" ]]; then
    log_error "数据快照不匹配: expected=${EXPECTED_DATA_SNAPSHOT_ID}, actual=${actual_snapshot:-<empty>}"
    exit 2
  fi
  if [[ -n "${EXPECTED_MODEL_ROUTE_FINGERPRINT}" && "${actual_model_route}" != "${EXPECTED_MODEL_ROUTE_FINGERPRINT}" ]]; then
    log_error "模型路由指纹不匹配"
    exit 2
  fi

  report_dir="$(dirname "${RUNTIME_FINGERPRINT_REPORT_FILE}")"
  mkdir -p "${report_dir}"
  cat > "${RUNTIME_FINGERPRINT_REPORT_FILE}" <<EOF
# LingSQL 运行候选指纹

- 候选提交: ${actual_commit}
- 应用版本: $(jq -r '.data.applicationVersion // ""' <<< "${response}")
- 构建时间: $(jq -r '.data.buildTime // ""' <<< "${response}")
- Flyway Schema: ${schema_version}
- 数据快照: ${actual_snapshot}
- 模型路由 SHA-256: ${actual_model_route}
- 激活模型数量: ${model_count}
- 结论: PASS
EOF
  echo "[INFO] 运行候选指纹校验通过: ${actual_commit}"
}

main "$@"
