package com.lingsql.billing.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 计费策略版本请求。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Data
public class BillingPolicyVersionRequest {

    /**
     * 版本号。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String versionCode;

    /**
     * 状态: 1-生效, 0-停用。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Integer status;

    /**
     * 生效时间(ISO格式)。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String effectiveTime;

    /**
     * 模型单价配置。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Map<String, Long> modelUnitCost;

    /**
     * 服务等级限额配置。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Map<String, BillingLimit> serviceLevelLimit;

    /**
     * 租户类型权重。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Map<String, BigDecimal> tenantTypeWeight;

    /**
     * 默认单价(点/Token)。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long defaultUnitCost;

    /**
     * 默认每日调用上限。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Integer defaultDailyLimit;

    /**
     * 默认每日 Token 上限。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long defaultDailyTokenLimit;

    /**
     * 默认租户权重。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private BigDecimal defaultTenantWeight;

    /**
     * 说明。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String description;
}
