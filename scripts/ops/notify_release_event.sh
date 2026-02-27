#!/usr/bin/env bash
# LingSQL 发布事件通知脚本
# 用法:
#   RELEASE_NOTIFY_WEBHOOK_URL=https://example.com/hook \
#   ./scripts/ops/notify_release_event.sh INFO "发布成功" "版本 v2026.02.27.1 已完成发布"
#
# 支持通道环境变量:
#   RELEASE_NOTIFY_WEBHOOK_URL
#   RELEASE_NOTIFY_DINGTALK_WEBHOOK_URL
#   RELEASE_NOTIFY_WECOM_WEBHOOK_URL
#   RELEASE_NOTIFY_REQUIRE_ALL_CHANNELS=false

set -euo pipefail

LEVEL="${1:-INFO}"
TITLE="${2:-LingSQL 发布通知}"
MESSAGE="${3:-}"

RELEASE_NOTIFY_WEBHOOK_URL="${RELEASE_NOTIFY_WEBHOOK_URL:-}"
RELEASE_NOTIFY_DINGTALK_WEBHOOK_URL="${RELEASE_NOTIFY_DINGTALK_WEBHOOK_URL:-}"
RELEASE_NOTIFY_WECOM_WEBHOOK_URL="${RELEASE_NOTIFY_WECOM_WEBHOOK_URL:-}"
RELEASE_NOTIFY_REQUIRE_ALL_CHANNELS="${RELEASE_NOTIFY_REQUIRE_ALL_CHANNELS:-false}"

log_info() {
  echo "[INFO] $*"
}

log_warn() {
  echo "[WARN] $*"
}

json_escape() {
  local input="${1:-}"
  input="${input//\\/\\\\}"
  input="${input//\"/\\\"}"
  input="${input//$'\n'/\\n}"
  input="${input//$'\r'/}"
  echo "${input}"
}

is_true() {
  local value="${1:-}"
  [[ "${value}" == "true" || "${value}" == "1" || "${value}" == "yes" ]]
}

post_json() {
  local url="$1"
  local payload="$2"
  local status
  status="$(curl -sS -o /tmp/lingsql_notify_resp.$$ -m 8 -w "%{http_code}" \
    -H "Content-Type: application/json" \
    -X POST \
    --data "${payload}" \
    "${url}" || true)"
  if [[ "${status}" =~ ^2[0-9][0-9]$ ]]; then
    return 0
  fi
  log_warn "通知发送失败: status=${status:-N/A}, url=${url}"
  return 1
}

send_generic() {
  local url="$1"
  local payload
  payload="$(cat <<EOF
{"app":"lingsql-engine","level":"$(json_escape "${LEVEL}")","title":"$(json_escape "${TITLE}")","message":"$(json_escape "${MESSAGE}")","timestamp":"$(date -Iseconds)"}
EOF
)"
  post_json "${url}" "${payload}"
}

send_dingtalk() {
  local url="$1"
  local text
  text="### ${TITLE}\n\n- 等级: ${LEVEL}\n- 时间: $(date -Iseconds)\n\n${MESSAGE}"
  local payload
  payload="$(cat <<EOF
{"msgtype":"markdown","markdown":{"title":"$(json_escape "${TITLE}")","text":"$(json_escape "${text}")"}}
EOF
)"
  post_json "${url}" "${payload}"
}

send_wecom() {
  local url="$1"
  local content
  content="**${TITLE}**\n> 等级: ${LEVEL}\n> 时间: $(date -Iseconds)\n\n${MESSAGE}"
  local payload
  payload="$(cat <<EOF
{"msgtype":"markdown","markdown":{"content":"$(json_escape "${content}")"}}
EOF
)"
  post_json "${url}" "${payload}"
}

main() {
  local has_target=false
  local any_success=false
  local all_success=true

  if [[ -n "${RELEASE_NOTIFY_WEBHOOK_URL}" ]]; then
    has_target=true
    if send_generic "${RELEASE_NOTIFY_WEBHOOK_URL}"; then
      any_success=true
    else
      all_success=false
    fi
  fi

  if [[ -n "${RELEASE_NOTIFY_DINGTALK_WEBHOOK_URL}" ]]; then
    has_target=true
    if send_dingtalk "${RELEASE_NOTIFY_DINGTALK_WEBHOOK_URL}"; then
      any_success=true
    else
      all_success=false
    fi
  fi

  if [[ -n "${RELEASE_NOTIFY_WECOM_WEBHOOK_URL}" ]]; then
    has_target=true
    if send_wecom "${RELEASE_NOTIFY_WECOM_WEBHOOK_URL}"; then
      any_success=true
    else
      all_success=false
    fi
  fi

  if [[ "${has_target}" != "true" ]]; then
    log_info "未配置通知通道，跳过发送"
    return 0
  fi

  if is_true "${RELEASE_NOTIFY_REQUIRE_ALL_CHANNELS}"; then
    if [[ "${all_success}" == "true" ]]; then
      return 0
    fi
    return 1
  fi

  if [[ "${any_success}" == "true" ]]; then
    return 0
  fi
  return 1
}

main "$@"

