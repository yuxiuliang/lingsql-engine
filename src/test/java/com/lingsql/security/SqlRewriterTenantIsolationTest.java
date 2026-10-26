package com.lingsql.security;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.metadata.service.MetadataExtService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行级租户隔离（LOGICAL 模式）的改写行为。
 *
 * <p>背景：演示租户用的是 PHYSICAL（独立库）隔离，执行 SQL 里**不会**出现租户条件——
 * 隔离发生在数据源层面。真正把 {@code logic_column} 注入到主表与每一个 JOIN 的是
 * LOGICAL 模式，而这条安全相关路径此前没有任何测试覆盖。</p>
 *
 * @author 余炘洋
 * @date 2026-10-26
 */
class SqlRewriterTenantIsolationTest {

    private SqlRewriter newRewriter() {
        LingSqlProperties properties = new LingSqlProperties();
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Optional.empty());
        return new SqlRewriter(properties, metadataExtService);
    }

    private TenantDbConfig logicalConfig() {
        TenantDbConfig config = new TenantDbConfig();
        config.setIsolationMode("LOGICAL");
        config.setLogicColumn("tenant_id");
        return config;
    }

    @Test
    void shouldInjectTenantConditionIntoMainTableAndEveryJoin() {
        String sql = "SELECT o.order_no FROM demo_sales_order o "
                + "JOIN demo_sales_order_item i ON i.order_id = o.order_id";

        String rewritten = newRewriter().rewrite(sql, logicalConfig(), "T1");

        assertTrue(rewritten.contains("o.tenant_id = 'T1'"), "主表必须注入租户条件，实际: " + rewritten);
        assertTrue(rewritten.contains("i.tenant_id = 'T1'"), "JOIN 表必须注入租户条件，实际: " + rewritten);
    }

    @Test
    void shouldFallbackToConfiguredLogicColumnWhenMetadataHasNoOverride() {
        TenantDbConfig config = new TenantDbConfig();
        config.setIsolationMode("LOGICAL");
        config.setLogicColumn("org_code");

        String rewritten = newRewriter().rewrite("SELECT o.id FROM demo_sales_order o", config, "T1");

        assertTrue(rewritten.contains("org_code = 'T1'"), "缺少元数据覆盖时应回落到配置列，实际: " + rewritten);
    }

    @Test
    void shouldNotInjectTenantConditionUnderPhysicalIsolation() {
        TenantDbConfig config = new TenantDbConfig();
        config.setIsolationMode("PHYSICAL");
        config.setLogicColumn("tenant_id");

        String rewritten = newRewriter().rewrite(
                "SELECT o.order_no FROM demo_sales_order o", config, "T1");

        assertFalse(rewritten.contains("tenant_id ="), "独立库隔离不应注入行级条件，实际: " + rewritten);
    }
}
