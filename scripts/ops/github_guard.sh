#!/usr/bin/env bash
set -euo pipefail

# 本地模拟 GitHub 门禁：后端测试 + 前端构建 + 依赖漏洞扫描。
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"

echo "[guard] 1/3 后端测试"
cd "${repo_root}"
mvn -B -ntp test -DskipITs

echo "[guard] 2/3 前端构建"
cd "${repo_root}/web_src"
npm ci
npm run build

echo "[guard] 3/3 前端依赖漏洞审计（高危及以上）"
cd "${repo_root}/web_src"
"${repo_root}/scripts/ops/npm_audit_with_fallback.sh"

echo "[guard] 全部门禁通过"
