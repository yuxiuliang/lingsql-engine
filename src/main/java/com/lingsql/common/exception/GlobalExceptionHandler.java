package com.lingsql.common.exception;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.trace.RequestTraceContext;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

/**
 * 全局异常处理器。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String LOGIN_ERROR_MESSAGE = "账号、密码或租户信息错误";

    /**
     * 处理请求体参数校验异常。
     *
     * @param ex 异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-01-23
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        if (isLoginRequest(request)) {
            return attachRequestId(ApiResponse.unauthorized(LOGIN_ERROR_MESSAGE));
        }
        String message = Optional.ofNullable(ex.getBindingResult().getFieldError())
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        return attachRequestId(ApiResponse.invalid(message));
    }

    /**
     * 处理表单绑定校验异常。
     *
     * @param ex 异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-01-23
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException ex, HttpServletRequest request) {
        if (isLoginRequest(request)) {
            return attachRequestId(ApiResponse.unauthorized(LOGIN_ERROR_MESSAGE));
        }
        String message = Optional.ofNullable(ex.getFieldError())
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        return attachRequestId(ApiResponse.invalid(message));
    }

    /**
     * 处理参数约束校验异常。
     *
     * @param ex 异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-01-23
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        if (isLoginRequest(request)) {
            return attachRequestId(ApiResponse.unauthorized(LOGIN_ERROR_MESSAGE));
        }
        String message = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getMessage())
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse("参数校验失败");
        return attachRequestId(ApiResponse.invalid(message));
    }

    /**
     * 处理认证异常。
     *
     * @param ex 异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-01-23
     */
    @ExceptionHandler(AuthenticationException.class)
    public ApiResponse<Void> handleAuthenticationException(AuthenticationException ex) {
        return attachRequestId(ApiResponse.unauthorized("未登录或登录已过期"));
    }

    /**
     * 处理权限异常。
     *
     * @param ex 异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-01-23
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException ex) {
        return attachRequestId(ApiResponse.forbidden("无权限访问"));
    }

    private <T> ApiResponse<T> attachRequestId(ApiResponse<T> response) {
        if (response == null) {
            return null;
        }
        response.setRequestId(RequestTraceContext.resolveRequestId());
        return response;
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(LOGIN_PATH);
    }
}
