package com.lingsql.rbac.authorization;

/**
 * 授权权威状态不可用异常。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
public class AuthorizationStateUnavailableException extends RuntimeException {

    public AuthorizationStateUnavailableException(String message) {
        super(message);
    }

    public AuthorizationStateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
