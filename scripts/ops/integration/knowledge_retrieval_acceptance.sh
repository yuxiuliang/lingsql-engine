#!/usr/bin/env bash
# 验证受控知识检索在真实应用中的注入链路：租户授权 → 网关执行 → 调用 Outbox 留痕 → 未授权降级。

set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
# shellcheck source=scripts/ops/lib/acceptance_auth.sh
source "${SCRIPT_DIR}/../lib/acceptance_auth.sh"

REPORT_FILE="${KNOWLEDGE_RETRIEVAL_REPORT_FILE:-${ROOT_DIR}/reports/deterministic_full_stack_knowledge_retrieval.md}"

KNOWLEDGE_PLUGIN_ID="knowledge_retrieve"
KNOWLEDGE_TITLE="销售额口径（CI 验收）"
KNOWLEDGE_CONTENT="已完成订单的销售额按订单支付时间统计；退款金额需要从销售额中扣除后再汇总。"
KNOWLEDGE_VERSION="1.0"
# 说明：集成桩只接受冻结的问题集，这里复用其中的销售额问题，保证知识检索验证与桩实现互不影响。
KNOWLEDGE_QUESTION="2026年8月已完成订单的销售额是多少？"

log_error() { echo "[ERROR] $*" >&2; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || { log_error "缺少命令: $1"; exit 1; }; }
require_value() { [[ -n "$2" ]] || { log_error "$1 不能为空"; exit 1; }; }

api_request() {
  local method="$1" path="$2" body="$3" token="$4" tenant_id="$5"
  printf '%s' "${body}" | curl -sS --connect-timeout 5 --max-time 90 \
    -H 'Content-Type: application/json; charset=UTF-8' \
    -H "Authorization: Bearer ${token}" -H "X-Tenant-Id: ${tenant_id}" \
    -X "${method}" "${BASE_URL%/}${path}" --data-binary @-
}

mysql_query() {
  local sql="$1"
  docker compose -f "${ROOT_DIR}/docker-compose.yml" -f "${ROOT_DIR}/docker-compose.integration.yml" \
    exec -T -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql \
    mysql --batch --skip-column-names -uroot "${MYSQL_DATABASE}" -e "${sql}"
}

count_plugin_invocations() {
  local status="$1"
  mysql_query "SELECT COUNT(*) FROM t_lingsql_engine_plugin_invocation_outbox WHERE tenant_id='${LINGSQL_DEMO_TENANT_ID}' AND plugin_id='${KNOWLEDGE_PLUGIN_ID}' AND status='${status}';"
}

submit_question() {
  local question="$1" response request_id
  response="$(api_request POST '/api/v1/chat/ask' \
    "$(jq -cn --arg question "${question}" '{question:$question}')" \
    "${DEMO_TOKEN}" "${LINGSQL_DEMO_TENANT_ID}")"
  request_id="$(jq -r '.requestId // empty' <<< "${response}")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" && "${request_id}" =~ ^[A-Za-z0-9_-]{8,64}$ ]] || {
    log_error "问数请求未正常完成: $(jq -r '.message // "无错误说明"' <<< "${response}")"
    exit 2
  }
  printf '%s' "${request_id}"
}

update_plugin_grant() {
  local granted="$1" response
  response="$(api_request PUT "/api/v1/admin/tenants/${LINGSQL_DEMO_TENANT_ID}/plugins/${KNOWLEDGE_PLUGIN_ID}" \
    "$(jq -cn --argjson granted "${granted}" '{granted:$granted}')" \
    "${CONTROL_TOKEN}" "${LINGSQL_ADMIN_TENANT_ID}")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || {
    log_error "更新租户插件授权失败: $(jq -r '.message // "无错误说明"' <<< "${response}")"
    exit 2
  }
}

seed_knowledge_document() {
  local document_id
  # 说明：管理端知识文档录入入口尚未建设（见 README 边界），验收脚本直接落库模拟“已审核并发布”的租户知识。
  mysql_query "DELETE FROM t_lingsql_engine_knowledge_document WHERE tenant_id='${LINGSQL_DEMO_TENANT_ID}' AND title='${KNOWLEDGE_TITLE}';" >/dev/null
  mysql_query "INSERT INTO t_lingsql_engine_knowledge_document (tenant_id, title, content, version, status, created_at) VALUES ('${LINGSQL_DEMO_TENANT_ID}', '${KNOWLEDGE_TITLE}', '${KNOWLEDGE_CONTENT}', '${KNOWLEDGE_VERSION}', 'PUBLISHED', NOW());" >/dev/null
  document_id="$(mysql_query "SELECT id FROM t_lingsql_engine_knowledge_document WHERE tenant_id='${LINGSQL_DEMO_TENANT_ID}' AND title='${KNOWLEDGE_TITLE}' ORDER BY id DESC LIMIT 1;")"
  [[ "${document_id}" =~ ^[0-9]+$ ]] || { log_error "知识文档落库失败"; exit 2; }
  printf '%s' "${document_id}"
}

remove_knowledge_document() {
  mysql_query "DELETE FROM t_lingsql_engine_knowledge_document WHERE tenant_id='${LINGSQL_DEMO_TENANT_ID}' AND title='${KNOWLEDGE_TITLE}';" >/dev/null
}

verify_denied_tenant_degrades() {
  local before after request_id
  before="$(count_plugin_invocations 'SUCCEEDED')"
  request_id="$(submit_question "${KNOWLEDGE_QUESTION}")"
  after="$(count_plugin_invocations 'SUCCEEDED')"
  # 说明：未授权时网关在预算、执行和 Outbox 之前就 fail-closed，因此不应留下任何成功调用痕迹。
  [[ "${before}" == "${after}" ]] || {
    log_error "未授权租户仍然执行了知识检索: before=${before}, after=${after}"
    exit 2
  }
  printf '%s' "${request_id}"
}

verify_granted_tenant_retrieves() {
  local before after attempt request_id
  before="$(count_plugin_invocations 'SUCCEEDED')"
  request_id="$(submit_question "${KNOWLEDGE_QUESTION}")"
  after="${before}"
  for attempt in $(seq 1 20); do
    after="$(count_plugin_invocations 'SUCCEEDED')"
    if [[ "${after}" -gt "${before}" ]]; then
      break
    fi
    sleep 1
  done
  [[ "${after}" -gt "${before}" ]] || {
    log_error "已授权租户的问数请求没有产生知识检索调用记录: before=${before}, after=${after}"
    exit 2
  }
  printf '%s' "${request_id}"
}

main() {
  for command in curl jq docker seq; do require_cmd "${command}"; done
  [[ -f "${ENV_FILE}" ]] || { log_error "未找到 ${ENV_FILE}"; exit 1; }
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  BASE_URL="${BASE_URL:-http://127.0.0.1:${APP_PORT:-8000}}"
  require_value "MYSQL_ROOT_PASSWORD" "${MYSQL_ROOT_PASSWORD:-}"
  require_value "MYSQL_DATABASE" "${MYSQL_DATABASE:-}"
  require_value "LINGSQL_DEMO_TENANT_ID" "${LINGSQL_DEMO_TENANT_ID:-}"
  require_value "LINGSQL_DEMO_ADMIN_USERNAME" "${LINGSQL_DEMO_ADMIN_USERNAME:-}"
  require_value "LINGSQL_DEMO_ADMIN_PASSWORD" "${LINGSQL_DEMO_ADMIN_PASSWORD:-}"
  require_value "LINGSQL_ADMIN_TENANT_ID" "${LINGSQL_ADMIN_TENANT_ID:-}"
  require_value "LINGSQL_ADMIN_USERNAME" "${LINGSQL_ADMIN_USERNAME:-}"
  require_value "LINGSQL_ADMIN_PASSWORD" "${LINGSQL_ADMIN_PASSWORD:-}"

  DEMO_TOKEN="$(acceptance_login "${BASE_URL}" "${LINGSQL_DEMO_TENANT_ID}" \
    "${LINGSQL_DEMO_ADMIN_USERNAME}" "${LINGSQL_DEMO_ADMIN_PASSWORD}")"
  CONTROL_TOKEN="$(acceptance_login "${BASE_URL}" "${LINGSQL_ADMIN_TENANT_ID}" \
    "${LINGSQL_ADMIN_USERNAME}" "${LINGSQL_ADMIN_PASSWORD}")"

  local denied_request granted_request document_id granted_total
  update_plugin_grant false
  denied_request="$(verify_denied_tenant_degrades)"
  log_error "未授权租户降级验证通过: requestId=${denied_request}"

  document_id="$(seed_knowledge_document)"
  update_plugin_grant true
  granted_request="$(verify_granted_tenant_retrieves)"
  granted_total="$(count_plugin_invocations 'SUCCEEDED')"
  log_error "已授权租户知识检索验证通过: requestId=${granted_request}, 成功调用=${granted_total}"

  remove_knowledge_document
  update_plugin_grant false

  mkdir -p "$(dirname "${REPORT_FILE}")"
  [[ ! -e "${REPORT_FILE}" ]] || { log_error "拒绝覆盖已有报告: ${REPORT_FILE}"; exit 2; }
  cat > "${REPORT_FILE}" <<EOF
# 受控知识检索全栈验收（非知识库完整性验收）

> 本报告只验证 \`knowledge_retrieve\` 在真实应用中的受控注入链路：租户授权、网关执行、调用留痕与未授权降级；脚本直接落库的合成知识文档只用于打通召回链路，不得作为知识库准确性、引用完整性、文档权限或客户 UAT 证据。

- 验收结论: PASS
- 未授权租户: 问数请求正常完成（插件调用在网关处 fail-closed，未产生任何成功调用记录），requestId=${denied_request}
- 已授权租户: 问数请求内成功执行受控知识检索，requestId=${granted_request}，累计成功调用=${granted_total}
- 合成知识文档: id=${document_id}，标题「${KNOWLEDGE_TITLE}」，版本 ${KNOWLEDGE_VERSION}，状态 PUBLISHED（验收后已清除）
- 覆盖边界: 召回排序、来源与版本标注、条数与长度截断、异常降级由单元测试覆盖（KnowledgeRetrievePluginTest、KnowledgeReferenceRendererTest、DbKnowledgeCorpusProviderTest、KnowledgeContextServiceTest）
EOF

  echo "知识检索全栈验收通过: ${REPORT_FILE}"
}

main "$@"
