package com.lingsql.metadata.model;

import lombok.Data;

/**
 * 隔离字段更新请求体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class IsolationUpdateRequest {

    private String tenantId;
    private String tableName;
    private String columnName;
    private boolean isolated;
}

