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
                   SUM(CASE WHEN event_type = 2 THEN IFNULL(cost_amount, 0) ELSE 0 END) AS closeTotal,
                   MAX(create_time) AS latestTime
              FROM t_lingsql_engine_billing_ledger
             WHERE create_time >= :since
             GROUP BY request_id
            HAVING SUM(CASE WHEN event_type = 2 THEN IFNULL(cost_amount, 0) ELSE 0 END) > 0
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
}
