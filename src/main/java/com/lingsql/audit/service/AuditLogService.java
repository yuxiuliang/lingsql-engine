package com.lingsql.audit.service;

import com.lingsql.audit.entity.AuditLog;
import com.lingsql.audit.model.AuditLogCommand;
import com.lingsql.audit.model.AuditLogQuery;
import com.lingsql.audit.model.AuditLogView;
import com.lingsql.audit.repository.AuditLogRepository;
import com.lingsql.common.result.PageResult;
import com.lingsql.common.pagination.PageParam;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.config.LingSqlProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    private final ObjectMapper objectMapper;
    private final LingSqlProperties properties;
    private final PageParamResolver pageParamResolver;

    private final java.util.Queue<RetryItem> retryQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicInteger retrySize = new java.util.concurrent.atomic.AtomicInteger();

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
            persist(command);
        } catch (Exception ex) {
            enqueueRetry(command, ex);
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
        PageParam param = pageParamResolver.resolve(page, size);
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
        PageRequest pageable = PageRequest.of(Math.max(param.page() - 1, 0), param.size(), Sort.by(Sort.Direction.DESC, "createTime"));
        Page<AuditLog> result = repository.findAll(specification, pageable);
        return new PageResult<>(
                result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(),
                param.page(),
                param.size()
        );
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${lingsql.audit.retry-delay-ms:60000}")
    public void retryFailedLogs() {
        LingSqlProperties.Audit audit = properties.getAudit();
        if (audit == null || !audit.isRetryEnabled()) {
            return;
        }
        int batchSize = Math.max(1, audit.getRetryBatchSize());
        for (int i = 0; i < batchSize; i++) {
            RetryItem item = retryQueue.poll();
            if (item == null) {
                return;
            }
            retrySize.decrementAndGet();
            try {
                persist(item.command());
            } catch (Exception ex) {
                handleRetryFailure(item, ex);
            }
        }
    }

    private void persist(AuditLogCommand command) {
        AuditLog logEntity = new AuditLog();
        logEntity.setTenantId(command.getTenantId());
        logEntity.setUserId(command.getUserId());
        logEntity.setRealUserId(resolveRealUserId(command));
        logEntity.setUsername(command.getUsername());
        logEntity.setAction(command.getAction());
        logEntity.setResource(command.getResource());
        logEntity.setDetail(sanitizeDetail(command.getDetail()));
        logEntity.setSummary(resolveSummary(command));
        logEntity.setSuccess(command.isSuccess() ? 1 : 0);
        logEntity.setErrorMessage(command.getErrorMessage());
        logEntity.setRequestIp(command.getRequestIp());
        logEntity.setUserAgent(command.getUserAgent());
        logEntity.setCreateTime(LocalDateTime.now());
        repository.save(logEntity);
    }

    private void enqueueRetry(AuditLogCommand command, Exception ex) {
        LingSqlProperties.Audit audit = properties.getAudit();
        if (audit == null || !audit.isRetryEnabled()) {
            log.error("写入审计日志失败且未启用重试: {}", ex.getMessage(), ex);
            return;
        }
        int maxQueue = audit.getRetryMaxQueue();
        if (maxQueue <= 0) {
            log.error("写入审计日志失败且重试队列容量为 0: {}", ex.getMessage(), ex);
            return;
        }
        int current = retrySize.get();
        if (current >= maxQueue) {
            log.error("审计重试队列已满，丢弃日志: size={}, reason={}", current, ex.getMessage());
            return;
        }
        retryQueue.offer(new RetryItem(command, 1));
        retrySize.incrementAndGet();
        log.warn("写入审计日志失败，已进入重试队列: size={}, reason={}", retrySize.get(), ex.getMessage());
    }

    private void handleRetryFailure(RetryItem item, Exception ex) {
        LingSqlProperties.Audit audit = properties.getAudit();
        if (audit == null) {
            log.error("审计重试失败: {}", ex.getMessage(), ex);
            return;
        }
        int maxAttempts = Math.max(1, audit.getRetryMaxAttempts());
        int nextAttempt = item.attempt() + 1;
        if (nextAttempt > maxAttempts) {
            log.error("审计日志重试超过最大次数，已丢弃: attempts={}, reason={}", item.attempt(), ex.getMessage());
            return;
        }
        retryQueue.offer(new RetryItem(item.command(), nextAttempt));
        retrySize.incrementAndGet();
        log.warn("审计日志重试失败，已重新入队: attempts={}, reason={}", nextAttempt, ex.getMessage());
    }

    private record RetryItem(AuditLogCommand command, int attempt) {
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
        view.setRealUserId(logEntity.getRealUserId());
        view.setUsername(logEntity.getUsername());
        view.setAction(logEntity.getAction());
        view.setResource(logEntity.getResource());
        view.setDetail(logEntity.getDetail());
        view.setSummary(logEntity.getSummary());
        view.setSuccess(logEntity.getSuccess());
        view.setErrorMessage(logEntity.getErrorMessage());
        view.setRequestIp(logEntity.getRequestIp());
        view.setUserAgent(logEntity.getUserAgent());
        view.setCreateTime(logEntity.getCreateTime());
        return view;
    }

    /**
     * 解析真实操作人ID。
     *
     * @param command 写入请求
     * @return 真实操作人ID
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long resolveRealUserId(AuditLogCommand command) {
        if (command == null) {
            return null;
        }
        if (command.getRealUserId() != null) {
            return command.getRealUserId();
        }
        return command.getUserId();
    }

    /**
     * 生成摘要信息。
     *
     * @param command 写入请求
     * @return 摘要
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String resolveSummary(AuditLogCommand command) {
        if (command == null) {
            return null;
        }
        if (StringUtils.hasText(command.getSummary())) {
            return command.getSummary().trim();
        }
        String action = normalize(command.getAction());
        String baseSummary = resolveActionSummary(action);
        String prefix = command.isSuccess() ? "成功" : "失败";
        String summary = prefix + baseSummary;
        String resource = normalize(command.getResource());
        if (StringUtils.hasText(resource) && !isGenericResource(resource)) {
            summary = summary + "，资源=" + resource;
        }
        return summary;
    }

    /**
     * 过滤详情中的敏感字段。
     *
     * @param detail 详情
     * @return 脱敏后的详情
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String sanitizeDetail(String detail) {
        if (!StringUtils.hasText(detail)) {
            return detail;
        }
        try {
            JsonNode root = objectMapper.readTree(detail);
            removePasswordFields(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return detail;
        }
    }

    /**
     * 递归移除 password 字段。
     *
     * @param node JSON 节点
     * @author 余炘洋
     * @date 2026-01-27
     */
    private void removePasswordFields(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject() && node instanceof ObjectNode objectNode) {
            List<String> toRemove = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                if (shouldMaskField(fieldName)) {
                    toRemove.add(fieldName);
                    continue;
                }
                removePasswordFields(entry.getValue());
            }
            if (!toRemove.isEmpty()) {
                objectNode.remove(toRemove);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                removePasswordFields(item);
            }
        }
    }

    /**
     * 判断字段是否需要脱敏。
     *
     * @param fieldName 字段名
     * @return 是否脱敏
     * @author 余炘洋
     * @date 2026-01-27
     */
    private boolean shouldMaskField(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return false;
        }
        return fieldName.trim().toLowerCase().contains("password");
    }

    /**
     * 规范化字符串。
     *
     * @param value 输入
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 判断资源是否为通用类型。
     *
     * @param resource 资源
     * @return 是否通用资源
     * @author 余炘洋
     * @date 2026-01-27
     */
    private boolean isGenericResource(String resource) {
        if (!StringUtils.hasText(resource)) {
            return false;
        }
        String normalized = resource.trim().toUpperCase();
        return "USER".equals(normalized)
                || "ROLE".equals(normalized)
                || "TENANT".equals(normalized)
                || "MODEL".equals(normalized)
                || "MENU".equals(normalized)
                || "TENANT_MENU".equals(normalized)
                || "MENU_RELEASE".equals(normalized)
                || "ROLE_SCOPE_RELEASE".equals(normalized)
                || "USER_SCOPE_RELEASE".equals(normalized)
                || "ORG_TYPE".equals(normalized)
                || "ORG_UNIT".equals(normalized)
                || "ORG_RELATION".equals(normalized);
    }

    /**
     * 解析动作摘要。
     *
     * @param action 动作
     * @return 摘要文本
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String resolveActionSummary(String action) {
        if (!StringUtils.hasText(action)) {
            return "管理操作";
        }
        String normalized = action.trim().toUpperCase();
        return switch (normalized) {
            case "USER_CREATE" -> "创建用户";
            case "USER_UPDATE" -> "更新用户";
            case "USER_PASSWORD_RESET" -> "重置用户密码";
            case "ROLE_CREATE" -> "创建角色";
            case "ROLE_UPDATE" -> "更新角色";
            case "TENANT_CREATE" -> "创建租户";
            case "TENANT_UPDATE" -> "更新租户";
            case "TENANT_DELETE" -> "删除租户";
            case "TENANT_LIFECYCLE_EXTEND" -> "续期租户";
            case "TENANT_LIFECYCLE_REACTIVATE" -> "恢复租户服务";
            case "MODEL_CREATE" -> "创建模型配置";
            case "MODEL_UPDATE" -> "更新模型配置";
            case "MODEL_DELETE" -> "删除模型配置";
            case "TENANT_PERMISSION_UPDATE" -> "更新租户权限池";
            case "ORG_TYPE_CREATE" -> "创建组织类型";
            case "ORG_TYPE_UPDATE" -> "更新组织类型";
            case "ORG_TYPE_DELETE" -> "删除组织类型";
            case "ORG_UNIT_CREATE" -> "创建组织节点";
            case "ORG_UNIT_UPDATE" -> "更新组织节点";
            case "ORG_UNIT_DELETE" -> "删除组织节点";
            case "ORG_RELATION_CREATE" -> "创建组织关系";
            case "ORG_RELATION_UPDATE" -> "更新组织关系";
            case "ORG_RELATION_DELETE" -> "删除组织关系";
            case "MENU_RULE_IMPACT_PREVIEW" -> "预览全局菜单规则影响";
            case "TENANT_MENU_RULE_IMPACT_PREVIEW" -> "预览租户菜单覆盖影响";
            case "MENU_RELEASE_CREATE" -> "创建菜单发布单";
            case "MENU_RELEASE_REVIEW" -> "审核菜单发布单";
            case "MENU_RELEASE_APPLY" -> "应用菜单发布单";
            case "MENU_RELEASE_ROLLBACK_CREATE" -> "生成菜单回滚发布单";
            case "ROLE_SCOPE_RELEASE_CREATE" -> "创建角色数据权限发布单";
            case "ROLE_SCOPE_RELEASE_REVIEW" -> "审核角色数据权限发布单";
            case "ROLE_SCOPE_RELEASE_APPLY" -> "应用角色数据权限发布单";
            case "USER_SCOPE_RELEASE_CREATE" -> "创建用户数据权限发布单";
            case "USER_SCOPE_RELEASE_REVIEW" -> "审核用户数据权限发布单";
            case "USER_SCOPE_RELEASE_APPLY" -> "应用用户数据权限发布单";
            default -> "执行操作(" + normalized + ")";
        };
    }
}
