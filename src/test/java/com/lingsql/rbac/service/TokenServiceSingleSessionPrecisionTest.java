package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.security.RbacSecurityConstants;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TokenServiceSingleSessionPrecisionTest {

    @Test
    void parseSessionShouldKeepCurrentTokenValidWhenRevokedTimestampInSameSecond() throws Exception {
        LingSqlProperties properties = new LingSqlProperties();
        properties.getAuth().setJwtSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.getAuth().setJwtIssuer("lingsql-engine");
        properties.getAuth().setJwtAudience("lingsql-engine");
        properties.getAuth().setJwtTtl(Duration.ofHours(1));

        Map<String, String> redisStore = new ConcurrentHashMap<>();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redisStore.get(invocation.getArgument(0)));
        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation -> redisStore.containsKey(invocation.getArgument(0)));
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> redisStore.remove(invocation.getArgument(0)) != null);

        TokenService tokenService = new TokenService(properties, new ObjectMapper(), redisTemplate);
        RbacUserSession session = new RbacUserSession();
        session.setUserId(101L);
        session.setTenantId("tenant-a");
        session.setRoles(List.of("TENANT_ADMIN"));
        session.setPermissions(List.of("MENU:CHAT"));

        TokenWithIat tokenWithIat = issueTokenWithNonZeroMillis(tokenService);
        String revokedUserKey = RbacSecurityConstants.TOKEN_CACHE_PREFIX + "revoked:user:" + session.getUserId();
        redisStore.put(revokedUserKey, String.valueOf(tokenWithIat.iatMillis() - 1));

        TokenService.TokenParseResult result = tokenService.parseSession(tokenWithIat.token());

        Assertions.assertEquals(TokenService.TokenStatus.VALID, result.status());
        Assertions.assertNotNull(result.session());
        Assertions.assertEquals(session.getUserId(), result.session().getUserId());
    }

    private TokenWithIat issueTokenWithNonZeroMillis(TokenService tokenService) throws Exception {
        for (int i = 0; i < 20; i++) {
            RbacUserSession session = new RbacUserSession();
            session.setUserId(101L);
            session.setTenantId("tenant-a");
            session.setRoles(List.of("TENANT_ADMIN"));
            session.setPermissions(List.of("MENU:CHAT"));
            String token = tokenService.createToken(session);
            long iatMillis = extractIatMillis(token);
            long iatSecondsMillis = extractIatSeconds(token) * 1000L;
            if (iatMillis - iatSecondsMillis >= 2L) {
                return new TokenWithIat(token, iatMillis);
            }
        }
        throw new IllegalStateException("未生成包含毫秒精度的测试 Token");
    }

    private long extractIatSeconds(String token) throws Exception {
        JsonNode payload = readPayload(token);
        return payload.path("iat").asLong();
    }

    private long extractIatMillis(String token) throws Exception {
        JsonNode payload = readPayload(token);
        return payload.path("iatMs").asLong();
    }

    private JsonNode readPayload(String token) throws Exception {
        String[] segments = token.split("\\.");
        String payloadSegment = segments[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payloadSegment);
        return new ObjectMapper().readTree(new String(decoded, StandardCharsets.UTF_8));
    }

    private record TokenWithIat(String token, long iatMillis) {
    }
}
