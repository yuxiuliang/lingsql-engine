package com.lingsql.metadata.repository;

import com.lingsql.metadata.entity.MetadataExt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 元数据扩展字典仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface MetadataExtRepository extends JpaRepository<MetadataExt, Long> {

    /**
     * 查询租户指定表的扩展配置。
     *
     * @param tenantId 租户标识
     * @param tableName 表名
     * @return 扩展列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    List<MetadataExt> findByTenantIdAndTableName(String tenantId, String tableName);

    /**
     * 查询租户全部扩展配置。
     *
     * @param tenantId 租户标识
     * @return 扩展列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    List<MetadataExt> findByTenantId(String tenantId);

    /**
     * 根据字段查询扩展配置。
     *
     * @param tenantId 租户标识
     * @param tableName 表名
     * @param columnName 字段名
     * @return 扩展配置
     * @author 余炘洋
     * @date 2026-01-23
     */
    Optional<MetadataExt> findByTenantIdAndTableNameAndColumnName(String tenantId, String tableName, String columnName);
}

