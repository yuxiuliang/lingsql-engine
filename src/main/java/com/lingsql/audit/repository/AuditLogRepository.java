package com.lingsql.audit.repository;

import com.lingsql.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 审计日志仓库。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    boolean existsByEventId(String eventId);

    /**
     * 统计指定时间之前的管理审计日志数量。
     *
     * @param createTime 截止时间
     * @return 数量
     * @author 余炘洋
     * @date 2026-02-27
     */
    long countByCreateTimeBefore(java.time.LocalDateTime createTime);

    /**
     * 删除指定时间之前的管理审计日志。
     *
     * @param createTime 截止时间
     * @return 删除数量
     * @author 余炘洋
     * @date 2026-02-27
     */
    long deleteByCreateTimeBefore(java.time.LocalDateTime createTime);
}
