#!/usr/bin/env bash
set -euo pipefail

# GitHub 自动修复脚本：
# 1) 尝试修复前端高危依赖漏洞（npm audit fix）
# 2) 重新执行仓库门禁（后端测试 + 前端构建 + 漏洞审计）

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
allow_npm_audit_fix="${AUTO_REMEDIATE_ALLOW_NPM_AUDIT_FIX:-true}"

log_info() {
  echo "[auto-remediate] $*"
}

is_true() {
  local value="${1:-false}"
  [[ "${value}" == "1" || "${value}" == "true" || "${value}" == "TRUE" || "${value}" == "yes" || "${value}" == "YES" ]]
}

log_info "开始执行自动修复"
log_info "仓库根目录: ${repo_root}"

if is_true "${allow_npm_audit_fix}"; then
  log_info "步骤 1/2: 尝试修复前端依赖漏洞"
  cd "${repo_root}/web_src"
  npm ci
  npm audit fix --omit=dev || npm audit fix --omit=dev --registry=https://registry.npmjs.org || true
else
  log_info "步骤 1/2: 已跳过 npm audit fix（AUTO_REMEDIATE_ALLOW_NPM_AUDIT_FIX=${allow_npm_audit_fix}）"
fi

log_info "步骤 2/2: 重新执行 GitHub 门禁"
cd "${repo_root}"
if ! command -v mvn >/dev/null 2>&1; then
  echo "[auto-remediate] 缺少命令: mvn" >&2
  exit 2
fi
if ! mvn -v >/dev/null 2>&1; then
  echo "[auto-remediate] Maven 环境不可用，请检查 JAVA_HOME/JDK 配置" >&2
  exit 2
fi
"${repo_root}/scripts/ops/github_guard.sh"

log_info "自动修复执行完成"
