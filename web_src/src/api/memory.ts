import {request} from '@/utils/request';

/**
 * 记忆层级：租户程序记忆或用户永久记忆。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
export type AgentMemoryTier = 'PROCEDURAL' | 'PERMANENT';

/**
 * 记忆状态：候选与生效记录共用同一状态机。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
export type AgentMemoryStatus =
  | 'ACTIVE'
  | 'CANDIDATE'
  | 'APPROVED'
  | 'REJECTED'
  | 'SUPERSEDED'
  | 'REVOKED'
  | 'EXPIRED';

/**
 * 记忆敏感级别：敏感候选禁止采纳。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
export type AgentMemorySensitivity = 'NORMAL' | 'SENSITIVE';

/**
 * 长期记忆视图。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
export interface AgentMemoryView {
  id: number;
  tenantId: string;
  userId: number | null;
  tier: string;
  tierLabel: string;
  factText: string;
  sourceType: string;
  sourceLabel: string;
  status: string;
  statusLabel: string;
  confidence: number;
  version: number;
  supersedesId: number | null;
  createdBy: string | null;
  sourceRef: string | null;
  sensitivity: string;
  sensitivityLabel: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  approvedMemoryId: number | null;
  rejectReason: string | null;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * 长期记忆导出视图。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
export interface AgentMemoryExportView {
  tenantId: string;
  userId: number | null;
  scope: string;
  total: number;
  exportedAt: string;
  memories: AgentMemoryView[];
}

/**
 * 写入或纠正记忆的请求体。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
export interface AgentMemoryWriteRequest {
  factText: string;
  retentionDays?: number;
}

/**
 * 管理端写入记忆的请求体。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
export interface AgentMemoryAdminWriteRequest extends AgentMemoryWriteRequest {
  tenantId: string;
  userId?: number;
  tier: AgentMemoryTier;
}

/**
 * 候选提取请求。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
export interface AgentMemoryExtractRequest {
  sourceRef?: string;
  maxCandidates?: number;
}

/**
 * 候选拒绝请求，原因可为空。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
export interface AgentMemoryRejectRequest {
  reason?: string;
}

/**
 * 查询参数候选值。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
type QueryValue = string | number | null | undefined;

/**
 * 手工拼接查询串，空值不参与拼接。
 *
 * @param params 查询参数
 * @return 带前导问号的查询串，无参数时为空字符串
 * @author 余炘洋
 * @date 2026-10-19
 */
const buildQuery = (params: Record<string, QueryValue>): string => {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === null || value === undefined) {
      return;
    }
    const text = String(value).trim();
    if (!text) {
      return;
    }
    query.set(key, text);
  });
  const suffix = query.toString();
  return suffix ? `?${suffix}` : '';
};

/**
 * 查询我的生效记忆。
 *
 * @return 记忆列表
 * @author 余炘洋
 * @date 2026-10-19
 */
export const fetchMyMemories = async (): Promise<AgentMemoryView[]> => {
  return request<AgentMemoryView[]>('/api/v1/chat/memories');
};

/**
 * 新增我的永久记忆。
 *
 * @param payload 写入请求
 * @return 新建的记忆
 * @author 余炘洋
 * @date 2026-10-19
 */
export const createMyMemory = async (payload: AgentMemoryWriteRequest): Promise<AgentMemoryView> => {
  return request<AgentMemoryView>('/api/v1/chat/memories', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 纠正我的记忆，旧事实失效并生成新版本。
 *
 * @param id      记忆主键
 * @param payload 纠正请求
 * @return 新版本记忆
 * @author 余炘洋
 * @date 2026-10-19
 */
export const correctMyMemory = async (
  id: number,
  payload: AgentMemoryWriteRequest
): Promise<AgentMemoryView> => {
  return request<AgentMemoryView>(`/api/v1/chat/memories/${encodeURIComponent(String(id))}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 撤销我的记忆。
 *
 * @param id 记忆主键
 * @return 撤销后的记忆
 * @author 余炘洋
 * @date 2026-10-19
 */
export const revokeMyMemory = async (id: number): Promise<AgentMemoryView> => {
  return request<AgentMemoryView>(`/api/v1/chat/memories/${encodeURIComponent(String(id))}`, {
    method: 'DELETE'
  });
};

/**
 * 查询我的待确认候选。
 *
 * @return 候选列表
 * @author 余炘洋
 * @date 2026-10-19
 */
export const fetchMyMemoryCandidates = async (): Promise<AgentMemoryView[]> => {
  return request<AgentMemoryView[]>('/api/v1/chat/memories/candidates');
};

/**
 * 从当前会话窗口提炼记忆候选。
 *
 * @param payload 提取请求
 * @return 本次新增的候选
 * @author 余炘洋
 * @date 2026-10-19
 */
export const extractMyMemoryCandidates = async (
  payload: AgentMemoryExtractRequest = {}
): Promise<AgentMemoryView[]> => {
  return request<AgentMemoryView[]>('/api/v1/chat/memories/candidates/extract', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 采纳我的记忆候选。
 *
 * @param id 候选主键
 * @return 生成的生效记忆
 * @author 余炘洋
 * @date 2026-10-19
 */
export const approveMyMemoryCandidate = async (id: number): Promise<AgentMemoryView> => {
  return request<AgentMemoryView>(
    `/api/v1/chat/memories/candidates/${encodeURIComponent(String(id))}/approve`,
    {method: 'POST'}
  );
};

/**
 * 拒绝我的记忆候选。
 *
 * @param id      候选主键
 * @param payload 拒绝请求
 * @return 候选记录
 * @author 余炘洋
 * @date 2026-10-19
 */
export const rejectMyMemoryCandidate = async (
  id: number,
  payload: AgentMemoryRejectRequest = {}
): Promise<AgentMemoryView> => {
  return request<AgentMemoryView>(
    `/api/v1/chat/memories/candidates/${encodeURIComponent(String(id))}/reject`,
    {
      method: 'POST',
      body: JSON.stringify(payload)
    }
  );
};

/**
 * 导出我的长期记忆与候选。
 *
 * @return 导出视图
 * @author 余炘洋
 * @date 2026-10-19
 */
export const exportMyMemories = async (): Promise<AgentMemoryExportView> => {
  return request<AgentMemoryExportView>('/api/v1/chat/memories/export');
};

/**
 * 管理端查询租户长期记忆。
 *
 * @param params 过滤条件
 * @return 记忆列表
 * @author 余炘洋
 * @date 2026-10-19
 */
export const fetchAdminMemories = async (params: {
  tenantId: string;
  userId?: number;
  tier?: string;
  status?: string;
}): Promise<AgentMemoryView[]> => {
  const suffix = buildQuery({
    tenantId: params.tenantId,
    userId: params.userId,
    tier: params.tier,
    status: params.status
  });
  return request<AgentMemoryView[]>(`/api/v1/admin/agent-memories${suffix}`);
};

/**
 * 管理端写入程序记忆或用户永久记忆。
 *
 * @param payload 写入请求
 * @return 新建的记忆
 * @author 余炘洋
 * @date 2026-10-19
 */
export const createAdminMemory = async (
  payload: AgentMemoryAdminWriteRequest
): Promise<AgentMemoryView> => {
  return request<AgentMemoryView>('/api/v1/admin/agent-memories', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 管理端纠正租户记忆。
 *
 * @param id       记忆主键
 * @param tenantId 租户标识
 * @param payload  纠正请求
 * @return 新版本记忆
 * @author 余炘洋
 * @date 2026-10-19
 */
export const correctAdminMemory = async (
  id: number,
  tenantId: string,
  payload: AgentMemoryWriteRequest
): Promise<AgentMemoryView> => {
  const suffix = buildQuery({tenantId});
  return request<AgentMemoryView>(
    `/api/v1/admin/agent-memories/${encodeURIComponent(String(id))}${suffix}`,
    {
      method: 'PUT',
      body: JSON.stringify(payload)
    }
  );
};

/**
 * 管理端撤销租户记忆。
 *
 * @param id       记忆主键
 * @param tenantId 租户标识
 * @return 撤销后的记忆
 * @author 余炘洋
 * @date 2026-10-19
 */
export const revokeAdminMemory = async (id: number, tenantId: string): Promise<AgentMemoryView> => {
  const suffix = buildQuery({tenantId});
  return request<AgentMemoryView>(
    `/api/v1/admin/agent-memories/${encodeURIComponent(String(id))}${suffix}`,
    {method: 'DELETE'}
  );
};

/**
 * 管理端查询租户内待确认候选。
 *
 * @param tenantId 租户标识
 * @param userId   用户标识，可为空
 * @return 候选列表
 * @author 余炘洋
 * @date 2026-10-19
 */
export const fetchAdminMemoryCandidates = async (
  tenantId: string,
  userId?: number
): Promise<AgentMemoryView[]> => {
  const suffix = buildQuery({tenantId, userId});
  return request<AgentMemoryView[]>(`/api/v1/admin/agent-memories/candidates${suffix}`);
};

/**
 * 管理端批准记忆候选。
 *
 * @param id       候选主键
 * @param tenantId 租户标识
 * @return 生成的生效记忆
 * @author 余炘洋
 * @date 2026-10-19
 */
export const approveAdminMemoryCandidate = async (
  id: number,
  tenantId: string
): Promise<AgentMemoryView> => {
  const suffix = buildQuery({tenantId});
  return request<AgentMemoryView>(
    `/api/v1/admin/agent-memories/candidates/${encodeURIComponent(String(id))}/approve${suffix}`,
    {method: 'POST'}
  );
};

/**
 * 管理端拒绝记忆候选。
 *
 * @param id       候选主键
 * @param tenantId 租户标识
 * @param payload  拒绝请求
 * @return 候选记录
 * @author 余炘洋
 * @date 2026-10-19
 */
export const rejectAdminMemoryCandidate = async (
  id: number,
  tenantId: string,
  payload: AgentMemoryRejectRequest = {}
): Promise<AgentMemoryView> => {
  const suffix = buildQuery({tenantId});
  return request<AgentMemoryView>(
    `/api/v1/admin/agent-memories/candidates/${encodeURIComponent(String(id))}/reject${suffix}`,
    {
      method: 'POST',
      body: JSON.stringify(payload)
    }
  );
};

/**
 * 管理端彻底清除记忆，不可恢复。
 *
 * @param id       记忆主键
 * @param tenantId 租户标识
 * @return 空响应
 * @author 余炘洋
 * @date 2026-10-19
 */
export const purgeAdminMemory = async (id: number, tenantId: string): Promise<void> => {
  const suffix = buildQuery({tenantId});
  return request<void>(
    `/api/v1/admin/agent-memories/${encodeURIComponent(String(id))}/purge${suffix}`,
    {method: 'DELETE'}
  );
};

/**
 * 管理端导出租户记忆与候选。
 *
 * @param tenantId 租户标识
 * @param userId   用户标识，可为空
 * @return 导出视图
 * @author 余炘洋
 * @date 2026-10-19
 */
export const exportAdminMemories = async (
  tenantId: string,
  userId?: number
): Promise<AgentMemoryExportView> => {
  const suffix = buildQuery({tenantId, userId});
  return request<AgentMemoryExportView>(`/api/v1/admin/agent-memories/export${suffix}`);
};
