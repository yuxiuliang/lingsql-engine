package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 数据权限规则模型。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class DataScopeRule {

    /**
     * 范围类型: ALL/TENANT/SELF/ORG/ORG_SUBTREE/CUSTOM。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String scopeType = "TENANT";

    /**
     * SELF / ORG / ORG_SUBTREE 默认字段名。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String scopeColumn = "";

    /**
     * CUSTOM SQL 条件。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String scopeRule = "";

}
