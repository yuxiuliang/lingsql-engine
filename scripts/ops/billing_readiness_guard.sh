#!/usr/bin/env bash
# LingSQL 计费商用验收门禁脚本
# 用法:
#   BILLING_READINESS_URL="https://api.example.com/api/v1/admin/billing/readiness/snapshot?windowHours=24" \
#   BILLING_AUTH_HEADER="Authorization: Bearer <token>" \
#   ./scripts/ops/billing_readiness_guard.sh
#
# 可选环境变量:
#   BASE_URL=https://api.example.com
#   BILLING_AUTH_TOKEN=<token>
#   BILLING_ALLOW_SKIP=false
#   REPORT_FILE=./reports/billing_readiness.md

set -euo pipefail

BILLING_READINESS_URL="${BILLING_READINESS_URL:-}"
BILLING_AUTH_HEADER="${BILLING_AUTH_HEADER:-}"
BILLING_AUTH_TOKEN="${BILLING_AUTH_TOKEN:-}"
BILLING_ALLOW_SKIP="${BILLING_ALLOW_SKIP:-false}"
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

is_true() {
  local value="${1:-false}"
  [[ "${value}" == "1" || "${value}" == "true" || "${value}" == "TRUE" || "${value}" == "yes" || "${value}" == "YES" ]]
}

main() {
  require_cmd curl
  require_cmd jq

  if [[ -z "${BILLING_READINESS_URL}" && -n "${BASE_URL:-}" ]]; then
    BILLING_READINESS_URL="${BASE_URL%/}/api/v1/admin/billing/readiness/snapshot"
  fi
  if [[ -z "${BILLING_AUTH_HEADER}" && -n "${BILLING_AUTH_TOKEN}" ]]; then
    BILLING_AUTH_HEADER="Authorization: Bearer ${BILLING_AUTH_TOKEN}"
  fi

  if [[ -z "${BILLING_READINESS_URL}" ]]; then
    if is_true "${BILLING_ALLOW_SKIP}"; then
      log_info "未配置 BILLING_READINESS_URL，跳过计费商用验收检查。"
      exit 0
    fi
    log_error "未配置 BILLING_READINESS_URL，无法执行计费商用验收。"
    exit 1
  fi

  log_info "开始调用计费商用验收接口: ${BILLING_READINESS_URL}"
  local response
  if [[ -n "${BILLING_AUTH_HEADER}" ]]; then
    response="$(curl -sS -H "${BILLING_AUTH_HEADER}" "${BILLING_READINESS_URL}")"
  else
    response="$(curl -sS "${BILLING_READINESS_URL}")"
  fi

  local code ready generated_at checks_failed
  code="$(echo "${response}" | jq -r '.code // 500')"
  ready="$(echo "${response}" | jq -r '.data.ready // false')"
  generated_at="$(echo "${response}" | jq -r '.data.generatedAt // ""')"
  checks_failed="$(echo "${response}" | jq -r '[.data.checks[]? | select(.passed == false)] | length')"

  if [[ -n "${REPORT_FILE}" ]]; then
    mkdir -p "$(dirname "${REPORT_FILE}")"
    echo "${response}" | jq . > "${REPORT_FILE}"
    log_info "验收响应已写入: ${REPORT_FILE}"
  fi

  if [[ "${code}" != "200" ]]; then
    log_error "接口返回异常 code=${code}"
    exit 1
  fi
  if [[ "${ready}" != "true" ]]; then
    log_error "计费商用验收未通过，失败检查项数量=${checks_failed}"
    exit 2
  fi

  log_info "计费商用验收通过，报告时间=${generated_at}"
}

main "$@"
