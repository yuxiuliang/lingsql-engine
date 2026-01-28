package com.lingsql.common.result;

import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.trace.RequestTraceContext;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Data
@NoArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String requestId;

    public ApiResponse(int code, String message, T data) {
        this(code, message, data, resolveRequestId());
    }

    public ApiResponse(int code, String message, T data, String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        if (errorCode == null) {
            return new ApiResponse<>(500, "系统异常，请稍后再试", null);
        }
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        if (errorCode == null) {
            return new ApiResponse<>(500, resolveMessage(message, "系统异常，请稍后再试"), null);
        }
        return new ApiResponse<>(errorCode.getCode(), resolveMessage(message, errorCode.getMessage()), null);
    }

    /**
     * 参数校验失败响应。
     *
     * @param msg 错误信息
     * @return 响应体
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static <T> ApiResponse<T> invalid(String msg) {
        return new ApiResponse<>(400, msg, null);
    }

    /**
     * 未授权响应。
     *
     * @param msg 错误信息
     * @return 响应体
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static <T> ApiResponse<T> unauthorized(String msg) {
        return new ApiResponse<>(401, msg, null);
    }

    /**
     * 无权限响应。
     *
     * @param msg 错误信息
     * @return 响应体
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static <T> ApiResponse<T> forbidden(String msg) {
        return new ApiResponse<>(403, msg, null);
    }

    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(500, msg, null);
    }

    /**
     * 请求过于频繁响应。
     *
     * @param msg 错误信息
     * @return 响应体
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static <T> ApiResponse<T> tooManyRequests(String msg) {
        return new ApiResponse<>(429, msg, null);
    }

    private static String resolveMessage(String message, String fallback) {
        if (StringUtils.hasText(message)) {
            return message;
        }
        return fallback;
    }

    private static String resolveRequestId() {
        String requestId = RequestTraceContext.getRequestId();
        return StringUtils.hasText(requestId) ? requestId : RequestTraceContext.resolveRequestId();
    }
}
