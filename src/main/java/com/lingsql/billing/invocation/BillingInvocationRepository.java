package com.lingsql.billing.invocation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 物理模型调用资金仓库。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface BillingInvocationRepository extends JpaRepository<BillingInvocationEntity, Long>,
        JpaSpecificationExecutor<BillingInvocationEntity> {

    Optional<BillingInvocationEntity> findByInvocationId(String invocationId);

    /**
     * 按供应商请求标识匹配物理调用。
     *
     * @param providerRequestId 供应商请求标识
     * @return 匹配的物理调用
     * @author 余炘洋
     * @date 2026-10-17
     */
    Optional<BillingInvocationEntity> findFirstByProviderRequestId(String providerRequestId);

    /**
     * 按物理调用标识批量查询。
     *
     * @param invocationIds 物理调用标识集合
     * @return 物理调用列表
     * @author 余炘洋
     * @date 2026-10-17
     */
    List<BillingInvocationEntity> findByInvocationIdIn(Collection<String> invocationIds);

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

    /**
     * 按工作流请求汇总逐物理调用状态与角色证据。
     *
     * @param tenantId 租户ID
     * @param workflowRequestIds 工作流请求ID
     * @return 逐请求调用证据
     */
    @Query(value = """
            SELECT workflow_request_id AS workflowRequestId,
                   COUNT(*) AS invocationCount,
                   SUM(CASE WHEN stage = 'SQL_ARCHITECT' AND state = 'SETTLED' THEN 1 ELSE 0 END)
                       AS architectSettledCount,
                   SUM(CASE WHEN stage = 'BUSINESS_ANALYST' AND state = 'SETTLED' THEN 1 ELSE 0 END)
                       AS analystSettledCount,
                   SUM(CASE WHEN state = 'SETTLED' THEN 1 ELSE 0 END) AS settledCount,
                   SUM(CASE WHEN state = 'UNKNOWN' THEN 1 ELSE 0 END) AS unknownCount,
                   SUM(CASE WHEN state IN ('RESERVED', 'DISPATCHED') THEN 1 ELSE 0 END) AS pendingCount,
                   SUM(CASE WHEN state = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelledCount
              FROM t_lingsql_engine_billing_invocation
             WHERE tenant_id = :tenantId
               AND workflow_request_id IN (:workflowRequestIds)
             GROUP BY workflow_request_id
            """, nativeQuery = true)
    List<RequestInvocationEvidenceProjection> findRequestInvocationEvidence(
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

    List<BillingInvocationEntity> findByTenantIdAndWorkflowRequestIdInOrderByInvocationIdAsc(
            String tenantId,
            List<String> workflowRequestIds
    );

    interface InvocationEvidenceProjection {

        Long getInvocationCount();

        Long getMatchedWorkflowCount();

        Long getSettledCount();

        Long getUnknownCount();

        Long getPendingCount();
    }

    interface RequestInvocationEvidenceProjection {
        String getWorkflowRequestId();

        Long getInvocationCount();

        Long getArchitectSettledCount();

        Long getAnalystSettledCount();

        Long getSettledCount();

        Long getUnknownCount();

        Long getPendingCount();

        Long getCancelledCount();
    }
}
