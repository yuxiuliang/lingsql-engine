package com.lingsql.context;

import lombok.extern.slf4j.Slf4j;

/**
 * 会话上下文管理器，使用 ThreadLocal 保存当前请求的租户与用户信息。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
public final class TenantContext {

    /**
     * 保存会话信息的 ThreadLocal。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private static final ThreadLocal<SessionInfo> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<String> CONVERSATION_CONTEXT = new ThreadLocal<>();

    /**
     * 私有构造函数。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private TenantContext() {
    }

    /**
     * 设置当前租户 ID。
     *
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static void setTenantId(String tenantId) {
        log.debug("设置当前租户上下文: {}", tenantId);
        SessionInfo current = CONTEXT.get();
        Long userId = current == null ? null : current.userId();
        CONTEXT.set(new SessionInfo(tenantId, userId));
    }

    /**
     * 获取当前租户 ID。
     *
     * @return 租户标识
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static String getTenantId() {
        SessionInfo info = CONTEXT.get();
        return info == null ? null : info.tenantId();
    }

    /**
     * 设置当前会话信息。
     *
     * @param sessionInfo 会话信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    public static void setSessionInfo(SessionInfo sessionInfo) {
        if (sessionInfo == null) {
            return;
        }
        log.debug("设置当前会话上下文: tenantId={}, userId={}", sessionInfo.tenantId(), sessionInfo.userId());
        CONTEXT.set(sessionInfo);
    }

    /**
     * 获取当前用户 ID。
     *
     * @return 用户 ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    public static Long getUserId() {
        SessionInfo info = CONTEXT.get();
        return info == null ? null : info.userId();
    }

    /**
     * 获取当前会话信息。
     *
     * @return 会话信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    public static SessionInfo getSessionInfo() {
        return CONTEXT.get();
    }

    /**
     * 设置当前聊天会话标识。
     *
     * @param conversationId 会话标识
     * @author 余炘洋
     * @date 2026-04-10
     */
    public static void setConversationId(String conversationId) {
        CONVERSATION_CONTEXT.set(conversationId);
    }

    /**
     * 获取当前聊天会话标识。
     *
     * @return 会话标识
     * @author 余炘洋
     * @date 2026-04-10
     */
    public static String getConversationId() {
        return CONVERSATION_CONTEXT.get();
    }

    /**
     * 清理当前线程上下文。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static void clear() {
        CONTEXT.remove();
        CONVERSATION_CONTEXT.remove();
    }
}
