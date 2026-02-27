package com.lingsql.billing.model;

import lombok.Data;

/**
 * 计费商用验收检查项。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Data
public class BillingReadinessCheckItem {

    /**
     * 检查项名称。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private String name;

    /**
     * 是否通过。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private boolean passed;

    /**
     * 检查说明。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private String message;
}
