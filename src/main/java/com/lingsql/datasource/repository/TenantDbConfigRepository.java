package com.lingsql.datasource.repository;

import com.lingsql.datasource.model.TenantDbConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantDbConfigRepository extends JpaRepository<TenantDbConfig, Long> {
    Optional<TenantDbConfig> findByTenantId(String tenantId);
}