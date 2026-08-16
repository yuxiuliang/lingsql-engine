package com.lingsql.rbac.authorization;

/**
 * 授权会话失效加速端口，仅用于数据库提交后的 Redis 清理。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
interface IAuthorizationSessionAccelerator {

    void expire(AuthorizationChange change);
}
