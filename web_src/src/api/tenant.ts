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
  notificationChannels?: string[];
  expireTime?: string;
  planName?: string;
  quotaBalance?: number;
  serviceLevel?: string;
  tenantType?: string;
  lifecycleState?: string;
  daysToExpire?: number | null;
  graceDaysRemaining?: number;
  riskScore?: number;
  riskLevel?: string;
  riskReasons?: string[];
  createTime?: string;
  updateTime?: string;
}

/**
 * 租户生命周期汇总视图。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
export interface TenantLifecycleSummaryView {
  total: number;
  active: number;
  expiringSoon: number;
  gracePeriod: number;
  suspended: number;
  disabled: number;
  atRisk: number;
  highRisk: number;
  criticalRisk: number;
}

/**
 * 生命周期运营任务。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
export interface TenantLifecycleOpsTaskView {
  tenantId: string;
  tenantName: string;
  lifecycleState: string;
  riskLevel: string;
  riskScore: number;
  daysToExpire?: number | null;
  priority: string;
  suggestedAction: string;
  triggerReason: string;
  slaHours: number;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
}

/**
 * 生命周期运营工作台。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
export interface TenantLifecycleWorkbenchView {
  generatedAt?: string;
  totalTasks: number;
  p0Count: number;
  p1Count: number;
  p2Count: number;
  tasks: TenantLifecycleOpsTaskView[];
}

/**
 * 租户创建请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface TenantCreateRequest {
  /**
   * 租户ID（由系统自动生成）。
   *
   * @author 余炘洋
   * @date 2026-01-26
   */
  tenantId?: string;
  tenantName: string;
  status?: number;
  remark?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  notificationChannels?: string[];
  expireTime?: string;
  planName?: string;
  jdbcUrl?: string;
  username?: string;
  password?: string;
  driverClass?: string;
  isolationMode?: string;
  logicColumn?: string;
  orgTemplateCode?: string;
  orgRootUnitName?: string;
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
  notificationChannels?: string[];
  expireTime?: string;
  planName?: string;
}

/**
 * 租户通知通道测试请求。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
export interface TenantNotificationChannelTestRequest {
  tenantId?: string;
  tenantName?: string;
  channels: string[];
}

/**
 * 租户通知通道测试明细。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
export interface TenantNotificationChannelTestChannelView {
  channel?: string;
  success?: boolean;
  statusCode?: number | null;
  detail?: string;
}

/**
 * 租户通知通道测试结果。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
export interface TenantNotificationChannelTestView {
  tenantId?: string;
  tenantName?: string;
  requestedChannels?: string[];
  success?: boolean;
  summary?: string;
  testTime?: string;
  channelResults?: TenantNotificationChannelTestChannelView[];
}

/**
 * 租户通知通道风险项。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
export interface TenantNotificationChannelIssueView {
  tenantId?: string;
  tenantName?: string;
  selectedChannels?: string[];
  unavailableChannels?: string[];
  reason?: string;
  suggestion?: string;
}

/**
 * 租户通知通道健康视图。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
export interface TenantNotificationChannelHealthView {
  alertEnabled?: boolean;
  configuredChannels?: string[];
  unconfiguredChannels?: string[];
  tenantUsingNotificationChannels?: number;
  tenantIssueCount?: number;
  summary?: string;
  issues?: TenantNotificationChannelIssueView[];
}

/**
 * 租户通知通道最近测试记录。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
export interface TenantNotificationChannelRecentTestView {
  auditLogId?: number;
  tenantId?: string;
  tenantName?: string;
  requestedChannels?: string[];
  success?: boolean;
  summary?: string;
  operatorUsername?: string;
  createTime?: string;
  channelResults?: TenantNotificationChannelTestChannelView[];
}

/**
 * 通知通道失败通道聚合项。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
export interface TenantNotificationChannelFailureChannelView {
  channel?: string;
  failureCount?: number;
}

/**
 * 通知通道失败原因聚合项。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
export interface TenantNotificationChannelFailureReasonView {
  reason?: string;
  count?: number;
}

/**
 * 通知通道失败统计视图。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
export interface TenantNotificationChannelFailureStatsView {
  tenantId?: string;
  totalTests?: number;
  failedTests?: number;
  successRate?: number;
  summary?: string;
  topFailingChannels?: TenantNotificationChannelFailureChannelView[];
  topFailureReasons?: TenantNotificationChannelFailureReasonView[];
}

/**
 * 租户续期请求。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
export interface TenantLifecycleExtendRequest {
  expireTime?: string;
  extendDays?: number;
  activate?: boolean;
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

/**
 * 查询租户生命周期汇总。
 *
 * @return 汇总结果
 * @author 余炘洋
 * @date 2026-02-27
 */
export const fetchTenantLifecycleSummary = async (): Promise<TenantLifecycleSummaryView> => {
  return request<TenantLifecycleSummaryView>('/api/v1/admin/tenants/lifecycle/summary');
};

/**
 * 查询生命周期运营工作台。
 *
 * @param limit 返回任务上限
 * @return 工作台结果
 * @author 余炘洋
 * @date 2026-02-27
 */
export const fetchTenantLifecycleWorkbench = async (limit = 50): Promise<TenantLifecycleWorkbenchView> => {
  return request<TenantLifecycleWorkbenchView>(`/api/v1/admin/tenants/lifecycle/workbench?limit=${limit}`);
};

/**
 * 查询租户默认通知通道健康情况。
 *
 * @return 健康视图
 * @author 余炘洋
 * @date 2026-04-15
 */
export const fetchTenantNotificationChannelHealth = async (): Promise<TenantNotificationChannelHealthView> => {
  return request<TenantNotificationChannelHealthView>('/api/v1/admin/tenants/notification-channels/health');
};

/**
 * 查询最近的通知通道测试记录。
 *
 * @param params 查询参数
 * @return 最近测试记录
 * @author 余炘洋
 * @date 2026-04-15
 */
export const fetchRecentTenantNotificationChannelTests = async (params: {
  tenantId?: string;
  success?: number;
  channel?: string;
  operatorKeyword?: string;
  limit?: number;
} = {}): Promise<TenantNotificationChannelRecentTestView[]> => {
  const query = new URLSearchParams();
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.success != null) {
    query.set('success', String(params.success));
  }
  if (params.channel) {
    query.set('channel', params.channel);
  }
  if (params.operatorKeyword) {
    query.set('operatorKeyword', params.operatorKeyword);
  }
  if (params.limit != null) {
    query.set('limit', String(params.limit));
  }
  const suffix = query.toString();
  return request<TenantNotificationChannelRecentTestView[]>(
    `/api/v1/admin/tenants/notification-channels/tests/recent${suffix ? `?${suffix}` : ''}`
  );
};

/**
 * 查询通知通道失败问题分布。
 *
 * @param params 查询参数
 * @return 失败统计
 * @author 余炘洋
 * @date 2026-04-15
 */
export const fetchTenantNotificationChannelFailureStats = async (params: {
  tenantId?: string;
  limit?: number;
} = {}): Promise<TenantNotificationChannelFailureStatsView> => {
  const query = new URLSearchParams();
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.limit != null) {
    query.set('limit', String(params.limit));
  }
  const suffix = query.toString();
  return request<TenantNotificationChannelFailureStatsView>(
    `/api/v1/admin/tenants/notification-channels/tests/stats${suffix ? `?${suffix}` : ''}`
  );
};

/**
 * 续期租户生命周期。
 *
 * @param id 租户ID
 * @param payload 续期请求
 * @return 更新后的租户
 * @author 余炘洋
 * @date 2026-02-27
 */
export const extendTenantLifecycle = async (id: number, payload: TenantLifecycleExtendRequest): Promise<TenantView> => {
  return request<TenantView>(`/api/v1/admin/tenants/${id}/lifecycle/extend`, {
    method: 'POST',
    body: JSON.stringify(payload || {})
  });
};

/**
 * 恢复租户服务。
 *
 * @param id 租户ID
 * @return 更新后的租户
 * @author 余炘洋
 * @date 2026-02-27
 */
export const reactivateTenant = async (id: number): Promise<TenantView> => {
  return request<TenantView>(`/api/v1/admin/tenants/${id}/lifecycle/reactivate`, {
    method: 'POST'
  });
};

/**
 * 测试租户通知通道。
 *
 * @param payload 测试请求
 * @return 测试结果
 * @author 余炘洋
 * @date 2026-04-15
 */
export const testTenantNotificationChannels = async (
  payload: TenantNotificationChannelTestRequest
): Promise<TenantNotificationChannelTestView> => {
  return request<TenantNotificationChannelTestView>('/api/v1/admin/tenants/notification-channels/test', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};
