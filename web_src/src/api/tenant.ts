import {request} from '@/utils/request';

/**
 * 租户视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface TenantView {
  id: number;
  tenantId: string;
  tenantName: string;
  status?: number;
  remark?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  expireTime?: string;
  planName?: string;
  quotaBalance?: number;
  serviceLevel?: string;
  tenantType?: string;
  lifecycleState?: string;
  daysToExpire?: number | null;
  graceDaysRemaining?: number;
  riskScore?: number;
  riskLevel?: string;
  riskReasons?: string[];
  createTime?: string;
  updateTime?: string;
}

/**
 * 租户生命周期汇总视图。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
export interface TenantLifecycleSummaryView {
  total: number;
  active: number;
  expiringSoon: number;
  gracePeriod: number;
  suspended: number;
  disabled: number;
  atRisk: number;
  highRisk: number;
  criticalRisk: number;
}

/**
 * 租户创建请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface TenantCreateRequest {
  /**
   * 租户ID（由系统自动生成）。
   *
   * @author 余炘洋
   * @date 2026-01-26
   */
  tenantId?: string;
  tenantName: string;
  status?: number;
  remark?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  expireTime?: string;
  planName?: string;
  jdbcUrl?: string;
  username?: string;
  password?: string;
  driverClass?: string;
  isolationMode?: string;
  logicColumn?: string;
}

/**
 * 租户更新请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface TenantUpdateRequest {
  tenantName?: string;
  status?: number;
  remark?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  expireTime?: string;
  planName?: string;
}

/**
 * 租户续期请求。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
export interface TenantLifecycleExtendRequest {
  expireTime?: string;
  extendDays?: number;
  activate?: boolean;
}

/**
 * 查询租户列表。
 *
 * @return 租户列表
 * @author 余炘洋
 * @date 2026-01-26
 */
export const fetchTenants = async (): Promise<TenantView[]> => {
  return request<TenantView[]>('/api/v1/admin/tenants');
};

/**
 * 创建租户。
 *
 * @param payload 请求体
 * @return 租户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
export const createTenant = async (payload: TenantCreateRequest): Promise<TenantView> => {
  return request<TenantView>('/api/v1/admin/tenants', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新租户。
 *
 * @param id 租户ID
 * @param payload 请求体
 * @return 租户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
export const updateTenant = async (id: number, payload: TenantUpdateRequest): Promise<TenantView> => {
  return request<TenantView>(`/api/v1/admin/tenants/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 删除租户。
 *
 * @param id 租户ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-01-26
 */
export const deleteTenant = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/admin/tenants/${id}`, {
    method: 'DELETE'
  });
};

/**
 * 查询租户生命周期汇总。
 *
 * @return 汇总结果
 * @author 余炘洋
 * @date 2026-02-27
 */
export const fetchTenantLifecycleSummary = async (): Promise<TenantLifecycleSummaryView> => {
  return request<TenantLifecycleSummaryView>('/api/v1/admin/tenants/lifecycle/summary');
};

/**
 * 续期租户生命周期。
 *
 * @param id 租户ID
 * @param payload 续期请求
 * @return 更新后的租户
 * @author 余炘洋
 * @date 2026-02-27
 */
export const extendTenantLifecycle = async (id: number, payload: TenantLifecycleExtendRequest): Promise<TenantView> => {
  return request<TenantView>(`/api/v1/admin/tenants/${id}/lifecycle/extend`, {
    method: 'POST',
    body: JSON.stringify(payload || {})
  });
};

/**
 * 恢复租户服务。
 *
 * @param id 租户ID
 * @return 更新后的租户
 * @author 余炘洋
 * @date 2026-02-27
 */
export const reactivateTenant = async (id: number): Promise<TenantView> => {
  return request<TenantView>(`/api/v1/admin/tenants/${id}/lifecycle/reactivate`, {
    method: 'POST'
  });
};
