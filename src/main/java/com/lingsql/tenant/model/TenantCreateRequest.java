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
     * 状态: 1-启用, 0-停用。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer status;

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
     * 余额。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long quotaBalance;

    /**
     * 服务等级。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String serviceLevel;

    /**
     * 租户类型。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String tenantType;

    /**
     * JDBC 地址。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String jdbcUrl;

    /**
     * 数据库用户名。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String username;

    /**
     * 数据库密码。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String password;

    /**
     * 驱动类。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String driverClass;

    /**
     * 隔离模式: PHYSICAL / LOGICAL。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String isolationMode;

    /**
     * 逻辑隔离字段。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String logicColumn;

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
