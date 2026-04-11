package com.lingsql.organization.model;

import lombok.Data;

/**
 * 组织类型更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
public class OrgUnitTypeUpdateRequest {

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
}
