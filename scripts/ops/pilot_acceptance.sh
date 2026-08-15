#!/usr/bin/env bash
# LingSQL 付费试点真实业务验收。脚本不会创建客户、模型或数据源，必须在完成书面接入确认后运行。
# 必填：PILOT_TENANT_ID、PILOT_USERNAME、PILOT_PASSWORD
# 可选：BASE_URL、PILOT_QUESTION_1..3、PILOT_CONTROL_TOKEN、PILOT_REPORT_FILE

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8000}"
PILOT_TENANT_ID="${PILOT_TENANT_ID:-}"
PILOT_USERNAME="${PILOT_USERNAME:-}"
PILOT_PASSWORD="${PILOT_PASSWORD:-}"
PILOT_CONTROL_TOKEN="${PILOT_CONTROL_TOKEN:-}"
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

main() {
  require_cmd curl
  require_cmd jq
  require_value "PILOT_TENANT_ID" "${PILOT_TENANT_ID}"
  require_value "PILOT_USERNAME" "${PILOT_USERNAME}"
  require_value "PILOT_PASSWORD" "${PILOT_PASSWORD}"

  local login_body login_response token user_id control_token
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
  control_token="${PILOT_CONTROL_TOKEN:-${token}}"

  local baseline_audit_total baseline_billing_snapshot baseline_ledger_total
  baseline_audit_total="$(load_audit_total "${control_token}" "${PILOT_TENANT_ID}" "${user_id}")"
  baseline_billing_snapshot="$(load_billing_snapshot "${control_token}")"
  baseline_ledger_total="$(jq -r '.ledgerTotal // 0' <<< "${baseline_billing_snapshot}")"

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap "rm -r -- '${tmp_dir}'" EXIT

  local question response sql row_count conversation_id expected_conversation_id
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
    require_value "第 ${index} 条问数的会话 ID" "${conversation_id}"
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
  if [[ "${ledger_increment}" -le 0 || "${billing_ready}" != "true" ]]; then
    log_error "本次请求没有产生计费账本增量或计费就绪检查未通过。"
    exit 2
  fi

  mkdir -p "$(dirname "${PILOT_REPORT_FILE}")"
  cat > "${PILOT_REPORT_FILE}" <<EOF
# LingSQL 付费试点真实业务验收报告

- 开始时间: ${started_at}
- 完成时间: $(date -Iseconds)
- 目标环境: ${BASE_URL}
- 租户: ${PILOT_TENANT_ID}
- 真实问数通过: ${passed}/3
- 本次新增 SQL 审计日志: ${audit_increment}
- 本次新增计费账本事件: ${ledger_increment}
- 计费就绪: ${billing_ready}
- 验收结论: PASS

## 验证边界

本报告证明登录、三条自然语言问数、只读 SQL、非空结果、会话延续、SQL 审计和计费账本在本次环境中形成闭环。报告不包含 Token、密码、API Key、SQL 明细或业务结果明细，也不替代双方的 40 题黄金问题签字验收。
EOF
  log_info "真实业务验收通过，报告已写入: ${PILOT_REPORT_FILE}"
}

main "$@"
