import {request} from '@/utils/request';

/**
 * 人工审批节点视图。
 *
 * <p>只包含审批与续跑所需的最小信息，不包含模型提示词或数据库内部结构。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
export interface AgentApprovalView {
  id: number;
  tenantId: string;
  executionId: string;
  stage: string;
  status: string;
  approverId?: string | null;
  comment?: string | null;
  createdAt?: string;
  decidedAt?: string | null;
}

/**
 * 人工审批续跑结果视图。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
export interface AgentApprovalResumeView {
  approvalId: number;
  executionId: string;
  status: string;
  answer?: string | null;
  sql?: string | null;
  durationMs: number;
}

/**
 * 查询人工审批节点列表。
 *
 * @param params 查询条件
 * @return 审批节点列表
 */
export const fetchApprovals = async (params: {
  tenantId?: string;
  status?: string;
} = {}): Promise<AgentApprovalView[]> => {
  const query = new URLSearchParams();
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.status) {
    query.set('status', params.status);
  }
  const suffix = query.toString();
  return request<AgentApprovalView[]>(`/api/v1/admin/agent-approvals${suffix ? `?${suffix}` : ''}`);
};

/**
 * 提交审批结论（通过或驳回）。
 *
 * @param id       审批节点主键
 * @param approved 是否通过
 * @param comment  审批意见
 * @return 裁决后的审批节点
 */
export const decideApproval = async (
  id: number,
  approved: boolean,
  comment?: string
): Promise<AgentApprovalView> => {
  return request<AgentApprovalView>(`/api/v1/admin/agent-approvals/${id}/decide`, {
    method: 'POST',
    body: JSON.stringify({approved, comment})
  });
};

/**
 * 审批通过后发起续跑。
 *
 * @param id 审批节点主键
 * @return 续跑结果
 */
export const resumeApproval = async (id: number): Promise<AgentApprovalResumeView> => {
  return request<AgentApprovalResumeView>(`/api/v1/admin/agent-approvals/${id}/resume`, {
    method: 'POST'
  });
};
