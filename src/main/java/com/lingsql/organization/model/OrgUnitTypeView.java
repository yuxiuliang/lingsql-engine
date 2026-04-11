package com.lingsql.organization.model;

import lombok.Data;

/**
 * 组织类型视图。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
public class OrgUnitTypeView {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String tenantId;

    /**
     * 类型编码。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String typeCode;

    /**
     * 类型名称。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String typeName;

    /**
     * 排序值。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Integer sort;

    /**
     * 状态。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Integer status;

    /**
     * 备注。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String remark;

    /**
     * 组织节点数量。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long unitCount;
}
