package com.lingsql.security;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.metadata.service.MetadataExtService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 敏感字段脱敏（改写为 NULL）的回归测试。
 *
 * <p>背景（2026-10-31）：{@code SensitiveColumnVisitor} 与 {@code ColumnCollector} 犯了同一个错——
 * 只重写 JSqlParser 的 1 参数 {@code visit(Column)}，而实际分发走的是 2 参数
 * {@code visit(Column, S)}，导致「把敏感字段替换成 NULL」这一步从来没执行过。
 * 当时没有任何租户配置 sensitiveColumns，所以也没人发现。</p>
 *
 * @author 余炘洋
 * @date 2026-10-31
 */
class SqlRewriterSensitiveMaskingTest {

    private SqlRewriter newRewriter() {
        LingSqlProperties properties = new LingSqlProperties();
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        return new SqlRewriter(properties, metadataExtService);
    }

    private TenantDbConfig config(String sensitiveColumns) {
        TenantDbConfig config = new TenantDbConfig();
        config.setTenantId("TENANT_A");
        config.setIsolationMode("PHYSICAL");
        config.setLogicColumn("tenant_id");
        config.setWhiteListTables("orders");
        config.setWhiteListColumns("");
        config.setSensitiveColumns(sensitiveColumns);
        return config;
    }

    @Test
    void shouldMaskSensitiveColumnIntoNull() {
        SqlRewriter rewriter = newRewriter();

        String rewritten = rewriter.rewrite(
                "SELECT o.password, o.order_id FROM orders o LIMIT 10",
                config("password"),
                "TENANT_A"
        );

        assertFalse(rewritten.toLowerCase().contains("o.password"),
                "敏感字段不能再以字段引用的形式出现在改写后的 SQL 里：" + rewritten);
        assertTrue(rewritten.toUpperCase().contains("NULL"),
                "脱敏方式是把该列取值替换成 NULL：" + rewritten);
    }

    @Test
    void shouldKeepNonSensitiveColumnIntact() {
        SqlRewriter rewriter = newRewriter();

        String rewritten = rewriter.rewrite(
                "SELECT o.order_id FROM orders o LIMIT 10",
                config("password"),
                "TENANT_A"
        );

        assertTrue(rewritten.toLowerCase().contains("order_id"),
                "非敏感字段必须保留——否则上面的脱敏断言可能只是恒真：" + rewritten);
    }
}
