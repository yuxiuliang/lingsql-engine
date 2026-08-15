#!/usr/bin/env bash
# 校验运行实例的持久审计投递状态，UNKNOWN 或 DEAD 会使 auditDelivery 组件返回 DOWN。

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8000}"
AUDIT_DELIVERY_HEALTH_URL="${AUDIT_DELIVERY_HEALTH_URL:-${BASE_URL%/}/actuator/health/auditDelivery}"
AUDIT_AUTH_TOKEN="${AUDIT_AUTH_TOKEN:-${PILOT_CONTROL_TOKEN:-}}"

log_error() {
  echo "[ERROR] $*" >&2
}

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    log_error "缺少命令: ${command}"
    exit 1
  fi
done

curl_args=(-sS)
if [[ -n "${AUDIT_AUTH_TOKEN}" ]]; then
  curl_args+=(-H "Authorization: Bearer ${AUDIT_AUTH_TOKEN}")
fi

response="$(curl "${curl_args[@]}" "${AUDIT_DELIVERY_HEALTH_URL}")"
status="$(jq -r '.status // "UNKNOWN"' <<< "${response}")"
if [[ "${status}" != "UP" ]]; then
  log_error "审计持久投递未就绪: status=${status}, url=${AUDIT_DELIVERY_HEALTH_URL}"
  exit 2
fi

echo "[INFO] 审计持久投递运行时健康检查通过。"
