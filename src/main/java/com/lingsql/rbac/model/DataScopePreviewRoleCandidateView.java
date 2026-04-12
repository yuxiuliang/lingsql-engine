package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户数据权限预览中的角色候选说明。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class DataScopePreviewRoleCandidateView {

    /**
     * 角色ID。
     */
    private Long roleId;

    /**
     * 角色编码。
     */
    private String roleCode;

    /**
     * 角色名称。
     */
    private String roleName;

    /**
     * 角色数据权限。
     */
    private DataScopeRule dataScope;

    /**
     * 是否为最终命中的角色规则。
     */
    private boolean selected;

    /**
     * 说明文本。
     */
    private String summary;

    /**
     * 关系类型列表。
     */
    private List<String> relationTypes = new ArrayList<>();
}
