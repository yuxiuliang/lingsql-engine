import http from "node:http";
import { createHash } from "node:crypto";
import { readFileSync, statSync } from "node:fs";

const DEFAULT_PORT = 18080;
const MAX_REQUEST_BYTES = 2 * 1024 * 1024;
const CHAT_PATH = "/v1/chat/completions";
const USAGE_FAULT_PATH = "/__ci/fault/usage-missing";
const USAGE_FAULT_COUNT = 20;
let forcedMissingUsageRemaining = 0;

function sha256(content) {
  return createHash("sha256").update(content).digest("hex");
}

function loadPrivateResponseMap() {
  const path = process.env.STUB_RESPONSE_MAP_FILE?.trim();
  const expectedHash = process.env.STUB_RESPONSE_MAP_SHA256?.trim();
  if (!path && !expectedHash) return null;
  if (!path || !/^[0-9a-f]{64}$/.test(expectedHash ?? "")) {
    throw new Error("私有响应映射路径与 SHA-256 必须同时配置。");
  }
  const stat = statSync(path);
  const writableByOtherUsers = process.platform !== "win32" && (stat.mode & 0o022) !== 0;
  if (!stat.isFile() || stat.size <= 0 || stat.size > MAX_REQUEST_BYTES || writableByOtherUsers) {
    throw new Error("私有响应映射必须是非空、受限大小且组/其他用户不可写的普通文件。");
  }
  const content = readFileSync(path, "utf8");
  if (sha256(content) !== expectedHash) {
    throw new Error("私有响应映射 SHA-256 不匹配。");
  }
  const document = JSON.parse(content);
  if (document?.schemaVersion !== "internal-rehearsal-stub-map/1.0"
      || document?.purpose !== "INTERNAL_REHEARSAL"
      || !Array.isArray(document.entries)
      || document.entries.length === 0) {
    throw new Error("私有响应映射结构或用途不合法。");
  }
  const result = new Map();
  for (const entry of document.entries) {
    if (!/^[0-9a-f]{64}$/.test(entry?.questionSha256 ?? "")
        || typeof entry?.sql !== "string"
        || entry.sql.trim() === ""
        || entry.sql.length > 100_000
        || result.has(entry.questionSha256)) {
      throw new Error("私有响应映射条目不合法或存在重复。");
    }
    result.set(entry.questionSha256, entry.sql);
  }
  return result;
}

const privateResponseMap = loadPrivateResponseMap();

function resolvePort(value) {
  const port = Number.parseInt(value ?? String(DEFAULT_PORT), 10);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("STUB_PORT 必须是有效端口。");
  }
  return port;
}

function sendJson(response, status, payload) {
  const body = JSON.stringify(payload);
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Cache-Control": "no-store"
  });
  response.end(body);
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_REQUEST_BYTES) {
        reject(new Error("请求体超过集成桩限制。"));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    request.on("error", reject);
  });
}

function messageContent(messages, role) {
  const message = [...messages].reverse().find((candidate) => candidate?.role === role);
  return typeof message?.content === "string" ? message.content : "";
}

function sqlForQuestion(question) {
  if (privateResponseMap) {
    const mappedSql = privateResponseMap.get(sha256(question));
    if (!mappedSql) {
      throw new Error("请求不在受控内部演练映射中。");
    }
    return mappedSql;
  }
  if (question.includes("安全库存")) {
    return `SELECT p.product_name, s.warehouse_name, s.available_quantity, s.safety_quantity
FROM demo_inventory_snapshot s
JOIN demo_product p ON p.product_id = s.product_id
WHERE s.available_quantity < s.safety_quantity
ORDER BY p.product_name
LIMIT 100`;
  }
  if (question.includes("按商品") && question.includes("销售额")) {
    return `SELECT p.product_name, SUM(i.quantity * i.sale_price) AS sales_amount
FROM demo_sales_order o
JOIN demo_sales_order_item i ON i.order_id = o.order_id
JOIN demo_product p ON p.product_id = i.product_id
WHERE o.order_status = 'COMPLETED'
  AND o.order_date >= '2026-08-01'
  AND o.order_date < '2026-09-01'
GROUP BY p.product_id, p.product_name
ORDER BY sales_amount DESC
LIMIT 100`;
  }
  if (question.includes("2026年8月") && question.includes("销售额")) {
    return `SELECT SUM(i.quantity * i.sale_price) AS sales_amount
FROM demo_sales_order o
JOIN demo_sales_order_item i ON i.order_id = o.order_id
WHERE o.order_status = 'COMPLETED'
  AND o.order_date >= '2026-08-01'
  AND o.order_date < '2026-09-01'
LIMIT 100`;
  }
  throw new Error("集成桩只接受冻结的三条销售库存问题。");
}

function resolveCompletion(payload) {
  if (!Array.isArray(payload.messages)) {
    throw new Error("messages 必须是数组。");
  }
  const systemPrompt = messageContent(payload.messages, "system");
  const userPrompt = messageContent(payload.messages, "user");
  if (userPrompt === "health check") {
    return "OK";
  }
  if (systemPrompt.includes("专业的 MySQL 专家")) {
    return sqlForQuestion(userPrompt);
  }
  if (systemPrompt.includes("专业的数据分析助手")) {
    return "查询已完成，结果仅依据本次受控查询数据生成。";
  }
  throw new Error("无法识别模型调用阶段。");
}

function completionResponse(model, content) {
  const response = {
    id: "chatcmpl-lingsql-ci-stub",
    object: "chat.completion",
    created: 1786761600,
    model,
    choices: [{
      index: 0,
      message: {role: "assistant", content},
      finish_reason: "stop"
    }]
  };
  response.usage = {
      prompt_tokens: 48,
      completion_tokens: 16,
      total_tokens: 64
  };
  return response;
}

function deterministicFault(messages) {
  const serializedMessages = JSON.stringify(messages ?? []);
  if (serializedMessages.includes("[CI_UNKNOWN_USAGE_SETTLE]")) {
    return {omitUsage: true, responseId: "chatcmpl-ci-unknown-settle"};
  }
  if (serializedMessages.includes("[CI_UNKNOWN_USAGE_CANCEL]")) {
    return {omitUsage: true, responseId: "chatcmpl-ci-unknown-cancel"};
  }
  return {omitUsage: false, responseId: "chatcmpl-lingsql-ci-stub"};
}

const port = resolvePort(process.env.STUB_PORT);
const server = http.createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    sendJson(response, 200, {status: "UP", mode: "DETERMINISTIC_INTEGRATION_STUB"});
    return;
  }
  if (request.method === "POST" && request.url === USAGE_FAULT_PATH) {
    const remoteAddress = request.socket.remoteAddress ?? "";
    if (remoteAddress !== "127.0.0.1" && remoteAddress !== "::1" && remoteAddress !== "::ffff:127.0.0.1") {
      sendJson(response, 403, {error: {message: "故障开关仅允许容器本机调用。"}});
      return;
    }
    forcedMissingUsageRemaining = USAGE_FAULT_COUNT;
    sendJson(response, 200, {status: "ARMED", remaining: forcedMissingUsageRemaining});
    return;
  }
  if (request.method !== "POST" || request.url !== CHAT_PATH) {
    sendJson(response, 404, {error: {message: "仅开放 OpenAI Chat Completions 集成端点。"}});
    return;
  }
  try {
    const rawBody = await readBody(request);
    const payload = JSON.parse(rawBody);
    const model = typeof payload.model === "string" && payload.model.trim()
      ? payload.model.trim()
      : "QWEN-PLUS";
    const content = resolveCompletion(payload);
    const forcedFault = forcedMissingUsageRemaining > 0;
    if (forcedFault) {
      forcedMissingUsageRemaining -= 1;
    }
    const fault = forcedFault
      ? {omitUsage: true, responseId: `chatcmpl-ci-forced-unknown-${forcedMissingUsageRemaining + 1}`}
      : deterministicFault(payload.messages);
    const completion = completionResponse(model, content);
    completion.id = fault.responseId;
    if (fault.omitUsage) {
      delete completion.usage;
    }
    sendJson(response, 200, completion);
  } catch (error) {
    sendJson(response, 400, {error: {message: error.message}});
  }
});

server.listen(port, "0.0.0.0", () => {
  const mappingMode = privateResponseMap ? "PRIVATE_HASH_MAP" : "BUILT_IN_THREE_QUESTION";
  console.log(`LingSQL 确定性 OpenAI 集成桩已启动，端口 ${port}，模式 ${mappingMode}。`);
});

function shutdown(signal) {
  console.log(`收到 ${signal}，停止集成桩。`);
  server.close(() => process.exit(0));
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
