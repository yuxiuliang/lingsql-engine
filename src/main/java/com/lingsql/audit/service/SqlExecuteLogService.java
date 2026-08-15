package com.lingsql.audit.service;

import com.lingsql.audit.entity.SqlExecuteLog;
import com.lingsql.audit.model.TokenAuditDimensionStat;
import com.lingsql.audit.model.TokenAuditOverviewView;
import com.lingsql.audit.model.TokenAuditQuery;
import com.lingsql.audit.model.TokenAuditTrendPoint;
import com.lingsql.audit.model.SqlExecuteLogCommand;
import com.lingsql.audit.model.SqlExecuteLogQuery;
import com.lingsql.audit.model.SqlExecuteLogView;
import com.lingsql.audit.repository.SqlExecuteLogRepository;
import com.lingsql.common.result.PageResult;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.pagination.PageParam;
import com.lingsql.common.pagination.PageParamResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * SQL 执行审计日志服务。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecuteLogService {

    /**
     * 慢查询阈值(毫秒)。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private static final long SLOW_EXECUTE_THRESHOLD_MS = 1000L;
    private static final int DEFAULT_TOKEN_AUDIT_DAYS = 30;
    private static final int DEFAULT_TOP_N = 10;
    private static final int MAX_TOP_N = 50;

    private final SqlExecuteLogRepository repository;
    private final PageParamResolver pageParamResolver;

    /**
     * 写入 SQL 执行日志。
     *
     * @param command 写入请求
     * @author 余炘洋
     * @date 2026-01-26
     */
    public void record(SqlExecuteLogCommand command) {
        if (command == null || !StringUtils.hasText(command.getTenantId())) {
            return;
        }
        try {
            SqlExecuteLog logEntity = new SqlExecuteLog();
            logEntity.setRequestId(command.getRequestId());
            logEntity.setTenantId(command.getTenantId());
            logEntity.setUserId(command.getUserId());
            logEntity.setQuestion(command.getQuestion());
            logEntity.setAiSql(command.getAiSql());
            logEntity.setExecutedSql(command.getExecutedSql());
            logEntity.setSuccess(command.isSuccess() ? 1 : 0);
            logEntity.setErrorMessage(command.getErrorMessage());
            logEntity.setModelProvider(command.getModelProvider());
            logEntity.setModelName(command.getModelName());
            logEntity.setModelVersion(command.getModelVersion());
            logEntity.setTokenCount(command.getTokenCount());
            logEntity.setCostAmount(command.getCostAmount());
            logEntity.setBillingVersion(command.getBillingVersion());
            logEntity.setExecuteCostMs(command.getExecuteCostMs());
            logEntity.setResultRows(command.getResultRows());
            logEntity.setRequestSource(command.getRequestSource());
            logEntity.setRating(command.getRating());
            logEntity.setComment(command.getComment());
            logEntity.setCreateTime(LocalDateTime.now());
            repository.save(logEntity);
            triggerSlowExecuteAlert(logEntity);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "SQL 执行审计日志写入失败", ex);
        }
    }

    /**
     * 查询 SQL 执行日志。
     *
     * @param query 查询条件
     * @param page  页码
     * @param size  每页大小
     * @return 分页结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    public PageResult<SqlExecuteLogView> query(SqlExecuteLogQuery query, int page, int size) {
        PageParam param = pageParamResolver.resolve(page, size);
        return queryInternal(query, param);
    }

    /**
     * 查询 SQL 执行日志（导出用途）。
     *
     * @param query 查询条件
     * @param page  页码
     * @param size  每页大小
     * @return 分页结果
     * @author 余炘洋
     * @date 2026-01-28
     */
    public PageResult<SqlExecuteLogView> queryForExport(SqlExecuteLogQuery query, int page, int size) {
        PageParam param = pageParamResolver.resolveExport(page, size);
        return queryInternal(query, param);
    }

    /**
     * 查询 Token 审计总览。
     *
     * @param query              查询条件
     * @param includeTenantStats 是否返回租户维度排行
     * @return Token 审计总览
     * @author 余炘洋
     * @date 2026-02-27
     */
    public TokenAuditOverviewView tokenOverview(TokenAuditQuery query, boolean includeTenantStats) {
        TokenAuditQuery safeQuery = query == null ? new TokenAuditQuery() : query;
        TimeRange range = resolveTimeRange(safeQuery.getStartTime(), safeQuery.getEndTime());
        int topN = resolveTopN(safeQuery.getTopN());

        String tenantId = normalizeText(safeQuery.getTenantId());
        Long userId = safeQuery.getUserId();
        String question = normalizeText(safeQuery.getQuestion());
        String requestSource = normalizeText(safeQuery.getRequestSource());

        TokenAuditOverviewView view = new TokenAuditOverviewView();
        view.setStartTime(range.start());
        view.setEndTime(range.end());

        SqlExecuteLogRepository.TokenAuditSummaryProjection summary = repository.findTokenAuditSummary(
                tenantId,
                userId,
                question,
                requestSource,
                range.start(),
                range.end()
        );
        applySummary(view, summary);

        List<TokenAuditDimensionStat> modelStats = repository.findTokenAuditModelStats(
                        tenantId,
                        userId,
                        question,
                        requestSource,
                        range.start(),
                        range.end(),
                        topN
                ).stream()
                .map(this::toDimensionStat)
                .toList();
        view.setModelStats(modelStats);

        if (includeTenantStats) {
            List<TokenAuditDimensionStat> tenantStats = repository.findTokenAuditTenantStats(
                            tenantId,
                            userId,
                            question,
                            requestSource,
                            range.start(),
                            range.end(),
                            topN
                    ).stream()
                    .map(this::toDimensionStat)
                    .toList();
            view.setTenantStats(tenantStats);
        } else {
            view.setTenantStats(Collections.emptyList());
        }

        List<TokenAuditTrendPoint> trend = repository.findTokenAuditTrend(
                        tenantId,
                        userId,
                        question,
                        requestSource,
                        range.start(),
                        range.end()
                ).stream()
                .map(this::toTrendPoint)
                .toList();
        view.setTrend(trend);
        return view;
    }

    private PageResult<SqlExecuteLogView> queryInternal(SqlExecuteLogQuery query, PageParam param) {
        SqlExecuteLogQuery safeQuery = query == null ? new SqlExecuteLogQuery() : query;
        Specification<SqlExecuteLog> specification = (root, criteriaQuery, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();
            if (StringUtils.hasText(safeQuery.getTenantId())) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("tenantId"), safeQuery.getTenantId()));
            }
            if (safeQuery.getUserId() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("userId"), safeQuery.getUserId()));
            }
            if (StringUtils.hasText(safeQuery.getRequestSource())) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("requestSource"), safeQuery.getRequestSource()));
            }
            if (StringUtils.hasText(safeQuery.getQuestion())) {
                String keyword = "%" + safeQuery.getQuestion().trim() + "%";
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.like(root.get("question"), keyword));
            }
            if (safeQuery.getStartTime() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("createTime"), safeQuery.getStartTime()));
            }
            if (safeQuery.getEndTime() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThanOrEqualTo(root.get("createTime"), safeQuery.getEndTime()));
            }
            return predicate;
        };
        PageRequest pageable = PageRequest.of(Math.max(param.page() - 1, 0), param.size(), Sort.by(Sort.Direction.DESC, "createTime"));
        Page<SqlExecuteLog> result = repository.findAll(specification, pageable);
        return new PageResult<>(
                result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(),
                param.page(),
                param.size()
        );
    }

    private void applySummary(TokenAuditOverviewView view, SqlExecuteLogRepository.TokenAuditSummaryProjection summary) {
        long totalCount = summary == null ? 0L : resolveLong(summary.getTotalCount());
        long successCount = summary == null ? 0L : resolveLong(summary.getSuccessCount());
        long failureCount = summary == null ? 0L : resolveLong(summary.getFailureCount());
        long totalTokens = summary == null ? 0L : resolveLong(summary.getTotalTokens());
        long totalCost = summary == null ? 0L : resolveLong(summary.getTotalCost());

        view.setTotalCount(totalCount);
        view.setSuccessCount(successCount);
        view.setFailureCount(failureCount);
        view.setSuccessRate(calculateRate(successCount, totalCount));
        view.setTotalTokens(totalTokens);
        view.setTotalCost(totalCost);

        if (summary == null) {
            view.setAvgTokensPerRequest(0d);
            view.setAvgCostPerRequest(0d);
            return;
        }
        view.setAvgTokensPerRequest(round2(resolveDouble(summary.getAvgTokensPerRequest())));
        view.setAvgCostPerRequest(round2(resolveDouble(summary.getAvgCostPerRequest())));
    }

    private TokenAuditDimensionStat toDimensionStat(SqlExecuteLogRepository.TokenAuditDimensionProjection projection) {
        TokenAuditDimensionStat stat = new TokenAuditDimensionStat();
        long totalCount = resolveLong(projection.getTotalCount());
        long successCount = resolveLong(projection.getSuccessCount());
        long failureCount = resolveLong(projection.getFailureCount());
        long totalTokens = resolveLong(projection.getTotalTokens());
        long totalCost = resolveLong(projection.getTotalCost());

        stat.setDimensionKey(normalizeDimensionKey(projection.getDimensionKey()));
        stat.setTotalCount(totalCount);
        stat.setSuccessCount(successCount);
        stat.setFailureCount(failureCount);
        stat.setSuccessRate(calculateRate(successCount, totalCount));
        stat.setTotalTokens(totalTokens);
        stat.setTotalCost(totalCost);
        stat.setAvgTokensPerRequest(calculateAvg(totalTokens, totalCount));
        stat.setAvgCostPerRequest(calculateAvg(totalCost, totalCount));
        return stat;
    }

    private TokenAuditTrendPoint toTrendPoint(SqlExecuteLogRepository.TokenAuditTrendProjection projection) {
        TokenAuditTrendPoint point = new TokenAuditTrendPoint();
        long totalCount = resolveLong(projection.getTotalCount());
        long successCount = resolveLong(projection.getSuccessCount());
        long failureCount = resolveLong(projection.getFailureCount());

        point.setStatDate(projection.getStatDate() == null ? null : projection.getStatDate().toLocalDate());
        point.setTotalCount(totalCount);
        point.setSuccessCount(successCount);
        point.setFailureCount(failureCount);
        point.setSuccessRate(calculateRate(successCount, totalCount));
        point.setTotalTokens(resolveLong(projection.getTotalTokens()));
        point.setTotalCost(resolveLong(projection.getTotalCost()));
        return point;
    }

    private TimeRange resolveTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime safeEnd = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime safeStart = startTime == null ? safeEnd.minusDays(DEFAULT_TOKEN_AUDIT_DAYS) : startTime;
        if (safeStart.isAfter(safeEnd)) {
            return new TimeRange(safeEnd, safeStart);
        }
        return new TimeRange(safeStart, safeEnd);
    }

    private int resolveTopN(Integer topN) {
        if (topN == null || topN <= 0) {
            return DEFAULT_TOP_N;
        }
        return Math.min(topN, MAX_TOP_N);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeDimensionKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "UNKNOWN";
        }
        return value.trim();
    }

    private long resolveLong(Long value) {
        return value == null ? 0L : value;
    }

    private double resolveDouble(Double value) {
        return value == null ? 0d : value;
    }

    private double calculateRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return round2((numerator * 100d) / denominator);
    }

    private double calculateAvg(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return round2(numerator * 1.0d / denominator);
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * 转换为视图对象。
     *
     * @param logEntity 实体
     * @return 视图对象
     * @author 余炘洋
     * @date 2026-01-26
     */
    private SqlExecuteLogView toView(SqlExecuteLog logEntity) {
        SqlExecuteLogView view = new SqlExecuteLogView();
        view.setId(logEntity.getId());
        view.setRequestId(logEntity.getRequestId());
        view.setTenantId(logEntity.getTenantId());
        view.setUserId(logEntity.getUserId());
        view.setQuestion(logEntity.getQuestion());
        view.setAiSql(logEntity.getAiSql());
        view.setExecutedSql(logEntity.getExecutedSql());
        view.setSuccess(logEntity.getSuccess());
        view.setErrorMessage(logEntity.getErrorMessage());
        view.setModelProvider(logEntity.getModelProvider());
        view.setModelName(logEntity.getModelName());
        view.setModelVersion(logEntity.getModelVersion());
        view.setTokenCount(logEntity.getTokenCount());
        view.setCostAmount(logEntity.getCostAmount());
        view.setBillingVersion(logEntity.getBillingVersion());
        view.setExecuteCostMs(logEntity.getExecuteCostMs());
        view.setResultRows(logEntity.getResultRows());
        view.setRequestSource(logEntity.getRequestSource());
        view.setRating(logEntity.getRating());
        view.setComment(logEntity.getComment());
        view.setSlow(isSlow(logEntity.getExecuteCostMs()));
        view.setCreateTime(logEntity.getCreateTime());
        return view;
    }

    /**
     * 判断是否慢查询。
     *
     * @param executeCostMs 执行耗时
     * @return 是否慢查询
     * @author 余炘洋
     * @date 2026-01-27
     */
    private boolean isSlow(Long executeCostMs) {
        return executeCostMs != null && executeCostMs > SLOW_EXECUTE_THRESHOLD_MS;
    }

    /**
     * 触发慢查询告警。
     *
     * @param logEntity 日志实体
     * @author 余炘洋
     * @date 2026-01-27
     */
    private void triggerSlowExecuteAlert(SqlExecuteLog logEntity) {
        if (logEntity == null || !isSlow(logEntity.getExecuteCostMs())) {
            return;
        }
        log.warn("SQL 执行耗时超过阈值: id={}, tenantId={}, userId={}, costMs={}",
                logEntity.getId(),
                logEntity.getTenantId(),
                logEntity.getUserId(),
                logEntity.getExecuteCostMs());
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end) {
    }
}
