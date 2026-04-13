import {BlobResponse, request, requestBlob} from '@/utils/request';

/**
 * 分页结果定义。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

/**
 * 审计日志视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface AuditLogView {
  id: number;
  tenantId?: string;
  userId?: number;
  username?: string;
  action?: string;
  resource?: string;
  detail?: string;
  summary?: string;
  success?: number;
  errorMessage?: string;
  requestIp?: string;
  createTime?: string;
}

/**
 * SQL 执行日志视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface SqlExecuteLogView {
  id: number;
  tenantId: string;
  userId?: number;
  question?: string;
  aiSql?: string;
  executedSql?: string;
  success?: number;
  errorMessage?: string;
  modelProvider?: string;
  modelName?: string;
  modelVersion?: string;
  tokenCount?: number;
  costAmount?: number;
  executeCostMs?: number;
  resultRows?: number;
  requestSource?: string;
  createTime?: string;
}

/**
 * Token 审计维度统计。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
export interface TokenAuditDimensionStat {
  dimensionKey: string;
  totalCount: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  totalTokens: number;
  totalCost: number;
  avgTokensPerRequest: number;
  avgCostPerRequest: number;
}

/**
 * Token 审计趋势点。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
export interface TokenAuditTrendPoint {
  statDate: string;
  totalCount: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  totalTokens: number;
  totalCost: number;
}

/**
 * Token 审计总览。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
export interface TokenAuditOverviewView {
  startTime: string;
  endTime: string;
  totalCount: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  totalTokens: number;
  totalCost: number;
  avgTokensPerRequest: number;
  avgCostPerRequest: number;
  modelStats: TokenAuditDimensionStat[];
  tenantStats: TokenAuditDimensionStat[];
  trend: TokenAuditTrendPoint[];
}

/**
 * 查询审计日志。
 *
 * @param params 查询参数
 * @return 分页结果
 * @author 余炘洋
 * @date 2026-01-26
 */
export const fetchAuditLogs = async (params: {
  tenantId?: string;
  userId?: number;
  action?: string;
  startTime?: string;
  endTime?: string;
  page: number;
  size: number;
}): Promise<PageResult<AuditLogView>> => {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size)
  });
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.userId !== undefined && params.userId !== null) {
    query.set('userId', String(params.userId));
  }
  if (params.action) {
    query.set('action', params.action);
  }
  if (params.startTime) {
    query.set('startTime', params.startTime);
  }
  if (params.endTime) {
    query.set('endTime', params.endTime);
  }
  return request<PageResult<AuditLogView>>(`/api/v1/admin/audit/logs?${query.toString()}`);
};

/**
 * 查询 SQL 执行日志。
 *
 * @param params 查询参数
 * @return 分页结果
 * @author 余炘洋
 * @date 2026-01-26
 */
export const fetchSqlLogs = async (params: {
  tenantId?: string;
  userId?: number;
  question?: string;
  requestSource?: string;
  startTime?: string;
  endTime?: string;
  page: number;
  size: number;
}): Promise<PageResult<SqlExecuteLogView>> => {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size)
  });
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.userId !== undefined && params.userId !== null) {
    query.set('userId', String(params.userId));
  }
  if (params.question) {
    query.set('question', params.question);
  }
  if (params.requestSource) {
    query.set('requestSource', params.requestSource);
  }
  if (params.startTime) {
    query.set('startTime', params.startTime);
  }
  if (params.endTime) {
    query.set('endTime', params.endTime);
  }
  return request<PageResult<SqlExecuteLogView>>(`/api/v1/admin/audit/sql-logs?${query.toString()}`);
};

/**
 * 导出 SQL 执行日志。
 *
 * @param params 查询参数
 * @return 日志列表
 * @author 余炘洋
 * @date 2026-01-26
 */
export const exportSqlLogs = async (params: {
  tenantId?: string;
  userId?: number;
  question?: string;
  requestSource?: string;
  startTime?: string;
  endTime?: string;
}): Promise<SqlExecuteLogView[]> => {
  const query = new URLSearchParams();
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.userId !== undefined && params.userId !== null) {
    query.set('userId', String(params.userId));
  }
  if (params.question) {
    query.set('question', params.question);
  }
  if (params.requestSource) {
    query.set('requestSource', params.requestSource);
  }
  if (params.startTime) {
    query.set('startTime', params.startTime);
  }
  if (params.endTime) {
    query.set('endTime', params.endTime);
  }
  const queryString = query.toString();
  const path = queryString ? `/api/v1/admin/audit/sql-logs/export?${queryString}` : '/api/v1/admin/audit/sql-logs/export';
  return request<SqlExecuteLogView[]>(path);
};

/**
 * 导出 SQL 执行日志 Excel。
 *
 * @param params 查询参数
 * @return 二进制文件
 * @author 余炘洋
 * @date 2026-04-07
 */
export const exportSqlLogsExcel = async (params: {
  tenantId?: string;
  userId?: number;
  question?: string;
  requestSource?: string;
  startTime?: string;
  endTime?: string;
}): Promise<BlobResponse> => {
  const query = new URLSearchParams();
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.userId !== undefined && params.userId !== null) {
    query.set('userId', String(params.userId));
  }
  if (params.question) {
    query.set('question', params.question);
  }
  if (params.requestSource) {
    query.set('requestSource', params.requestSource);
  }
  if (params.startTime) {
    query.set('startTime', params.startTime);
  }
  if (params.endTime) {
    query.set('endTime', params.endTime);
  }
  const queryString = query.toString();
  const path = queryString ? `/api/v1/admin/audit/sql-logs/export/excel?${queryString}` : '/api/v1/admin/audit/sql-logs/export/excel';
  return requestBlob(path);
};

/**
 * 查询 Token 审计总览。
 *
 * @param params 查询参数
 * @return 总览数据
 * @author 余炘洋
 * @date 2026-02-27
 */
export const fetchTokenAuditOverview = async (params: {
  tenantId?: string;
  userId?: number;
  question?: string;
  requestSource?: string;
  startTime?: string;
  endTime?: string;
  topN?: number;
}): Promise<TokenAuditOverviewView> => {
  const query = new URLSearchParams();
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.userId !== undefined && params.userId !== null) {
    query.set('userId', String(params.userId));
  }
  if (params.question) {
    query.set('question', params.question);
  }
  if (params.requestSource) {
    query.set('requestSource', params.requestSource);
  }
  if (params.startTime) {
    query.set('startTime', params.startTime);
  }
  if (params.endTime) {
    query.set('endTime', params.endTime);
  }
  if (params.topN !== undefined && params.topN !== null) {
    query.set('topN', String(params.topN));
  }
  const queryString = query.toString();
  const path = queryString ? `/api/v1/admin/audit/token-overview?${queryString}` : '/api/v1/admin/audit/token-overview';
  return request<TokenAuditOverviewView>(path);
};
