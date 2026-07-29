package com.lingsql.executor;

/**
 * SQL 自愈决策。
 *
 * @param recoverable 是否允许进入下一轮恢复
 * @param refreshSchema 是否需要刷新元数据
 * @param observation 模型可消费的受控观察结果
 * @author 余炘洋
 * @date 2026-07-29
 */
public record SqlRecoveryDecision(boolean recoverable, boolean refreshSchema, String observation) {

    /**
     * 构造不可恢复决策。
     *
     * @return 不可恢复决策
     * @author 余炘洋
     * @date 2026-07-29
     */
    public static SqlRecoveryDecision notRecoverable() {
        return new SqlRecoveryDecision(false, false, "当前失败类型不允许自动重试。");
    }
}
