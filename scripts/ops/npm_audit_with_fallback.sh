#!/usr/bin/env bash
set -euo pipefail

# npm 镜像源可能不支持 audit 接口（如 npmmirror），失败时自动切换官方源重试。
audit_level="${NPM_AUDIT_LEVEL:-high}"
omit_dev="${NPM_AUDIT_OMIT_DEV:-true}"

args=(audit "--audit-level=${audit_level}")
if [[ "${omit_dev}" == "true" ]]; then
  args+=(--omit=dev)
fi

echo "[npm-audit] 使用当前 registry 执行安全审计"
if npm "${args[@]}"; then
  echo "[npm-audit] 审计通过"
  exit 0
fi

echo "[npm-audit] 当前 registry 审计失败，切换官方源重试: https://registry.npmjs.org"
npm "${args[@]}" --registry=https://registry.npmjs.org
echo "[npm-audit] 官方源审计通过"
