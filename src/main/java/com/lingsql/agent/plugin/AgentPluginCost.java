package com.lingsql.agent.plugin;

/**
 * Agent 插件成本等级。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public enum AgentPluginCost {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int units;

    AgentPluginCost(int units) {
        this.units = units;
    }

    public int units() {
        return units;
    }
}
