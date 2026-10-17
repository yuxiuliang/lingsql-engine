import {request} from '@/utils/request';

/**
 * Agent 插件治理视图。
 *
 * <p>只包含稳定元数据，不包含插件实例、类名或内部实现。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
export interface AgentPluginView {
  pluginId: string;
  version: string;
  access: string;
  costLevel: string;
  idempotent: boolean;
  grantable: boolean;
  granted: boolean | null;
  description: string;
}

/**
 * 查询插件目录（全局，不含租户授权状态）。
 *
 * @return 插件元数据列表
 */
export const fetchPlugins = async (): Promise<AgentPluginView[]> => {
  return request<AgentPluginView[]>('/api/v1/admin/plugins');
};

/**
 * 查询租户插件授权视图。
 *
 * @param tenantId 租户标识
 * @return 合并授权状态后的插件列表
 */
export const fetchTenantPlugins = async (tenantId: string): Promise<AgentPluginView[]> => {
  return request<AgentPluginView[]>(`/api/v1/admin/tenants/${encodeURIComponent(tenantId)}/plugins`);
};

/**
 * 维护租户插件授权。
 *
 * @param tenantId 租户标识
 * @param pluginId 插件标识
 * @param granted  是否授权
 * @return 更新后的插件视图
 */
export const updateTenantPluginGrant = async (
  tenantId: string,
  pluginId: string,
  granted: boolean
): Promise<AgentPluginView> => {
  return request<AgentPluginView>(
    `/api/v1/admin/tenants/${encodeURIComponent(tenantId)}/plugins/${encodeURIComponent(pluginId)}`,
    {
      method: 'PUT',
      body: JSON.stringify({granted})
    }
  );
};
