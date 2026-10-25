// 生成确定性的销售＋库存演示数据（同一随机种子 → 同一份结果）。
//
// 为什么要生成而不是直接用 sql/demo_sales_inventory.sql：
// 仓库自带的样例只有 4 个客户、6 张订单，不足以支撑「按商品/按地区/按渠道」这类多维度提问，
// 也不包含退货状态；而演示必须可复现，所以这里用固定种子生成，输出一段幂等的 INSERT 脚本。
//
// 用法：node scripts/demo/seed/generate-demo-data.mjs > .local-private/demo/generated/seed.sql
// 输出只含 demo_* 五张表，不触碰任何其他库表。

const CUSTOMERS = [
  { id: 1, name: '启明商贸有限公司', level: 'A', region: '华东' },
  { id: 2, name: '远航连锁零售有限公司', level: 'B', region: '华东' },
  { id: 3, name: '星河便利集团', level: 'B', region: '华南' },
  { id: 4, name: '新禾供应链有限公司', level: 'A', region: '西南' },
  { id: 5, name: '恒达食品批发部', level: 'C', region: '华南' },
  { id: 6, name: '北方优选商超', level: 'B', region: '华北' },
  { id: 7, name: '蓉城生活超市', level: 'C', region: '西南' },
  { id: 8, name: '江南百货集团', level: 'A', region: '华东' }
];

const PRODUCTS = [
  { id: 101, name: '轻盈苏打水 330ml', category: '饮料', price: 6.0, cost: 3.2 },
  { id: 102, name: '原味燕麦片 500g', category: '食品', price: 28.0, cost: 17.0 },
  { id: 103, name: '便携保温杯 450ml', category: '日用', price: 79.0, cost: 45.0 },
  { id: 104, name: '坚果礼盒 750g', category: '食品', price: 118.0, cost: 72.0 },
  { id: 105, name: '无糖气泡水 480ml', category: '饮料', price: 5.5, cost: 2.6 },
  { id: 106, name: '全麦吐司 400g', category: '食品', price: 16.0, cost: 9.5 },
  { id: 107, name: '厨房纸巾 4 卷', category: '日用', price: 22.0, cost: 12.8 },
  { id: 108, name: '冷萃咖啡 270ml', category: '饮料', price: 12.0, cost: 6.4 },
  { id: 109, name: '海盐苏打饼干', category: '食品', price: 9.9, cost: 5.4 },
  { id: 110, name: '洗洁精 1.5L', category: '日用', price: 18.5, cost: 10.2 },
  { id: 111, name: '纯净水 550ml', category: '饮料', price: 2.0, cost: 0.9 },
  { id: 112, name: '每日坚果 25g', category: '食品', price: 6.8, cost: 3.9 }
];

const CHANNELS = ['线上', '门店', '批发'];
const WAREHOUSES = ['上海一仓', '广州一仓', '成都一仓'];

// 固定种子的线性同余发生器，保证任何机器、任何时间生成的数据完全一致。
function createRandom(seed) {
  let state = seed >>> 0;
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 0x100000000;
  };
}

const random = createRandom(20261025);
const pick = (items) => items[Math.floor(random() * items.length)];
const intBetween = (min, max) => min + Math.floor(random() * (max - min + 1));

function formatMoney(value) {
  return value.toFixed(2);
}

function orderDate(index) {
  // 2026-07-01 起共 92 天，覆盖 7/8/9 三个月，9 月数据量最足。
  const start = Date.UTC(2026, 6, 1);
  const dayOffset = index % 92;
  const date = new Date(start + dayOffset * 86400000);
  return date.toISOString().slice(0, 10);
}

const ORDER_COUNT = 600;
const orders = [];
const items = [];
let itemId = 1;

for (let index = 0; index < ORDER_COUNT; index += 1) {
  const orderId = 10001 + index;
  const roll = random();
  const status = roll < 0.88 ? 'COMPLETED' : roll < 0.96 ? 'CANCELLED' : 'REFUNDED';
  const date = orderDate(index);
  const customer = pick(CUSTOMERS);
  orders.push({
    id: orderId,
    no: `SO${date.replace(/-/g, '')}${String(index + 1).padStart(3, '0')}`,
    customerId: customer.id,
    date,
    status,
    channel: pick(CHANNELS)
  });

  const lineCount = intBetween(1, 3);
  const used = new Set();
  for (let line = 0; line < lineCount; line += 1) {
    let product = pick(PRODUCTS);
    let guard = 0;
    while (used.has(product.id) && guard < 8) {
      product = pick(PRODUCTS);
      guard += 1;
    }
    used.add(product.id);
    const discount = 0.9 + random() * 0.1;
    items.push({
      id: itemId++,
      orderId,
      productId: product.id,
      quantity: intBetween(1, 160),
      price: product.price * discount
    });
  }
}

const inventory = [];
// 每个商品在每个仓库各一条快照；只让 3 个商品低于安全库存，便于演示「异常清单」有确定答案。
const BELOW_SAFETY = new Set([102, 105, 109]);
for (const product of PRODUCTS) {
  for (const warehouse of WAREHOUSES) {
    const safety = intBetween(80, 200);
    const available = BELOW_SAFETY.has(product.id) && warehouse === WAREHOUSES[0]
      ? Math.max(1, safety - intBetween(20, 60))
      : safety + intBetween(60, 600);
    inventory.push({ date: '2026-09-30', productId: product.id, warehouse, available, safety });
  }
}

const lines = [];
lines.push('-- 由 scripts/demo/seed/generate-demo-data.mjs 生成的确定性演示数据，请勿手工修改。');
lines.push('-- 只清理并重建 demo_* 五张表，不影响其他任何表。');
lines.push('SET FOREIGN_KEY_CHECKS = 0;');
for (const table of ['demo_sales_order_item', 'demo_sales_order', 'demo_inventory_snapshot', 'demo_product', 'demo_customer']) {
  lines.push(`DELETE FROM ${table};`);
}
lines.push('SET FOREIGN_KEY_CHECKS = 1;');
lines.push('');

lines.push(`INSERT INTO demo_customer (customer_id, customer_name, customer_level, region_name) VALUES`);
lines.push(CUSTOMERS.map((row) => `(${row.id}, '${row.name}', '${row.level}', '${row.region}')`).join(',\n') + ';');
lines.push('');

lines.push(`INSERT INTO demo_product (product_id, product_name, category_name, unit_price, unit_cost) VALUES`);
lines.push(PRODUCTS.map((row) => `(${row.id}, '${row.name}', '${row.category}', ${formatMoney(row.price)}, ${formatMoney(row.cost)})`).join(',\n') + ';');
lines.push('');

lines.push(`INSERT INTO demo_sales_order (order_id, order_no, customer_id, order_date, order_status, sales_channel) VALUES`);
lines.push(orders.map((row) => `(${row.id}, '${row.no}', ${row.customerId}, '${row.date}', '${row.status}', '${row.channel}')`).join(',\n') + ';');
lines.push('');

lines.push(`INSERT INTO demo_sales_order_item (item_id, order_id, product_id, quantity, sale_price) VALUES`);
lines.push(items.map((row) => `(${row.id}, ${row.orderId}, ${row.productId}, ${row.quantity}, ${formatMoney(row.price)})`).join(',\n') + ';');
lines.push('');

lines.push(`INSERT INTO demo_inventory_snapshot (snapshot_date, product_id, warehouse_name, available_quantity, safety_quantity) VALUES`);
lines.push(inventory.map((row) => `('${row.date}', ${row.productId}, '${row.warehouse}', ${row.available}, ${row.safety})`).join(',\n') + ';');
lines.push('');

process.stdout.write(lines.join('\n'));
