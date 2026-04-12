package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 数据权限命中组织预览项。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class DataScopePreviewOrgView {

    /**
     * 组织节点ID。
     */
    private Long orgUnitId;

    /**
     * 组织编码。
     */
    private String unitCode;

    /**
     * 组织名称。
     */
    private String unitName;

    /**
     * 组织类型编码。
     */
    private String typeCode;

    /**
     * 命中来源说明。
     */
    private String hitSource;
}
