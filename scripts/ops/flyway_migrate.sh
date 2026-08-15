#!/usr/bin/env bash
# LingSQL Flyway 迁移入口：空库迁移、当前旧库显式接管、已纳管库校验迁移。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
ADOPTION_GUARD="${ROOT_DIR}/scripts/ops/schema_adoption_guard.sh"
BASELINE_VERSION="20260815.02"

log_info() {
  echo "[INFO] $*"
}

log_error() {
  echo "[ERROR] $*" >&2
}

load_env() {
  if [[ -f "${ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
  fi
  : "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD 不能为空}"
  : "${MYSQL_DATABASE:?MYSQL_DATABASE 不能为空}"
}

compose() {
  local args=(-f "${COMPOSE_FILE}")
  if [[ -f "${ENV_FILE}" ]]; then
    args+=(--env-file "${ENV_FILE}")
  fi
  docker compose "${args[@]}" "$@"
}

run_flyway() {
  compose run --rm migration "$@"
}

main() {
  command -v docker >/dev/null 2>&1 || { log_error "缺少命令: docker"; exit 1; }
  load_env
  if [[ ! -x "${ADOPTION_GUARD}" ]]; then
    log_error "数据库接管守卫不可执行: ${ADOPTION_GUARD}"
    exit 1
  fi

  local state
  state="$(ENV_FILE="${ENV_FILE}" "${ADOPTION_GUARD}")"
  case "${state}" in
    EMPTY)
      log_info "检测到空库，执行 Flyway 合并基线迁移。"
      run_flyway migrate
      ;;
    CURRENT)
      log_info "检测到与当前基线完全一致的未纳管库，执行显式 baseline 接管。"
      run_flyway "-baselineVersion=${BASELINE_VERSION}" \
        "-baselineDescription=已核验的LingSQL合并基线" baseline
      run_flyway migrate
      ;;
    MANAGED)
      log_info "检测到已纳管库，先校验历史再执行增量迁移。"
      run_flyway validate
      run_flyway migrate
      ;;
    *)
      log_error "未知数据库接管状态: ${state}"
      exit 1
      ;;
  esac
  run_flyway validate
  log_info "Flyway 迁移与校验完成。"
}

main "$@"
