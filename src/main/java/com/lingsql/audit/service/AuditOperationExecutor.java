package com.lingsql.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public <T> ApiResponse<T> execute(String action, String resource, Object request, HttpServletRequest http, Supplier<T> supplier) {
        try {
            T result = supplier.get();
            recordAudit(action, resource, request, true, null, http);
            return ApiResponse.success(result);
        } catch (BizException ex) {
            recordAudit(action, resource, request, false, ex.getMessage(), http);
            throw ex;
        } catch (Exception ex) {
            recordAudit(action, resource, request, false, ex.getMessage(), http);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "系统异常，请稍后再试", ex);
        }
    }

    public ApiResponse<String> executeVoid(String action, String resource, Object request, HttpServletRequest http, Runnable runnable, String successMessage) {
        try {
            runnable.run();
            recordAudit(action, resource, request, true, null, http);
            return ApiResponse.success(successMessage);
        } catch (BizException ex) {
            recordAudit(action, resource, request, false, ex.getMessage(), http);
            throw ex;
        } catch (Exception ex) {
            recordAudit(action, resource, request, false, ex.getMessage(), http);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "系统异常，请稍后再试", ex);
        }
    }

    private void recordAudit(String action, String resource, Object request, boolean success, String error, HttpServletRequest http) {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            return;
        }
        AuditLogCommand command = new AuditLogCommand();
        command.setTenantId(session.getTenantId());
        command.setUserId(session.getUserId());
        command.setRealUserId(session.getUserId());
        command.setUsername(session.getUsername());
        command.setAction(action);
        command.setResource(resource);
        command.setDetail(serialize(request));
        command.setSuccess(success);
        command.setErrorMessage(error);
        command.setRequestIp(resolveIp(http));
        command.setUserAgent(resolveUserAgent(http));
        auditLogService.record(command);
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
