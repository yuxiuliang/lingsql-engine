-- 演示数据刷新：以 CURDATE() 为基准重建销售与库存数据。
--
-- 用途：sql/demo_sales_inventory.sql 是固定日期的冻结种子，用于回归；其日期不会随时间推移
-- 而更新，因此「昨日经营快报」「环比下滑」「滞销 90 天」这类按当前日期取数的日报场景
-- 在冻结种子上必然返回空结果。本脚本只用于本地演示与交付预演，把数据重新对齐到今天。
--
-- 特性：
--   1. 幂等：每次执行先清空交易数据再全量重建，重复执行结果一致（确定性伪随机，无 RAND()）；
--   2. 保留 5 张演示表的字段与演示语义，表名/列名与租户白名单一致；
--   3. 覆盖日报六个预置场景所需的全部数据形态（见文件末尾「数据形态自检」）。
--
-- 不进入生产迁移流程，不作为 Flyway 迁移文件使用。

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0. 清空交易数据（先子后父，兼容外键）
-- ---------------------------------------------------------------------------
DELETE FROM demo_sales_order_item;
DELETE FROM demo_sales_order;
DELETE FROM demo_inventory_snapshot;
DELETE FROM demo_customer;
DELETE FROM demo_product;

-- ---------------------------------------------------------------------------
-- 1. 主数据：8 个客户 / 12 个商品（4 个品类）
-- ---------------------------------------------------------------------------
INSERT INTO demo_customer (customer_id, customer_name, customer_level, region_name) VALUES
(1, '杭州宏发商贸有限公司',   '批发客户', '华东'),
(2, '上海联华超市有限公司',   '连锁客户', '华东'),
(3, '苏州恒通物流有限公司',   '批发客户', '华东'),
(4, '广州南方食品有限公司',   '批发客户', '华南'),
(5, '深圳万家便利店集团',     '连锁客户', '华南'),
(6, '北京京客隆商业集团',     '连锁客户', '华北'),
(7, '天津华润万家有限公司',   '连锁客户', '华北'),
(8, '成都蜀通商贸有限公司',   '批发客户', '华南');

-- 商品 12（老款保温杯）刻意不参与销售明细生成，用于「滞销 90 天」场景。
INSERT INTO demo_product (product_id, product_name, category_name, unit_price, unit_cost) VALUES
(1,  '可乐 330ml 罐装',   '冷饮',   3.50,   2.40),
(2,  '农夫山泉 550ml',    '冷饮',   2.00,   1.30),
(3,  '红牛 250ml',        '冷饮',   6.00,   4.50),
(4,  '鲜橙汁 1L',         '冷饮',  12.00,   8.00),
(5,  '薯片原味 70g',      '零食',   8.50,   5.60),
(6,  '巧克力夹心饼干',    '零食',   9.90,   6.20),
(7,  '混合坚果 500g',     '零食',  25.00,  17.00),
(8,  '抽纸 3 层 6 包',    '日用品', 15.90, 10.50),
(9,  '洗衣液 2L',         '日用品', 28.00, 19.00),
(10, '牙膏 120g',         '日用品', 12.50,  7.80),
(11, '电水壶 1.7L',       '小家电', 129.00, 92.00),
(12, '老款保温杯（停产）','小家电', 89.00,  65.00);

-- ---------------------------------------------------------------------------
-- 2. 订单：d.n = 距今天数（0=今天，1=昨天，…，99=100 天前）
--    每天 6～14 单，客户与渠道按确定性规则轮转，少量退款单。
-- ---------------------------------------------------------------------------
INSERT INTO demo_sales_order (order_id, order_no, customer_id, order_date, order_status, sales_channel)
WITH RECURSIVE d(n) AS (
    SELECT 0 UNION ALL SELECT n + 1 FROM d WHERE n < 99
),
s(k) AS (
    SELECT 1 UNION ALL SELECT k + 1 FROM s WHERE k < 14
)
SELECT d.n * 100 + s.k,
       CONCAT('SO', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL d.n DAY), '%Y%m%d'), LPAD(s.k, 3, '0')),
       1 + ((d.n * 3 + s.k * 5) % 8),
       DATE_SUB(CURDATE(), INTERVAL d.n DAY),
       CASE WHEN (d.n + s.k) % 17 = 0 THEN 'REFUNDED' ELSE 'COMPLETED' END,
       ELT(1 + ((d.n + s.k) % 3), '批发', '线上', '门店')
FROM d, s
WHERE s.k <= 10 + ((d.n * 7 + 3) % 5);

-- ---------------------------------------------------------------------------
-- 3. 订单明细：每单 1～2 行，商品在 1～11 之间轮转（12 不出现在明细）
--    冷饮（商品 1～4）：昨天只卖 1 件、前天卖 12 件 —— 制造「昨日下滑最多：冷饮」
-- ---------------------------------------------------------------------------
INSERT INTO demo_sales_order_item (item_id, order_id, product_id, quantity, sale_price)
WITH RECURSIVE d(n) AS (
    SELECT 0 UNION ALL SELECT n + 1 FROM d WHERE n < 99
),
s(k) AS (
    SELECT 1 UNION ALL SELECT k + 1 FROM s WHERE k < 14
),
j(m) AS (
    SELECT 1 UNION ALL SELECT m + 1 FROM j WHERE m < 2
),
item_lines AS (
    SELECT d.n AS n,
           s.k AS k,
           j.m AS m,
           d.n * 100 + s.k AS order_id,
           1 + ((d.n * 5 + s.k * 3 + j.m * 7) % 11) AS product_id,
           CASE
               WHEN 1 + ((d.n * 5 + s.k * 3 + j.m * 7) % 11) <= 4 THEN
                   CASE WHEN d.n = 1 THEN 1 WHEN d.n = 2 THEN 12 ELSE 4 + ((d.n + s.k + j.m) % 10) END
               ELSE 4 + ((d.n + s.k + j.m) % 10)
           END AS quantity
    FROM d, s, j
    WHERE s.k <= 10 + ((d.n * 7 + 3) % 5)
      AND j.m <= 1 + ((d.n + s.k) % 2)
)
SELECT l.order_id * 10 + l.m,
       l.order_id,
       l.product_id,
       l.quantity,
       ROUND(p.unit_price * (1 + ((l.n + l.k) % 3) * 0.02), 2)
FROM item_lines l
JOIN demo_product p ON p.product_id = l.product_id;

-- ---------------------------------------------------------------------------
-- 4. 异常大单：3 天前一笔 60 台电水壶，合计 7,740 元（约为常规单笔的 20 倍）
--    刻意不放在昨天：单笔 5 万级大单会占掉昨日营收的绝大部分，把「昨日环比下滑」的
--    演示故事彻底冲掉。放在 3 天前，既满足「最近 7 天异常交易」场景，又不污染昨日快报。
-- ---------------------------------------------------------------------------
INSERT INTO demo_sales_order (order_id, order_no, customer_id, order_date, order_status, sales_channel)
VALUES (199,
        CONCAT('SO', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '%Y%m%d'), '900'),
        4,
        DATE_SUB(CURDATE(), INTERVAL 3 DAY),
        'COMPLETED',
        '批发');

INSERT INTO demo_sales_order_item (item_id, order_id, product_id, quantity, sale_price)
VALUES (1991, 199, 11, 60, 129.00);

-- ---------------------------------------------------------------------------
-- 5. 库存快照（快照日期 = 今天）
--    商品 1/2/3/7 低于安全库存 —— 触发「缺货预警」
--    商品 12 库存 640 件、100 天无销售 —— 触发「滞销库存」
-- ---------------------------------------------------------------------------
INSERT INTO demo_inventory_snapshot (snapshot_date, product_id, warehouse_name, available_quantity, safety_quantity)
WITH RECURSIVE p(pid) AS (
    SELECT 1 UNION ALL SELECT pid + 1 FROM p WHERE pid < 11
)
SELECT CURDATE(),
       pid,
       '杭州中心仓',
       CASE pid WHEN 1 THEN 0 WHEN 2 THEN 46 WHEN 3 THEN 12 WHEN 7 THEN 30 ELSE 300 + pid * 40 END,
       CASE pid WHEN 1 THEN 200 WHEN 2 THEN 300 WHEN 3 THEN 150 WHEN 7 THEN 120 ELSE 150 END
FROM p;

INSERT INTO demo_inventory_snapshot (snapshot_date, product_id, warehouse_name, available_quantity, safety_quantity)
VALUES (CURDATE(), 12, '杭州中心仓', 640, 100);

-- ---------------------------------------------------------------------------
-- 数据形态自检（人工执行，不属于脚本必须部分）
-- ---------------------------------------------------------------------------
-- 昨日销售额与订单数：
--   SELECT COUNT(*) AS orders,
--          ROUND(SUM(i.quantity * i.sale_price), 2) AS revenue
--   FROM demo_sales_order o
--   JOIN demo_sales_order_item i ON i.order_id = o.order_id
--   WHERE o.order_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
--     AND o.order_status = 'COMPLETED';
--
-- 冷饮昨日 vs 前日（应出现明显下滑）：
--   SELECT o.order_date,
--          SUM(CASE WHEN p.category_name = '冷饮' THEN i.quantity * i.sale_price ELSE 0 END) AS cold_revenue
--   FROM demo_sales_order o
--   JOIN demo_sales_order_item i ON i.order_id = o.order_id
--   JOIN demo_product p ON p.product_id = i.product_id
--   WHERE o.order_date IN (DATE_SUB(CURDATE(), INTERVAL 1 DAY), DATE_SUB(CURDATE(), INTERVAL 2 DAY))
--   GROUP BY o.order_date;
--
-- 缺货项：
--   SELECT * FROM demo_inventory_snapshot
--   WHERE snapshot_date = CURDATE() AND available_quantity < safety_quantity;
--
-- 最近 7 天异常大单（应命中 3 天前那笔 7,740 元）：
--   SELECT o.order_id, o.order_no, o.order_date, ROUND(i.quantity * i.sale_price, 2) AS amount
--   FROM demo_sales_order o
--   JOIN demo_sales_order_item i ON i.order_id = o.order_id
--   WHERE o.order_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
--     AND i.quantity * i.sale_price > 5000
--   ORDER BY amount DESC;
--
-- 滞销 90 天：
--   SELECT p.product_id, p.product_name, SUM(s.available_quantity) AS stock
--   FROM demo_product p
--   JOIN demo_inventory_snapshot s ON s.product_id = p.product_id
--   WHERE p.product_id NOT IN (
--       SELECT DISTINCT i.product_id FROM demo_sales_order_item i
--       JOIN demo_sales_order o ON o.order_id = i.order_id
--       WHERE o.order_date >= DATE_SUB(CURDATE(), INTERVAL 90 DAY))
--   GROUP BY p.product_id, p.product_name;
