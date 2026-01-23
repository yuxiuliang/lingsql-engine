package com.lingsql.metadata.model;

import lombok.Data;

import java.util.List;

/**
 * 元数据表视图对象。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class MetadataTableView {

    private String schemaName;
    private String tableName;
    private String tableComment;
    private List<MetadataFieldView> columns;
}

