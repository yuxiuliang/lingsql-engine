package com.lingsql.rbac.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.config.LingSqlProperties.Security.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * 敏感接口限流拦截器。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensitiveRateLimitInterceptor implements HandlerInterceptor {

    private static final String PREFIX = "lingsql:ratelimit:sensitive:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LingSqlProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!isSensitiveMethod(request)) {
            return true;
        }
        RateLimit config = properties.getSecurity() == null ? null : properties.getSecurity().getRateLimit();
        if (config == null || !config.isSensitiveEnabled()) {
            return true;
        }
        String clientIp = RateLimitClientIpResolver.resolve(request, config);
        if (!StringUtils.hasText(clientIp)) {
            clientIp = "unknown";
        }
        Duration window = Optional.ofNullable(config.getSensitiveWindow()).filter(d -> !d.isZero() && !d.isNegative())
                .orElse(Duration.ofMinutes(1));
        Duration block = Optional.ofNullable(config.getSensitiveBlock()).filter(d -> !d.isZero() && !d.isNegative())
                .orElse(Duration.ofMinutes(5));
        int maxRequests = config.getSensitiveMaxRequests() <= 0 ? 60 : config.getSensitiveMaxRequests();
        String scopeKey = normalizePath(request.getRequestURI());
        String blockKey = PREFIX + "block:" + clientIp + ":" + scopeKey;
        String windowKey = PREFIX + "window:" + clientIp + ":" + scopeKey;
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
                return reject(response, "请求过于频繁，请稍后再试", block);
            }
            Long count = redisTemplate.opsForValue().increment(windowKey);
            if (count != null && count == 1) {
                redisTemplate.expire(windowKey, window);
            }
            if (count != null && count > maxRequests) {
                redisTemplate.opsForValue().set(blockKey, "1", block);
                return reject(response, "请求过于频繁，请稍后再试", block);
            }
            return true;
        } catch (Exception ex) {
            log.warn("敏感接口限流失败，已降级放行: ip={}", clientIp, ex);
            return true;
        }
    }

    private boolean isSensitiveMethod(HttpServletRequest request) {
        String method = request.getMethod();
        return HttpMethod.POST.matches(method) || HttpMethod.PUT.matches(method) || HttpMethod.DELETE.matches(method);
    }

    private boolean reject(HttpServletResponse response, String message, Duration block) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (block != null && !block.isZero() && !block.isNegative()) {
            response.setHeader("Retry-After", String.valueOf(block.toSeconds()));
        }
        ApiResponse<Void> payload = ApiResponse.tooManyRequests(message);
        response.getWriter().write(objectMapper.writeValueAsString(payload));
        return false;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "unknown";
        }
        return path.replace("/", ":");
    }
}
