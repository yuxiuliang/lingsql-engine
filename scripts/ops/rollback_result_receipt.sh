#!/usr/bin/env bash
# 为已经通过公网身份与首问复核的回滚生成不可覆盖 HMAC 收据。

set -euo pipefail
umask 077

DEPLOY_BASE="${LINGSQL_DEPLOY_BASE:-/opt/lingsql}"
ROLLBACK_RECEIPT_FILE="${ROLLBACK_RECEIPT_FILE:-./reports/rollback_result_receipt.json}"

require_value() { [[ -n "$2" ]] || { echo "[ERROR] $1 不能为空" >&2; exit 1; }; }

main() {
  local runtime_sha readiness_sha payload seal archive_dir archive_file temp_file receipt_sha archive_sha
  command -v jq >/dev/null 2>&1 || { echo "[ERROR] 缺少命令: jq" >&2; exit 1; }
  command -v node >/dev/null 2>&1 || { echo "[ERROR] 缺少命令: node" >&2; exit 1; }
  for name in EXPECTED_ROLLBACK_COMMIT EXPECTED_ROLLBACK_SLOT EXPECTED_ROLLBACK_RELEASE_ID \
    FAILED_RELEASE_ID ROLLBACK_ATTEMPT_ID ROLLBACK_RECEIPT_HMAC_KEY \
    ROLLBACK_RUNTIME_REPORT_FILE ROLLBACK_READINESS_REPORT_FILE; do
    require_value "${name}" "${!name:-}"
  done
  [[ "${ROLLBACK_RECEIPT_HMAC_KEY}" =~ ^[0-9a-f]{64}$ ]] \
    || { echo "[ERROR] ROLLBACK_RECEIPT_HMAC_KEY 格式非法" >&2; exit 1; }
  [[ -s "${ROLLBACK_RUNTIME_REPORT_FILE}" && -s "${ROLLBACK_READINESS_REPORT_FILE}" ]] \
    || { echo "[ERROR] 回滚验证证据不完整" >&2; exit 2; }
  runtime_sha="$(sha256sum "${ROLLBACK_RUNTIME_REPORT_FILE}" | awk '{print $1}')"
  readiness_sha="$(sha256sum "${ROLLBACK_READINESS_REPORT_FILE}" | awk '{print $1}')"
  payload="$(jq -cnS \
    --arg verifiedAt "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
    --arg commit "${EXPECTED_ROLLBACK_COMMIT}" --arg slot "${EXPECTED_ROLLBACK_SLOT}" \
    --arg releaseId "${EXPECTED_ROLLBACK_RELEASE_ID}" --arg failedReleaseId "${FAILED_RELEASE_ID}" \
    --arg attemptId "${ROLLBACK_ATTEMPT_ID}" --arg runtimeSha256 "${runtime_sha}" \
    --arg readinessSha256 "${readiness_sha}" \
    '{schemaVersion:1,verifiedAt:$verifiedAt,commit:$commit,slot:$slot,releaseId:$releaseId,
      failedReleaseId:$failedReleaseId,attemptId:$attemptId,publicEndpointVerified:true,
      runtimeReportSha256:$runtimeSha256,readinessReportSha256:$readinessSha256,conclusion:"PASS"}')"
  seal="$(printf '%s' "${payload}" | node -e 'const crypto=require("node:crypto");const chunks=[];process.stdin.on("data",c=>chunks.push(c));process.stdin.on("end",()=>process.stdout.write(crypto.createHmac("sha256",Buffer.from(process.env.ROLLBACK_RECEIPT_HMAC_KEY,"hex")).update(Buffer.concat(chunks)).digest("hex")));')"
  mkdir -p "$(dirname "${ROLLBACK_RECEIPT_FILE}")"
  jq -cS --arg seal "${seal}" '. + {seal:$seal}' <<< "${payload}" > "${ROLLBACK_RECEIPT_FILE}"
  chmod 400 "${ROLLBACK_RECEIPT_FILE}"
  archive_dir="${DEPLOY_BASE}/rollback-results"
  archive_file="${archive_dir}/${ROLLBACK_ATTEMPT_ID}.json"
  mkdir -p "${archive_dir}"
  chmod 700 "${archive_dir}"
  receipt_sha="$(sha256sum "${ROLLBACK_RECEIPT_FILE}" | awk '{print $1}')"
  if [[ -e "${archive_file}" ]]; then
    archive_sha="$(sha256sum "${archive_file}" | awk '{print $1}')"
    [[ "${archive_sha}" == "${receipt_sha}" ]] || { echo "[ERROR] 回滚收据已存在且摘要不同" >&2; exit 2; }
    return
  fi
  temp_file="$(mktemp "${archive_dir}/.rollback-result.XXXXXX")"
  cp -- "${ROLLBACK_RECEIPT_FILE}" "${temp_file}"
  chmod 400 "${temp_file}"
  ln "${temp_file}" "${archive_file}" || { rm -f -- "${temp_file}"; exit 2; }
  rm -f -- "${temp_file}"
  echo "[INFO] 公网回滚验证收据已归档"
}

main "$@"
