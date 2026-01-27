package com.lingsql.common.trace;

/**
 * 请求链路追踪信息。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
public record RequestTrace(String requestId, String traceId) {
}
