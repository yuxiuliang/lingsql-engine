#!/usr/bin/env bash
# LingSQL 蓝绿发布脚本
# 用法:
#   ./scripts/ops/release_blue_green.sh <artifact_path> [release_tag]
#
# 依赖:
#   1) 优先使用 systemd 服务（lingsql-blue / lingsql-green）
#   2) 若未使用 systemd，可通过 LINGSQL_START_CMD_TEMPLATE 提供启动命令模板
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
#   LINGSQL_KEEP_RELEASES=10
#   LINGSQL_SWITCH_HOOK=""           # 可选：流量切换 hook，入参 <slot> <port>
#   LINGSQL_STOP_OLD_SLOT=false      # 可选：发布成功后是否停止旧槽位
#   LINGSQL_START_CMD_TEMPLATE=""    # 可选：无 systemd 时启用，支持变量 {slot} {port} {artifact} {slot_dir}

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

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    log_error "缺少命令: ${cmd}"
    exit 1
  fi
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
    log_info "重启服务: ${service_name}"
    systemctl restart "${service_name}"
    if ! systemctl is-active --quiet "${service_name}"; then
      log_error "服务启动失败: ${service_name}"
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
  log_info "执行自定义启动命令: ${start_cmd}"
  bash -lc "${start_cmd}"
}

stop_slot_service_if_needed() {
  local service_name="$1"

  if [[ "${LINGSQL_STOP_OLD_SLOT:-false}" != "true" ]]; then
    return
  fi

  if command -v systemctl >/dev/null 2>&1 && service_exists "${service_name}"; then
    log_info "按配置停止旧槽位服务: ${service_name}"
    systemctl stop "${service_name}"
  else
    log_warn "LINGSQL_STOP_OLD_SLOT=true，但未找到可停止的 systemd 服务: ${service_name}"
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
      log_info "槽位 ${slot} 健康检查通过: ${url}"
      return
    fi
    log_info "槽位 ${slot} 健康检查未通过（第 ${i}/${retries} 次，状态码: ${code:-N/A}）"
    sleep "${interval}"
  done

  log_error "槽位 ${slot} 健康检查超时: ${url}"
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

prune_old_releases() {
  local releases_dir="$1"
  local keep_releases="$2"
  if ! [[ "${keep_releases}" =~ ^[0-9]+$ ]]; then
    log_warn "LINGSQL_KEEP_RELEASES 不是数字，跳过清理: ${keep_releases}"
    return
  fi
  if (( keep_releases <= 0 )); then
    log_warn "LINGSQL_KEEP_RELEASES<=0，跳过清理"
    return
  fi

  mapfile -t release_dirs < <(find "${releases_dir}" -mindepth 1 -maxdepth 1 -type d | sort)
  if (( ${#release_dirs[@]} <= keep_releases )); then
    return
  fi

  local delete_count=$(( ${#release_dirs[@]} - keep_releases ))
  for ((i = 0; i < delete_count; i++)); do
    log_info "清理旧发布目录: ${release_dirs[${i}]}"
    rm -rf "${release_dirs[${i}]}"
  done
}

main() {
  local artifact_path="${1:-}"
  local release_tag="${2:-manual}"
  if [[ -z "${artifact_path}" ]]; then
    log_error "参数不足"
    echo "用法: ./scripts/ops/release_blue_green.sh <artifact_path> [release_tag]"
    exit 1
  fi
  if [[ ! -f "${artifact_path}" ]]; then
    log_error "制品不存在: ${artifact_path}"
    exit 1
  fi

  require_cmd find
  require_cmd cp
  require_cmd date

  local deploy_base="${LINGSQL_DEPLOY_BASE:-/opt/lingsql}"
  local run_dir="${deploy_base}/run"
  local releases_dir="${deploy_base}/releases"
  local blue_dir="${deploy_base}/blue"
  local green_dir="${deploy_base}/green"
  local slot_file="${run_dir}/active_slot"
  local history_file="${run_dir}/release_history.log"

  local blue_service="${LINGSQL_BLUE_SERVICE:-lingsql-blue}"
  local green_service="${LINGSQL_GREEN_SERVICE:-lingsql-green}"
  local blue_port="${LINGSQL_BLUE_PORT:-18080}"
  local green_port="${LINGSQL_GREEN_PORT:-28080}"
  local health_path="${LINGSQL_HEALTH_PATH:-/actuator/health/readiness}"
  local health_retries="${LINGSQL_HEALTH_RETRIES:-30}"
  local health_interval="${LINGSQL_HEALTH_INTERVAL_SECONDS:-2}"
  local keep_releases="${LINGSQL_KEEP_RELEASES:-10}"

  mkdir -p "${run_dir}" "${releases_dir}" "${blue_dir}" "${green_dir}"

  local active_slot="blue"
  if [[ -f "${slot_file}" ]]; then
    active_slot="$(tr -d '[:space:]' < "${slot_file}")"
  fi
  if [[ "${active_slot}" != "blue" && "${active_slot}" != "green" ]]; then
    log_warn "active_slot 文件异常（${active_slot}），默认按 blue 处理"
    active_slot="blue"
  fi

  local target_slot="green"
  if [[ "${active_slot}" == "green" ]]; then
    target_slot="blue"
  fi

  local target_service="${green_service}"
  local old_service="${blue_service}"
  local target_port="${green_port}"
  local target_dir="${green_dir}"
  if [[ "${target_slot}" == "blue" ]]; then
    target_service="${blue_service}"
    old_service="${green_service}"
    target_port="${blue_port}"
    target_dir="${blue_dir}"
  fi

  local safe_tag
  safe_tag="$(echo "${release_tag}" | tr -cs '[:alnum:]._-' '_')"
  local release_id
  release_id="$(date +%Y%m%d_%H%M%S)_${safe_tag}"
  local release_dir="${releases_dir}/${release_id}"
  local artifact_name
  artifact_name="$(basename "${artifact_path}")"
  local release_artifact="${release_dir}/lingsql-engine.jar"
  local slot_artifact="${target_dir}/app.jar"

  mkdir -p "${release_dir}" "${target_dir}"
  cp "${artifact_path}" "${release_artifact}"
  cp "${release_artifact}" "${slot_artifact}"
  echo "${release_id}" > "${target_dir}/release_id"

  log_info "当前活跃槽位: ${active_slot}"
  log_info "目标发布槽位: ${target_slot}"
  log_info "发布目录: ${release_dir}"

  start_slot_service "${target_slot}" "${target_service}" "${target_port}" "${slot_artifact}" "${target_dir}"
  wait_for_health "${target_slot}" "${target_port}" "${health_path}" "${health_retries}" "${health_interval}"

  run_switch_hook "${target_slot}" "${target_port}"
  echo "${target_slot}" > "${slot_file}"
  stop_slot_service_if_needed "${old_service}"

  local operator="${SUDO_USER:-${USER:-unknown}}"
  printf "%s|%s|%s|%s|%s\n" "$(date -Iseconds)" "${release_id}" "${target_slot}" "${artifact_name}" "${operator}" >> "${history_file}"
  prune_old_releases "${releases_dir}" "${keep_releases}"

  log_info "发布完成，活跃槽位已切换为: ${target_slot}"
  log_info "发布记录: ${history_file}"
}

main "$@"
