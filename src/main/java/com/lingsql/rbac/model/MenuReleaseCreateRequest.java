package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 菜单治理发布单创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class MenuReleaseCreateRequest {

    /**
     * 目标租户ID。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String tenantId;

    /**
     * 发布单标题。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String releaseTitle;

    /**
     * 变更说明。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String changeRemark;

    /**
     * 影响分析请求。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private MenuImpactPreviewRequest impactRequest;
}
