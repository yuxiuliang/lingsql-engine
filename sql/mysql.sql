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

-- 租户信息表
CREATE TABLE `t_lingsql_engine_tenant`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`     varchar(64)  NOT NULL COMMENT '租户唯一标识',
    `tenant_name`   varchar(128) NOT NULL COMMENT '租户名称',
    `status`        tinyint      DEFAULT '1' COMMENT '状态: 1-启用, 0-停用',
    `remark`        varchar(255) DEFAULT NULL COMMENT '备注',
    `contact_name`  varchar(64)  DEFAULT NULL COMMENT '联系人',
    `contact_phone` varchar(32)  DEFAULT NULL COMMENT '联系电话',
    `contact_email` varchar(128) DEFAULT NULL COMMENT '联系邮箱',
    `expire_time`   datetime     DEFAULT NULL COMMENT '到期时间',
    `plan_name`     varchar(128) DEFAULT NULL COMMENT '套餐名称',
    `is_deleted`    tinyint      DEFAULT '0' COMMENT '删除标记: 1-删除, 0-正常',
    `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_info` (`tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='租户信息表';



CREATE TABLE `t_lingsql_engine_ai_model_config`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT,
    `provider_name`    varchar(64)  NOT NULL COMMENT '供应商名称: 阿里, OpenAI, DeepSeek',
    `model_name`       varchar(64)  NOT NULL COMMENT '模型具体名称: qwen-plus, gpt-4',
    `model_version`    varchar(64)  DEFAULT NULL COMMENT '模型版本',
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

-- 用户表
CREATE TABLE `t_lingsql_engine_user`
(
    `id`              bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`       varchar(64) NOT NULL COMMENT '租户ID',
    `username`        varchar(64) NOT NULL COMMENT '账号',
    `password`        varchar(128) NOT NULL COMMENT '密码(BCrypt)',
    `nickname`        varchar(64)          DEFAULT NULL COMMENT '昵称',
    `status`          tinyint     DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    `is_experience`   tinyint     DEFAULT 0 COMMENT '是否体验账号: 1-是, 0-否',
    `last_login_time` datetime    DEFAULT NULL COMMENT '最近登录时间',
    `failed_login_count` int      DEFAULT 0 COMMENT '登录失败次数',
    `lock_until`      datetime    DEFAULT NULL COMMENT '锁定截止时间',
    `create_time`     datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tenant` (`tenant_id`, `username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户表';

-- 角色表
CREATE TABLE `t_lingsql_engine_role`
(
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`   varchar(64) DEFAULT NULL COMMENT '租户ID，NULL 表示全局',
    `tenant_key`  varchar(64) GENERATED ALWAYS AS (IFNULL(`tenant_id`, 'GLOBAL')) STORED COMMENT '租户唯一键(兼容NULL)',
    `role_code`   varchar(64) NOT NULL COMMENT '角色编码',
    `role_name`   varchar(64) NOT NULL COMMENT '角色名称',
    `status`      tinyint     DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    `create_time` datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_tenant` (`tenant_id`, `role_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='角色表';

-- 用户角色关系表
CREATE TABLE `t_lingsql_engine_user_role`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     bigint   NOT NULL COMMENT '用户ID',
    `role_id`     bigint   NOT NULL COMMENT '角色ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户角色关系表';

-- 权限表
CREATE TABLE `t_lingsql_engine_permission`
(
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `perm_code`   varchar(128) NOT NULL COMMENT '权限编码',
    `perm_name`   varchar(128) NOT NULL COMMENT '权限名称',
    `perm_type`   varchar(32)  NOT NULL COMMENT '权限类型: MENU/API/BUTTON/DATA',
    `status`      tinyint     DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    `create_time` datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='权限表';

-- 角色权限关系表
CREATE TABLE `t_lingsql_engine_role_permission`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`     bigint   NOT NULL COMMENT '角色ID',
    `perm_id`     bigint   NOT NULL COMMENT '权限ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `perm_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='角色权限关系表';

-- 租户权限池表
CREATE TABLE `t_lingsql_engine_tenant_permission`
(
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`   varchar(64) NOT NULL COMMENT '租户ID',
    `perm_id`     bigint      NOT NULL COMMENT '权限ID',
    `create_time` datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_perm` (`tenant_id`, `perm_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='租户权限池表';

-- 菜单表
CREATE TABLE `t_lingsql_engine_menu`
(
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`   varchar(64) DEFAULT NULL COMMENT '租户ID，NULL 表示全局',
    `menu_code`   varchar(64) NOT NULL COMMENT '菜单编码',
    `menu_name`   varchar(128) NOT NULL COMMENT '菜单名称',
    `parent_id`   bigint      DEFAULT NULL COMMENT '父级菜单ID',
    `path`        varchar(256) DEFAULT NULL COMMENT '路由路径',
    `component`   varchar(256) DEFAULT NULL COMMENT '前端组件',
    `icon`        varchar(128) DEFAULT NULL COMMENT '菜单图标',
    `sort`        int         DEFAULT 0 COMMENT '排序',
    `visible`     tinyint     DEFAULT 1 COMMENT '是否可见: 1-可见, 0-隐藏',
    `status`      tinyint     DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    `perm_code`   varchar(128) DEFAULT NULL COMMENT '关联权限编码',
    `create_time` datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_menu_tenant` (`tenant_id`, `menu_code`),
    UNIQUE KEY `uk_menu_tenant_key` (`tenant_key`, `menu_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='菜单表';

-- 角色数据权限表
CREATE TABLE `t_lingsql_engine_role_data_scope`
(
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`      bigint      NOT NULL COMMENT '角色ID',
    `scope_type`   varchar(32) NOT NULL COMMENT '范围类型: ALL/TENANT/SELF/CUSTOM',
    `scope_column` varchar(64) DEFAULT NULL COMMENT 'SELF 默认字段名',
    `scope_rule`   text        DEFAULT NULL COMMENT 'CUSTOM 时的 SQL 条件',
    `status`       tinyint     DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    `create_time`  datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_role_scope` (`role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='角色数据权限表';

-- 用户数据权限覆盖表
CREATE TABLE `t_lingsql_engine_user_data_scope`
(
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`      bigint      NOT NULL COMMENT '用户ID',
    `scope_type`   varchar(32) NOT NULL COMMENT '范围类型: ALL/TENANT/SELF/CUSTOM',
    `scope_column` varchar(64) DEFAULT NULL COMMENT 'SELF 默认字段名',
    `scope_rule`   text        DEFAULT NULL COMMENT 'CUSTOM 时的 SQL 条件',
    `status`       tinyint     DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    `create_time`  datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_scope` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户数据权限覆盖表';

-- 体验账号策略表
CREATE TABLE `t_lingsql_engine_user_experience_policy`
(
    `id`                 bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`            bigint   NOT NULL COMMENT '用户ID',
    `menu_whitelist`     text     DEFAULT NULL COMMENT '菜单白名单(JSON)',
    `data_scope_override` text    DEFAULT NULL COMMENT '数据权限覆盖(JSON)',
    `status`             tinyint  DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    `create_time`        datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_experience_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='体验账号策略表';

-- 管理操作审计日志
CREATE TABLE `t_lingsql_engine_audit_log`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`    varchar(64)  DEFAULT NULL COMMENT '租户ID',
    `user_id`      bigint       DEFAULT NULL COMMENT '用户ID',
    `real_user_id` bigint       DEFAULT NULL COMMENT '真实操作人ID',
    `username`     varchar(64)  DEFAULT NULL COMMENT '账号',
    `action`       varchar(128) DEFAULT NULL COMMENT '操作动作',
    `resource`     varchar(128) DEFAULT NULL COMMENT '操作对象',
    `detail`       text         DEFAULT NULL COMMENT '操作详情(JSON)',
    `summary`      varchar(255) DEFAULT NULL COMMENT '摘要',
    `success`      tinyint      DEFAULT 1 COMMENT '是否成功',
    `error_message` text        DEFAULT NULL COMMENT '错误信息',
    `request_ip`   varchar(64)  DEFAULT NULL COMMENT '请求IP',
    `user_agent`   varchar(512) DEFAULT NULL COMMENT 'User-Agent',
    `create_time`  datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_tenant` (`tenant_id`),
    KEY `idx_audit_user` (`user_id`),
    KEY `idx_audit_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='管理操作审计日志';

-- SQL 执行日志
CREATE TABLE `t_lingsql_engine_sql_execute_log`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`      varchar(64)  NOT NULL COMMENT '租户ID',
    `user_id`        bigint       DEFAULT NULL COMMENT '执行人ID',
    `question`       text         DEFAULT NULL COMMENT '原始问题',
    `ai_sql`         text         DEFAULT NULL COMMENT 'AI生成SQL',
    `executed_sql`   text         DEFAULT NULL COMMENT '执行SQL',
    `success`        tinyint      DEFAULT 1 COMMENT '是否成功',
    `error_message`  text         DEFAULT NULL COMMENT '错误信息',
    `model_provider` varchar(64)  DEFAULT NULL COMMENT '模型供应商',
    `model_name`     varchar(64)  DEFAULT NULL COMMENT '模型名称',
    `model_version`  varchar(64)  DEFAULT NULL COMMENT '模型版本',
    `token_count`    int          DEFAULT NULL COMMENT 'Token 数量',
    `execute_cost_ms` bigint      DEFAULT NULL COMMENT '执行耗时(毫秒)',
    `result_rows`    int          DEFAULT NULL COMMENT '返回行数',
    `rating`         tinyint      DEFAULT NULL COMMENT '评分',
    `comment`        text         DEFAULT NULL COMMENT '评价内容',
    `request_source` varchar(32)  DEFAULT NULL COMMENT '请求来源',
    `create_time`    datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_sql_tenant` (`tenant_id`),
    KEY `idx_sql_user` (`user_id`),
    KEY `idx_sql_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='SQL 执行日志';
