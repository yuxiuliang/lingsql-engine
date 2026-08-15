package com.lingsql.tenant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 租户开户数据源请求。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Data
public class TenantDataSourceProvisionRequest {

    private String jdbcUrl;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String driverClass;

    private String isolationMode;

    private String logicColumn;

    private String whiteListTables;

    private String whiteListColumns;

    private String sensitiveColumns;
}
