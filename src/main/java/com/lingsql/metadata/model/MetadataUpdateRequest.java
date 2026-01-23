package com.lingsql.metadata.model;

import lombok.Data;

/**
 * 元数据更新请求体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class MetadataUpdateRequest {

    private String tenantId;
    private String tableName;
    private String columnName;
    private String businessComment;
}

