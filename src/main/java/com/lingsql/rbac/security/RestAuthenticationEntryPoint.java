package com.lingsql.rbac.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.result.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证响应入口，返回标准 JSON。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> payload = resolvePayload(request);
        response.getWriter().write(objectMapper.writeValueAsString(payload));
    }

    private ApiResponse<Void> resolvePayload(HttpServletRequest request) {
        if (request != null && Boolean.TRUE.equals(request.getAttribute(RbacSecurityConstants.TOKEN_REVOKED_ATTRIBUTE))) {
            return ApiResponse.unauthorized("账号已在其他设备登录，请重新登录");
        }
        if (request != null && Boolean.TRUE.equals(request.getAttribute(RbacSecurityConstants.TOKEN_EXPIRED_ATTRIBUTE))) {
            return ApiResponse.unauthorized(RbacSecurityConstants.TOKEN_EXPIRED_MESSAGE);
        }
        return ApiResponse.unauthorized("会话已过期，请重新登录");
    }
}
