#!/usr/bin/env bash
# LingSQL 商用验收总门禁脚本（四块能力）
# 用法:
#   BASE_URL=http://127.0.0.1:8000 \
#   PROMETHEUS_URL=http://127.0.0.1:9090 \
#   BILLING_READINESS_URL=http://127.0.0.1:8000/api/v1/admin/billing/readiness/snapshot \
#   BILLING_AUTH_HEADER="Authorization: Bearer <token>" \
#   ./scripts/ops/commercial_acceptance_gate.sh
#
# 可选环境变量:
#   COMMERCIAL_REQUIRE_SLO=false
#   COMMERCIAL_REPORT_FILE=./reports/commercial_acceptance.md

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMERCIAL_REQUIRE_SLO="${COMMERCIAL_REQUIRE_SLO:-false}"
COMMERCIAL_REPORT_FILE="${COMMERCIAL_REPORT_FILE:-./reports/commercial_acceptance.md}"

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
  local value="${1:-false}"
  [[ "${value}" == "1" || "${value}" == "true" || "${value}" == "TRUE" || "${value}" == "yes" || "${value}" == "YES" ]]
}

run_step() {
  local name="$1"
  shift
  if "$@"; then
    echo "${name}|PASS"
    return 0
  fi
  echo "${name}|FAIL"
  return 1
}

main() {
  mkdir -p "$(dirname "${COMMERCIAL_REPORT_FILE}")"
  local tmp_status
  tmp_status="$(mktemp)"
  trap 'rm -f "${tmp_status}"' EXIT

  log_info "开始执行商用验收总门禁"

  # 1) 商业闭环：计费商用验收快照
  if run_step "商业闭环-计费验收" bash "${SCRIPT_DIR}/billing_readiness_guard.sh"; then
    true
  else
    true
  fi >> "${tmp_status}"

  # 2) 可靠性：容量基线 + SLO
  if run_step "可靠性-容量基线" bash "${SCRIPT_DIR}/reliability_baseline.sh"; then
    true
  else
    true
  fi >> "${tmp_status}"

  if [[ -n "${PROMETHEUS_URL:-}" ]]; then
    if run_step "可靠性-SLO预算" bash "${SCRIPT_DIR}/slo_error_budget_report.sh"; then
      true
    else
      true
    fi >> "${tmp_status}"
  else
    if is_true "${COMMERCIAL_REQUIRE_SLO}"; then
      log_error "COMMERCIAL_REQUIRE_SLO=true 但未设置 PROMETHEUS_URL"
      echo "可靠性-SLO预算|FAIL" >> "${tmp_status}"
    else
      log_warn "未设置 PROMETHEUS_URL，已跳过 SLO 预算门禁"
      echo "可靠性-SLO预算|SKIP" >> "${tmp_status}"
    fi
  fi

  # 3) 合规安全：配置与最小权限复核
  if run_step "合规安全-门禁检查" bash "${SCRIPT_DIR}/security_compliance_guard.sh"; then
    true
  else
    true
  fi >> "${tmp_status}"

  # 4) 运维值守：P1 演练闭环
  if run_step "运维值守-P1演练" bash "${SCRIPT_DIR}/p1_incident_drill.sh"; then
    true
  else
    true
  fi >> "${tmp_status}"

  local pass_count fail_count skip_count
  pass_count="$(awk -F'|' '$2=="PASS"{c++} END {print c+0}' "${tmp_status}")"
  fail_count="$(awk -F'|' '$2=="FAIL"{c++} END {print c+0}' "${tmp_status}")"
  skip_count="$(awk -F'|' '$2=="SKIP"{c++} END {print c+0}' "${tmp_status}")"

  cat > "${COMMERCIAL_REPORT_FILE}" <<EOF
# LingSQL 商用验收总报告

- 执行时间: $(date -Iseconds)
- PASS: ${pass_count}
- FAIL: ${fail_count}
- SKIP: ${skip_count}

## 检查结果
$(awk -F'|' '{printf "- %s: %s\n", $1, $2}' "${tmp_status}")
EOF

  log_info "商用验收报告已写入: ${COMMERCIAL_REPORT_FILE}"
  if [[ "${fail_count}" -gt 0 ]]; then
    log_error "商用验收存在失败项，请按报告逐项整改。"
    exit 2
  fi
  log_info "商用验收通过。"
}

main "$@"
