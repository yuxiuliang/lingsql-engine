package com.lingsql.datasource.model;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import com.lingsql.security.SensitiveStringAttributeConverter;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户数据库配置实体。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Entity
@Table(name = "t_lingsql_engine_tenant_db_config")
@Data
public class TenantDbConfig {
    /**
     * 主键 ID。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户唯一标识。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "tenant_id", unique = true, nullable = false)
    private String tenantId;

    /**
     * JDBC 连接地址。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "jdbc_url", nullable = false)
    private String jdbcUrl;

    /**
     * 数据库用户名。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "username")
    private String username;

    /**
     * 数据库密码。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "password")
    @Convert(converter = SensitiveStringAttributeConverter.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * 判断数据库密码是否已配置，不返回密码内容。
     *
     * @return 是否已配置密码
     * @author 余炘洋
     * @date 2026-08-15
     */
    @Transient
    public boolean isPasswordConfigured() {
        return password != null && !password.isBlank();
    }

    /**
     * JDBC 驱动类。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "driver_class")
    private String driverClass = "com.mysql.cj.jdbc.Driver";

    /**
     * 隔离模式：PHYSICAL / LOGICAL。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "isolation_mode")
    private String isolationMode;

    /**
     * 逻辑隔离字段名，默认 tenant_id。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "logic_column")
    private String logicColumn;

    /**
     * 核心表白名单（逗号分隔）。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "white_list_tables", columnDefinition = "TEXT")
    private String whiteListTables;

    /**
     * 字段白名单（逗号分隔）。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "white_list_columns", columnDefinition = "TEXT")
    private String whiteListColumns;

    /**
     * 敏感字段黑名单（逗号分隔）。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "sensitive_columns", columnDefinition = "TEXT")
    private String sensitiveColumns;

    /**
     * 元数据同步状态。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "schema_sync_status")
    private Integer schemaSyncStatus;

    /**
     * 最近同步时间。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    /**
     * 最近一次成功同步的实际表数。
     *
     * @author 余炘洋
     * @date 2026-08-16
     */
    @Column(name = "schema_table_count")
    private Integer schemaTableCount;

    /**
     * 最近一次成功同步的白名单表数。
     *
     * @author 余炘洋
     * @date 2026-08-16
     */
    @Column(name = "schema_whitelist_table_count")
    private Integer schemaWhitelistTableCount;

    /**
     * 最近一次成功同步的规范化白名单 SHA-256。
     *
     * @author 余炘洋
     * @date 2026-08-16
     */
    @Column(name = "schema_whitelist_digest", length = 64)
    private String schemaWhitelistDigest;

    /**
     * 当前同步尝试令牌，用于阻止旧数据源的迟到结果覆盖新配置。
     *
     * @author 余炘洋
     * @date 2026-08-16
     */
    @JsonIgnore
    @Column(name = "schema_sync_token", length = 36)
    private String schemaSyncToken;

    /**
     * 配置状态。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 判断是否为逻辑隔离。
     *
     * @return 是否逻辑隔离
     * @author 余炘洋
     * @date 2026-01-22
     */
    public boolean isLogicalIsolation() {
        return "LOGICAL".equalsIgnoreCase(isolationMode);
    }

    /**
     * 获取逻辑隔离字段名，默认 tenant_id。
     *
     * @return 字段名
     * @author 余炘洋
     * @date 2026-01-22
     */
    public String resolveLogicColumn() {
        return (logicColumn == null || logicColumn.isBlank()) ? "tenant_id" : logicColumn;
    }
}
