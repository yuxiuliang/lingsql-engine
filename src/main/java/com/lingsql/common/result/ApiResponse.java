package com.lingsql.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
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

    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(500, msg, null);
    }
}
