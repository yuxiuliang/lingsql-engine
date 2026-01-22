package com.lingsql.config;

import com.lingsql.context.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置，注册租户拦截器。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    /**
     * 构造函数注入。
     *
     * @param tenantInterceptor 租户拦截器
     * @author 余炘洋
     * @date 2026-01-22
     */
    public WebMvcConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
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
    }
}
