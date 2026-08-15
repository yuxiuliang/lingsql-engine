package com.lingsql.tenant.model;

/**
 * 租户 READY 开户回执。
 *
 * <p>回执只表达已提交的开户事实，不返回管理员密码、数据库凭据或 JDBC 地址。</p>
 *
 * @param tenantRecordId 租户记录主键
 * @param tenantId 租户业务标识
 * @param tenantName 租户名称
 * @param status 开户状态
 * @param adminUsername 首个管理员账号
 * @param planName 生效套餐
 * @param openingBalance 开户余额
 * @param isolationMode 数据隔离模式
 * @param loginReady 登录前置条件是否就绪
 * @param billingReady 计费前置条件是否就绪
 * @param dataSourceReady 数据源配置是否就绪
 * @param dataSourceConnectionVerified 数据源连接是否验证
 * @param dataSourceReadOnlyVerified 数据库账号只读权限是否验证
 * @author 余炘洋
 * @date 2026-08-15
 */
public record TenantProvisioningReceipt(
        Long tenantRecordId,
        String tenantId,
        String tenantName,
        String status,
        String adminUsername,
        String planName,
        long openingBalance,
        String isolationMode,
        boolean loginReady,
        boolean billingReady,
        boolean dataSourceReady,
        boolean dataSourceConnectionVerified,
        boolean dataSourceReadOnlyVerified
) {
}
