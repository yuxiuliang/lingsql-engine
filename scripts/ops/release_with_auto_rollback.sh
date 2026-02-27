#!/usr/bin/env bash
# LingSQL 自动化发布编排脚本
# 流程: 蓝绿发布 -> 发布后冒烟 -> 失败自动回滚 -> 发送通知
#
# 用法:
#   BASE_URL=https://api.lingsql.com \
#   LINGSQL_DEPLOY_BASE=/opt/lingsql \
#   ./scripts/ops/release_with_auto_rollback.sh ./dist/lingsql-engine.jar v2026.02.28.1
#
# 可选环境变量:
#   RELEASE_POST_SMOKE_ENABLED=true
#   RELEASE_AUTO_ROLLBACK_ON_SMOKE_FAIL=true
#   RELEASE_NOTIFY_ENABLED=true
#   RELEASE_NOTIFY_SCRIPT=./scripts/ops/notify_release_event.sh

set -euo pipefail

ARTIFACT_PATH="${1:-}"
RELEASE_TAG="${2:-manual}"

RELEASE_POST_SMOKE_ENABLED="${RELEASE_POST_SMOKE_ENABLED:-true}"
RELEASE_AUTO_ROLLBACK_ON_SMOKE_FAIL="${RELEASE_AUTO_ROLLBACK_ON_SMOKE_FAIL:-true}"
RELEASE_NOTIFY_ENABLED="${RELEASE_NOTIFY_ENABLED:-true}"
RELEASE_NOTIFY_SCRIPT="${RELEASE_NOTIFY_SCRIPT:-./scripts/ops/notify_release_event.sh}"

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
  local value="${1:-}"
  [[ "${value}" == "true" || "${value}" == "1" || "${value}" == "yes" ]]
}

notify() {
  local level="$1"
  local title="$2"
  local message="$3"
  if ! is_true "${RELEASE_NOTIFY_ENABLED}"; then
    return 0
  fi
  if [[ ! -x "${RELEASE_NOTIFY_SCRIPT}" ]]; then
    log_warn "通知脚本不可执行，已跳过通知: ${RELEASE_NOTIFY_SCRIPT}"
    return 0
  fi
  "${RELEASE_NOTIFY_SCRIPT}" "${level}" "${title}" "${message}" || log_warn "通知发送失败"
}

run_blue_green_release() {
  log_info "开始蓝绿发布: artifact=${ARTIFACT_PATH}, tag=${RELEASE_TAG}"
  ./scripts/ops/release_blue_green.sh "${ARTIFACT_PATH}" "${RELEASE_TAG}"
}

run_post_smoke() {
  if ! is_true "${RELEASE_POST_SMOKE_ENABLED}"; then
    log_info "已跳过发布后冒烟（RELEASE_POST_SMOKE_ENABLED=${RELEASE_POST_SMOKE_ENABLED}）"
    return 0
  fi
  if [[ -z "${BASE_URL:-}" ]]; then
    log_error "启用发布后冒烟时必须设置 BASE_URL"
    return 1
  fi
  log_info "开始发布后冒烟: BASE_URL=${BASE_URL}"
  ./scripts/ops/release_smoke_test.sh
}

try_rollback() {
  log_warn "尝试执行自动回滚"
  if ./scripts/ops/release_rollback.sh; then
    notify "WARN" "LingSQL 发布回滚成功" "版本 ${RELEASE_TAG} 发布后冒烟失败，系统已自动回滚到上一稳定版本。"
    log_warn "自动回滚成功"
    return 0
  fi
  notify "ERROR" "LingSQL 发布回滚失败" "版本 ${RELEASE_TAG} 发布后冒烟失败，且自动回滚失败，请立即人工介入。"
  log_error "自动回滚失败，请立即人工处理"
  return 1
}

main() {
  if [[ -z "${ARTIFACT_PATH}" ]]; then
    log_error "参数不足"
    echo "用法: ./scripts/ops/release_with_auto_rollback.sh <artifact_path> [release_tag]"
    exit 1
  fi
  if [[ ! -f "${ARTIFACT_PATH}" ]]; then
    log_error "制品不存在: ${ARTIFACT_PATH}"
    exit 1
  fi

  local deploy_ok=false
  if run_blue_green_release; then
    deploy_ok=true
  fi
  if [[ "${deploy_ok}" != "true" ]]; then
    notify "ERROR" "LingSQL 发布失败" "版本 ${RELEASE_TAG} 蓝绿发布阶段失败，请检查发布日志。"
    exit 1
  fi

  if run_post_smoke; then
    notify "INFO" "LingSQL 发布成功" "版本 ${RELEASE_TAG} 已完成发布并通过发布后冒烟验收。"
    log_info "发布流程完成"
    exit 0
  fi

  notify "WARN" "LingSQL 发布后冒烟失败" "版本 ${RELEASE_TAG} 冒烟未通过，准备执行自动回滚。"
  if is_true "${RELEASE_AUTO_ROLLBACK_ON_SMOKE_FAIL}"; then
    if try_rollback; then
      exit 1
    fi
    exit 2
  fi

  notify "ERROR" "LingSQL 发布异常未回滚" "版本 ${RELEASE_TAG} 冒烟失败，且当前配置未启用自动回滚，请立即人工处理。"
  log_error "冒烟失败且未启用自动回滚"
  exit 1
}

main "$@"

