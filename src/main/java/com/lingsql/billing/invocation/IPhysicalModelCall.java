package com.lingsql.billing.invocation;

/**
 * 已完成本地校验的物理模型调用。
 *
 * @param <T> 响应载荷类型
 * @author 余炘洋
 * @date 2026-08-15
 */
@FunctionalInterface
public interface IPhysicalModelCall<T> {

    /**
     * 发出一次物理模型请求。
     *
     * @return 带供应商用量的响应
     */
    PhysicalModelResponse<T> invoke();
}
