package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 用户数据权限发布单回滚请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class UserScopeReleaseRollbackRequest {

    /**
     * 回滚发布单标题。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String releaseTitle;

    /**
     * 回滚说明。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String changeRemark;
}
