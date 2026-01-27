package com.lingsql.common.trace;

import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 请求链路追踪上下文。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
public final class RequestTraceContext {

    private static final ThreadLocal<RequestTrace> CONTEXT = new ThreadLocal<>();

    private RequestTraceContext() {
    }

    /**
     * 写入追踪信息。
     *
     * @param trace 追踪信息
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static void setTrace(RequestTrace trace) {
        if (trace == null) {
            return;
        }
        CONTEXT.set(trace);
    }

    /**
     * 获取 RequestId。
     *
     * @return RequestId
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static String getRequestId() {
        RequestTrace trace = CONTEXT.get();
        return trace == null ? null : trace.requestId();
    }

    /**
     * 获取 TraceId。
     *
     * @return TraceId
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static String getTraceId() {
        RequestTrace trace = CONTEXT.get();
        return trace == null ? null : trace.traceId();
    }

    /**
     * 解析 RequestId，若不存在则生成。
     *
     * @return RequestId
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static String resolveRequestId() {
        String requestId = getRequestId();
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        return generateId();
    }

    /**
     * 清理上下文。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static void clear() {
        CONTEXT.remove();
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
