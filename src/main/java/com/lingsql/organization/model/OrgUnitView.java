package com.lingsql.organization.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织单元视图。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
public class OrgUnitView {

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
     * 组织编码。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String unitCode;

    /**
     * 组织名称。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String unitName;

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
     * 负责人用户ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long leaderUserId;

    /**
     * 负责人账号。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String leaderUsername;

    /**
     * 负责人昵称。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String leaderNickname;

    /**
     * 父节点ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long parentId;

    /**
     * 树路径。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String treePath;

    /**
     * 树层级。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Integer treeLevel;

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
    private List<String> tags = new ArrayList<>();

    /**
     * 扩展属性 JSON。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String attributeJson;

    /**
     * 成员数量。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long memberCount;

    /**
     * 子节点列表。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<OrgUnitView> children = new ArrayList<>();
}
