package com.lingsql.employee.repository;

import com.lingsql.employee.entity.DigitalEmployeeRunLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数字员工运行日志仓库。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
public interface DigitalEmployeeRunLogRepository extends JpaRepository<DigitalEmployeeRunLog, Long>,
        JpaSpecificationExecutor<DigitalEmployeeRunLog> {

    /**
     * 统计窗口内运行总次数。
     *
     * @param tenantId 租户ID
     * @param start    开始时间
     * @param end      结束时间
     * @return 运行次数
     * @author 余炘洋
     * @date 2026-02-28
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM t_lingsql_engine_digital_employee_run_log
            WHERE create_time BETWEEN :start AND :end
              AND (:tenantId IS NULL OR :tenantId = '' OR tenant_id = :tenantId)
            """, nativeQuery = true)
    long countInWindow(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 统计窗口内运行成功次数。
     *
     * @param tenantId 租户ID
     * @param start    开始时间
     * @param end      结束时间
     * @return 成功次数
     * @author 余炘洋
     * @date 2026-02-28
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM t_lingsql_engine_digital_employee_run_log
            WHERE create_time BETWEEN :start AND :end
              AND success = 1
              AND (:tenantId IS NULL OR :tenantId = '' OR tenant_id = :tenantId)
            """, nativeQuery = true)
    long countSuccessInWindow(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 查询窗口内运行次数 TopN 员工。
     *
     * @param tenantId 租户ID
     * @param start    开始时间
     * @param end      结束时间
     * @param limit    限制数量
     * @return TopN 统计
     * @author 余炘洋
     * @date 2026-02-28
     */
    @Query(value = """
            SELECT employee_id AS employeeId,
                   COUNT(*) AS runCount,
                   SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount
            FROM t_lingsql_engine_digital_employee_run_log
            WHERE create_time BETWEEN :start AND :end
              AND (:tenantId IS NULL OR :tenantId = '' OR tenant_id = :tenantId)
            GROUP BY employee_id
            ORDER BY runCount DESC, successCount DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopRunProjection> findTopRunEmployees(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit
    );

    /**
     * Top 员工投影。
     *
     * @author 余炘洋
     * @date 2026-02-28
     */
    interface TopRunProjection {
        Long getEmployeeId();

        Long getRunCount();

        Long getSuccessCount();
    }
}

