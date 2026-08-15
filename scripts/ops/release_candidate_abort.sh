#!/usr/bin/env bash
# 严格门禁失败时停止尚未切流的候选槽位。

set -euo pipefail

log_error() { echo "[ERROR] $*" >&2; }

main() {
  local deploy_base run_dir script_dir slot service release_id active_slot stopped=false
  deploy_base="${LINGSQL_DEPLOY_BASE:-/opt/lingsql}"
  run_dir="${deploy_base}/run"
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  [[ -f "${run_dir}/candidate_slot" ]] || { echo "[INFO] 没有待中止候选"; return 0; }
  for file in candidate_port candidate_service candidate_release_id candidate_artifact_sha256; do
    [[ -f "${run_dir}/${file}" ]] || { log_error "候选元数据缺失: ${run_dir}/${file}"; exit 1; }
  done
  slot="$(tr -d '[:space:]' < "${run_dir}/candidate_slot")"
  service="$(tr -d '[:space:]' < "${run_dir}/candidate_service")"
  release_id="$(tr -d '[:space:]' < "${run_dir}/candidate_release_id")"
  [[ "${slot}" == "blue" || "${slot}" == "green" ]] || { log_error "候选槽位非法"; exit 1; }
  [[ "${release_id}" =~ ^[A-Za-z0-9._-]+$ ]] || { log_error "候选发布ID非法"; exit 1; }
  active_slot="$(tr -d '[:space:]' < "${run_dir}/active_slot" 2>/dev/null || true)"
  [[ "${active_slot}" != "${slot}" ]] || { log_error "候选已经切流，必须走正式回滚，拒绝直接停止"; exit 2; }

  if [[ -f "${run_dir}/candidate_acceptance_receipt.json" ]]; then
    # 仍在运行时完成封印、候选指纹、制品和证据复核；损坏收据不得被静默归档。
    bash "${script_dir}/release_candidate_receipt.sh" reserve
  fi

  if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files "${service}.service" --no-legend 2>/dev/null | grep -q "^${service}\.service"; then
    systemctl stop "${service}"
    stopped=true
  elif [[ -n "${LINGSQL_CANDIDATE_ABORT_HOOK:-}" ]]; then
    bash -lc "${LINGSQL_CANDIDATE_ABORT_HOOK} ${slot} ${service}"
    stopped=true
  fi
  [[ "${stopped}" == "true" ]] || { log_error "无法停止候选服务，请配置 systemd 或 LINGSQL_CANDIDATE_ABORT_HOOK"; exit 2; }
  if [[ -f "${run_dir}/candidate_acceptance_receipt.json" ]]; then
    bash "${script_dir}/release_candidate_receipt.sh" finalize
  fi
  printf "%s|%s|%s|ABORTED\n" "$(date -Iseconds)" "${release_id}" "${slot}" >> "${run_dir}/release_history.log"
  rm -f -- "${run_dir}/candidate_slot" "${run_dir}/candidate_port" "${run_dir}/candidate_service" \
    "${run_dir}/candidate_release_id" "${run_dir}/candidate_artifact_sha256"
  echo "[INFO] 未切流候选已停止: release=${release_id}, slot=${slot}"
}

main "$@"
