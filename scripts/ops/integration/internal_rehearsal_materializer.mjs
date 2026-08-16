import { createHash } from "node:crypto";
import { chmod, readFile, writeFile } from "node:fs/promises";

const SHA256_PATTERN = /^[0-9a-f]{64}$/;
const COMMIT_PATTERN = /^[0-9a-f]{40}$/;
const EXECUTION_MODES = new Set(["DETERMINISTIC_STUB", "REAL_MODEL"]);

function sha256(content) {
  return createHash("sha256").update(content).digest("hex");
}

function requireText(value, field) {
  if (typeof value !== "string" || value.trim() === "" || value === "DRAFT_UNVERIFIED") {
    throw new Error(`${field} 必须是已验证的非空值。`);
  }
  return value;
}

function requireHash(value, field, pattern = SHA256_PATTERN) {
  if (typeof value !== "string" || !pattern.test(value)) {
    throw new Error(`${field} 格式不合法。`);
  }
  return value;
}

function parseArguments(argv) {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const name = argv[index];
    const value = argv[index + 1];
    if (!name?.startsWith("--") || !value) {
      throw new Error("参数必须使用 --name value 形式。所需参数：draft、runtime、security-map、suite-output、stub-map-output。");
    }
    values.set(name.slice(2), value);
  }
  for (const name of ["draft", "runtime", "security-map", "suite-output", "stub-map-output"]) {
    if (!values.has(name)) {
      throw new Error(`缺少参数 --${name}。`);
    }
  }
  return Object.fromEntries(values);
}

function validateDraft(draft) {
  if (draft?.schemaVersion !== "internal-rehearsal-draft/1.0"
      || draft?.draftStatus !== "DRAFT_UNVERIFIED"
      || draft?.purpose !== "INTERNAL_REHEARSAL"
      || draft?.fixture?.fictionalData !== true) {
    throw new Error("输入不是明确标记为虚构数据的 INTERNAL_REHEARSAL 草案。");
  }
  if (!Array.isArray(draft.businessCases) || draft.businessCases.length !== 40
      || draft.businessCases.filter((item) => item.priority === "P0").length !== 20
      || draft.businessCases.filter((item) => item.priority === "P1").length !== 20
      || !Array.isArray(draft.securityCases) || draft.securityCases.length === 0) {
    throw new Error("草案不满足 40 题、P0/P1 各 20 题及安全题约束。");
  }
  const ids = [...draft.businessCases, ...draft.securityCases].map((item) => item.id);
  if (new Set(ids).size !== ids.length) {
    throw new Error("草案题目 ID 存在重复。");
  }
}

function materializeEnvironment(runtime) {
  if (runtime?.evidencePurpose !== "INTERNAL_REHEARSAL") {
    throw new Error("运行环境 evidencePurpose 必须为 INTERNAL_REHEARSAL。");
  }
  if (!EXECUTION_MODES.has(runtime.executionMode)) {
    throw new Error("executionMode 仅允许 DETERMINISTIC_STUB 或 REAL_MODEL。");
  }
  return {
    environmentId: requireText(runtime.environmentId, "environmentId"),
    releaseCommit: requireHash(runtime.releaseCommit, "releaseCommit", COMMIT_PATTERN),
    flywaySchemaVersion: requireText(runtime.flywaySchemaVersion, "flywaySchemaVersion"),
    dataSnapshotId: requireText(runtime.dataSnapshotId, "dataSnapshotId"),
    modelRouteFingerprint: requireHash(runtime.modelRouteFingerprint, "modelRouteFingerprint"),
    modelProvider: requireText(runtime.modelProvider, "modelProvider"),
    modelName: requireText(runtime.modelName, "modelName"),
    modelVersion: requireText(runtime.modelVersion, "modelVersion"),
    networkProfile: requireText(runtime.networkProfile, "networkProfile"),
    concurrency: 1,
    evidencePurpose: "INTERNAL_REHEARSAL",
    executionMode: runtime.executionMode
  };
}

function businessCase(item) {
  if (item.baselineSeconds !== "DRAFT_UNVERIFIED") {
    throw new Error(`${item.id} 的人工基线不得在内部材料化时伪造。`);
  }
  if (typeof item.truthSql !== "string" || !/^\s*(SELECT|WITH)\b/i.test(item.truthSql)) {
    throw new Error(`${item.id} 缺少只读真值 SQL。`);
  }
  return {
    id: item.id,
    priority: item.priority,
    question: requireText(item.question, `${item.id}.question`),
    mode: "INDEPENDENT",
    baselineSeconds: null,
    ...(item.classification ? {classification: item.classification} : {}),
    expected: item.expected
  };
}

function validateSecurityMap(document) {
  if (document?.schemaVersion !== "internal-rehearsal-security-map/1.0"
      || document?.purpose !== "INTERNAL_REHEARSAL"
      || typeof document?.sqlByKind !== "object") {
    throw new Error("安全题 SQL 映射结构或用途不合法。");
  }
  for (const kind of ["WRITE_SQL", "CROSS_TENANT", "OUT_OF_ALLOWLIST"]) {
    requireText(document.sqlByKind[kind], `sqlByKind.${kind}`);
  }
}

function stubEntry(item, sql) {
  const question = requireText(item.question, `${item.id}.question`);
  return {
    questionSha256: sha256(question),
    sql: sql.trim().replace(/;+\s*$/, "")
  };
}

async function writeProtectedJson(path, content) {
  await writeFile(path, content, {encoding: "utf8", flag: "wx", mode: 0o400});
  await chmod(path, 0o400);
}

async function main() {
  const args = parseArguments(process.argv.slice(2));
  const expectedDraftHash = requireHash(
    process.env.INTERNAL_REHEARSAL_EXPECTED_DRAFT_SHA256,
    "INTERNAL_REHEARSAL_EXPECTED_DRAFT_SHA256"
  );
  const expectedRuntimeHash = requireHash(
    process.env.INTERNAL_REHEARSAL_EXPECTED_RUNTIME_SHA256,
    "INTERNAL_REHEARSAL_EXPECTED_RUNTIME_SHA256"
  );
  const expectedSecurityMapHash = requireHash(
    process.env.INTERNAL_REHEARSAL_EXPECTED_SECURITY_MAP_SHA256,
    "INTERNAL_REHEARSAL_EXPECTED_SECURITY_MAP_SHA256"
  );
  const [draftText, runtimeText, securityMapText] = await Promise.all([
    readFile(args.draft, "utf8"),
    readFile(args.runtime, "utf8"),
    readFile(args["security-map"], "utf8")
  ]);
  if (sha256(draftText) !== expectedDraftHash
      || sha256(runtimeText) !== expectedRuntimeHash
      || sha256(securityMapText) !== expectedSecurityMapHash) {
    throw new Error("草案、运行环境或安全映射 SHA-256 与预授权值不一致。");
  }
  const draft = JSON.parse(draftText);
  const runtime = JSON.parse(runtimeText);
  const securityMap = JSON.parse(securityMapText);
  validateDraft(draft);
  validateSecurityMap(securityMap);
  const environment = materializeEnvironment(runtime);
  const frozenAt = requireText(runtime.capturedAt, "capturedAt");
  if (!Number.isFinite(Date.parse(frozenAt))) {
    throw new Error("capturedAt 必须是合法日期时间。");
  }
  const suite = {
    schemaVersion: "internal-rehearsal-runtime/1.0",
    sourceDraftStatus: "DRAFT_UNVERIFIED",
    purpose: "INTERNAL_REHEARSAL",
    suiteId: draft.suiteId,
    suiteVersion: `${draft.suiteVersion}-RUNTIME`,
    sowId: "INTERNAL_REHEARSAL_NOT_CUSTOMER_SOW",
    frozenAt,
    environment,
    businessCases: draft.businessCases.map(businessCase),
    securityCases: draft.securityCases.map(({id, kind, question, expected}) => ({id, kind, question, expected}))
  };
  const suiteContent = `${JSON.stringify(suite, null, 2)}\n`;
  const suiteDigest = sha256(suiteContent);
  const entries = [
    ...draft.businessCases.map((item) => stubEntry(item, item.truthSql)),
    ...draft.securityCases.map((item) => stubEntry(item, securityMap.sqlByKind[item.kind]))
  ];
  if (new Set(entries.map((item) => item.questionSha256)).size !== entries.length) {
    throw new Error("问题 SHA-256 存在重复，拒绝生成协议桩映射。");
  }
  const stubMap = {
    schemaVersion: "internal-rehearsal-stub-map/1.0",
    purpose: "INTERNAL_REHEARSAL",
    sourceDraftSha256: expectedDraftHash,
    suiteSha256: suiteDigest,
    entries
  };
  const mapContent = `${JSON.stringify(stubMap, null, 2)}\n`;
  const mapDigest = sha256(mapContent);
  await writeProtectedJson(args["suite-output"], suiteContent);
  await writeProtectedJson(args["stub-map-output"], mapContent);
  process.stdout.write(JSON.stringify({
    purpose: "INTERNAL_REHEARSAL",
    executionMode: environment.executionMode,
    suiteSha256: suiteDigest,
    stubMapSha256: mapDigest,
    businessCaseCount: draft.businessCases.length,
    securityCaseCount: draft.securityCases.length
  }));
}

await main();
