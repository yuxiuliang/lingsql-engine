package com.lingsql.rbac.service;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.rbac.authorization.AuthorizationStamp;
import com.lingsql.rbac.security.RbacSecurityConstants;
import com.lingsql.rbac.security.RbacUserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh Token 管理服务。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REFRESH_TOKEN_PREFIX = RbacSecurityConstants.TOKEN_CACHE_PREFIX + "refresh:";
    private static final String REFRESH_USER_PREFIX = RbacSecurityConstants.TOKEN_CACHE_PREFIX + "refresh:user:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RefreshTokenResult rotateRefreshToken(String refreshToken, Duration ttl) {
        RefreshTokenPayload payload = verify(refreshToken);
        if (payload == null) {
            return null;
        }
        revoke(refreshToken, payload.userId());
        String newToken = issueRefreshToken(
                payload.userId(),
                payload.tenantId(),
                resolveCredentialVersion(payload.credentialVersion()),
                payload.authorizationStamp(),
                ttl
        );
        return new RefreshTokenResult(payload, newToken);
    }

    public String issueRefreshToken(RbacUserSession session, Duration ttl) {
        if (session == null || session.getUserId() == null) {
            return null;
        }
        return issueRefreshToken(
                session.getUserId(),
                session.getTenantId(),
                resolveCredentialVersion(session.getCredentialVersion()),
                session.getAuthorizationStamp(),
                ttl
        );
    }

    public void revokeByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        String userKey = buildUserKey(userId);
        String hash = redisTemplate.opsForValue().get(userKey);
        if (StringUtils.hasText(hash)) {
            redisTemplate.delete(buildRefreshKey(hash));
        }
        redisTemplate.delete(userKey);
    }

    public void revoke(String refreshToken, Long userId) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        String hash = hashToken(refreshToken);
        if (!StringUtils.hasText(hash)) {
            return;
        }
        redisTemplate.delete(buildRefreshKey(hash));
        if (userId != null) {
            redisTemplate.delete(buildUserKey(userId));
        }
    }

    public RefreshTokenPayload verify(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        String hash = hashToken(refreshToken);
        if (!StringUtils.hasText(hash)) {
            return null;
        }
        String raw = redisTemplate.opsForValue().get(buildRefreshKey(hash));
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            RefreshTokenPayload payload = objectMapper.readValue(raw, RefreshTokenPayload.class);
            if (payload == null || payload.userId() == null
                    || payload.credentialVersion() == null || payload.credentialVersion() < 1L
                    || payload.authorizationStamp() == null || !payload.authorizationStamp().isValid()) {
                return null;
            }
            String currentHash = redisTemplate.opsForValue().get(buildUserKey(payload.userId()));
            if (!StringUtils.hasText(currentHash) || !currentHash.equals(hash)) {
                return null;
            }
            return payload;
        } catch (Exception ex) {
            log.warn("Refresh Token 解析失败", ex);
            return null;
        }
    }

    private String issueRefreshToken(Long userId,
                                     String tenantId,
                                     long credentialVersion,
                                     AuthorizationStamp authorizationStamp,
                                     Duration ttl) {
        if (userId == null || authorizationStamp == null || !authorizationStamp.isValid()) {
            return null;
        }
        Duration resolvedTtl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofDays(7) : ttl;
        String refreshToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String hash = hashToken(refreshToken);
        if (!StringUtils.hasText(hash)) {
            return null;
        }
        RefreshTokenPayload payload = new RefreshTokenPayload(
                userId,
                tenantId,
                Instant.now().toEpochMilli(),
                credentialVersion,
                authorizationStamp
        );
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(buildRefreshKey(hash), json, resolvedTtl);
            redisTemplate.opsForValue().set(buildUserKey(userId), hash, resolvedTtl);
            return refreshToken;
        } catch (Exception ex) {
            log.warn("Refresh Token 写入失败", ex);
            return null;
        }
    }

    private String buildRefreshKey(String hash) {
        return REFRESH_TOKEN_PREFIX + hash;
    }

    private String buildUserKey(Long userId) {
        return REFRESH_USER_PREFIX + userId;
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

    private long resolveCredentialVersion(Long credentialVersion) {
        return credentialVersion == null || credentialVersion < 1L ? 1L : credentialVersion;
    }

    /**
     * Refresh Token 载荷。
     * credentialVersion 必须存在；升级前的旧 Token 必须重新登录。
     */
    public record RefreshTokenPayload(Long userId,
                                      String tenantId,
                                      long issuedAt,
                                      Long credentialVersion,
                                      AuthorizationStamp authorizationStamp) {
    }

    public record RefreshTokenResult(RefreshTokenPayload payload, String refreshToken) {
    }
}
