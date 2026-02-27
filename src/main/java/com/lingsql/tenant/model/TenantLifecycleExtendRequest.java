package com.lingsql.tenant.model;

import lombok.Data;

/**
 * 租户生命周期续期请求。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Data
public class TenantLifecycleExtendRequest {

    /**
     * 目标到期时间（ISO 格式）。
     * 为空时按 extendDays 叠加。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private String expireTime;

    /**
     * 延长天数。
     * 默认 30 天。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private Integer extendDays;

    /**
     * 是否自动恢复服务（默认 true）。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private Boolean activate;
}

