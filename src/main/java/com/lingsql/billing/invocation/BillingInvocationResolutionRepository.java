package com.lingsql.billing.invocation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 物理调用资金裁决记录仓库。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
interface BillingInvocationResolutionRepository
        extends JpaRepository<BillingInvocationResolutionEntity, Long> {

    Optional<BillingInvocationResolutionEntity> findByInvocationId(String invocationId);
}
