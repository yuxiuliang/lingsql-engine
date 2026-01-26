package com.lingsql.tenant.model;

import lombok.Data;

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

}
