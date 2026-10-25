-- 演示租户 demo_sales 的语义字典种子。
-- 作用：让模型知道「销售额 / 毛利 / 安全库存」这些业务词对应到哪张表、哪个字段或表达式，
-- 而不是靠猜列名。这份数据同时是「语义层可配置」这个卖点在演示环境里的唯一证据。
--
-- 使用：mysql --database=lingsql_demo_control < sql/demo_semantic_lexicon.sql
--       然后 POST /api/v1/admin/refresh-semantic/demo_sales 清理缓存（否则最多等缓存过期才生效）。
--
-- mapping 会被拼成 SELECT <mapping> FROM dual 做语法校验，因此必须是**表达式**，
-- 不能写完整 SELECT，也不要写 WHERE 之类的谓词。

DELETE FROM t_lingsql_engine_semantic_lexicon WHERE tenant_id = 'demo_sales';

INSERT INTO t_lingsql_engine_semantic_lexicon (tenant_id, term, mapping, description, status) VALUES
('demo_sales', '销售额',   'SUM(demo_sales_order_item.quantity * demo_sales_order_item.sale_price)', '已完成订单的成交金额合计，不含退款', 1),
('demo_sales', '订单数',   'COUNT(DISTINCT demo_sales_order.order_id)',                            '去重后的订单笔数', 1),
('demo_sales', '销售数量', 'demo_sales_order_item.quantity',                                       '订单明细中的件数', 1),
('demo_sales', '成交价',   'demo_sales_order_item.sale_price',                                     '订单明细中的实际成交单价', 1),
('demo_sales', '毛利',     'SUM(demo_sales_order_item.quantity * (demo_sales_order_item.sale_price - demo_product.unit_cost))', '成交价减去成本价后的毛利额', 1),
('demo_sales', '毛利率',   '(demo_product.unit_price - demo_product.unit_cost) / demo_product.unit_price', '按商品单价计算的毛利率，小数形式', 1),
('demo_sales', '成本价',   'demo_product.unit_cost',                                               '商品成本单价', 1),
('demo_sales', '单价',     'demo_product.unit_price',                                              '商品标准售价', 1),
('demo_sales', '商品',     'demo_product.product_name',                                            '商品名称', 1),
('demo_sales', '品类',     'demo_product.category_name',                                           '商品所属品类，如饮料、日化', 1),
('demo_sales', '客户',     'demo_customer.customer_name',                                          '客户名称', 1),
('demo_sales', '客户等级', 'demo_customer.customer_level',                                         'A/B/C 三级客户分层', 1),
('demo_sales', '地区',     'demo_customer.region_name',                                            '客户所属大区，如华东、华南', 1),
('demo_sales', '销售渠道', 'demo_sales_order.sales_channel',                                       '订单来源渠道，取值 线上/门店/批发', 1),
('demo_sales', '订单号',   'demo_sales_order.order_no',                                            '订单业务编号', 1),
('demo_sales', '下单日期', 'demo_sales_order.order_date',                                          '订单日期，按天', 1),
('demo_sales', '订单状态', 'demo_sales_order.order_status',                                        'COMPLETED 为已完成，CANCELLED 为已取消', 1),
('demo_sales', '可用库存', 'demo_inventory_snapshot.available_quantity',                           '当前仓库可用数量', 1),
('demo_sales', '安全库存', 'demo_inventory_snapshot.safety_quantity',                              '低于该值即视为缺货风险', 1),
('demo_sales', '仓库',     'demo_inventory_snapshot.warehouse_name',                               '库存所在仓库', 1),
('demo_sales', '快照日期', 'demo_inventory_snapshot.snapshot_date',                                '库存快照的日期，按天', 1);
