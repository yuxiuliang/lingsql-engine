package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.security.RbacSecurityConstants;
import com.lingsql.rbac.security.RbacUserSession;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.UUID;

/**
 * Token 会话管理服务（JWT + Redis 会话）。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String REVOKED_JTI_PREFIX = RbacSecurityConstants.TOKEN_CACHE_PREFIX + "revoked:jti:";
    private static final String REVOKED_HASH_PREFIX = RbacSecurityConstants.TOKEN_CACHE_PREFIX + "revoked:hash:";
    private static final String REVOKED_USER_PREFIX = RbacSecurityConstants.TOKEN_CACHE_PREFIX + "revoked:user:";
    private static final String SESSION_CACHE_PREFIX = RbacSecurityConstants.TOKEN_CACHE_PREFIX + "session:";
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

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
            throw new BizException(ErrorCode.INVALID_PARAM, "会话数据不能为空");
        }
        Instant now = Instant.now();
        Duration ttl = resolveTtl(session);
        Instant expiresAt = now.plus(ttl);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .header().type("JWT").and()
                .issuer(resolveIssuer())
                .subject(String.valueOf(session.getUserId()))
                .audience().add(resolveAudience()).and()
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(resolveSigningKey())
                .compact();
        if (token.getBytes(StandardCharsets.UTF_8).length > 800) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "Token 长度超过限制");
        }
        cacheSession(jti, session, ttl);
        return token;
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
        TokenParseResult result = parseSession(token);
        return result.status() == TokenStatus.VALID ? result.session() : null;
    }

    /**
     * 解析 Token 并返回状态。
     *
     * @param token Token
     * @return 解析结果
     * @author 余炘洋
     * @date 2026-01-29
     */
    public TokenParseResult parseSession(String token) {
        if (!StringUtils.hasText(token)) {
            return new TokenParseResult(TokenStatus.EMPTY, null);
        }
        try {
            Claims claims = parseClaims(token);
            if (isRevoked(token, claims)) {
                return new TokenParseResult(TokenStatus.REVOKED, null);
            }
            String jti = claims.getId();
            RbacUserSession cached = loadSession(jti);
            if (cached != null) {
                return new TokenParseResult(TokenStatus.VALID, cached);
            }
            log.warn("Token 会话缓存缺失: jti={}", jti);
            return new TokenParseResult(TokenStatus.INVALID, null);
        } catch (ExpiredJwtException ex) {
            return new TokenParseResult(TokenStatus.EXPIRED, null);
        } catch (Exception ex) {
            log.warn("解析 Token 失败: {}", ex.getMessage());
            return new TokenParseResult(TokenStatus.INVALID, null);
        }
    }

    public enum TokenStatus {
        VALID,
        EXPIRED,
        REVOKED,
        INVALID,
        EMPTY
    }

    public record TokenParseResult(TokenStatus status, RbacUserSession session) {
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
        // 会话由 Redis 缓存维护，无需刷新
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
            redisTemplate.delete(buildSessionKey(jti));
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
     * 获取指定会话的过期秒数。
     *
     * @param session 会话
     * @return 过期秒数
     * @author 余炘洋
     * @date 2026-01-27
     */
    public long getExpireSeconds(RbacUserSession session) {
        return resolveTtl(session).getSeconds();
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

    private Duration resolveTtl(RbacUserSession session) {
        if (session != null && session.getRoles() != null && session.getRoles().contains(SUPER_ADMIN_ROLE)) {
            LingSqlProperties.Auth auth = properties.getAuth();
            if (auth != null && auth.getSuperAdminJwtTtl() != null) {
                return auth.getSuperAdminJwtTtl();
            }
            return Duration.ofHours(2);
        }
        return resolveTtl();
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
                throw new BizException(ErrorCode.CONFIG_INVALID, "JWT 密钥未配置");
            }
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                throw new BizException(ErrorCode.CONFIG_INVALID, "JWT 密钥长度不足 32 字符");
            }
            signingKey = Keys.hmacShaKeyFor(keyBytes);
            return signingKey;
        }
    }

    private Claims parseClaims(String token) {
        var parser = Jwts.parser()
                .verifyWith(resolveSigningKey());
        String audience = resolveAudience();
        if (StringUtils.hasText(audience)) {
            parser = parser.requireAudience(audience);
        }
        return parser.build()
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

    private void cacheSession(String jti, RbacUserSession session, Duration ttl) {
        if (!StringUtils.hasText(jti) || session == null || ttl == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(buildSessionKey(jti), json, ttl);
        } catch (Exception ex) {
            log.error("缓存会话失败: jti={}", jti, ex);
            throw new IllegalStateException("会话缓存失败", ex);
        }
    }

    private RbacUserSession loadSession(String jti) {
        if (!StringUtils.hasText(jti)) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(buildSessionKey(jti));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RbacUserSession.class);
        } catch (Exception ex) {
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

    private String buildSessionKey(String jti) {
        return SESSION_CACHE_PREFIX + jti;
    }

    private String resolveAudience() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth == null || !StringUtils.hasText(auth.getJwtAudience())) {
            return "lingsql-engine";
        }
        return auth.getJwtAudience().trim();
    }
}
