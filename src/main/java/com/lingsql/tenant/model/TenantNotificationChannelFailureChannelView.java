package com.lingsql.tenant.model;

import lombok.Data;

/**
 * 通知通道失败聚合项。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
@Data
public class TenantNotificationChannelFailureChannelView {

    /**
     * 通道编码。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String channel;

    /**
     * 失败次数。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Integer failureCount;
}
