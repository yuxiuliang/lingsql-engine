package com.lingsql.organization.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
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
     * 负责人字段是否在请求中显式出现。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @JsonIgnore
    private boolean leaderUserIdSpecified;

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

    /**
     * 显式设置负责人字段，同时记录字段出现状态，便于区分“未传”和“清空”。
     *
     * @param leaderUserId 负责人用户ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    @JsonSetter("leaderUserId")
    public void setLeaderUserId(Long leaderUserId) {
        this.leaderUserId = leaderUserId;
        this.leaderUserIdSpecified = true;
    }
}
