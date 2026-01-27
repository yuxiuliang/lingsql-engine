package com.lingsql.audit.service;

import com.lingsql.audit.entity.SqlExecuteLog;
import com.lingsql.audit.model.SqlExecuteLogCommand;
import com.lingsql.audit.model.SqlExecuteLogQuery;
import com.lingsql.audit.model.SqlExecuteLogView;
import com.lingsql.audit.repository.SqlExecuteLogRepository;
import com.lingsql.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

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

    private final SqlExecuteLogRepository repository;

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
            log.warn("写入 SQL 执行日志失败: {}", ex.getMessage());
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
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1), Sort.by(Sort.Direction.DESC, "createTime"));
        Page<SqlExecuteLog> result = repository.findAll(specification, pageable);
        return new PageResult<>(
                result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(),
                page,
                size
        );
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
}
