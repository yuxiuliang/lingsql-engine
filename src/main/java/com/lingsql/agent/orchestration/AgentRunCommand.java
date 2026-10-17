package com.lingsql.agent.orchestration;

/**
 * 受控 Agent 单次运行命令。
 *
 * <p>显式携带执行上下文，避免编排模块在虚拟线程或后续异步执行中依赖可变上下文。</p>
 *
 * @param tenantId 租户标识
 * @param userId 用户标识
 * @param conversationId 会话标识
 * @param question 用户问题
 * @param requestSource 请求来源
 * @param requestId 请求链路标识
 * @param requireHumanApproval 是否需要人工审批后再执行
 * @author 余炘洋
 * @date 2026-08-15
 */
public record AgentRunCommand(
        String tenantId,
        Long userId,
        String conversationId,
        String question,
        String requestSource,
        String requestId,
        boolean requireHumanApproval
) {

    /**
     * 兼容构造：默认不要求人工审批。
     *
     * @param tenantId 租户标识
     * @param userId 用户标识
     * @param conversationId 会话标识
     * @param question 用户问题
     * @param requestSource 请求来源
     * @param requestId 请求链路标识
     * @author 余炘洋
     * @date 2026-10-17
     */
    public AgentRunCommand(String tenantId, Long userId, String conversationId, String question,
                           String requestSource, String requestId) {
        this(tenantId, userId, conversationId, question, requestSource, requestId, false);
    }
}
