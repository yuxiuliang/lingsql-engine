import {request} from '@/utils/request';

/**
 * 租户模型授权路由。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
export interface TenantModelRouteView {
  id: number;
  tenantId: string;
  modelConfigId: number;
  providerName?: string;
  modelName?: string;
  modelVersion?: string;
  priority: number;
  weight: number;
  status: number;
}

/**
 * 租户模型授权替换命令。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
export interface TenantModelRouteCommand {
  modelConfigId: number;
  priority: number;
  weight: number;
}

/**
 * 查询租户模型授权路由。
 *
 * @param tenantId 租户标识
 * @return 授权路由列表
 */
export const fetchTenantModelRoutes = async (tenantId: string): Promise<TenantModelRouteView[]> => {
  return request<TenantModelRouteView[]>(
    `/api/v1/admin/tenant-model-routes/${encodeURIComponent(tenantId)}`
  );
};

/**
 * 原子替换租户的全部模型授权路由。
 *
 * @param tenantId 租户标识
 * @param commands 授权路由集合
 * @return 生效后的授权路由
 */
export const replaceTenantModelRoutes = async (
  tenantId: string,
  commands: TenantModelRouteCommand[]
): Promise<TenantModelRouteView[]> => {
  return request<TenantModelRouteView[]>(
    `/api/v1/admin/tenant-model-routes/${encodeURIComponent(tenantId)}`,
    {
      method: 'PUT',
      body: JSON.stringify(commands)
    }
  );
};
