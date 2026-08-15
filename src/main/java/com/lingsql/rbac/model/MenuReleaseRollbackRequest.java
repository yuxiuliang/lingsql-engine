package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 菜单发布单回滚创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class MenuReleaseRollbackRequest {

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
