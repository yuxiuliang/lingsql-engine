package com.lingsql.agent.memory;

/**
 * 记忆敏感分类：候选事实在写入候选池前完成分类，敏感候选保留证据但不允许采纳。
 *
 * <p>分类只决定“能否进入长期记忆”，不改变内容本身；真正写入生效记忆时仍会再次
 * 经过 {@link MemoryContentGuard} 的注入与敏感信息校验。</p>
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
public enum MemorySensitivity {

    /**
     * 普通：未命中敏感信息或注入特征。
     */
    NORMAL("普通", true),

    /**
     * 敏感：命中凭据、证件、联系方式等敏感特征，禁止采纳为长期记忆。
     */
    SENSITIVE("敏感", false);

    private final String label;

    private final boolean promotable;

    MemorySensitivity(String label, boolean promotable) {
        this.label = label;
        this.promotable = promotable;
    }

    /**
     * 分类中文名。
     *
     * @return 中文名
     */
    public String label() {
        return label;
    }

    /**
     * 是否允许采纳为长期记忆。
     *
     * @return 是否允许采纳
     */
    public boolean promotable() {
        return promotable;
    }

    /**
     * 解析分类编码，未知编码返回空。
     *
     * @param code 分类编码
     * @return 敏感分类
     */
    public static MemorySensitivity of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MemorySensitivity sensitivity : values()) {
            if (sensitivity.name().equalsIgnoreCase(code.trim())) {
                return sensitivity;
            }
        }
        return null;
    }
}
