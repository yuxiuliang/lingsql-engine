#!/usr/bin/env bash
# 将已经通过隔离门禁的候选槽位切换为活跃槽位。

set -euo pipefail

log_error() { echo "[ERROR] $*" >&2; }

main() {
  local deploy_base run_dir slot port service release_id active_slot old_service health_url code
  deploy_base="${LINGSQL_DEPLOY_BASE:-/opt/lingsql}"
  command -v curl >/dev/null 2>&1 || { log_error "缺少命令: curl"; exit 1; }
  run_dir="${deploy_base}/run"
  for file in candidate_slot candidate_port candidate_service candidate_release_id candidate_artifact_sha256; do
    [[ -f "${run_dir}/${file}" ]] || { log_error "候选元数据缺失: ${run_dir}/${file}"; exit 1; }
  done
  slot="$(tr -d '[:space:]' < "${run_dir}/candidate_slot")"
  port="$(tr -d '[:space:]' < "${run_dir}/candidate_port")"
  service="$(tr -d '[:space:]' < "${run_dir}/candidate_service")"
  release_id="$(tr -d '[:space:]' < "${run_dir}/candidate_release_id")"
  [[ "${slot}" == "blue" || "${slot}" == "green" ]] || { log_error "候选槽位非法"; exit 1; }
  [[ "${port}" =~ ^[0-9]{2,5}$ ]] || { log_error "候选端口非法"; exit 1; }
  [[ "${release_id}" =~ ^[A-Za-z0-9._-]+$ ]] || { log_error "候选发布ID非法"; exit 1; }
  [[ -n "${service}" ]] || { log_error "候选服务名为空"; exit 1; }
  [[ -n "${LINGSQL_SWITCH_HOOK:-}" ]] || { log_error "提升候选必须配置 LINGSQL_SWITCH_HOOK"; exit 1; }

  health_url="http://127.0.0.1:${port}${LINGSQL_HEALTH_PATH:-/actuator/health/readiness}"
  code="$(curl -sS -o /dev/null -m 5 -w '%{http_code}' "${health_url}" || true)"
  [[ "${code}" == "200" ]] || { log_error "候选提升前健康检查失败: ${health_url}"; exit 2; }

  active_slot="blue"
  [[ -f "${run_dir}/active_slot" ]] && active_slot="$(tr -d '[:space:]' < "${run_dir}/active_slot")"
  [[ "${active_slot}" != "${slot}" ]] || { log_error "候选槽位已经是活跃槽位，拒绝重复提升"; exit 2; }
  old_service="${LINGSQL_BLUE_SERVICE:-lingsql-blue}"
  [[ "${active_slot}" == "green" ]] && old_service="${LINGSQL_GREEN_SERVICE:-lingsql-green}"

  bash -lc "${LINGSQL_SWITCH_HOOK} ${slot} ${port}"
  printf '%s' "${slot}" > "${run_dir}/active_slot"
  printf '%s' "${port}" > "${run_dir}/active_port"
  printf "%s|%s|%s|PROMOTED\n" "$(date -Iseconds)" "${release_id}" "${slot}" >> "${run_dir}/release_history.log"
  if [[ "${LINGSQL_STOP_OLD_SLOT:-false}" == "true" ]] && command -v systemctl >/dev/null 2>&1; then
    systemctl stop "${old_service}"
  fi
  rm -f -- "${run_dir}/candidate_slot" "${run_dir}/candidate_port" "${run_dir}/candidate_service" \
    "${run_dir}/candidate_release_id" "${run_dir}/candidate_artifact_sha256"
  echo "[INFO] 候选已提升并切流: release=${release_id}, slot=${slot}, port=${port}"
}

main "$@"
