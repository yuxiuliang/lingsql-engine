#!/usr/bin/env bash
# LingSQL SLO / 错误预算报告脚本
# 用法:
#   PROMETHEUS_URL=http://127.0.0.1:9090 ./scripts/ops/slo_error_budget_report.sh
#
# 可选环境变量:
#   SLO_WINDOW=30d
#   SLO_TARGET=99.9
#   SLO_MIN_REQUESTS=100
#   SLO_TOTAL_QUERY=...
#   SLO_ERROR_QUERY=...
#   REPORT_FILE=./reports/slo_20260227.md

set -euo pipefail

PROMETHEUS_URL="${PROMETHEUS_URL:-http://127.0.0.1:9090}"
SLO_WINDOW="${SLO_WINDOW:-30d}"
SLO_TARGET="${SLO_TARGET:-99.9}"
SLO_MIN_REQUESTS="${SLO_MIN_REQUESTS:-100}"
SLO_TOTAL_QUERY="${SLO_TOTAL_QUERY:-sum(increase(http_server_requests_seconds_count{uri!~\"/actuator.*\"}[${SLO_WINDOW}]))}"
SLO_ERROR_QUERY="${SLO_ERROR_QUERY:-sum(increase(http_server_requests_seconds_count{uri!~\"/actuator.*\",status=~\"5..\"}[${SLO_WINDOW}]))}"
REPORT_FILE="${REPORT_FILE:-}"

log_info() {
  echo "[INFO] $*"
}

log_error() {
  echo "[ERROR] $*" >&2
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    log_error "缺少命令: ${cmd}"
    exit 1
  fi
}

query_prometheus() {
  local query="$1"
  curl -sS --get "${PROMETHEUS_URL%/}/api/v1/query" --data-urlencode "query=${query}"
}

extract_value() {
  local json="$1"
  echo "${json}" | jq -r '.data.result[0].value[1] // "0"'
}

verify_success_response() {
  local json="$1"
  local status
  status="$(echo "${json}" | jq -r '.status // empty')"
  if [[ "${status}" != "success" ]]; then
    log_error "Prometheus 查询失败: ${json}"
    exit 1
  fi
}

main() {
  require_cmd curl
  require_cmd jq
  require_cmd awk

  if ! awk -v value="${SLO_TARGET}" 'BEGIN { exit (value ~ /^[0-9]+([.][0-9]+)?$/ && value > 0 && value < 100) ? 0 : 1 }'; then
    log_error "SLO_TARGET 必须是 0 到 100 之间的数字: ${SLO_TARGET}"
    exit 1
  fi
  if ! awk -v value="${SLO_MIN_REQUESTS}" 'BEGIN { exit (value ~ /^[0-9]+$/ && value > 0) ? 0 : 1 }'; then
    log_error "SLO_MIN_REQUESTS 必须是正整数: ${SLO_MIN_REQUESTS}"
    exit 1
  fi

  log_info "开始生成 SLO 报告，窗口=${SLO_WINDOW}，目标=${SLO_TARGET}%"

  local total_json
  total_json="$(query_prometheus "${SLO_TOTAL_QUERY}")"
  verify_success_response "${total_json}"
  local total_requests
  total_requests="$(extract_value "${total_json}")"

  local error_json
  error_json="$(query_prometheus "${SLO_ERROR_QUERY}")"
  verify_success_response "${error_json}"
  local error_requests
  error_requests="$(extract_value "${error_json}")"

  if ! awk -v value="${total_requests}" 'BEGIN { exit (value ~ /^[0-9]+([.][0-9]+)?$/) ? 0 : 1 }'; then
    log_error "Prometheus 返回的总请求数不是非负数: ${total_requests}"
    exit 1
  fi
  if ! awk -v value="${error_requests}" 'BEGIN { exit (value ~ /^[0-9]+([.][0-9]+)?$/) ? 0 : 1 }'; then
    log_error "Prometheus 返回的错误请求数不是非负数: ${error_requests}"
    exit 1
  fi
  if ! awk -v total="${total_requests}" -v err="${error_requests}" 'BEGIN { exit (err <= total) ? 0 : 1 }'; then
    log_error "错误请求数不能大于总请求数: errors=${error_requests}, total=${total_requests}"
    exit 1
  fi

  local sample_status="SUFFICIENT"
  if ! awk -v total="${total_requests}" -v minimum="${SLO_MIN_REQUESTS}" 'BEGIN { exit (total >= minimum) ? 0 : 1 }'; then
    sample_status="INSUFFICIENT_DATA"
  fi

  local availability
  availability="$(awk -v total="${total_requests}" -v err="${error_requests}" 'BEGIN { if (total <= 0) printf "%.6f", 0; else printf "%.6f", ((total - err) / total) * 100 }')"
  local error_rate
  error_rate="$(awk -v total="${total_requests}" -v err="${error_requests}" 'BEGIN { if (total <= 0) printf "%.6f", 0; else printf "%.6f", (err / total) * 100 }')"
  local budget_rate
  budget_rate="$(awk -v target="${SLO_TARGET}" 'BEGIN { printf "%.6f", 100 - target }')"
  local budget_consumed
  budget_consumed="$(awk -v er="${error_rate}" -v br="${budget_rate}" 'BEGIN { if (br <= 0) printf "%.2f", 0; else printf "%.2f", (er / br) * 100 }')"
  local budget_remaining
  budget_remaining="$(awk -v consumed="${budget_consumed}" 'BEGIN { left = 100 - consumed; if (left < 0) left = 0; printf "%.2f", left }')"

  local budget_status="HEALTHY"
  budget_status="$(awk -v consumed="${budget_consumed}" 'BEGIN { if (consumed >= 100) print "BREACHED"; else if (consumed >= 70) print "AT_RISK"; else print "HEALTHY" }')"

  local report_time
  report_time="$(date -Iseconds)"

  log_info "报告时间: ${report_time}"
  log_info "总请求数: ${total_requests}"
  log_info "最小样本数: ${SLO_MIN_REQUESTS}"
  log_info "样本状态: ${sample_status}"
  log_info "错误请求数(5xx): ${error_requests}"
  log_info "可用性: ${availability}%"
  log_info "错误率: ${error_rate}%"
  log_info "错误预算总额: ${budget_rate}%"
  log_info "错误预算已消耗: ${budget_consumed}%"
  log_info "错误预算剩余: ${budget_remaining}%"
  log_info "预算状态: ${budget_status}"

  if [[ -n "${REPORT_FILE}" ]]; then
    mkdir -p "$(dirname "${REPORT_FILE}")"
    cat > "${REPORT_FILE}" <<EOF
# LingSQL SLO / 错误预算报告

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

## PromQL

- 总请求查询: \`${SLO_TOTAL_QUERY}\`
- 错误请求查询: \`${SLO_ERROR_QUERY}\`
EOF
    log_info "报告文件已写入: ${REPORT_FILE}"
  fi

  if [[ "${sample_status}" == "INSUFFICIENT_DATA" ]]; then
    log_error "请求样本不足，不能据此证明 SLO 达标。"
    exit 2
  fi

  if [[ "${budget_status}" == "BREACHED" ]]; then
    log_error "错误预算已耗尽，建议立即冻结非紧急发布并执行稳定性专项治理。"
    exit 2
  fi
}

main "$@"
