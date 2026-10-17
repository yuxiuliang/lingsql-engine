package com.lingsql.agent.memory;

/**
 * 记忆来源：决定置信度并限制写入通道。
 *
 * <p>模型不能自行决定永久保存对话内容，因此 {@link #MODEL_CANDIDATE} 当前不允许写入，
 * 待候选提取、事实验证与用户授权链路落地后再开放。</p>
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
public enum MemorySourceType {

    /**
     * 用户确认：用户通过接口或会话显式声明的事实。
     */
    USER_CONFIRMED("用户确认", 90, true),

    /**
     * 管理端批准：管理员为该租户或用户写入并批准的事实。
     */
    ADMIN_APPROVED("管理端批准", 100, true),

    /**
     * 模型候选：由模型从对话中提取的候选事实，尚未开放写入。
     */
    MODEL_CANDIDATE("模型候选", 0, false);

    private final String label;

    private final int confidence;

    private final boolean writeAllowed;

    MemorySourceType(String label, int confidence, boolean writeAllowed) {
        this.label = label;
        this.confidence = confidence;
        this.writeAllowed = writeAllowed;
    }

    /**
     * 来源中文名。
     *
     * @return 中文名
     */
    public String label() {
        return label;
    }

    /**
     * 默认置信度分值。
     *
     * @return 置信度 0-100
     */
    public int confidence() {
        return confidence;
    }

    /**
     * 是否允许写入。
     *
     * @return 是否允许写入
     */
    public boolean writeAllowed() {
        return writeAllowed;
    }

    /**
     * 解析来源编码，未知编码返回空。
     *
     * @param code 来源编码
     * @return 记忆来源
     */
    public static MemorySourceType of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MemorySourceType sourceType : values()) {
            if (sourceType.name().equalsIgnoreCase(code.trim())) {
                return sourceType;
            }
        }
        return null;
    }
}
