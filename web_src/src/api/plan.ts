import {request} from '@/utils/request';

/**
 * 套餐视图。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
export interface BillingPlanView {
  id: number;
  planCode: string;
  planName: string;
  status?: number;
  serviceLevel?: string;
  tenantType?: string;
  defaultQuotaBalance?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 套餐创建请求。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
export interface BillingPlanCreateRequest {
  planCode?: string;
  planName: string;
  status?: number;
  serviceLevel?: string;
  tenantType?: string;
  defaultQuotaBalance?: number;
  remark?: string;
}

/**
 * 套餐更新请求。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
export interface BillingPlanUpdateRequest {
  planCode?: string;
  planName?: string;
  status?: number;
  serviceLevel?: string;
  tenantType?: string;
  defaultQuotaBalance?: number;
  remark?: string;
}

export interface PlanQuery {
  keyword?: string;
  status?: number;
}

/**
 * 查询套餐列表。
 *
 * @param query 查询参数
 * @return 套餐列表
 * @author 余炘洋
 * @date 2026-01-29
 */
export const fetchPlans = async (query: PlanQuery = {}): Promise<BillingPlanView[]> => {
  const params = new URLSearchParams();
  if (query.keyword) {
    params.set('keyword', query.keyword);
  }
  if (query.status != null) {
    params.set('status', String(query.status));
  }
  const suffix = params.toString();
  const path = suffix ? `/api/v1/admin/plans?${suffix}` : '/api/v1/admin/plans';
  return request<BillingPlanView[]>(path);
};

/**
 * 创建套餐。
 *
 * @param payload 请求体
 * @return 套餐视图
 * @author 余炘洋
 * @date 2026-01-29
 */
export const createPlan = async (payload: BillingPlanCreateRequest): Promise<BillingPlanView> => {
  return request<BillingPlanView>('/api/v1/admin/plans', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新套餐。
 *
 * @param id 套餐ID
 * @param payload 请求体
 * @return 套餐视图
 * @author 余炘洋
 * @date 2026-01-29
 */
export const updatePlan = async (id: number, payload: BillingPlanUpdateRequest): Promise<BillingPlanView> => {
  return request<BillingPlanView>(`/api/v1/admin/plans/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 删除套餐。
 *
 * @param id 套餐ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-01-29
 */
export const deletePlan = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/admin/plans/${id}`, {
    method: 'DELETE'
  });
};
