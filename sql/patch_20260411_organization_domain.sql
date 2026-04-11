-- 通用组织域补丁 (2026-04-11)
-- 目标：补齐商业化所需的通用组织架构能力，支持政府、学校、企业、连锁等场景。

CREATE TABLE IF NOT EXISTS `t_lingsql_engine_org_unit_type`
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

CREATE TABLE IF NOT EXISTS `t_lingsql_engine_org_unit`
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

CREATE TABLE IF NOT EXISTS `t_lingsql_engine_org_unit_relation`
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

CREATE TABLE IF NOT EXISTS `t_lingsql_engine_user_org`
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

INSERT INTO `t_lingsql_engine_permission` (`perm_code`, `perm_name`, `perm_type`, `status`)
VALUES
    ('MENU:ADMIN:ORG', '菜单-组织架构', 'MENU', 1),
    ('ADMIN:ORG:READ', '接口-组织架构读取', 'API', 1),
    ('ADMIN:ORG:WRITE', '接口-组织架构维护', 'API', 1),
    ('ADMIN:ORG:DELETE', '接口-组织架构删除', 'API', 1)
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
    'MENU_ADMIN_ORG',
    '组织架构',
    @menu_admin_id,
    '/organization',
    'OrganizationManage',
    'tenant',
    165,
    1,
    1,
    'MENU:ADMIN:ORG'
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
                  'MENU:ADMIN:ORG',
                  'ADMIN:ORG:READ',
                  'ADMIN:ORG:WRITE',
                  'ADMIN:ORG:DELETE'
              )
WHERE `role`.`role_code` IN ('SYS_ADMIN', 'SUPER_ADMIN', 'TENANT_ADMIN');
