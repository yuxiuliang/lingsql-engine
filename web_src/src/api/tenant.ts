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
  createTime?: string;
  updateTime?: string;
}

/**
 * 租户创建请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface TenantCreateRequest {
  tenantId: string;
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
