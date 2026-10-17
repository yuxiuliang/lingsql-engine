package com.lingsql.agent.memory;

/**
 * 记忆层级：临时记忆由 Redis 会话窗口承担，不进入长期记忆表。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
public enum MemoryTier {

    /**
     * 程序记忆：租户级已验证口径、工作流与术语约定，仅管理端可维护。
     */
    PROCEDURAL("程序记忆", true),

    /**
     * 永久记忆：用户级跨会话事实，用户可自行维护，管理端可治理。
     */
    PERMANENT("永久记忆", false);

    private final String label;

    private final boolean tenantScoped;

    MemoryTier(String label, boolean tenantScoped) {
        this.label = label;
        this.tenantScoped = tenantScoped;
    }

    /**
     * 层级中文名。
     *
     * @return 中文名
     */
    public String label() {
        return label;
    }

    /**
     * 是否为租户级记忆。
     *
     * @return 是否租户级
     */
    public boolean tenantScoped() {
        return tenantScoped;
    }

    /**
     * 解析层级编码，未知编码返回空。
     *
     * @param code 层级编码
     * @return 记忆层级
     */
    public static MemoryTier of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MemoryTier tier : values()) {
            if (tier.name().equalsIgnoreCase(code.trim())) {
                return tier;
            }
        }
        return null;
    }
}
