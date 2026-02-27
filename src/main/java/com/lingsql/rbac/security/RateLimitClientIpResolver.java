package com.lingsql.rbac.security;

import com.lingsql.config.LingSqlProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 限流场景下的客户端 IP 解析器。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
public final class RateLimitClientIpResolver {

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private RateLimitClientIpResolver() {
    }

    /**
     * 解析客户端 IP。
     *
     * @param request HTTP 请求
     * @param config  限流配置
     * @return 客户端 IP
     * @author 余炘洋
     * @date 2026-02-27
     */
    public static String resolve(HttpServletRequest request, LingSqlProperties.Security.RateLimit config) {
        if (request == null) {
            return null;
        }
        String remoteAddr = request.getRemoteAddr();
        if (!shouldTrustProxyHeaders(remoteAddr, config)) {
            return remoteAddr;
        }
        String forwarded = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (StringUtils.hasText(forwarded)) {
            String[] parts = forwarded.split(",");
            if (parts.length > 0 && StringUtils.hasText(parts[0])) {
                return parts[0].trim();
            }
        }
        String realIp = request.getHeader(HEADER_X_REAL_IP);
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return remoteAddr;
    }

    private static boolean shouldTrustProxyHeaders(String remoteAddr, LingSqlProperties.Security.RateLimit config) {
        if (config == null || !config.isTrustProxyHeaders()) {
            return false;
        }
        if (!StringUtils.hasText(remoteAddr)) {
            return false;
        }
        List<String> trustedProxyIps = config.getTrustedProxyIps();
        if (trustedProxyIps == null || trustedProxyIps.isEmpty()) {
            return false;
        }
        return trustedProxyIps.stream().filter(StringUtils::hasText).anyMatch(remoteAddr::equals);
    }
}

