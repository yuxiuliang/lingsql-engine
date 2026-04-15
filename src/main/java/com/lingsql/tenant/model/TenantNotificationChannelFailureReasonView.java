package com.lingsql.tenant.model;

import lombok.Data;

/**
 * 通知通道失败原因聚合项。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
@Data
public class TenantNotificationChannelFailureReasonView {

    /**
     * 失败原因。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String reason;

    /**
     * 出现次数。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Integer count;
}
