package com.lingsql.billing.invocation;

/**
 * 逐物理模型调用计费接缝。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface IPhysicalModelBilling {

    /**
     * 在持久预留和调用状态保护下执行一次物理模型调用。
     *
     * @param invocation 调用描述
     * @param physicalCall 物理模型调用
     * @return 已结算的模型响应
     * @param <T> 响应载荷类型
     */
    <T> BilledModelResponse<T> execute(
            PhysicalModelInvocation invocation,
            IPhysicalModelCall<T> physicalCall
    );
}
