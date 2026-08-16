package com.lingsql.rbac.authorization;

import java.util.function.Supplier;

/**
 * 授权会话生命周期深模块入口。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
public interface IAuthorizationSessionLifecycle {

    /**
     * 在同一事务内发布授权事实变更并递增权威版本。
     *
     * @param change  授权变更
     * @param mutation 授权事实写操作
     * @param <T> 返回类型
     * @return 写操作结果
     */
    <T> T publish(AuthorizationChange change, Supplier<T> mutation);

    /**
     * 捕获用户当前的授权版本指纹。
     *
     * @param userId 用户ID
     * @param tenantId 租户标识
     * @return 授权版本指纹
     */
    AuthorizationStamp capture(Long userId, String tenantId);

    /**
     * 逐请求校验授权版本指纹。
     *
     * @param userId 用户ID
     * @param tenantId 租户标识
     * @param stamp 会话签发时的授权版本指纹
     * @return 校验结论
     */
    AuthorizationDecision validate(Long userId, String tenantId, AuthorizationStamp stamp);
}
