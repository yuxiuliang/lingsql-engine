#!/usr/bin/env bash
# 内部40题演练唯一编排入口。私有输入与原始证据只允许保存在受控 self-hosted 主机。

set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=scripts/ops/lib/acceptance_auth.sh
source "${ROOT_DIR}/scripts/ops/lib/acceptance_auth.sh"

log_info() { printf '[INFO] %s\n' "$*"; }
log_error() { printf '[ERROR] %s\n' "$*" >&2; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || { log_error "缺少命令: $1"; exit 1; }; }
require_value() { [[ -n "${2:-}" ]] || { log_error "$1 不能为空"; exit 1; }; }
require_sha256() { [[ "${2:-}" =~ ^[0-9a-f]{64}$ ]] || { log_error "$1 必须是64位小写 SHA-256"; exit 1; }; }
require_commit() { [[ "${2:-}" =~ ^[0-9a-f]{40}$ ]] || { log_error "$1 必须是40位小写 Git SHA"; exit 1; }; }

require_protected_file() {
  local name="$1" path="$2" resolved mode permission
  require_value "${name}" "${path}"
  [[ "${path}" == /* ]] || { log_error "${name} 必须是 self-hosted 主机绝对路径"; exit 1; }
  resolved="$(realpath -e -- "${path}")"
  [[ -f "${resolved}" ]] || { log_error "${name} 不是普通文件"; exit 1; }
  mode="$(stat -c '%a' -- "${resolved}")"
  permission=$((8#${mode}))
  (( (permission & 0022) == 0 )) || { log_error "${name} 不得允许组或其他用户写入"; exit 1; }
  printf '%s' "${resolved}"
}

require_protected_directory() {
  local name="$1" path="$2" resolved mode permission owner
  require_value "${name}" "${path}"
  [[ "${path}" == /* ]] || { log_error "${name} 必须是 self-hosted 主机绝对路径"; exit 1; }
  resolved="$(realpath -e -- "${path}")"
  [[ -d "${resolved}" ]] || { log_error "${name} 不是目录"; exit 1; }
  mode="$(stat -c '%a' -- "${resolved}")"
  permission=$((8#${mode}))
  owner="$(stat -c '%u' -- "${resolved}")"
  (( (permission & 0077) == 0 )) || { log_error "${name} 必须仅目录所有者可访问"; exit 1; }
  [[ "${owner}" == "$(id -u)" ]] || { log_error "${name} 必须归当前 Runner 用户所有"; exit 1; }
  printf '%s' "${resolved}"
}

sha256_file() { sha256sum -- "$1" | awk '{print $1}'; }

RUN_STATUS="FAIL"
RUN_DIR=""
RUN_LOG=""
SUMMARY_FILE=""
RUNTIME_INPUT_SHA=""
SUITE_SHA=""
STUB_MAP_SHA=""
EVIDENCE_SHA=""
REPORT_SHA=""
DETERMINISTIC_STUB_STARTED=false

write_summary() {
  local exit_code="$1" temporary_summary
  [[ -n "${RUN_DIR}" && -d "${RUN_DIR}" ]] || return 0
  temporary_summary="${RUN_DIR}/.summary.json.tmp"
  jq -n \
    --arg purpose "INTERNAL_REHEARSAL" \
    --arg executionMode "${IR_EXECUTION_MODE:-UNKNOWN}" \
    --arg releaseCommit "${IR_EXPECTED_RELEASE_COMMIT:-}" \
    --arg draftSha256 "${IR_EXPECTED_DRAFT_SHA256:-}" \
    --arg securityMapSha256 "${IR_EXPECTED_SECURITY_MAP_SHA256:-}" \
    --arg runtimeInputSha256 "${RUNTIME_INPUT_SHA}" \
    --arg suiteSha256 "${SUITE_SHA}" \
    --arg stubMapSha256 "${STUB_MAP_SHA}" \
    --arg evidenceSha256 "${EVIDENCE_SHA}" \
    --arg reportSha256 "${REPORT_SHA}" \
    --arg status "${RUN_STATUS}" \
    --argjson exitCode "${exit_code}" \
    '{schemaVersion:"internal-rehearsal-summary/1.0",purpose:$purpose,executionMode:$executionMode,
      releaseCommit:$releaseCommit,status:$status,exitCode:$exitCode,businessCaseCount:40,securityCaseCount:3,
      hashes:{draft:$draftSha256,securityMap:(if $securityMapSha256=="" then null else $securityMapSha256 end),
        runtimeInput:(if $runtimeInputSha256=="" then null else $runtimeInputSha256 end),
        suite:(if $suiteSha256=="" then null else $suiteSha256 end),
        stubMap:(if $stubMapSha256=="" then null else $stubMapSha256 end),
        evidence:(if $evidenceSha256=="" then null else $evidenceSha256 end),
        report:(if $reportSha256=="" then null else $reportSha256 end)}}' > "${temporary_summary}"
  mv -- "${temporary_summary}" "${SUMMARY_FILE}"
  chmod 400 -- "${SUMMARY_FILE}"
  [[ -z "${RUN_LOG}" || ! -f "${RUN_LOG}" ]] || chmod 400 -- "${RUN_LOG}"
}

finish() {
  local original_status="$1" final_status="${original_status}"
  trap - EXIT
  if [[ "${DETERMINISTIC_STUB_STARTED}" == "true" ]]; then
    if ! docker compose -f "${ROOT_DIR}/docker-compose.yml" -f "${ROOT_DIR}/docker-compose.integration.yml" \
        stop model-stub >> "${RUN_LOG}" 2>&1; then
      log_error "确定性模型桩清理失败，演练按失败处理；详情仅保存在本机日志。"
      final_status=3
      RUN_STATUS="FAIL"
    fi
  fi
  write_summary "${final_status}"
  exit "${final_status}"
}

trap 'finish $?' EXIT

main() {
  local env_file draft_file security_map_file output_root runtime_raw runtime_input suite_file stub_map_file
  local control_token fingerprint_response active_model_count provider_name model_name model_version actual_commit
  local actual_schema actual_snapshot actual_route captured_at materializer_result comparator_commit
  local ir_mode ir_commit ir_draft_sha ir_environment_id ir_network_profile ir_run_dir ir_output_root
  local ir_base_url ir_skip_bootstrap ir_security_file ir_security_sha ir_comparator control_token_override

  for command_name in curl jq sha256sum awk stat realpath date java node chmod mv mkdir find id dirname; do require_cmd "${command_name}"; done
  require_value "IR_EXECUTION_MODE" "${IR_EXECUTION_MODE:-}"
  [[ "${IR_EXECUTION_MODE}" == "DETERMINISTIC_STUB" || "${IR_EXECUTION_MODE}" == "REAL_MODEL" ]] \
    || { log_error "IR_EXECUTION_MODE 仅允许 DETERMINISTIC_STUB 或 REAL_MODEL"; exit 1; }
  require_commit "IR_EXPECTED_RELEASE_COMMIT" "${IR_EXPECTED_RELEASE_COMMIT:-}"
  require_sha256 "IR_EXPECTED_DRAFT_SHA256" "${IR_EXPECTED_DRAFT_SHA256:-}"
  require_value "IR_ENVIRONMENT_ID" "${IR_ENVIRONMENT_ID:-}"
  require_value "IR_NETWORK_PROFILE" "${IR_NETWORK_PROFILE:-}"
  require_value "IR_RUN_DIR" "${IR_RUN_DIR:-}"
  [[ "${IR_RUN_DIR}" == /* ]] || { log_error "IR_RUN_DIR 必须是绝对路径"; exit 1; }
  output_root="$(require_protected_directory "IR_OUTPUT_ROOT" "${IR_OUTPUT_ROOT:-}")"
  [[ "$(realpath -e -- "$(dirname -- "${IR_RUN_DIR}")")" == "${output_root}" ]] \
    || { log_error "IR_RUN_DIR 必须是受保护输出根目录的直接子目录"; exit 1; }
  [[ ! -e "${IR_RUN_DIR}" ]] || { log_error "拒绝覆盖已存在的内部演练目录"; exit 2; }
  mkdir -m 700 -- "${IR_RUN_DIR}"
  RUN_DIR="$(realpath -e -- "${IR_RUN_DIR}")"
  RUN_LOG="${RUN_DIR}/run.log"
  SUMMARY_FILE="${RUN_DIR}/summary.json"
  : > "${RUN_LOG}"

  draft_file="$(require_protected_file "IR_PRIVATE_DRAFT_FILE" "${IR_PRIVATE_DRAFT_FILE:-}")"
  [[ "$(sha256_file "${draft_file}")" == "${IR_EXPECTED_DRAFT_SHA256}" ]] \
    || { log_error "私有草案 SHA-256 与预授权值不一致"; exit 2; }

  env_file="${IR_ENV_FILE:-${ROOT_DIR}/.env}"
  env_file="$(require_protected_file "IR_ENV_FILE" "${env_file}")"
  ir_mode="${IR_EXECUTION_MODE}"; ir_commit="${IR_EXPECTED_RELEASE_COMMIT}"; ir_draft_sha="${IR_EXPECTED_DRAFT_SHA256}"
  ir_environment_id="${IR_ENVIRONMENT_ID}"; ir_network_profile="${IR_NETWORK_PROFILE}"; ir_run_dir="${IR_RUN_DIR}"
  ir_output_root="${IR_OUTPUT_ROOT}"; ir_base_url="${IR_BASE_URL:-}"; ir_skip_bootstrap="${IR_SKIP_INFRA_BOOTSTRAP:-false}"
  ir_security_file="${IR_PRIVATE_SECURITY_MAP_FILE:-}"; ir_security_sha="${IR_EXPECTED_SECURITY_MAP_SHA256:-}"
  ir_comparator="${IR_COMPARATOR_JAR:-}"; control_token_override="${PILOT_CONTROL_TOKEN:-}"
  # shellcheck disable=SC1090
  set -a
  source "${env_file}"
  set +a
  IR_EXECUTION_MODE="${ir_mode}"; IR_EXPECTED_RELEASE_COMMIT="${ir_commit}"; IR_EXPECTED_DRAFT_SHA256="${ir_draft_sha}"
  IR_ENVIRONMENT_ID="${ir_environment_id}"; IR_NETWORK_PROFILE="${ir_network_profile}"; IR_RUN_DIR="${ir_run_dir}"
  IR_OUTPUT_ROOT="${ir_output_root}"; IR_BASE_URL="${ir_base_url}"; IR_SKIP_INFRA_BOOTSTRAP="${ir_skip_bootstrap}"
  IR_PRIVATE_SECURITY_MAP_FILE="${ir_security_file}"; IR_EXPECTED_SECURITY_MAP_SHA256="${ir_security_sha}"
  IR_COMPARATOR_JAR="${ir_comparator}"; PILOT_CONTROL_TOKEN="${control_token_override}"
  BASE_URL="${IR_BASE_URL:-${BASE_URL:-http://127.0.0.1:${APP_PORT:-8000}}}"
  if [[ "${IR_SKIP_INFRA_BOOTSTRAP}" != "true" && "${IR_SKIP_INFRA_BOOTSTRAP}" != "TRUE" \
      && "${IR_SKIP_INFRA_BOOTSTRAP}" != "1" && "${env_file}" != "${ROOT_DIR}/.env" ]]; then
    log_error "启动本地基础设施时 IR_ENV_FILE 必须是仓库根目录 .env；外部受保护环境文件仅用于已启动栈"
    exit 2
  fi

  if [[ "${IR_EXECUTION_MODE}" == "DETERMINISTIC_STUB" ]]; then
    require_cmd docker
    require_sha256 "IR_EXPECTED_SECURITY_MAP_SHA256" "${IR_EXPECTED_SECURITY_MAP_SHA256:-}"
    security_map_file="$(require_protected_file "IR_PRIVATE_SECURITY_MAP_FILE" "${IR_PRIVATE_SECURITY_MAP_FILE:-}")"
    [[ "$(sha256_file "${security_map_file}")" == "${IR_EXPECTED_SECURITY_MAP_SHA256}" ]] \
      || { log_error "私有安全映射 SHA-256 与预授权值不一致"; exit 2; }
    [[ "${LINGSQL_DEMO_MODEL_PROVIDER:-}" == "CI_PROTOCOL_STUB" ]] \
      || { log_error "DETERMINISTIC_STUB 必须绑定 CI_PROTOCOL_STUB Provider"; exit 2; }
    [[ "${LINGSQL_DEMO_MODEL_BASE_URL:-}" == "http://model-stub:18080" ]] \
      || { log_error "DETERMINISTIC_STUB 必须绑定隔离的 model-stub 地址"; exit 2; }
    log_info "启动仅用于配置阶段的内置确定性模型桩。"
    docker compose -f "${ROOT_DIR}/docker-compose.yml" -f "${ROOT_DIR}/docker-compose.integration.yml" \
      up -d --wait model-stub >> "${RUN_LOG}" 2>&1
    DETERMINISTIC_STUB_STARTED=true
  else
    [[ -z "${IR_PRIVATE_SECURITY_MAP_FILE:-}" && -z "${IR_EXPECTED_SECURITY_MAP_SHA256:-}" ]] \
      || { log_error "REAL_MODEL 禁止接收确定性安全映射输入"; exit 2; }
    [[ "${LINGSQL_DEMO_MODEL_PROVIDER:-}" != "CI_PROTOCOL_STUB" \
        && "${LINGSQL_DEMO_MODEL_BASE_URL:-}" != *model-stub* ]] \
      || { log_error "REAL_MODEL 禁止使用协议桩 Provider 或地址"; exit 2; }
    [[ -z "${STUB_RESPONSE_MAP_FILE:-}" && -z "${STUB_RESPONSE_MAP_SHA256:-}" \
        && -z "${INTERNAL_REHEARSAL_STUB_MAP_FILE:-}" && -z "${INTERNAL_REHEARSAL_STUB_MAP_SHA256:-}" ]] \
      || { log_error "REAL_MODEL 禁止注入任何协议桩映射"; exit 2; }
    if command -v docker >/dev/null 2>&1; then
      local running_stub
      if ! running_stub="$(docker ps --quiet --filter 'name=^/lingsql-model-stub$' 2>> "${RUN_LOG}")"; then
        log_error "REAL_MODEL 无法权威核验本机协议桩状态"
        exit 2
      fi
      [[ -z "${running_stub}" ]] || { log_error "REAL_MODEL 检测到正在运行的 lingsql-model-stub 容器"; exit 2; }
    fi
  fi

  log_info "配置演练租户、数据源与模型路由。"
  env LINGSQL_DEMO_PROVISIONING_ONLY=true \
    LINGSQL_ENV_FILE="${env_file}" \
    SKIP_INFRA_BOOTSTRAP="${IR_SKIP_INFRA_BOOTSTRAP:-false}" \
    "${ROOT_DIR}/scripts/ops/bootstrap_demo_pilot.sh" >> "${RUN_LOG}" 2>&1

  control_token="$(acceptance_resolve_control_token \
    "${BASE_URL}" "${PILOT_CONTROL_TOKEN:-}" "${PILOT_CONTROL_TENANT_ID:-${LINGSQL_ADMIN_TENANT_ID:-}}" \
    "${PILOT_CONTROL_USERNAME:-${LINGSQL_ADMIN_USERNAME:-}}" "${PILOT_CONTROL_PASSWORD:-${LINGSQL_ADMIN_PASSWORD:-}}")"
  require_value "内部演练控制 Token" "${control_token}"
  fingerprint_response="$(curl -fsS --get --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer ${control_token}" \
    -H "X-Tenant-Id: ${PILOT_CONTROL_TENANT_ID:-${LINGSQL_ADMIN_TENANT_ID}}" \
    --data-urlencode "modelTenantId=${LINGSQL_DEMO_TENANT_ID}" \
    "${BASE_URL%/}/api/v1/admin/runtime/fingerprint")"
  [[ "$(jq -r '.code // 0' <<< "${fingerprint_response}")" == "200" ]] \
    || { log_error "读取受认证 runtime fingerprint 失败"; exit 2; }
  active_model_count="$(jq -r '(.data.activeModels // []) | length' <<< "${fingerprint_response}")"
  [[ "${active_model_count}" -eq 1 ]] || { log_error "内部演练要求 runtime fingerprint 恰好一个 active model"; exit 2; }
  provider_name="$(jq -r '.data.activeModels[0].providerName // empty' <<< "${fingerprint_response}")"
  model_name="$(jq -r '.data.activeModels[0].modelName // empty' <<< "${fingerprint_response}")"
  model_version="$(jq -r '.data.activeModels[0].modelVersion // empty' <<< "${fingerprint_response}")"
  actual_commit="$(jq -r '.data.commit // empty' <<< "${fingerprint_response}")"
  actual_schema="$(jq -r '.data.schemaVersion // empty' <<< "${fingerprint_response}")"
  actual_snapshot="$(jq -r '.data.dataSnapshotId // empty' <<< "${fingerprint_response}")"
  actual_route="$(jq -r '.data.modelRouteFingerprint // empty' <<< "${fingerprint_response}")"
  [[ "${actual_commit}" == "${IR_EXPECTED_RELEASE_COMMIT}" ]] || { log_error "runtime commit 与预期 master 提交不一致"; exit 2; }
  require_value "runtime schemaVersion" "${actual_schema}"
  require_value "runtime dataSnapshotId" "${actual_snapshot}"
  require_sha256 "runtime modelRouteFingerprint" "${actual_route}"
  require_value "runtime model provider" "${provider_name}"
  require_value "runtime model name" "${model_name}"
  require_value "runtime model version" "${model_version}"
  if [[ "${IR_EXECUTION_MODE}" == "DETERMINISTIC_STUB" ]]; then
    [[ "${provider_name}" == "CI_PROTOCOL_STUB" ]] || { log_error "权威指纹不是确定性协议桩 Provider"; exit 2; }
  else
    [[ "${provider_name}" != "CI_PROTOCOL_STUB" ]] || { log_error "REAL_MODEL 权威指纹不得是协议桩 Provider"; exit 2; }
  fi

  runtime_raw="${RUN_DIR}/runtime-fingerprint-response.json"
  runtime_input="${RUN_DIR}/runtime-input.json"
  printf '%s\n' "${fingerprint_response}" > "${runtime_raw}"
  chmod 400 -- "${runtime_raw}"
  captured_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  jq -n \
    --arg evidencePurpose "INTERNAL_REHEARSAL" --arg executionMode "${IR_EXECUTION_MODE}" \
    --arg environmentId "${IR_ENVIRONMENT_ID}" --arg releaseCommit "${actual_commit}" \
    --arg flywaySchemaVersion "${actual_schema}" --arg dataSnapshotId "${actual_snapshot}" \
    --arg modelRouteFingerprint "${actual_route}" --arg modelProvider "${provider_name}" \
    --arg modelName "${model_name}" --arg modelVersion "${model_version}" \
    --arg networkProfile "${IR_NETWORK_PROFILE}" --arg capturedAt "${captured_at}" \
    '{evidencePurpose:$evidencePurpose,executionMode:$executionMode,environmentId:$environmentId,
      releaseCommit:$releaseCommit,flywaySchemaVersion:$flywaySchemaVersion,dataSnapshotId:$dataSnapshotId,
      modelRouteFingerprint:$modelRouteFingerprint,modelProvider:$modelProvider,modelName:$modelName,
      modelVersion:$modelVersion,networkProfile:$networkProfile,capturedAt:$capturedAt}' > "${runtime_input}"
  chmod 400 -- "${runtime_input}"
  RUNTIME_INPUT_SHA="$(sha256_file "${runtime_input}")"
  suite_file="${RUN_DIR}/internal-rehearsal-suite.json"
  stub_map_file="${RUN_DIR}/internal-rehearsal-stub-map.json"
  if [[ "${IR_EXECUTION_MODE}" == "DETERMINISTIC_STUB" ]]; then
    materializer_result="$(INTERNAL_REHEARSAL_EXPECTED_DRAFT_SHA256="${IR_EXPECTED_DRAFT_SHA256}" \
      INTERNAL_REHEARSAL_EXPECTED_RUNTIME_SHA256="${RUNTIME_INPUT_SHA}" \
      INTERNAL_REHEARSAL_EXPECTED_SECURITY_MAP_SHA256="${IR_EXPECTED_SECURITY_MAP_SHA256}" \
      node "${ROOT_DIR}/scripts/ops/integration/internal_rehearsal_materializer.mjs" \
      --draft "${draft_file}" --runtime "${runtime_input}" --security-map "${security_map_file}" \
      --suite-output "${suite_file}" --stub-map-output "${stub_map_file}")"
    STUB_MAP_SHA="$(jq -r '.stubMapSha256 // empty' <<< "${materializer_result}")"
    require_sha256 "材料化 stub map SHA-256" "${STUB_MAP_SHA}"
    [[ "$(sha256_file "${stub_map_file}")" == "${STUB_MAP_SHA}" ]] || { log_error "stub map 写出后摘要不一致"; exit 2; }
  else
    materializer_result="$(env -u INTERNAL_REHEARSAL_EXPECTED_SECURITY_MAP_SHA256 \
      INTERNAL_REHEARSAL_EXPECTED_DRAFT_SHA256="${IR_EXPECTED_DRAFT_SHA256}" \
      INTERNAL_REHEARSAL_EXPECTED_RUNTIME_SHA256="${RUNTIME_INPUT_SHA}" \
      node "${ROOT_DIR}/scripts/ops/integration/internal_rehearsal_materializer.mjs" \
      --draft "${draft_file}" --runtime "${runtime_input}" --suite-output "${suite_file}")"
    [[ ! -e "${stub_map_file}" ]] || { log_error "REAL_MODEL 不得生成 stub map"; exit 2; }
  fi
  SUITE_SHA="$(jq -r '.suiteSha256 // empty' <<< "${materializer_result}")"
  require_sha256 "材料化 Suite SHA-256" "${SUITE_SHA}"
  [[ "$(sha256_file "${suite_file}")" == "${SUITE_SHA}" ]] || { log_error "Suite 写出后摘要不一致"; exit 2; }

  if [[ "${IR_EXECUTION_MODE}" == "DETERMINISTIC_STUB" ]]; then
    [[ "$(jq -r '.suiteSha256 // empty' "${stub_map_file}")" == "${SUITE_SHA}" ]] \
      || { log_error "协议桩映射未绑定当前 Suite SHA-256"; exit 2; }
    export INTERNAL_REHEARSAL_STUB_MAP_FILE="${stub_map_file}"
    export INTERNAL_REHEARSAL_STUB_MAP_SHA256="${STUB_MAP_SHA}"
    export INTERNAL_REHEARSAL_RUNNER_UID="$(id -u)"
    export INTERNAL_REHEARSAL_RUNNER_GID="$(id -g)"
    log_info "以私有哈希映射重建确定性模型桩。"
    docker compose -f "${ROOT_DIR}/docker-compose.yml" -f "${ROOT_DIR}/docker-compose.integration.yml" \
      -f "${ROOT_DIR}/docker-compose.internal-rehearsal.yml" up -d --force-recreate --wait model-stub \
      >> "${RUN_LOG}" 2>&1
  fi

  require_value "IR_COMPARATOR_JAR" "${IR_COMPARATOR_JAR:-}"
  IR_COMPARATOR_JAR="$(require_protected_file "IR_COMPARATOR_JAR" "${IR_COMPARATOR_JAR}")"
  comparator_commit="$(java -Dloader.main=com.lingsql.acceptance.GoldenQuestionResultComparator \
    -cp "${IR_COMPARATOR_JAR}" org.springframework.boot.loader.launch.PropertiesLauncher --build-commit)"
  [[ "${comparator_commit}" == "${actual_commit}" ]] || { log_error "比较器制品与 runtime commit 不一致"; exit 2; }

  log_info "串行执行40题 INTERNAL_REHEARSAL；原始输出仅写入本机受控目录。"
  env BASE_URL="${BASE_URL}" GQ_SUITE_FILE="${suite_file}" GQ_EXPECTED_SUITE_SHA256="${SUITE_SHA}" \
    GQ_PHASE=FIRST GQ_EVIDENCE_PURPOSE=INTERNAL_REHEARSAL GQ_OUTPUT_DIR="${RUN_DIR}/golden-question" \
    GQ_COMPARATOR_JAR="${IR_COMPARATOR_JAR}" PILOT_TENANT_ID="${LINGSQL_DEMO_TENANT_ID}" \
    PILOT_USERNAME="${LINGSQL_DEMO_ADMIN_USERNAME}" PILOT_PASSWORD="${LINGSQL_DEMO_ADMIN_PASSWORD}" \
    PILOT_CONTROL_TOKEN="${control_token}" PILOT_CONTROL_TENANT_ID="${PILOT_CONTROL_TENANT_ID:-${LINGSQL_ADMIN_TENANT_ID}}" \
    "${ROOT_DIR}/scripts/ops/golden_question_acceptance.sh" >> "${RUN_LOG}" 2>&1

  EVIDENCE_SHA="$(sha256_file "${RUN_DIR}/golden-question/evidence.jsonl")"
  REPORT_SHA="$(sha256_file "${RUN_DIR}/golden-question/report.md")"
  find "${RUN_DIR}/golden-question" -type f -exec chmod 400 -- {} +
  RUN_STATUS="PASS"
  log_info "内部40题演练完成；远端仅可使用脱敏 summary.json。"
}

main "$@"
