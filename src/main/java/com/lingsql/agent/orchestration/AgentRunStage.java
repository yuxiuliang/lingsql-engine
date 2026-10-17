package com.lingsql.agent.orchestration;

/**
 * 受控 Agent 运行阶段。
 *
 * <p>编排状态图只能引用本枚举取值：检查点与审批节点都禁止写入任意字符串。
 * 恢复边界由 {@link #isResumable()} 固化，只有严格位于安全查询模块之前的阶段才允许重新进入。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
public enum AgentRunStage {

    /** 已请求人工审批：在审批通过前不产生任何模型调用，也不执行 SQL。 */
    APPROVAL_REQUESTED(true, true),
    /** 已把查询派发进安全查询模块（SQL 生成、审计与 JDBC 属于同一个不可拆分模块）。 */
    SQL_ARCHITECT(false, false),
    /** 安全查询模块已返回结果。 */
    SECURE_QUERY(false, false),
    /** 业务分析阶段。 */
    ANALYST(false, false),
    /** 人工审批通过后的续跑已经结束。 */
    RESUME_COMPLETED(false, true);

    private final boolean resumable;
    private final boolean approvalStage;

    AgentRunStage(boolean resumable, boolean approvalStage) {
        this.resumable = resumable;
        this.approvalStage = approvalStage;
    }

    /**
     * 是否允许从该阶段恢复。
     *
     * @return 是否可恢复
     * @author 余炘洋
     * @date 2026-10-17
     */
    public boolean isResumable() {
        return resumable;
    }

    /**
     * 是否属于人工审批节点阶段。
     *
     * @return 是否审批阶段
     * @author 余炘洋
     * @date 2026-10-17
     */
    public boolean isApprovalStage() {
        return approvalStage;
    }

    /**
     * 解析阶段名称。
     *
     * <p>历史检查点可能写入未知字符串，这里 fail-closed：无法识别的阶段一律按不可恢复处理。</p>
     *
     * @param stage 阶段名称
     * @return 阶段，无法识别时返回 {@link #SQL_ARCHITECT}
     * @author 余炘洋
     * @date 2026-10-17
     */
    public static AgentRunStage parse(String stage) {
        if (stage == null || stage.isBlank()) {
            return SQL_ARCHITECT;
        }
        for (AgentRunStage candidate : values()) {
            if (candidate.name().equals(stage.trim())) {
                return candidate;
            }
        }
        return SQL_ARCHITECT;
    }
}
