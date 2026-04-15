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
    `notification_channels` varchar(128) DEFAULT NULL COMMENT '默认通知通道列表，逗号分隔',
    `expire_time`   datetime     DEFAULT NULL COMMENT '到期时间',
    `plan_name`     varchar(128) DEFAULT NULL COMMENT '套餐名称',
    `quota_balance` bigint       DEFAULT 0 COMMENT '余额',
    `service_level` varchar(32)  DEFAULT NULL COMMENT '服务等级',
    `tenant_type`   varchar(32)  DEFAULT NULL COMMENT '租户类型',
    `is_deleted`    tinyint      DEFAULT '0' COMMENT '删除标记: 1-删除, 0-正常',
    `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_info` (`tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='租户信息表';

-- 计费策略版本表
CREATE TABLE `t_lingsql_engine_billing_policy_version`
(
    `id`                      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `version_code`            varchar(64)  NOT NULL COMMENT '版本号',
    `status`                  tinyint      DEFAULT '1' COMMENT '状态: 1-生效, 0-停用',
    `effective_time`          datetime     DEFAULT NULL COMMENT '生效时间',
    `model_unit_cost`         text         DEFAULT NULL COMMENT '模型单价配置(JSON)',
    `service_level_limit`     text         DEFAULT NULL COMMENT '服务等级限额(JSON)',
    `tenant_type_weight`      text         DEFAULT NULL COMMENT '租户类型权重(JSON)',
    `default_unit_cost`       bigint       DEFAULT 1 COMMENT '默认单价(点/Token)',
    `default_daily_limit`     int          DEFAULT 100 COMMENT '默认每日调用上限',
    `default_daily_token_limit` bigint     DEFAULT 0 COMMENT '默认每日Token上限',
    `default_tenant_weight`   decimal(10,4) DEFAULT 1.0000 COMMENT '默认租户权重',
    `description`             varchar(255) DEFAULT NULL COMMENT '说明',
    `create_time`             datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`             datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_version` (`version_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='计费策略版本表';

-- 租户计费覆盖表
CREATE TABLE `t_lingsql_engine_billing_tenant_override`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`        varchar(64)  NOT NULL COMMENT '租户ID',
    `price_multiplier` decimal(10,4) DEFAULT NULL COMMENT '加价倍率',
    `status`           tinyint      DEFAULT '1' COMMENT '状态: 1-生效, 0-停用',
    `remark`           varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time`      datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_override` (`tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='租户计费覆盖表';

-- 计费账本事件表
CREATE TABLE `t_lingsql_engine_billing_ledger`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `request_id`     varchar(64)  NOT NULL COMMENT '请求ID',
    `event_seq`      int          NOT NULL COMMENT '事件序号',
    `tenant_id`      varchar(64)  NOT NULL COMMENT '租户ID',
    `model_name`     varchar(64)  DEFAULT NULL COMMENT '模型名称',
    `token_count`    int          DEFAULT NULL COMMENT 'Token 数量',
    `unit_cost`      bigint       DEFAULT NULL COMMENT '单价(点/Token)',
    `cost_amount`    bigint       DEFAULT NULL COMMENT '成本金额',
    `billing_version` varchar(64) DEFAULT NULL COMMENT '计费版本',
    `event_type`     tinyint      DEFAULT '0' COMMENT '事件类型: 0-预扣, 1-退款, 2-CLOSE(对账)',
    `status`         tinyint      DEFAULT '0' COMMENT '状态: 0-待结算, 1-已结算, 2-失败',
    `create_time`    datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `apply_time`     datetime     DEFAULT NULL COMMENT '结算时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_ledger_request_seq_model` (`request_id`, `event_seq`, `model_name`),
    KEY `idx_billing_ledger_tenant` (`tenant_id`),
    KEY `idx_billing_ledger_status` (`status`),
    KEY `idx_billing_ledger_create` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='计费账本事件表';

-- 计费对账日志表
CREATE TABLE `t_lingsql_engine_billing_reconcile_log`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`    varchar(64)  DEFAULT NULL COMMENT '租户ID',
    `request_id`   varchar(64)  NOT NULL COMMENT '请求ID',
    `reserve_total` bigint      DEFAULT NULL COMMENT '预扣总额',
    `close_total`  bigint       DEFAULT NULL COMMENT 'CLOSE 总额',
    `diff_amount`  bigint       DEFAULT NULL COMMENT '差异金额',
    `status`       tinyint      DEFAULT '0' COMMENT '状态: 0-未处理, 1-已处理',
    `resolve_note` varchar(255) DEFAULT NULL COMMENT '处理备注',
    `resolve_user_id` bigint    DEFAULT NULL COMMENT '处理人ID',
    `resolve_time` datetime     DEFAULT NULL COMMENT '处理时间',
    `latest_time`  datetime     DEFAULT NULL COMMENT '最新账本时间',
    `create_time`  datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_billing_reconcile_request` (`request_id`),
    KEY `idx_billing_reconcile_tenant` (`tenant_id`),
    KEY `idx_billing_reconcile_create` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='计费对账日志表';

-- 套餐定义表
CREATE TABLE `t_lingsql_engine_billing_plan`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `plan_code`             varchar(64)  NOT NULL COMMENT '套餐编码',
    `plan_name`             varchar(128) NOT NULL COMMENT '套餐名称',
    `status`                tinyint      DEFAULT '1' COMMENT '状态: 1-启用, 0-停用',
    `service_level`         varchar(32)  DEFAULT NULL COMMENT '服务等级',
    `tenant_type`           varchar(32)  DEFAULT NULL COMMENT '租户类型',
    `default_quota_balance` bigint       NOT NULL DEFAULT 0 COMMENT '默认余额',
    `remark`                varchar(255) DEFAULT NULL COMMENT '备注',
    `is_deleted`            tinyint      NOT NULL DEFAULT '0' COMMENT '删除标记: 1-删除, 0-正常',
    `create_time`           datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_plan_code` (`plan_code`),
    KEY `idx_billing_plan_status` (`status`),
    KEY `idx_billing_plan_deleted` (`is_deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='套餐定义表';

-- 数字员工定义表
CREATE TABLE `t_lingsql_engine_digital_employee`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`        varchar(64)  NOT NULL COMMENT '目标租户ID',
    `employee_code`    varchar(64)  NOT NULL COMMENT '员工编码',
    `employee_name`    varchar(128) NOT NULL COMMENT '员工名称',
    `role_name`        varchar(128) DEFAULT NULL COMMENT '岗位角色',
    `scenario`         varchar(255) DEFAULT NULL COMMENT '业务场景',
    `prompt_template`  text COMMENT '提示词模板',
    `default_question` text COMMENT '默认执行问题',
    `schedule_enabled` tinyint      NOT NULL DEFAULT '0' COMMENT '是否启用定时执行: 1-启用, 0-停用',
    `schedule_cron`    varchar(64)  DEFAULT NULL COMMENT '定时执行 Cron',
    `alert_enabled`    tinyint      NOT NULL DEFAULT '0' COMMENT '是否启用告警: 1-启用, 0-停用',
    `alert_rule_type`  varchar(32)  DEFAULT NULL COMMENT '告警规则类型',
    `alert_threshold`  int          DEFAULT NULL COMMENT '结果行数阈值',
    `push_enabled`     tinyint      NOT NULL DEFAULT '0' COMMENT '是否启用结果推送: 1-启用, 0-停用',
    `push_channels`    varchar(128) DEFAULT NULL COMMENT '结果推送通道列表，逗号分隔',
    `push_max_rows`    int          DEFAULT NULL COMMENT '结果推送预览最大行数',
    `push_title_template` varchar(255) DEFAULT NULL COMMENT '结果推送标题模板',
    `push_message_template` text COMMENT '结果推送摘要模板',
    `status`           tinyint      DEFAULT '1' COMMENT '状态: 1-启用, 0-停用',
    `run_count`        bigint       NOT NULL DEFAULT 0 COMMENT '累计运行次数',
    `success_count`    bigint       NOT NULL DEFAULT 0 COMMENT '累计成功次数',
    `last_run_status`  tinyint      DEFAULT NULL COMMENT '最近运行状态: 1-成功, 0-失败',
    `last_run_time`    datetime     DEFAULT NULL COMMENT '最近运行时间',
    `last_schedule_time` datetime   DEFAULT NULL COMMENT '最近一次计划触发时间',
    `last_error_message` text COMMENT '最近错误信息',
    `remark`           varchar(255) DEFAULT NULL COMMENT '备注',
    `is_deleted`       tinyint      NOT NULL DEFAULT '0' COMMENT '删除标记: 1-删除, 0-正常',
    `create_time`      datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_digital_employee_tenant_code` (`tenant_id`, `employee_code`),
    KEY `idx_digital_employee_tenant_status` (`tenant_id`, `status`),
    KEY `idx_digital_employee_update` (`update_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='数字员工定义表';

-- 数字员工运行日志表
CREATE TABLE `t_lingsql_engine_digital_employee_run_log`
(
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `employee_id`  bigint      NOT NULL COMMENT '数字员工ID',
    `tenant_id`    varchar(64) NOT NULL COMMENT '租户ID',
    `trigger_source` varchar(32) DEFAULT 'MANUAL' COMMENT '触发来源',
    `question`     text COMMENT '执行问题',
    `answer`       text COMMENT '执行答复',
    `executed_sql` text COMMENT '执行SQL',
    `success`      tinyint     DEFAULT '1' COMMENT '是否成功: 1-成功, 0-失败',
    `error_message` text COMMENT '错误信息',
    `result_rows`  int         DEFAULT NULL COMMENT '结果行数',
    `request_id`   varchar(64) DEFAULT NULL COMMENT '请求ID',
    `duration_ms`  bigint      DEFAULT NULL COMMENT '执行耗时(毫秒)',
    `alert_delivery_status` tinyint DEFAULT NULL COMMENT '告警投递状态: 1-成功, 0-失败, NULL-未触发',
    `alert_delivery_detail` text COMMENT '告警投递诊断明细',
    `push_delivery_status`  tinyint DEFAULT NULL COMMENT '结果推送状态: 1-成功, 0-失败, NULL-未触发',
    `push_delivery_detail`  text COMMENT '结果推送诊断明细',
    `create_time`  datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_de_run_employee_time` (`employee_id`, `create_time`),
    KEY `idx_de_run_tenant_time` (`tenant_id`, `create_time`),
    KEY `idx_de_run_request` (`request_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='数字员工运行日志表';

-- 聊天历史记录表
CREATE TABLE `t_lingsql_engine_chat_history`
(
    `id`                 bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`          varchar(64) NOT NULL COMMENT '租户ID',
    `user_id`            bigint      NOT NULL COMMENT '用户ID',
    `conversation_id`    varchar(64) DEFAULT NULL COMMENT '会话ID',
    `conversation_title` text COMMENT '会话标题',
    `question`           text COMMENT '问题',
    `answer`             text COMMENT '回答',
    `sql_text`           text COMMENT 'SQL 文本',
    `data_json`          text COMMENT '结果数据(JSON)',
    `status`             tinyint     DEFAULT '1' COMMENT '状态: 1-正常, 0-异常',
    `create_time`        datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chat_history_tenant_user_time` (`tenant_id`, `user_id`, `create_time`),
    KEY `idx_chat_history_conversation` (`tenant_id`, `user_id`, `conversation_id`),
    KEY `idx_chat_history_create` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='聊天历史记录表';

-- 元数据扩展字典表
CREATE TABLE `t_lingsql_engine_metadata_ext`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`         varchar(64)  NOT NULL COMMENT '租户ID',
    `schema_name`       varchar(128) DEFAULT NULL COMMENT '数据库名称',
    `table_name`        varchar(128) NOT NULL COMMENT '表名',
    `column_name`       varchar(128) NOT NULL COMMENT '字段名',
    `business_comment`  text COMMENT '业务注释',
    `is_isolated_field` tinyint      DEFAULT '0' COMMENT '是否隔离字段: 1-是, 0-否',
    `create_time`       datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_metadata_ext_tenant_table_column` (`tenant_id`, `table_name`, `column_name`),
    KEY `idx_metadata_ext_tenant_table` (`tenant_id`, `table_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='元数据扩展字典表';



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

-- 组织单元类型表
CREATE TABLE `t_lingsql_engine_org_unit_type`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`   varchar(64)  NOT NULL COMMENT '租户ID',
    `type_code`   varchar(64)  NOT NULL COMMENT '组织类型编码',
    `type_name`   varchar(128) NOT NULL COMMENT '组织类型名称',
    `sort`        int          DEFAULT 0 COMMENT '排序值',
    `status`      tinyint      DEFAULT 1 COMMENT '状态: 1-启用, 0-停用',
    `remark`      varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_org_unit_type_tenant_code` (`tenant_id`, `type_code`),
    KEY `idx_org_unit_type_tenant_sort` (`tenant_id`, `sort`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='组织单元类型表';

-- 组织单元表
CREATE TABLE `t_lingsql_engine_org_unit`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`   varchar(64)  NOT NULL COMMENT '租户ID',
    `unit_code`   varchar(64)  NOT NULL COMMENT '组织节点编码',
    `unit_name`   varchar(128) NOT NULL COMMENT '组织节点名称',
    `type_code`   varchar(64)  NOT NULL COMMENT '组织类型编码',
    `leader_user_id` bigint    DEFAULT NULL COMMENT '负责人用户ID',
    `parent_id`   bigint       DEFAULT NULL COMMENT '父节点ID',
    `tree_path`   varchar(512) DEFAULT NULL COMMENT '树路径',
    `tree_level`  int          DEFAULT 1 COMMENT '树层级',
    `sort`        int          DEFAULT 0 COMMENT '排序值',
    `status`      tinyint      DEFAULT 1 COMMENT '状态: 1-启用, 0-停用',
    `remark`      varchar(255) DEFAULT NULL COMMENT '备注',
    `tag_text`    text COMMENT '标签文本，逗号分隔',
    `attribute_json` text COMMENT '扩展属性 JSON',
    `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_org_unit_tenant_code` (`tenant_id`, `unit_code`),
    KEY `idx_org_unit_tenant_parent` (`tenant_id`, `parent_id`),
    KEY `idx_org_unit_tenant_type` (`tenant_id`, `type_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='组织单元表';

-- 组织关系表
CREATE TABLE `t_lingsql_engine_org_unit_relation`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`          varchar(64)  NOT NULL COMMENT '租户ID',
    `source_org_unit_id` bigint       NOT NULL COMMENT '源组织节点ID',
    `target_org_unit_id` bigint       NOT NULL COMMENT '目标组织节点ID',
    `relation_type`      varchar(64)  NOT NULL COMMENT '关系类型编码',
    `relation_name`      varchar(128) DEFAULT NULL COMMENT '关系名称',
    `sort`               int          DEFAULT 0 COMMENT '排序值',
    `status`             tinyint      DEFAULT 1 COMMENT '状态: 1-启用, 0-停用',
    `remark`             varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time`        datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_org_relation_unique` (`tenant_id`, `source_org_unit_id`, `target_org_unit_id`, `relation_type`),
    KEY `idx_org_relation_source` (`tenant_id`, `source_org_unit_id`),
    KEY `idx_org_relation_target` (`tenant_id`, `target_org_unit_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='组织关系表';

-- 用户组织归属表
CREATE TABLE `t_lingsql_engine_user_org`
(
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`   varchar(64) NOT NULL COMMENT '租户ID',
    `user_id`     bigint      NOT NULL COMMENT '用户ID',
    `org_unit_id` bigint      NOT NULL COMMENT '组织节点ID',
    `is_primary`  tinyint     DEFAULT 0 COMMENT '是否主归属: 1-是, 0-否',
    `create_time` datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_org_membership` (`user_id`, `org_unit_id`),
    KEY `idx_user_org_tenant` (`tenant_id`),
    KEY `idx_user_org_org_unit` (`org_unit_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户组织归属表';

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
    `tenant_key`  varchar(64) GENERATED ALWAYS AS (IFNULL(`tenant_id`, 'GLOBAL')) STORED COMMENT '租户唯一键(兼容NULL)',
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

-- 组织关系执行台账
CREATE TABLE `t_lingsql_engine_org_relation_execution`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`            varchar(64)  NOT NULL COMMENT '租户ID',
    `relation_id`          bigint       NOT NULL COMMENT '组织关系ID',
    `relation_type`        varchar(64)  NOT NULL COMMENT '关系类型',
    `relation_name`        varchar(128) DEFAULT NULL COMMENT '关系名称',
    `scene_code`           varchar(64)  DEFAULT NULL COMMENT '场景编码',
    `scene_name`           varchar(128) DEFAULT NULL COMMENT '场景名称',
    `execution_title`      varchar(255) NOT NULL COMMENT '执行标题',
    `execution_content`    text         DEFAULT NULL COMMENT '执行内容',
    `priority`             varchar(16)  NOT NULL COMMENT '优先级',
    `sla_hours`            int          DEFAULT NULL COMMENT 'SLA时长(小时)',
    `due_time`             datetime     DEFAULT NULL COMMENT '截止时间',
    `escalation_status`    varchar(32)  DEFAULT NULL COMMENT '升级状态',
    `escalation_level`     int          DEFAULT NULL COMMENT '升级级别',
    `last_escalated_time`  datetime     DEFAULT NULL COMMENT '最近升级时间',
    `status`               varchar(32)  NOT NULL COMMENT '状态',
    `source_org_unit_id`   bigint       NOT NULL COMMENT '源组织ID',
    `source_org_unit_code` varchar(128) DEFAULT NULL COMMENT '源组织编码',
    `source_org_unit_name` varchar(255) DEFAULT NULL COMMENT '源组织名称',
    `target_org_unit_id`   bigint       NOT NULL COMMENT '目标组织ID',
    `target_org_unit_code` varchar(128) DEFAULT NULL COMMENT '目标组织编码',
    `target_org_unit_name` varchar(255) DEFAULT NULL COMMENT '目标组织名称',
    `route_scope_summary`  varchar(512) DEFAULT NULL COMMENT '路由范围说明',
    `scope_unit_count`     int          DEFAULT NULL COMMENT '路由范围节点数',
    `scope_unit_codes_json` text        DEFAULT NULL COMMENT '路由范围编码快照',
    `requested_by_user_id` bigint       NOT NULL COMMENT '创建人ID',
    `requested_by_name`    varchar(128) DEFAULT NULL COMMENT '创建人名称',
    `assignee_user_id`     bigint       DEFAULT NULL COMMENT '处理人ID',
    `assignee_name`        varchar(128) DEFAULT NULL COMMENT '处理人名称',
    `result_summary`       text         DEFAULT NULL COMMENT '处理结果',
    `started_time`         datetime     DEFAULT NULL COMMENT '开始处理时间',
    `completed_time`       datetime     DEFAULT NULL COMMENT '完成时间',
    `create_time`          datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_org_relation_execution_tenant_create` (`tenant_id`, `create_time`, `id`),
    KEY `idx_org_relation_execution_tenant_status` (`tenant_id`, `status`),
    KEY `idx_org_relation_execution_tenant_relation_type` (`tenant_id`, `relation_type`),
    KEY `idx_org_relation_execution_relation_id` (`relation_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='组织关系执行台账';

-- 组织关系执行轨迹
CREATE TABLE `t_lingsql_engine_org_relation_execution_timeline`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`             varchar(64)  NOT NULL COMMENT '租户ID',
    `execution_id`          bigint       NOT NULL COMMENT '执行项ID',
    `event_type`            varchar(32)  NOT NULL COMMENT '事件类型',
    `event_summary`         varchar(255) NOT NULL COMMENT '事件摘要',
    `event_detail`          text         DEFAULT NULL COMMENT '事件详情',
    `actor_user_id`         bigint       DEFAULT NULL COMMENT '操作人ID',
    `actor_name`            varchar(128) DEFAULT NULL COMMENT '操作人名称',
    `before_status`         varchar(32)  DEFAULT NULL COMMENT '变更前状态',
    `after_status`          varchar(32)  DEFAULT NULL COMMENT '变更后状态',
    `before_assignee_name`  varchar(128) DEFAULT NULL COMMENT '变更前处理人',
    `after_assignee_name`   varchar(128) DEFAULT NULL COMMENT '变更后处理人',
    `before_priority`       varchar(16)  DEFAULT NULL COMMENT '变更前优先级',
    `after_priority`        varchar(16)  DEFAULT NULL COMMENT '变更后优先级',
    `before_sla_hours`      int          DEFAULT NULL COMMENT '变更前SLA时长',
    `after_sla_hours`       int          DEFAULT NULL COMMENT '变更后SLA时长',
    `before_due_time`       datetime     DEFAULT NULL COMMENT '变更前截止时间',
    `after_due_time`        datetime     DEFAULT NULL COMMENT '变更后截止时间',
    `before_result_summary` text         DEFAULT NULL COMMENT '变更前结果摘要',
    `after_result_summary`  text         DEFAULT NULL COMMENT '变更后结果摘要',
    `create_time`           datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_org_relation_execution_timeline_tenant_execution` (`tenant_id`, `execution_id`, `create_time`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='组织关系执行轨迹';

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

-- 菜单治理发布单
CREATE TABLE `t_lingsql_engine_menu_release_record`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`             varchar(64)  NOT NULL COMMENT '租户ID',
    `release_type`          varchar(32)  NOT NULL COMMENT '发布类型：GLOBAL_RULE / TENANT_RULE',
    `release_title`         varchar(255) NOT NULL COMMENT '发布单标题',
    `menu_id`               bigint       DEFAULT NULL COMMENT '全局菜单ID',
    `menu_code`             varchar(128) NOT NULL COMMENT '菜单编码',
    `menu_name`             varchar(255) NOT NULL COMMENT '菜单名称',
    `baseline_rule_summary` text         DEFAULT NULL COMMENT '基线规则摘要',
    `target_rule_summary`   text         DEFAULT NULL COMMENT '目标规则摘要',
    `impact_summary`        text         DEFAULT NULL COMMENT '影响摘要',
    `changed_dimensions_json` text       DEFAULT NULL COMMENT '变更维度快照(JSON)',
    `added_menu_count`      int          DEFAULT 0 COMMENT '新增命中菜单数',
    `removed_menu_count`    int          DEFAULT 0 COMMENT '移除命中菜单数',
    `impact_request_json`   text         DEFAULT NULL COMMENT '影响分析请求快照(JSON)',
    `impact_snapshot_json`  text         DEFAULT NULL COMMENT '影响分析结果快照(JSON)',
    `change_remark`         text         DEFAULT NULL COMMENT '变更说明',
    `release_status`        varchar(32)  NOT NULL COMMENT '发布状态：DRAFT / APPROVED / REJECTED / APPLIED',
    `requested_by_user_id`  bigint       NOT NULL COMMENT '创建人ID',
    `requested_by_name`     varchar(128) DEFAULT NULL COMMENT '创建人名称',
    `reviewed_by_user_id`   bigint       DEFAULT NULL COMMENT '审核人ID',
    `reviewed_by_name`      varchar(128) DEFAULT NULL COMMENT '审核人名称',
    `review_comment`        text         DEFAULT NULL COMMENT '审核意见',
    `reviewed_time`         datetime     DEFAULT NULL COMMENT '审核时间',
    `applied_by_user_id`    bigint       DEFAULT NULL COMMENT '应用人ID',
    `applied_by_name`       varchar(128) DEFAULT NULL COMMENT '应用人名称',
    `applied_time`          datetime     DEFAULT NULL COMMENT '应用时间',
    `create_time`           datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_menu_release_tenant_status` (`tenant_id`, `release_status`, `create_time`, `id`),
    KEY `idx_menu_release_menu_code` (`menu_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='菜单治理发布单';

-- 角色数据权限发布单
CREATE TABLE `t_lingsql_engine_role_scope_release_record`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`           varchar(64)  NOT NULL COMMENT '租户ID',
    `release_title`       varchar(255) NOT NULL COMMENT '发布单标题',
    `role_id`             bigint       NOT NULL COMMENT '角色ID',
    `role_code`           varchar(128) NOT NULL COMMENT '角色编码',
    `role_name`           varchar(255) NOT NULL COMMENT '角色名称',
    `baseline_summary`    text         DEFAULT NULL COMMENT '基线摘要',
    `target_summary`      text         DEFAULT NULL COMMENT '目标摘要',
    `impact_summary`      text         DEFAULT NULL COMMENT '影响摘要',
    `matched_org_count`   int          DEFAULT 0 COMMENT '命中组织数',
    `affected_user_count` int          DEFAULT 0 COMMENT '受影响用户数',
    `added_org_count`     int          DEFAULT 0 COMMENT '新增组织数',
    `removed_org_count`   int          DEFAULT 0 COMMENT '移除组织数',
    `preview_request_json` text        DEFAULT NULL COMMENT '预览请求快照(JSON)',
    `preview_snapshot_json` text       DEFAULT NULL COMMENT '预览结果快照(JSON)',
    `change_remark`       text         DEFAULT NULL COMMENT '变更说明',
    `release_status`      varchar(32)  NOT NULL COMMENT '发布状态：DRAFT / APPROVED / REJECTED / APPLIED',
    `requested_by_user_id` bigint      NOT NULL COMMENT '创建人ID',
    `requested_by_name`   varchar(128) DEFAULT NULL COMMENT '创建人名称',
    `reviewed_by_user_id` bigint       DEFAULT NULL COMMENT '审核人ID',
    `reviewed_by_name`    varchar(128) DEFAULT NULL COMMENT '审核人名称',
    `review_comment`      text         DEFAULT NULL COMMENT '审核意见',
    `reviewed_time`       datetime     DEFAULT NULL COMMENT '审核时间',
    `applied_by_user_id`  bigint       DEFAULT NULL COMMENT '应用人ID',
    `applied_by_name`     varchar(128) DEFAULT NULL COMMENT '应用人名称',
    `applied_time`        datetime     DEFAULT NULL COMMENT '应用时间',
    `create_time`         datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_role_scope_release_tenant_status` (`tenant_id`, `release_status`, `create_time`, `id`),
    KEY `idx_role_scope_release_role_id` (`role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='角色数据权限发布单';

-- 用户数据权限发布单
CREATE TABLE `t_lingsql_engine_user_scope_release_record`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`           varchar(64)  NOT NULL COMMENT '租户ID',
    `release_title`       varchar(255) NOT NULL COMMENT '发布单标题',
    `user_id`             bigint       NOT NULL COMMENT '用户ID',
    `username`            varchar(128) NOT NULL COMMENT '用户账号',
    `nickname`            varchar(255) DEFAULT NULL COMMENT '用户昵称',
    `baseline_summary`    text         DEFAULT NULL COMMENT '基线摘要',
    `target_summary`      text         DEFAULT NULL COMMENT '目标摘要',
    `impact_summary`      text         DEFAULT NULL COMMENT '影响摘要',
    `matched_org_count`   int          DEFAULT 0 COMMENT '命中组织数',
    `affected_role_count` int          DEFAULT 0 COMMENT '受影响角色数',
    `affected_user_count` int          DEFAULT 0 COMMENT '受影响用户数',
    `added_org_count`     int          DEFAULT 0 COMMENT '新增组织数',
    `removed_org_count`   int          DEFAULT 0 COMMENT '移除组织数',
    `preview_request_json` text        DEFAULT NULL COMMENT '预览请求快照(JSON)',
    `preview_snapshot_json` text       DEFAULT NULL COMMENT '预览结果快照(JSON)',
    `change_remark`       text         DEFAULT NULL COMMENT '变更说明',
    `release_status`      varchar(32)  NOT NULL COMMENT '发布状态：DRAFT / APPROVED / REJECTED / APPLIED',
    `requested_by_user_id` bigint      NOT NULL COMMENT '创建人ID',
    `requested_by_name`   varchar(128) DEFAULT NULL COMMENT '创建人名称',
    `reviewed_by_user_id` bigint       DEFAULT NULL COMMENT '审核人ID',
    `reviewed_by_name`    varchar(128) DEFAULT NULL COMMENT '审核人名称',
    `review_comment`      text         DEFAULT NULL COMMENT '审核意见',
    `reviewed_time`       datetime     DEFAULT NULL COMMENT '审核时间',
    `applied_by_user_id`  bigint       DEFAULT NULL COMMENT '应用人ID',
    `applied_by_name`     varchar(128) DEFAULT NULL COMMENT '应用人名称',
    `applied_time`        datetime     DEFAULT NULL COMMENT '应用时间',
    `create_time`         datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_scope_release_tenant_status` (`tenant_id`, `release_status`, `create_time`, `id`),
    KEY `idx_user_scope_release_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户数据权限发布单';

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
    `cost_amount`    bigint       DEFAULT NULL COMMENT '成本金额',
    `billing_version` varchar(64) DEFAULT NULL COMMENT '计费版本',
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
