#!/usr/bin/env bash
# LingSQL P1 事件演练脚本
# 用法:
#   ./scripts/ops/p1_incident_drill.sh
#
# 可选环境变量:
#   DRILL_ID=drill-20260227-01
#   INCIDENT_LEVEL=P1
#   DETECTED_AT=2026-02-27T09:00:00+08:00
#   ACKED_AT=2026-02-27T09:03:00+08:00
#   MITIGATED_AT=2026-02-27T09:18:00+08:00
#   P1_MTTA_TARGET_MIN=5
#   P1_MTTR_TARGET_MIN=30
#   REPORT_FILE=./reports/p1_drill.md

set -euo pipefail

DRILL_ID="${DRILL_ID:-drill-$(date +%Y%m%d-%H%M%S)}"
INCIDENT_LEVEL="${INCIDENT_LEVEL:-P1}"
DETECTED_AT="${DETECTED_AT:-}"
ACKED_AT="${ACKED_AT:-}"
MITIGATED_AT="${MITIGATED_AT:-}"
P1_MTTA_TARGET_MIN="${P1_MTTA_TARGET_MIN:-5}"
P1_MTTR_TARGET_MIN="${P1_MTTR_TARGET_MIN:-30}"
REPORT_FILE="${REPORT_FILE:-./reports/${DRILL_ID}.md}"

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

parse_ts() {
  local value="$1"
  date -d "${value}" +%s
}

main() {
  require_cmd date
  require_cmd awk

  if [[ "${INCIDENT_LEVEL}" != "P1" ]]; then
    log_error "当前脚本仅支持 P1 演练，INCIDENT_LEVEL=${INCIDENT_LEVEL}"
    exit 1
  fi

  if [[ -z "${DETECTED_AT}" || -z "${ACKED_AT}" || -z "${MITIGATED_AT}" ]]; then
    log_error "必须提供真实演练的 DETECTED_AT、ACKED_AT、MITIGATED_AT，禁止自动生成演练时间。"
    exit 1
  fi
  local detected_ts acked_ts mitigated_ts
  detected_ts="$(parse_ts "${DETECTED_AT}")"
  acked_ts="$(parse_ts "${ACKED_AT}")"
  mitigated_ts="$(parse_ts "${MITIGATED_AT}")"

  if [[ "${acked_ts}" -lt "${detected_ts}" ]]; then
    log_error "ACKED_AT 不能早于 DETECTED_AT"
    exit 1
  fi
  if [[ "${mitigated_ts}" -lt "${acked_ts}" ]]; then
    log_error "MITIGATED_AT 不能早于 ACKED_AT"
    exit 1
  fi

  local mtta_min mttr_min total_min
  mtta_min="$(awk -v a="${acked_ts}" -v d="${detected_ts}" 'BEGIN { printf "%.2f", (a-d)/60 }')"
  mttr_min="$(awk -v m="${mitigated_ts}" -v a="${acked_ts}" 'BEGIN { printf "%.2f", (m-a)/60 }')"
  total_min="$(awk -v m="${mitigated_ts}" -v d="${detected_ts}" 'BEGIN { printf "%.2f", (m-d)/60 }')"

  local status="PASS"
  if ! awk -v actual="${mtta_min}" -v target="${P1_MTTA_TARGET_MIN}" 'BEGIN { exit (actual <= target + 0.000001) ? 0 : 1 }'; then
    status="FAIL"
  fi
  if ! awk -v actual="${mttr_min}" -v target="${P1_MTTR_TARGET_MIN}" 'BEGIN { exit (actual <= target + 0.000001) ? 0 : 1 }'; then
    status="FAIL"
  fi

  mkdir -p "$(dirname "${REPORT_FILE}")"
  cat > "${REPORT_FILE}" <<EOF
# LingSQL P1 事件演练报告

- 演练编号: ${DRILL_ID}
- 事件等级: ${INCIDENT_LEVEL}
- 发现时间: ${DETECTED_AT}
- 响应确认时间: ${ACKED_AT}
- 缓解完成时间: ${MITIGATED_AT}
- MTTA: ${mtta_min} 分钟（目标 <= ${P1_MTTA_TARGET_MIN}）
- MTTR: ${mttr_min} 分钟（目标 <= ${P1_MTTR_TARGET_MIN}）
- 端到端恢复时长: ${total_min} 分钟
- 演练结果: ${status}

## 复盘建议

1. 若 MTTA 超标，优先检查告警触达链路与值班轮值配置。
2. 若 MTTR 超标，补齐标准化故障定位步骤与一键回滚预案。
3. 将本次演练结论同步到值班手册与发布门禁清单。
EOF

  log_info "P1 演练报告已生成: ${REPORT_FILE}"
  log_info "MTTA=${mtta_min} 分钟, MTTR=${mttr_min} 分钟, 结果=${status}"
  if [[ "${status}" != "PASS" ]]; then
    log_error "P1 演练未达标，请整改后重试。"
    exit 2
  fi
}

main "$@"
