package com.lingsql.rbac.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.security.RbacUserSession;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Token 会话管理服务（JWT 无状态）。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String SESSION_CLAIM = "session";

    private final LingSqlProperties properties;
    private final ObjectMapper objectMapper;
    private volatile SecretKey signingKey;

    /**
     * 创建 Token 并缓存会话。
     *
     * @param session 会话数据
     * @return Token
     * @author 余炘洋
     * @date 2026-01-26
     */
    public String createToken(RbacUserSession session) {
        if (session == null || session.getUserId() == null) {
            throw new RuntimeException("会话数据不能为空");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(resolveTtl());
        Map<String, Object> sessionMap = objectMapper.convertValue(session, new TypeReference<>() {
        });
        return Jwts.builder()
                .issuer(resolveIssuer())
                .subject(String.valueOf(session.getUserId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(SESSION_CLAIM, sessionMap)
                .signWith(resolveSigningKey())
                .compact();
    }

    /**
     * 获取会话。
     *
     * @param token Token
     * @return 会话数据
     * @author 余炘洋
     * @date 2026-01-26
     */
    public RbacUserSession getSession(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(resolveSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object sessionObj = claims.get(SESSION_CLAIM);
            if (sessionObj == null) {
                return null;
            }
            return objectMapper.convertValue(sessionObj, RbacUserSession.class);
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
     * @date 2026-01-26
     */
    public void refreshSession(String token, RbacUserSession session) {
        // JWT 无状态模式下无需刷新
    }

    /**
     * 删除 Token。
     *
     * @param token Token
     * @author 余炘洋
     * @date 2026-01-26
     */
    public void revokeToken(String token) {
        // JWT 无状态模式下无需吊销
    }

    /**
     * 获取默认过期秒数。
     *
     * @return 过期秒数
     * @author 余炘洋
     * @date 2026-01-26
     */
    public long getExpireSeconds() {
        return resolveTtl().getSeconds();
    }

    /**
     * 解析 JWT 签发方。
     *
     * @return 签发方
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveIssuer() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth == null || !StringUtils.hasText(auth.getJwtIssuer())) {
            return "lingsql-engine";
        }
        return auth.getJwtIssuer().trim();
    }

    /**
     * 解析 Token 有效期。
     *
     * @return 有效期
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Duration resolveTtl() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth == null || auth.getJwtTtl() == null) {
            return Duration.ofHours(24);
        }
        return auth.getJwtTtl();
    }

    /**
     * 获取签名密钥。
     *
     * @return 密钥
     * @author 余炘洋
     * @date 2026-01-26
     */
    private SecretKey resolveSigningKey() {
        if (signingKey != null) {
            return signingKey;
        }
        synchronized (this) {
            if (signingKey != null) {
                return signingKey;
            }
            LingSqlProperties.Auth auth = properties.getAuth();
            String secret = auth == null ? null : auth.getJwtSecret();
            if (!StringUtils.hasText(secret)) {
                throw new RuntimeException("JWT 密钥未配置");
            }
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                throw new RuntimeException("JWT 密钥长度不足 32 字符");
            }
            signingKey = Keys.hmacShaKeyFor(keyBytes);
            return signingKey;
        }
    }
}
