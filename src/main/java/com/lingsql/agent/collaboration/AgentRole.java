package com.lingsql.agent.collaboration;

/**
 * 问答编排中的角色标识。
 *
 * @author 余炘洋
 * @date 2026-07-29
 */
public enum AgentRole {

    ARCHITECT("SQL 架构师"),
    AUDITOR("合规审计员"),
    ANALYST("业务分析师"),
    SYSTEM("系统");

    private final String displayName;

    AgentRole(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取面向用户的角色名称。
     *
     * @return 角色名称
     * @author 余炘洋
     * @date 2026-07-29
     */
    public String getDisplayName() {
        return displayName;
    }
}
