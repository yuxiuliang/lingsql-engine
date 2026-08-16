import {request} from '@/utils/request';

export interface TenantReadinessCheckView {
  code: string;
  name: string;
  passed: boolean;
  failureReason?: string;
}

export interface TenantFirstQuestionReadinessView {
  tenantId: string;
  tenantName?: string;
  status: 'READY_TO_ASK' | 'BLOCKED';
  ready: boolean;
  checks: readonly TenantReadinessCheckView[];
  failureReasons: readonly string[];
}

/**
 * 查询租户首问就绪事实。
 *
 * @param tenantId 租户标识
 * @return 首问就绪视图
 */
export const fetchTenantFirstQuestionReadiness = async (
  tenantId: string
): Promise<TenantFirstQuestionReadinessView> => {
  return request<TenantFirstQuestionReadinessView>(
    `/api/v1/admin/tenant-readiness/${encodeURIComponent(tenantId)}/first-question`
  );
};
