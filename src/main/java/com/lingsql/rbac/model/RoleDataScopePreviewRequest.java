package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色数据权限预览请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class RoleDataScopePreviewRequest {

    /**
     * 租户ID，系统管理员可指定。
     */
    private String tenantId;

    /**
     * 待预览的数据权限规则。
     */
    private DataScopeRule dataScope;

    /**
     * 角色ID，编辑已有角色时可传。
     */
    private Long roleId;

    /**
     * 基线数据权限规则。
     */
    private DataScopeRule baselineDataScope;

    /**
     * 样本直属组织节点ID列表。
     */
    private List<Long> orgUnitIds = new ArrayList<>();

    /**
     * 样本 SQL。
     */
    private String sampleSql;
}
