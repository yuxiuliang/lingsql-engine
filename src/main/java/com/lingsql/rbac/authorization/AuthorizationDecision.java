package com.lingsql.rbac.authorization;

/**
 * 授权会话权威复核结论。
 *
 * @param permitted 是否允许继续使用会话
 * @param reason 拒绝原因编码
 * @author 余炘洋
 * @date 2026-08-16
 */
public record AuthorizationDecision(boolean permitted, String reason) {

    public static AuthorizationDecision permit() {
        return new AuthorizationDecision(true, "AUTHORIZED");
    }

    public static AuthorizationDecision deny(String reason) {
        return new AuthorizationDecision(false, reason);
    }
}
