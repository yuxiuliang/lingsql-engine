package com.lingsql.rbac.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户密码重置请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class UserPasswordResetRequest {

    /**
     * 新密码。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
