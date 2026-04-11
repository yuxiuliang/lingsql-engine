package com.lingsql.organization.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 组织类型创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
public class OrgUnitTypeCreateRequest {

    /**
     * 租户ID（系统管理员可指定）。
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
    @NotBlank(message = "类型编码不能为空")
    private String typeCode;

    /**
     * 类型名称。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @NotBlank(message = "类型名称不能为空")
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
}
