#!/usr/bin/env bash
# LingSQL 可靠性基线压测脚本（轻量版）
# 用法:
#   BASE_URL=http://127.0.0.1:8000 ./scripts/ops/reliability_baseline.sh
#
# 可选环境变量:
#   TARGET_PATH=/actuator/health/readiness
#   TOTAL_REQUESTS=200
#   CONCURRENCY=20
#   TIMEOUT_SECONDS=5
#   SLA_SUCCESS_RATE=99
#   SLA_P95_MS=800
#   REPORT_FILE=./reports/reliability_baseline.md
#   ACCESS_TOKEN=***              # 可选：Bearer Token，仅通过临时 curl 配置传递
#   TENANT_ID=tenant-id           # 可选：X-Tenant-Id

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8000}"
TARGET_PATH="${TARGET_PATH:-/actuator/health/readiness}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-200}"
CONCURRENCY="${CONCURRENCY:-20}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-5}"
SLA_SUCCESS_RATE="${SLA_SUCCESS_RATE:-99}"
SLA_P95_MS="${SLA_P95_MS:-800}"
REPORT_FILE="${REPORT_FILE:-}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
TENANT_ID="${TENANT_ID:-}"

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

validate_positive_int() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]] || [[ "${value}" -le 0 ]]; then
    log_error "${name} 必须是正整数，当前值: ${value}"
    exit 1
  fi
}

validate_positive_number() {
  local name="$1"
  local value="$2"
  if ! awk -v v="${value}" 'BEGIN { exit (v > 0) ? 0 : 1 }'; then
    log_error "${name} 必须大于 0，当前值: ${value}"
    exit 1
  fi
}

validate_success_rate() {
  local value="$1"
  if ! awk -v v="${value}" 'BEGIN { exit (v > 0 && v <= 100) ? 0 : 1 }'; then
    log_error "SLA_SUCCESS_RATE 必须在 (0, 100] 范围内，当前值: ${value}"
    exit 1
  fi
}

main() {
  require_cmd curl
  require_cmd awk
  require_cmd sort
  require_cmd xargs

  validate_positive_int "TOTAL_REQUESTS" "${TOTAL_REQUESTS}"
  validate_positive_int "CONCURRENCY" "${CONCURRENCY}"
  validate_positive_int "TIMEOUT_SECONDS" "${TIMEOUT_SECONDS}"
  validate_success_rate "${SLA_SUCCESS_RATE}"
  validate_positive_number "SLA_P95_MS" "${SLA_P95_MS}"

  local url="${BASE_URL%/}${TARGET_PATH}"
  local tmp_file sorted_file curl_config_file
  tmp_file="$(mktemp)"
  sorted_file="$(mktemp)"
  curl_config_file="$(mktemp)"
  chmod 600 "${curl_config_file}"
  trap "rm -f '${tmp_file}' '${sorted_file}' '${curl_config_file}'" EXIT

  if [[ -n "${ACCESS_TOKEN}" ]]; then
    [[ "${ACCESS_TOKEN}" =~ ^[A-Za-z0-9._~+/=-]+$ ]] || {
      log_error "ACCESS_TOKEN 包含不允许写入 HTTP Header 的字符"
      exit 1
    }
    printf 'header = "Authorization: Bearer %s"\n' "${ACCESS_TOKEN}" >> "${curl_config_file}"
  fi
  if [[ -n "${TENANT_ID}" ]]; then
    [[ "${TENANT_ID}" =~ ^[A-Za-z0-9._-]{1,128}$ ]] || {
      log_error "TENANT_ID 格式非法"
      exit 1
    }
    printf 'header = "X-Tenant-Id: %s"\n' "${TENANT_ID}" >> "${curl_config_file}"
  fi

  log_info "开始执行可靠性基线压测: url=${url}, requests=${TOTAL_REQUESTS}, concurrency=${CONCURRENCY}"
  seq "${TOTAL_REQUESTS}" | xargs -P "${CONCURRENCY}" -I{} bash -c '
    url="$1"
    timeout="$2"
    start_ms="$(date +%s%3N)"
    curl_config="$3"
    code="$(curl --config "${curl_config}" -s -m "${timeout}" -o /dev/null -w "%{http_code}" "${url}" || true)"
    if [[ -z "${code}" ]]; then
      code="000"
    fi
    end_ms="$(date +%s%3N)"
    elapsed=$((end_ms - start_ms))
    echo "${elapsed},${code}"
  ' _ "${url}" "${TIMEOUT_SECONDS}" "${curl_config_file}" > "${tmp_file}"

  local total success fail success_rate avg_ms p95_ms max_ms min_ms
  total="$(wc -l < "${tmp_file}" | tr -d ' ')"
  success="$(awk -F, '$2 ~ /^[23][0-9][0-9]$/ {c++} END {print c+0}' "${tmp_file}")"
  fail=$((total - success))
  success_rate="$(awk -v s="${success}" -v t="${total}" 'BEGIN { if (t <= 0) printf "%.2f", 0; else printf "%.2f", (s/t)*100 }')"
  avg_ms="$(awk -F, '{sum+=$1} END { if (NR<=0) printf "%.2f", 0; else printf "%.2f", sum/NR }' "${tmp_file}")"
  max_ms="$(awk -F, 'BEGIN {max=0} {if($1>max) max=$1} END {print max+0}' "${tmp_file}")"
  min_ms="$(awk -F, 'BEGIN {min=-1} {if(min<0 || $1<min) min=$1} END {if(min<0) min=0; print min+0}' "${tmp_file}")"

  sort -t, -k1,1n "${tmp_file}" > "${sorted_file}"
  local p95_index
  p95_index="$(awk -v t="${total}" 'BEGIN { if (t <= 0) print 1; else { i = int((t*95 + 99)/100); if (i < 1) i = 1; print i } }')"
  p95_ms="$(awk -F, -v idx="${p95_index}" 'NR==idx {print $1; found=1; exit} END { if (!found) print 0 }' "${sorted_file}")"

  local status="PASS"
  if ! awk -v actual="${success_rate}" -v target="${SLA_SUCCESS_RATE}" 'BEGIN { exit (actual + 0.000001 >= target) ? 0 : 1 }'; then
    status="FAIL"
  fi
  if ! awk -v actual="${p95_ms}" -v target="${SLA_P95_MS}" 'BEGIN { exit (actual <= target + 0.000001) ? 0 : 1 }'; then
    status="FAIL"
  fi

  log_info "压测完成: total=${total}, success=${success}, fail=${fail}, successRate=${success_rate}%"
  log_info "延迟统计(ms): min=${min_ms}, avg=${avg_ms}, p95=${p95_ms}, max=${max_ms}"
  log_info "SLA 校验: successRate>=${SLA_SUCCESS_RATE}%, p95<=${SLA_P95_MS}ms, result=${status}"

  if [[ -n "${REPORT_FILE}" ]]; then
    mkdir -p "$(dirname "${REPORT_FILE}")"
    cat > "${REPORT_FILE}" <<EOF
# LingSQL 可靠性基线报告

- 检测时间: $(date -Iseconds)
- 目标地址: ${url}
- 请求总数: ${total}
- 并发数: ${CONCURRENCY}
- 成功数: ${success}
- 失败数: ${fail}
- 成功率: ${success_rate}%
- 延迟最小值: ${min_ms} ms
- 延迟平均值: ${avg_ms} ms
- 延迟 P95: ${p95_ms} ms
- 延迟最大值: ${max_ms} ms
- SLA 成功率目标: ${SLA_SUCCESS_RATE}%
- SLA P95 目标: ${SLA_P95_MS} ms
- 基线结果: ${status}
EOF
    log_info "报告文件已写入: ${REPORT_FILE}"
  fi

  if [[ "${status}" != "PASS" ]]; then
    log_error "可靠性基线未达标，请先完成性能治理再推进发布。"
    exit 2
  fi
}

main "$@"
