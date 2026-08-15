package com.lingsql.billing.acceptance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 计费验收回执仓库。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
interface BillingAcceptanceReceiptRepository extends JpaRepository<BillingAcceptanceReceiptEntity, Long> {

    Optional<BillingAcceptanceReceiptEntity> findBySessionId(String sessionId);
}
