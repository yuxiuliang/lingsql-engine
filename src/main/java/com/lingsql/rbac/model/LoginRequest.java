package com.lingsql.rbac.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class LoginRequest {

    /**
     * 租户ID（必填）。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @NotBlank(message = "租户ID不能为空")
    private String tenantId;

    /**
     * 账号。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @NotBlank(message = "账号不能为空")
    private String username;

    /**
     * 密码。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
