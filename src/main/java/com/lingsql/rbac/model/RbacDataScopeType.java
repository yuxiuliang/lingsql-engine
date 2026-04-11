package com.lingsql.rbac.model;

/**
 * 数据权限范围类型。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public final class RbacDataScopeType {

    /**
     * 全量。
     */
    public static final String ALL = "ALL";

    /**
     * 本租户。
     */
    public static final String TENANT = "TENANT";

    /**
     * 仅本人。
     */
    public static final String SELF = "SELF";

    /**
     * 指定组织。
     */
    public static final String ORG = "ORG";

    /**
     * 组织及下级。
     */
    public static final String ORG_SUBTREE = "ORG_SUBTREE";

    /**
     * 自定义规则。
     */
    public static final String CUSTOM = "CUSTOM";

    private RbacDataScopeType() {
    }
}
