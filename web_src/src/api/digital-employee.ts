import {request} from '@/utils/request';

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

/**
 * 数字员工视图。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface DigitalEmployeeView {
  id: number;
  tenantId: string;
  employeeCode?: string;
  employeeName: string;
  roleName?: string;
  scenario?: string;
  promptTemplate?: string;
  defaultQuestion: string;
  scheduleEnabled?: number;
  scheduleCron?: string;
  alertEnabled?: number;
  alertRuleType?: string;
  alertThreshold?: number | null;
  pushEnabled?: number;
  pushChannels?: string[];
  pushMaxRows?: number | null;
  pushTitleTemplate?: string;
  pushMessageTemplate?: string;
  status?: number;
  runCount?: number;
  successCount?: number;
  lastRunStatus?: number | null;
  lastRunTime?: string;
  lastScheduleTime?: string;
  lastErrorMessage?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 数字员工排行视图。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface DigitalEmployeeTopEmployeeView {
  employeeId?: number;
  employeeName?: string;
  runCount?: number;
  successCount?: number;
  failureCount?: number;
  successRate?: number;
}

/**
 * 数字员工概览视图。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface DigitalEmployeeOverviewView {
  tenantId?: string;
  windowHours?: number;
  startTime?: string;
  endTime?: string;
  totalEmployeeCount?: number;
  activeEmployeeCount?: number;
  runCount?: number;
  successCount?: number;
  failureCount?: number;
  successRate?: number;
  topEmployees?: DigitalEmployeeTopEmployeeView[];
}

/**
 * 数字员工创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface DigitalEmployeeCreateRequest {
  tenantId: string;
  employeeCode?: string;
  employeeName: string;
  roleName?: string;
  scenario?: string;
  promptTemplate?: string;
  defaultQuestion: string;
  scheduleEnabled?: number;
  scheduleCron?: string;
  alertEnabled?: number;
  alertRuleType?: string;
  alertThreshold?: number | null;
  pushEnabled?: number;
  pushChannels?: string[];
  pushMaxRows?: number | null;
  pushTitleTemplate?: string;
  pushMessageTemplate?: string;
  status?: number;
  remark?: string;
}

/**
 * 数字员工更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface DigitalEmployeeUpdateRequest {
  tenantId?: string;
  employeeCode?: string;
  employeeName?: string;
  roleName?: string;
  scenario?: string;
  promptTemplate?: string;
  defaultQuestion?: string;
  scheduleEnabled?: number;
  scheduleCron?: string;
  alertEnabled?: number;
  alertRuleType?: string;
  alertThreshold?: number | null;
  pushEnabled?: number;
  pushChannels?: string[];
  pushMaxRows?: number | null;
  pushTitleTemplate?: string;
  pushMessageTemplate?: string;
  status?: number;
  remark?: string;
}

/**
 * 数字员工执行请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface DigitalEmployeeRunRequest {
  question?: string;
  triggerSource?: string;
}

/**
 * 数字员工执行结果。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface DigitalEmployeeRunResult {
  runLogId?: number;
  employeeId?: number;
  employeeName?: string;
  tenantId?: string;
  triggerSource?: string;
  question?: string;
  answer?: string;
  sql?: string;
  data?: Array<Record<string, unknown>>;
  success?: boolean;
  errorMessage?: string;
  durationMs?: number;
  executeTime?: string;
}

/**
 * 数字员工运行日志视图。
 *
 * @author 余炘洋
 * @date 2026-04-14
 */
export interface DigitalEmployeeRunLogView {
  id?: number;
  employeeId?: number;
  employeeName?: string;
  tenantId?: string;
  triggerSource?: string;
  question?: string;
  answer?: string;
  executedSql?: string;
  success?: number;
  errorMessage?: string;
  resultRows?: number;
  requestId?: string;
  durationMs?: number;
  alertDeliveryStatus?: number | null;
  alertDeliveryDetail?: string;
  pushDeliveryStatus?: number | null;
  pushDeliveryDetail?: string;
  createTime?: string;
}

/**
 * 查询数字员工列表。
 *
 * @param params 查询条件
 * @return 数字员工列表
 * @author 余炘洋
 * @date 2026-04-11
 */
export const fetchDigitalEmployees = async (params: {
  tenantId?: string;
  keyword?: string;
  status?: number;
} = {}): Promise<DigitalEmployeeView[]> => {
  const query = new URLSearchParams();
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.keyword) {
    query.set('keyword', params.keyword);
  }
  if (params.status != null) {
    query.set('status', String(params.status));
  }
  const suffix = query.toString();
  return request<DigitalEmployeeView[]>(`/api/v1/admin/digital-employees${suffix ? `?${suffix}` : ''}`);
};

/**
 * 查询数字员工概览。
 *
 * @param params 查询条件
 * @return 概览视图
 * @author 余炘洋
 * @date 2026-04-11
 */
export const fetchDigitalEmployeeOverview = async (params: {
  tenantId?: string;
  windowHours?: number;
} = {}): Promise<DigitalEmployeeOverviewView> => {
  const query = new URLSearchParams();
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.windowHours != null) {
    query.set('windowHours', String(params.windowHours));
  }
  const suffix = query.toString();
  return request<DigitalEmployeeOverviewView>(`/api/v1/admin/digital-employees/overview${suffix ? `?${suffix}` : ''}`);
};

/**
 * 创建数字员工。
 *
 * @param payload 创建请求
 * @return 数字员工视图
 * @author 余炘洋
 * @date 2026-04-11
 */
export const createDigitalEmployee = async (payload: DigitalEmployeeCreateRequest): Promise<DigitalEmployeeView> => {
  return request<DigitalEmployeeView>('/api/v1/admin/digital-employees', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新数字员工。
 *
 * @param id 员工ID
 * @param payload 更新请求
 * @return 数字员工视图
 * @author 余炘洋
 * @date 2026-04-11
 */
export const updateDigitalEmployee = async (id: number, payload: DigitalEmployeeUpdateRequest): Promise<DigitalEmployeeView> => {
  return request<DigitalEmployeeView>(`/api/v1/admin/digital-employees/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 删除数字员工。
 *
 * @param id 员工ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-04-11
 */
export const deleteDigitalEmployee = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/admin/digital-employees/${id}`, {
    method: 'DELETE'
  });
};

/**
 * 手动执行数字员工。
 *
 * @param id 员工ID
 * @param payload 执行请求
 * @return 执行结果
 * @author 余炘洋
 * @date 2026-04-11
 */
export const runDigitalEmployee = async (id: number, payload: DigitalEmployeeRunRequest): Promise<DigitalEmployeeRunResult> => {
  return request<DigitalEmployeeRunResult>(`/api/v1/admin/digital-employees/${id}/run`, {
    method: 'POST',
    body: JSON.stringify(payload || {})
  });
};

/**
 * 查询数字员工运行日志。
 *
 * @param params 查询条件
 * @return 日志分页
 * @author 余炘洋
 * @date 2026-04-14
 */
export const fetchDigitalEmployeeRunLogs = async (params: {
  tenantId?: string;
  employeeId?: number;
  success?: number;
  triggerSource?: string;
  page?: number;
  size?: number;
} = {}): Promise<PageResult<DigitalEmployeeRunLogView>> => {
  const query = new URLSearchParams();
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.employeeId != null) {
    query.set('employeeId', String(params.employeeId));
  }
  if (params.success != null) {
    query.set('success', String(params.success));
  }
  if (params.triggerSource) {
    query.set('triggerSource', params.triggerSource);
  }
  query.set('page', String(params.page ?? 1));
  query.set('size', String(params.size ?? 10));
  return request<PageResult<DigitalEmployeeRunLogView>>(`/api/v1/admin/digital-employees/runs?${query.toString()}`);
};
