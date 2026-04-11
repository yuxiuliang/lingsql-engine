package com.lingsql.organization.model;

import lombok.Data;

/**
 * 组织关系更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
public class OrgUnitRelationUpdateRequest {

    /**
     * 源组织节点ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long sourceOrgUnitId;

    /**
     * 目标组织节点ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long targetOrgUnitId;

    /**
     * 关系类型编码。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String relationType;

    /**
     * 关系名称。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String relationName;

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
}
