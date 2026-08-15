-- LingSQL 销售库存付费试点演示数据
-- 仅包含虚构企业、客户、商品与订单，不得替换为真实客户敏感数据。

CREATE TABLE IF NOT EXISTS demo_customer (
  customer_id BIGINT PRIMARY KEY,
  customer_name VARCHAR(100) NOT NULL,
  customer_level VARCHAR(20) NOT NULL,
  region_name VARCHAR(50) NOT NULL
) COMMENT='演示客户';

CREATE TABLE IF NOT EXISTS demo_product (
  product_id BIGINT PRIMARY KEY,
  product_name VARCHAR(100) NOT NULL,
  category_name VARCHAR(50) NOT NULL,
  unit_price DECIMAL(12, 2) NOT NULL,
  unit_cost DECIMAL(12, 2) NOT NULL
) COMMENT='演示商品';

CREATE TABLE IF NOT EXISTS demo_sales_order (
  order_id BIGINT PRIMARY KEY,
  order_no VARCHAR(40) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL,
  order_date DATE NOT NULL,
  order_status VARCHAR(20) NOT NULL,
  sales_channel VARCHAR(30) NOT NULL,
  CONSTRAINT fk_demo_order_customer FOREIGN KEY (customer_id) REFERENCES demo_customer(customer_id)
) COMMENT='演示销售订单';

CREATE TABLE IF NOT EXISTS demo_sales_order_item (
  item_id BIGINT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  sale_price DECIMAL(12, 2) NOT NULL,
  CONSTRAINT fk_demo_item_order FOREIGN KEY (order_id) REFERENCES demo_sales_order(order_id),
  CONSTRAINT fk_demo_item_product FOREIGN KEY (product_id) REFERENCES demo_product(product_id)
) COMMENT='演示销售订单明细';

CREATE TABLE IF NOT EXISTS demo_inventory_snapshot (
  snapshot_date DATE NOT NULL,
  product_id BIGINT NOT NULL,
  warehouse_name VARCHAR(50) NOT NULL,
  available_quantity INT NOT NULL,
  safety_quantity INT NOT NULL,
  PRIMARY KEY (snapshot_date, product_id, warehouse_name),
  CONSTRAINT fk_demo_inventory_product FOREIGN KEY (product_id) REFERENCES demo_product(product_id)
) COMMENT='演示库存快照';

INSERT INTO demo_customer (customer_id, customer_name, customer_level, region_name) VALUES
(1, '启明商贸', 'A', '华东'),
(2, '远航零售', 'B', '华南'),
(3, '星河便利', 'B', '华北'),
(4, '新禾供应链', 'A', '西南')
ON DUPLICATE KEY UPDATE customer_name=VALUES(customer_name), customer_level=VALUES(customer_level), region_name=VALUES(region_name);

INSERT INTO demo_product (product_id, product_name, category_name, unit_price, unit_cost) VALUES
(101, '轻盈苏打水', '饮料', 6.00, 3.20),
(102, '原味燕麦片', '食品', 28.00, 17.00),
(103, '便携保温杯', '日用', 79.00, 45.00),
(104, '坚果礼盒', '食品', 118.00, 72.00)
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name), category_name=VALUES(category_name), unit_price=VALUES(unit_price), unit_cost=VALUES(unit_cost);

INSERT INTO demo_sales_order (order_id, order_no, customer_id, order_date, order_status, sales_channel) VALUES
(1001, 'DEMO-20260801-001', 1, '2026-08-01', 'COMPLETED', '直营网销'),
(1002, 'DEMO-20260803-002', 2, '2026-08-03', 'COMPLETED', '经销'),
(1003, 'DEMO-20260806-003', 3, '2026-08-06', 'COMPLETED', '直营网销'),
(1004, 'DEMO-20260809-004', 1, '2026-08-09', 'COMPLETED', '经销'),
(1005, 'DEMO-20260812-005', 4, '2026-08-12', 'COMPLETED', '经销'),
(1006, 'DEMO-20260814-006', 2, '2026-08-14', 'CANCELLED', '直营网销')
ON DUPLICATE KEY UPDATE customer_id=VALUES(customer_id), order_date=VALUES(order_date), order_status=VALUES(order_status), sales_channel=VALUES(sales_channel);

INSERT INTO demo_sales_order_item (item_id, order_id, product_id, quantity, sale_price) VALUES
(1, 1001, 101, 120, 5.80),
(2, 1001, 102, 30, 27.00),
(3, 1002, 103, 18, 75.00),
(4, 1002, 104, 12, 112.00),
(5, 1003, 101, 80, 6.00),
(6, 1003, 104, 8, 118.00),
(7, 1004, 102, 45, 26.50),
(8, 1004, 103, 10, 78.00),
(9, 1005, 104, 20, 110.00),
(10, 1005, 101, 200, 5.60),
(11, 1006, 103, 6, 79.00)
ON DUPLICATE KEY UPDATE order_id=VALUES(order_id), product_id=VALUES(product_id), quantity=VALUES(quantity), sale_price=VALUES(sale_price);

INSERT INTO demo_inventory_snapshot (snapshot_date, product_id, warehouse_name, available_quantity, safety_quantity) VALUES
('2026-08-15', 101, '上海一仓', 420, 300),
('2026-08-15', 102, '上海一仓', 75, 100),
('2026-08-15', 103, '广州一仓', 32, 40),
('2026-08-15', 104, '广州一仓', 160, 80)
ON DUPLICATE KEY UPDATE available_quantity=VALUES(available_quantity), safety_quantity=VALUES(safety_quantity);
