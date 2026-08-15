#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

RELEASE_COMMIT="${RELEASE_COMMIT:-}"
RELEASE_ARTIFACT_FILE="${RELEASE_ARTIFACT_FILE:-}"
SBOM_OUTPUT_DIR="${SBOM_OUTPUT_DIR:-./dist}"
EMBEDDED_BACKEND_SBOM="target/classes/META-INF/sbom/application.cdx.json"

if [[ ! "${RELEASE_COMMIT}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "发布 commit 必须是 40 位小写 Git SHA。" >&2
  exit 2
fi
if [[ -z "${RELEASE_ARTIFACT_FILE}" || ! -f "${RELEASE_ARTIFACT_FILE}" ]]; then
  echo "RELEASE_ARTIFACT_FILE 必须指向已构建的发布 JAR。" >&2
  exit 2
fi
if [[ ! -s "${EMBEDDED_BACKEND_SBOM}" ]]; then
  echo "发布 JAR 构建没有生成内嵌后端 SBOM。" >&2
  exit 2
fi

mkdir -p "${SBOM_OUTPUT_DIR}"
RELEASE_ARTIFACT_FILE="$(cd "$(dirname "${RELEASE_ARTIFACT_FILE}")" && pwd)/$(basename "${RELEASE_ARTIFACT_FILE}")"
SBOM_OUTPUT_DIR="$(cd "${SBOM_OUTPUT_DIR}" && pwd)"
cp "${EMBEDDED_BACKEND_SBOM}" "${SBOM_OUTPUT_DIR}/lingsql-backend.cdx.json"
(
  cd web_src
  npm sbom --sbom-format=cyclonedx --sbom-type=application > "${SBOM_OUTPUT_DIR}/lingsql-frontend.cdx.json"
)
cp LICENSE THIRD_PARTY_NOTICES.md "${SBOM_OUTPUT_DIR}/"

node scripts/ops/sbom_guard.mjs \
  --backend "${SBOM_OUTPUT_DIR}/lingsql-backend.cdx.json" \
  --frontend "${SBOM_OUTPUT_DIR}/lingsql-frontend.cdx.json" \
  --policy docs/supply-chain-license-policy.json \
  --artifact "${RELEASE_ARTIFACT_FILE}" \
  --commit "${RELEASE_COMMIT}" \
  --output-dir "${SBOM_OUTPUT_DIR}"
