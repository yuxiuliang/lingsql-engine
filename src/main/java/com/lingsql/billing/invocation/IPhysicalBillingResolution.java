package com.lingsql.billing.invocation;

/**
 * UNKNOWN 物理调用资金裁决深模块。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface IPhysicalBillingResolution {

    /**
     * 依据外部证据将 UNKNOWN 调用原子收口为 SETTLED 或 CANCELLED。
     *
     * @param command 裁决命令
     * @return 不含证据正文的稳定回执
     */
    PhysicalBillingResolutionReceipt resolve(PhysicalBillingResolutionCommand command);
}
