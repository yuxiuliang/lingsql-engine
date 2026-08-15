package com.lingsql.audit.service;

import com.lingsql.audit.model.AuditLogCommand;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 管理操作统一执行器，负责审计与异常包装。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditOperationExecutor {

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private final ObjectMapper objectMapper;
    private final AuditDeliveryService auditDeliveryService;
    private final AuditDeliveryPublisher auditDeliveryPublisher;

    public <T> ApiResponse<T> execute(String action, String resource, Object request, HttpServletRequest http, Supplier<T> supplier) {
        return executeInternal(action, resource, request, http, supplier, result -> request);
    }

    public <T> ApiResponse<T> execute(String action,
                                      String resource,
                                      Object request,
                                      HttpServletRequest http,
                                      Supplier<T> supplier,
                                      Function<T, Object> successAuditDetailMapper) {
        Function<T, Object> mapper = successAuditDetailMapper == null ? result -> request : successAuditDetailMapper;
        return executeInternal(action, resource, request, http, supplier, mapper);
    }

    public ApiResponse<String> executeVoid(String action, String resource, Object request, HttpServletRequest http, Runnable runnable, String successMessage) {
        return executeInternal(action, resource, request, http, () -> {
            runnable.run();
            return successMessage;
        }, result -> request);
    }

    private <T> ApiResponse<T> executeInternal(String action,
                                               String resource,
                                               Object request,
                                               HttpServletRequest http,
                                               Supplier<T> supplier,
                                               Function<T, Object> successAuditDetailMapper) {
        AuditLogCommand command = buildCommand(action, resource, request, http);
        String eventId = auditDeliveryService.prepare(command);
        T result;
        try {
            result = supplier.get();
        } catch (BizException ex) {
            finalizeFailure(eventId, request, ex);
            throw ex;
        } catch (AuditPersistenceException ex) {
            throw ex;
        } catch (Exception ex) {
            finalizeFailure(eventId, request, ex);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "系统异常，请稍后再试", ex);
        }
        Object auditDetail = resolveSuccessDetail(successAuditDetailMapper, result, request, eventId);
        try {
            finalizeSuccess(eventId, auditDetail);
        } catch (AuditPersistenceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuditPersistenceException("成功操作的审计结果无法持久化，需要人工核对", ex);
        }
        return ApiResponse.success(result);
    }

    private AuditLogCommand buildCommand(String action,
                                         String resource,
                                         Object request,
                                         HttpServletRequest http) {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            throw new AuditPersistenceException("缺少审计操作人上下文，已拒绝执行管理操作", null);
        }
        AuditLogCommand command = new AuditLogCommand();
        command.setTenantId(session.getTenantId());
        command.setUserId(session.getUserId());
        command.setRealUserId(session.getUserId());
        command.setUsername(session.getUsername());
        command.setAction(action);
        command.setResource(resource);
        command.setDetail(serialize(request));
        command.setRequestIp(resolveIp(http));
        command.setUserAgent(resolveUserAgent(http));
        return command;
    }

    private <T> Object resolveSuccessDetail(Function<T, Object> mapper,
                                            T result,
                                            Object fallback,
                                            String eventId) {
        try {
            return mapper.apply(result);
        } catch (Exception ex) {
            log.error("成功审计详情映射失败，使用原始请求: eventId={}", eventId, ex);
            return fallback;
        }
    }

    private void finalizeSuccess(String eventId, Object detail) {
        auditDeliveryService.finalizeOutcome(eventId, serialize(detail), true, null);
        auditDeliveryPublisher.publishNow(eventId);
    }

    private void finalizeFailure(String eventId, Object detail, Exception businessFailure) {
        try {
            auditDeliveryService.finalizeOutcome(eventId, serialize(detail), false, businessFailure.getMessage());
            auditDeliveryPublisher.publishNow(eventId);
        } catch (Exception auditFailure) {
            businessFailure.addSuppressed(auditFailure);
            log.error("失败操作的审计结果持久化异常: eventId={}", eventId, auditFailure);
        }
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("审计详情序列化失败: {}", ex.getMessage());
            return String.valueOf(value);
        }
    }

    private String resolveIp(HttpServletRequest http) {
        if (http == null) {
            return null;
        }
        String forwarded = http.getHeader(HEADER_X_FORWARDED_FOR);
        if (StringUtils.hasText(forwarded)) {
            String[] parts = forwarded.split(",");
            if (parts.length > 0 && StringUtils.hasText(parts[0])) {
                return parts[0].trim();
            }
        }
        String realIp = http.getHeader(HEADER_X_REAL_IP);
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return http.getRemoteAddr();
    }

    private String resolveUserAgent(HttpServletRequest http) {
        if (http == null) {
            return null;
        }
        return http.getHeader("User-Agent");
    }
}
