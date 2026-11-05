import http from 'node:http';
import { appendFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

// 本机演示用的确定性 OpenAI 兼容桩（不联网、不执行应用代码）。
//
// 作用：在没有真实模型 Key 的机器上，把「自然语言 → SQL → 受控执行 → 分析答复」这条链路跑通，
// 并固定 10 个演示问题的答案形态，便于反复彩排。
//
// 诚实边界：
//   1. 7 个可回答问题由固定 SQL 命中，答复文本基于**真实查询结果**拼装，数字不是编的；
//   2. 3 个不可回答问题故意查询演示库中不存在的月份／历史／维度，走「结果为空 → 说明原因 + 澄清问题」的受控降级路径；
//   3. 接真实模型后同一批问题会走模型真实推理，两者不可混为一谈，交付时必须说明当前是哪一种。

const port = Number.parseInt(process.env.STUB_PORT ?? '18082', 10);
const logPath = resolve(process.env.STUB_LOG ?? '.local-private/demo/ten-question-requests.jsonl');
mkdirSync(dirname(logPath), { recursive: true });

const MONTH_START = '2026-09-01';
const MONTH_END = '2026-10-01';

const CASES = [
  {
    id: 'Q1',
    question: '2026年9月已完成订单的销售额是多少？',
    sql: `SELECT SUM(i.quantity * i.sale_price) AS \`销售额\`
FROM demo_sales_order o
JOIN demo_sales_order_item i ON i.order_id = o.order_id
WHERE o.order_status = 'COMPLETED'
  AND o.order_date >= '${MONTH_START}'
  AND o.order_date < '${MONTH_END}'
LIMIT 100`,
    answer: (rows) => `2026 年 9 月已完成订单的销售额合计 ${money(rows[0]?.['销售额'])}。`
  },
  {
    id: 'Q2',
    question: '哪些商品当前库存低于安全库存？',
    sql: `SELECT p.product_name AS \`商品名称\`, s.warehouse_name AS \`仓库\`,
       s.available_quantity AS \`可用数量\`, s.safety_quantity AS \`安全库存\`
FROM demo_inventory_snapshot s
JOIN demo_product p ON p.product_id = s.product_id
WHERE s.available_quantity < s.safety_quantity
ORDER BY (s.safety_quantity - s.available_quantity) DESC
LIMIT 100`,
    answer: (rows) => rows.length === 0
      ? '当前没有商品低于安全库存。'
      : `当前有 ${rows.length} 个商品低于安全库存：` +
        rows.map((row) => `${row['商品名称']}（${row['仓库']} 可用 ${row['可用数量']}，安全线 ${row['安全库存']}，缺口 ${row['安全库存'] - row['可用数量']}）`).join('；') + '。'
  },
  {
    id: 'Q3',
    question: '按商品统计9月销售额，从高到低排列。',
    // 别名用中文：结果表头、结果概览的「主维度/合计」以及图表标题都直接取结果列名，
    // 用英文别名会在界面上露出 product_name / sales_amount，业务用户看不懂。
    sql: `SELECT p.product_name AS \`商品名称\`, SUM(i.quantity * i.sale_price) AS \`销售额\`
FROM demo_sales_order o
JOIN demo_sales_order_item i ON i.order_id = o.order_id
JOIN demo_product p ON p.product_id = i.product_id
WHERE o.order_status = 'COMPLETED'
  AND o.order_date >= '${MONTH_START}'
  AND o.order_date < '${MONTH_END}'
GROUP BY p.product_id, p.product_name
ORDER BY \`销售额\` DESC
LIMIT 100`,
    answer: (rows) => `9 月销售额从高到低（前 ${Math.min(rows.length, 5)} 名）：` +
      rows.slice(0, 5).map((row, index) => `${index + 1}. ${row['商品名称']} ${money(row['销售额'])}`).join('；') +
      (rows.length > 5 ? `；其余 ${rows.length - 5} 个商品见结果表。` : '。')
  },
  {
    id: 'Q4',
    question: '华东地区客户9月贡献了多少销售额？',
    sql: `SELECT c.region_name AS \`地区\`, SUM(i.quantity * i.sale_price) AS \`销售额\`
FROM demo_sales_order o
JOIN demo_sales_order_item i ON i.order_id = o.order_id
JOIN demo_customer c ON c.customer_id = o.customer_id
WHERE o.order_status = 'COMPLETED'
  AND o.order_date >= '${MONTH_START}'
  AND o.order_date < '${MONTH_END}'
  AND c.region_name = '华东'
GROUP BY c.region_name
LIMIT 100`,
    answer: (rows) => rows.length === 0
      ? '没有华东地区 9 月已完成订单的记录。'
      : `华东地区客户 9 月贡献的已完成订单销售额为 ${money(rows[0]['销售额'])}。`
  },
  {
    id: 'Q5',
    question: '各销售渠道的订单数量分别是多少？',
    sql: `SELECT o.sales_channel AS \`销售渠道\`, COUNT(*) AS \`订单数\`
FROM demo_sales_order o
GROUP BY o.sales_channel
ORDER BY \`订单数\` DESC
LIMIT 100`,
    answer: (rows) => `各销售渠道订单数：${rows.map((row) => `${row['销售渠道']} ${row['订单数']} 单`).join('；')}。`
  },
  {
    id: 'Q6',
    question: '哪个商品的毛利率最高？',
    sql: `SELECT p.product_name AS \`商品\`,
       ROUND((p.unit_price - p.unit_cost) / p.unit_price * 100, 1) AS \`毛利率\`
FROM demo_product p
ORDER BY \`毛利率\` DESC
LIMIT 100`,
    answer: (rows) => rows.length === 0
      ? '商品主数据为空，无法计算毛利率。'
      : `毛利率最高的是 ${rows[0]['商品']}，毛利率 ${rows[0]['毛利率']}%。`
  },
  {
    id: 'Q7',
    question: '2026年9月有多少个客户下过单？',
    sql: `SELECT COUNT(DISTINCT o.customer_id) AS \`客户数\`
FROM demo_sales_order o
WHERE o.order_date >= '${MONTH_START}'
  AND o.order_date < '${MONTH_END}'
  AND o.order_status = 'COMPLETED'
LIMIT 100`,
    answer: (rows) => `2026 年 9 月有 ${rows[0]?.['客户数']} 个客户完成了下单。`
  },
  {
    id: 'Q8',
    question: '2026年10月的销售额是多少？',
    // 注意：这里必须是单行汇总，不能 GROUP BY o.order_id——按订单分组会返回最多 100 行
    // 每条订单的小计，既不是"总销售额"，也会让 LIMIT 截断。
    sql: `SELECT SUM(i.quantity * i.sale_price) AS \`销售额\`
FROM demo_sales_order o
JOIN demo_sales_order_item i ON i.order_id = o.order_id
WHERE o.order_status = 'COMPLETED'
  AND o.order_date >= '2026-10-01'
  AND o.order_date < '2026-11-01'
LIMIT 100`,
    // 答案必须由结果驱动：演示数据集换成覆盖 10 月的那一份后，写死的"10 月没有数据"会与
    // 实际返回的行直接矛盾，在客户面前等于演示失败。
    answer: (rows) => {
      const amount = rows[0]?.['销售额'];
      const number = Number(amount);
      if (!Number.isFinite(number) || number <= 0) {
        return '演示库里 2026 年 10 月还没有已完成订单，这个月的销售额为空。' +
          '要回答这个问题，需要等 10 月数据接入；或者先看 9 月与 8 月的环比？';
      }
      return `2026 年 10 月已完成订单的销售额合计 ${money(amount)}。`;
    }
  },
  {
    id: 'Q9',
    question: '和2025年9月相比销售额增长了多少？',
    // 同上：同比需要单行汇总，不能按订单分组。
    sql: `SELECT SUM(i.quantity * i.sale_price) AS \`销售额\`
FROM demo_sales_order o
JOIN demo_sales_order_item i ON i.order_id = o.order_id
WHERE o.order_status = 'COMPLETED'
  AND o.order_date >= '2025-09-01'
  AND o.order_date < '2025-10-01'
LIMIT 100`,
    answer: (rows) => {
      const amount = rows[0]?.['销售额'];
      const number = Number(amount);
      if (!Number.isFinite(number) || number <= 0) {
        return '演示库没有导入 2025 年的历史数据，因此算不出同比。' +
          '要算同比，需要先把去年同期订单接入；在此之前可以先看月度环比。';
      }
      return `2025 年 9 月已完成订单的销售额合计 ${money(amount)}，可用于与 2026 年 9 月做同比。`;
    }
  },
  {
    id: 'Q10',
    question: '各客户的满意度评分是多少？',
    sql: `SELECT c.customer_name AS \`客户\`, c.customer_level AS \`客户等级\`
FROM demo_customer c
WHERE c.customer_level = 'S'
LIMIT 100`,
    answer: () => '客户表里没有「满意度评分」字段，只有客户等级（A／B／C）和所属区域，' +
      '查不到 S 级客户，也就没有评分可以回答。' +
      '要回答满意度，需要接入调研或工单评分数据；或者先用客户等级做分层替代？'
  },
  // D1：经营日报场景。相对 CURDATE() 取数，用于验证「昨日经营快报」的定时推送链路。
  // 不进入 ask-demo-questions.ps1 的 10 问清单（该脚本自带固定列表），也不在 UNANSWERABLE 内。
  {
    id: 'D1',
    question: '昨天销售额、订单数和毛利分别是多少？',
    // 同上：日报的三个指标也用中文别名，推送预览与结果概览才读得懂。
    sql: `SELECT COUNT(DISTINCT o.order_id) AS \`订单数\`,
       SUM(i.quantity * i.sale_price) AS \`销售额\`,
       SUM(i.quantity * (i.sale_price - p.unit_cost)) AS \`毛利\`
FROM demo_sales_order o
JOIN demo_sales_order_item i ON i.order_id = o.order_id
JOIN demo_product p ON p.product_id = i.product_id
WHERE o.order_status = 'COMPLETED'
  AND o.order_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
LIMIT 100`,
    answer: (rows) => {
      const row = rows[0] ?? {};
      const orders = Number(row['订单数']);
      if (!Number.isFinite(orders) || orders === 0) {
        return '昨天没有已完成订单，日报无数据可推。';
      }
      return `昨日销售额 ${money(row['销售额'])}，订单 ${orders} 单，毛利 ${money(row['毛利'])}。`;
    }
  },
  // ISO1：行级隔离的常驻验证样本。共享表里同时存在 iso_a（350）与 iso_b（1000）的数据，
  // 以 iso_a 身份提问时，SqlRewriter 必须把 o.tenant_id = 'iso_a' 注入进去——
  // 结果里**只有 iso_a 一行**才算隔离生效；若出现 iso_b，就是隔离失效。
  // 注意：这条规则不进入 10 问清单，也不在 UNANSWERABLE 内。
  {
    id: 'ISO1',
    question: 'demo_shared_orders 表里各租户的订单金额分别是多少？',
    sql: `SELECT o.tenant_id AS \`租户\`, COUNT(*) AS \`订单数\`, SUM(o.amount) AS \`金额\`
FROM demo_shared_orders o
GROUP BY o.tenant_id
LIMIT 100`,
    answer: (rows) => rows.length === 0
      ? '没有查到任何订单。'
      : `共 ${rows.length} 个租户：` + rows.map((row) => `${row['租户']} ${row['订单数']} 单 / ${money(row['金额'])}`).join('；') + '。'
  }
];

function normalize(question) {
  return String(question ?? '').replace(/\s+/g, '').replace(/[?？]/g, '');
}

const CASE_BY_QUESTION = new Map(CASES.map((entry) => [normalize(entry.question), entry]));
const UNANSWERABLE = new Set(['Q8', 'Q9', 'Q10']);

function money(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return String(value ?? '');
  return `¥${number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function parseRows(prompt) {
  const marker = prompt.indexOf('查询结果');
  if (marker < 0) return [];
  const body = prompt.slice(marker);
  const rows = [];
  const rowPattern = /第\s*\d+\s*行：([^]*?)(?=第\s*\d+\s*行：|$)/g;
  let match;
  while ((match = rowPattern.exec(body)) !== null) {
    const fields = {};
    for (const pair of match[1].split('；')) {
      const trimmed = pair.trim();
      if (trimmed === '') continue;
      const index = trimmed.indexOf('=');
      if (index <= 0) continue;
      fields[trimmed.slice(0, index).trim()] = trimmed.slice(index + 1).trim();
    }
    if (Object.keys(fields).length > 0) rows.push(fields);
  }
  return rows;
}

function answerFor(prompt) {
  const question = (prompt.match(/用户问题:\s*([^\n]*)/) ?? [])[1] ?? '';
  const execution = (prompt.match(/执行状态:\s*(\S+)/) ?? [])[1] ?? '';
  const rows = parseRows(prompt);
  const entry = CASE_BY_QUESTION.get(normalize(question));

  if (execution !== '成功') {
    return '这次查询没有执行成功，我不给数字，先说明原因再重试。';
  }
  if (!entry) {
    return rows.length === 0
      ? '当前数据里没有与这个问题匹配的记录，需要先确认口径。'
      : `本次查询返回 ${rows.length} 行结果，请以结果表为准。`;
  }
  if (rows.length === 0 && !UNANSWERABLE.has(entry.id)) {
    return `按当前数据没有查到记录：${entry.question}`;
  }
  return entry.answer(rows);
}

function sqlFor(question) {
  const entry = CASE_BY_QUESTION.get(normalize(question));
  if (entry) return entry.sql;
  // 未登记的问题：返回可执行但必然为空的查询，让「结果为空必须说明原因」的约束接管。
  return `SELECT o.order_no
FROM demo_sales_order o
WHERE 1 = 0
LIMIT 100`;
}

function readBody(request) {
  return new Promise((resolvePromise, reject) => {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(chunk));
    request.on('end', () => resolvePromise(Buffer.concat(chunks).toString('utf8')));
    request.on('error', reject);
  });
}

function lastMessage(messages, role) {
  const found = [...messages].reverse().find((message) => message?.role === role);
  return typeof found?.content === 'string' ? found.content : '';
}

const server = http.createServer(async (request, response) => {
  if (request.method === 'GET' && request.url === '/health') {
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ status: 'UP', mode: 'TEN_QUESTION_DEMO_STUB' }));
    return;
  }
  if (request.method !== 'POST' || request.url !== '/v1/chat/completions') {
    response.writeHead(404, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ error: { message: 'not found' } }));
    return;
  }
  const raw = await readBody(request);
  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    response.writeHead(400, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ error: { message: 'bad json' } }));
    return;
  }

  const system = lastMessage(payload.messages, 'system');
  const user = lastMessage(payload.messages, 'user');
  const stage = user === 'health check' ? 'probe' : system.includes('MySQL 专家') ? 'architect' : 'analyst';
  let content;
  if (stage === 'probe') {
    content = 'OK';
  } else if (stage === 'architect') {
    content = sqlFor(user);
  } else {
    content = answerFor(user);
  }

  appendFileSync(logPath, `${JSON.stringify({
    at: new Date().toISOString(),
    stage,
    user: user.slice(0, 4000),
    content
  })}\n`, 'utf8');

  const body = JSON.stringify({
    id: 'chatcmpl-lingsql-demo-stub',
    object: 'chat.completion',
    created: Math.floor(Date.now() / 1000),
    model: payload.model,
    choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' }],
    usage: { prompt_tokens: 120, completion_tokens: 48, total_tokens: 168 }
  });
  response.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) });
  response.end(body);
});

server.listen(port, '127.0.0.1', () => {
  console.log(`10 问演示桩已启动 http://127.0.0.1:${port}，请求落盘 ${logPath}`);
});
