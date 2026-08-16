#!/usr/bin/env bash

# 验收脚本共享的租户首问就绪门禁。调用方负责预先检查 curl 与 jq。

acceptance_require_first_question_ready() {
  local base_url="$1" control_token="$2" tenant_id="$3"
  local tenant_encoded response valid failure_reasons
  tenant_encoded="$(jq -rn --arg value "${tenant_id}" '$value|@uri')"
  response="$(curl -fsS --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer ${control_token}" -H "X-Tenant-Id: ${tenant_id}" \
    "${base_url%/}/api/v1/admin/tenant-readiness/${tenant_encoded}/first-question")"
  if [[ "$(jq -r '.code // 0' <<< "${response}")" != "200" ]]; then
    echo "[ERROR] 控制账号无法读取租户首问就绪状态。" >&2
    return 2
  fi
  valid="$(jq -r '
    .data as $data |
    $data.ready == true and $data.status == "READY_TO_ASK" and
    ($data.checks | length) == 5 and
    (["TENANT_ACTIVE","TENANT_ADMIN_LOGIN","BILLING_AVAILABLE","DATASOURCE_SCHEMA_READY","MODEL_ROUTE_READY"]
      - [$data.checks[] | select(.passed == true) | .code] | length) == 0
  ' <<< "${response}")"
  if [[ "${valid}" != "true" ]]; then
    failure_reasons="$(jq -r '.data.failureReasons // [] | join("；")' <<< "${response}")"
    echo "[ERROR] 租户尚未达到首问就绪状态: ${failure_reasons:-检查项不完整}" >&2
    return 2
  fi
  jq -c '.data' <<< "${response}"
}
