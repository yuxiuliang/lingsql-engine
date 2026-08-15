import {createHash} from "node:crypto";
import {readFileSync, statSync, writeFileSync} from "node:fs";
import {basename, join, resolve} from "node:path";

const UTF8_BOM = /^\uFEFF/;
const SHA_PATTERN = /^[0-9a-f]{40}$/;

function fail(message) {
  throw new Error(message);
}

function parseArguments(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index];
    const value = argv[index + 1];
    if (!key?.startsWith("--") || value === undefined) {
      fail("SBOM 门禁参数必须使用 --名称 值 的形式。");
    }
    args[key.slice(2)] = value;
  }
  for (const required of ["backend", "frontend", "policy", "artifact", "commit", "output-dir"]) {
    if (!args[required]?.trim()) {
      fail(`SBOM 门禁缺少参数 --${required}。`);
    }
  }
  if (!SHA_PATTERN.test(args.commit)) {
    fail("发布 commit 必须是 40 位小写 Git SHA。");
  }
  return args;
}

function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8").replace(UTF8_BOM, ""));
  } catch (error) {
    fail(`无法读取 JSON 文件 ${file}: ${error.message}`);
  }
}

function licenseNames(component) {
  return (component.licenses ?? []).map((item) =>
    item.license?.id ?? item.license?.name ?? item.expression ?? ""
  ).filter(Boolean);
}

function validateBom(label, file, policy) {
  const bom = readJson(file);
  if (bom.bomFormat !== "CycloneDX" || !String(bom.specVersion ?? "").startsWith("1.")) {
    fail(`${label} SBOM 不是受支持的 CycloneDX 1.x JSON。`);
  }
  if (!Array.isArray(bom.components) || bom.components.length === 0) {
    fail(`${label} SBOM 没有组件。`);
  }
  if (!bom.metadata?.component?.name || !bom.metadata?.component?.version) {
    fail(`${label} SBOM 缺少根应用身份。`);
  }

  const allowed = new Set(policy.allowedLicenses);
  const denied = policy.deniedLicensePatterns.map((pattern) => new RegExp(pattern, "i"));
  const purls = new Set();
  const licenseCounts = new Map();

  for (const component of bom.components) {
    const identity = component.purl ?? `${component.name ?? ""}@${component.version ?? ""}`;
    if (!component.name || !component.version || !component.purl) {
      fail(`${label} SBOM 组件身份不完整: ${identity}`);
    }
    if (purls.has(component.purl)) {
      fail(`${label} SBOM 出现重复 PURL: ${component.purl}`);
    }
    purls.add(component.purl);

    const licenses = licenseNames(component);
    if (licenses.length === 0) {
      fail(`${label} SBOM 组件缺少许可证: ${component.purl}`);
    }
    for (const license of licenses) {
      if (denied.some((pattern) => pattern.test(license))) {
        fail(`${label} SBOM 命中禁止许可证 ${license}: ${component.purl}`);
      }
      if (!allowed.has(license)) {
        fail(`${label} SBOM 出现未经审阅的许可证 ${license}: ${component.purl}`);
      }
      licenseCounts.set(license, (licenseCounts.get(license) ?? 0) + 1);
    }
  }

  return {
    file: resolve(file),
    componentCount: bom.components.length,
    components: bom.components.map((component) => ({
      purl: component.purl,
      licenses: licenseNames(component).sort()
    })).sort((left, right) => left.purl.localeCompare(right.purl)),
    licenseCounts: Object.fromEntries([...licenseCounts.entries()].sort())
  };
}

function sha256(file) {
  return createHash("sha256").update(readFileSync(file)).digest("hex");
}

function fileEvidence(file) {
  const stat = statSync(file);
  if (!stat.isFile() || stat.size === 0) {
    fail(`供应链证据文件不存在或为空: ${file}`);
  }
  return {
    fileName: basename(file),
    size: stat.size,
    sha256: sha256(file)
  };
}

function markdownInventory(commit, policy, backend, frontend) {
  const lines = [
    "# LingSQL 第三方组件与许可证索引",
    "",
    `- 发布 commit：\`${commit}\``,
    `- 许可证策略：\`${policy.policyId}\``,
    `- 后端组件：${backend.componentCount}`,
    `- 前端组件：${frontend.componentCount}`,
    "",
    "本索引由发布 SBOM 自动生成。组件原始许可证和版权声明仍以对应上游制品为准。",
    "",
    "| 范围 | Package URL | 许可证 |",
    "| --- | --- | --- |"
  ];
  for (const [scope, evidence] of [["后端", backend], ["前端", frontend]]) {
    for (const component of evidence.components) {
      lines.push(`| ${scope} | \`${component.purl}\` | ${component.licenses.join(" / ")} |`);
    }
  }
  return `${lines.join("\n")}\n`;
}

function main() {
  const args = parseArguments(process.argv.slice(2));
  const policy = readJson(args.policy);
  if (policy.schemaVersion !== "1.0" || !policy.policyId
      || !Array.isArray(policy.allowedLicenses) || policy.allowedLicenses.length === 0
      || !Array.isArray(policy.deniedLicensePatterns)) {
    fail("许可证策略结构无效。");
  }
  if (new Set(policy.allowedLicenses).size !== policy.allowedLicenses.length) {
    fail("许可证策略包含重复的允许项。");
  }

  const backend = validateBom("后端", args.backend, policy);
  const frontend = validateBom("前端", args.frontend, policy);
  const outputDir = resolve(args["output-dir"]);
  const inventoryFile = join(outputDir, "THIRD_PARTY_COMPONENTS.md");
  writeFileSync(inventoryFile, markdownInventory(args.commit, policy, backend, frontend), "utf8");

  const evidenceFiles = [
    args.artifact,
    args.backend,
    args.frontend,
    "LICENSE",
    "THIRD_PARTY_NOTICES.md",
    inventoryFile
  ].map(fileEvidence);

  const manifest = {
    schemaVersion: "1.0",
    releaseCommit: args.commit,
    licensePolicyId: policy.policyId,
    backendComponentCount: backend.componentCount,
    frontendComponentCount: frontend.componentCount,
    backendLicenseCounts: backend.licenseCounts,
    frontendLicenseCounts: frontend.licenseCounts,
    files: evidenceFiles
  };
  const manifestFile = join(outputDir, "release-supply-chain-manifest.json");
  writeFileSync(manifestFile, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  console.log(`SBOM 门禁通过：后端 ${backend.componentCount} 个组件，前端 ${frontend.componentCount} 个组件。`);
  console.log(`供应链清单：${manifestFile}`);
}

try {
  main();
} catch (error) {
  console.error(`SBOM 门禁失败：${error.message}`);
  process.exitCode = 2;
}
