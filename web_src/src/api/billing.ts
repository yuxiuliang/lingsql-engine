import {request} from '@/utils/request';

/**
 * 计费策略版本定义。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface BillingPolicyVersion {
  id: number;
  versionCode: string;
  status?: number;
  effectiveTime?: string;
  modelUnitCost?: string;
  serviceLevelLimit?: string;
  tenantTypeWeight?: string;
  defaultUnitCost?: number;
  defaultDailyLimit?: number;
  defaultDailyTokenLimit?: number;
  defaultTenantWeight?: number;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 计费策略版本请求。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface BillingPolicyVersionRequest {
  versionCode: string;
  status?: number;
  effectiveTime?: string;
  modelUnitCost?: Record<string, number>;
  serviceLevelLimit?: Record<string, {dailyCallLimit: number; dailyTokenLimit: number}>;
  tenantTypeWeight?: Record<string, number>;
  defaultUnitCost?: number;
  defaultDailyLimit?: number;
  defaultDailyTokenLimit?: number;
  defaultTenantWeight?: number;
  description?: string;
}

/**
 * 租户计费覆盖定义。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface TenantBillingOverride {
  id: number;
  tenantId: string;
  priceMultiplier?: number;
  status?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 租户计费覆盖请求。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface TenantBillingOverrideRequest {
  priceMultiplier?: number;
  status?: number;
  remark?: string;
}

/**
 * 查询计费策略版本列表。
 *
 * @return 版本列表
 * @author 余炘洋
 * @date 2026-01-27
 */
export const fetchBillingPolicies = async (): Promise<BillingPolicyVersion[]> => {
  return request<BillingPolicyVersion[]>('/api/v1/admin/billing/policies');
};

/**
 * 创建计费策略版本。
 *
 * @param payload 请求体
 * @return 创建结果
 * @author 余炘洋
 * @date 2026-01-27
 */
export const createBillingPolicy = async (payload: BillingPolicyVersionRequest): Promise<BillingPolicyVersion> => {
  return request<BillingPolicyVersion>('/api/v1/admin/billing/policies', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新计费策略版本。
 *
 * @param id 版本ID
 * @param payload 请求体
 * @return 更新结果
 * @author 余炘洋
 * @date 2026-01-27
 */
export const updateBillingPolicy = async (id: number, payload: BillingPolicyVersionRequest): Promise<BillingPolicyVersion> => {
  return request<BillingPolicyVersion>(`/api/v1/admin/billing/policies/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 激活计费策略版本。
 *
 * @param id 版本ID
 * @return 生效结果
 * @author 余炘洋
 * @date 2026-01-27
 */
export const activateBillingPolicy = async (id: number): Promise<BillingPolicyVersion> => {
  return request<BillingPolicyVersion>(`/api/v1/admin/billing/policies/${id}/activate`, {
    method: 'POST'
  });
};

/**
 * 查询租户计费覆盖列表。
 *
 * @return 覆盖列表
 * @author 余炘洋
 * @date 2026-01-27
 */
export const fetchBillingOverrides = async (): Promise<TenantBillingOverride[]> => {
  return request<TenantBillingOverride[]>('/api/v1/admin/billing/overrides');
};

/**
 * 查询租户计费覆盖。
 *
 * @param tenantId 租户ID
 * @return 覆盖详情
 * @author 余炘洋
 * @date 2026-01-27
 */
export const fetchBillingOverride = async (tenantId: string): Promise<TenantBillingOverride> => {
  return request<TenantBillingOverride>(`/api/v1/admin/billing/overrides/${tenantId}`);
};

/**
 * 新增或更新租户计费覆盖。
 *
 * @param tenantId 租户ID
 * @param payload 请求体
 * @return 覆盖结果
 * @author 余炘洋
 * @date 2026-01-27
 */
export const upsertBillingOverride = async (
  tenantId: string,
  payload: TenantBillingOverrideRequest
): Promise<TenantBillingOverride> => {
  return request<TenantBillingOverride>(`/api/v1/admin/billing/overrides/${tenantId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 通用分页结果。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

/**
 * 供应商账单视图。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
export interface VendorBillEvent {
  id: number;
  tenantId?: string;
  providerName?: string;
  externalBillId?: string;
  providerRequestId?: string;
  amountMinor?: number;
  vendorTokens?: number;
  currency?: string;
  status?: string;
  matchState?: string;
  matchStateDescription?: string;
  matchedInvocationId?: string;
  matchedState?: string;
  matchedActualAmount?: number;
  matchedActualTokens?: number;
  evidenceHash?: string;
  matchTime?: string;
  createdAt?: string;
}

/**
 * 供应商账单导入逐行结果。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
export interface VendorBillImportRow {
  lineNumber: number;
  externalBillId?: string;
  outcome: string;
  matchState?: string;
  message?: string;
}

/**
 * 供应商账单导入报告。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
export interface VendorBillImportReport {
  csvSha256: string;
  totalRows: number;
  importedCount: number;
  duplicateCount: number;
  rejectedCount: number;
  matchSummary?: Record<string, number>;
  rows: VendorBillImportRow[];
}

/**
 * UNKNOWN 物理调用视图。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
export interface BillingUnknownInvocation {
  invocationId: string;
  tenantId?: string;
  workflowRequestId?: string;
  logicalCallNo?: number;
  stage?: string;
  caller?: string;
  providerName?: string;
  modelName?: string;
  modelVersion?: string;
  maxBillableTokens?: number;
  unitCost?: number;
  reservedAmount?: number;
  failureCode?: string;
  reservedAt?: string;
  dispatchedAt?: string;
}

/**
 * UNKNOWN 物理调用裁决请求。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
export interface BillingInvocationResolutionRequest {
  evidenceHash: string;
  providerRequestId: string;
  resolutionType: 'CONFIRMED_USAGE' | 'CONFIRMED_NOT_BILLED';
  actualTokens?: number;
}

/**
 * UNKNOWN 物理调用裁决回执。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
export interface BillingInvocationResolutionReceipt {
  resolutionId: string;
  invocationId: string;
  tenantId?: string;
  resolutionType: string;
  state: string;
  actualTokens?: number;
  actualAmount: number;
  refundAmount: number;
  providerRequestId?: string;
  evidenceHash: string;
  operator?: string;
  resolvedAt?: string;
}

/**
 * 导入供应商账单 CSV。
 *
 * @param csv CSV 文本
 * @return 导入报告
 * @author 余炘洋
 * @date 2026-10-17
 */
export const importVendorBills = async (csv: string): Promise<VendorBillImportReport> => {
  return request<VendorBillImportReport>('/api/v1/admin/billing/vendor-bills/import', {
    method: 'POST',
    body: JSON.stringify({csv})
  });
};

/**
 * 查询供应商账单。
 *
 * @param params 查询参数
 * @return 分页账单视图
 * @author 余炘洋
 * @date 2026-10-17
 */
export const fetchVendorBills = async (params: {
  tenantId?: string;
  providerName?: string;
  matchState?: string;
  externalBillId?: string;
  page: number;
  size: number;
}): Promise<PageResult<VendorBillEvent>> => {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size)
  });
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.providerName) {
    query.set('providerName', params.providerName);
  }
  if (params.matchState) {
    query.set('matchState', params.matchState);
  }
  if (params.externalBillId) {
    query.set('externalBillId', params.externalBillId);
  }
  return request<PageResult<VendorBillEvent>>(`/api/v1/admin/billing/vendor-bills?${query.toString()}`);
};

/**
 * 查询待裁决的 UNKNOWN 物理调用。
 *
 * @param params 查询参数
 * @return 分页 UNKNOWN 物理调用
 * @author 余炘洋
 * @date 2026-10-17
 */
export const fetchUnknownInvocations = async (params: {
  tenantId?: string;
  providerName?: string;
  page: number;
  size: number;
}): Promise<PageResult<BillingUnknownInvocation>> => {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size)
  });
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.providerName) {
    query.set('providerName', params.providerName);
  }
  return request<PageResult<BillingUnknownInvocation>>(
    `/api/v1/admin/billing/invocations/unknown?${query.toString()}`
  );
};

/**
 * 依据外部证据裁决 UNKNOWN 物理调用。
 *
 * @param invocationId 物理调用ID
 * @param payload 裁决请求
 * @return 裁决回执
 * @author 余炘洋
 * @date 2026-10-17
 */
export const resolveUnknownInvocation = async (
  invocationId: string,
  payload: BillingInvocationResolutionRequest
): Promise<BillingInvocationResolutionReceipt> => {
  return request<BillingInvocationResolutionReceipt>(
    `/api/v1/admin/billing/invocations/${encodeURIComponent(invocationId)}/resolve`,
    {
      method: 'PUT',
      body: JSON.stringify(payload)
    }
  );
};
