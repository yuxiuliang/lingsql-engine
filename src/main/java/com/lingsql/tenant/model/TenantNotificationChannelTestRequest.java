package com.lingsql.tenant.model;

import lombok.Data;

import java.util.List;

/**
 * 租户通知通道测试请求。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
@Data
public class TenantNotificationChannelTestRequest {

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String tenantId;

    /**
     * 租户名称。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String tenantName;

    /**
     * 待测试的通知通道列表。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<String> channels;
}
