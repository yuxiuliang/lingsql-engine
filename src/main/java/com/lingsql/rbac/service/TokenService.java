package com.lingsql.rbac.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.security.RbacSecurityConstants;
import com.lingsql.rbac.security.RbacUserSession;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

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
    private static final String REVOKED_JTI_PREFIX = RbacSecurityConstants.TOKEN_CACHE_PREFIX + "revoked:jti:";
    private static final String REVOKED_HASH_PREFIX = RbacSecurityConstants.TOKEN_CACHE_PREFIX + "revoked:hash:";
    private static final String REVOKED_USER_PREFIX = RbacSecurityConstants.TOKEN_CACHE_PREFIX + "revoked:user:";

    private final LingSqlProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
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
                .id(UUID.randomUUID().toString())
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
            Claims claims = parseClaims(token);
            if (isRevoked(token, claims)) {
                return null;
            }
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
        if (!StringUtils.hasText(token)) {
            return;
        }
        Claims claims;
        try {
            claims = parseClaims(token);
        } catch (Exception ex) {
            return;
        }
        Instant now = Instant.now();
        Instant expiresAt = claims.getExpiration() == null ? now.plus(resolveTtl()) : claims.getExpiration().toInstant();
        Duration ttl = Duration.between(now, expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        String jti = claims.getId();
        if (StringUtils.hasText(jti)) {
            redisTemplate.opsForValue().set(buildRevokedJtiKey(jti), "1", ttl);
        }
        String tokenHash = hashToken(token);
        if (StringUtils.hasText(tokenHash)) {
            redisTemplate.opsForValue().set(buildRevokedHashKey(tokenHash), "1", ttl);
        }
    }

    /**
     * 吊销指定用户的全部 Token。
     *
     * @param userId 用户ID
     * @author 余炘洋
     * @date 2026-01-27
     */
    public void revokeUserTokens(Long userId) {
        if (userId == null) {
            return;
        }
        Duration ttl = resolveTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            ttl = Duration.ofHours(24);
        }
        redisTemplate.opsForValue().set(buildRevokedUserKey(userId), String.valueOf(Instant.now().toEpochMilli()), ttl);
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

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(resolveSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isRevoked(String token, Claims claims) {
        if (isUserRevoked(claims)) {
            return true;
        }
        String jti = claims == null ? null : claims.getId();
        if (StringUtils.hasText(jti) && Boolean.TRUE.equals(redisTemplate.hasKey(buildRevokedJtiKey(jti)))) {
            return true;
        }
        String tokenHash = hashToken(token);
        return StringUtils.hasText(tokenHash) && Boolean.TRUE.equals(redisTemplate.hasKey(buildRevokedHashKey(tokenHash)));
    }

    private boolean isUserRevoked(Claims claims) {
        if (claims == null || claims.getIssuedAt() == null) {
            return false;
        }
        String subject = claims.getSubject();
        if (!StringUtils.hasText(subject)) {
            return false;
        }
        Long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            return false;
        }
        String revokedAt = redisTemplate.opsForValue().get(buildRevokedUserKey(userId));
        if (!StringUtils.hasText(revokedAt)) {
            return false;
        }
        try {
            long revokedMillis = Long.parseLong(revokedAt);
            return claims.getIssuedAt().toInstant().toEpochMilli() < revokedMillis;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String hashToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                String hex = Integer.toHexString(value & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private String buildRevokedJtiKey(String jti) {
        return REVOKED_JTI_PREFIX + jti;
    }

    private String buildRevokedHashKey(String hash) {
        return REVOKED_HASH_PREFIX + hash;
    }

    private String buildRevokedUserKey(Long userId) {
        return REVOKED_USER_PREFIX + userId;
    }
}
