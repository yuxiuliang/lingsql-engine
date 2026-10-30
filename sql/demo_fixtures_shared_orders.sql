-- 演示夹具：多租户共享表（逻辑隔离验证用）
--
-- 作用：`demo-preflight.ps1` 的「租户隔离（LOGICAL）」检查以 iso_a 身份提问，
-- 断言结果里只出现 iso_a、绝不出现 iso_b。该检查依赖：
--   1) 本文件创建的库与表（同一张物理表里放两个租户的数据）；
--   2) 控制面里的 iso_a 租户 + 数据源配置（LOGICAL / logic_column=tenant_id）；
--      这一部分由 scripts/demo/provision-iso-fixture.ps1 通过管理端 API 创建。
--
-- 幂等：库/表 IF NOT EXISTS；数据按 id 判重插入，重复执行不会翻倍。
-- 用法：由 provision-iso-fixture.ps1 自动应用；也可手工：
--   mysql ... < sql/demo_fixtures_shared_orders.sql

CREATE DATABASE IF NOT EXISTS lingsql_iso_test
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS lingsql_iso_test.demo_shared_orders (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64)  NOT NULL,
    amount    DECIMAL(12,2) NOT NULL,
    remark    VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    KEY idx_shared_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 两个租户各两行：iso_a 合计 350.00，iso_b 合计 1000.00。
-- 断言"只看到 iso_a"时，350 与 1000 的差异让越权一眼可见。
INSERT INTO lingsql_iso_test.demo_shared_orders (id, tenant_id, amount, remark)
SELECT 1, 'iso_a', 100.00, 'A1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lingsql_iso_test.demo_shared_orders WHERE id = 1);

INSERT INTO lingsql_iso_test.demo_shared_orders (id, tenant_id, amount, remark)
SELECT 2, 'iso_b', 999.00, 'B1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lingsql_iso_test.demo_shared_orders WHERE id = 2);

INSERT INTO lingsql_iso_test.demo_shared_orders (id, tenant_id, amount, remark)
SELECT 3, 'iso_a', 250.00, 'A2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lingsql_iso_test.demo_shared_orders WHERE id = 3);

INSERT INTO lingsql_iso_test.demo_shared_orders (id, tenant_id, amount, remark)
SELECT 4, 'iso_b', 1.00, 'B2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lingsql_iso_test.demo_shared_orders WHERE id = 4);
