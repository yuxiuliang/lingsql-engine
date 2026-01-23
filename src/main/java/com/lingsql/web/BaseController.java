package com.lingsql.web;

import com.lingsql.rbac.security.SecureTenantResolver;

/**
 * 基础控制器，提供通用安全能力。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public abstract class BaseController {

    /**
     * 安全解析租户ID。
     *
     * @param inputTenantId 接口传入的租户ID
     * @return 安全租户ID
     * @author 余炘洋
     * @date 2026-01-23
     */
    protected String resolveSecureTenantId(String inputTenantId) {
        return SecureTenantResolver.resolveSecureTenantId(inputTenantId);
    }
}
