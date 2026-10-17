package com.lingsql.agent.memory;

/**
 * 记忆状态：纠正生成新版本并使旧事实失效，撤销用于管理端与用户清除。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
public enum MemoryStatus {

    /**
     * 生效。
     */
    ACTIVE("生效"),

    /**
     * 已被新版本取代。
     */
    SUPERSEDED("已被新版本取代"),

    /**
     * 已撤销。
     */
    REVOKED("已撤销");

    private final String label;

    MemoryStatus(String label) {
        this.label = label;
    }

    /**
     * 状态中文名。
     *
     * @return 中文名
     */
    public String label() {
        return label;
    }

    /**
     * 解析状态编码，未知编码返回空。
     *
     * @param code 状态编码
     * @return 记忆状态
     */
    public static MemoryStatus of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MemoryStatus status : values()) {
            if (status.name().equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
