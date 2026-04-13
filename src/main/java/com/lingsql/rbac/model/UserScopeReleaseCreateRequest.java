package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 用户数据权限发布单创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class UserScopeReleaseCreateRequest {

    /**
     * 租户ID。
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
     * 用户数据权限预览请求。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private UserDataScopePreviewRequest previewRequest;
}
