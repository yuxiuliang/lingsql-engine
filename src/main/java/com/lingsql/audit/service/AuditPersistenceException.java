package com.lingsql.audit.service;

/**
 * 审计记录无法持久化或进入持久补偿队列时抛出的异常。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class AuditPersistenceException extends RuntimeException {

    public AuditPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
