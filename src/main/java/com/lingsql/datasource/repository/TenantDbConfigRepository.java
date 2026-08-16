package com.lingsql.datasource.repository;

import com.lingsql.datasource.model.TenantDbConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TenantDbConfigRepository extends JpaRepository<TenantDbConfig, Long> {
    Optional<TenantDbConfig> findByTenantId(String tenantId);

    /**
     * 以悲观写锁读取租户数据源配置，锁一直持有到调用方事务结束。
     *
     * @param tenantId 租户标识
     * @return 数据源配置
     * @author 余炘洋
     * @date 2026-08-16
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select config from TenantDbConfig config where config.tenantId = :tenantId")
    Optional<TenantDbConfig> findByTenantIdForUpdate(@Param("tenantId") String tenantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update TenantDbConfig config
               set config.schemaSyncStatus = 1,
                   config.lastSyncTime = :syncedAt,
                   config.schemaTableCount = :tableCount,
                   config.schemaWhitelistTableCount = :whitelistTableCount,
                   config.schemaWhitelistDigest = :whitelistDigest,
                   config.schemaSyncToken = null
             where config.id = :configId
               and config.schemaSyncToken = :syncToken
            """)
    int completeSchemaSynchronization(
            @Param("configId") Long configId,
            @Param("syncToken") String syncToken,
            @Param("syncedAt") LocalDateTime syncedAt,
            @Param("tableCount") int tableCount,
            @Param("whitelistTableCount") int whitelistTableCount,
            @Param("whitelistDigest") String whitelistDigest
    );
}
