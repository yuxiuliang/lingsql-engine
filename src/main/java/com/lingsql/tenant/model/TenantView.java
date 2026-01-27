package com.lingsql.tenant.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class TenantView {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long id;

    /**
     * 租户ID。
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
     * 状态。
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
    private LocalDateTime expireTime;

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
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime updateTime;

}
