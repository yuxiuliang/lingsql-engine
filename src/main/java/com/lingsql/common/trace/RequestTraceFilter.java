package com.lingsql.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求链路追踪过滤器，负责生成 requestId/traceId 并注入日志上下文。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_TENANT_ID = "tenantId";
    public static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveHeader(request, HEADER_REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = generateId();
        }
        String traceId = resolveHeader(request, HEADER_TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            traceId = requestId;
        }
        RequestTraceContext.setTrace(new RequestTrace(requestId, traceId));
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        response.setHeader(HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestTraceContext.clear();
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    private String resolveHeader(HttpServletRequest request, String headerName) {
        String header = request.getHeader(headerName);
        if (!StringUtils.hasText(header)) {
            return null;
        }
        return header.trim();
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
