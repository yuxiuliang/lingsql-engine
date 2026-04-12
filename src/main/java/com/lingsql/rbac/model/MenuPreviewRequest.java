package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单命中预览请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class MenuPreviewRequest {

    /**
     * 目标租户ID，系统管理员可指定。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String tenantId;

    /**
     * 权限编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> permissionCodes = new ArrayList<>();

    /**
     * 直接归属组织类型编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> orgTypeCodes = new ArrayList<>();

    /**
     * 直接归属组织编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> orgUnitCodes = new ArrayList<>();

    /**
     * 直接归属组织标签列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> orgTags = new ArrayList<>();

    /**
     * 已具备的组织关系类型列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> relationTypes = new ArrayList<>();

    /**
     * 是否体验账号。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Boolean experience;

    /**
     * 菜单白名单。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> menuWhitelist = new ArrayList<>();
}
