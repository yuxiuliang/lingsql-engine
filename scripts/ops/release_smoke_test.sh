#!/usr/bin/env bash
# LingSQL 发布后冒烟验收脚本
# 用法:
#   BASE_URL=http://127.0.0.1:8000 ./scripts/ops/release_smoke_test.sh
#
# 可选环境变量:
#   BASE_URL=http://127.0.0.1:8000
#   SMOKE_TIMEOUT_SECONDS=5
#   ACCESS_TOKEN=xxx              # 可选：登录后 token，用于校验 /api/v1/auth/me = 200

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8000}"
SMOKE_TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-5}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"

failures=0

log_info() {
  echo "[INFO] $*"
}

log_error() {
  echo "[ERROR] $*" >&2
}

normalize_base_url() {
  echo "${BASE_URL%/}"
}

request_code() {
  local method="$1"
  local url="$2"
  local auth_header="$3"
  if [[ -n "${auth_header}" ]]; then
    curl -sS -o /dev/null -m "${SMOKE_TIMEOUT_SECONDS}" -w "%{http_code}" -X "${method}" -H "${auth_header}" "${url}" || true
    return
  fi
  curl -sS -o /dev/null -m "${SMOKE_TIMEOUT_SECONDS}" -w "%{http_code}" -X "${method}" "${url}" || true
}

expect_http_code() {
  local title="$1"
  local method="$2"
  local path="$3"
  local expected="$4"
  local auth_header="${5:-}"
  local url
  url="$(normalize_base_url)${path}"
  local code
  code="$(request_code "${method}" "${url}" "${auth_header}")"
  if [[ "${code}" == "${expected}" ]]; then
    log_info "[PASS] ${title} (${code})"
    return
  fi
  log_error "[FAIL] ${title} 预期 ${expected}，实际 ${code:-N/A}，URL=${url}"
  failures=$((failures + 1))
}

expect_http_code_any() {
  local title="$1"
  local method="$2"
  local path="$3"
  local expected_codes="$4"
  local auth_header="${5:-}"
  local url
  url="$(normalize_base_url)${path}"
  local code
  code="$(request_code "${method}" "${url}" "${auth_header}")"

  IFS=',' read -r -a code_array <<< "${expected_codes}"
  for one in "${code_array[@]}"; do
    if [[ "${code}" == "${one}" ]]; then
      log_info "[PASS] ${title} (${code})"
      return
    fi
  done

  log_error "[FAIL] ${title} 预期之一 ${expected_codes}，实际 ${code:-N/A}，URL=${url}"
  failures=$((failures + 1))
}

main() {
  if ! command -v curl >/dev/null 2>&1; then
    log_error "缺少 curl，请先安装"
    exit 1
  fi

  log_info "开始执行发布后冒烟验收，BASE_URL=$(normalize_base_url)"

  expect_http_code "健康检查 (health)" "GET" "/actuator/health" "200"
  expect_http_code "健康检查 (readiness)" "GET" "/actuator/health/readiness" "200"
  expect_http_code "管理端首页可访问" "GET" "/admin/index.html" "200"
  expect_http_code "登录路由可访问" "GET" "/login" "200"
  expect_http_code "未登录访问 /auth/me 应返回 401" "GET" "/api/v1/auth/me" "401"
  expect_http_code_any "Prometheus 端点应受保护" "GET" "/actuator/prometheus" "401,403"

  if [[ -n "${ACCESS_TOKEN}" ]]; then
    expect_http_code "登录态访问 /auth/me" "GET" "/api/v1/auth/me" "200" "Authorization: Bearer ${ACCESS_TOKEN}"
  else
    log_info "未提供 ACCESS_TOKEN，跳过登录态接口验收"
  fi

  if (( failures > 0 )); then
    log_error "冒烟验收失败，失败项数量: ${failures}"
    exit 1
  fi

  log_info "冒烟验收通过"
}

main "$@"

