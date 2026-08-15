#!/usr/bin/env bash
# LingSQL 40题黄金问题正式验收 runner。JSON 是签字真源；脚本仅生成自动化证据，不代替双方签字。

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8000}"
PILOT_TENANT_ID="${PILOT_TENANT_ID:-}"
PILOT_USERNAME="${PILOT_USERNAME:-}"
PILOT_PASSWORD="${PILOT_PASSWORD:-}"
PILOT_CONTROL_TOKEN="${PILOT_CONTROL_TOKEN:-}"
GQ_SUITE_FILE="${GQ_SUITE_FILE:-}"
GQ_PHASE="${GQ_PHASE:-FIRST}"
GQ_PARENT_EVIDENCE="${GQ_PARENT_EVIDENCE:-}"
GQ_OUTPUT_DIR="${GQ_OUTPUT_DIR:-}"
GQ_EVIDENCE_SETTLE_SECONDS="${GQ_EVIDENCE_SETTLE_SECONDS:-10}"

log_info() { echo "[INFO] $*"; }
log_error() { echo "[ERROR] $*" >&2; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { log_error "缺少命令: $1"; exit 1; }
}

require_value() {
  [[ -n "$2" ]] || { log_error "$1 不能为空"; exit 1; }
}

api_get() {
  local path="$1" token="$2"
  curl -fsS --connect-timeout 5 --max-time 90 \
    -H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${PILOT_TENANT_ID}" \
    "${BASE_URL%/}${path}"
}

post_capture() {
  local path="$1" body="$2" token="$3" response_file="$4" metrics_file="$5"
  local -a auth_args=()
  [[ -n "${token}" ]] && auth_args=(-H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${PILOT_TENANT_ID}")
  if ! printf '%s' "${body}" | curl -sS --connect-timeout 5 --max-time 90 \
      -o "${response_file}" -w '%{http_code}\t%{time_total}' \
      -H 'Content-Type: application/json; charset=UTF-8' "${auth_args[@]}" \
      -X POST "${BASE_URL%/}${path}" --data-binary @- > "${metrics_file}"; then
    [[ -s "${metrics_file}" ]] || printf '000\t90.000000' > "${metrics_file}"
    [[ -s "${response_file}" ]] || printf '{"code":0,"message":"transport failure"}' > "${response_file}"
    return 1
  fi
}

validate_suite() {
  jq -e '
    .schemaVersion == "1.0" and
    (.suiteId | type == "string" and length > 0) and
    (.suiteVersion | type == "string" and length > 0) and
    (.sowId | type == "string" and length > 0) and
    (.environment.concurrency == 1) and
    ([.environment.environmentId,.environment.dataSnapshotId,.environment.modelProvider,
      .environment.modelName,.environment.modelVersion,.environment.networkProfile]
      | all(type == "string" and length > 0)) and
    (.businessCases | length == 40) and
    ([.businessCases[] | select(.priority == "P0")] | length == 20) and
    ([.businessCases[] | select(.priority == "P1")] | length == 20) and
    ([.businessCases[].id,.securityCases[].id] as $ids | ($ids | length) == ($ids | unique | length)) and
    (.securityCases | length > 0) and
    all(.businessCases[];
      (.id | test("^[A-Z][A-Z0-9_-]{2,39}$")) and .mode == "INDEPENDENT" and
      (.baselineSeconds | type == "number" and . > 0) and
      (.question | type == "string" and length > 0 and (startswith("请替换为") | not)) and
      if .expected.kind == "SCALAR_NUMBER" then
        (.expected.rowIndex | type == "number" and . >= 0 and floor == .) and
        (.expected.column | type == "string" and length > 0) and
        (.expected.value | type == "number") and
        (.expected.absoluteTolerance | type == "number" and . >= 0) and
        (.expected.relativeTolerance | type == "number" and . >= 0)
      elif .expected.kind == "ROWSET" then
        (.expected.columns | type == "array" and length > 0 and length == (unique | length)) and
        (.expected.rows | type == "array") and (.expected.orderSensitive | type == "boolean") and
        (.expected.columns as $columns | all(.expected.rows[]; . as $row | all($columns[]; . as $column | $row | has($column))))
      else false end) and
    all(.securityCases[];
      (.id | test("^[A-Z][A-Z0-9_-]{2,39}$")) and
      (.kind == "WRITE_SQL" or .kind == "CROSS_TENANT" or .kind == "OUT_OF_ALLOWLIST") and
      (.question | type == "string" and length > 0) and .expected.kind == "REJECTION" and
      (.expected.allowedApiCodes | type == "array" and length > 0) and
      (.expected.allowSafeRefusal200 | type == "boolean"))
  ' "${GQ_SUITE_FILE}" >/dev/null || { log_error "黄金问题 JSON 结构或 40题/20-20 约束不合法"; exit 2; }
}

compare_scalar() {
  local case_json="$1" response_file="$2"
  local row column expected abs_tol rel_tol actual
  row="$(jq -r '.expected.rowIndex' <<< "${case_json}")"
  column="$(jq -r '.expected.column' <<< "${case_json}")"
  expected="$(jq -r '.expected.value' <<< "${case_json}")"
  abs_tol="$(jq -r '.expected.absoluteTolerance' <<< "${case_json}")"
  rel_tol="$(jq -r '.expected.relativeTolerance' <<< "${case_json}")"
  actual="$(jq -r --argjson row "${row}" --arg column "${column}" '.data.data[$row][$column] // empty' "${response_file}")"
  [[ "${actual}" =~ ^-?[0-9]+([.][0-9]+)?([eE][+-]?[0-9]+)?$ ]] || return 1
  awk -v a="${actual}" -v e="${expected}" -v at="${abs_tol}" -v rt="${rel_tol}" \
    'BEGIN { d=a-e; if(d<0)d=-d; re=e; if(re<0)re=-re; t=at; if(re*rt>t)t=re*rt; exit(d<=t?0:1) }'
}

compare_rowset() {
  local case_json="$1" response_file="$2" columns expected_rows order_sensitive actual_rows
  columns="$(jq -c '.expected.columns' <<< "${case_json}")"
  order_sensitive="$(jq -r '.expected.orderSensitive' <<< "${case_json}")"
  if ! jq -e --argjson columns "${columns}" '
      (.data.data | type == "array") and
      all(.data.data[]; . as $row | all($columns[]; . as $column | $row | has($column)))
    ' "${response_file}" >/dev/null; then
    return 1
  fi
  actual_rows="$(jq -cS --argjson columns "${columns}" '[.data.data[] | . as $row | reduce $columns[] as $c ({}; .[$c]=$row[$c])]' "${response_file}")"
  expected_rows="$(jq -cS --argjson columns "${columns}" '[.expected.rows[] | . as $row | reduce $columns[] as $c ({}; .[$c]=$row[$c])]' <<< "${case_json}")"
  if [[ "${order_sensitive}" != "true" ]]; then
    actual_rows="$(jq -cS 'sort_by(tojson)' <<< "${actual_rows}")"
    expected_rows="$(jq -cS 'sort_by(tojson)' <<< "${expected_rows}")"
  fi
  [[ "${actual_rows}" == "${expected_rows}" ]]
}

append_business_evidence() {
  local case_json="$1" response_file="$2" http_code="$3" duration_ms="$4"
  local id priority api_code request_id sql kind correct=false reason="MISMATCH" digest
  id="$(jq -r '.id' <<< "${case_json}")"
  priority="$(jq -r '.priority' <<< "${case_json}")"
  kind="$(jq -r '.expected.kind' <<< "${case_json}")"
  api_code="$(jq -r '.code // 0' "${response_file}")"
  request_id="$(jq -r '.requestId // empty' "${response_file}")"
  sql="$(jq -r '.data.sql // empty' "${response_file}")"
  digest="$(jq -cS '.data.data // []' "${response_file}" | sha256sum | awk '{print $1}')"
  if [[ "${http_code}" != "200" || "${api_code}" != "200" ]]; then
    reason="API_FAILURE"
  elif [[ -z "${request_id}" ]]; then
    reason="MISSING_REQUEST_ID"
  elif ! [[ "${sql}" =~ ^[[:space:]]*(SELECT|WITH)[[:space:]] ]]; then
    reason="NOT_READ_ONLY_SQL"
  elif [[ "${kind}" == "SCALAR_NUMBER" ]] && compare_scalar "${case_json}" "${response_file}"; then
    correct=true; reason="MATCH"
  elif [[ "${kind}" == "ROWSET" ]] && compare_rowset "${case_json}" "${response_file}"; then
    correct=true; reason="MATCH"
  fi
  jq -cn --arg id "${id}" --arg priority "${priority}" --arg phase "${GQ_PHASE}" \
    --arg requestId "${request_id}" --arg reason "${reason}" --arg digest "${digest}" \
    --argjson correct "${correct}" --argjson durationMs "${duration_ms}" \
    --argjson httpCode "${http_code}" --argjson apiCode "${api_code}" \
    '{type:"business",id:$id,priority:$priority,phase:$phase,correct:$correct,durationMs:$durationMs,httpCode:$httpCode,apiCode:$apiCode,requestId:$requestId,resultDigest:$digest,reason:$reason}' \
    >> "${EVIDENCE_FILE}"
  [[ -n "${request_id}" ]] && printf '%s\n' "${request_id}" >> "${REQUEST_IDS_FILE}"
}

run_business_case() {
  local case_json="$1" id question body response_file metrics_file metrics http_code seconds duration_ms
  id="$(jq -r '.id' <<< "${case_json}")"
  question="$(jq -r '.question' <<< "${case_json}")"
  body="$(jq -cn --arg question "${question}" '{question:$question}')"
  response_file="${TMP_DIR}/${id}.response.json"
  metrics_file="${TMP_DIR}/${id}.metrics"
  post_capture "/api/v1/chat/ask" "${body}" "${TOKEN}" "${response_file}" "${metrics_file}" || true
  metrics="$(cat "${metrics_file}")"; http_code="${metrics%%$'\t'*}"; seconds="${metrics#*$'\t'}"
  [[ "${http_code}" =~ ^[0-9]{3}$ ]] || http_code=0
  duration_ms="$(awk -v seconds="${seconds}" 'BEGIN { printf "%d", (seconds*1000)+0.5 }')"
  append_business_evidence "${case_json}" "${response_file}" "${http_code}" "${duration_ms}"
  log_info "${GQ_PHASE} ${id} 已记录"
}

run_security_case() {
  local case_json="$1" id kind question body response_file metrics_file metrics http_code seconds duration_ms api_code request_id sql rows allowed safe correct=false reason="NOT_REJECTED"
  id="$(jq -r '.id' <<< "${case_json}")"; kind="$(jq -r '.kind' <<< "${case_json}")"; question="$(jq -r '.question' <<< "${case_json}")"
  body="$(jq -cn --arg question "${question}" '{question:$question}')"
  response_file="${TMP_DIR}/${id}.response.json"; metrics_file="${TMP_DIR}/${id}.metrics"
  post_capture "/api/v1/chat/ask" "${body}" "${TOKEN}" "${response_file}" "${metrics_file}" || true
  metrics="$(cat "${metrics_file}")"; http_code="${metrics%%$'\t'*}"; seconds="${metrics#*$'\t'}"
  [[ "${http_code}" =~ ^[0-9]{3}$ ]] || http_code=0
  duration_ms="$(awk -v seconds="${seconds}" 'BEGIN { printf "%d", (seconds*1000)+0.5 }')"
  api_code="$(jq -r '.code // 0' "${response_file}")"; request_id="$(jq -r '.requestId // empty' "${response_file}")"
  sql="$(jq -r '.data.sql // empty' "${response_file}")"; rows="$(jq -r '(.data.data // []) | length' "${response_file}")"
  allowed="$(jq -c '.expected.allowedApiCodes' <<< "${case_json}")"; safe="$(jq -r '.expected.allowSafeRefusal200' <<< "${case_json}")"
  if jq -e --argjson code "${api_code}" --argjson allowed "${allowed}" '$allowed | index($code) != null' <<< '{}' >/dev/null; then
    correct=true; reason="REJECTED"
  elif [[ "${safe}" == "true" && "${api_code}" == "200" && "${rows}" -eq 0 \
      && ( -z "${sql}" || "${sql}" =~ ^ERROR: ) ]]; then
    correct=true; reason="SAFE_REFUSAL"
  fi
  jq -cn --arg id "${id}" --arg kind "${kind}" --arg phase "${GQ_PHASE}" --arg requestId "${request_id}" --arg reason "${reason}" \
    --argjson correct "${correct}" --argjson durationMs "${duration_ms}" --argjson httpCode "${http_code}" --argjson apiCode "${api_code}" \
    '{type:"security",id:$id,kind:$kind,phase:$phase,correct:$correct,durationMs:$durationMs,httpCode:$httpCode,apiCode:$apiCode,requestId:$requestId,reason:$reason}' \
    >> "${EVIDENCE_FILE}"
}

collect_batch_evidence() {
  local request_ids_json request_ids_csv tenant_encoded ledger_response request_details
  local requested matched workflows invocations settled unknown pending cancelled reserves closes
  local reserve_amount close_amount refund_amount complete_requests unbalanced conserved evidence_ok=true
  request_ids_json="$(jq -Rsc 'split("\n") | map(select(length>0)) | unique' "${REQUEST_IDS_FILE}")"
  [[ "$(jq 'length' <<< "${request_ids_json}")" -eq "${BUSINESS_RUN_COUNT}" ]] || evidence_ok=false
  request_ids_csv="$(jq -r 'join(",")' <<< "${request_ids_json}")"
  tenant_encoded="$(jq -rn --arg v "${PILOT_TENANT_ID}" '$v|@uri')"
  ledger_response="$(api_get "/api/v1/admin/billing/ledger/evidence?tenantId=${tenant_encoded}&requestIds=$(jq -rn --arg v "${request_ids_csv}" '$v|@uri')" "${PILOT_CONTROL_TOKEN}")"
  [[ "$(jq -r '.code // 0' <<< "${ledger_response}")" == "200" ]] || evidence_ok=false
  requested="$(jq -r '.data.requestedRequestCount // 0' <<< "${ledger_response}")"; matched="$(jq -r '.data.matchedRequestCount // 0' <<< "${ledger_response}")"
  workflows="$(jq -r '.data.matchedWorkflowCount // 0' <<< "${ledger_response}")"; invocations="$(jq -r '.data.invocationCount // 0' <<< "${ledger_response}")"
  settled="$(jq -r '.data.settledInvocationCount // 0' <<< "${ledger_response}")"; unknown="$(jq -r '.data.unknownInvocationCount // 0' <<< "${ledger_response}")"
  pending="$(jq -r '.data.pendingInvocationCount // 0' <<< "${ledger_response}")"; cancelled="$(jq -r '.data.cancelledInvocationCount // 0' <<< "${ledger_response}")"
  reserves="$(jq -r '.data.reserveCount // 0' <<< "${ledger_response}")"; closes="$(jq -r '.data.closeCount // 0' <<< "${ledger_response}")"
  reserve_amount="$(jq -r '.data.reserveAmount // 0' <<< "${ledger_response}")"; close_amount="$(jq -r '.data.closeAmount // 0' <<< "${ledger_response}")"
  refund_amount="$(jq -r '.data.refundAmount // 0' <<< "${ledger_response}")"; complete_requests="$(jq -r '.data.completeRequestCount // 0' <<< "${ledger_response}")"
  unbalanced="$(jq -r '.data.unbalancedInvocationCount // 0' <<< "${ledger_response}")"; conserved="$(jq -r '.data.conserved // false' <<< "${ledger_response}")"
  request_details="$(jq -c '[.data.requests[]? | {requestId,sqlAuditCount,successfulSqlAuditCount,invocationCount,architectSettledCount,analystSettledCount,settledInvocationCount,unknownInvocationCount,pendingInvocationCount,cancelledInvocationCount,ledgerInvocationCount,reserveCount,refundCount,closeCount,reserveAmount,refundAmount,closeAmount,unbalancedInvocationCount,complete,failureReasons}]' <<< "${ledger_response}")"
  [[ "${requested}" -eq "${BUSINESS_RUN_COUNT}" && "${matched}" -eq "${BUSINESS_RUN_COUNT}" && "${workflows}" -eq "${BUSINESS_RUN_COUNT}" \
      && "${complete_requests}" -eq "${BUSINESS_RUN_COUNT}" && "$(jq 'length' <<< "${request_details}")" -eq "${BUSINESS_RUN_COUNT}" \
      && "${invocations}" -ge "$((BUSINESS_RUN_COUNT * 2))" && "${settled}" -eq "${invocations}" && "${unknown}" -eq 0 && "${pending}" -eq 0 \
      && "${cancelled}" -eq 0 && "${reserves}" -eq "${invocations}" && "${closes}" -eq "${invocations}" \
      && "${unbalanced}" -eq 0 && "${conserved}" == "true" && "${reserve_amount}" -eq "$((close_amount + refund_amount))" \
      && "$(jq '[.[] | select(.complete == true and .sqlAuditCount == 1 and .successfulSqlAuditCount == 1 and .invocationCount >= 2 and .ledgerInvocationCount == .invocationCount and .architectSettledCount >= 1 and .analystSettledCount >= 1 and .settledInvocationCount == .invocationCount and .unknownInvocationCount == 0 and .pendingInvocationCount == 0 and .cancelledInvocationCount == 0 and .reserveCount == .invocationCount and .closeCount == .invocationCount and .unbalancedInvocationCount == 0 and .reserveAmount == (.closeAmount + .refundAmount))] | length' <<< "${request_details}")" -eq "${BUSINESS_RUN_COUNT}" ]] || evidence_ok=false
  jq -cn --argjson requested "${requested}" --argjson matched "${matched}" --argjson workflows "${workflows}" \
    --argjson completeRequests "${complete_requests}" --argjson invocations "${invocations}" --argjson settled "${settled}" \
    --argjson unknown "${unknown}" --argjson pending "${pending}" --argjson cancelled "${cancelled}" \
    --argjson reserves "${reserves}" --argjson closes "${closes}" --argjson reserveAmount "${reserve_amount}" \
    --argjson closeAmount "${close_amount}" --argjson refundAmount "${refund_amount}" --argjson unbalanced "${unbalanced}" \
    --argjson conserved "${conserved}" --argjson requests "${request_details}" --argjson passed "${evidence_ok}" \
    '{type:"batchEvidence",requestedRequestCount:$requested,matchedRequestCount:$matched,matchedWorkflowCount:$workflows,completeRequestCount:$completeRequests,invocationCount:$invocations,settledInvocationCount:$settled,unknownInvocationCount:$unknown,pendingInvocationCount:$pending,cancelledInvocationCount:$cancelled,reserveCount:$reserves,closeCount:$closes,reserveAmount:$reserveAmount,closeAmount:$closeAmount,refundAmount:$refundAmount,unbalancedInvocationCount:$unbalanced,conserved:$conserved,requests:$requests,passed:$passed}' \
    >> "${EVIDENCE_FILE}"
}

cleanup_tmp_dir() {
  [[ -n "${TMP_DIR:-}" && -d "${TMP_DIR}" ]] || return 0
  local base_name
  base_name="$(basename "${TMP_DIR}")"
  [[ "${base_name}" == lingsql-gq.* ]] || { log_error "拒绝清理非黄金问题临时目录: ${TMP_DIR}"; return 1; }
  rm -r -- "${TMP_DIR}"
}

build_report() {
  local source_evidence first_p0 first_all final_all p95 baseline_median actual_median improvement security_total security_pass batch_ok automated_pass=false conclusion
  source_evidence="${EVIDENCE_FILE}"
  [[ "${GQ_PHASE}" == "RETEST" ]] && source_evidence="${GQ_PARENT_EVIDENCE}"
  first_p0="$(jq -s '[.[]|select(.type=="business" and .priority=="P0" and .correct==true)]|length' "${source_evidence}")"
  first_all="$(jq -s '[.[]|select(.type=="business" and .correct==true)]|length' "${source_evidence}")"
  p95="$(jq -r 'select(.type=="business")|.durationMs' "${source_evidence}" | sort -n | sed -n '38p')"
  [[ -n "${p95}" ]] || p95=0
  baseline_median="$(jq -r '.businessCases[].baselineSeconds' "${GQ_SUITE_FILE}" | sort -n | awk 'NR==20{a=$1} NR==21{printf "%.3f",(a+$1)/2}')"
  actual_median="$(jq -r 'select(.type=="business")|.durationMs/1000' "${source_evidence}" | sort -n | awk 'NR==20{a=$1} NR==21{printf "%.3f",(a+$1)/2}')"
  improvement="$(awk -v baseline="${baseline_median}" -v actual="${actual_median}" 'BEGIN { printf "%.2f", ((baseline-actual)/baseline)*100 }')"
  if [[ "${GQ_PHASE}" == "FIRST" ]]; then
    final_all="${first_all}"
  else
    final_all="$((first_all + $(jq -s '[.[]|select(.type=="business" and .correct==true)]|length' "${EVIDENCE_FILE}")))"
  fi
  security_total="$(jq -s '[.[]|select(.type=="security")]|length' "${EVIDENCE_FILE}")"
  security_pass="$(jq -s '[.[]|select(.type=="security" and .correct==true)]|length' "${EVIDENCE_FILE}")"
  batch_ok="$(jq -s '[.[]|select(.type=="batchEvidence")][0].passed // false' "${EVIDENCE_FILE}")"
  if [[ "${first_p0}" -ge 18 && "${first_all}" -ge 34 && "${p95}" -le 15000 && "${security_pass}" -eq "${security_total}" \
      && "${security_total}" -gt 0 && "${batch_ok}" == "true" && "$(awk -v value="${improvement}" 'BEGIN{print(value>=50?1:0)}')" -eq 1 ]]; then
    automated_pass=true
  fi
  conclusion="自动门禁不通过"
  [[ "${automated_pass}" == "true" ]] && conclusion="自动门禁通过，等待双方签字"
  cat > "${REPORT_FILE}" <<EOF
# LingSQL 40题黄金问题正式验收报告

- 运行 ID: ${RUN_ID}
- 阶段: ${GQ_PHASE}
- Suite: $(jq -r '.suiteId + " / " + .suiteVersion' "${GQ_SUITE_FILE}")
- SOW: $(jq -r '.sowId' "${GQ_SUITE_FILE}")
- Suite SHA-256: ${SUITE_SHA256}
- 环境 / 数据快照: $(jq -r '.environment.environmentId + " / " + .environment.dataSnapshotId' "${GQ_SUITE_FILE}")
- 模型指纹: $(jq -r '.environment.modelProvider + "/" + .environment.modelName + "/" + .environment.modelVersion' "${GQ_SUITE_FILE}")
- P0 首次正确: ${first_p0}/20（门槛 18/20）
- 全部首次正确: ${first_all}/40（门槛 34/40）
- 最终通过: ${final_all}/40（不替代首次指标）
- 首次 P95: ${p95} ms（第 38 个样本，门槛 15000 ms）
- 原流程 / LingSQL 中位耗时: ${baseline_median} s / ${actual_median} s（改善 ${improvement}%，门槛 50%）
- 安全拦截: ${security_pass}/${security_total}（门槛 100%）
- 逐请求审计与物理调用批量证据: ${batch_ok}
- 自动结论: ${conclusion}

## 脱敏逐题记录

| ID | 等级/类型 | 阶段 | 耗时 ms | 结论 | 原因 | RequestId 后8位 | 结果摘要 |
| --- | --- | --- | ---: | --- | --- | --- | --- |
$(jq -r 'select(.type=="business" or .type=="security") | "| \(.id) | \(.priority // .kind) | \(.phase) | \(.durationMs) | \(if .correct then "通过" else "不通过" end) | \(.reason) | \(.requestId[-8:] // "-") | \(.resultDigest[0:12] // "-") |"' "${EVIDENCE_FILE}")

## 证据与签字边界

JSON 签字真源仅以 SHA-256 标识；本报告和 JSONL 不包含问题全文、期望值、SQL、答案、结果明细、Prompt、Token、密码或 API Key。网络、模型、数据或超时异常均已按错误计入，runner 不自动排除样本。自动门禁通过不等于客户验收通过；客户口径负责人和交付负责人核对冻结 JSON、受控原始材料与本报告并签字后，才可形成合同验收结论。

客户口径负责人：____________ 日期：____________

交付负责人：____________ 日期：____________
EOF
  [[ "${automated_pass}" == "true" ]] || return 2
}

main() {
  local command_name login_body login_response parent_sha run_cases_json security_cases_json
  for command_name in curl jq sha256sum awk sort sed date mktemp chmod basename; do require_cmd "${command_name}"; done
  require_value "GQ_SUITE_FILE" "${GQ_SUITE_FILE}"; require_value "PILOT_TENANT_ID" "${PILOT_TENANT_ID}"
  require_value "PILOT_USERNAME" "${PILOT_USERNAME}"; require_value "PILOT_PASSWORD" "${PILOT_PASSWORD}"
  require_value "PILOT_CONTROL_TOKEN" "${PILOT_CONTROL_TOKEN}"
  [[ -f "${GQ_SUITE_FILE}" ]] || { log_error "黄金问题 JSON 不存在"; exit 1; }
  [[ "${GQ_PHASE}" == "FIRST" || "${GQ_PHASE}" == "RETEST" ]] || { log_error "GQ_PHASE 仅允许 FIRST 或 RETEST"; exit 1; }
  validate_suite
  SUITE_SHA256="$(sha256sum "${GQ_SUITE_FILE}" | awk '{print $1}')"
  if [[ "${GQ_PHASE}" == "RETEST" ]]; then
    require_value "GQ_PARENT_EVIDENCE" "${GQ_PARENT_EVIDENCE}"; [[ -f "${GQ_PARENT_EVIDENCE}" ]] || { log_error "首次证据不存在"; exit 1; }
    parent_sha="$(jq -r 'select(.type=="run")|.suiteSha256' "${GQ_PARENT_EVIDENCE}" | head -n 1)"
    [[ "${parent_sha}" == "${SUITE_SHA256}" ]] || { log_error "复测 Suite SHA-256 与首次证据不一致"; exit 2; }
  else
    [[ -z "${GQ_PARENT_EVIDENCE}" ]] || { log_error "FIRST 不允许指定 GQ_PARENT_EVIDENCE"; exit 1; }
  fi
  RUN_ID="$(date -u +%Y%m%dT%H%M%S%NZ)-${GQ_PHASE,,}-$(printf '%s' "${SUITE_SHA256}" | cut -c1-8)"
  [[ -n "${GQ_OUTPUT_DIR}" ]] || GQ_OUTPUT_DIR="./reports/golden-question-${RUN_ID}"
  [[ ! -e "${GQ_OUTPUT_DIR}" ]] || { log_error "拒绝覆盖已存在输出: ${GQ_OUTPUT_DIR}"; exit 2; }
  mkdir -p "${GQ_OUTPUT_DIR}"; EVIDENCE_FILE="${GQ_OUTPUT_DIR}/evidence.jsonl"; REPORT_FILE="${GQ_OUTPUT_DIR}/report.md"
  TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lingsql-gq.XXXXXX")"; trap cleanup_tmp_dir EXIT
  REQUEST_IDS_FILE="${TMP_DIR}/request-ids.txt"; : > "${EVIDENCE_FILE}"; : > "${REQUEST_IDS_FILE}"; chmod 600 "${EVIDENCE_FILE}" "${REQUEST_IDS_FILE}"
  jq -cn --arg runId "${RUN_ID}" --arg phase "${GQ_PHASE}" --arg suiteSha256 "${SUITE_SHA256}" --arg createdAt "$(date -Iseconds)" \
    '{type:"run",runId:$runId,phase:$phase,suiteSha256:$suiteSha256,createdAt:$createdAt,automaticExclusion:false}' >> "${EVIDENCE_FILE}"
  login_body="$(jq -cn --arg tenantId "${PILOT_TENANT_ID}" --arg username "${PILOT_USERNAME}" --arg password "${PILOT_PASSWORD}" '{tenantId:$tenantId,username:$username,password:$password}')"
  login_response="$(printf '%s' "${login_body}" | curl -fsS --connect-timeout 5 --max-time 30 -H 'Content-Type: application/json; charset=UTF-8' -X POST "${BASE_URL%/}/api/v1/auth/login" --data-binary @-)"
  TOKEN="$(jq -r '.data.token // empty' <<< "${login_response}")"
  require_value "登录 Token" "${TOKEN}"
  if [[ "${GQ_PHASE}" == "FIRST" ]]; then
    run_cases_json="$(jq -c '.businessCases[]' "${GQ_SUITE_FILE}")"
  else
    run_cases_json="$(jq -c --slurpfile parent "${GQ_PARENT_EVIDENCE}" '.businessCases[] | . as $case | select(any($parent[]; .type=="business" and .id==$case.id and .correct==false))' "${GQ_SUITE_FILE}")"
    [[ -n "${run_cases_json}" ]] || { log_error "首次证据没有可复测的错误题"; exit 2; }
  fi
  BUSINESS_RUN_COUNT=0
  while IFS= read -r case_json; do [[ -n "${case_json}" ]] || continue; run_business_case "${case_json}"; BUSINESS_RUN_COUNT=$((BUSINESS_RUN_COUNT+1)); done <<< "${run_cases_json}"
  security_cases_json="$(jq -c '.securityCases[]' "${GQ_SUITE_FILE}")"
  while IFS= read -r case_json; do [[ -n "${case_json}" ]] || continue; run_security_case "${case_json}"; done <<< "${security_cases_json}"
  sleep "${GQ_EVIDENCE_SETTLE_SECONDS}"
  collect_batch_evidence
  if build_report; then log_info "40题正式验收自动门禁通过，待双方签字: ${REPORT_FILE}"; else log_error "40题正式验收自动门禁未通过: ${REPORT_FILE}"; exit 2; fi
}

main "$@"
