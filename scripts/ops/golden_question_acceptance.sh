#!/usr/bin/env bash
# LingSQL 40题黄金问题 runner。客户验收与内部演练必须显式分类，禁止混用证据。

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8000}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ops/lib/acceptance_auth.sh
source "${SCRIPT_DIR}/lib/acceptance_auth.sh"
PILOT_TENANT_ID="${PILOT_TENANT_ID:-}"
PILOT_USERNAME="${PILOT_USERNAME:-}"
PILOT_PASSWORD="${PILOT_PASSWORD:-}"
PILOT_CONTROL_TOKEN="${PILOT_CONTROL_TOKEN:-}"
PILOT_CONTROL_TENANT_ID="${PILOT_CONTROL_TENANT_ID:-}"
PILOT_CONTROL_USERNAME="${PILOT_CONTROL_USERNAME:-}"
PILOT_CONTROL_PASSWORD="${PILOT_CONTROL_PASSWORD:-}"
GQ_SUITE_FILE="${GQ_SUITE_FILE:-}"
GQ_EXPECTED_SUITE_SHA256="${GQ_EXPECTED_SUITE_SHA256:-}"
GQ_PHASE="${GQ_PHASE:-FIRST}"
GQ_EVIDENCE_PURPOSE="${GQ_EVIDENCE_PURPOSE:-}"
GQ_PARENT_EVIDENCE="${GQ_PARENT_EVIDENCE:-}"
GQ_PARENT_EVIDENCE_SHA256="${GQ_PARENT_EVIDENCE_SHA256:-}"
GQ_OUTPUT_DIR="${GQ_OUTPUT_DIR:-}"
GQ_EVIDENCE_SETTLE_SECONDS="${GQ_EVIDENCE_SETTLE_SECONDS:-10}"
GQ_COMPARATOR_JAR="${GQ_COMPARATOR_JAR:-./dist/lingsql-engine.jar}"

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

api_post_json() {
  local path="$1" body="$2" token="$3"
  printf '%s' "${body}" | curl -fsS --connect-timeout 5 --max-time 90 \
    -H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${PILOT_TENANT_ID}" \
    -H 'Content-Type: application/json; charset=UTF-8' \
    -X POST "${BASE_URL%/}${path}" --data-binary @-
}

open_billing_acceptance() {
  local body response
  body="$(jq -cn --arg tenantId "${PILOT_TENANT_ID}" '{tenantId:$tenantId}')"
  response="$(api_post_json "/api/v1/admin/billing/acceptance/sessions" "${body}" "${PILOT_CONTROL_TOKEN}")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || {
    log_error "无法冻结黄金问题验收的 MySQL 余额与账本水位"
    exit 2
  }
  jq -r '.data.sessionId // empty' <<< "${response}"
}

close_billing_acceptance() {
  local request_ids_json="$1" body response
  body="$(jq -cn --argjson requestIds "${request_ids_json}" '{requestIds:$requestIds}')"
  response="$(api_post_json "/api/v1/admin/billing/acceptance/sessions/${BILLING_ACCEPTANCE_SESSION_ID}/close" "${body}" "${PILOT_CONTROL_TOKEN}")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || {
    log_error "黄金问题批次未通过 MySQL 余额与账本区间验收"
    exit 2
  }
  jq -c '.data' <<< "${response}"
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
    (.environment.releaseCommit | test("^[0-9a-f]{40}$")) and
    (.environment.modelRouteFingerprint | test("^[0-9a-f]{64}$")) and
    ([.environment.environmentId,.environment.releaseCommit,.environment.flywaySchemaVersion,
      .environment.dataSnapshotId,.environment.modelRouteFingerprint,.environment.modelProvider,
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
      ((.kind == "WRITE_SQL" and .expected.failureCode == "SECURITY_WRITE_BLOCKED") or
       (.kind == "CROSS_TENANT" and .expected.failureCode == "SECURITY_TENANT_SCOPE_BLOCKED") or
       (.kind == "OUT_OF_ALLOWLIST" and .expected.failureCode == "SECURITY_ALLOWLIST_BLOCKED")))
  ' "${GQ_SUITE_FILE}" >/dev/null || { log_error "黄金问题 JSON 结构或 40题/20-20 约束不合法"; exit 2; }
}

validate_parent_evidence() {
  jq -s -e --slurpfile suite "${GQ_SUITE_FILE}" --arg suiteSha "${SUITE_SHA256}" '
    ([.[] | select(.type == "run")] | length == 1) and
    ([.[] | select(.type == "run")][0] | .phase == "FIRST" and .suiteSha256 == $suiteSha) and
    ([.[] | select(.type == "business")] as $business |
      ($business | length == 40) and
      ([$business[].id] | length == (unique | length)) and
      ([$business[].id] | sort) == ([$suite[0].businessCases[].id] | sort) and
      all($business[]; .phase == "FIRST" and (.correct | type == "boolean"))) and
    ([.[] | select(.type == "security")] as $security |
      ($security | length == ($suite[0].securityCases | length)) and
      ([$security[].id] | length == (unique | length)) and
      ([$security[].id] | sort) == ([$suite[0].securityCases[].id] | sort) and
      all($security[]; .phase == "FIRST" and (.correct | type == "boolean"))) and
    ([.[] | select(.type == "batchEvidence")] | length == 1 and .[0].passed == true)
  ' "${GQ_PARENT_EVIDENCE}" >/dev/null || {
    log_error "首次证据结构不完整、存在重复题目或批量证据未通过"
    exit 2
  }
}

validate_runtime_fingerprint() {
  local response expected_commit expected_schema expected_snapshot expected_route
  local actual_commit actual_schema actual_snapshot actual_route expected_provider expected_model expected_version model_match
  response="$(api_get "/api/v1/admin/runtime/fingerprint?modelTenantId=${PILOT_TENANT_ID}" "${PILOT_CONTROL_TOKEN}")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || { log_error "无法读取运行候选指纹"; exit 2; }
  expected_commit="$(jq -r '.environment.releaseCommit' "${GQ_SUITE_FILE}")"
  expected_schema="$(jq -r '.environment.flywaySchemaVersion' "${GQ_SUITE_FILE}")"
  expected_snapshot="$(jq -r '.environment.dataSnapshotId' "${GQ_SUITE_FILE}")"
  expected_route="$(jq -r '.environment.modelRouteFingerprint' "${GQ_SUITE_FILE}")"
  expected_provider="$(jq -r '.environment.modelProvider' "${GQ_SUITE_FILE}")"
  expected_model="$(jq -r '.environment.modelName' "${GQ_SUITE_FILE}")"
  expected_version="$(jq -r '.environment.modelVersion' "${GQ_SUITE_FILE}")"
  actual_commit="$(jq -r '.data.commit // empty' <<< "${response}")"
  actual_schema="$(jq -r '.data.schemaVersion // empty' <<< "${response}")"
  actual_snapshot="$(jq -r '.data.dataSnapshotId // empty' <<< "${response}")"
  actual_route="$(jq -r '.data.modelRouteFingerprint // empty' <<< "${response}")"
  model_match="$(jq --arg provider "${expected_provider}" --arg model "${expected_model}" --arg version "${expected_version}" \
    '[.data.activeModels[]? | select(.providerName == $provider and .modelName == $model and .modelVersion == $version)] | length' \
    <<< "${response}")"
  [[ "${actual_commit}" == "${expected_commit}" ]] || { log_error "运行提交与冻结 Suite 不一致"; exit 2; }
  [[ "${actual_schema}" == "${expected_schema}" ]] || { log_error "Flyway Schema 与冻结 Suite 不一致"; exit 2; }
  [[ "${actual_snapshot}" == "${expected_snapshot}" && "${actual_snapshot}" != "UNSET" ]] || { log_error "数据快照与冻结 Suite 不一致或未设置"; exit 2; }
  [[ "${actual_route}" == "${expected_route}" ]] || { log_error "模型路由指纹与冻结 Suite 不一致"; exit 2; }
  [[ "${model_match}" -gt 0 ]] || { log_error "冻结模型不在当前激活路由中"; exit 2; }
  jq -cn --arg commit "${actual_commit}" --arg schemaVersion "${actual_schema}" \
    --arg dataSnapshotId "${actual_snapshot}" --arg modelRouteFingerprint "${actual_route}" \
    '{type:"runtimeFingerprint",commit:$commit,schemaVersion:$schemaVersion,dataSnapshotId:$dataSnapshotId,modelRouteFingerprint:$modelRouteFingerprint,matched:true}' \
    >> "${EVIDENCE_FILE}"
}

compare_business_result() {
  local case_id="$1" response_file="$2" exit_code
  run_result_comparator "${GQ_SUITE_FILE}" "${case_id}" "${response_file}" && return 0
  exit_code=$?
  if [[ "${exit_code}" -eq 2 ]]; then
    log_error "验收比较器拒绝了 Suite 或响应结构: ${case_id}"
    exit 2
  fi
  return "${exit_code}"
}

run_result_comparator() {
  java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
    -Dloader.main=com.lingsql.acceptance.GoldenQuestionResultComparator \
    -cp "${GQ_COMPARATOR_JAR}" org.springframework.boot.loader.launch.PropertiesLauncher "$@"
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
  digest="$(sha256sum "${response_file}" | awk '{print $1}')"
  if [[ "${http_code}" != "200" || "${api_code}" != "200" ]]; then
    reason="API_FAILURE"
  elif [[ -z "${request_id}" ]]; then
    reason="MISSING_REQUEST_ID"
  elif ! [[ "${sql}" =~ ^[[:space:]]*(SELECT|WITH)[[:space:]] ]]; then
    reason="NOT_READ_ONLY_SQL"
  elif [[ "${kind}" == "SCALAR_NUMBER" || "${kind}" == "ROWSET" ]] \
      && compare_business_result "${id}" "${response_file}"; then
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
  local case_json="$1" id kind question body response_file metrics_file metrics http_code seconds duration_ms api_code request_id sql data_empty expected_failure actual_failure correct=false reason="NOT_REJECTED"
  id="$(jq -r '.id' <<< "${case_json}")"; kind="$(jq -r '.kind' <<< "${case_json}")"; question="$(jq -r '.question' <<< "${case_json}")"
  body="$(jq -cn --arg question "${question}" '{question:$question}')"
  response_file="${TMP_DIR}/${id}.response.json"; metrics_file="${TMP_DIR}/${id}.metrics"
  post_capture "/api/v1/chat/ask" "${body}" "${TOKEN}" "${response_file}" "${metrics_file}" || true
  metrics="$(cat "${metrics_file}")"; http_code="${metrics%%$'\t'*}"; seconds="${metrics#*$'\t'}"
  [[ "${http_code}" =~ ^[0-9]{3}$ ]] || http_code=0
  duration_ms="$(awk -v seconds="${seconds}" 'BEGIN { printf "%d", (seconds*1000)+0.5 }')"
  api_code="$(jq -r '.code // 0' "${response_file}")"; request_id="$(jq -r '.requestId // empty' "${response_file}")"
  sql="$(jq -r '.data.sql // empty' "${response_file}")"
  data_empty="$(jq -r '
    if .data == null then true
    elif (.data | type) != "object" then false
    elif .data.data == null then true
    else ((.data.data | type) == "array" and (.data.data | length) == 0)
    end
  ' "${response_file}")"
  expected_failure="$(jq -r '.expected.failureCode' <<< "${case_json}")"
  actual_failure="$(jq -r '.data.failureCode // empty' "${response_file}")"
  if [[ "${http_code}" == "200" && "${api_code}" == "200" && -n "${request_id}" \
      && "${sql}" =~ ^ERROR: && "${data_empty}" == "true" && "${actual_failure}" == "${expected_failure}" ]]; then
    correct=true; reason="${actual_failure}"
  fi
  jq -cn --arg id "${id}" --arg kind "${kind}" --arg phase "${GQ_PHASE}" --arg requestId "${request_id}" --arg reason "${reason}" \
    --argjson correct "${correct}" --argjson durationMs "${duration_ms}" --argjson httpCode "${http_code}" --argjson apiCode "${api_code}" \
    '{type:"security",id:$id,kind:$kind,phase:$phase,correct:$correct,durationMs:$durationMs,httpCode:$httpCode,apiCode:$apiCode,requestId:$requestId,reason:$reason}' \
    >> "${EVIDENCE_FILE}"
}

collect_business_batch_evidence() {
  local request_ids_json request_ids_csv tenant_encoded ledger_response request_details
  local requested matched workflows invocations settled unknown pending cancelled reserves closes
  local reserve_amount close_amount refund_amount complete_requests unbalanced conserved evidence_ok=true
  local acceptance_receipt acceptance_receipt_valid acceptance_receipt_hash balance_before balance_after interval_net_amount
  local identified_invocations route_fingerprints identity_match_count expected_provider expected_model expected_version expected_route
  request_ids_json="$(jq -Rsc 'split("\n") | map(select(length>0)) | unique' "${REQUEST_IDS_FILE}")"
  [[ "$(jq 'length' <<< "${request_ids_json}")" -eq "${BUSINESS_RUN_COUNT}" ]] || evidence_ok=false
  request_ids_csv="$(jq -r 'join(",")' <<< "${request_ids_json}")"
  acceptance_receipt="$(close_billing_acceptance "${request_ids_json}")"
  acceptance_receipt_valid="$(jq -r --argjson expectedCount "${BUSINESS_RUN_COUNT}" '
    (.requestIds | length) == $expectedCount and
    .unrelatedEventCount == 0 and
    .cancelledInvocationCount == 0 and
    .settledInvocationCount == .invocationCount and
    .scopeNetAmount == .intervalNetAmount and
    (.balanceBefore - .balanceAfter) == .intervalNetAmount and
    (.receiptHash | test("^[0-9a-f]{64}$"))
  ' <<< "${acceptance_receipt}")"
  [[ "${acceptance_receipt_valid}" == "true" ]] || evidence_ok=false
  acceptance_receipt_hash="$(jq -r '.receiptHash // empty' <<< "${acceptance_receipt}")"
  balance_before="$(jq -r '.balanceBefore // 0' <<< "${acceptance_receipt}")"
  balance_after="$(jq -r '.balanceAfter // 0' <<< "${acceptance_receipt}")"
  interval_net_amount="$(jq -r '.intervalNetAmount // 0' <<< "${acceptance_receipt}")"
  tenant_encoded="$(jq -rn --arg v "${PILOT_TENANT_ID}" '$v|@uri')"
  ledger_response="$(api_get "/api/v1/admin/billing/ledger/evidence?tenantId=${tenant_encoded}&requestIds=$(jq -rn --arg v "${request_ids_csv}" '$v|@uri')" "${PILOT_CONTROL_TOKEN}")"
  [[ "$(jq -r '.code // 0' <<< "${ledger_response}")" == "200" ]] || evidence_ok=false
  expected_provider="$(jq -r '.environment.modelProvider' "${GQ_SUITE_FILE}")"
  expected_model="$(jq -r '.environment.modelName' "${GQ_SUITE_FILE}")"
  expected_version="$(jq -r '.environment.modelVersion' "${GQ_SUITE_FILE}")"
  expected_route="$(jq -r '.environment.modelRouteFingerprint' "${GQ_SUITE_FILE}")"
  requested="$(jq -r '.data.requestedRequestCount // 0' <<< "${ledger_response}")"; matched="$(jq -r '.data.matchedRequestCount // 0' <<< "${ledger_response}")"
  workflows="$(jq -r '.data.matchedWorkflowCount // 0' <<< "${ledger_response}")"; invocations="$(jq -r '.data.invocationCount // 0' <<< "${ledger_response}")"
  settled="$(jq -r '.data.settledInvocationCount // 0' <<< "${ledger_response}")"; unknown="$(jq -r '.data.unknownInvocationCount // 0' <<< "${ledger_response}")"
  pending="$(jq -r '.data.pendingInvocationCount // 0' <<< "${ledger_response}")"; cancelled="$(jq -r '.data.cancelledInvocationCount // 0' <<< "${ledger_response}")"
  reserves="$(jq -r '.data.reserveCount // 0' <<< "${ledger_response}")"; closes="$(jq -r '.data.closeCount // 0' <<< "${ledger_response}")"
  reserve_amount="$(jq -r '.data.reserveAmount // 0' <<< "${ledger_response}")"; close_amount="$(jq -r '.data.closeAmount // 0' <<< "${ledger_response}")"
  refund_amount="$(jq -r '.data.refundAmount // 0' <<< "${ledger_response}")"; complete_requests="$(jq -r '.data.completeRequestCount // 0' <<< "${ledger_response}")"
  unbalanced="$(jq -r '.data.unbalancedInvocationCount // 0' <<< "${ledger_response}")"; conserved="$(jq -r '.data.conserved // false' <<< "${ledger_response}")"
  identified_invocations="$(jq -r '.data.identifiedInvocationCount // 0' <<< "${ledger_response}")"
  route_fingerprints="$(jq -c '.data.routeFingerprints // []' <<< "${ledger_response}")"
  identity_match_count="$(jq -r --arg provider "${expected_provider}" --arg model "${expected_model}" \
    --arg version "${expected_version}" --arg route "${expected_route}" \
    '[.data.requests[]?.modelIdentities[]? | select(
      .identityComplete == true and
      ((.providerName // "") | ascii_downcase) == ($provider | ascii_downcase) and
      ((.modelName // "") | ascii_downcase) == ($model | ascii_downcase) and
      .modelVersion == $version and .routeFingerprint == $route
    )] | length' <<< "${ledger_response}")"
  request_details="$(jq -c '[.data.requests[]? | {requestId,sqlAuditCount,successfulSqlAuditCount,invocationCount,architectSettledCount,analystSettledCount,settledInvocationCount,unknownInvocationCount,pendingInvocationCount,cancelledInvocationCount,identifiedInvocationCount,modelIdentities,ledgerInvocationCount,reserveCount,refundCount,closeCount,reserveAmount,refundAmount,closeAmount,unbalancedInvocationCount,complete,failureReasons}]' <<< "${ledger_response}")"
  [[ "${requested}" -eq "${BUSINESS_RUN_COUNT}" && "${matched}" -eq "${BUSINESS_RUN_COUNT}" && "${workflows}" -eq "${BUSINESS_RUN_COUNT}" \
      && "${complete_requests}" -eq "${BUSINESS_RUN_COUNT}" && "$(jq 'length' <<< "${request_details}")" -eq "${BUSINESS_RUN_COUNT}" \
      && "${invocations}" -ge "$((BUSINESS_RUN_COUNT * 2))" && "${settled}" -eq "${invocations}" && "${unknown}" -eq 0 && "${pending}" -eq 0 \
      && "${identified_invocations}" -eq "${invocations}" && "${identity_match_count}" -eq "${invocations}" \
      && "$(jq --arg route "${expected_route}" 'length == 1 and .[0] == $route' <<< "${route_fingerprints}")" == "true" \
      && "${cancelled}" -eq 0 && "${reserves}" -eq "${invocations}" && "${closes}" -eq "${invocations}" \
      && "${unbalanced}" -eq 0 && "${conserved}" == "true" && "${reserve_amount}" -eq "$((close_amount + refund_amount))" \
      && "$(jq --arg provider "${expected_provider}" --arg model "${expected_model}" --arg version "${expected_version}" --arg route "${expected_route}" \
        '[.[] | select(.complete == true and .sqlAuditCount == 1 and .successfulSqlAuditCount == 1 and .invocationCount >= 2 and .ledgerInvocationCount == .invocationCount and .architectSettledCount >= 1 and .analystSettledCount >= 1 and .settledInvocationCount == .invocationCount and .unknownInvocationCount == 0 and .pendingInvocationCount == 0 and .cancelledInvocationCount == 0 and .identifiedInvocationCount == .invocationCount and ([.modelIdentities[]? | select(.identityComplete == true and ((.providerName // "") | ascii_downcase) == ($provider | ascii_downcase) and ((.modelName // "") | ascii_downcase) == ($model | ascii_downcase) and .modelVersion == $version and .routeFingerprint == $route)] | length) == .invocationCount and .reserveCount == .invocationCount and .closeCount == .invocationCount and .unbalancedInvocationCount == 0 and .reserveAmount == (.closeAmount + .refundAmount))] | length' <<< "${request_details}")" -eq "${BUSINESS_RUN_COUNT}" ]] || evidence_ok=false
  jq -cn --argjson requested "${requested}" --argjson matched "${matched}" --argjson workflows "${workflows}" \
    --argjson completeRequests "${complete_requests}" --argjson invocations "${invocations}" --argjson settled "${settled}" \
    --argjson unknown "${unknown}" --argjson pending "${pending}" --argjson cancelled "${cancelled}" \
    --argjson reserves "${reserves}" --argjson closes "${closes}" --argjson reserveAmount "${reserve_amount}" \
    --argjson closeAmount "${close_amount}" --argjson refundAmount "${refund_amount}" --argjson unbalanced "${unbalanced}" \
    --argjson conserved "${conserved}" --argjson identifiedInvocations "${identified_invocations}" --argjson routeFingerprints "${route_fingerprints}" \
    --argjson requests "${request_details}" --arg acceptanceReceiptHash "${acceptance_receipt_hash}" \
    --argjson balanceBefore "${balance_before}" --argjson balanceAfter "${balance_after}" --argjson intervalNetAmount "${interval_net_amount}" \
    --argjson passed "${evidence_ok}" \
    '{type:"batchEvidence",requestedRequestCount:$requested,matchedRequestCount:$matched,matchedWorkflowCount:$workflows,completeRequestCount:$completeRequests,invocationCount:$invocations,settledInvocationCount:$settled,unknownInvocationCount:$unknown,pendingInvocationCount:$pending,cancelledInvocationCount:$cancelled,identifiedInvocationCount:$identifiedInvocations,routeFingerprints:$routeFingerprints,reserveCount:$reserves,closeCount:$closes,reserveAmount:$reserveAmount,closeAmount:$closeAmount,refundAmount:$refundAmount,unbalancedInvocationCount:$unbalanced,conserved:$conserved,billingAcceptance:{receiptHash:$acceptanceReceiptHash,balanceBefore:$balanceBefore,balanceAfter:$balanceAfter,intervalNetAmount:$intervalNetAmount},requests:$requests,passed:$passed}' \
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
  local source_evidence first_p0 first_all final_all p95 baseline_median actual_median improvement security_total security_pass batch_ok billing_receipt_hash automated_pass=false conclusion evidence_sha256 current_business_total current_business_pass
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
    current_business_total="$(jq -s '[.[]|select(.type=="business")]|length' "${EVIDENCE_FILE}")"
    current_business_pass="$(jq -s '[.[]|select(.type=="business" and .correct==true)]|length' "${EVIDENCE_FILE}")"
    final_all="$((first_all + current_business_pass))"
  fi
  security_total="$(jq -s '[.[]|select(.type=="security")]|length' "${EVIDENCE_FILE}")"
  security_pass="$(jq -s '[.[]|select(.type=="security" and .correct==true)]|length' "${EVIDENCE_FILE}")"
  batch_ok="$(jq -s '[.[]|select(.type=="batchEvidence")][0].passed // false' "${EVIDENCE_FILE}")"
  billing_receipt_hash="$(jq -rs '[.[]|select(.type=="batchEvidence")][0].billingAcceptance.receiptHash // empty' "${EVIDENCE_FILE}")"
  if [[ "${first_p0}" -ge 18 && "${first_all}" -ge 34 && "${p95}" -le 15000 && "${security_pass}" -eq "${security_total}" \
      && "${security_total}" -gt 0 && "${batch_ok}" == "true" && "$(awk -v value="${improvement}" 'BEGIN{print(value>=50?1:0)}')" -eq 1 ]]; then
    automated_pass=true
  fi
  if [[ "${GQ_PHASE}" == "RETEST" \
      && ( "${current_business_total}" -ne "$((40 - first_all))" \
        || "${current_business_pass}" -ne "${current_business_total}" \
        || "${final_all}" -ne 40 ) ]]; then
    automated_pass=false
  fi
  local report_title boundary_text signature_block
  conclusion="自动门禁不通过"
  report_title="LingSQL 40题内部演练报告"
  boundary_text="本报告仅证明虚构或内部数据环境下的工程演练结果，不构成客户验收、合同履约或商用上线证据。"
  signature_block=""
  if [[ "${GQ_EVIDENCE_PURPOSE}" == "CUSTOMER_UAT" ]]; then
    report_title="LingSQL 40题黄金问题正式验收报告"
    boundary_text="远端报告只保留脱敏汇总；JSONL 是仅存放于受控交付环境的明细证据，不得上传为公共或仓库 Artifact。网络、模型、数据或超时异常均已按错误计入，runner 不自动排除样本。自动门禁通过不等于客户验收通过；客户口径负责人和交付负责人核对冻结 JSON、受控原始材料与本报告并签字后，才可形成合同验收结论。"
    signature_block=$'客户口径负责人：____________ 日期：____________\n\n交付负责人：____________ 日期：____________'
    [[ "${automated_pass}" == "true" ]] && conclusion="自动门禁通过，等待双方签字"
  elif [[ "${automated_pass}" == "true" ]]; then
    conclusion="内部演练自动门禁通过，不构成客户验收"
  fi
  evidence_sha256="$(sha256sum "${EVIDENCE_FILE}" | awk '{print $1}')"
  cat > "${REPORT_FILE}" <<EOF
# ${report_title}

- 运行 ID: ${RUN_ID}
- 阶段: ${GQ_PHASE}
- 证据用途: ${GQ_EVIDENCE_PURPOSE}
- Suite SHA-256: ${SUITE_SHA256}
- 受控证据 SHA-256: ${evidence_sha256}
- P0 首次正确: ${first_p0}/20（门槛 18/20）
- 全部首次正确: ${first_all}/40（门槛 34/40）
- 最终通过: ${final_all}/40（不替代首次指标）
- 首次 P95: ${p95} ms（第 38 个样本，门槛 15000 ms）
- 原流程 / LingSQL 中位耗时: ${baseline_median} s / ${actual_median} s（改善 ${improvement}%，门槛 50%）
- 安全拦截: ${security_pass}/${security_total}（门槛 100%）
- 逐请求审计与物理调用批量证据: ${batch_ok}
- MySQL 余额与账本区间回执: ${billing_receipt_hash}
- 自动结论: ${conclusion}

## 脱敏逐题记录

| ID | 等级/类型 | 阶段 | 耗时 ms | 结论 | 原因 | 结果摘要 |
| --- | --- | --- | ---: | --- | --- | --- |
$(jq -r 'select(.type=="business" or .type=="security") | "| \(.id) | \(.priority // .kind) | \(.phase) | \(.durationMs) | \(if .correct then "通过" else "不通过" end) | \(.reason) | \(.resultDigest[0:12] // "-") |"' "${EVIDENCE_FILE}")

## 证据与签字边界

${boundary_text}

${signature_block}
EOF
  [[ "${automated_pass}" == "true" ]] || return 2
}

main() {
  local command_name login_body login_response parent_sha parent_run_id run_cases_json security_cases_json comparator_commit expected_commit source_suite_file source_parent_evidence
  for command_name in curl jq sha256sum awk sort sed date mktemp chmod basename java cp; do require_cmd "${command_name}"; done
  require_value "GQ_SUITE_FILE" "${GQ_SUITE_FILE}"; require_value "PILOT_TENANT_ID" "${PILOT_TENANT_ID}"
  require_value "GQ_EXPECTED_SUITE_SHA256" "${GQ_EXPECTED_SUITE_SHA256}"
  [[ "${GQ_EXPECTED_SUITE_SHA256}" =~ ^[0-9a-f]{64}$ ]] || { log_error "GQ_EXPECTED_SUITE_SHA256 格式不合法"; exit 1; }
  require_value "PILOT_USERNAME" "${PILOT_USERNAME}"; require_value "PILOT_PASSWORD" "${PILOT_PASSWORD}"
  [[ "${GQ_PHASE}" == "FIRST" || "${GQ_PHASE}" == "RETEST" ]] || { log_error "GQ_PHASE 仅允许 FIRST 或 RETEST"; exit 1; }
  [[ "${GQ_EVIDENCE_PURPOSE}" == "INTERNAL_REHEARSAL" || "${GQ_EVIDENCE_PURPOSE}" == "CUSTOMER_UAT" ]] \
    || { log_error "GQ_EVIDENCE_PURPOSE 仅允许 INTERNAL_REHEARSAL 或 CUSTOMER_UAT"; exit 1; }
  PILOT_CONTROL_TOKEN="$(acceptance_resolve_control_token \
    "${BASE_URL}" "${PILOT_CONTROL_TOKEN}" "${PILOT_CONTROL_TENANT_ID}" \
    "${PILOT_CONTROL_USERNAME}" "${PILOT_CONTROL_PASSWORD}")"
  [[ -f "${GQ_SUITE_FILE}" ]] || { log_error "黄金问题 JSON 不存在"; exit 1; }
  [[ -f "${GQ_COMPARATOR_JAR}" ]] || { log_error "验收比较器制品不存在: ${GQ_COMPARATOR_JAR}"; exit 1; }
  source_suite_file="${GQ_SUITE_FILE}"
  SUITE_SHA256="$(sha256sum "${source_suite_file}" | awk '{print $1}')"
  [[ "${SUITE_SHA256}" == "${GQ_EXPECTED_SUITE_SHA256}" ]] || { log_error "冻结 Suite SHA-256 与受保护配置不一致"; exit 2; }
  TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lingsql-gq.XXXXXX")"; trap cleanup_tmp_dir EXIT
  GQ_SUITE_FILE="${TMP_DIR}/suite.json"
  cp -- "${source_suite_file}" "${GQ_SUITE_FILE}"
  chmod 600 "${GQ_SUITE_FILE}"
  [[ "$(sha256sum "${GQ_SUITE_FILE}" | awk '{print $1}')" == "${SUITE_SHA256}" ]] || { log_error "冻结 Suite 快照复制校验失败"; exit 2; }
  validate_suite
  expected_commit="$(jq -r '.environment.releaseCommit' "${GQ_SUITE_FILE}")"
  comparator_commit="$(run_result_comparator --build-commit)"
  [[ "${comparator_commit}" == "${expected_commit}" ]] || {
    log_error "验收比较器提交与冻结 Suite 不一致: expected=${expected_commit}, actual=${comparator_commit:-<empty>}"
    exit 2
  }
  if [[ "${GQ_PHASE}" == "RETEST" ]]; then
    require_value "GQ_PARENT_EVIDENCE" "${GQ_PARENT_EVIDENCE}"; [[ -f "${GQ_PARENT_EVIDENCE}" ]] || { log_error "首次证据不存在"; exit 1; }
    require_value "GQ_PARENT_EVIDENCE_SHA256" "${GQ_PARENT_EVIDENCE_SHA256}"
    [[ "${GQ_PARENT_EVIDENCE_SHA256}" =~ ^[0-9a-f]{64}$ ]] || { log_error "GQ_PARENT_EVIDENCE_SHA256 格式不合法"; exit 1; }
    source_parent_evidence="${GQ_PARENT_EVIDENCE}"
    [[ "$(sha256sum "${source_parent_evidence}" | awk '{print $1}')" == "${GQ_PARENT_EVIDENCE_SHA256}" ]] || { log_error "首次证据 SHA-256 不匹配"; exit 2; }
    GQ_PARENT_EVIDENCE="${TMP_DIR}/parent-evidence.jsonl"
    cp -- "${source_parent_evidence}" "${GQ_PARENT_EVIDENCE}"
    chmod 600 "${GQ_PARENT_EVIDENCE}"
    [[ "$(sha256sum "${GQ_PARENT_EVIDENCE}" | awk '{print $1}')" == "${GQ_PARENT_EVIDENCE_SHA256}" ]] || { log_error "首次证据快照复制校验失败"; exit 2; }
    parent_sha="$(jq -r 'select(.type=="run")|.suiteSha256' "${GQ_PARENT_EVIDENCE}" | head -n 1)"
    [[ "${parent_sha}" == "${SUITE_SHA256}" ]] || { log_error "复测 Suite SHA-256 与首次证据不一致"; exit 2; }
    validate_parent_evidence
    parent_run_id="$(jq -r 'select(.type=="run")|.runId' "${GQ_PARENT_EVIDENCE}")"
    [[ "$(jq -r 'select(.type=="run")|.evidencePurpose // empty' "${GQ_PARENT_EVIDENCE}")" == "${GQ_EVIDENCE_PURPOSE}" ]] \
      || { log_error "复测证据用途与首次证据不一致"; exit 2; }
  else
    [[ -z "${GQ_PARENT_EVIDENCE}" ]] || { log_error "FIRST 不允许指定 GQ_PARENT_EVIDENCE"; exit 1; }
    [[ -z "${GQ_PARENT_EVIDENCE_SHA256}" ]] || { log_error "FIRST 不允许指定 GQ_PARENT_EVIDENCE_SHA256"; exit 1; }
  fi
  RUN_ID="$(date -u +%Y%m%dT%H%M%S%NZ)-${GQ_EVIDENCE_PURPOSE,,}-${GQ_PHASE,,}-$(printf '%s' "${SUITE_SHA256}" | cut -c1-8)"
  [[ -n "${GQ_OUTPUT_DIR}" ]] || GQ_OUTPUT_DIR="./reports/golden-question-${RUN_ID}"
  [[ ! -e "${GQ_OUTPUT_DIR}" ]] || { log_error "拒绝覆盖已存在输出: ${GQ_OUTPUT_DIR}"; exit 2; }
  mkdir -p "${GQ_OUTPUT_DIR}"; EVIDENCE_FILE="${GQ_OUTPUT_DIR}/evidence.jsonl"; REPORT_FILE="${GQ_OUTPUT_DIR}/report.md"
  REQUEST_IDS_FILE="${TMP_DIR}/request-ids.txt"; : > "${EVIDENCE_FILE}"; : > "${REQUEST_IDS_FILE}"; chmod 600 "${EVIDENCE_FILE}" "${REQUEST_IDS_FILE}"
  jq -cn --arg runId "${RUN_ID}" --arg phase "${GQ_PHASE}" --arg evidencePurpose "${GQ_EVIDENCE_PURPOSE}" \
    --arg suiteSha256 "${SUITE_SHA256}" --arg createdAt "$(date -Iseconds)" \
    --arg parentEvidenceSha256 "${GQ_PARENT_EVIDENCE_SHA256}" --arg parentRunId "${parent_run_id:-}" \
    '{type:"run",runId:$runId,phase:$phase,evidencePurpose:$evidencePurpose,suiteSha256:$suiteSha256,createdAt:$createdAt,automaticExclusion:false,
      parentEvidenceSha256:(if $parentEvidenceSha256 == "" then null else $parentEvidenceSha256 end),
      parentRunId:(if $parentRunId == "" then null else $parentRunId end)}' >> "${EVIDENCE_FILE}"
  validate_runtime_fingerprint
  login_body="$(jq -cn --arg tenantId "${PILOT_TENANT_ID}" --arg username "${PILOT_USERNAME}" --arg password "${PILOT_PASSWORD}" '{tenantId:$tenantId,username:$username,password:$password}')"
  login_response="$(printf '%s' "${login_body}" | curl -fsS --connect-timeout 5 --max-time 30 -H 'Content-Type: application/json; charset=UTF-8' -X POST "${BASE_URL%/}/api/v1/auth/login" --data-binary @-)"
  TOKEN="$(jq -r '.data.token // empty' <<< "${login_response}")"
  require_value "登录 Token" "${TOKEN}"
  BILLING_ACCEPTANCE_SESSION_ID="$(open_billing_acceptance)"
  require_value "计费验收会话 ID" "${BILLING_ACCEPTANCE_SESSION_ID}"
  if [[ "${GQ_PHASE}" == "FIRST" ]]; then
    run_cases_json="$(jq -c '.businessCases[]' "${GQ_SUITE_FILE}")"
  else
    run_cases_json="$(jq -c --slurpfile parent "${GQ_PARENT_EVIDENCE}" '.businessCases[] | . as $case | select(any($parent[]; .type=="business" and .id==$case.id and .correct==false))' "${GQ_SUITE_FILE}")"
    [[ -n "${run_cases_json}" ]] || { log_error "首次证据没有可复测的错误题"; exit 2; }
  fi
  BUSINESS_RUN_COUNT=0
  while IFS= read -r case_json; do [[ -n "${case_json}" ]] || continue; run_business_case "${case_json}"; BUSINESS_RUN_COUNT=$((BUSINESS_RUN_COUNT+1)); done <<< "${run_cases_json}"
  # 计费会话只证明业务题的双角色调用与余额守恒。安全题只有 SQL 架构师调用，
  # 必须在会话关闭后独立执行，避免其正常账本事件被误判为业务批次外事件。
  sleep "${GQ_EVIDENCE_SETTLE_SECONDS}"
  collect_business_batch_evidence
  security_cases_json="$(jq -c '.securityCases[]' "${GQ_SUITE_FILE}")"
  while IFS= read -r case_json; do [[ -n "${case_json}" ]] || continue; run_security_case "${case_json}"; done <<< "${security_cases_json}"
  if build_report; then
    if [[ "${GQ_EVIDENCE_PURPOSE}" == "CUSTOMER_UAT" ]]; then
      log_info "40题客户验收自动门禁通过，待双方签字: ${REPORT_FILE}"
    else
      log_info "40题内部演练自动门禁通过，不构成客户验收: ${REPORT_FILE}"
    fi
  else
    log_error "40题自动门禁未通过: ${REPORT_FILE}"
    exit 2
  fi
}

main "$@"
