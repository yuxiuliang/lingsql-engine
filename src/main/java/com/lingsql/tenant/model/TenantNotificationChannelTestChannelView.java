package com.lingsql.tenant.model;

import lombok.Data;

/**
 * 租户通知通道测试明细。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
@Data
public class TenantNotificationChannelTestChannelView {

    /**
     * 通道编码。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String channel;

    /**
     * 是否成功。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Boolean success;

    /**
     * HTTP 状态码。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Integer statusCode;

    /**
     * 诊断明细。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String detail;
}
