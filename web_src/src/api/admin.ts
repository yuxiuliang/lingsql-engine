import {request} from '@/utils/request';

/**
 * 模型健康视图定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface ModelHealthView {
  id: number;
  providerName: string;
  modelName: string;
  modelVersion?: string;
  baseUrl: string;
  isActive: number;
  autoRecover: number;
  weight: number;
  priority: number;
  cooldownSeconds: number;
  fuseTime?: string;
  fuseReason?: string;
  failureCount: number;
  tested: boolean;
  fused: boolean;
  lastFailReason?: string;
  lastFailTime?: string;
  lastCostMs?: number;
  probeStatus: 'HEALTHY' | 'FAILED' | 'UNTESTED';
  lastProbeTime?: string;
  lastProbeCostMs?: number;
  lastProbeErrorCode?: string;
}

export interface ModelProbeView {
  modelId: number;
  status: 'HEALTHY' | 'FAILED';
  checkedAt: string;
  costMs: number;
  errorCode?: string;
  message: string;
}

/**
 * Schema 表结构定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface SchemaTableInfo {
  tableName: string;
  tableComment?: string;
  columns: Array<{
    columnName: string;
    columnComment?: string;
    dataType?: string;
    primaryKey?: boolean;
  }>;
}

/**
 * 手动同步租户元数据。
 *
 * @param tenantId 租户标识
 * @return 执行结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const syncTenantSchema = async (tenantId: string): Promise<string> => {
  return request<string>(`/api/v1/admin/sync-schema/${encodeURIComponent(tenantId)}`, {
    method: 'POST'
  });
};

/**
 * 刷新租户语义字典缓存。
 *
 * @param tenantId 租户标识
 * @return 执行结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const refreshTenantSemantic = async (tenantId: string): Promise<string> => {
  return request<string>(`/api/v1/admin/refresh-semantic/${encodeURIComponent(tenantId)}`, {
    method: 'POST'
  });
};

/**
 * 查询模型健康状态。
 *
 * @return 模型健康列表
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchModelHealth = async (): Promise<ModelHealthView[]> => {
  return request<ModelHealthView[]>('/api/v1/admin/model/health');
};

export const probeModel = async (modelId: number): Promise<ModelProbeView> => {
  return request<ModelProbeView>(`/api/v1/admin/model/${modelId}/probe`, {
    method: 'POST'
  });
};

/**
 * 启用模型。
 *
 * @param modelId 模型ID
 * @return 执行结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const enableModel = async (modelId: number): Promise<string> => {
  return request<string>(`/api/v1/admin/model/enable/${modelId}`, {
    method: 'POST'
  });
};

/**
 * 禁用模型。
 *
 * @param modelId 模型ID
 * @return 执行结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const disableModel = async (modelId: number): Promise<string> => {
  return request<string>(`/api/v1/admin/model/disable/${modelId}`, {
    method: 'POST'
  });
};

/**
 * 获取租户 Schema 缓存。
 *
 * @param tenantId 租户标识
 * @return Schema 列表
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchTenantSchema = async (tenantId: string): Promise<SchemaTableInfo[]> => {
  return request<SchemaTableInfo[]>(`/api/v1/admin/schema/${encodeURIComponent(tenantId)}`);
};
