#!/usr/bin/env bash
# LingSQL 合规与安全门禁脚本
# 用法:
#   ./scripts/ops/security_compliance_guard.sh
#
# 可选环境变量:
#   PROD_CONFIG=src/main/resources/application-prod.yml
#   RBAC_SEED=src/main/resources/rbac-seed.json
#   DOCKERFILE=Dockerfile
#   COMPOSE_FILE=docker-compose.yml
#   BOOTSTRAP_SCRIPT=scripts/ops/bootstrap_local_stack.sh

set -euo pipefail

PROD_CONFIG="${PROD_CONFIG:-src/main/resources/application-prod.yml}"
RBAC_SEED="${RBAC_SEED:-src/main/resources/rbac-seed.json}"
DOCKERFILE="${DOCKERFILE:-Dockerfile}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
BOOTSTRAP_SCRIPT="${BOOTSTRAP_SCRIPT:-scripts/ops/bootstrap_local_stack.sh}"

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

has_cmd() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1
}

search_fixed() {
  local pattern="$1"
  local file="$2"
  if has_cmd rg; then
    rg -n --fixed-strings "${pattern}" "${file}" >/dev/null 2>&1
    return $?
  fi
  grep -nF "${pattern}" "${file}" >/dev/null 2>&1
}

search_regex() {
  local pattern="$1"
  local file="$2"
  if has_cmd rg; then
    rg -n "${pattern}" "${file}" >/dev/null 2>&1
    return $?
  fi
  grep -nE "${pattern}" "${file}" >/dev/null 2>&1
}

assert_file_exists() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    log_error "文件不存在: ${file}"
    exit 1
  fi
}

assert_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! search_fixed "${pattern}" "${file}"; then
    log_error "${message}（期望包含: ${pattern}）"
    exit 1
  fi
}

main() {
  assert_file_exists "${PROD_CONFIG}"
  assert_file_exists "${RBAC_SEED}"
  assert_file_exists "${DOCKERFILE}"
  assert_file_exists "${COMPOSE_FILE}"
  assert_file_exists "${BOOTSTRAP_SCRIPT}"

  log_info "开始执行合规与安全门禁"
  log_info "生产配置文件: ${PROD_CONFIG}"

  # 1) 生产环境核心安全开关
  assert_contains "${PROD_CONFIG}" "data-encryption-enabled: \${LINGSQL_DATA_ENCRYPTION_ENABLED:true}" "生产环境必须默认启用敏感字段加密"
  assert_contains "${PROD_CONFIG}" "single-session-enabled: \${LINGSQL_AUTH_SINGLE_SESSION_ENABLED:true}" "生产环境必须默认启用单设备会话"
  assert_contains "${PROD_CONFIG}" "trust-proxy-headers: \${LINGSQL_SECURITY_RATE_TRUST_PROXY_HEADERS:false}" "生产环境应默认关闭代理头信任"
  assert_contains "${PROD_CONFIG}" "refresh-cookie-secure: \${LINGSQL_REFRESH_COOKIE_SECURE:true}" "生产环境 Refresh Cookie 必须默认仅 HTTPS 传输"
  assert_contains "${PROD_CONFIG}" "cors:" "生产配置缺少 CORS 段"
  assert_contains "${PROD_CONFIG}" "enabled: \${LINGSQL_CORS_ENABLED:false}" "生产环境 CORS 必须默认关闭"

  # 2) 不允许在生产配置写死敏感凭据
  if grep -nE "jwt-secret:" "${PROD_CONFIG}" | grep -v '\${' >/dev/null 2>&1; then
    log_error "检测到生产配置存在硬编码 jwt-secret"
    exit 1
  fi
  if grep -nE "password:" "${PROD_CONFIG}" | grep -v '\${' >/dev/null 2>&1; then
    log_error "检测到生产配置存在硬编码 password"
    exit 1
  fi

  # 3) 最小权限复核：权限编码不得使用通配符
  if grep -nE '"permCode"\s*:\s*"[^"]*\*' "${RBAC_SEED}" >/dev/null 2>&1; then
    log_error "权限种子存在通配符权限，违反最小权限原则"
    exit 1
  fi

  # 4) 运行时可观察能力必备项
  if ! search_regex "delivery-max-attempts:" "${PROD_CONFIG}"; then
    log_error "缺少审计持久投递配置，可能导致关键审计数据无法补偿"
    exit 1
  fi
  if ! search_regex "retention:" "${PROD_CONFIG}"; then
    log_error "缺少数据留存配置，无法执行合规留存策略"
    exit 1
  fi

  # 5) 标准交付容器必须保持非 root、只读根文件系统和本机默认端口绑定
  assert_contains "${DOCKERFILE}" "USER 10001:10001" "应用镜像必须使用非 root 用户"
  assert_contains "${COMPOSE_FILE}" '${MYSQL_BIND_ADDRESS:-127.0.0.1}:${MYSQL_PORT:-3306}:3306' "MySQL 默认只能绑定本机"
  assert_contains "${COMPOSE_FILE}" '${REDIS_BIND_ADDRESS:-127.0.0.1}:${REDIS_PORT:-6379}:6379' "Redis 默认只能绑定本机"
  assert_contains "${COMPOSE_FILE}" "read_only: true" "应用容器根文件系统必须只读"
  assert_contains "${COMPOSE_FILE}" "no-new-privileges:true" "应用容器必须禁止提权"
  if search_regex '^[[:space:]]+MYSQL_USER:' "${COMPOSE_FILE}" || search_regex '^[[:space:]]+MYSQL_PASSWORD:' "${COMPOSE_FILE}"; then
    log_error "禁止由 MySQL 官方入口为应用账号自动授予目标库全部权限"
    exit 1
  fi

  # 6) 初始化脚本必须先撤销历史授权，再授予并校验最小 DML 权限
  assert_contains "${BOOTSTRAP_SCRIPT}" "REVOKE ALL PRIVILEGES, GRANT OPTION" "应用数据库账号必须先撤销历史授权"
  assert_contains "${BOOTSTRAP_SCRIPT}" 'assert_database_privileges "${MYSQL_APP_USERNAME}" "${MYSQL_DATABASE}" "DELETE,INSERT,SELECT,UPDATE"' "应用数据库账号必须执行最小权限断言"

  log_info "合规与安全门禁通过"
}

main "$@"
