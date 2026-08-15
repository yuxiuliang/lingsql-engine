#!/usr/bin/env bash

# 验收脚本共享的登录边界。调用方负责预先检查 curl 与 jq。

acceptance_login() {
  local base_url="$1" tenant_id="$2" username="$3" password="$4"
  local login_body login_response token
  login_body="$(jq -cn \
    --arg tenantId "${tenant_id}" \
    --arg username "${username}" \
    --arg password "${password}" \
    '{tenantId:$tenantId,username:$username,password:$password}')"
  login_response="$(printf '%s' "${login_body}" | curl -fsS --connect-timeout 5 --max-time 30 \
    -H 'Content-Type: application/json; charset=UTF-8' \
    -X POST "${base_url%/}/api/v1/auth/login" --data-binary @-)"
  if [[ "$(jq -r '.code // 0' <<< "${login_response}")" != "200" ]]; then
    echo "[ERROR] 验收控制账号登录失败。" >&2
    return 2
  fi
  token="$(jq -r '.data.token // empty' <<< "${login_response}")"
  if [[ -z "${token}" ]]; then
    echo "[ERROR] 验收控制账号登录未返回 Token。" >&2
    return 2
  fi
  printf '%s' "${token}"
}

acceptance_resolve_control_token() {
  local base_url="$1" explicit_token="$2" tenant_id="$3" username="$4" password="$5"
  if [[ -n "${explicit_token}" ]]; then
    printf '%s' "${explicit_token}"
    return 0
  fi
  if [[ -z "${tenant_id}" || -z "${username}" || -z "${password}" ]]; then
    echo "[ERROR] 未提供 PILOT_CONTROL_TOKEN 时，必须同时配置 PILOT_CONTROL_TENANT_ID、PILOT_CONTROL_USERNAME、PILOT_CONTROL_PASSWORD。" >&2
    return 2
  fi
  acceptance_login "${base_url}" "${tenant_id}" "${username}" "${password}"
}
