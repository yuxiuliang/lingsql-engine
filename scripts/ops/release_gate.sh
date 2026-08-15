#!/usr/bin/env bash
# LingSQL 发布门禁脚本
# 用法:
#   BASE_URL=https://api.lingsql.com \
#   PROMETHEUS_URL=http://prometheus:9090 \
#   ./scripts/ops/release_gate.sh
#
# 可选环境变量:
#   RELEASE_GATE_ENABLE_SMOKE=true
#   RELEASE_GATE_ENABLE_SLO=true
#   RELEASE_GATE_ENABLE_COMMERCIAL=true
#   RELEASE_GATE_BLOCK_ON_SLO_BREACH=true
#   RELEASE_GATE_SLO_REPORT_FILE=./reports/release_gate_slo.md
#   RELEASE_GATE_COMMERCIAL_REPORT_FILE=./reports/commercial_acceptance.md
#   以及 release_smoke_test.sh / slo_error_budget_report.sh 支持的参数

set -euo pipefail

RELEASE_GATE_ENABLE_SMOKE="${RELEASE_GATE_ENABLE_SMOKE:-true}"
RELEASE_GATE_ENABLE_SLO="${RELEASE_GATE_ENABLE_SLO:-true}"
RELEASE_GATE_ENABLE_COMMERCIAL="${RELEASE_GATE_ENABLE_COMMERCIAL:-true}"
RELEASE_GATE_BLOCK_ON_SLO_BREACH="${RELEASE_GATE_BLOCK_ON_SLO_BREACH:-true}"
RELEASE_GATE_SLO_REPORT_FILE="${RELEASE_GATE_SLO_REPORT_FILE:-}"
RELEASE_GATE_COMMERCIAL_REPORT_FILE="${RELEASE_GATE_COMMERCIAL_REPORT_FILE:-}"

log_info() {
  echo "[INFO] $*"
}

log_warn() {
  echo "[WARN] $*"
}

log_error() {
  echo "[ERROR] $*" >&2
}

is_true() {
  local value="$1"
  [[ "${value}" == "true" || "${value}" == "1" || "${value}" == "yes" ]]
}

run_smoke_gate() {
  if ! is_true "${RELEASE_GATE_ENABLE_SMOKE}"; then
    log_info "已跳过冒烟门禁（RELEASE_GATE_ENABLE_SMOKE=${RELEASE_GATE_ENABLE_SMOKE}）"
    return 0
  fi
  if [[ -z "${BASE_URL:-}" ]]; then
    log_error "启用冒烟门禁时必须设置 BASE_URL"
    return 1
  fi
  log_info "开始执行冒烟门禁，BASE_URL=${BASE_URL}"
  ./scripts/ops/release_smoke_test.sh
}

run_slo_gate() {
  if ! is_true "${RELEASE_GATE_ENABLE_SLO}"; then
    log_info "已跳过 SLO 门禁（RELEASE_GATE_ENABLE_SLO=${RELEASE_GATE_ENABLE_SLO}）"
    return 0
  fi
  if [[ -z "${PROMETHEUS_URL:-}" ]]; then
    log_error "启用 SLO 门禁时必须设置 PROMETHEUS_URL"
    return 1
  fi
  if [[ -n "${RELEASE_GATE_SLO_REPORT_FILE}" ]]; then
    export REPORT_FILE="${RELEASE_GATE_SLO_REPORT_FILE}"
  fi
  log_info "开始执行 SLO 门禁，PROMETHEUS_URL=${PROMETHEUS_URL}"
  set +e
  ./scripts/ops/slo_error_budget_report.sh
  local slo_code=$?
  set -e
  if [[ ${slo_code} -eq 0 ]]; then
    return 0
  fi
  if [[ ${slo_code} -eq 2 ]]; then
    if is_true "${RELEASE_GATE_BLOCK_ON_SLO_BREACH}"; then
      log_error "SLO 门禁未达到可发布条件，发布门禁拦截"
      return 1
    fi
    log_warn "SLO 错误预算已耗尽，但当前配置允许继续发布"
    return 0
  fi
  log_error "SLO 门禁执行失败，退出码=${slo_code}"
  return 1
}

run_commercial_gate() {
  if ! is_true "${RELEASE_GATE_ENABLE_COMMERCIAL}"; then
    log_info "已跳过商用验收门禁（RELEASE_GATE_ENABLE_COMMERCIAL=${RELEASE_GATE_ENABLE_COMMERCIAL}）"
    return 0
  fi
  if [[ -n "${RELEASE_GATE_COMMERCIAL_REPORT_FILE}" ]]; then
    export COMMERCIAL_REPORT_FILE="${RELEASE_GATE_COMMERCIAL_REPORT_FILE}"
  fi
  log_info "开始执行商用验收总门禁"
  ./scripts/ops/commercial_acceptance_gate.sh
}

main() {
  local failed=0

  log_info "发布门禁开始"
  log_info "参数: smoke=${RELEASE_GATE_ENABLE_SMOKE}, slo=${RELEASE_GATE_ENABLE_SLO}, commercial=${RELEASE_GATE_ENABLE_COMMERCIAL}, blockOnBreach=${RELEASE_GATE_BLOCK_ON_SLO_BREACH}"

  if ! run_smoke_gate; then
    failed=1
  fi

  if ! run_slo_gate; then
    failed=1
  fi

  if ! run_commercial_gate; then
    failed=1
  fi

  if [[ ${failed} -ne 0 ]]; then
    log_error "发布门禁未通过"
    exit 1
  fi

  log_info "发布门禁通过"
}

main "$@"
