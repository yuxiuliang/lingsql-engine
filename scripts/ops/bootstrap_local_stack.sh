#!/usr/bin/env bash
# LingSQL 本地/PoC 一键拉起脚本（Docker Compose）
# 用法：
#   cp .env.example .env
#   # 修改 .env 中密码与密钥
#   ./scripts/ops/bootstrap_local_stack.sh
#
# 可选环境变量：
#   SKIP_SEED=true      跳过商业化基线种子数据
#   SKIP_APP_START=true 仅启动 MySQL/Redis 并初始化数据库

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"
ENV_FILE="${ROOT_DIR}/.env"
SCHEMA_FILE="${ROOT_DIR}/sql/mysql.sql"
SEED_FILE="${ROOT_DIR}/sql/seed_commercial_baseline.sql"
DEMO_SCHEMA_FILE="${ROOT_DIR}/sql/demo_sales_inventory.sql"

SKIP_SEED="${SKIP_SEED:-false}"
SKIP_APP_START="${SKIP_APP_START:-false}"
LINGSQL_DEMO_ENABLED="${LINGSQL_DEMO_ENABLED:-true}"

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

load_env() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    log_error "未找到 ${ENV_FILE}，请先复制 .env.example 并填写参数。"
    exit 1
  fi
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  : "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD 不能为空}"
  : "${MYSQL_DATABASE:?MYSQL_DATABASE 不能为空}"
  : "${MYSQL_APP_USERNAME:?MYSQL_APP_USERNAME 不能为空}"
  : "${MYSQL_APP_PASSWORD:?MYSQL_APP_PASSWORD 不能为空}"
  : "${REDIS_PASSWORD:?REDIS_PASSWORD 不能为空}"
  : "${LINGSQL_JWT_SECRET:?LINGSQL_JWT_SECRET 不能为空}"
  : "${LINGSQL_DATA_ENCRYPTION_KEY:?LINGSQL_DATA_ENCRYPTION_KEY 不能为空}"
  : "${LINGSQL_ADMIN_PASSWORD:?LINGSQL_ADMIN_PASSWORD 不能为空}"
  validate_demo_identifier "MYSQL_DATABASE" "${MYSQL_DATABASE}"
  validate_demo_identifier "MYSQL_APP_USERNAME" "${MYSQL_APP_USERNAME}"
  if ! [[ "${MYSQL_APP_PASSWORD}" =~ ^[A-Za-z0-9_@%+=:.,-]+$ ]]; then
    log_error "MYSQL_APP_PASSWORD 包含不支持的字符，请使用字母、数字或 _@%+=:.,-。"
    exit 1
  fi
  reject_placeholder "MYSQL_ROOT_PASSWORD" "${MYSQL_ROOT_PASSWORD}"
  reject_placeholder "MYSQL_APP_PASSWORD" "${MYSQL_APP_PASSWORD}"
  reject_placeholder "REDIS_PASSWORD" "${REDIS_PASSWORD}"
  reject_placeholder "LINGSQL_JWT_SECRET" "${LINGSQL_JWT_SECRET}"
  reject_placeholder "LINGSQL_DATA_ENCRYPTION_KEY" "${LINGSQL_DATA_ENCRYPTION_KEY}"
  reject_placeholder "LINGSQL_ADMIN_PASSWORD" "${LINGSQL_ADMIN_PASSWORD}"
}

ensure_app_database_user() {
  log_info "校验应用数据库最小权限账号..."
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" exec -T mysql \
    mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" -e \
    "CREATE USER IF NOT EXISTS '${MYSQL_APP_USERNAME}'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
     ALTER USER '${MYSQL_APP_USERNAME}'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
     GRANT SELECT, INSERT, UPDATE, DELETE ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_APP_USERNAME}'@'%';
     FLUSH PRIVILEGES;"
}

reject_placeholder() {
  local name="$1"
  local value="$2"
  if [[ "${value}" == ChangeMe_* || "${value}" == ReplaceWith* ]]; then
    log_error "${name} 仍是示例占位值，请先修改 .env。"
    exit 1
  fi
}

wait_mysql_ready() {
  log_info "等待 MySQL 就绪..."
  for _ in {1..30}; do
    if docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" exec -T mysql \
      mysqladmin ping -h 127.0.0.1 -uroot "-p${MYSQL_ROOT_PASSWORD}" >/dev/null 2>&1; then
      log_info "MySQL 已就绪。"
      return 0
    fi
    sleep 2
  done
  log_error "MySQL 启动超时。"
  exit 1
}

init_schema() {
  if [[ ! -f "${SCHEMA_FILE}" ]]; then
    log_error "未找到 schema 文件: ${SCHEMA_FILE}"
    exit 1
  fi
  local table_count
  table_count="$(docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" exec -T mysql \
    mysql -N -uroot "-p${MYSQL_ROOT_PASSWORD}" -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name='t_lingsql_engine_tenant';")"
  if [[ "${table_count}" -gt 0 ]]; then
    log_info "数据库结构已存在，跳过基线 DDL；版本升级请执行受控迁移脚本。"
    return 0
  fi
  log_info "开始初始化数据库结构..."
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" exec -T mysql \
    mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${SCHEMA_FILE}"
  log_info "数据库结构初始化完成。"
}

validate_demo_identifier() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[A-Za-z0-9_]+$ ]]; then
    log_error "${name} 只能包含字母、数字和下划线。"
    exit 1
  fi
}

init_demo_database() {
  if ! is_true "${LINGSQL_DEMO_ENABLED}"; then
    log_info "已跳过销售库存演示库。"
    return 0
  fi
  : "${LINGSQL_DEMO_DATABASE:?LINGSQL_DEMO_DATABASE 不能为空}"
  : "${LINGSQL_DEMO_USERNAME:?LINGSQL_DEMO_USERNAME 不能为空}"
  : "${LINGSQL_DEMO_PASSWORD:?LINGSQL_DEMO_PASSWORD 不能为空}"
  validate_demo_identifier "LINGSQL_DEMO_DATABASE" "${LINGSQL_DEMO_DATABASE}"
  validate_demo_identifier "LINGSQL_DEMO_USERNAME" "${LINGSQL_DEMO_USERNAME}"
  if ! [[ "${LINGSQL_DEMO_PASSWORD}" =~ ^[A-Za-z0-9_@%+=:.,-]+$ ]]; then
    log_error "LINGSQL_DEMO_PASSWORD 包含不支持的字符，请使用字母、数字或 _@%+=:.,-。"
    exit 1
  fi
  reject_placeholder "LINGSQL_DEMO_PASSWORD" "${LINGSQL_DEMO_PASSWORD}"
  if [[ ! -f "${DEMO_SCHEMA_FILE}" ]]; then
    log_error "未找到演示库文件: ${DEMO_SCHEMA_FILE}"
    exit 1
  fi

  log_info "初始化销售库存演示库与只读账号..."
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" exec -T mysql \
    mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" -e \
    "CREATE DATABASE IF NOT EXISTS \`${LINGSQL_DEMO_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
     CREATE USER IF NOT EXISTS '${LINGSQL_DEMO_USERNAME}'@'%' IDENTIFIED BY '${LINGSQL_DEMO_PASSWORD}';
     ALTER USER '${LINGSQL_DEMO_USERNAME}'@'%' IDENTIFIED BY '${LINGSQL_DEMO_PASSWORD}';
     GRANT SELECT ON \`${LINGSQL_DEMO_DATABASE}\`.* TO '${LINGSQL_DEMO_USERNAME}'@'%';
     FLUSH PRIVILEGES;"
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" exec -T mysql \
    mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" "${LINGSQL_DEMO_DATABASE}" < "${DEMO_SCHEMA_FILE}"
  log_info "演示数据源: jdbc:mysql://mysql:3306/${LINGSQL_DEMO_DATABASE}"
  log_info "演示只读账号: ${LINGSQL_DEMO_USERNAME}"
}

seed_commercial_baseline() {
  if is_true "${SKIP_SEED}"; then
    log_info "已跳过商业化基线种子数据。"
    return 0
  fi
  if [[ ! -f "${SEED_FILE}" ]]; then
    log_error "未找到种子文件: ${SEED_FILE}"
    exit 1
  fi
  log_info "开始导入商业化基线种子数据..."
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" exec -T mysql \
    mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${SEED_FILE}"
  log_info "商业化基线种子导入完成。"
}

wait_app_ready() {
  local app_port
  app_port="${APP_PORT:-8000}"
  log_info "等待应用健康检查通过..."
  for _ in {1..60}; do
    if curl -fsS "http://127.0.0.1:${app_port}/actuator/health/readiness" >/dev/null 2>&1; then
      log_info "应用已就绪： http://127.0.0.1:${app_port}"
      return 0
    fi
    sleep 2
  done
  log_error "应用就绪超时，请执行: docker compose logs -f app"
  exit 1
}

main() {
  require_cmd docker
  require_cmd curl
  load_env

  log_info "启动基础依赖（MySQL/Redis）..."
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d mysql redis

  wait_mysql_ready
  init_schema
  ensure_app_database_user
  seed_commercial_baseline
  init_demo_database

  if is_true "${SKIP_APP_START}"; then
    log_info "已按配置跳过应用启动。"
    exit 0
  fi

  log_info "启动 LingSQL 应用..."
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d app
  wait_app_ready

  log_info "一键拉起完成。可访问:"
  log_info "- 管理后台: http://127.0.0.1:${APP_PORT:-8000}/admin"
  log_info "- 健康检查: http://127.0.0.1:${APP_PORT:-8000}/actuator/health"
}

main "$@"

