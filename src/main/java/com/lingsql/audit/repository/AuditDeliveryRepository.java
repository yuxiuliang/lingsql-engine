package com.lingsql.audit.repository;

import com.lingsql.audit.entity.AuditDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 审计投递箱仓库。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface AuditDeliveryRepository extends JpaRepository<AuditDelivery, Long> {

    Optional<AuditDelivery> findByEventId(String eventId);

    @Query("""
            SELECT d FROM AuditDelivery d
             WHERE d.deliveryStatus = :status
               AND d.outcome <> :preparedOutcome
               AND (d.nextAttemptTime IS NULL OR d.nextAttemptTime <= :now)
             ORDER BY d.id ASC
            """)
    List<AuditDelivery> findDeliverable(@Param("status") String status,
                                        @Param("preparedOutcome") String preparedOutcome,
                                        @Param("now") LocalDateTime now,
                                        Pageable pageable);

    @Query("""
            SELECT d FROM AuditDelivery d
             WHERE d.outcome = :outcome
               AND d.createTime < :deadline
             ORDER BY d.id ASC
            """)
    List<AuditDelivery> findStalePrepared(@Param("outcome") String outcome,
                                          @Param("deadline") LocalDateTime deadline,
                                          Pageable pageable);

    long countByDeliveryStatus(String deliveryStatus);

    long countByOutcome(String outcome);

    long countByDeliveryStatusAndUpdateTimeBefore(String deliveryStatus, LocalDateTime updateTime);

    long deleteByDeliveryStatusAndUpdateTimeBefore(String deliveryStatus, LocalDateTime updateTime);
}
