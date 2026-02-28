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

SKIP_SEED="${SKIP_SEED:-false}"
SKIP_APP_START="${SKIP_APP_START:-false}"

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
  : "${REDIS_PASSWORD:?REDIS_PASSWORD 不能为空}"
  : "${LINGSQL_JWT_SECRET:?LINGSQL_JWT_SECRET 不能为空}"
  : "${LINGSQL_DATA_ENCRYPTION_KEY:?LINGSQL_DATA_ENCRYPTION_KEY 不能为空}"
  : "${LINGSQL_ADMIN_PASSWORD:?LINGSQL_ADMIN_PASSWORD 不能为空}"
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
  log_info "开始初始化数据库结构..."
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" exec -T mysql \
    mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${SCHEMA_FILE}"
  log_info "数据库结构初始化完成。"
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
  seed_commercial_baseline

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

