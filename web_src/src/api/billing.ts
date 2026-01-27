import {request} from '@/utils/request';

/**
 * 计费策略版本定义。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface BillingPolicyVersion {
  id: number;
  versionCode: string;
  status?: number;
  effectiveTime?: string;
  modelUnitCost?: string;
  serviceLevelLimit?: string;
  tenantTypeWeight?: string;
  defaultUnitCost?: number;
  defaultDailyLimit?: number;
  defaultDailyTokenLimit?: number;
  defaultTenantWeight?: number;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 计费策略版本请求。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface BillingPolicyVersionRequest {
  versionCode: string;
  status?: number;
  effectiveTime?: string;
  modelUnitCost?: Record<string, number>;
  serviceLevelLimit?: Record<string, {dailyCallLimit: number; dailyTokenLimit: number}>;
  tenantTypeWeight?: Record<string, number>;
  defaultUnitCost?: number;
  defaultDailyLimit?: number;
  defaultDailyTokenLimit?: number;
  defaultTenantWeight?: number;
  description?: string;
}

/**
 * 租户计费覆盖定义。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface TenantBillingOverride {
  id: number;
  tenantId: string;
  priceMultiplier?: number;
  status?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 租户计费覆盖请求。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface TenantBillingOverrideRequest {
  priceMultiplier?: number;
  status?: number;
  remark?: string;
}

/**
 * 查询计费策略版本列表。
 *
 * @return 版本列表
 * @author 余炘洋
 * @date 2026-01-27
 */
export const fetchBillingPolicies = async (): Promise<BillingPolicyVersion[]> => {
  return request<BillingPolicyVersion[]>('/api/v1/admin/billing/policies');
};

/**
 * 创建计费策略版本。
 *
 * @param payload 请求体
 * @return 创建结果
 * @author 余炘洋
 * @date 2026-01-27
 */
export const createBillingPolicy = async (payload: BillingPolicyVersionRequest): Promise<BillingPolicyVersion> => {
  return request<BillingPolicyVersion>('/api/v1/admin/billing/policies', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新计费策略版本。
 *
 * @param id 版本ID
 * @param payload 请求体
 * @return 更新结果
 * @author 余炘洋
 * @date 2026-01-27
 */
export const updateBillingPolicy = async (id: number, payload: BillingPolicyVersionRequest): Promise<BillingPolicyVersion> => {
  return request<BillingPolicyVersion>(`/api/v1/admin/billing/policies/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 激活计费策略版本。
 *
 * @param id 版本ID
 * @return 生效结果
 * @author 余炘洋
 * @date 2026-01-27
 */
export const activateBillingPolicy = async (id: number): Promise<BillingPolicyVersion> => {
  return request<BillingPolicyVersion>(`/api/v1/admin/billing/policies/${id}/activate`, {
    method: 'POST'
  });
};

/**
 * 查询租户计费覆盖列表。
 *
 * @return 覆盖列表
 * @author 余炘洋
 * @date 2026-01-27
 */
export const fetchBillingOverrides = async (): Promise<TenantBillingOverride[]> => {
  return request<TenantBillingOverride[]>('/api/v1/admin/billing/overrides');
};

/**
 * 查询租户计费覆盖。
 *
 * @param tenantId 租户ID
 * @return 覆盖详情
 * @author 余炘洋
 * @date 2026-01-27
 */
export const fetchBillingOverride = async (tenantId: string): Promise<TenantBillingOverride> => {
  return request<TenantBillingOverride>(`/api/v1/admin/billing/overrides/${tenantId}`);
};

/**
 * 新增或更新租户计费覆盖。
 *
 * @param tenantId 租户ID
 * @param payload 请求体
 * @return 覆盖结果
 * @author 余炘洋
 * @date 2026-01-27
 */
export const upsertBillingOverride = async (
  tenantId: string,
  payload: TenantBillingOverrideRequest
): Promise<TenantBillingOverride> => {
  return request<TenantBillingOverride>(`/api/v1/admin/billing/overrides/${tenantId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};
