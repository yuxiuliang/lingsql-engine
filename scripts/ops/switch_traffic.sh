#!/usr/bin/env bash
# LingSQL 蓝绿切流脚本（Nginx）
# 用法:
#   ./scripts/ops/switch_traffic.sh <blue|green> <port>
#
# 关键环境变量:
#   NGINX_UPSTREAM_CONF=/etc/nginx/conf.d/lingsql-upstream.conf
#   NGINX_TEST_CMD="nginx -t"
#   NGINX_RELOAD_CMD="systemctl reload nginx"
#   LINGSQL_DEPLOY_BASE=/opt/lingsql

set -euo pipefail

TARGET_SLOT="${1:-}"
TARGET_PORT="${2:-}"

if [[ "${TARGET_SLOT}" != "blue" && "${TARGET_SLOT}" != "green" ]]; then
  echo "[ERROR] 第一个参数必须是 blue 或 green" >&2
  echo "用法: ./scripts/ops/switch_traffic.sh <blue|green> <port>" >&2
  exit 1
fi

if [[ -z "${TARGET_PORT}" ]]; then
  echo "[ERROR] 缺少目标端口" >&2
  echo "用法: ./scripts/ops/switch_traffic.sh <blue|green> <port>" >&2
  exit 1
fi

NGINX_UPSTREAM_CONF="${NGINX_UPSTREAM_CONF:-/etc/nginx/conf.d/lingsql-upstream.conf}"
NGINX_TEST_CMD="${NGINX_TEST_CMD:-nginx -t}"
NGINX_RELOAD_CMD="${NGINX_RELOAD_CMD:-systemctl reload nginx}"
LINGSQL_DEPLOY_BASE="${LINGSQL_DEPLOY_BASE:-/opt/lingsql}"

RUN_DIR="${LINGSQL_DEPLOY_BASE}/run"
mkdir -p "${RUN_DIR}"

tmp_file="$(mktemp)"
cat > "${tmp_file}" <<EOF
upstream lingsql_backend {
    server 127.0.0.1:${TARGET_PORT} max_fails=3 fail_timeout=10s;
    keepalive 32;
}
EOF

install -D -m 0644 "${tmp_file}" "${NGINX_UPSTREAM_CONF}"
rm -f "${tmp_file}"

echo "[INFO] 已更新 upstream 配置: ${NGINX_UPSTREAM_CONF} -> 127.0.0.1:${TARGET_PORT}"

echo "[INFO] 执行 Nginx 配置检查: ${NGINX_TEST_CMD}"
bash -lc "${NGINX_TEST_CMD}"

echo "[INFO] 重载 Nginx: ${NGINX_RELOAD_CMD}"
bash -lc "${NGINX_RELOAD_CMD}"

echo "${TARGET_SLOT}" > "${RUN_DIR}/active_slot"
echo "${TARGET_PORT}" > "${RUN_DIR}/active_port"

printf "%s|%s|%s\n" "$(date -Iseconds)" "${TARGET_SLOT}" "${TARGET_PORT}" >> "${RUN_DIR}/traffic_switch_history.log"
echo "[INFO] 切流完成: slot=${TARGET_SLOT}, port=${TARGET_PORT}"

