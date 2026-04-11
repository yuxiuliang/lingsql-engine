package com.lingsql.organization.model;

import lombok.Data;

/**
 * 组织单元更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
public class OrgUnitUpdateRequest {

    /**
     * 组织名称。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String unitName;

    /**
     * 组织类型编码。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String typeCode;

    /**
     * 负责人用户ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long leaderUserId;

    /**
     * 父节点ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long parentId;

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
     * 标签列表。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private java.util.List<String> tags;

    /**
     * 扩展属性 JSON。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String attributeJson;
}
