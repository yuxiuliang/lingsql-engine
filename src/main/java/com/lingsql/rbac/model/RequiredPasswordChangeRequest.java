package com.lingsql.rbac.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 首次登录密码修改请求。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Data
public class RequiredPasswordChangeRequest {

    @NotBlank(message = "当前密码不能为空")
    @Size(max = 128, message = "当前密码长度不能超过 128 位")
    private String currentPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "新密码长度需在 8-32 位")
    private String newPassword;
}
