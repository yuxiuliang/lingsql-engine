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
 * 组织单元类型实体。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_org_unit_type")
public class OrgUnitType {

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
     * 类型编码。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "type_code", nullable = false)
    private String typeCode;

    /**
     * 类型名称。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "type_name", nullable = false)
    private String typeName;

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
