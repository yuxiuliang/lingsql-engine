-- Digital Employee system patch (2026-02-28)
-- 目标：补齐数字员工商业化能力所需的结构、权限与菜单。

CREATE TABLE IF NOT EXISTS `t_lingsql_engine_digital_employee`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`        varchar(64)  NOT NULL COMMENT '目标租户ID',
    `employee_code`    varchar(64)  NOT NULL COMMENT '员工编码',
    `employee_name`    varchar(128) NOT NULL COMMENT '员工名称',
    `role_name`        varchar(128) DEFAULT NULL COMMENT '岗位角色',
    `scenario`         varchar(255) DEFAULT NULL COMMENT '业务场景',
    `prompt_template`  text COMMENT '提示词模板',
    `default_question` text COMMENT '默认执行问题',
    `status`           tinyint      DEFAULT '1' COMMENT '状态: 1-启用, 0-停用',
    `run_count`        bigint       NOT NULL DEFAULT 0 COMMENT '累计运行次数',
    `success_count`    bigint       NOT NULL DEFAULT 0 COMMENT '累计成功次数',
    `last_run_status`  tinyint      DEFAULT NULL COMMENT '最近运行状态: 1-成功, 0-失败',
    `last_run_time`    datetime     DEFAULT NULL COMMENT '最近运行时间',
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

CREATE TABLE IF NOT EXISTS `t_lingsql_engine_digital_employee_run_log`
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
    `create_time`  datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_de_run_employee_time` (`employee_id`, `create_time`),
    KEY `idx_de_run_tenant_time` (`tenant_id`, `create_time`),
    KEY `idx_de_run_request` (`request_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='数字员工运行日志表';

INSERT INTO `t_lingsql_engine_permission` (`perm_code`, `perm_name`, `perm_type`, `status`)
VALUES
    ('MENU:ADMIN:DIGITAL_EMPLOYEE', '菜单-数字员工', 'MENU', 1),
    ('ADMIN:DIGITAL_EMPLOYEE:READ', '接口-数字员工读取', 'API', 1),
    ('ADMIN:DIGITAL_EMPLOYEE:WRITE', '接口-数字员工维护', 'API', 1),
    ('ADMIN:DIGITAL_EMPLOYEE:RUN', '接口-数字员工执行', 'API', 1)
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `perm_type` = VALUES(`perm_type`),
    `status` = VALUES(`status`);

SET @menu_admin_id = (
    SELECT `id`
    FROM `t_lingsql_engine_menu`
    WHERE `tenant_id` IS NULL
      AND `menu_code` = 'MENU_ADMIN'
    LIMIT 1
);

INSERT INTO `t_lingsql_engine_menu`
(`tenant_id`, `menu_code`, `menu_name`, `parent_id`, `path`, `component`, `icon`, `sort`, `visible`, `status`, `perm_code`)
SELECT
    NULL,
    'MENU_ADMIN_DIGITAL_EMPLOYEE',
    '数字员工',
    @menu_admin_id,
    '/digital-employee',
    'DigitalEmployee',
    'robot',
    116,
    1,
    1,
    'MENU:ADMIN:DIGITAL_EMPLOYEE'
FROM DUAL
WHERE @menu_admin_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    `menu_name` = VALUES(`menu_name`),
    `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`),
    `component` = VALUES(`component`),
    `icon` = VALUES(`icon`),
    `sort` = VALUES(`sort`),
    `visible` = VALUES(`visible`),
    `status` = VALUES(`status`),
    `perm_code` = VALUES(`perm_code`);

INSERT IGNORE INTO `t_lingsql_engine_role_permission` (`role_id`, `perm_id`)
SELECT `role`.`id`, `perm`.`id`
FROM `t_lingsql_engine_role` `role`
         JOIN `t_lingsql_engine_permission` `perm`
              ON `perm`.`perm_code` IN (
                  'MENU:ADMIN:DIGITAL_EMPLOYEE',
                  'ADMIN:DIGITAL_EMPLOYEE:READ',
                  'ADMIN:DIGITAL_EMPLOYEE:WRITE',
                  'ADMIN:DIGITAL_EMPLOYEE:RUN'
              )
WHERE `role`.`tenant_id` IS NULL
  AND `role`.`role_code` IN ('SYS_ADMIN', 'SUPER_ADMIN');

