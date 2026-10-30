package com.lingsql.common.exception;

import com.lingsql.common.result.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void noResourceFoundShouldReturnNotFoundResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ApiResponse<Void> response = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "/static/admin/index.html", "")
        );

        assertEquals(404, response.getCode());
        assertEquals("资源不存在", response.getMessage());
    }

    @Test
    void httpMessageNotReadableShouldReturnInvalidParamResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/chat/history/1");

        ApiResponse<Void> response = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0])),
                request
        );

        assertEquals(400, response.getCode());
        assertEquals("请求体格式错误", response.getMessage());
    }

    @Test
    void loginHttpMessageNotReadableShouldReturnGenericUnauthorizedResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");

        ApiResponse<Void> response = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0])),
                request
        );

        assertEquals(401, response.getCode());
        assertEquals("账号、密码或租户信息错误", response.getMessage());
    }

    /**
     * 对没有对应映射的方法（例如 DELETE 用户）必须报 405，而不是"系统异常"。
     *
     * <p>2026-10-31 线上实测：`DELETE /api/v1/admin/users/9` 返回 code=500「系统异常，请稍后再试」，
     * 让人以为是服务端坏了，实际只是没实现该接口。</p>
     *
     * @author 余炘洋
     * @date 2026-10-31
     */
    @Test
    void requestMethodNotSupportedShouldReturnMethodNotSupportedResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ApiResponse<Void> response = handler.handleRequestMethodNotSupported(
                new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE")
        );

        assertEquals(405, response.getCode());
        assertEquals("请求方法不支持：DELETE", response.getMessage());
    }
}
