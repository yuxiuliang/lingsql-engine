package com.lingsql.agent.memory;

/**
 * 记忆状态：模型候选先进入候选池，用户确认或管理端批准后另建生效记录；
 * 纠正生成新版本并使旧事实失效，撤销用于用户与管理端清除，保留期到期由任务转为失效。
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
     * 候选待确认：模型提取出的事实，尚未生效，不参与提示词注入。
     */
    CANDIDATE("候选待确认"),

    /**
     * 候选已采纳：已生成生效记忆，候选记录只作证据保留。
     */
    APPROVED("候选已采纳"),

    /**
     * 候选已拒绝：用户或管理端明确不予采纳。
     */
    REJECTED("候选已拒绝"),

    /**
     * 已被新版本取代。
     */
    SUPERSEDED("已被新版本取代"),

    /**
     * 已撤销。
     */
    REVOKED("已撤销"),

    /**
     * 已过保留期：由保留期到期任务转为失效，不再参与查询与提示词注入。
     */
    EXPIRED("已过保留期");

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
