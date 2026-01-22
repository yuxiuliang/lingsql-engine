package com.lingsql.datasource;

import com.lingsql.context.TenantContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;

/**
 * 动态数据源路由器，根据 ThreadLocal 中的租户 ID 切换数据源。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Component
public class DynamicDataSource extends AbstractRoutingDataSource {

    private final DataSourceManager dataSourceManager;

    /**
     * 构造函数。
     *
     * @param dataSourceManager 租户数据源管理器
     * @param defaultDataSource 主配置库数据源
     * @author 余炘洋
     * @date 2026-01-22
     */
    public DynamicDataSource(DataSourceManager dataSourceManager, DataSource defaultDataSource) {
        this.dataSourceManager = dataSourceManager;
        /**
         * 初始化目标数据源占位，避免 AbstractRoutingDataSource 初始化异常。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        super.setTargetDataSources(new HashMap<>());
        super.setDefaultTargetDataSource(defaultDataSource);
    }

    /**
     * 返回当前线程持有的租户 ID 作为路由 Key。
     *
     * @return 路由 Key
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantId();
    }

    /**
     * 获取目标数据源，如果没有租户 ID，则回退到默认主库。
     *
     * @return 数据源
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Override
    protected DataSource determineTargetDataSource() {
        String tenantId = (String) determineCurrentLookupKey();
        if (tenantId == null) {
            return super.getResolvedDefaultDataSource();
        }
        return dataSourceManager.getDataSource(tenantId);
    }
}
