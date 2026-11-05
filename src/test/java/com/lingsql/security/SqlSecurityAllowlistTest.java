package com.lingsql.security;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.metadata.service.MetadataExtService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 白名单拦截（{@code SECURITY_ALLOWLIST_BLOCKED}）的回归测试。
 *
 * <p>背景（2026-10-31）：这个错误码在 5 个生产文件里出现，用于挡住「查未授权表」和
 * 「白名单开启时用 * 查全部字段」，但**测试里一次都没出现过**——也就是说这套拦截
 * 从来没有被自动验证过。40 题 Suite 里的安全用例 IR-SEC-003（查系统用户表）期望的
 * 正是这个码。</p>
 *
 * <p>注意：端到端问「查询系统用户表」验不到它——离线桩压根不会生成那样的 SQL，
 * 请求根本走不到安全层。要验这个拦截，只能直接测检查器。</p>
 *
 * @author 余炘洋
 * @date 2026-10-31
 */
class SqlSecurityAllowlistTest {

    private SqlSecurityChecker newChecker() {
        LingSqlProperties properties = new LingSqlProperties();
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        return new SqlSecurityChecker(properties, metadataExtService);
    }

    private TenantDbConfig config(String whiteTables, String whiteColumns, String sensitiveColumns) {
        TenantDbConfig config = new TenantDbConfig();
        config.setTenantId("TENANT_A");
        config.setIsolationMode("LOGICAL");
        config.setLogicColumn("tenant_id");
        config.setWhiteListTables(whiteTables);
        config.setWhiteListColumns(whiteColumns);
        config.setSensitiveColumns(sensitiveColumns);
        return config;
    }

    @Test
    void shouldRejectTableOutsideAllowlist() {
        SqlSecurityChecker checker = newChecker();

        BizException ex = assertThrows(BizException.class, () -> checker.check(
                "SELECT u.user_name FROM sys_user u WHERE u.tenant_id = 'TENANT_A' LIMIT 10",
                config("orders", "", ""),
                "TENANT_A"
        ));

        assertEquals(ErrorCode.SECURITY_ALLOWLIST_BLOCKED, ex.getErrorCode(),
                "查询白名单外的表必须被拦住，且错误码是白名单拦截");
    }

    @Test
    void shouldRejectStarWhenColumnWhitelistConfigured() {
        SqlSecurityChecker checker = newChecker();

        BizException ex = assertThrows(BizException.class, () -> checker.check(
                "SELECT * FROM orders o WHERE o.tenant_id = 'TENANT_A' LIMIT 10",
                config("orders", "order_id", ""),
                "TENANT_A"
        ));

        assertEquals(ErrorCode.SECURITY_ALLOWLIST_BLOCKED, ex.getErrorCode(),
                "字段白名单开启时禁止用 * 查全部字段");
    }

    @Test
    void shouldAllowAllowlistedTableWithExplicitColumns() {
        SqlSecurityChecker checker = newChecker();

        assertDoesNotThrow(() -> checker.check(
                "SELECT o.order_id FROM orders o WHERE o.tenant_id = 'TENANT_A' LIMIT 10",
                config("orders", "order_id", ""),
                "TENANT_A"
        ), "白名单内的表 + 显式字段应当放行——否则上面的拦截断言可能只是恒真");
    }

    @Test
    void shouldRejectSensitiveColumnEvenWhenExplicitlyNamed() {
        SqlSecurityChecker checker = newChecker();

        BizException ex = assertThrows(BizException.class, () -> checker.check(
                "SELECT o.password FROM orders o WHERE o.tenant_id = 'TENANT_A' LIMIT 10",
                config("orders", "", "password"),
                "TENANT_A"
        ));

        assertEquals(ErrorCode.SECURITY_ALLOWLIST_BLOCKED, ex.getErrorCode(),
                "敏感字段被显式点名时同样要拦住——不能只挡 * 查询");
    }

    @Test
    void shouldRejectColumnOutsideWhitelist() {
        SqlSecurityChecker checker = newChecker();

        BizException ex = assertThrows(BizException.class, () -> checker.check(
                "SELECT o.secret_note FROM orders o WHERE o.tenant_id = 'TENANT_A' LIMIT 10",
                config("orders", "order_id", ""),
                "TENANT_A"
        ));

        assertEquals(ErrorCode.SECURITY_ALLOWLIST_BLOCKED, ex.getErrorCode(),
                "白名单开启时，点名查白名单外的字段必须被拦住");
    }

    @Test
    void shouldNotTreatSelectAliasAsColumnInOrderBy() {
        SqlSecurityChecker checker = newChecker();

        // 真实字段都在白名单里，ORDER BY 引用的"合计""编号"是 SELECT 的别名。
        // 2026-10-31 修复前，别名会被当成字段去比对白名单，导致带 ORDER BY 别名的正常查询被误拦
        // （线上实测 10 题里被拦掉 3 题）。
        assertDoesNotThrow(() -> checker.check(
                "SELECT o.order_id AS 编号, o.total AS 合计 FROM orders o "
                        + "WHERE o.tenant_id = 'TENANT_A' ORDER BY 合计 DESC LIMIT 10",
                config("orders", "order_id,total", ""),
                "TENANT_A"
        ), "ORDER BY 里的别名不是字段，不应参与白名单校验");
    }
}
