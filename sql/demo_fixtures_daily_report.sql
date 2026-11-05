-- 演示夹具：三个"并行验证"日报员工（demo_sales）
--
-- 为什么需要它：`demo-preflight.ps1` 的「日报 cron」检查要求 demo_sales 下至少有 3 个
-- 名字以"并行验证"开头、cron 为 `0 0 8 * * ?` 的员工；手册里的日报演示也依赖它们。
-- 这三个员工此前是手工创建的，没有进开户流程，导致**全新环境 preflight 直接红**
-- （2026-10-31 实测）。
--
-- 本文件幂等：按 (tenant_id, employee_name) 判断是否已存在，存在则跳过。
-- 员工默认 `schedule_enabled=0`（不自动跑），演示时再临时开启，避免每天产生无谓调用与费用。
--
-- 用法：pwsh -File scripts/demo/provision-demo.ps1 会自动应用；
--       也可手工：mysql ... --database=lingsql_demo_control < sql/demo_fixtures_daily_report.sql

INSERT INTO t_lingsql_engine_digital_employee
    (tenant_id, employee_code, employee_name, scenario, default_question, schedule_cron,
     schedule_enabled, push_enabled, push_channels, status, is_deleted, create_time, update_time)
SELECT 'demo_sales', 'FIXTURE_DR_YESTERDAY', '并行验证-昨日快报', '昨日经营快报',
       '昨天销售额、订单数和毛利分别是多少？', '0 0 8 * * ?',
       0, 1, 'webhook', 1, 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM t_lingsql_engine_digital_employee
    WHERE tenant_id = 'demo_sales' AND employee_name = '并行验证-昨日快报' AND is_deleted = 0
);

INSERT INTO t_lingsql_engine_digital_employee
    (tenant_id, employee_code, employee_name, scenario, default_question, schedule_cron,
     schedule_enabled, push_enabled, push_channels, status, is_deleted, create_time, update_time)
SELECT 'demo_sales', 'FIXTURE_DR_STOCKOUT', '并行验证-缺货清单', '缺货预警',
       '哪些商品当前库存低于安全库存？', '0 0 8 * * ?',
       0, 1, 'webhook', 1, 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM t_lingsql_engine_digital_employee
    WHERE tenant_id = 'demo_sales' AND employee_name = '并行验证-缺货清单' AND is_deleted = 0
);

INSERT INTO t_lingsql_engine_digital_employee
    (tenant_id, employee_code, employee_name, scenario, default_question, schedule_cron,
     schedule_enabled, push_enabled, push_channels, status, is_deleted, create_time, update_time)
SELECT 'demo_sales', 'FIXTURE_DR_CATEGORY', '并行验证-品类排名', '品类销售排名',
       '按商品统计9月销售额，从高到低排列。', '0 0 8 * * ?',
       0, 1, 'webhook', 1, 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM t_lingsql_engine_digital_employee
    WHERE tenant_id = 'demo_sales' AND employee_name = '并行验证-品类排名' AND is_deleted = 0
);
