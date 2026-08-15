package com.lingsql.tenant.model;

import lombok.Data;

import java.util.List;

/**
 * 租户创建请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class TenantCreateRequest {

    /**
     * 首个租户管理员账号。创建租户时必须同步开户，避免产生无法登录的空租户。
     *
     * @author 余炘洋
     * @date 2026-08-15
     */
    private TenantAdminAccountRequest adminAccount;

    /**
     * 首问使用的数据源。READY 开户必须完成连接与只读权限验证。
     *
     * @author 余炘洋
     * @date 2026-08-15
     */
    private TenantDataSourceProvisionRequest dataSource;

    /**
     * 租户ID（由系统自动生成）。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantId;

    /**
     * 租户名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantName;

    /**
     * 备注。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String remark;

    /**
     * 联系人姓名。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String contactName;

    /**
     * 联系电话。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String contactPhone;

    /**
     * 联系邮箱。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String contactEmail;

    /**
     * 默认通知通道列表。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<String> notificationChannels;

    /**
     * 到期时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String expireTime;

    /**
     * 套餐名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String planName;

    /**
     * 组织模板编码。
     *
     * <p>用于在创建租户后自动初始化一套组织类型、节点树和关系语义。</p>
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String orgTemplateCode;

    /**
     * 组织模板根节点名称。
     *
     * <p>用于开户时把模板默认根节点名称替换成客户自己的组织主体名称。</p>
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String orgRootUnitName;

}
