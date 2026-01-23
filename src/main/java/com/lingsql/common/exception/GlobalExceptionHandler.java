package com.lingsql.common.exception;

import com.lingsql.common.result.ApiResponse;
import jakarta.validation.ConstraintViolationException;
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

    /**
     * 处理请求体参数校验异常。
     *
     * @param ex 异常
     * @return 统一响应
     * @author 余炘洋
     * @date 2026-01-23
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String message = Optional.ofNullable(ex.getBindingResult().getFieldError())
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        return ApiResponse.invalid(message);
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
    public ApiResponse<Void> handleBindException(BindException ex) {
        String message = Optional.ofNullable(ex.getFieldError())
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        return ApiResponse.invalid(message);
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
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getMessage())
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse("参数校验失败");
        return ApiResponse.invalid(message);
    }
}
