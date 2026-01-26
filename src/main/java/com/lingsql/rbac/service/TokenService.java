package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.rbac.security.RbacSecurityConstants;
import com.lingsql.rbac.security.RbacUserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Token 会话管理服务。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Token 并缓存会话。
     *
     * @param session 会话数据
     * @return Token
     * @author 余炘洋
     * @date 2026-01-23
     */
    public String createToken(RbacUserSession session) {
        String token = UUID.randomUUID().toString().replace("-", "");
        cacheSession(token, session);
        return token;
    }

    /**
     * 获取会话。
     *
     * @param token Token
     * @return 会话数据
     * @author 余炘洋
     * @date 2026-01-23
     */
    public RbacUserSession getSession(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = stringRedisTemplate.opsForValue().get(buildKey(token));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, RbacUserSession.class);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 刷新会话缓存。
     *
     * @param token   Token
     * @param session 会话数据
     * @author 余炘洋
     * @date 2026-01-23
     */
    public void refreshSession(String token, RbacUserSession session) {
        if (token == null || token.isBlank() || session == null) {
            return;
        }
        cacheSession(token, session);
    }

    /**
     * 删除 Token。
     *
     * @param token Token
     * @author 余炘洋
     * @date 2026-01-23
     */
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        stringRedisTemplate.delete(buildKey(token));
    }

    /**
     * 获取默认过期秒数。
     *
     * @return 过期秒数
     * @author 余炘洋
     * @date 2026-01-23
     */
    public long getExpireSeconds() {
        return RbacSecurityConstants.DEFAULT_EXPIRE_SECONDS;
    }

    /**
     * 缓存会话信息。
     *
     * @param token   Token
     * @param session 会话
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void cacheSession(String token, RbacUserSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            stringRedisTemplate.opsForValue().set(
                    buildKey(token),
                    json,
                    Duration.ofSeconds(getExpireSeconds())
            );
        } catch (Exception ex) {
            throw new RuntimeException("缓存登录会话失败");
        }
    }

    /**
     * 构建缓存 Key。
     *
     * @param token Token
     * @return Key
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String buildKey(String token) {
        String safeSuffix = DigestUtils.md5DigestAsHex(token.getBytes(StandardCharsets.UTF_8));
        return RbacSecurityConstants.TOKEN_CACHE_PREFIX + safeSuffix;
    }
}
