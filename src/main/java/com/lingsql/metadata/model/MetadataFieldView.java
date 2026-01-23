package com.lingsql.metadata.model;

import lombok.Data;

/**
 * 元数据字段视图对象。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class MetadataFieldView {

    private String columnName;
    private String dataType;
    private String columnComment;
    private String businessComment;
    private boolean primaryKey;
    private boolean isolatedField;
}

