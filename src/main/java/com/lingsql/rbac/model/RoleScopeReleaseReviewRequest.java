package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 角色数据权限发布单审核请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class RoleScopeReleaseReviewRequest {

    /**
     * 审核结论，仅支持 APPROVED / REJECTED。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String decision;

    /**
     * 审核意见。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String reviewComment;
}
