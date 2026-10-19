#!/usr/bin/env bash
# 确定性制造两个 USAGE_MISSING 调用，验证 UNKNOWN 的两种人工裁决与资金回执。

set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
# shellcheck source=scripts/ops/lib/acceptance_auth.sh
source "${SCRIPT_DIR}/../lib/acceptance_auth.sh"

REPORT_FILE="${UNKNOWN_BILLING_REPORT_FILE:-${ROOT_DIR}/reports/deterministic_full_stack_unknown_billing.md}"

log_error() { echo "[ERROR] $*" >&2; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || { log_error "缺少命令: $1"; exit 1; }; }
require_value() { [[ -n "$2" ]] || { log_error "$1 不能为空"; exit 1; }; }

api_request() {
  local method="$1" path="$2" body="$3" token="$4" tenant_id="$5"
  printf '%s' "${body}" | curl -sS --connect-timeout 5 --max-time 90 \
    -H 'Content-Type: application/json; charset=UTF-8' \
    -H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${tenant_id}" \
    -X "${method}" "${BASE_URL%/}${path}" --data-binary @-
}

api_get() {
  local path="$1" token="$2" tenant_id="$3"
  curl -fsS --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${tenant_id}" \
    "${BASE_URL%/}${path}"
}

mysql_query() {
  local sql="$1"
  docker compose -f "${ROOT_DIR}/docker-compose.yml" -f "${ROOT_DIR}/docker-compose.integration.yml" \
    exec -T -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql \
    mysql --batch --skip-column-names -uroot "${MYSQL_DATABASE}" -e "${sql}"
}

arm_usage_missing_fault() {
  docker compose -f "${ROOT_DIR}/docker-compose.yml" -f "${ROOT_DIR}/docker-compose.integration.yml" \
    exec -T model-stub node -e '
      fetch("http://127.0.0.1:18080/__ci/fault/usage-missing", {method: "POST"})
        .then(async response => {
          const body = await response.json();
          if (!response.ok || body.status !== "ARMED" || body.remaining < 2) process.exit(2);
        })
        .catch(() => process.exit(2));
    '
}

open_acceptance_session() {
  local response
  response="$(api_request POST '/api/v1/admin/billing/acceptance/sessions' \
    "$(jq -cn --arg tenantId "${LINGSQL_DEMO_TENANT_ID}" '{tenantId:$tenantId}')" \
    "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || {
    log_error "无法开启 UNKNOWN 计费验收会话"
    exit 2
  }
  jq -r '.data.sessionId // empty' <<< "${response}"
}

submit_usage_missing() {
  local question="$1" response request_id
  response="$(api_request POST '/api/v1/chat/ask' \
    "$(jq -cn --arg question "${question}" '{question:$question}')" \
    "${DEMO_TOKEN}" "${LINGSQL_DEMO_TENANT_ID}")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || {
    log_error "确定性问数请求未返回标准 API 响应"
    exit 2
  }
  request_id="$(jq -r '.requestId // empty' <<< "${response}")"
  [[ "${request_id}" =~ ^[A-Za-z0-9_-]{8,64}$ ]] || {
    log_error "失败响应未返回可审计 requestId"
    exit 2
  }
  log_error "确定性请求摘要: requestId=${request_id}, failureCode=$(jq -r '.data.failureCode // "NONE"' <<< "${response}"), hasSql=$(jq -r '((.data.sql // "") | length) > 0' <<< "${response}"), rowCount=$(jq -r '(.data.data // []) | length' <<< "${response}")"
  printf '%s' "${request_id}"
}

load_unknown_invocation() {
  local request_id="$1" row="" attempt
  for attempt in $(seq 1 20); do
    row="$(mysql_query "SELECT invocation_id, state, failure_code, max_billable_tokens, reserved_amount FROM t_lingsql_engine_billing_invocation WHERE tenant_id='${LINGSQL_DEMO_TENANT_ID}' AND workflow_request_id='${request_id}' ORDER BY id;")"
    if [[ -n "${row}" ]]; then
      break
    fi
    sleep 1
  done
  [[ "$(wc -l <<< "${row}" | tr -d ' ')" == "1" ]] || {
    log_error "请求 ${request_id} 未对应唯一物理调用"
    exit 2
  }
  local invocation_id state failure_code max_tokens reserved_amount
  IFS=$'\t' read -r invocation_id state failure_code max_tokens reserved_amount <<< "${row}"
  [[ "${invocation_id}" =~ ^[0-9a-f]{64}$ && "${state}" == "UNKNOWN" \
    && "${failure_code}" == "USAGE_MISSING" && "${max_tokens}" -gt 32 \
    && "${reserved_amount}" =~ ^[0-9]+$ ]] || {
    log_error "请求 ${request_id} 没有形成预期状态: state=${state:-NONE}, failure=${failure_code:-NONE}"
    mysql_query "SELECT CONCAT('recent=', workflow_request_id, '/', state, '/', COALESCE(failure_code, 'NONE')) FROM t_lingsql_engine_billing_invocation WHERE tenant_id='${LINGSQL_DEMO_TENANT_ID}' ORDER BY id DESC LIMIT 3;" >&2 || true
    exit 2
  }
  printf '%s' "${invocation_id}"
}

resolve_invocation() {
  local invocation_id="$1" body="$2" response
  response="$(api_request PUT "/api/v1/admin/billing/invocations/${invocation_id}/resolve" \
    "${body}" "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || {
    log_error "UNKNOWN 裁决失败: $(jq -r '.message // "无错误说明"' <<< "${response}")"
    exit 2
  }
  jq -c '.data' <<< "${response}"
}

assert_idempotent() {
  local first="$1" second="$2"
  [[ "$(jq -S -c . <<< "${first}")" == "$(jq -S -c . <<< "${second}")" ]] || {
    log_error "相同裁决证据重放未返回同一不可变回执"
    exit 2
  }
}

assert_conflict() {
  local invocation_id="$1" body="$2" response
  response="$(api_request PUT "/api/v1/admin/billing/invocations/${invocation_id}/resolve" \
    "${body}" "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "409" ]] || {
    log_error "不同证据未以资源冲突拒绝，实际 code=$(jq -r '.code // 0' <<< "${response}")"
    exit 2
  }
}

close_acceptance_session() {
  local session_id="$1" request_a="$2" request_b="$3" body response
  body="$(jq -cn --arg a "${request_a}" --arg b "${request_b}" '{requestIds:[$a,$b]}')"
  response="$(api_request POST "/api/v1/admin/billing/acceptance/sessions/${session_id}/close" \
    "${body}" "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || {
    log_error "UNKNOWN 裁决后的 MySQL 计费验收回执生成失败: $(jq -r '.message // "无错误说明"' <<< "${response}")"
    exit 2
  }
  jq -c '.data' <<< "${response}"
}

verify_vendor_bill_reconciliation() {
  local invocation_id="$1" receipt="$2"
  local settled_amount vendor_tokens bill_id_matched bill_id_missing csv csv_sha body
  local import_response report list_response bill unknown_response
  settled_amount="$(jq -r '.actualAmount' <<< "${receipt}")"
  vendor_tokens="$(jq -r '.actualTokens' <<< "${receipt}")"
  bill_id_matched="ci-bill-${invocation_id:0:16}"
  bill_id_missing="ci-bill-unmatched"
  # 合成账单仅用于验证导入、匹配与证据摘要链路，不代表真实供应商账单。
  csv="tenantId,providerName,externalBillId,amountMinor,currency,status,providerRequestId,vendorTokens
${LINGSQL_DEMO_TENANT_ID},CI_PROTOCOL_STUB,${bill_id_matched},${settled_amount},CNY,BILLED,chatcmpl-ci-unknown-settle,${vendor_tokens}
${LINGSQL_DEMO_TENANT_ID},CI_PROTOCOL_STUB,${bill_id_missing},1,CNY,BILLED,chatcmpl-ci-unknown-not-found,
${LINGSQL_DEMO_TENANT_ID},CI_PROTOCOL_STUB,${bill_id_matched},${settled_amount},CNY,BILLED,chatcmpl-ci-unknown-settle,${vendor_tokens}"
  csv_sha="$(printf '%s' "${csv}" | sha256sum | awk '{print $1}')"
  body="$(jq -cn --arg csv "${csv}" '{csv:$csv}')"
  import_response="$(api_request POST '/api/v1/admin/billing/vendor-bills/import' \
    "${body}" "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  [[ "$(jq -r '.code // 0' <<< "${import_response}")" == "200" ]] || {
    log_error "供应商账单导入失败: $(jq -r '.message // "无错误说明"' <<< "${import_response}")"
    exit 2
  }
  report="$(jq -c '.data' <<< "${import_response}")"
  [[ "$(jq -r '.totalRows' <<< "${report}")" == "3" \
    && "$(jq -r '.importedCount' <<< "${report}")" == "2" \
    && "$(jq -r '.duplicateCount' <<< "${report}")" == "1" \
    && "$(jq -r '.rejectedCount' <<< "${report}")" == "0" \
    && "$(jq -r '.matchSummary.MATCHED // 0' <<< "${report}")" == "1" \
    && "$(jq -r '.matchSummary.NOT_FOUND // 0' <<< "${report}")" == "1" \
    && "$(jq -r '.csvSha256' <<< "${report}")" == "${csv_sha}" ]] || {
    log_error "供应商账单导入报告与预期不一致: ${report}"
    exit 2
  }

  list_response="$(api_get "/api/v1/admin/billing/vendor-bills?tenantId=${LINGSQL_DEMO_TENANT_ID}&externalBillId=${bill_id_matched}" \
    "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  bill="$(jq -c '.data.list[0]' <<< "${list_response}")"
  [[ "$(jq -r '.matchState' <<< "${bill}")" == "MATCHED" \
    && "$(jq -r '.matchedInvocationId' <<< "${bill}")" == "${invocation_id}" \
    && "$(jq -r '.matchedState' <<< "${bill}")" == "SETTLED" \
    && "$(jq -r '.providerRequestId' <<< "${bill}")" == "chatcmpl-ci-unknown-settle" \
    && "$(jq -r '.evidenceHash' <<< "${bill}")" =~ ^[0-9a-f]{64}$ ]] || {
    log_error "已结算调用与合成账单未形成 MATCHED 匹配: ${bill}"
    exit 2
  }
  VENDOR_BILL_EVIDENCE_HASH="$(jq -r '.evidenceHash' <<< "${bill}")"
  VENDOR_BILL_MATCHED_AMOUNT="${settled_amount}"

  unknown_response="$(api_get "/api/v1/admin/billing/invocations/unknown?tenantId=${LINGSQL_DEMO_TENANT_ID}" \
    "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  [[ "$(jq --arg a "${invocation_id}" '[.data.list[].invocationId] | index($a) == null' <<< "${unknown_response}")" == "true" ]] || {
    log_error "已裁决物理调用仍然出现在 UNKNOWN 裁决工作台"
    exit 2
  }
}

main() {
  for command in curl jq docker sha256sum seq wc tr awk; do require_cmd "${command}"; done
  [[ -f "${ENV_FILE}" ]] || { log_error "未找到 ${ENV_FILE}"; exit 1; }
  # CI 生成的专用 .env 是该确定性验收的受控输入。
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  BASE_URL="${BASE_URL:-http://127.0.0.1:${APP_PORT:-8000}}"
  require_value "MYSQL_ROOT_PASSWORD" "${MYSQL_ROOT_PASSWORD:-}"
  require_value "MYSQL_DATABASE" "${MYSQL_DATABASE:-}"
  require_value "LINGSQL_DEMO_TENANT_ID" "${LINGSQL_DEMO_TENANT_ID:-}"
  require_value "LINGSQL_DEMO_ADMIN_USERNAME" "${LINGSQL_DEMO_ADMIN_USERNAME:-}"
  require_value "LINGSQL_DEMO_ADMIN_PASSWORD" "${LINGSQL_DEMO_ADMIN_PASSWORD:-}"
  require_value "LINGSQL_ADMIN_TENANT_ID" "${LINGSQL_ADMIN_TENANT_ID:-}"
  require_value "LINGSQL_ADMIN_USERNAME" "${LINGSQL_ADMIN_USERNAME:-}"
  require_value "LINGSQL_ADMIN_PASSWORD" "${LINGSQL_ADMIN_PASSWORD:-}"

  DEMO_TOKEN="$(acceptance_login "${BASE_URL}" "${LINGSQL_DEMO_TENANT_ID}" \
    "${LINGSQL_DEMO_ADMIN_USERNAME}" "${LINGSQL_DEMO_ADMIN_PASSWORD}")"
  CONTROL_TOKEN="$(acceptance_login "${BASE_URL}" "${LINGSQL_ADMIN_TENANT_ID}" \
    "${LINGSQL_ADMIN_USERNAME}" "${LINGSQL_ADMIN_PASSWORD}")"

  local session_id request_usage request_cancel invocation_usage invocation_cancel
  local usage_hash cancel_hash conflict_hash usage_body cancel_body conflict_body
  local usage_receipt usage_replay cancel_receipt cancel_replay acceptance_receipt ledger_response ledger
  session_id="$(open_acceptance_session)"
  require_value "计费验收会话 ID" "${session_id}"
  arm_usage_missing_fault || { log_error "无法武装确定性缺失 usage 故障"; exit 2; }

  request_usage="$(submit_usage_missing '2026年8月已完成订单的销售额是多少？')"
  request_cancel="$(submit_usage_missing '按商品统计2026年8月已完成订单的销售额。')"
  invocation_usage="$(load_unknown_invocation "${request_usage}")"
  invocation_cancel="$(load_unknown_invocation "${request_cancel}")"

  usage_hash="$(printf '%s' "FICTIONAL_STUB|${request_usage}|chatcmpl-ci-unknown-settle|32" | sha256sum | awk '{print $1}')"
  cancel_hash="$(printf '%s' "FICTIONAL_STUB|${request_cancel}|chatcmpl-ci-unknown-cancel|NOT_BILLED" | sha256sum | awk '{print $1}')"
  conflict_hash="$(printf '%s' "FICTIONAL_STUB|${request_usage}|CONFLICT" | sha256sum | awk '{print $1}')"
  usage_body="$(jq -cn --arg hash "${usage_hash}" --arg providerId 'chatcmpl-ci-unknown-settle' \
    '{evidenceHash:$hash,providerRequestId:$providerId,resolutionType:"CONFIRMED_USAGE",actualTokens:32}')"
  cancel_body="$(jq -cn --arg hash "${cancel_hash}" --arg providerId 'chatcmpl-ci-unknown-cancel' \
    '{evidenceHash:$hash,providerRequestId:$providerId,resolutionType:"CONFIRMED_NOT_BILLED"}')"
  conflict_body="$(jq -cn --arg hash "${conflict_hash}" --arg providerId 'chatcmpl-ci-unknown-settle' \
    '{evidenceHash:$hash,providerRequestId:$providerId,resolutionType:"CONFIRMED_USAGE",actualTokens:32}')"

  usage_receipt="$(resolve_invocation "${invocation_usage}" "${usage_body}")"
  usage_replay="$(resolve_invocation "${invocation_usage}" "${usage_body}")"
  assert_idempotent "${usage_receipt}" "${usage_replay}"
  assert_conflict "${invocation_usage}" "${conflict_body}"
  cancel_receipt="$(resolve_invocation "${invocation_cancel}" "${cancel_body}")"
  cancel_replay="$(resolve_invocation "${invocation_cancel}" "${cancel_body}")"
  assert_idempotent "${cancel_receipt}" "${cancel_replay}"

  [[ "$(jq -r '.state' <<< "${usage_receipt}")" == "SETTLED" \
    && "$(jq -r '.resolutionType' <<< "${usage_receipt}")" == "CONFIRMED_USAGE" \
    && "$(jq -r '.actualTokens' <<< "${usage_receipt}")" == "32" ]] || {
    log_error "CONFIRMED_USAGE 回执不完整"
    exit 2
  }
  [[ "$(jq -r '.state' <<< "${cancel_receipt}")" == "CANCELLED" \
    && "$(jq -r '.resolutionType' <<< "${cancel_receipt}")" == "CONFIRMED_NOT_BILLED" \
    && "$(jq -r '.actualAmount' <<< "${cancel_receipt}")" == "0" ]] || {
    log_error "CONFIRMED_NOT_BILLED 回执不完整"
    exit 2
  }

  ledger_response="$(api_get "/api/v1/admin/billing/ledger/evidence?tenantId=${LINGSQL_DEMO_TENANT_ID}&requestIds=${request_usage},${request_cancel}" \
    "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  [[ "$(jq -r '.code // 0' <<< "${ledger_response}")" == "200" ]] || { log_error "无法读取裁决后账本证据"; exit 2; }
  ledger="$(jq -c '.data' <<< "${ledger_response}")"
  [[ "$(jq -r '.invocationCount' <<< "${ledger}")" == "2" \
    && "$(jq -r '.settledInvocationCount' <<< "${ledger}")" == "1" \
    && "$(jq -r '.cancelledInvocationCount' <<< "${ledger}")" == "1" \
    && "$(jq -r '.unknownInvocationCount' <<< "${ledger}")" == "0" \
    && "$(jq -r '.pendingInvocationCount' <<< "${ledger}")" == "0" \
    && "$(jq -r '.reserveCount' <<< "${ledger}")" == "2" \
    && "$(jq -r '.closeCount' <<< "${ledger}")" == "1" ]] || {
    log_error "裁决后物理调用或账本终态数量不一致"
    exit 2
  }

  acceptance_receipt="$(close_acceptance_session "${session_id}" "${request_usage}" "${request_cancel}")"
  jq -e --arg a "${request_usage}" --arg b "${request_cancel}" \
    --argjson settledAmount "$(jq -r '.actualAmount' <<< "${usage_receipt}")" '
      (.requestIds | sort) == ([$a,$b] | sort) and
      .invocationCount == 2 and .settledInvocationCount == 1 and .cancelledInvocationCount == 1 and
      .unrelatedEventCount == 0 and .scopeNetAmount == .intervalNetAmount and
      (.balanceBefore - .balanceAfter) == .intervalNetAmount and
      .intervalNetAmount == $settledAmount and (.receiptHash | test("^[0-9a-f]{64}$"))
    ' <<< "${acceptance_receipt}" >/dev/null || {
    log_error "billing acceptance receipt 未证明余额、账本与两种裁决守恒"
    exit 2
  }

  verify_vendor_bill_reconciliation "${invocation_usage}" "${usage_receipt}"

  mkdir -p "$(dirname "${REPORT_FILE}")"
  [[ ! -e "${REPORT_FILE}" ]] || { log_error "拒绝覆盖已有报告: ${REPORT_FILE}"; exit 2; }
  cat > "${REPORT_FILE}" <<EOF
# UNKNOWN 物理调用确定性全栈验收（非供应商账单证据）

> 本报告使用 CI OpenAI 协议桩故意省略 usage，只验证系统 UNKNOWN 状态机、人工裁决、幂等冲突和 MySQL 资金守恒；合成账单只用于验证导入、匹配与证据摘要链路，不得作为真实供应商账单、客户 UAT 或商业收款证据。

- 验收结论: PASS
- UNKNOWN 原因: USAGE_MISSING × 2
- CONFIRMED_USAGE: SETTLED，Token 32，实际金额 $(jq -r '.actualAmount' <<< "${usage_receipt}")，退款 $(jq -r '.refundAmount' <<< "${usage_receipt}")
- CONFIRMED_NOT_BILLED: CANCELLED，实际金额 0，退款 $(jq -r '.refundAmount' <<< "${cancel_receipt}")
- 相同证据重放: 两种裁决均返回原不可变回执
- 不同证据重放: 冲突并拒绝覆盖
- 合成供应商账单: 导入 2 行（MATCHED 1，NOT_FOUND 1），重复 1 行跳过；MATCHED 金额 ${VENDOR_BILL_MATCHED_AMOUNT} 分，证据摘要 ${VENDOR_BILL_EVIDENCE_HASH}
- 验收请求数: $(jq -r '.requestIds | length' <<< "${acceptance_receipt}")
- 物理调用: $(jq -r '.invocationCount' <<< "${acceptance_receipt}")（SETTLED $(jq -r '.settledInvocationCount' <<< "${acceptance_receipt}")，CANCELLED $(jq -r '.cancelledInvocationCount' <<< "${acceptance_receipt}")）
- MySQL 余额: $(jq -r '.balanceBefore' <<< "${acceptance_receipt}") -> $(jq -r '.balanceAfter' <<< "${acceptance_receipt}")
- 账本区间净扣款: $(jq -r '.intervalNetAmount' <<< "${acceptance_receipt}")
- 不相关账本事件: $(jq -r '.unrelatedEventCount' <<< "${acceptance_receipt}")
- 不可变验收回执 SHA-256: $(jq -r '.receiptHash' <<< "${acceptance_receipt}")
EOF
  chmod 600 "${REPORT_FILE}"
  echo "[INFO] UNKNOWN 物理调用确定性验收通过: ${REPORT_FILE}"
}

main "$@"
