import {request} from '@/utils/request';

/**
 * 租户数据库配置定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface TenantDbConfig {
  id?: number;
  tenantId: string;
  jdbcUrl: string;
  username?: string;
  password?: string;
  driverClass?: string;
  isolationMode?: string;
  logicColumn?: string;
  whiteListTables?: string;
  whiteListColumns?: string;
  sensitiveColumns?: string;
  schemaSyncStatus?: number;
  lastSyncTime?: string;
  status?: number;
}

/**
 * 模型配置定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface AiModelConfig {
  id?: number;
  providerName?: string;
  modelName?: string;
  modelVersion?: string;
  baseUrl?: string;
  apiKey?: string;
  isActive?: number;
  weight?: number;
  priority?: number;
  autoRecover?: number;
  cooldownSeconds?: number;
  fuseTime?: string;
  fuseReason?: string;
}

/**
 * 语义字典定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface SemanticLexiconItem {
  id?: number;
  tenantId?: string;
  term: string;
  mapping: string;
  description?: string;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * Join 规则定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface JoinRuleItem {
  id?: number;
  tenantId?: string;
  leftTable: string;
  rightTable: string;
  onExpr: string;
  description?: string;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 聊天运行时配置定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface ChatRuntimeConfig {
  maxQuestionLength: number;
}

/**
 * 查询租户配置列表。
 *
 * @return 租户配置列表
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchTenantConfigs = async (): Promise<TenantDbConfig[]> => {
  return request<TenantDbConfig[]>('/api/v1/admin/config/tenant');
};

/**
 * 新增租户配置。
 *
 * @param payload 配置数据
 * @return 新增结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const createTenantConfig = async (payload: TenantDbConfig): Promise<TenantDbConfig> => {
  return request<TenantDbConfig>('/api/v1/admin/config/tenant', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新租户配置。
 *
 * @param id 配置ID
 * @param payload 配置数据
 * @return 更新结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const updateTenantConfig = async (id: number, payload: TenantDbConfig): Promise<TenantDbConfig> => {
  return request<TenantDbConfig>(`/api/v1/admin/config/tenant/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 删除租户配置。
 *
 * @param id 配置ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const deleteTenantConfig = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/admin/config/tenant/${id}`, {
    method: 'DELETE'
  });
};

/**
 * 测试租户连接。
 *
 * @param payload 租户配置
 * @return 测试结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const testTenantConnection = async (payload: TenantDbConfig): Promise<string> => {
  return request<string>('/api/v1/admin/config/tenant/test-connection', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 查询模型配置列表。
 *
 * @return 模型配置列表
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchModelConfigs = async (): Promise<AiModelConfig[]> => {
  return request<AiModelConfig[]>('/api/v1/admin/config/model');
};

/**
 * 新增模型配置。
 *
 * @param payload 模型配置
 * @return 新增结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const createModelConfig = async (payload: AiModelConfig): Promise<AiModelConfig> => {
  return request<AiModelConfig>('/api/v1/admin/config/model', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新模型配置。
 *
 * @param id 模型ID
 * @param payload 模型配置
 * @return 更新结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const updateModelConfig = async (id: number, payload: AiModelConfig): Promise<AiModelConfig> => {
  return request<AiModelConfig>(`/api/v1/admin/config/model/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 删除模型配置。
 *
 * @param id 模型ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const deleteModelConfig = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/admin/config/model/${id}`, {
    method: 'DELETE'
  });
};

/**
 * 查询语义字典列表。
 *
 * @return 语义字典列表
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchLexiconItems = async (): Promise<SemanticLexiconItem[]> => {
  return request<SemanticLexiconItem[]>('/api/v1/admin/config/semantic');
};

/**
 * 新增语义字典。
 *
 * @param payload 语义字典
 * @return 新增结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const createLexiconItem = async (payload: SemanticLexiconItem): Promise<SemanticLexiconItem> => {
  return request<SemanticLexiconItem>('/api/v1/admin/config/semantic', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新语义字典。
 *
 * @param id 记录ID
 * @param payload 语义字典
 * @return 更新结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const updateLexiconItem = async (id: number, payload: SemanticLexiconItem): Promise<SemanticLexiconItem> => {
  return request<SemanticLexiconItem>(`/api/v1/admin/config/semantic/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 删除语义字典。
 *
 * @param id 记录ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const deleteLexiconItem = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/admin/config/semantic/${id}`, {
    method: 'DELETE'
  });
};

/**
 * 查询 Join 规则列表。
 *
 * @return Join 规则列表
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchJoinRules = async (): Promise<JoinRuleItem[]> => {
  return request<JoinRuleItem[]>('/api/v1/admin/config/join-rule');
};

/**
 * 新增 Join 规则。
 *
 * @param payload Join 规则
 * @return 新增结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const createJoinRule = async (payload: JoinRuleItem): Promise<JoinRuleItem> => {
  return request<JoinRuleItem>('/api/v1/admin/config/join-rule', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新 Join 规则。
 *
 * @param id 记录ID
 * @param payload Join 规则
 * @return 更新结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const updateJoinRule = async (id: number, payload: JoinRuleItem): Promise<JoinRuleItem> => {
  return request<JoinRuleItem>(`/api/v1/admin/config/join-rule/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 删除 Join 规则。
 *
 * @param id 记录ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const deleteJoinRule = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/admin/config/join-rule/${id}`, {
    method: 'DELETE'
  });
};

/**
 * 查询聊天运行时配置。
 *
 * @return 配置信息
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchChatRuntimeConfig = async (): Promise<ChatRuntimeConfig> => {
  return request<ChatRuntimeConfig>('/api/v1/config/chat');
};
