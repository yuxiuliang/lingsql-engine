package com.lingsql.web;

import com.lingsql.rbac.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端 SPA 路由清单一致性。
 *
 * <p>同一个路由清单在代码里存在两份：{@code SecurityConfig.ADMIN_SPA_ROUTES}（决定是否放行）
 * 与 {@code AdminSpaController} 的 {@code @GetMapping}（决定是否转发到 index.html）。
 * 2026-10-27 实际渲染界面时发现两份都落后于前端路由：`/memory-admin`、`/plugin`、`/organization`
 * 等页面从菜单点进去正常，**一刷新就变成一段 JSON**。</p>
 *
 * <p>这条测试把两份清单钉在一起：任何一边新增/删除路由而另一边没跟上，构建立刻失败。</p>
 *
 * @author 余炘洋
 * @date 2026-10-27
 */
class AdminSpaRouteConsistencyTest {

    @Test
    void securityAllowlistAndSpaForwardListMustMatch() throws Exception {
        Set<String> securityRoutes = readSecurityRoutes();
        Set<String> spaRoutes = readSpaRoutes();

        assertEquals(securityRoutes, spaRoutes,
                "SecurityConfig.ADMIN_SPA_ROUTES 与 AdminSpaController 的 @GetMapping 必须完全一致；"
                        + "不一致会导致部分页面刷新后返回 JSON 而不是界面");
    }

    @Test
    void shouldCoverKnownRegressedRoutes() throws Exception {
        Set<String> spaRoutes = readSpaRoutes();
        assertTrue(spaRoutes.containsAll(Arrays.asList(
                        "/password-change", "/approval", "/memory", "/memory-admin",
                        "/menu", "/organization", "/plugin")),
                "2026-10-27 修复过的路由不能再次缺失：" + spaRoutes);
    }

    private Set<String> readSecurityRoutes() throws Exception {
        Field field = SecurityConfig.class.getDeclaredField("ADMIN_SPA_ROUTES");
        field.setAccessible(true);
        String[] values = (String[]) field.get(null);
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    private Set<String> readSpaRoutes() throws Exception {
        Method method = AdminSpaController.class.getMethod("forwardAdminSpaRoutes");
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        return new LinkedHashSet<>(Arrays.asList(mapping.value()));
    }
}
