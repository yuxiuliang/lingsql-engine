package com.lingsql.tenant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 租户管理员开户请求。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Data
public class TenantAdminAccountRequest {

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String nickname;
}
