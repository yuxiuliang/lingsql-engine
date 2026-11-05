package com.lingsql.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 管理端 SPA 路由入口。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Controller
public class AdminSpaController {

    private static final String FORWARD_ADMIN_INDEX = "forward:/admin/index.html";

    /**
     * 管理端入口地址重定向。
     *
     * @return 跳转地址
     * @author 余炘洋
     * @date 2026-02-27
     */
    @GetMapping("/admin")
    public String redirectAdminEntry() {
        return "redirect:/admin/index.html";
    }

    /**
     * 前端路由刷新兼容，统一转发到管理端入口页面。
     *
     * @return 转发地址
     * @author 余炘洋
     * @date 2026-02-27
     */
    @GetMapping({
            "/",
            "/login",
            "/password-change",
            "/layout",
            "/chat",
            "/history",
            "/chat-history",
            "/chat/history",
            "/dashboard",
            "/model",
            "/tenant",
            "/plan",
            "/digital-employee",
            "/billing",
            "/datasource",
            "/lexicon",
            "/join-rule",
            "/metadata",
            "/experience",
            "/user",
            "/role",
            "/permission",
            "/audit",
            "/sql-log",
            // 2026-10-27 补齐：以下路由前端已实现，但此前既不在本清单、也不在
            // SecurityConfig.ADMIN_SPA_ROUTES，直接访问或刷新会返回 JSON（404/401），
            // 表现为"从菜单点进去正常，一刷新就成一段 JSON"。
            // 两份清单从此必须完全一致，由 AdminSpaRouteConsistencyTest 守住。
            "/approval",
            "/memory",
            "/memory-admin",
            "/menu",
            "/organization",
            "/plugin"
    })
    public String forwardAdminSpaRoutes() {
        return FORWARD_ADMIN_INDEX;
    }
}
