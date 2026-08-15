import http from "node:http";

const DEFAULT_PORT = 18080;
const MAX_REQUEST_BYTES = 2 * 1024 * 1024;
const CHAT_PATH = "/v1/chat/completions";

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
  return {
    id: "chatcmpl-lingsql-ci-stub",
    object: "chat.completion",
    created: 1786761600,
    model,
    choices: [{
      index: 0,
      message: {role: "assistant", content},
      finish_reason: "stop"
    }],
    usage: {
      prompt_tokens: 48,
      completion_tokens: 16,
      total_tokens: 64
    }
  };
}

const port = resolvePort(process.env.STUB_PORT);
const server = http.createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    sendJson(response, 200, {status: "UP", mode: "DETERMINISTIC_INTEGRATION_STUB"});
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
    sendJson(response, 200, completionResponse(model, content));
  } catch (error) {
    sendJson(response, 400, {error: {message: error.message}});
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`LingSQL 确定性 OpenAI 集成桩已启动，端口 ${port}。`);
});

function shutdown(signal) {
  console.log(`收到 ${signal}，停止集成桩。`);
  server.close(() => process.exit(0));
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
