-- 创建租户数据库配置表
CREATE TABLE `t_lingsql_engine_tenant_db_config`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`          varchar(64)  NOT NULL COMMENT '租户唯一标识/公司编码',
    `jdbc_url`           varchar(512) NOT NULL COMMENT '数据库JDBC连接地址',
    `username`           varchar(128) NOT NULL COMMENT '数据库账号',
    `password`           varchar(128) NOT NULL COMMENT '数据库密码 (建议加密存储)',
    `driver_class`       varchar(128)          DEFAULT 'com.mysql.cj.jdbc.Driver' COMMENT 'JDBC驱动类',

    -- 隔离模式
    `isolation_mode`     varchar(20)  NOT NULL DEFAULT 'PHYSICAL' COMMENT '隔离模式: PHYSICAL(物理库切换), LOGICAL(单库字段隔离)',
    `logic_column`       varchar(64)           DEFAULT 'tenant_id' COMMENT '逻辑隔离模式下的过滤字段名',

    -- 安全控制
    `white_list_tables`  text COMMENT '核心表白名单 (逗号分隔)，仅允许访问这些表',
    `white_list_columns` text COMMENT '字段白名单 (逗号分隔)，仅允许访问这些字段',
    `sensitive_columns`  text COMMENT '敏感字段黑名单 (逗号分隔)，禁止返回这些字段',

    -- 元数据管理
    `schema_sync_status` tinyint               DEFAULT '0' COMMENT '元数据同步状态: 0-未同步, 1-已同步, 2-待刷新',
    `last_sync_time`     datetime              DEFAULT NULL COMMENT '最近一次元数据扫描时间',

    -- 状态与审计
    `status`             tinyint               DEFAULT '1' COMMENT '连接状态: 1-启用, 0-禁用',
    `create_time`        datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant` (`tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='租户数据源及安全配置表';



CREATE TABLE `t_lingsql_engine_ai_model_config`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT,
    `provider_name`    varchar(64)  NOT NULL COMMENT '供应商名称: 阿里, OpenAI, DeepSeek',
    `model_name`       varchar(64)  NOT NULL COMMENT '模型具体名称: qwen-plus, gpt-4',
    `base_url`         varchar(256) NOT NULL,
    `api_key`          varchar(256) NOT NULL,
    `is_active`        tinyint      DEFAULT '1' COMMENT '是否启用',
    `auto_recover`     tinyint      DEFAULT '1' COMMENT '是否自动恢复',
    `cooldown_seconds` int          DEFAULT '600' COMMENT '熔断冷却秒数',
    `fuse_time`        datetime     DEFAULT NULL COMMENT '熔断时间',
    `fuse_reason`      varchar(255) DEFAULT NULL COMMENT '熔断原因',
    `weight`           int          DEFAULT '10' COMMENT '负载均衡权重',
    `priority`         int          DEFAULT '1' COMMENT '优先级',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT ='AI模型资源池';

-- 语义字典表
CREATE TABLE `t_lingsql_engine_semantic_lexicon`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`   varchar(64)  DEFAULT NULL COMMENT '租户ID，NULL 表示全局',
    `term`        varchar(128) NOT NULL COMMENT '业务术语',
    `mapping`     varchar(512) NOT NULL COMMENT '字段/指标映射表达式',
    `description` varchar(255) DEFAULT NULL COMMENT '说明',
    `status`      tinyint      DEFAULT '1' COMMENT '状态: 1-启用, 0-禁用',
    `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_lexicon_tenant` (`tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='语义术语字典';

-- Join 规则表
CREATE TABLE `t_lingsql_engine_join_rule`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`   varchar(64)  DEFAULT NULL COMMENT '租户ID，NULL 表示全局',
    `left_table`  varchar(128) NOT NULL COMMENT '左表',
    `right_table` varchar(128) NOT NULL COMMENT '右表',
    `on_expr`     varchar(512) NOT NULL COMMENT 'Join 条件表达式',
    `description` varchar(255) DEFAULT NULL COMMENT '说明',
    `status`      tinyint      DEFAULT '1' COMMENT '状态: 1-启用, 0-禁用',
    `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_join_tenant` (`tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='Join 规则配置';

-- 添加熔断字段
ALTER TABLE t_lingsql_engine_ai_model_config
    ADD COLUMN auto_recover     tinyint      DEFAULT 1 COMMENT '是否自动恢复',
    ADD COLUMN cooldown_seconds int          DEFAULT 600 COMMENT '熔断冷却秒数',
    ADD COLUMN fuse_time        datetime     DEFAULT NULL COMMENT '熔断时间',
    ADD COLUMN fuse_reason      varchar(255) DEFAULT NULL COMMENT '熔断原因';