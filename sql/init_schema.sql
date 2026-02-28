-- LingSQL 初始化入口脚本
-- 使用方式：
--   mysql -h <host> -u <user> -p <database> < sql/init_schema.sql
--
-- 说明：
-- 1. 该入口用于“全新安装”场景，只执行当前基线结构。
-- 2. 老版本升级请按补丁顺序执行 sql/patch_*.sql。

SOURCE sql/mysql.sql;

