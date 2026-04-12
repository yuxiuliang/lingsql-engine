package com.lingsql.common.exception;

/**
 * 业务错误码定义。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
public enum ErrorCode {
    SUCCESS(200, "success"),
    INVALID_PARAM(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    BUSINESS_ERROR(600, "业务处理失败"),
    TENANT_NOT_FOUND(601, "租户不存在"),
    USER_NOT_FOUND(602, "用户不存在"),
    ROLE_NOT_FOUND(603, "角色不存在"),
    MODEL_NOT_FOUND(604, "模型不存在"),
    MODEL_UNAVAILABLE(605, "模型不可用"),
    TENANT_DB_CONFIG_NOT_FOUND(606, "租户数据源配置不存在"),
    ORG_UNIT_NOT_FOUND(607, "组织节点不存在"),
    ORG_UNIT_TYPE_NOT_FOUND(608, "组织类型不存在"),
    ORG_RELATION_NOT_FOUND(609, "组织关系不存在"),
    CONFIG_INVALID(610, "配置错误"),
    ORG_RELATION_EXECUTION_NOT_FOUND(611, "组织关系执行项不存在"),
    MENU_NOT_FOUND(612, "菜单不存在"),
    SECURITY_VIOLATION(620, "安全校验失败"),
    SQL_INVALID(621, "SQL 校验失败"),
    DATA_SCOPE_INVALID(622, "数据权限规则错误"),
    BILLING_INVALID(630, "计费配置错误"),
    BILLING_BALANCE_INSUFFICIENT(631, "余额不足"),
    BILLING_QUOTA_EXCEEDED(632, "限额触发"),
    BILLING_PLAN_CONFLICT(633, "套餐冲突"),
    AUTH_INVALID(640, "认证失败"),
    TOKEN_INVALID(641, "Token 无效"),
    SYSTEM_ERROR(500, "系统异常，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
