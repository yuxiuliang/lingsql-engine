package com.lingsql.common.exception;

import org.springframework.util.StringUtils;

/**
 * 业务异常。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode == null ? null : errorCode.getMessage());
        this.errorCode = errorCode == null ? ErrorCode.BUSINESS_ERROR : errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(StringUtils.hasText(message) ? message : (errorCode == null ? null : errorCode.getMessage()));
        this.errorCode = errorCode == null ? ErrorCode.BUSINESS_ERROR : errorCode;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(StringUtils.hasText(message) ? message : (errorCode == null ? null : errorCode.getMessage()), cause);
        this.errorCode = errorCode == null ? ErrorCode.BUSINESS_ERROR : errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
