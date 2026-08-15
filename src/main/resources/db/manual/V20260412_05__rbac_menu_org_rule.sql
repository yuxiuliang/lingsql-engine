ALTER TABLE `t_lingsql_engine_menu`
  ADD COLUMN `org_type_codes` TEXT DEFAULT NULL COMMENT '适配的直接归属组织类型编码，逗号分隔' AFTER `perm_code`,
  ADD COLUMN `org_unit_codes` TEXT DEFAULT NULL COMMENT '适配的直接归属组织编码，逗号分隔' AFTER `org_type_codes`,
  ADD COLUMN `org_tags` TEXT DEFAULT NULL COMMENT '适配的直接归属组织标签，逗号分隔' AFTER `org_unit_codes`,
  ADD COLUMN `required_relation_types` TEXT DEFAULT NULL COMMENT '要求具备的组织关系类型，逗号分隔' AFTER `org_tags`;
