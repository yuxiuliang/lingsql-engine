package com.lingsql.audit.service;

import com.lingsql.audit.entity.AuditLog;
import com.lingsql.audit.model.AuditLogCommand;
import com.lingsql.audit.model.AuditLogQuery;
import com.lingsql.audit.model.AuditLogView;
import com.lingsql.audit.repository.AuditLogRepository;
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
 * 管理操作审计日志服务。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;

    /**
     * 写入审计日志。
     *
     * @param command 写入请求
     * @author 余炘洋
     * @date 2026-01-26
     */
    public void record(AuditLogCommand command) {
        if (command == null) {
            return;
        }
        try {
            AuditLog logEntity = new AuditLog();
            logEntity.setTenantId(command.getTenantId());
            logEntity.setUserId(command.getUserId());
            logEntity.setUsername(command.getUsername());
            logEntity.setAction(command.getAction());
            logEntity.setResource(command.getResource());
            logEntity.setDetail(command.getDetail());
            logEntity.setSuccess(command.isSuccess() ? 1 : 0);
            logEntity.setErrorMessage(command.getErrorMessage());
            logEntity.setRequestIp(command.getRequestIp());
            logEntity.setCreateTime(LocalDateTime.now());
            repository.save(logEntity);
        } catch (Exception ex) {
            log.warn("写入审计日志失败: {}", ex.getMessage());
        }
    }

    /**
     * 查询审计日志。
     *
     * @param query 查询条件
     * @param page  页码
     * @param size  每页大小
     * @return 分页结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    public PageResult<AuditLogView> query(AuditLogQuery query, int page, int size) {
        AuditLogQuery safeQuery = query == null ? new AuditLogQuery() : query;
        Specification<AuditLog> specification = (root, criteriaQuery, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();
            if (StringUtils.hasText(safeQuery.getTenantId())) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("tenantId"), safeQuery.getTenantId()));
            }
            if (safeQuery.getUserId() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("userId"), safeQuery.getUserId()));
            }
            if (StringUtils.hasText(safeQuery.getAction())) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.like(root.get("action"), "%" + safeQuery.getAction().trim() + "%"));
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
        Page<AuditLog> result = repository.findAll(specification, pageable);
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
    private AuditLogView toView(AuditLog logEntity) {
        AuditLogView view = new AuditLogView();
        view.setId(logEntity.getId());
        view.setTenantId(logEntity.getTenantId());
        view.setUserId(logEntity.getUserId());
        view.setUsername(logEntity.getUsername());
        view.setAction(logEntity.getAction());
        view.setResource(logEntity.getResource());
        view.setDetail(logEntity.getDetail());
        view.setSuccess(logEntity.getSuccess());
        view.setErrorMessage(logEntity.getErrorMessage());
        view.setRequestIp(logEntity.getRequestIp());
        view.setCreateTime(logEntity.getCreateTime());
        return view;
    }
}
