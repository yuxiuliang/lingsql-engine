-- 组织域增强补丁 (2026-04-11)
-- 目标：为组织节点补充负责人、标签、扩展属性，并新增组织关系表。

ALTER TABLE `t_lingsql_engine_org_unit`
    ADD COLUMN `leader_user_id` bigint DEFAULT NULL COMMENT '负责人用户ID' AFTER `type_code`,
    ADD COLUMN `tag_text` text COMMENT '标签文本，逗号分隔' AFTER `remark`,
    ADD COLUMN `attribute_json` text COMMENT '扩展属性 JSON' AFTER `tag_text`;

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
