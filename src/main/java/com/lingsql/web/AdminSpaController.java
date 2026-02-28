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
            "/layout",
            "/chat",
            "/history",
            "/chat-history",
            "/chat/history",
            "/dashboard",
            "/model",
            "/tenant",
            "/plan",
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
            "/sql-log"
    })
    public String forwardAdminSpaRoutes() {
        return FORWARD_ADMIN_INDEX;
    }
}
