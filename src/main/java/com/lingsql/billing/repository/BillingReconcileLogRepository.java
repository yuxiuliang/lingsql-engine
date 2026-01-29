package com.lingsql.billing.repository;

import com.lingsql.billing.entity.BillingReconcileLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 计费对账日志仓库。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
public interface BillingReconcileLogRepository
        extends JpaRepository<BillingReconcileLog, Long>, JpaSpecificationExecutor<BillingReconcileLog> {

    /**
     * 查询最新对账记录。
     *
     * @param requestId 请求ID
     * @return 日志
     * @author 余炘洋
     * @date 2026-01-29
     */
    Optional<BillingReconcileLog> findTopByRequestIdOrderByCreateTimeDesc(String requestId);

    /**
     * 标记对账日志为已处理。
     *
     * @param id         日志ID
     * @param status     新状态
     * @param oldStatus  旧状态
     * @param resolveNote 处理备注
     * @param resolveUserId 处理人
     * @param resolveTime 处理时间
     * @param updateTime 更新时间
     * @return 更新条数
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Modifying
    @Query("""
            UPDATE BillingReconcileLog l
               SET l.status = :status,
                   l.resolveNote = :resolveNote,
                   l.resolveUserId = :resolveUserId,
                   l.resolveTime = :resolveTime,
                   l.updateTime = :updateTime
             WHERE l.id = :id
               AND l.status = :oldStatus
            """)
    int resolveById(@Param("id") Long id,
                    @Param("status") Integer status,
                    @Param("oldStatus") Integer oldStatus,
                    @Param("resolveNote") String resolveNote,
                    @Param("resolveUserId") Long resolveUserId,
                    @Param("resolveTime") java.time.LocalDateTime resolveTime,
                    @Param("updateTime") java.time.LocalDateTime updateTime);
}
