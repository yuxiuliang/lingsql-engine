package com.lingsql.rbac.security;

import com.lingsql.config.LingSqlProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitClientIpResolverTest {

    @Test
    void shouldUseRemoteAddrWhenProxyTrustDisabled() {
        LingSqlProperties.Security.RateLimit config = new LingSqlProperties.Security.RateLimit();
        config.setTrustProxyHeaders(false);
        config.setTrustedProxyIps(List.of("127.0.0.1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        String clientIp = RateLimitClientIpResolver.resolve(request, config);

        assertEquals("10.10.10.10", clientIp);
    }

    @Test
    void shouldUseRemoteAddrWhenProxyNotTrusted() {
        LingSqlProperties.Security.RateLimit config = new LingSqlProperties.Security.RateLimit();
        config.setTrustProxyHeaders(true);
        config.setTrustedProxyIps(List.of("127.0.0.1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        String clientIp = RateLimitClientIpResolver.resolve(request, config);

        assertEquals("10.10.10.10", clientIp);
    }

    @Test
    void shouldResolveForwardedForWhenProxyTrusted() {
        LingSqlProperties.Security.RateLimit config = new LingSqlProperties.Security.RateLimit();
        config.setTrustProxyHeaders(true);
        config.setTrustedProxyIps(List.of("127.0.0.1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.2");

        String clientIp = RateLimitClientIpResolver.resolve(request, config);

        assertEquals("203.0.113.7", clientIp);
    }

    @Test
    void shouldFallbackToXRealIpWhenForwardedForMissing() {
        LingSqlProperties.Security.RateLimit config = new LingSqlProperties.Security.RateLimit();
        config.setTrustProxyHeaders(true);
        config.setTrustedProxyIps(List.of("127.0.0.1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.3");

        String clientIp = RateLimitClientIpResolver.resolve(request, config);

        assertEquals("198.51.100.3", clientIp);
    }
}

