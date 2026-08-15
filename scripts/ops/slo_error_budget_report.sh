#!/usr/bin/env bash
# 生成历史服务 SLO 或候选专属受控负载 SLO；候选模式禁止复用全局历史流量。

set -euo pipefail

PROMETHEUS_URL="${PROMETHEUS_URL:-http://127.0.0.1:9090}"
SLO_EVIDENCE_MODE="${SLO_EVIDENCE_MODE:-HISTORICAL}"
SLO_WINDOW="${SLO_WINDOW:-30d}"
SLO_TARGET="${SLO_TARGET:-99.9}"
SLO_MIN_REQUESTS="${SLO_MIN_REQUESTS:-100}"
SLO_TOTAL_QUERY_OVERRIDE="${SLO_TOTAL_QUERY:-}"
SLO_ERROR_QUERY_OVERRIDE="${SLO_ERROR_QUERY:-}"
SLO_EXPECTED_BUILD_COMMIT="${SLO_EXPECTED_BUILD_COMMIT:-}"
SLO_EXPECTED_SLOT="${SLO_EXPECTED_SLOT:-}"
SLO_EXPECTED_RELEASE_ID="${SLO_EXPECTED_RELEASE_ID:-}"
SLO_EXPECTED_CANDIDATE_BASE_URL="${SLO_EXPECTED_CANDIDATE_BASE_URL:-}"
SLO_CONTROLLED_LOAD_REPORT_FILE="${SLO_CONTROLLED_LOAD_REPORT_FILE:-}"
SLO_CONTROLLED_LOAD_PATH="${SLO_CONTROLLED_LOAD_PATH:-/api/v1/admin/runtime/fingerprint}"
SLO_SCRAPE_SETTLE_SECONDS="${SLO_SCRAPE_SETTLE_SECONDS:-20}"
REPORT_FILE="${REPORT_FILE:-}"

SLO_TOTAL_QUERY=""
SLO_ERROR_QUERY=""
CONTROLLED_LOAD_REPORT_SHA=""

log_info() { echo "[INFO] $*"; }
log_error() { echo "[ERROR] $*" >&2; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || { log_error "缺少命令: $1"; exit 1; }; }

query_prometheus() {
  local query="$1"
  curl -sS --get "${PROMETHEUS_URL%/}/api/v1/query" --data-urlencode "query=${query}"
}

extract_value() {
  jq -r '.data.result[0].value[1] // "0"' <<< "$1"
}

verify_success_response() {
  local json="$1"
  [[ "$(jq -r '.status // empty' <<< "${json}")" == "success" ]] || {
    log_error "Prometheus 查询失败"
    exit 1
  }
  [[ "$(jq -r '.data.resultType // empty' <<< "${json}")" == "vector" ]] || {
    log_error "Prometheus 即时查询未返回 vector"
    exit 1
  }
}

verify_candidate_series() {
  local name="$1" json="$2" count commit slot release_id
  count="$(jq -r '.data.result | length' <<< "${json}")"
  [[ "${count}" == "1" ]] || { log_error "候选 ${name} 查询必须且只能返回一条带身份标签的序列，实际 ${count} 条"; exit 2; }
  commit="$(jq -r '.data.result[0].metric.build_commit // empty' <<< "${json}")"
  slot="$(jq -r '.data.result[0].metric.slot // empty' <<< "${json}")"
  release_id="$(jq -r '.data.result[0].metric.release_id // empty' <<< "${json}")"
  [[ "${commit}" == "${SLO_EXPECTED_BUILD_COMMIT}" \
      && "${slot}" == "${SLO_EXPECTED_SLOT}" \
      && "${release_id}" == "${SLO_EXPECTED_RELEASE_ID}" ]] || {
    log_error "候选 ${name} 序列身份不匹配或缺少 build_commit/slot/release_id 标签"
    exit 2
  }
}

validate_common_inputs() {
  if ! awk -v value="${SLO_TARGET}" 'BEGIN { exit (value ~ /^[0-9]+([.][0-9]+)?$/ && value > 0 && value < 100) ? 0 : 1 }'; then
    log_error "SLO_TARGET 必须是 0 到 100 之间的数字: ${SLO_TARGET}"
    exit 1
  fi
  [[ "${SLO_MIN_REQUESTS}" =~ ^[1-9][0-9]*$ ]] || { log_error "SLO_MIN_REQUESTS 必须是正整数"; exit 1; }
  [[ "${SLO_WINDOW}" =~ ^[1-9][0-9]*(s|m|h|d|w|y)$ ]] || { log_error "SLO_WINDOW 格式非法"; exit 1; }
}

prepare_historical_queries() {
  SLO_TOTAL_QUERY="${SLO_TOTAL_QUERY_OVERRIDE:-sum(increase(http_server_requests_seconds_count{uri!~\"/actuator.*\"}[${SLO_WINDOW}]))}"
  SLO_ERROR_QUERY="${SLO_ERROR_QUERY_OVERRIDE:-sum(increase(http_server_requests_seconds_count{uri!~\"/actuator.*\",status=~\"5..\"}[${SLO_WINDOW}]))}"
}

prepare_candidate_queries() {
  local selector total_by_label error_by_label report_total report_result report_target expected_target
  [[ -z "${SLO_TOTAL_QUERY_OVERRIDE}" && -z "${SLO_ERROR_QUERY_OVERRIDE}" ]] || {
    log_error "候选模式禁止覆盖 SLO_TOTAL_QUERY/SLO_ERROR_QUERY，避免移除强制身份过滤"
    exit 1
  }
  [[ "${SLO_EXPECTED_BUILD_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || { log_error "候选 build_commit 必须为40位小写 Git SHA"; exit 1; }
  [[ "${SLO_EXPECTED_SLOT}" == "blue" || "${SLO_EXPECTED_SLOT}" == "green" ]] || { log_error "候选 slot 必须为 blue 或 green"; exit 1; }
  [[ "${SLO_EXPECTED_RELEASE_ID}" =~ ^[A-Za-z0-9._-]{1,128}$ ]] || { log_error "候选 release_id 非法"; exit 1; }
  [[ "${SLO_EXPECTED_CANDIDATE_BASE_URL}" =~ ^https?://[^[:space:]]+$ ]] || { log_error "缺少合法候选 BASE_URL"; exit 1; }
  [[ "${SLO_CONTROLLED_LOAD_PATH}" =~ ^/[A-Za-z0-9._~:/?&=%+-]*$ ]] || { log_error "候选受控负载路径非法"; exit 1; }
  [[ -s "${SLO_CONTROLLED_LOAD_REPORT_FILE}" ]] || { log_error "缺少候选受控负载报告"; exit 1; }
  report_total="$(sed -n 's/^- 请求总数: //p' "${SLO_CONTROLLED_LOAD_REPORT_FILE}" | tail -n 1)"
  report_result="$(sed -n 's/^- 基线结果: //p' "${SLO_CONTROLLED_LOAD_REPORT_FILE}" | tail -n 1)"
  report_target="$(sed -n 's/^- 目标地址: //p' "${SLO_CONTROLLED_LOAD_REPORT_FILE}" | tail -n 1)"
  expected_target="${SLO_EXPECTED_CANDIDATE_BASE_URL%/}${SLO_CONTROLLED_LOAD_PATH}"
  [[ "${report_total}" =~ ^[1-9][0-9]*$ && "${report_total}" -ge "${SLO_MIN_REQUESTS}" \
      && "${report_result}" == "PASS" && "${report_target}" == "${expected_target}" ]] || {
    log_error "受控负载报告不是当前候选、样本不足或结论非 PASS"
    exit 2
  }
  CONTROLLED_LOAD_REPORT_SHA="$(sha256sum "${SLO_CONTROLLED_LOAD_REPORT_FILE}" | awk '{print $1}')"

  selector="build_commit=\"${SLO_EXPECTED_BUILD_COMMIT}\",slot=\"${SLO_EXPECTED_SLOT}\",release_id=\"${SLO_EXPECTED_RELEASE_ID}\",uri!~\"/actuator.*\""
  total_by_label="sum by (build_commit, slot, release_id) (increase(http_server_requests_seconds_count{${selector}}[${SLO_WINDOW}]))"
  error_by_label="sum by (build_commit, slot, release_id) (increase(http_server_requests_seconds_count{${selector},status=~\"5..\"}[${SLO_WINDOW}]))"
  SLO_TOTAL_QUERY="${total_by_label}"
  SLO_ERROR_QUERY="(${error_by_label}) or on (build_commit, slot, release_id) (0 * (${total_by_label}))"
  [[ "${SLO_SCRAPE_SETTLE_SECONDS}" =~ ^[0-9]+$ && "${SLO_SCRAPE_SETTLE_SECONDS}" -le 60 ]] || {
    log_error "SLO_SCRAPE_SETTLE_SECONDS 必须是0到60秒整数"
    exit 1
  }
  if [[ "${SLO_SCRAPE_SETTLE_SECONDS}" -gt 0 ]]; then
    log_info "等待 Prometheus 抓取候选受控负载: ${SLO_SCRAPE_SETTLE_SECONDS}s"
    sleep "${SLO_SCRAPE_SETTLE_SECONDS}"
  fi
}

write_report() {
  local report_time="$1" total_requests="$2" error_requests="$3" availability="$4" error_rate="$5"
  local budget_rate="$6" budget_consumed="$7" budget_remaining="$8" sample_status="$9" budget_status="${10}"
  [[ -n "${REPORT_FILE}" ]] || return 0
  mkdir -p "$(dirname "${REPORT_FILE}")"
  cat > "${REPORT_FILE}" <<EOF
# LingSQL SLO / 错误预算报告

- 证据模式: ${SLO_EVIDENCE_MODE}
- 报告时间: ${report_time}
- 查询窗口: ${SLO_WINDOW}
- SLO 目标: ${SLO_TARGET}%
- 总请求数: ${total_requests}
- 最小样本数: ${SLO_MIN_REQUESTS}
- 样本状态: ${sample_status}
- 5xx 错误数: ${error_requests}
- 可用性: ${availability}%
- 错误率: ${error_rate}%
- 错误预算总额: ${budget_rate}%
- 错误预算已消耗: ${budget_consumed}%
- 错误预算剩余: ${budget_remaining}%
- 预算状态: ${budget_status}
EOF
  if [[ "${SLO_EVIDENCE_MODE}" == "CANDIDATE" ]]; then
    cat >> "${REPORT_FILE}" <<EOF
- 候选提交: ${SLO_EXPECTED_BUILD_COMMIT}
- 候选槽位: ${SLO_EXPECTED_SLOT}
- 候选发布ID: ${SLO_EXPECTED_RELEASE_ID}
- 受控负载报告 SHA-256: ${CONTROLLED_LOAD_REPORT_SHA}
- Prometheus 序列标签校验: PASS
EOF
  fi
  cat >> "${REPORT_FILE}" <<EOF

## PromQL

- 总请求查询: \`${SLO_TOTAL_QUERY}\`
- 错误请求查询: \`${SLO_ERROR_QUERY}\`
EOF
  log_info "报告文件已写入: ${REPORT_FILE}"
}

main() {
  local total_json error_json total_requests error_requests sample_status availability error_rate
  local budget_rate budget_consumed budget_remaining budget_status report_time
  for command_name in curl jq awk sed tail sha256sum sleep; do require_cmd "${command_name}"; done
  validate_common_inputs
  case "${SLO_EVIDENCE_MODE}" in
    HISTORICAL) prepare_historical_queries ;;
    CANDIDATE) prepare_candidate_queries ;;
    *) log_error "SLO_EVIDENCE_MODE 仅支持 HISTORICAL 或 CANDIDATE"; exit 1 ;;
  esac

  log_info "开始生成 ${SLO_EVIDENCE_MODE} SLO 报告，窗口=${SLO_WINDOW}，目标=${SLO_TARGET}%"
  total_json="$(query_prometheus "${SLO_TOTAL_QUERY}")"
  error_json="$(query_prometheus "${SLO_ERROR_QUERY}")"
  verify_success_response "${total_json}"
  verify_success_response "${error_json}"
  if [[ "${SLO_EVIDENCE_MODE}" == "CANDIDATE" ]]; then
    verify_candidate_series "总请求" "${total_json}"
    verify_candidate_series "错误请求" "${error_json}"
  fi
  total_requests="$(extract_value "${total_json}")"
  error_requests="$(extract_value "${error_json}")"
  if ! awk -v value="${total_requests}" 'BEGIN { exit (value ~ /^[0-9]+([.][0-9]+)?$/) ? 0 : 1 }'; then
    log_error "Prometheus 返回的总请求数不是非负数: ${total_requests}"; exit 1
  fi
  if ! awk -v value="${error_requests}" 'BEGIN { exit (value ~ /^[0-9]+([.][0-9]+)?$/) ? 0 : 1 }'; then
    log_error "Prometheus 返回的错误请求数不是非负数: ${error_requests}"; exit 1
  fi
  awk -v total="${total_requests}" -v err="${error_requests}" 'BEGIN { exit (err <= total) ? 0 : 1 }' || {
    log_error "错误请求数不能大于总请求数"; exit 1
  }

  sample_status="SUFFICIENT"
  awk -v total="${total_requests}" -v minimum="${SLO_MIN_REQUESTS}" 'BEGIN { exit (total >= minimum) ? 0 : 1 }' \
    || sample_status="INSUFFICIENT_DATA"
  availability="$(awk -v total="${total_requests}" -v err="${error_requests}" 'BEGIN { if (total <= 0) printf "%.6f", 0; else printf "%.6f", ((total - err) / total) * 100 }')"
  error_rate="$(awk -v total="${total_requests}" -v err="${error_requests}" 'BEGIN { if (total <= 0) printf "%.6f", 0; else printf "%.6f", (err / total) * 100 }')"
  budget_rate="$(awk -v target="${SLO_TARGET}" 'BEGIN { printf "%.6f", 100 - target }')"
  budget_consumed="$(awk -v er="${error_rate}" -v br="${budget_rate}" 'BEGIN { if (br <= 0) printf "%.2f", 0; else printf "%.2f", (er / br) * 100 }')"
  budget_remaining="$(awk -v consumed="${budget_consumed}" 'BEGIN { left = 100 - consumed; if (left < 0) left = 0; printf "%.2f", left }')"
  budget_status="$(awk -v consumed="${budget_consumed}" 'BEGIN { if (consumed >= 100) print "BREACHED"; else if (consumed >= 70) print "AT_RISK"; else print "HEALTHY" }')"
  report_time="$(date -Iseconds)"
  write_report "${report_time}" "${total_requests}" "${error_requests}" "${availability}" "${error_rate}" \
    "${budget_rate}" "${budget_consumed}" "${budget_remaining}" "${sample_status}" "${budget_status}"

  [[ "${sample_status}" != "INSUFFICIENT_DATA" ]] || { log_error "请求样本不足，不能证明 SLO 达标"; exit 2; }
  [[ "${budget_status}" != "BREACHED" ]] || { log_error "错误预算已耗尽"; exit 2; }
  log_info "SLO 证据通过: mode=${SLO_EVIDENCE_MODE}, total=${total_requests}, availability=${availability}%"
}

main "$@"
