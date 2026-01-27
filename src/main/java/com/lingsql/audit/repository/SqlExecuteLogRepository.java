package com.lingsql.audit.repository;

import com.lingsql.audit.entity.SqlExecuteLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SQL 执行日志仓库。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
public interface SqlExecuteLogRepository extends JpaRepository<SqlExecuteLog, Long>, JpaSpecificationExecutor<SqlExecuteLog> {

    /**
     * 统计租户在时间范围内的调用次数。
     *
     * @param tenantId 租户ID
     * @param start    开始时间
     * @param end      结束时间
     * @return 次数
     * @author 余炘洋
     * @date 2026-01-27
     */
    long countByTenantIdAndCreateTimeBetween(String tenantId, LocalDateTime start, LocalDateTime end);

    /**
     * 统计算力消耗最高的问题。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @param limit 限制数量
     * @return 统计列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Query(value = """
            SELECT question AS question,
                   SUM(IFNULL(token_count, 0)) AS totalTokens,
                   SUM(IFNULL(cost_amount, 0)) AS totalCost,
                   COUNT(*) AS totalCount
            FROM t_lingsql_engine_sql_execute_log
            WHERE question IS NOT NULL
              AND question <> ''
              AND create_time BETWEEN :start AND :end
            GROUP BY question
            ORDER BY SUM(IFNULL(token_count, 0)) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopQuestionProjection> findTopQuestions(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit
    );

    /**
     * 查询准确率趋势。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 统计列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Query(value = """
            SELECT DATE(create_time) AS statDate,
                   SUM(CASE WHEN rating >= 4 THEN 1 ELSE 0 END) AS positiveCount,
                   COUNT(*) AS totalCount
            FROM t_lingsql_engine_sql_execute_log
            WHERE rating IS NOT NULL
              AND create_time BETWEEN :start AND :end
            GROUP BY DATE(create_time)
            ORDER BY DATE(create_time)
            """, nativeQuery = true)
    List<AccuracyTrendProjection> findAccuracyTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 查询模型成本对比。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 统计列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Query(value = """
            SELECT model_name AS modelName,
                   SUM(IFNULL(token_count, 0)) AS totalTokens,
                   SUM(IFNULL(cost_amount, 0)) AS totalCost,
                   COUNT(*) AS totalCount
            FROM t_lingsql_engine_sql_execute_log
            WHERE model_name IS NOT NULL
              AND model_name <> ''
              AND create_time BETWEEN :start AND :end
            GROUP BY model_name
            ORDER BY SUM(IFNULL(cost_amount, 0)) DESC
            """, nativeQuery = true)
    List<ModelCostProjection> findModelCostStats(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Top 问题投影。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    interface TopQuestionProjection {
        String getQuestion();

        Long getTotalTokens();

        Long getTotalCost();

        Long getTotalCount();
    }

    /**
     * 准确率趋势投影。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    interface AccuracyTrendProjection {
        java.sql.Date getStatDate();

        Long getPositiveCount();

        Long getTotalCount();
    }

    /**
     * 模型成本投影。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    interface ModelCostProjection {
        String getModelName();

        Long getTotalTokens();

        Long getTotalCost();

        Long getTotalCount();
    }
}
