package com.lingsql.tenant.model;

import lombok.Data;

import java.util.List;

/**
 * 租户通知分组创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
@Data
public class TenantNotificationGroupCreateRequest {

    private String tenantId;
    private String groupCode;
    private String groupName;
    private List<String> channels;
    private String remark;
}
