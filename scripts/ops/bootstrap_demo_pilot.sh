#!/usr/bin/env bash
# 从全新本地栈初始化到可复核三问演示。任何开户、模型、数据源或验收步骤失败都会退出。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
BASE_BOOTSTRAP="${ROOT_DIR}/scripts/ops/bootstrap_local_stack.sh"
PILOT_ACCEPTANCE="${ROOT_DIR}/scripts/ops/pilot_acceptance.sh"
RUNTIME_FINGERPRINT_GUARD="${ROOT_DIR}/scripts/ops/runtime_fingerprint_guard.sh"

log_info() { echo "[INFO] $*"; }
log_error() { echo "[ERROR] $*" >&2; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || { log_error "缺少命令: $1"; exit 1; }; }
require_value() { [[ -n "$2" ]] || { log_error "$1 不能为空"; exit 1; }; }
is_true() { [[ "${1:-false}" =~ ^(1|true|TRUE|yes|YES)$ ]]; }

api_get() {
  local path="$1" token="$2" tenant_header="$3"
  curl -fsS --connect-timeout 5 --max-time 60 \
    -H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${tenant_header}" \
    "${BASE_URL%/}${path}"
}

api_post() {
  local path="$1" body="$2" token="$3" tenant_header="$4"
  printf '%s' "${body}" | curl -fsS --connect-timeout 5 --max-time 90 \
    -H 'Content-Type: application/json; charset=UTF-8' \
    -H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${tenant_header}" \
    -X POST "${BASE_URL%/}${path}" --data-binary @-
}

api_put() {
  local path="$1" body="$2" token="$3" tenant_header="$4"
  printf '%s' "${body}" | curl -fsS --connect-timeout 5 --max-time 90 \
    -H 'Content-Type: application/json; charset=UTF-8' \
    -H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${tenant_header}" \
    -X PUT "${BASE_URL%/}${path}" --data-binary @-
}

login() {
  local tenant_id="$1" username="$2" password="$3"
  local body response token
  body="$(jq -cn --arg tenantId "${tenant_id}" --arg username "${username}" --arg password "${password}" \
    '{tenantId:$tenantId,username:$username,password:$password}')"
  response="$(printf '%s' "${body}" | curl -fsS --connect-timeout 5 --max-time 30 \
    -H 'Content-Type: application/json; charset=UTF-8' \
    -X POST "${BASE_URL%/}/api/v1/auth/login" --data-binary @-)"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || { log_error "账号 ${tenant_id}/${username} 登录失败"; return 2; }
  token="$(jq -r '.data.token // empty' <<< "${response}")"
  require_value "登录 Token" "${token}"
  printf '%s' "${token}"
}

require_api_success() {
  local action="$1" response="$2"
  if [[ "$(jq -r '.code // 0' <<< "${response}")" != "200" ]]; then
    log_error "${action}失败: code=$(jq -r '.code // 0' <<< "${response}"), message=$(jq -r '.message // "无错误说明"' <<< "${response}")"
    exit 2
  fi
}

ensure_demo_tenant() {
  local list_response existing_count create_body create_response
  list_response="$(api_get '/api/v1/admin/tenants' "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  require_api_success "读取租户列表" "${list_response}"
  existing_count="$(jq --arg tenantId "${LINGSQL_DEMO_TENANT_ID}" '[.data[]? | select(.tenantId == $tenantId)] | length' <<< "${list_response}")"
  if [[ "${existing_count}" -eq 0 ]]; then
    create_body="$(jq -cn \
      --arg tenantId "${LINGSQL_DEMO_TENANT_ID}" --arg tenantName "${LINGSQL_DEMO_TENANT_NAME}" \
      --arg planName "${LINGSQL_DEMO_PLAN_NAME}" --arg jdbcUrl "${LINGSQL_DEMO_JDBC_URL}" \
      --arg username "${LINGSQL_DEMO_USERNAME}" --arg password "${LINGSQL_DEMO_PASSWORD}" \
      --arg adminUsername "${LINGSQL_DEMO_ADMIN_USERNAME}" --arg adminPassword "${LINGSQL_DEMO_ADMIN_PASSWORD}" \
      --arg adminNickname "${LINGSQL_DEMO_ADMIN_NICKNAME}" \
      '{tenantId:$tenantId,tenantName:$tenantName,status:1,planName:$planName,
        jdbcUrl:$jdbcUrl,username:$username,password:$password,driverClass:"com.mysql.cj.jdbc.Driver",
        isolationMode:"PHYSICAL",logicColumn:"tenant_id",
        adminAccount:{username:$adminUsername,password:$adminPassword,nickname:$adminNickname}}')"
    create_response="$(api_post '/api/v1/admin/tenants' "${create_body}" "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
    require_api_success "创建演示租户" "${create_response}"
    log_info "演示租户已创建: ${LINGSQL_DEMO_TENANT_ID}"
  elif [[ "${existing_count}" -eq 1 ]]; then
    log_info "演示租户已存在，进入幂等复核: ${LINGSQL_DEMO_TENANT_ID}"
  else
    log_error "演示租户ID出现重复记录，拒绝继续。"
    exit 2
  fi
  DEMO_TOKEN="$(login "${LINGSQL_DEMO_TENANT_ID}" "${LINGSQL_DEMO_ADMIN_USERNAME}" "${LINGSQL_DEMO_ADMIN_PASSWORD}")"
}

reconcile_demo_datasource() {
  local list_response config_count config_id update_body update_response probe_body probe_response sync_response
  list_response="$(api_get '/api/v1/admin/config/tenant' "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  require_api_success "读取数据源配置" "${list_response}"
  config_count="$(jq --arg tenantId "${LINGSQL_DEMO_TENANT_ID}" '[.data[]? | select(.tenantId == $tenantId)] | length' <<< "${list_response}")"
  [[ "${config_count}" -eq 1 ]] || { log_error "演示租户必须恰有一个数据源配置"; exit 2; }
  config_id="$(jq -r --arg tenantId "${LINGSQL_DEMO_TENANT_ID}" '.data[] | select(.tenantId == $tenantId) | .id' <<< "${list_response}")"
  update_body="$(jq -cn \
    --arg tenantId "${LINGSQL_DEMO_TENANT_ID}" --arg jdbcUrl "${LINGSQL_DEMO_JDBC_URL}" \
    --arg username "${LINGSQL_DEMO_USERNAME}" --arg password "${LINGSQL_DEMO_PASSWORD}" \
    --arg tables "${LINGSQL_DEMO_WHITE_LIST_TABLES}" \
    '{tenantId:$tenantId,jdbcUrl:$jdbcUrl,username:$username,password:$password,
      driverClass:"com.mysql.cj.jdbc.Driver",isolationMode:"PHYSICAL",logicColumn:"tenant_id",
      whiteListTables:$tables,whiteListColumns:"",sensitiveColumns:"",status:1}')"
  update_response="$(api_put "/api/v1/admin/config/tenant/${config_id}" "${update_body}" "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  require_api_success "收敛演示数据源配置" "${update_response}"
  probe_body="${update_body}"
  probe_response="$(api_post '/api/v1/admin/config/tenant/test-connection' "${probe_body}" "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  require_api_success "验证演示数据源连接" "${probe_response}"
  sync_response="$(api_post "/api/v1/admin/sync-schema/${LINGSQL_DEMO_TENANT_ID}" '{}' "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  require_api_success "同步演示 Schema" "${sync_response}"
  log_info "演示数据源已验证并同步 Schema。"
}

ensure_model_ready() {
  local list_response model_id model_response probe_response enable_response model_body
  list_response="$(api_get '/api/v1/admin/config/model' "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  require_api_success "读取模型配置" "${list_response}"
  if [[ -n "${LINGSQL_DEMO_MODEL_PROVIDER}" && -n "${LINGSQL_DEMO_MODEL_NAME}" && -n "${LINGSQL_DEMO_MODEL_BASE_URL}" ]]; then
    model_id="$(jq -r --arg provider "${LINGSQL_DEMO_MODEL_PROVIDER}" --arg model "${LINGSQL_DEMO_MODEL_NAME}" \
      --arg baseUrl "${LINGSQL_DEMO_MODEL_BASE_URL}" \
      '.data[]? | select(.providerName == $provider and .modelName == $model and .baseUrl == $baseUrl) | .id' \
      <<< "${list_response}" | head -n 1)"
    model_body="$(jq -cn \
      --arg providerName "${LINGSQL_DEMO_MODEL_PROVIDER}" --arg modelName "${LINGSQL_DEMO_MODEL_NAME}" \
      --arg modelVersion "${LINGSQL_DEMO_MODEL_VERSION}" --arg baseUrl "${LINGSQL_DEMO_MODEL_BASE_URL}" \
      --arg apiKey "${LINGSQL_DEMO_MODEL_API_KEY}" \
      '{providerName:$providerName,modelName:$modelName,modelVersion:$modelVersion,baseUrl:$baseUrl,apiKey:$apiKey,
        weight:100,priority:1,autoRecover:1,cooldownSeconds:300}')"
    if [[ -z "${model_id}" ]]; then
      require_value "LINGSQL_DEMO_MODEL_API_KEY（无鉴权私有模型可显式填 NO_AUTH）" "${LINGSQL_DEMO_MODEL_API_KEY}"
      [[ "${LINGSQL_DEMO_MODEL_API_KEY}" != "NO_AUTH" ]] || model_body="$(jq '.apiKey=""' <<< "${model_body}")"
      model_response="$(api_post '/api/v1/admin/config/model' "${model_body}" "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
      require_api_success "创建演示模型" "${model_response}"
      model_id="$(jq -r '.data.id // empty' <<< "${model_response}")"
    elif [[ -n "${LINGSQL_DEMO_MODEL_API_KEY}" ]]; then
      [[ "${LINGSQL_DEMO_MODEL_API_KEY}" != "NO_AUTH" ]] || model_body="$(jq '.apiKey=""' <<< "${model_body}")"
      model_response="$(api_put "/api/v1/admin/config/model/${model_id}" "${model_body}" "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
      require_api_success "更新演示模型" "${model_response}"
    fi
  else
    model_id="$(jq -r '.data[]? | select(.isActive == 1 and .lastProbeStatus == "HEALTHY") | .id' <<< "${list_response}" | head -n 1)"
  fi
  require_value "可验证模型ID；请配置 LINGSQL_DEMO_MODEL_* 或预先准备 HEALTHY 模型" "${model_id}"
  probe_response="$(api_post "/api/v1/admin/model/${model_id}/probe" '{}' "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  require_api_success "实际调用验证模型" "${probe_response}"
  [[ "$(jq -r '.data.status // empty' <<< "${probe_response}")" == "HEALTHY" ]] || { log_error "模型实际调用验证未通过"; exit 2; }
  enable_response="$(api_post "/api/v1/admin/model/enable/${model_id}" '{}' "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  require_api_success "启用已验证模型" "${enable_response}"
  log_info "模型已实际验证并启用: ${model_id}"
}

main() {
  require_cmd curl
  require_cmd jq
  [[ -f "${ENV_FILE}" ]] || { log_error "未找到 ${ENV_FILE}"; exit 1; }
  # shellcheck disable=SC1090
  source "${ENV_FILE}"

  BASE_URL="${BASE_URL:-http://127.0.0.1:${APP_PORT:-8000}}"
  LINGSQL_DEMO_TENANT_ID="${LINGSQL_DEMO_TENANT_ID:-demo_sales}"
  LINGSQL_DEMO_TENANT_NAME="${LINGSQL_DEMO_TENANT_NAME:-销售库存演示租户}"
  LINGSQL_DEMO_PLAN_NAME="${LINGSQL_DEMO_PLAN_NAME:-标准版}"
  LINGSQL_DEMO_ADMIN_USERNAME="${LINGSQL_DEMO_ADMIN_USERNAME:-demo_admin}"
  LINGSQL_DEMO_ADMIN_PASSWORD="${LINGSQL_DEMO_ADMIN_PASSWORD:-}"
  LINGSQL_DEMO_ADMIN_NICKNAME="${LINGSQL_DEMO_ADMIN_NICKNAME:-演示管理员}"
  LINGSQL_DEMO_JDBC_URL="${LINGSQL_DEMO_JDBC_URL:-jdbc:mysql://mysql:3306/${LINGSQL_DEMO_DATABASE:-}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai}"
  LINGSQL_DEMO_WHITE_LIST_TABLES="${LINGSQL_DEMO_WHITE_LIST_TABLES:-demo_customer,demo_product,demo_sales_order,demo_sales_order_item,demo_inventory_snapshot}"
  LINGSQL_DEMO_MODEL_PROVIDER="${LINGSQL_DEMO_MODEL_PROVIDER:-}"
  LINGSQL_DEMO_MODEL_NAME="${LINGSQL_DEMO_MODEL_NAME:-}"
  LINGSQL_DEMO_MODEL_VERSION="${LINGSQL_DEMO_MODEL_VERSION:-}"
  LINGSQL_DEMO_MODEL_BASE_URL="${LINGSQL_DEMO_MODEL_BASE_URL:-}"
  LINGSQL_DEMO_MODEL_API_KEY="${LINGSQL_DEMO_MODEL_API_KEY:-}"

  require_value "LINGSQL_ADMIN_TENANT_ID" "${LINGSQL_ADMIN_TENANT_ID:-}"
  require_value "LINGSQL_ADMIN_USERNAME" "${LINGSQL_ADMIN_USERNAME:-}"
  require_value "LINGSQL_ADMIN_PASSWORD" "${LINGSQL_ADMIN_PASSWORD:-}"
  require_value "LINGSQL_DEMO_DATABASE" "${LINGSQL_DEMO_DATABASE:-}"
  require_value "LINGSQL_DEMO_USERNAME" "${LINGSQL_DEMO_USERNAME:-}"
  require_value "LINGSQL_DEMO_PASSWORD" "${LINGSQL_DEMO_PASSWORD:-}"
  require_value "LINGSQL_DEMO_ADMIN_PASSWORD" "${LINGSQL_DEMO_ADMIN_PASSWORD}"

  if ! is_true "${SKIP_INFRA_BOOTSTRAP:-false}"; then
    "${BASE_BOOTSTRAP}"
  fi
  CONTROL_TOKEN="$(login "${LINGSQL_ADMIN_TENANT_ID}" "${LINGSQL_ADMIN_USERNAME}" "${LINGSQL_ADMIN_PASSWORD}")"
  ensure_demo_tenant
  reconcile_demo_datasource
  ensure_model_ready

  if [[ -n "${EXPECTED_RELEASE_COMMIT:-}" ]]; then
    BASE_URL="${BASE_URL}" \
    PILOT_CONTROL_TOKEN="${CONTROL_TOKEN}" \
    PILOT_CONTROL_TENANT_ID="${LINGSQL_ADMIN_TENANT_ID}" \
    EXPECTED_RELEASE_COMMIT="${EXPECTED_RELEASE_COMMIT}" \
    EXPECTED_DATA_SNAPSHOT_ID="${EXPECTED_DATA_SNAPSHOT_ID:-}" \
    RUNTIME_FINGERPRINT_REPORT_FILE="${RUNTIME_FINGERPRINT_REPORT_FILE:-${ROOT_DIR}/reports/runtime_fingerprint.md}" \
      "${RUNTIME_FINGERPRINT_GUARD}"
  fi

  log_info "开始执行三问真实业务验收。"
  BASE_URL="${BASE_URL}" \
  PILOT_TENANT_ID="${LINGSQL_DEMO_TENANT_ID}" \
  PILOT_USERNAME="${LINGSQL_DEMO_ADMIN_USERNAME}" \
  PILOT_PASSWORD="${LINGSQL_DEMO_ADMIN_PASSWORD}" \
  PILOT_CONTROL_TENANT_ID="${LINGSQL_ADMIN_TENANT_ID}" \
  PILOT_CONTROL_USERNAME="${LINGSQL_ADMIN_USERNAME}" \
  PILOT_CONTROL_PASSWORD="${LINGSQL_ADMIN_PASSWORD}" \
  PILOT_REPORT_FILE="${PILOT_REPORT_FILE:-${ROOT_DIR}/reports/demo_pilot_acceptance.md}" \
    "${PILOT_ACCEPTANCE}"
  log_info "演示试点初始化与三问验收完成。"
}

main "$@"
