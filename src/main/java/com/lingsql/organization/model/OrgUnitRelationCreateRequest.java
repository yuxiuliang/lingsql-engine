package com.lingsql.organization.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 组织关系创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
public class OrgUnitRelationCreateRequest {

    /**
     * 租户ID（系统管理员可指定）。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String tenantId;

    /**
     * 源组织节点ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @NotNull(message = "源组织节点不能为空")
    private Long sourceOrgUnitId;

    /**
     * 目标组织节点ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @NotNull(message = "目标组织节点不能为空")
    private Long targetOrgUnitId;

    /**
     * 关系类型编码。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @NotBlank(message = "关系类型不能为空")
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
