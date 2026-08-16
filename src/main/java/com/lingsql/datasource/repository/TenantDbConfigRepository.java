package com.lingsql.datasource.repository;

import com.lingsql.datasource.model.TenantDbConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
