import {request} from '@/utils/request';

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
  status?: number;
  runCount?: number;
  successCount?: number;
  lastRunStatus?: number | null;
  lastRunTime?: string;
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
