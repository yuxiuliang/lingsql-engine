package com.lingsql.billing.repository;

import com.lingsql.billing.entity.BillingLedgerEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 计费账本事件仓库。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
public interface BillingLedgerEventRepository extends JpaRepository<BillingLedgerEvent, Long> {

    /**
     * 判断请求序号是否已记账。
     *
     * @param requestId 请求ID
     * @param eventSeq  事件序号
     * @return 是否存在
     * @author 余炘洋
     * @date 2026-01-29
     */
    boolean existsByRequestIdAndEventSeq(String requestId, Integer eventSeq);

    /**
     * 查询指定请求序号的完整账本事件，用于校验幂等重放内容。
     *
     * @param requestId 请求ID
     * @param eventSeq 事件序号
     * @return 账本事件
     */
    List<BillingLedgerEvent> findByTenantIdAndRequestIdAndEventSeqOrderByModelNameAsc(
            String tenantId,
            String requestId,
            Integer eventSeq
    );

    /**
     * 查询请求的指定类型账本事件。
     *
     * @param requestId 请求ID
     * @param eventType 事件类型
     * @return 账本事件
     */
    List<BillingLedgerEvent> findByTenantIdAndRequestIdAndEventTypeOrderByIdAsc(
            String tenantId,
            String requestId,
            Integer eventType
    );

    /**
     * 锁定一批遗留待结算事件，避免多实例重复应用余额副作用。
     *
     * @param status 待结算状态
     * @param limit 批量上限
     * @return 已加行锁的事件
     */
    @Query(value = """
            SELECT *
              FROM t_lingsql_engine_billing_ledger
             WHERE status = :status
             ORDER BY id ASC
             LIMIT :limit
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BillingLedgerEvent> findPendingForUpdate(@Param("status") Integer status,
                                                   @Param("limit") Integer limit);

    /**
     * 查询待结算账本事件。
     *
     * @param status   状态
     * @param pageable 分页
     * @return 事件列表
     * @author 余炘洋
     * @date 2026-01-29
     */
    List<BillingLedgerEvent> findByStatusOrderByIdAsc(Integer status, Pageable pageable);

    /**
     * 批量更新账本事件状态。
     *
     * @param ids         事件ID列表
     * @param status      更新状态
     * @param oldStatus   旧状态
     * @param applyTime   结算时间
     * @return 更新数量
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Modifying
    @Query("""
            UPDATE BillingLedgerEvent e
               SET e.status = :status,
                   e.applyTime = :applyTime
             WHERE e.id IN :ids
               AND e.status = :oldStatus
            """)
    int updateStatus(@Param("ids") List<Long> ids,
                     @Param("status") Integer status,
                     @Param("oldStatus") Integer oldStatus,
                     @Param("applyTime") LocalDateTime applyTime);

    /**
     * 查询账本对账候选。
     *
     * @param since    起始时间
     * @param pageable 分页
     * @return 对账统计
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Query(value = """
            SELECT request_id AS requestId,
                   MIN(tenant_id) AS tenantId,
                   SUM(CASE WHEN event_type = 0 THEN IFNULL(cost_amount, 0) ELSE 0 END) AS reserveTotal,
                   SUM(CASE WHEN event_type IN (1, 2) THEN IFNULL(cost_amount, 0) ELSE 0 END) AS closeTotal,
                   MAX(create_time) AS latestTime
              FROM t_lingsql_engine_billing_ledger
             WHERE create_time >= :since
             GROUP BY tenant_id, request_id
            HAVING SUM(CASE WHEN event_type = 0 THEN IFNULL(cost_amount, 0) ELSE 0 END) > 0
             ORDER BY latestTime DESC
            """, nativeQuery = true)
    List<LedgerReconcileProjection> findReconcileCandidates(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * 统计指定时间后的账本事件数量。
     *
     * @param since 起始时间
     * @return 账本事件数量
     * @author 余炘洋
     * @date 2026-02-27
     */
    long countByCreateTimeAfter(LocalDateTime since);

    /**
     * 统计指定时间后、指定状态的账本事件数量。
     *
     * @param since  起始时间
     * @param status 事件状态
     * @return 账本事件数量
     * @author 余炘洋
     * @date 2026-02-27
     */
    long countByCreateTimeAfterAndStatus(LocalDateTime since, Integer status);

    /**
     * 统计指定状态且超过截止时间的账本事件。
     *
     * @param status 事件状态
     * @param createTime 截止时间
     * @return 事件数量
     */
    long countByStatusAndCreateTimeBefore(Integer status, LocalDateTime createTime);

    /**
     * 汇总指定租户和请求集合的账本验收证据。
     *
     * @param tenantId 租户ID
     * @param requestIds 请求ID集合
     * @return 验收投影
     */
    @Query(value = """
            SELECT COUNT(*) AS eventCount,
                   COUNT(DISTINCT CASE
                       WHEN workflow_request_id IN (:requestIds) THEN workflow_request_id
                       ELSE request_id
                   END) AS matchedRequestCount,
                   SUM(CASE WHEN event_type = 0 THEN 1 ELSE 0 END) AS reserveCount,
                   SUM(CASE WHEN event_type = 2 THEN 1 ELSE 0 END) AS closeCount,
                   SUM(CASE WHEN event_type = 0 THEN IFNULL(cost_amount, 0) ELSE 0 END) AS reserveAmount
             FROM t_lingsql_engine_billing_ledger
             WHERE tenant_id = :tenantId
               AND (request_id IN (:requestIds) OR workflow_request_id IN (:requestIds))
            """, nativeQuery = true)
    LedgerEvidenceProjection findLedgerEvidence(@Param("tenantId") String tenantId,
                                                 @Param("requestIds") List<String> requestIds);

    /**
     * 按工作流请求汇总逐物理调用账本守恒证据。
     *
     * @param tenantId 租户ID
     * @param requestIds 工作流请求ID
     * @return 逐请求账本证据
     */
    @Query(value = """
            SELECT workflowRequestId,
                   COUNT(*) AS ledgerInvocationCount,
                   SUM(eventCount) AS eventCount,
                   SUM(reserveCount) AS reserveCount,
                   SUM(refundCount) AS refundCount,
                   SUM(closeCount) AS closeCount,
                   SUM(reserveAmount) AS reserveAmount,
                   SUM(refundAmount) AS refundAmount,
                   SUM(closeAmount) AS closeAmount,
                   SUM(CASE
                       WHEN reserveCount = 1
                        AND closeCount = 1
                        AND refundCount IN (0, 1)
                        AND reserveAmount = refundAmount + closeAmount
                       THEN 1 ELSE 0
                   END) AS conservedInvocationCount,
                   SUM(CASE
                       WHEN reserveCount <> 1
                         OR closeCount <> 1
                         OR refundCount NOT IN (0, 1)
                         OR reserveAmount <> refundAmount + closeAmount
                       THEN 1 ELSE 0
                   END) AS unbalancedInvocationCount
              FROM (
                    SELECT workflow_request_id AS workflowRequestId,
                           invocation_id AS invocationId,
                           COUNT(*) AS eventCount,
                           SUM(CASE WHEN event_name = 'RESERVE' THEN 1 ELSE 0 END) AS reserveCount,
                           SUM(CASE WHEN event_name = 'REFUND' THEN 1 ELSE 0 END) AS refundCount,
                           SUM(CASE WHEN event_name = 'CLOSE' THEN 1 ELSE 0 END) AS closeCount,
                           SUM(CASE WHEN event_name = 'RESERVE' THEN IFNULL(cost_amount, 0) ELSE 0 END)
                               AS reserveAmount,
                           SUM(CASE WHEN event_name = 'REFUND' THEN IFNULL(cost_amount, 0) ELSE 0 END)
                               AS refundAmount,
                           SUM(CASE WHEN event_name = 'CLOSE' THEN IFNULL(cost_amount, 0) ELSE 0 END)
                               AS closeAmount
                      FROM t_lingsql_engine_billing_ledger
                     WHERE tenant_id = :tenantId
                       AND workflow_request_id IN (:requestIds)
                       AND invocation_id IS NOT NULL
                     GROUP BY workflow_request_id, invocation_id
                   ) invocationLedger
             GROUP BY workflowRequestId
            """, nativeQuery = true)
    List<RequestLedgerEvidenceProjection> findRequestLedgerEvidence(
            @Param("tenantId") String tenantId,
            @Param("requestIds") List<String> requestIds
    );

    /**
     * 账本对账投影。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    interface LedgerReconcileProjection {
        String getRequestId();

        String getTenantId();

        Long getReserveTotal();

        Long getCloseTotal();

        LocalDateTime getLatestTime();
    }

    interface LedgerEvidenceProjection {
        Long getEventCount();

        Long getMatchedRequestCount();

        Long getReserveCount();

        Long getCloseCount();

        Long getReserveAmount();
    }

    interface RequestLedgerEvidenceProjection {
        String getWorkflowRequestId();

        Long getLedgerInvocationCount();

        Long getEventCount();

        Long getReserveCount();

        Long getRefundCount();

        Long getCloseCount();

        Long getReserveAmount();

        Long getRefundAmount();

        Long getCloseAmount();

        Long getConservedInvocationCount();

        Long getUnbalancedInvocationCount();
    }
}
