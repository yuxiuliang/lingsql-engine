package com.lingsql.datasource;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;

/**
 * 租户数据源配置异常工具。
 *
 * @author 余炘洋
 * @date 2026-04-10
 */
public final class TenantDbConfigErrors {

    /**
     * 租户数据源配置缺失提示。
     *
     * @author 余炘洋
     * @date 2026-04-10
     */
    public static final String MISSING_CONFIG_MESSAGE = "租户数据源配置不存在，请先完成租户数据库连接配置";

    private TenantDbConfigErrors() {
    }

    /**
     * 创建租户数据源配置缺失异常。
     *
     * @return 业务异常
     * @author 余炘洋
     * @date 2026-04-10
     */
    public static BizException missingConfig() {
        return new BizException(ErrorCode.TENANT_DB_CONFIG_NOT_FOUND, MISSING_CONFIG_MESSAGE);
    }
}
