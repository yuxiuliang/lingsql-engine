package com.lingsql.config;

import com.lingsql.context.TenantInterceptor;
import com.lingsql.billing.PaymentWallInterceptor;
import com.lingsql.rbac.security.LoginRateLimitInterceptor;
import com.lingsql.rbac.security.SensitiveRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.util.List;

/**
 * Web MVC 配置，注册租户拦截器。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final PaymentWallInterceptor paymentWallInterceptor;
    private final LingSqlProperties properties;
    private final LoginRateLimitInterceptor loginRateLimitInterceptor;
    private final SensitiveRateLimitInterceptor sensitiveRateLimitInterceptor;

    /**
     * 构造函数注入。
     *
     * @param tenantInterceptor 租户拦截器
     * @author 余炘洋
     * @date 2026-01-22
     */
    public WebMvcConfig(
            TenantInterceptor tenantInterceptor,
            PaymentWallInterceptor paymentWallInterceptor,
            LingSqlProperties properties,
            LoginRateLimitInterceptor loginRateLimitInterceptor,
            SensitiveRateLimitInterceptor sensitiveRateLimitInterceptor
    ) {
        this.tenantInterceptor = tenantInterceptor;
        this.paymentWallInterceptor = paymentWallInterceptor;
        this.properties = properties;
        this.loginRateLimitInterceptor = loginRateLimitInterceptor;
        this.sensitiveRateLimitInterceptor = sensitiveRateLimitInterceptor;
    }

    /**
     * 注册拦截器。
     *
     * @param registry 拦截器注册器
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(paymentWallInterceptor).addPathPatterns("/api/v1/chat/ask");
        registry.addInterceptor(loginRateLimitInterceptor).addPathPatterns("/api/v1/auth/login");
        registry.addInterceptor(sensitiveRateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/admin/users/*/password"
                );
    }

    /**
     * 配置跨域白名单。
     *
     * @param registry 跨域注册器
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        LingSqlProperties.Cors cors = properties.getCors();
        if (cors == null || !cors.isEnabled()) {
            return;
        }
        List<String> allowedOrigins = cors.getAllowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return;
        }
        var mapping = registry.addMapping("/api/**")
                .allowedMethods(cors.getAllowedMethods().toArray(new String[0]))
                .allowedHeaders(cors.getAllowedHeaders().toArray(new String[0]))
                .allowCredentials(cors.isAllowCredentials())
                .maxAge(cors.getMaxAge());
        if (cors.getExposedHeaders() != null && !cors.getExposedHeaders().isEmpty()) {
            mapping.exposedHeaders(cors.getExposedHeaders().toArray(new String[0]));
        }
        if (allowedOrigins.contains("*")) {
            mapping.allowedOriginPatterns("*");
        } else {
            mapping.allowedOrigins(allowedOrigins.toArray(new String[0]));
        }
    }

    /**
     * 增加管理端前端资源别名，兼容 SPA 路由刷新后静态资源加载路径。
     *
     * @param registry 资源映射注册器
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/admin/assets/");
    }
}
