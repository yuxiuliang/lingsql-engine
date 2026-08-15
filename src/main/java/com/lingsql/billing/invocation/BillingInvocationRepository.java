package com.lingsql.billing.invocation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 物理模型调用资金仓库。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface BillingInvocationRepository extends JpaRepository<BillingInvocationEntity, Long> {

    Optional<BillingInvocationEntity> findByInvocationId(String invocationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM BillingInvocationEntity i WHERE i.invocationId = :invocationId")
    Optional<BillingInvocationEntity> findByInvocationIdForUpdate(@Param("invocationId") String invocationId);

    @Modifying
    @Query("""
            UPDATE BillingInvocationEntity i
               SET i.state = :nextState,
                   i.dispatchedAt = :now,
                   i.updateTime = :now
             WHERE i.invocationId = :invocationId
               AND i.state = :expectedState
            """)
    int transitionToDispatched(@Param("invocationId") String invocationId,
                               @Param("expectedState") String expectedState,
                               @Param("nextState") String nextState,
                               @Param("now") LocalDateTime now);

    long countByState(String state);

    long countByStateAndReservedAtBefore(String state, LocalDateTime cutoff);

    long countByStateAndDispatchedAtBefore(String state, LocalDateTime cutoff);

    /**
     * 汇总指定工作流请求的逐物理调用验收证据。
     *
     * @param tenantId 租户ID
     * @param workflowRequestIds 工作流请求ID
     * @return 验收投影
     */
    @Query(value = """
            SELECT COUNT(*) AS invocationCount,
                   COUNT(DISTINCT workflow_request_id) AS matchedWorkflowCount,
                   SUM(CASE WHEN state = 'SETTLED' THEN 1 ELSE 0 END) AS settledCount,
                   SUM(CASE WHEN state = 'UNKNOWN' THEN 1 ELSE 0 END) AS unknownCount,
                   SUM(CASE WHEN state IN ('RESERVED', 'DISPATCHED') THEN 1 ELSE 0 END) AS pendingCount
              FROM t_lingsql_engine_billing_invocation
             WHERE tenant_id = :tenantId
               AND workflow_request_id IN (:workflowRequestIds)
            """, nativeQuery = true)
    InvocationEvidenceProjection findInvocationEvidence(
            @Param("tenantId") String tenantId,
            @Param("workflowRequestIds") List<String> workflowRequestIds
    );

    List<BillingInvocationEntity> findByStateAndReservedAtBeforeOrderByIdAsc(
            String state,
            LocalDateTime cutoff,
            Pageable pageable
    );

    List<BillingInvocationEntity> findByStateAndDispatchedAtBeforeOrderByIdAsc(
            String state,
            LocalDateTime cutoff,
            Pageable pageable
    );

    interface InvocationEvidenceProjection {

        Long getInvocationCount();

        Long getMatchedWorkflowCount();

        Long getSettledCount();

        Long getUnknownCount();

        Long getPendingCount();
    }
}
