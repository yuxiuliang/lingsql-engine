#!/usr/bin/env bash
# 创建、校验并归档候选验收收据；收据是切流前必须消费的一次性证据。

set -euo pipefail
umask 077

log_error() { echo "[ERROR] $*" >&2; }
require_value() { [[ -n "$2" ]] || { log_error "$1 不能为空"; exit 1; }; }
sha256_file() { sha256sum "$1" | awk '{print $1}'; }

require_file() {
  [[ -s "$2" ]] || { log_error "$1 不存在或为空: $2"; exit 1; }
}

read_metadata() {
  local name="$1"
  local file="${RUN_DIR}/${name}"
  require_file "候选元数据 ${name}" "${file}"
  tr -d '[:space:]' < "${file}"
}

validate_sha() {
  [[ "$2" =~ ^[0-9a-f]{64}$ ]] || { log_error "$1 不是小写 SHA-256"; exit 1; }
}

load_candidate() {
  SLOT="$(read_metadata candidate_slot)"
  PORT="$(read_metadata candidate_port)"
  RELEASE_ID="$(read_metadata candidate_release_id)"
  METADATA_ARTIFACT_SHA="$(read_metadata candidate_artifact_sha256)"
  [[ "${SLOT}" == "blue" || "${SLOT}" == "green" ]] || { log_error "候选槽位非法"; exit 1; }
  [[ "${PORT}" =~ ^[1-9][0-9]{0,4}$ ]] || { log_error "候选端口非法"; exit 1; }
  (( PORT <= 65535 )) || { log_error "候选端口超出范围"; exit 1; }
  [[ "${RELEASE_ID}" =~ ^[A-Za-z0-9._-]+$ ]] || { log_error "候选发布 ID 非法"; exit 1; }
  validate_sha "候选制品摘要" "${METADATA_ARTIFACT_SHA}"
  APP_JAR="${DEPLOY_BASE}/${SLOT}/app.jar"
  require_file "候选 app.jar" "${APP_JAR}"
  ACTUAL_ARTIFACT_SHA="$(sha256_file "${APP_JAR}")"
  [[ "${ACTUAL_ARTIFACT_SHA}" == "${METADATA_ARTIFACT_SHA}" ]] || {
    log_error "候选 app.jar 与部署元数据不一致"
    exit 2
  }
}

validate_runtime_fingerprint() {
  local response actual_commit actual_snapshot actual_model_route schema_version model_count canonical
  response="$(curl -fsS --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer ${PILOT_CONTROL_TOKEN}" \
    -H "X-Tenant-Id: ${PILOT_CONTROL_TENANT_ID}" \
    "http://127.0.0.1:${PORT}/api/v1/admin/runtime/fingerprint")"
  [[ "$(jq -r '.code // 0' <<< "${response}")" == "200" ]] || {
    log_error "读取候选运行指纹失败"
    exit 2
  }
  actual_commit="$(jq -r '.data.commit // empty' <<< "${response}")"
  actual_snapshot="$(jq -r '.data.dataSnapshotId // empty' <<< "${response}")"
  actual_model_route="$(jq -r '.data.modelRouteFingerprint // empty' <<< "${response}")"
  schema_version="$(jq -r '.data.schemaVersion // empty' <<< "${response}")"
  model_count="$(jq -r '(.data.activeModels // []) | length' <<< "${response}")"
  [[ "${actual_commit}" == "${EXPECTED_RELEASE_COMMIT}" ]] || {
    log_error "候选运行提交不匹配: expected=${EXPECTED_RELEASE_COMMIT}, actual=${actual_commit:-<empty>}"
    exit 2
  }
  [[ -n "${schema_version}" && "${schema_version}" != "UNMANAGED" ]] || { log_error "候选 Flyway Schema 未纳管"; exit 2; }
  [[ "${model_count}" -gt 0 ]] || { log_error "候选实例没有激活模型"; exit 2; }
  if [[ -n "${EXPECTED_DATA_SNAPSHOT_ID}" && "${actual_snapshot}" != "${EXPECTED_DATA_SNAPSHOT_ID}" ]]; then
    log_error "候选数据快照不匹配"
    exit 2
  fi
  if [[ -n "${EXPECTED_MODEL_ROUTE_FINGERPRINT}" && "${actual_model_route}" != "${EXPECTED_MODEL_ROUTE_FINGERPRINT}" ]]; then
    log_error "候选模型路由指纹不匹配"
    exit 2
  fi
  canonical="$(jq -cS '.data | {commit,schemaVersion,dataSnapshotId,modelRouteFingerprint,activeModels}' <<< "${response}")"
  RUNTIME_FINGERPRINT_SHA="$(printf '%s' "${canonical}" | sha256sum | awk '{print $1}')"
}

load_evidence_hashes() {
  require_file "40 题 Suite" "${GQ_SUITE_FILE}"
  require_file "40 题逐题证据" "${CANDIDATE_GQ_EVIDENCE_FILE}"
  require_file "40 题报告" "${CANDIDATE_GQ_REPORT_FILE}"
  require_file "运行指纹报告" "${CANDIDATE_RUNTIME_REPORT_FILE}"
  require_file "三问验收报告" "${CANDIDATE_PILOT_REPORT_FILE}"
  require_file "灾备验收报告" "${CANDIDATE_BACKUP_REPORT_FILE}"
  require_file "商用门禁报告" "${CANDIDATE_COMMERCIAL_REPORT_FILE}"
  require_file "SLO 门禁报告" "${CANDIDATE_SLO_REPORT_FILE}"

  SUITE_SHA="$(sha256_file "${GQ_SUITE_FILE}")"
  validate_sha "预授权 40 题 Suite 摘要" "${GQ_EXPECTED_SUITE_SHA256}"
  [[ "${SUITE_SHA}" == "${GQ_EXPECTED_SUITE_SHA256}" ]] || { log_error "40 题 Suite 摘要不匹配"; exit 2; }
  GQ_EVIDENCE_SHA="$(sha256_file "${CANDIDATE_GQ_EVIDENCE_FILE}")"
  GQ_REPORT_SHA="$(sha256_file "${CANDIDATE_GQ_REPORT_FILE}")"
  RUNTIME_REPORT_SHA="$(sha256_file "${CANDIDATE_RUNTIME_REPORT_FILE}")"
  PILOT_REPORT_SHA="$(sha256_file "${CANDIDATE_PILOT_REPORT_FILE}")"
  BACKUP_REPORT_SHA="$(sha256_file "${CANDIDATE_BACKUP_REPORT_FILE}")"
  COMMERCIAL_REPORT_SHA="$(sha256_file "${CANDIDATE_COMMERCIAL_REPORT_FILE}")"
  SLO_REPORT_SHA="$(sha256_file "${CANDIDATE_SLO_REPORT_FILE}")"
}

receipt_seal() {
  local receipt_without_seal="$1"
  printf '%s' "${receipt_without_seal}" \
    | node -e 'const crypto = require("node:crypto"); const chunks = []; process.stdin.on("data", chunk => chunks.push(chunk)); process.stdin.on("end", () => process.stdout.write(crypto.createHmac("sha256", Buffer.from(process.env.CANDIDATE_RECEIPT_HMAC_KEY, "hex")).update(Buffer.concat(chunks)).digest("hex")));'
}

canonical_without_seal() {
  jq -cS 'del(.seal)' "$1"
}

create_receipt() {
  local temp_file payload seal
  [[ ! -e "${RECEIPT_FILE}" ]] || { log_error "候选验收收据已存在，拒绝覆盖: ${RECEIPT_FILE}"; exit 2; }
  load_candidate
  load_evidence_hashes
  validate_runtime_fingerprint
  temp_file="$(mktemp "${RUN_DIR}/.candidate-receipt.XXXXXX")"
  trap 'rm -f -- "${temp_file:-}"' EXIT
  payload="$(jq -cnS \
    --arg createdAt "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
    --arg commit "${EXPECTED_RELEASE_COMMIT}" --arg slot "${SLOT}" --argjson port "${PORT}" \
    --arg releaseId "${RELEASE_ID}" --arg artifact "${ACTUAL_ARTIFACT_SHA}" --arg runtimeState "${RUNTIME_FINGERPRINT_SHA}" \
    --arg suite "${SUITE_SHA}" --arg gqEvidence "${GQ_EVIDENCE_SHA}" \
    --arg gqReport "${GQ_REPORT_SHA}" --arg runtime "${RUNTIME_REPORT_SHA}" \
    --arg pilot "${PILOT_REPORT_SHA}" --arg backup "${BACKUP_REPORT_SHA}" \
    --arg commercial "${COMMERCIAL_REPORT_SHA}" --arg slo "${SLO_REPORT_SHA}" \
    '{schemaVersion:1,createdAt:$createdAt,commit:$commit,slot:$slot,port:$port,releaseId:$releaseId,artifactSha256:$artifact,runtimeFingerprintSha256:$runtimeState,suiteSha256:$suite,goldenQuestionEvidenceSha256:$gqEvidence,reports:{goldenQuestion:$gqReport,runtimeFingerprint:$runtime,pilotThreeQuestions:$pilot,backupRestore:$backup,commercialGate:$commercial,sloGate:$slo}}')"
  seal="$(receipt_seal "${payload}")"
  jq -cS --arg seal "${seal}" '. + {seal:$seal}' <<< "${payload}" > "${temp_file}"
  chmod 400 "${temp_file}"
  ln "${temp_file}" "${RECEIPT_FILE}" || { log_error "候选验收收据已存在，拒绝覆盖"; exit 2; }
  rm -f -- "${temp_file}"
  trap - EXIT
  echo "[INFO] 候选验收收据已创建: ${RECEIPT_FILE}"
}

verify_receipt() {
  local canonical expected_seal actual_seal
  require_file "候选验收收据" "${RECEIPT_FILE}"
  jq -e 'type == "object" and .schemaVersion == 1 and (.seal | type == "string")' "${RECEIPT_FILE}" >/dev/null || {
    log_error "候选验收收据格式非法"
    exit 2
  }
  canonical="$(canonical_without_seal "${RECEIPT_FILE}")"
  expected_seal="$(receipt_seal "${canonical}")"
  actual_seal="$(jq -r '.seal' "${RECEIPT_FILE}")"
  [[ "${actual_seal}" == "${expected_seal}" ]] || { log_error "候选验收收据封印不匹配"; exit 2; }

  load_candidate
  load_evidence_hashes
  validate_runtime_fingerprint
  jq -e \
    --arg commit "${EXPECTED_RELEASE_COMMIT}" --arg slot "${SLOT}" --argjson port "${PORT}" \
    --arg releaseId "${RELEASE_ID}" --arg artifact "${ACTUAL_ARTIFACT_SHA}" --arg runtimeState "${RUNTIME_FINGERPRINT_SHA}" \
    --arg suite "${SUITE_SHA}" --arg gqEvidence "${GQ_EVIDENCE_SHA}" \
    --arg gqReport "${GQ_REPORT_SHA}" --arg runtime "${RUNTIME_REPORT_SHA}" \
    --arg pilot "${PILOT_REPORT_SHA}" --arg backup "${BACKUP_REPORT_SHA}" \
    --arg commercial "${COMMERCIAL_REPORT_SHA}" --arg slo "${SLO_REPORT_SHA}" \
    '.commit == $commit and .slot == $slot and .port == $port and .releaseId == $releaseId and
     .artifactSha256 == $artifact and .runtimeFingerprintSha256 == $runtimeState and .suiteSha256 == $suite and
     .goldenQuestionEvidenceSha256 == $gqEvidence and
     .reports.goldenQuestion == $gqReport and .reports.runtimeFingerprint == $runtime and
     .reports.pilotThreeQuestions == $pilot and .reports.backupRestore == $backup and
     .reports.commercialGate == $commercial and .reports.sloGate == $slo' \
    "${RECEIPT_FILE}" >/dev/null || { log_error "候选验收收据与当前候选或证据不一致"; exit 2; }
  echo "[INFO] 候选验收收据复核通过: commit=${EXPECTED_RELEASE_COMMIT}, slot=${SLOT}"
}

archive_evidence_file() {
  local label="$1" source_file="$2" expected_sha="$3" target_file="$4" temp_file actual_sha
  if [[ -e "${target_file}" ]]; then
    [[ -f "${target_file}" ]] || { log_error "${label} 归档目标不是普通文件"; exit 2; }
    actual_sha="$(sha256_file "${target_file}")"
    [[ "${actual_sha}" == "${expected_sha}" ]] || { log_error "${label} 归档已存在且摘要不同，拒绝覆盖"; exit 2; }
    chmod 400 "${target_file}"
    return 0
  fi
  temp_file="$(mktemp "${EVIDENCE_ARCHIVE_DIR}/.evidence.XXXXXX")"
  if ! cp -- "${source_file}" "${temp_file}" || ! chmod 400 "${temp_file}"; then
    rm -f -- "${temp_file}"
    log_error "${label} 复制失败"
    exit 2
  fi
  actual_sha="$(sha256_file "${temp_file}")"
  [[ "${actual_sha}" == "${expected_sha}" ]] || { rm -f -- "${temp_file}"; log_error "${label} 复制后摘要不一致"; exit 2; }
  ln "${temp_file}" "${target_file}" || { rm -f -- "${temp_file}"; log_error "${label} 归档目标已存在，拒绝覆盖"; exit 2; }
  rm -f -- "${temp_file}"
}

archive_evidence() {
  EVIDENCE_ARCHIVE_DIR="${DEPLOY_BASE}/evidence/${RELEASE_ID}"
  if [[ -e "${EVIDENCE_ARCHIVE_DIR}" && ! -d "${EVIDENCE_ARCHIVE_DIR}" ]]; then
    log_error "候选证据归档路径不是目录"
    exit 2
  fi
  mkdir -p "${EVIDENCE_ARCHIVE_DIR}"
  chmod 700 "${EVIDENCE_ARCHIVE_DIR}"
  archive_evidence_file "40 题逐题证据" "${CANDIDATE_GQ_EVIDENCE_FILE}" "${GQ_EVIDENCE_SHA}" "${EVIDENCE_ARCHIVE_DIR}/golden-question-evidence.jsonl"
  archive_evidence_file "40 题报告" "${CANDIDATE_GQ_REPORT_FILE}" "${GQ_REPORT_SHA}" "${EVIDENCE_ARCHIVE_DIR}/golden-question-report.md"
  archive_evidence_file "运行指纹报告" "${CANDIDATE_RUNTIME_REPORT_FILE}" "${RUNTIME_REPORT_SHA}" "${EVIDENCE_ARCHIVE_DIR}/runtime-fingerprint-report.md"
  archive_evidence_file "三问验收报告" "${CANDIDATE_PILOT_REPORT_FILE}" "${PILOT_REPORT_SHA}" "${EVIDENCE_ARCHIVE_DIR}/pilot-three-questions-report.md"
  archive_evidence_file "灾备验收报告" "${CANDIDATE_BACKUP_REPORT_FILE}" "${BACKUP_REPORT_SHA}" "${EVIDENCE_ARCHIVE_DIR}/backup-restore-report.md"
  archive_evidence_file "商用门禁报告" "${CANDIDATE_COMMERCIAL_REPORT_FILE}" "${COMMERCIAL_REPORT_SHA}" "${EVIDENCE_ARCHIVE_DIR}/commercial-gate-report.md"
  archive_evidence_file "SLO 门禁报告" "${CANDIDATE_SLO_REPORT_FILE}" "${SLO_REPORT_SHA}" "${EVIDENCE_ARCHIVE_DIR}/slo-gate-report.md"
}

reserve_receipt() {
  local archive_file
  require_file "候选验收收据" "${RECEIPT_FILE}"
  load_candidate
  archive_evidence
  mkdir -p "${ARCHIVE_DIR}"
  chmod 700 "${ARCHIVE_DIR}"
  archive_file="${ARCHIVE_DIR}/${RELEASE_ID}.json"
  if [[ -e "${archive_file}" ]]; then
    [[ "${RECEIPT_FILE}" -ef "${archive_file}" ]] || { log_error "归档收据已存在且内容来源不同，拒绝覆盖: ${archive_file}"; exit 2; }
    echo "[INFO] 候选验收收据归档已预留: ${archive_file}"
    return 0
  fi
  ln "${RECEIPT_FILE}" "${archive_file}" || { log_error "无法以不可覆盖方式归档候选收据"; exit 2; }
  chmod 400 "${archive_file}"
  echo "[INFO] 候选验收收据归档已预留: ${archive_file}"
}

finalize_receipt() {
  local archive_file
  require_file "候选验收收据" "${RECEIPT_FILE}"
  load_candidate
  archive_file="${ARCHIVE_DIR}/${RELEASE_ID}.json"
  require_file "候选验收归档" "${archive_file}"
  [[ "${RECEIPT_FILE}" -ef "${archive_file}" ]] || { log_error "当前收据与预留归档不是同一文件"; exit 2; }
  rm -f -- "${RECEIPT_FILE}"
  echo "[INFO] 候选验收收据已归档: ${archive_file}"
}

main() {
  local action="${1:-}"
  DEPLOY_BASE="${LINGSQL_DEPLOY_BASE:-/opt/lingsql}"
  RUN_DIR="${DEPLOY_BASE}/run"
  RECEIPT_FILE="${RUN_DIR}/candidate_acceptance_receipt.json"
  ARCHIVE_DIR="${DEPLOY_BASE}/receipts"
  PILOT_CONTROL_TOKEN="${PILOT_CONTROL_TOKEN:-}"
  CANDIDATE_RECEIPT_HMAC_KEY="${CANDIDATE_RECEIPT_HMAC_KEY:-}"
  PILOT_CONTROL_TENANT_ID="${PILOT_CONTROL_TENANT_ID:-sys_tenant}"
  EXPECTED_RELEASE_COMMIT="${EXPECTED_RELEASE_COMMIT:-}"
  EXPECTED_DATA_SNAPSHOT_ID="${EXPECTED_DATA_SNAPSHOT_ID:-}"
  EXPECTED_MODEL_ROUTE_FINGERPRINT="${EXPECTED_MODEL_ROUTE_FINGERPRINT:-}"
  GQ_SUITE_FILE="${GQ_SUITE_FILE:-}"
  GQ_EXPECTED_SUITE_SHA256="${GQ_EXPECTED_SUITE_SHA256:-}"
  CANDIDATE_GQ_EVIDENCE_FILE="${CANDIDATE_GQ_EVIDENCE_FILE:-}"
  CANDIDATE_GQ_REPORT_FILE="${CANDIDATE_GQ_REPORT_FILE:-}"
  CANDIDATE_RUNTIME_REPORT_FILE="${CANDIDATE_RUNTIME_REPORT_FILE:-}"
  CANDIDATE_PILOT_REPORT_FILE="${CANDIDATE_PILOT_REPORT_FILE:-}"
  CANDIDATE_BACKUP_REPORT_FILE="${CANDIDATE_BACKUP_REPORT_FILE:-}"
  CANDIDATE_COMMERCIAL_REPORT_FILE="${CANDIDATE_COMMERCIAL_REPORT_FILE:-}"
  CANDIDATE_SLO_REPORT_FILE="${CANDIDATE_SLO_REPORT_FILE:-}"

  command -v jq >/dev/null 2>&1 || { log_error "缺少命令: jq"; exit 1; }
  command -v sha256sum >/dev/null 2>&1 || { log_error "缺少命令: sha256sum"; exit 1; }
  require_value "操作" "${action}"
  mkdir -p "${RUN_DIR}"
  command -v curl >/dev/null 2>&1 || { log_error "缺少命令: curl"; exit 1; }
  command -v node >/dev/null 2>&1 || { log_error "缺少命令: node"; exit 1; }
  require_value "PILOT_CONTROL_TOKEN" "${PILOT_CONTROL_TOKEN}"
  require_value "CANDIDATE_RECEIPT_HMAC_KEY" "${CANDIDATE_RECEIPT_HMAC_KEY}"
  [[ "${CANDIDATE_RECEIPT_HMAC_KEY}" =~ ^[0-9a-f]{64}$ ]] || { log_error "CANDIDATE_RECEIPT_HMAC_KEY 必须为 64 位小写十六进制密钥"; exit 1; }
  require_value "EXPECTED_RELEASE_COMMIT" "${EXPECTED_RELEASE_COMMIT}"
  [[ "${EXPECTED_RELEASE_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || { log_error "EXPECTED_RELEASE_COMMIT 必须为 40 位小写 Git SHA"; exit 1; }
  case "${action}" in
    create) create_receipt ;;
    verify) verify_receipt ;;
    reserve) verify_receipt; reserve_receipt ;;
    finalize) finalize_receipt ;;
    *) log_error "用法: $0 {create|verify|reserve|finalize}"; exit 1 ;;
  esac
}

main "$@"
