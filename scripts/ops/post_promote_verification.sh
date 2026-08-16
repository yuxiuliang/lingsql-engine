#!/usr/bin/env bash
# 切流后从公网复核发布身份，并生成与候选收据绑定的脱敏部署结果收据。

set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_BASE="${LINGSQL_DEPLOY_BASE:-/opt/lingsql}"
REPORT_FILE="${POST_PROMOTE_REPORT_FILE:-./reports/post_promote_runtime_fingerprint.md}"
RESULT_FILE="${DEPLOYMENT_RESULT_FILE:-./reports/deployment_result_receipt.json}"
READINESS_REPORT_FILE="${POST_PROMOTE_READINESS_REPORT_FILE:-./reports/post_promote_tenant_readiness.json}"

log_error() { echo "[ERROR] $*" >&2; }
require_value() { [[ -n "$2" ]] || { log_error "$1 不能为空"; exit 1; }; }

persist_result() {
  local archive_dir archive_file result_sha archive_sha temp_file
  archive_dir="${DEPLOY_BASE}/deployment-results"
  archive_file="${archive_dir}/${EXPECTED_RELEASE_ID}.json"
  mkdir -p "${archive_dir}"
  chmod 700 "${archive_dir}"
  result_sha="$(sha256sum "${RESULT_FILE}" | awk '{print $1}')"
  if [[ -e "${archive_file}" ]]; then
    archive_sha="$(sha256sum "${archive_file}" | awk '{print $1}')"
    [[ "${archive_sha}" == "${result_sha}" ]] \
      || { log_error "部署结果归档已存在且摘要不同，拒绝覆盖: ${archive_file}"; exit 2; }
    return
  fi
  temp_file="$(mktemp "${archive_dir}/.deployment-result.XXXXXX")"
  cp -- "${RESULT_FILE}" "${temp_file}"
  chmod 400 "${temp_file}"
  ln "${temp_file}" "${archive_file}" || {
    rm -f -- "${temp_file}"
    log_error "无法以不可覆盖方式归档部署结果"
    exit 2
  }
  rm -f -- "${temp_file}"
}

main() {
  local actual_candidate_seal candidate_canonical candidate_receipt candidate_receipt_sha
  local completed=false expected_candidate_seal private_report report_sha readiness_sha payload seal result_dir
  command -v jq >/dev/null 2>&1 || { log_error "缺少命令: jq"; exit 1; }
  command -v node >/dev/null 2>&1 || { log_error "缺少命令: node"; exit 1; }
  require_value "BASE_URL" "${BASE_URL:-}"
  require_value "PILOT_CONTROL_TOKEN" "${PILOT_CONTROL_TOKEN:-}"
  require_value "EXPECTED_RELEASE_COMMIT" "${EXPECTED_RELEASE_COMMIT:-}"
  require_value "EXPECTED_DATA_SNAPSHOT_ID" "${EXPECTED_DATA_SNAPSHOT_ID:-}"
  require_value "EXPECTED_MODEL_ROUTE_FINGERPRINT" "${EXPECTED_MODEL_ROUTE_FINGERPRINT:-}"
  require_value "EXPECTED_SLOT" "${EXPECTED_SLOT:-}"
  require_value "EXPECTED_RELEASE_ID" "${EXPECTED_RELEASE_ID:-}"
  require_value "CANDIDATE_RECEIPT_HMAC_KEY" "${CANDIDATE_RECEIPT_HMAC_KEY:-}"
  [[ "${CANDIDATE_RECEIPT_HMAC_KEY}" =~ ^[0-9a-f]{64}$ ]] \
    || { log_error "CANDIDATE_RECEIPT_HMAC_KEY 格式非法"; exit 1; }

  ACCESS_TOKEN="${PILOT_CONTROL_TOKEN}" "${SCRIPT_DIR}/release_smoke_test.sh"
  private_report="$(mktemp)"
  cleanup_post_promote_files() {
    rm -f -- "${private_report:-}"
    if [[ "${completed}" != "true" ]]; then
      rm -f -- "${REPORT_FILE}" "${RESULT_FILE}" "${READINESS_REPORT_FILE}"
    fi
  }
  trap cleanup_post_promote_files EXIT
  RUNTIME_FINGERPRINT_REPORT_FILE="${private_report}" "${SCRIPT_DIR}/runtime_fingerprint_guard.sh"
  READINESS_REPORT_FILE="${READINESS_REPORT_FILE}" \
    bash "${SCRIPT_DIR}/tenant_first_question_readiness_guard.sh"

  candidate_receipt="${DEPLOY_BASE}/receipts/${EXPECTED_RELEASE_ID}.json"
  [[ -s "${candidate_receipt}" ]] || { log_error "候选验收收据归档不存在"; exit 2; }
  jq -e --arg commit "${EXPECTED_RELEASE_COMMIT}" --arg slot "${EXPECTED_SLOT}" --arg releaseId "${EXPECTED_RELEASE_ID}" \
    '.commit == $commit and .slot == $slot and .releaseId == $releaseId and (.seal | type == "string")' \
    "${candidate_receipt}" >/dev/null || { log_error "候选验收收据身份不匹配"; exit 2; }
  candidate_canonical="$(jq -cS 'del(.seal)' "${candidate_receipt}")"
  actual_candidate_seal="$(jq -r '.seal' "${candidate_receipt}")"
  expected_candidate_seal="$(printf '%s' "${candidate_canonical}" | node -e 'const crypto=require("node:crypto");const chunks=[];process.stdin.on("data",c=>chunks.push(c));process.stdin.on("end",()=>process.stdout.write(crypto.createHmac("sha256",Buffer.from(process.env.CANDIDATE_RECEIPT_HMAC_KEY,"hex")).update(Buffer.concat(chunks)).digest("hex")));')"
  [[ "${actual_candidate_seal}" == "${expected_candidate_seal}" ]] \
    || { log_error "候选验收收据封印不匹配"; exit 2; }

  result_dir="$(dirname "${REPORT_FILE}")"
  mkdir -p "${result_dir}"
  cat > "${REPORT_FILE}" <<EOF
# LingSQL 切流后公网验证

- 候选提交: ${EXPECTED_RELEASE_COMMIT}
- 运行槽位: ${EXPECTED_SLOT}
- 发布标识: ${EXPECTED_RELEASE_ID}
- 数据快照一致性: PASS
- 模型路由一致性: PASS
- 公网运行指纹: PASS
- 登录态冒烟: PASS
- 试点租户首问五项事实: PASS
- 候选验收收据封印: PASS
- 结论: PASS
EOF

  candidate_receipt_sha="$(sha256sum "${candidate_receipt}" | awk '{print $1}')"
  report_sha="$(sha256sum "${REPORT_FILE}" | awk '{print $1}')"
  readiness_sha="$(sha256sum "${READINESS_REPORT_FILE}" | awk '{print $1}')"
  payload="$(jq -cnS \
    --arg verifiedAt "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
    --arg commit "${EXPECTED_RELEASE_COMMIT}" --arg slot "${EXPECTED_SLOT}" \
    --arg releaseId "${EXPECTED_RELEASE_ID}" --arg candidateReceiptSha256 "${candidate_receipt_sha}" \
    --arg postPromoteReportSha256 "${report_sha}" --arg readinessReportSha256 "${readiness_sha}" \
    '{schemaVersion:1,verifiedAt:$verifiedAt,commit:$commit,slot:$slot,releaseId:$releaseId,publicEndpointVerified:true,candidateReceiptSha256:$candidateReceiptSha256,postPromoteReportSha256:$postPromoteReportSha256,readinessReportSha256:$readinessReportSha256,conclusion:"PASS"}')"
  seal="$(printf '%s' "${payload}" | node -e 'const crypto=require("node:crypto");const chunks=[];process.stdin.on("data",c=>chunks.push(c));process.stdin.on("end",()=>process.stdout.write(crypto.createHmac("sha256",Buffer.from(process.env.CANDIDATE_RECEIPT_HMAC_KEY,"hex")).update(Buffer.concat(chunks)).digest("hex")));')"
  result_dir="$(dirname "${RESULT_FILE}")"
  mkdir -p "${result_dir}"
  jq -cS --arg seal "${seal}" '. + {seal:$seal}' <<< "${payload}" > "${RESULT_FILE}"
  chmod 400 "${RESULT_FILE}"
  persist_result
  completed=true
  cleanup_post_promote_files
  trap - EXIT
  echo "[INFO] 切流后公网运行指纹与部署结果收据已验证并归档"
}

main "$@"
