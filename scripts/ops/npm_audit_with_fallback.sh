#!/usr/bin/env bash
set -euo pipefail

# npm 镜像源可能不支持 audit 接口（如 npmmirror），失败时自动切换官方源重试。
audit_level="${NPM_AUDIT_LEVEL:-high}"
omit_dev="${NPM_AUDIT_OMIT_DEV:-true}"
max_retry="${NPM_AUDIT_RETRY:-3}"

args=(audit "--audit-level=${audit_level}")
if [[ "${omit_dev}" == "true" ]]; then
  args+=(--omit=dev)
fi

run_audit_with_registry() {
  local registry="$1"
  local label="$2"
  local print_last_error="${3:-true}"
  local attempt=1
  local output=""
  while [[ "${attempt}" -le "${max_retry}" ]]; do
    echo "[npm-audit] 使用 ${label} 执行安全审计（第 ${attempt}/${max_retry} 次）"
    if [[ -n "${registry}" ]]; then
      if output="$(npm "${args[@]}" --registry="${registry}" 2>&1)"; then
        echo "${output}"
        return 0
      fi
    else
      if output="$(npm "${args[@]}" 2>&1)"; then
        echo "${output}"
        return 0
      fi
    fi
    attempt=$((attempt + 1))
    if [[ "${attempt}" -le "${max_retry}" ]]; then
      echo "[npm-audit] 审计失败，准备重试..."
      sleep 2
    fi
  done
  if [[ "${print_last_error}" == "true" ]]; then
    echo "${output}" >&2
  fi
  return 1
}

current_registry="$(npm config get registry 2>/dev/null || true)"
if run_audit_with_registry "" "当前 registry(${current_registry})" "false"; then
  echo "[npm-audit] 审计通过"
  exit 0
fi

echo "[npm-audit] 当前 registry 审计失败，切换官方源重试: https://registry.npmjs.org"
run_audit_with_registry "https://registry.npmjs.org" "官方源" "true" || exit 1
echo "[npm-audit] 官方源审计通过"
