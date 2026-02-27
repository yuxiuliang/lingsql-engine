package com.lingsql.rbac.security;

import com.lingsql.config.LingSqlProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenCookieManagerTest {

    @Test
    void shouldWriteRefreshTokenCookie() {
        LingSqlProperties properties = new LingSqlProperties();
        RefreshTokenCookieManager manager = new RefreshTokenCookieManager(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.writeRefreshTokenCookie(request, response, "refresh_token_value", 3600L);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("lingsql_refresh_token=refresh_token_value"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Max-Age=3600"));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        assertTrue(setCookie.contains("SameSite=Lax"));
    }

    @Test
    void shouldResolveRefreshTokenFromCookie() {
        LingSqlProperties properties = new LingSqlProperties();
        RefreshTokenCookieManager manager = new RefreshTokenCookieManager(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("lingsql_refresh_token", "refresh_from_cookie"));

        String refreshToken = manager.resolveRefreshToken(request);

        assertEquals("refresh_from_cookie", refreshToken);
    }

    @Test
    void shouldClearRefreshTokenCookie() {
        LingSqlProperties properties = new LingSqlProperties();
        RefreshTokenCookieManager manager = new RefreshTokenCookieManager(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.clearRefreshTokenCookie(request, response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("lingsql_refresh_token="));
        assertTrue(setCookie.contains("Max-Age=0"));
        assertTrue(setCookie.contains("HttpOnly"));
    }
}

