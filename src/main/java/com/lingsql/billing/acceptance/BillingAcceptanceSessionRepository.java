package com.lingsql.billing.acceptance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 计费验收会话仓库。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
interface BillingAcceptanceSessionRepository extends JpaRepository<BillingAcceptanceSessionEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BillingAcceptanceSessionEntity s WHERE s.sessionId = :sessionId")
    Optional<BillingAcceptanceSessionEntity> findBySessionIdForUpdate(@Param("sessionId") String sessionId);
}
