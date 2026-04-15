package com.lingsql.tenant.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户通知通道最近测试记录。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
@Data
public class TenantNotificationChannelRecentTestView {

    /**
     * 审计日志ID。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Long auditLogId;

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
     * 本次测试的通道列表。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<String> requestedChannels;

    /**
     * 整体是否成功。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Boolean success;

    /**
     * 总结说明。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String summary;

    /**
     * 操作人账号。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String operatorUsername;

    /**
     * 测试时间。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private LocalDateTime createTime;

    /**
     * 通道明细。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<TenantNotificationChannelTestChannelView> channelResults;
}
