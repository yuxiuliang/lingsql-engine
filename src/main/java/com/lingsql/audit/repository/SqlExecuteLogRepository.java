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
     * 统计租户在时间范围内的 Token 消耗。
     *
     * @param tenantId 租户ID
     * @param start    开始时间
     * @param end      结束时间
     * @return Token 总数
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Query(value = """
            SELECT IFNULL(SUM(IFNULL(token_count, 0)), 0)
            FROM t_lingsql_engine_sql_execute_log
            WHERE tenant_id = :tenantId
              AND create_time BETWEEN :start AND :end
            """, nativeQuery = true)
    long sumTokenCountByTenantIdAndCreateTimeBetween(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 查询 Token 审计汇总。
     *
     * @param tenantId      租户ID
     * @param userId        用户ID
     * @param question      问题关键词
     * @param requestSource 请求来源
     * @param start         开始时间
     * @param end           结束时间
     * @return 汇总结果
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Query(value = """
            SELECT COUNT(*) AS totalCount,
                   SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failureCount,
                   IFNULL(SUM(IFNULL(token_count, 0)), 0) AS totalTokens,
                   IFNULL(SUM(IFNULL(cost_amount, 0)), 0) AS totalCost,
                   IFNULL(ROUND(SUM(IFNULL(token_count, 0)) / NULLIF(COUNT(*), 0), 2), 0) AS avgTokensPerRequest,
                   IFNULL(ROUND(SUM(IFNULL(cost_amount, 0)) / NULLIF(COUNT(*), 0), 2), 0) AS avgCostPerRequest
              FROM t_lingsql_engine_sql_execute_log
             WHERE create_time BETWEEN :start AND :end
               AND (:tenantId IS NULL OR :tenantId = '' OR tenant_id = :tenantId)
               AND (:userId IS NULL OR user_id = :userId)
               AND (:requestSource IS NULL OR :requestSource = '' OR request_source = :requestSource)
               AND (:question IS NULL OR :question = '' OR question LIKE CONCAT('%', :question, '%'))
            """, nativeQuery = true)
    TokenAuditSummaryProjection findTokenAuditSummary(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("question") String question,
            @Param("requestSource") String requestSource,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 查询模型维度 Token 统计。
     *
     * @param tenantId      租户ID
     * @param userId        用户ID
     * @param question      问题关键词
     * @param requestSource 请求来源
     * @param start         开始时间
     * @param end           结束时间
     * @param limit         TopN
     * @return 模型统计
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Query(value = """
            SELECT COALESCE(NULLIF(model_name, ''), 'UNKNOWN') AS dimensionKey,
                   COUNT(*) AS totalCount,
                   SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failureCount,
                   IFNULL(SUM(IFNULL(token_count, 0)), 0) AS totalTokens,
                   IFNULL(SUM(IFNULL(cost_amount, 0)), 0) AS totalCost
              FROM t_lingsql_engine_sql_execute_log
             WHERE create_time BETWEEN :start AND :end
               AND (:tenantId IS NULL OR :tenantId = '' OR tenant_id = :tenantId)
               AND (:userId IS NULL OR user_id = :userId)
               AND (:requestSource IS NULL OR :requestSource = '' OR request_source = :requestSource)
               AND (:question IS NULL OR :question = '' OR question LIKE CONCAT('%', :question, '%'))
             GROUP BY COALESCE(NULLIF(model_name, ''), 'UNKNOWN')
             ORDER BY totalTokens DESC, totalCost DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<TokenAuditDimensionProjection> findTokenAuditModelStats(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("question") String question,
            @Param("requestSource") String requestSource,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit
    );

    /**
     * 查询租户维度 Token 统计。
     *
     * @param tenantId      租户ID
     * @param userId        用户ID
     * @param question      问题关键词
     * @param requestSource 请求来源
     * @param start         开始时间
     * @param end           结束时间
     * @param limit         TopN
     * @return 租户统计
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Query(value = """
            SELECT tenant_id AS dimensionKey,
                   COUNT(*) AS totalCount,
                   SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failureCount,
                   IFNULL(SUM(IFNULL(token_count, 0)), 0) AS totalTokens,
                   IFNULL(SUM(IFNULL(cost_amount, 0)), 0) AS totalCost
              FROM t_lingsql_engine_sql_execute_log
             WHERE create_time BETWEEN :start AND :end
               AND (:tenantId IS NULL OR :tenantId = '' OR tenant_id = :tenantId)
               AND (:userId IS NULL OR user_id = :userId)
               AND (:requestSource IS NULL OR :requestSource = '' OR request_source = :requestSource)
               AND (:question IS NULL OR :question = '' OR question LIKE CONCAT('%', :question, '%'))
             GROUP BY tenant_id
             ORDER BY totalTokens DESC, totalCost DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<TokenAuditDimensionProjection> findTokenAuditTenantStats(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("question") String question,
            @Param("requestSource") String requestSource,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit
    );

    /**
     * 查询 Token 审计趋势。
     *
     * @param tenantId      租户ID
     * @param userId        用户ID
     * @param question      问题关键词
     * @param requestSource 请求来源
     * @param start         开始时间
     * @param end           结束时间
     * @return 趋势列表
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Query(value = """
            SELECT DATE(create_time) AS statDate,
                   COUNT(*) AS totalCount,
                   SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failureCount,
                   IFNULL(SUM(IFNULL(token_count, 0)), 0) AS totalTokens,
                   IFNULL(SUM(IFNULL(cost_amount, 0)), 0) AS totalCost
              FROM t_lingsql_engine_sql_execute_log
             WHERE create_time BETWEEN :start AND :end
               AND (:tenantId IS NULL OR :tenantId = '' OR tenant_id = :tenantId)
               AND (:userId IS NULL OR user_id = :userId)
               AND (:requestSource IS NULL OR :requestSource = '' OR request_source = :requestSource)
               AND (:question IS NULL OR :question = '' OR question LIKE CONCAT('%', :question, '%'))
             GROUP BY DATE(create_time)
             ORDER BY DATE(create_time)
            """, nativeQuery = true)
    List<TokenAuditTrendProjection> findTokenAuditTrend(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("question") String question,
            @Param("requestSource") String requestSource,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

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

    /**
     * Token 审计汇总投影。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    interface TokenAuditSummaryProjection {
        Long getTotalCount();

        Long getSuccessCount();

        Long getFailureCount();

        Long getTotalTokens();

        Long getTotalCost();

        Double getAvgTokensPerRequest();

        Double getAvgCostPerRequest();
    }

    /**
     * Token 审计维度投影。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    interface TokenAuditDimensionProjection {
        String getDimensionKey();

        Long getTotalCount();

        Long getSuccessCount();

        Long getFailureCount();

        Long getTotalTokens();

        Long getTotalCost();
    }

    /**
     * Token 审计趋势投影。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    interface TokenAuditTrendProjection {
        java.sql.Date getStatDate();

        Long getTotalCount();

        Long getSuccessCount();

        Long getFailureCount();

        Long getTotalTokens();

        Long getTotalCost();
    }
}
