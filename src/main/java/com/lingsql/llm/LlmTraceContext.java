package com.lingsql.llm;

/**
 * 模型调用追踪上下文。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
public final class LlmTraceContext {

    private static final ThreadLocal<LlmTraceInfo> CONTEXT = new ThreadLocal<>();

    private LlmTraceContext() {
    }

    /**
     * 设置追踪信息。
     *
     * @param info 追踪信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    public static void set(LlmTraceInfo info) {
        if (info == null) {
            return;
        }
        CONTEXT.set(info);
    }

    /**
     * 获取追踪信息。
     *
     * @return 追踪信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    public static LlmTraceInfo get() {
        return CONTEXT.get();
    }

    /**
     * 清理追踪信息。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
