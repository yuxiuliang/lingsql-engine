package com.lingsql.agent.orchestration;

/**
 * Agent 核心执行上下文。
 *
 * <p>核心链路只接受显式上下文，避免在虚拟线程、异步恢复或后台任务中读取错误的 ThreadLocal 身份。</p>
 *
 * @param executionId Agent 执行标识
 * @param requestId 请求链路标识
 * @param tenantId 租户标识
 * @param userId 用户标识
 * @param conversationId 会话标识
 * @author 余炘洋
 * @date 2026-08-15
 */
public record AgentExecutionContext(
        String executionId,
        String requestId,
        String tenantId,
        Long userId,
        String conversationId
) {
}
