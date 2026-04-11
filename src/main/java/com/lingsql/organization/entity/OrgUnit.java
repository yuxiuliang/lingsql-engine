package com.lingsql.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织单元实体。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_org_unit")
public class OrgUnit {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * 组织编码。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    /**
     * 组织名称。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "unit_name", nullable = false)
    private String unitName;

    /**
     * 组织类型编码。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "type_code", nullable = false)
    private String typeCode;

    /**
     * 负责人用户ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "leader_user_id")
    private Long leaderUserId;

    /**
     * 父节点ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * 树路径，使用 ID 级联表示。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "tree_path")
    private String treePath;

    /**
     * 树层级，从 1 开始。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "tree_level")
    private Integer treeLevel;

    /**
     * 排序值。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "sort")
    private Integer sort;

    /**
     * 状态: 1-启用, 0-停用。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 备注。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 标签文本，使用逗号分隔。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "tag_text", columnDefinition = "TEXT")
    private String tagText;

    /**
     * 扩展属性 JSON。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "attribute_json", columnDefinition = "TEXT")
    private String attributeJson;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
