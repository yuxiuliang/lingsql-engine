package com.lingsql.common.exception;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.trace.RequestTraceContext;
import com.lingsql.executor.SqlExecutionException;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

/**
 * 全局异常处理器。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
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
            return attachRequestId(ApiResponse.fail(ErrorCode.UNAUTHORIZED, LOGIN_ERROR_MESSAGE));
        }
        String message = Optional.ofNullable(ex.getBindingResult().getFieldError())
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        return attachRequestId(ApiResponse.fail(ErrorCode.INVALID_PARAM, message));
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
            return attachRequestId(ApiResponse.fail(ErrorCode.UNAUTHORIZED, LOGIN_ERROR_MESSAGE));
        }
        String message = Optional.ofNullable(ex.getFieldError())
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        return attachRequestId(ApiResponse.fail(ErrorCode.INVALID_PARAM, message));
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
            return attachRequestId(ApiResponse.fail(ErrorCode.UNAUTHORIZED, LOGIN_ERROR_MESSAGE));
        }
        String message = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getMessage())
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse("参数校验失败");
        return attachRequestId(ApiResponse.fail(ErrorCode.INVALID_PARAM, message));
    }

    /**
     * 处理请求体无法解析异常。
     *
     * @param ex 异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-04-11
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (isLoginRequest(request)) {
            return attachRequestId(ApiResponse.fail(ErrorCode.UNAUTHORIZED, LOGIN_ERROR_MESSAGE));
        }
        return attachRequestId(ApiResponse.fail(ErrorCode.INVALID_PARAM, "请求体格式错误"));
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
    public ApiResponse<Void> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        if (isLoginRequest(request)) {
            return attachRequestId(ApiResponse.fail(ErrorCode.UNAUTHORIZED, LOGIN_ERROR_MESSAGE));
        }
        if (isRefreshRequest(request)) {
            return attachRequestId(ApiResponse.fail(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录"));
        }
        return attachRequestId(ApiResponse.fail(ErrorCode.UNAUTHORIZED, "未登录或登录已过期"));
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
        return attachRequestId(ApiResponse.fail(ErrorCode.FORBIDDEN, "无权限访问"));
    }

    /**
     * 处理业务异常。
     *
     * @param ex 业务异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-01-28
     */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        if (errorCode == null) {
            errorCode = ErrorCode.BUSINESS_ERROR;
        }
        if (errorCode == ErrorCode.SYSTEM_ERROR || errorCode.getCode() >= 500) {
            log.error("业务异常: {}", ex.getMessage(), ex);
        } else {
            log.warn("业务异常: {}", ex.getMessage());
        }
        return attachRequestId(ApiResponse.fail(errorCode, ex.getMessage()));
    }

    /**
     * 处理 SQL 执行异常。
     *
     * @param ex SQL 异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-01-28
     */
    @ExceptionHandler(SqlExecutionException.class)
    public ApiResponse<Void> handleSqlExecutionException(SqlExecutionException ex) {
        log.warn("SQL 执行异常: {}", ex.getMessage());
        return attachRequestId(ApiResponse.fail(ErrorCode.SQL_INVALID, "SQL 执行失败，请检查查询条件"));
    }

    /**
     * 处理静态资源不存在异常。
     *
     * @param ex 异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-02-26
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.debug("资源不存在: {}", ex.getMessage());
        return attachRequestId(ApiResponse.fail(ErrorCode.NOT_FOUND, "资源不存在"));
    }

    /**
     * 处理未捕获异常。
     *
     * @param ex 异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-01-28
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpectedException(Exception ex) {
        log.error("系统异常: {}", ex.getMessage(), ex);
        return attachRequestId(ApiResponse.fail(ErrorCode.SYSTEM_ERROR, "系统异常，请稍后再试"));
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

    private boolean isRefreshRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(REFRESH_PATH);
    }
}
