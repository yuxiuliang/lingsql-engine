import {request} from '@/utils/request';

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
  executeCostMs?: number;
  resultRows?: number;
  requestSource?: string;
  createTime?: string;
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
