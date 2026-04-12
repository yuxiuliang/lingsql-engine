package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户数据权限预览请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class UserDataScopePreviewRequest {

    /**
     * 租户ID，系统管理员可指定。
     */
    private String tenantId;

    /**
     * 用户覆盖规则；可传 `INHERIT` 预览角色继承效果。
     */
    private DataScopeRule dataScope;

    /**
     * 用户ID，编辑已有用户时可传。
     */
    private Long userId;

    /**
     * 基线用户覆盖规则。
     */
    private DataScopeRule baselineDataScope;

    /**
     * 当前用户绑定的直属组织节点ID列表。
     */
    private List<Long> orgUnitIds = new ArrayList<>();

    /**
     * 基线直属组织节点ID列表。
     */
    private List<Long> baselineOrgUnitIds = new ArrayList<>();

    /**
     * 用户当前绑定的角色ID列表。
     */
    private List<Long> roleIds = new ArrayList<>();

    /**
     * 基线角色ID列表。
     */
    private List<Long> baselineRoleIds = new ArrayList<>();

    /**
     * 样本 SQL。
     */
    private String sampleSql;
}
