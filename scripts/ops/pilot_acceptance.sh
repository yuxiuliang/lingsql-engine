#!/usr/bin/env bash
# LingSQL 付费试点真实业务验收。脚本不会创建客户、模型或数据源，必须在完成书面接入确认后运行。
# 必填：PILOT_TENANT_ID、PILOT_USERNAME、PILOT_PASSWORD
# 控制身份：提供 PILOT_CONTROL_TOKEN，或同时提供 PILOT_CONTROL_TENANT_ID、PILOT_CONTROL_USERNAME、PILOT_CONTROL_PASSWORD
# 可选：BASE_URL、PILOT_QUESTION_1..3、PILOT_REPORT_FILE

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8000}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ops/lib/acceptance_auth.sh
source "${SCRIPT_DIR}/lib/acceptance_auth.sh"
# shellcheck source=scripts/ops/lib/acceptance_tenant_readiness.sh
source "${SCRIPT_DIR}/lib/acceptance_tenant_readiness.sh"
PILOT_TENANT_ID="${PILOT_TENANT_ID:-}"
PILOT_USERNAME="${PILOT_USERNAME:-}"
PILOT_PASSWORD="${PILOT_PASSWORD:-}"
PILOT_CONTROL_TOKEN="${PILOT_CONTROL_TOKEN:-}"
PILOT_CONTROL_TENANT_ID="${PILOT_CONTROL_TENANT_ID:-}"
PILOT_CONTROL_USERNAME="${PILOT_CONTROL_USERNAME:-}"
PILOT_CONTROL_PASSWORD="${PILOT_CONTROL_PASSWORD:-}"
PILOT_QUESTION_1="${PILOT_QUESTION_1:-2026年8月已完成订单的销售额是多少？}"
PILOT_QUESTION_2="${PILOT_QUESTION_2:-哪些商品当前库存低于安全库存？}"
PILOT_QUESTION_3="${PILOT_QUESTION_3:-按商品统计已完成订单销售额，从高到低排列。}"
PILOT_REPORT_FILE="${PILOT_REPORT_FILE:-./reports/pilot_acceptance.md}"

log_info() {
  echo "[INFO] $*"
}

log_error() {
  echo "[ERROR] $*" >&2
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log_error "缺少命令: $1"
    exit 1
  fi
}

require_value() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    log_error "${name} 不能为空"
    exit 1
  fi
}

api_post() {
  local path="$1"
  local body="$2"
  local token="${3:-}"
  local auth_args=()
  if [[ -n "${token}" ]]; then
    auth_args=(-H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${PILOT_TENANT_ID}")
  fi
  printf '%s' "${body}" | curl -fsS --connect-timeout 5 --max-time 90 \
    -H "Content-Type: application/json; charset=UTF-8" "${auth_args[@]}" \
    -X POST "${BASE_URL%/}${path}" --data-binary @-
}

api_get() {
  local path="$1"
  local token="$2"
  curl -fsS --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${PILOT_TENANT_ID}" \
    "${BASE_URL%/}${path}"
}

load_audit_total() {
  local token="$1"
  local tenant_id="$2"
  local user_id="$3"
  local response
  response="$(api_get "/api/v1/admin/audit/sql-logs?tenantId=$(jq -rn --arg v "${tenant_id}" '$v|@uri')&userId=${user_id}&page=1&size=1" "${token}")"
  if [[ "$(jq -r '.code' <<< "${response}")" != "200" ]]; then
    log_error "控制账号无法读取 SQL 审计日志，请通过 PILOT_CONTROL_TOKEN 提供系统验收 Token。"
    exit 2
  fi
  jq -r '.data.total // 0' <<< "${response}"
}

load_billing_snapshot() {
  local token="$1"
  local response
  response="$(api_get "/api/v1/admin/billing/readiness/snapshot" "${token}")"
  if [[ "$(jq -r '.code' <<< "${response}")" != "200" ]]; then
    log_error "控制账号无法读取计费就绪快照，请通过 PILOT_CONTROL_TOKEN 提供系统验收 Token。"
    exit 2
  fi
  jq -c '.data' <<< "${response}"
}

load_ledger_evidence() {
  local token="$1"
  local tenant_id="$2"
  local request_ids="$3"
  local tenant_encoded request_ids_encoded response
  tenant_encoded="$(jq -rn --arg v "${tenant_id}" '$v|@uri')"
  request_ids_encoded="$(jq -rn --arg v "${request_ids}" '$v|@uri')"
  response="$(api_get "/api/v1/admin/billing/ledger/evidence?tenantId=${tenant_encoded}&requestIds=${request_ids_encoded}" "${token}")"
  if [[ "$(jq -r '.code' <<< "${response}")" != "200" ]]; then
    log_error "控制账号无法读取本次请求的计费账本证据。"
    exit 2
  fi
  jq -c '.data' <<< "${response}"
}

open_billing_acceptance() {
  local token="$1"
  local body response
  body="$(jq -cn --arg tenantId "${PILOT_TENANT_ID}" '{tenantId:$tenantId}')"
  response="$(api_post "/api/v1/admin/billing/acceptance/sessions" "${body}" "${token}")"
  if [[ "$(jq -r '.code // 0' <<< "${response}")" != "200" ]]; then
    log_error "无法冻结计费验收余额与账本水位。"
    exit 2
  fi
  jq -r '.data.sessionId // empty' <<< "${response}"
}

close_billing_acceptance() {
  local token="$1"
  local session_id="$2"
  shift 2
  local request_ids_json body response valid expected_count
  expected_count="$#"
  request_ids_json="$(printf '%s\n' "$@" | jq -Rsc 'split("\n") | map(select(length > 0))')"
  body="$(jq -cn --argjson requestIds "${request_ids_json}" '{requestIds:$requestIds}')"
  response="$(api_post "/api/v1/admin/billing/acceptance/sessions/${session_id}/close" "${body}" "${token}")"
  if [[ "$(jq -r '.code // 0' <<< "${response}")" != "200" ]]; then
    log_error "本批请求未通过 MySQL 余额与账本区间验收。"
    exit 2
  fi
  valid="$(jq -r --argjson expectedCount "${expected_count}" '
    .data as $receipt |
    ($receipt.requestIds | length) == $expectedCount and
    $receipt.unrelatedEventCount == 0 and
    $receipt.cancelledInvocationCount == 0 and
    $receipt.settledInvocationCount == $receipt.invocationCount and
    $receipt.scopeNetAmount == $receipt.intervalNetAmount and
    ($receipt.balanceBefore - $receipt.balanceAfter) == $receipt.intervalNetAmount and
    ($receipt.receiptHash | test("^[0-9a-f]{64}$"))
  ' <<< "${response}")"
  [[ "${valid}" == "true" ]] || { log_error "计费验收回执不满足资金守恒约束。"; exit 2; }
  jq -c '.data' <<< "${response}"
}

main() {
  require_cmd curl
  require_cmd jq
  require_value "PILOT_TENANT_ID" "${PILOT_TENANT_ID}"
  require_value "PILOT_USERNAME" "${PILOT_USERNAME}"
  require_value "PILOT_PASSWORD" "${PILOT_PASSWORD}"

  local login_body login_response token user_id control_token tenant_readiness
  login_body="$(jq -cn \
    --arg tenantId "${PILOT_TENANT_ID}" \
    --arg username "${PILOT_USERNAME}" \
    --arg password "${PILOT_PASSWORD}" \
    '{tenantId:$tenantId,username:$username,password:$password}')"
  login_response="$(api_post "/api/v1/auth/login" "${login_body}")"
  if [[ "$(jq -r '.code' <<< "${login_response}")" != "200" ]]; then
    log_error "试点账号登录失败。"
    exit 2
  fi
  token="$(jq -r '.data.token // empty' <<< "${login_response}")"
  user_id="$(jq -r '.data.userId // empty' <<< "${login_response}")"
  require_value "登录 Token" "${token}"
  require_value "登录用户 ID" "${user_id}"
  control_token="$(acceptance_resolve_control_token \
    "${BASE_URL}" "${PILOT_CONTROL_TOKEN}" "${PILOT_CONTROL_TENANT_ID}" \
    "${PILOT_CONTROL_USERNAME}" "${PILOT_CONTROL_PASSWORD}")"
  tenant_readiness="$(acceptance_require_first_question_ready \
    "${BASE_URL}" "${control_token}" "${PILOT_TENANT_ID}")"
  [[ "$(jq -r '.ready' <<< "${tenant_readiness}")" == "true" ]] \
    || { log_error "租户首问就绪门禁未通过。"; exit 2; }

  local baseline_audit_total baseline_billing_snapshot baseline_ledger_total billing_acceptance_session_id
  baseline_audit_total="$(load_audit_total "${control_token}" "${PILOT_TENANT_ID}" "${user_id}")"
  baseline_billing_snapshot="$(load_billing_snapshot "${control_token}")"
  baseline_ledger_total="$(jq -r '.ledgerTotal // 0' <<< "${baseline_billing_snapshot}")"
  billing_acceptance_session_id="$(open_billing_acceptance "${control_token}")"
  require_value "计费验收会话 ID" "${billing_acceptance_session_id}"

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap "rm -r -- '${tmp_dir}'" EXIT

  local question response sql row_count conversation_id expected_conversation_id request_id
  local -a request_ids=()
  local passed=0
  local index=0
  local started_at
  started_at="$(date -Iseconds)"
  shopt -s nocasematch
  for question in "${PILOT_QUESTION_1}" "${PILOT_QUESTION_2}" "${PILOT_QUESTION_3}"; do
    index=$((index + 1))
    local ask_body
    ask_body="$(jq -cn --arg question "${question}" --arg conversationId "${conversation_id:-}" \
      'if $conversationId == "" then {question:$question} else {question:$question,conversationId:$conversationId} end')"
    response="$(api_post "/api/v1/chat/ask" "${ask_body}" "${token}")"
    if [[ "$(jq -r '.code' <<< "${response}")" != "200" ]]; then
      log_error "第 ${index} 条真实问数失败: code=$(jq -r '.code // "unknown"' <<< "${response}"), message=$(jq -r '.message // "未返回错误信息"' <<< "${response}")"
      exit 2
    fi
    sql="$(jq -r '.data.sql // empty' <<< "${response}")"
    row_count="$(jq -r '(.data.data // []) | length' <<< "${response}")"
    conversation_id="$(jq -r '.data.conversationId // empty' <<< "${response}")"
    request_id="$(jq -r '.requestId // empty' <<< "${response}")"
    require_value "第 ${index} 条问数的会话 ID" "${conversation_id}"
    require_value "第 ${index} 条问数的请求 ID" "${request_id}"
    request_ids+=("${request_id}")
    if [[ -z "${expected_conversation_id:-}" ]]; then
      expected_conversation_id="${conversation_id}"
    elif [[ "${conversation_id}" != "${expected_conversation_id}" ]]; then
      log_error "第 ${index} 条问数没有延续同一会话。"
      exit 2
    fi
    if [[ "${row_count}" -le 0 ]]; then
      log_error "第 ${index} 条真实问数返回空结果。"
      exit 2
    fi
    if ! [[ "${sql}" =~ ^[[:space:]]*(SELECT|WITH)[[:space:]] ]]; then
      log_error "第 ${index} 条问数未生成只读 SQL。"
      exit 2
    fi
    jq -cn --arg question "${question}" --arg sql "${sql}" --argjson rows "${row_count}" \
      '{question:$question,sql:$sql,rows:$rows}' > "${tmp_dir}/result-${index}.json"
    passed=$((passed + 1))
  done
  shopt -u nocasematch

  local audit_total audit_increment billing_snapshot ledger_total ledger_increment billing_ready
  local request_ids_csv ledger_evidence matched_request_count reserve_count close_count
  local invocation_count matched_workflow_count settled_invocation_count unknown_invocation_count pending_invocation_count
  local cancelled_invocation_count complete_request_count unbalanced_invocation_count conserved strict_request_count
  local billing_acceptance_receipt billing_acceptance_receipt_hash balance_before balance_after interval_net_amount
  audit_total="$(load_audit_total "${control_token}" "${PILOT_TENANT_ID}" "${user_id}")"
  audit_increment=$((audit_total - baseline_audit_total))
  if [[ "${audit_increment}" -lt 3 ]]; then
    log_error "本次新增 SQL 审计日志不足 3 条，真实链路不可追溯。"
    exit 2
  fi

  for _ in {1..10}; do
    billing_snapshot="$(load_billing_snapshot "${control_token}")"
    ledger_total="$(jq -r '.ledgerTotal // 0' <<< "${billing_snapshot}")"
    if [[ "${ledger_total}" -gt "${baseline_ledger_total}" ]]; then
      break
    fi
    sleep 1
  done
  billing_ready="$(jq -r '.ready // false' <<< "${billing_snapshot}")"
  ledger_increment=$((ledger_total - baseline_ledger_total))
  request_ids_csv="$(IFS=,; echo "${request_ids[*]}")"
  billing_acceptance_receipt="$(close_billing_acceptance "${control_token}" "${billing_acceptance_session_id}" "${request_ids[@]}")"
  billing_acceptance_receipt_hash="$(jq -r '.receiptHash' <<< "${billing_acceptance_receipt}")"
  balance_before="$(jq -r '.balanceBefore' <<< "${billing_acceptance_receipt}")"
  balance_after="$(jq -r '.balanceAfter' <<< "${billing_acceptance_receipt}")"
  interval_net_amount="$(jq -r '.intervalNetAmount' <<< "${billing_acceptance_receipt}")"
  ledger_evidence="$(load_ledger_evidence "${control_token}" "${PILOT_TENANT_ID}" "${request_ids_csv}")"
  matched_request_count="$(jq -r '.matchedRequestCount // 0' <<< "${ledger_evidence}")"
  reserve_count="$(jq -r '.reserveCount // 0' <<< "${ledger_evidence}")"
  close_count="$(jq -r '.closeCount // 0' <<< "${ledger_evidence}")"
  invocation_count="$(jq -r '.invocationCount // 0' <<< "${ledger_evidence}")"
  matched_workflow_count="$(jq -r '.matchedWorkflowCount // 0' <<< "${ledger_evidence}")"
  settled_invocation_count="$(jq -r '.settledInvocationCount // 0' <<< "${ledger_evidence}")"
  unknown_invocation_count="$(jq -r '.unknownInvocationCount // 0' <<< "${ledger_evidence}")"
  pending_invocation_count="$(jq -r '.pendingInvocationCount // 0' <<< "${ledger_evidence}")"
  cancelled_invocation_count="$(jq -r '.cancelledInvocationCount // 0' <<< "${ledger_evidence}")"
  complete_request_count="$(jq -r '.completeRequestCount // 0' <<< "${ledger_evidence}")"
  unbalanced_invocation_count="$(jq -r '.unbalancedInvocationCount // 0' <<< "${ledger_evidence}")"
  conserved="$(jq -r '.conserved // false' <<< "${ledger_evidence}")"
  strict_request_count="$(jq '[.requests[]? | select(
    .complete == true and
    .sqlAuditCount == 1 and .successfulSqlAuditCount == 1 and
    .invocationCount >= 2 and .ledgerInvocationCount == .invocationCount and
    .architectSettledCount >= 1 and .analystSettledCount >= 1 and
    .settledInvocationCount == .invocationCount and
    .unknownInvocationCount == 0 and .pendingInvocationCount == 0 and .cancelledInvocationCount == 0 and
    .reserveCount == .invocationCount and .closeCount == .invocationCount and
    .unbalancedInvocationCount == 0 and (.failureReasons | length) == 0
  )] | length' <<< "${ledger_evidence}")"
  if [[ "${matched_request_count}" -ne 3
      || "${invocation_count}" -lt 6
      || "${matched_workflow_count}" -ne 3
      || "${settled_invocation_count}" -ne "${invocation_count}"
      || "${unknown_invocation_count}" -ne 0
      || "${pending_invocation_count}" -ne 0
      || "${cancelled_invocation_count}" -ne 0
      || "${complete_request_count}" -ne 3
      || "${unbalanced_invocation_count}" -ne 0
      || "${conserved}" != "true"
      || "${reserve_count}" -ne "${invocation_count}"
      || "${close_count}" -ne "${invocation_count}"
      || "${strict_request_count}" -ne 3
      || "${billing_ready}" != "true" ]]; then
    log_error "本次三个请求未逐项形成 SQL 审计、双角色调用、账本事件和资金守恒闭环。"
    jq -c '.requests[]? | select(.complete != true) | {requestId,failureReasons}' <<< "${ledger_evidence}" >&2 || true
    exit 2
  fi

  mkdir -p "$(dirname "${PILOT_REPORT_FILE}")"
  cat > "${PILOT_REPORT_FILE}" <<EOF
# LingSQL 付费试点真实业务验收报告

- 开始时间: ${started_at}
- 完成时间: $(date -Iseconds)
- 目标环境: ${BASE_URL}
- 租户: ${PILOT_TENANT_ID}
- 首问就绪: $(jq -r '.status' <<< "${tenant_readiness}")（$(jq '[.checks[]|select(.passed==true)]|length' <<< "${tenant_readiness}")/5）
- 真实问数通过: ${passed}/3
- 本次新增 SQL 审计日志: ${audit_increment}
- 本次新增计费账本事件: ${ledger_increment}
- 关联请求数: ${matched_request_count}/3
- 关联 RESERVE/CLOSE: ${reserve_count}/${close_count}
- 完整逐请求证据: ${complete_request_count}/3（严格复核 ${strict_request_count}/3，资金守恒 ${conserved}）
- 物理模型调用: ${invocation_count}（已结算 ${settled_invocation_count}，UNKNOWN ${unknown_invocation_count}，未终态 ${pending_invocation_count}，取消 ${cancelled_invocation_count}）
- MySQL 余额与账本区间: ${balance_before} -> ${balance_after}（净扣款 ${interval_net_amount}，回执 ${billing_acceptance_receipt_hash}）
- 计费就绪: ${billing_ready}
- 验收结论: PASS

## 验证边界

本报告证明首问五项门禁、登录、三条自然语言问数、只读 SQL、非空结果、会话延续、SQL 审计，以及本次请求关联的逐物理模型调用计费在当前环境中形成闭环。报告不包含 Token、密码、API Key、Prompt、模型原文、SQL 明细或业务结果明细，也不替代双方的 40 题黄金问题签字验收。
EOF
  log_info "真实业务验收通过，报告已写入: ${PILOT_REPORT_FILE}"
}

main "$@"
