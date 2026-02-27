#!/usr/bin/env bash
# LingSQL 蓝绿回滚脚本
# 用法:
#   ./scripts/ops/release_rollback.sh [target_slot]
#
# 说明:
#   1) 不传 target_slot 时，默认回滚到当前活跃槽位的另一侧
#   2) 仅切回已有槽位制品（slot/app.jar），不修改历史发布包
#
# 关键环境变量:
#   LINGSQL_DEPLOY_BASE=/opt/lingsql
#   LINGSQL_BLUE_SERVICE=lingsql-blue
#   LINGSQL_GREEN_SERVICE=lingsql-green
#   LINGSQL_BLUE_PORT=18080
#   LINGSQL_GREEN_PORT=28080
#   LINGSQL_HEALTH_PATH=/actuator/health/readiness
#   LINGSQL_HEALTH_RETRIES=30
#   LINGSQL_HEALTH_INTERVAL_SECONDS=2
#   LINGSQL_SWITCH_HOOK=""             # 可选：流量切换 hook，入参 <slot> <port>
#   LINGSQL_STOP_FAILED_SLOT=true      # 可选：回滚成功后是否停止故障槽位
#   LINGSQL_START_CMD_TEMPLATE=""      # 可选：无 systemd 时启用，支持变量 {slot} {port} {artifact} {slot_dir}

set -euo pipefail

log_info() {
  echo "[INFO] $*"
}

log_warn() {
  echo "[WARN] $*"
}

log_error() {
  echo "[ERROR] $*" >&2
}

render_template() {
  local template="$1"
  local slot="$2"
  local port="$3"
  local artifact="$4"
  local slot_dir="$5"
  template="${template//\{slot\}/${slot}}"
  template="${template//\{port\}/${port}}"
  template="${template//\{artifact\}/${artifact}}"
  template="${template//\{slot_dir\}/${slot_dir}}"
  echo "${template}"
}

service_exists() {
  local service_name="$1"
  systemctl list-unit-files "${service_name}.service" --no-legend 2>/dev/null | grep -q "^${service_name}\.service"
}

start_slot_service() {
  local slot="$1"
  local service_name="$2"
  local port="$3"
  local artifact="$4"
  local slot_dir="$5"

  if command -v systemctl >/dev/null 2>&1 && service_exists "${service_name}"; then
    log_info "重启回滚槽位服务: ${service_name}"
    systemctl restart "${service_name}"
    if ! systemctl is-active --quiet "${service_name}"; then
      log_error "回滚槽位服务启动失败: ${service_name}"
      exit 1
    fi
    return
  fi

  if [[ -z "${LINGSQL_START_CMD_TEMPLATE:-}" ]]; then
    log_error "未发现可用的 systemd 服务，且未配置 LINGSQL_START_CMD_TEMPLATE"
    log_error "请先配置启动方式后重试"
    exit 1
  fi

  local start_cmd
  start_cmd="$(render_template "${LINGSQL_START_CMD_TEMPLATE}" "${slot}" "${port}" "${artifact}" "${slot_dir}")"
  log_info "执行自定义回滚启动命令: ${start_cmd}"
  bash -lc "${start_cmd}"
}

stop_service_if_needed() {
  local service_name="$1"
  if [[ "${LINGSQL_STOP_FAILED_SLOT:-true}" != "true" ]]; then
    return
  fi
  if command -v systemctl >/dev/null 2>&1 && service_exists "${service_name}"; then
    log_info "停止故障槽位服务: ${service_name}"
    systemctl stop "${service_name}"
  else
    log_warn "LINGSQL_STOP_FAILED_SLOT=true，但未找到可停止的 systemd 服务: ${service_name}"
  fi
}

wait_for_health() {
  local slot="$1"
  local port="$2"
  local health_path="$3"
  local retries="$4"
  local interval="$5"
  local url="http://127.0.0.1:${port}${health_path}"

  if ! command -v curl >/dev/null 2>&1; then
    log_warn "未找到 curl，跳过健康检查: ${url}"
    return
  fi

  for ((i = 1; i <= retries; i++)); do
    local code
    code="$(curl -sS -o /dev/null -m 5 -w "%{http_code}" "${url}" || true)"
    if [[ "${code}" == "200" ]]; then
      log_info "回滚槽位健康检查通过: ${url}"
      return
    fi
    log_info "回滚槽位健康检查未通过（第 ${i}/${retries} 次，状态码: ${code:-N/A}）"
    sleep "${interval}"
  done

  log_error "回滚槽位健康检查超时: ${url}"
  exit 1
}

run_switch_hook() {
  local slot="$1"
  local port="$2"
  if [[ -z "${LINGSQL_SWITCH_HOOK:-}" ]]; then
    return
  fi
  log_info "执行流量切换 Hook: ${LINGSQL_SWITCH_HOOK} ${slot} ${port}"
  bash -lc "${LINGSQL_SWITCH_HOOK} ${slot} ${port}"
}

main() {
  local expected_slot="${1:-}"
  if [[ -n "${expected_slot}" && "${expected_slot}" != "blue" && "${expected_slot}" != "green" ]]; then
    log_error "target_slot 仅支持 blue 或 green"
    exit 1
  fi

  local deploy_base="${LINGSQL_DEPLOY_BASE:-/opt/lingsql}"
  local run_dir="${deploy_base}/run"
  local slot_file="${run_dir}/active_slot"
  local history_file="${run_dir}/release_history.log"
  local rollback_history_file="${run_dir}/rollback_history.log"

  local blue_dir="${deploy_base}/blue"
  local green_dir="${deploy_base}/green"

  local blue_service="${LINGSQL_BLUE_SERVICE:-lingsql-blue}"
  local green_service="${LINGSQL_GREEN_SERVICE:-lingsql-green}"
  local blue_port="${LINGSQL_BLUE_PORT:-18080}"
  local green_port="${LINGSQL_GREEN_PORT:-28080}"
  local health_path="${LINGSQL_HEALTH_PATH:-/actuator/health/readiness}"
  local health_retries="${LINGSQL_HEALTH_RETRIES:-30}"
  local health_interval="${LINGSQL_HEALTH_INTERVAL_SECONDS:-2}"

  mkdir -p "${run_dir}" "${blue_dir}" "${green_dir}"

  local active_slot="blue"
  if [[ -f "${slot_file}" ]]; then
    active_slot="$(tr -d '[:space:]' < "${slot_file}")"
  fi
  if [[ "${active_slot}" != "blue" && "${active_slot}" != "green" ]]; then
    log_warn "active_slot 文件异常（${active_slot}），默认按 blue 处理"
    active_slot="blue"
  fi

  local rollback_slot="${expected_slot}"
  if [[ -z "${rollback_slot}" ]]; then
    rollback_slot="green"
    if [[ "${active_slot}" == "green" ]]; then
      rollback_slot="blue"
    fi
  fi

  if [[ "${rollback_slot}" == "${active_slot}" ]]; then
    log_error "目标回滚槽位与当前活跃槽位一致，无需回滚: ${rollback_slot}"
    exit 1
  fi

  local failed_slot="${active_slot}"
  local rollback_dir="${green_dir}"
  local rollback_service="${green_service}"
  local rollback_port="${green_port}"
  local failed_service="${blue_service}"
  if [[ "${rollback_slot}" == "blue" ]]; then
    rollback_dir="${blue_dir}"
    rollback_service="${blue_service}"
    rollback_port="${blue_port}"
    failed_service="${green_service}"
  fi

  local rollback_artifact="${rollback_dir}/app.jar"
  if [[ ! -f "${rollback_artifact}" ]]; then
    log_error "回滚槽位缺少制品: ${rollback_artifact}"
    log_error "请先执行一次完整发布，或手动放置 app.jar 后再回滚"
    exit 1
  fi

  local rollback_release_id="unknown"
  if [[ -f "${rollback_dir}/release_id" ]]; then
    rollback_release_id="$(tr -d '[:space:]' < "${rollback_dir}/release_id")"
  fi

  log_info "当前活跃槽位: ${active_slot}"
  log_info "目标回滚槽位: ${rollback_slot}"
  log_info "回滚版本标识: ${rollback_release_id}"

  start_slot_service "${rollback_slot}" "${rollback_service}" "${rollback_port}" "${rollback_artifact}" "${rollback_dir}"
  wait_for_health "${rollback_slot}" "${rollback_port}" "${health_path}" "${health_retries}" "${health_interval}"

  run_switch_hook "${rollback_slot}" "${rollback_port}"
  echo "${rollback_slot}" > "${slot_file}"
  stop_service_if_needed "${failed_service}"

  local operator="${SUDO_USER:-${USER:-unknown}}"
  printf "%s|%s|%s|%s|%s\n" \
    "$(date -Iseconds)" "${rollback_slot}" "${rollback_release_id}" "${failed_slot}" "${operator}" >> "${rollback_history_file}"
  printf "%s|ROLLBACK|%s|%s|%s|%s\n" \
    "$(date -Iseconds)" "${rollback_release_id}" "${rollback_slot}" "rollback" "${operator}" >> "${history_file}"

  log_info "回滚完成，当前活跃槽位: ${rollback_slot}"
  log_info "回滚记录: ${rollback_history_file}"
}

main "$@"
