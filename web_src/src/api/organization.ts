import {request} from '@/utils/request';

/**
 * 组织类型视图。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgUnitTypeView {
  id: number;
  tenantId: string;
  typeCode: string;
  typeName: string;
  sort?: number;
  status?: number;
  remark?: string;
  unitCount?: number;
}

/**
 * 组织类型创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgUnitTypeCreateRequest {
  tenantId?: string;
  typeCode: string;
  typeName: string;
  sort?: number;
  status?: number;
  remark?: string;
}

/**
 * 组织类型更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgUnitTypeUpdateRequest {
  typeName?: string;
  sort?: number;
  status?: number;
  remark?: string;
}

/**
 * 组织节点视图。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgUnitView {
  id: number;
  tenantId: string;
  unitCode: string;
  unitName: string;
  typeCode: string;
  typeName?: string;
  leaderUserId?: number;
  leaderUsername?: string;
  leaderNickname?: string;
  parentId?: number;
  treePath?: string;
  treeLevel?: number;
  sort?: number;
  status?: number;
  remark?: string;
  tags?: string[];
  attributeJson?: string;
  memberCount?: number;
  children?: OrgUnitView[];
}

/**
 * 组织节点创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgUnitCreateRequest {
  tenantId?: string;
  unitCode: string;
  unitName: string;
  typeCode: string;
  leaderUserId?: number | null;
  parentId?: number | null;
  sort?: number;
  status?: number;
  remark?: string;
  tags?: string[];
  attributeJson?: string;
}

/**
 * 组织节点更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgUnitUpdateRequest {
  unitName?: string;
  typeCode?: string;
  leaderUserId?: number | null;
  parentId?: number | null;
  sort?: number;
  status?: number;
  remark?: string;
  tags?: string[];
  attributeJson?: string;
}

/**
 * 组织关系视图。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgUnitRelationView {
  id: number;
  tenantId: string;
  sourceOrgUnitId: number;
  sourceOrgUnitName?: string;
  targetOrgUnitId: number;
  targetOrgUnitName?: string;
  relationType: string;
  relationName?: string;
  sort?: number;
  status?: number;
  remark?: string;
}

/**
 * 组织关系创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgUnitRelationCreateRequest {
  tenantId?: string;
  sourceOrgUnitId: number;
  targetOrgUnitId: number;
  relationType: string;
  relationName?: string;
  sort?: number;
  status?: number;
  remark?: string;
}

/**
 * 组织关系更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgUnitRelationUpdateRequest {
  sourceOrgUnitId?: number;
  targetOrgUnitId?: number;
  relationType?: string;
  relationName?: string;
  sort?: number;
  status?: number;
  remark?: string;
}

/**
 * 组织模板视图。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgTemplateView {
  templateCode: string;
  templateName: string;
  industry?: string;
  description?: string;
  typeCount?: number;
  unitCount?: number;
  relationCount?: number;
}

/**
 * 组织模板初始化请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface OrgTemplateApplyRequest {
  tenantId?: string;
  templateCode: string;
}

/**
 * 组织关系消费任务视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface OrgRelationOpsTaskView {
  relationId?: number;
  relationType: string;
  relationName?: string;
  sourceOrgUnitName?: string;
  targetOrgUnitName?: string;
  sourceOrgUnitCode?: string;
  targetOrgUnitCode?: string;
  sceneCode?: string;
  sceneName?: string;
  priority?: string;
  readinessStatus?: string;
  suggestedAction?: string;
  triggerReason?: string;
  routeScopeSummary?: string;
  scopeUnitCount?: number;
  targetLeaderUserId?: number;
  targetLeaderName?: string;
  missingCapabilities?: string[];
}

/**
 * 组织关系消费工作台视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface OrgRelationWorkbenchView {
  generatedAt?: string;
  totalTasks?: number;
  p0Count?: number;
  p1Count?: number;
  p2Count?: number;
  tasks?: OrgRelationOpsTaskView[];
}

/**
 * 组织关系路由候选视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface OrgRelationRouteCandidateView {
  relationId?: number;
  relationName?: string;
  relationType?: string;
  targetOrgUnitId?: number;
  targetOrgUnitCode?: string;
  targetOrgUnitName?: string;
  targetLeaderUserId?: number;
  targetLeaderName?: string;
  sort?: number;
  scopeUnitCount?: number;
  scopeUnitCodes?: string[];
  routeScopeSummary?: string;
}

/**
 * 组织关系路由解析结果。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface OrgRelationRouteResolveView {
  tenantId?: string;
  sourceOrgUnitId?: number;
  sourceOrgUnitCode?: string;
  sourceOrgUnitName?: string;
  relationType?: string;
  matched?: boolean;
  ambiguous?: boolean;
  candidateCount?: number;
  primaryRelationId?: number;
  primaryRelationName?: string;
  primaryTargetOrgUnitId?: number;
  primaryTargetOrgUnitCode?: string;
  primaryTargetOrgUnitName?: string;
  primaryLeaderUserId?: number;
  primaryLeaderName?: string;
  primaryScopeUnitCount?: number;
  primaryScopeUnitCodes?: string[];
  primaryRouteScopeSummary?: string;
  warnings?: string[];
  candidates?: OrgRelationRouteCandidateView[];
}

/**
 * 组织关系执行项视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface OrgRelationExecutionView {
  id: number;
  tenantId: string;
  relationId?: number;
  relationType?: string;
  relationName?: string;
  sceneCode?: string;
  sceneName?: string;
  executionTitle?: string;
  executionContent?: string;
  priority?: string;
  slaHours?: number;
  dueTime?: string;
  slaStatus?: string;
  overdue?: boolean;
  overdueMinutes?: number;
  completedWithinSla?: boolean;
  escalationStatus?: string;
  escalationLevel?: number;
  lastEscalatedTime?: string;
  status?: string;
  sourceOrgUnitId?: number;
  sourceOrgUnitCode?: string;
  sourceOrgUnitName?: string;
  targetOrgUnitId?: number;
  targetOrgUnitCode?: string;
  targetOrgUnitName?: string;
  routeScopeSummary?: string;
  scopeUnitCount?: number;
  scopeUnitCodes?: string[];
  requestedByUserId?: number;
  requestedByName?: string;
  assigneeUserId?: number;
  assigneeName?: string;
  resultSummary?: string;
  startedTime?: string;
  completedTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 组织关系执行轨迹视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface OrgRelationExecutionTimelineView {
  id: number;
  executionId?: number;
  eventType?: string;
  eventSummary?: string;
  eventDetail?: string;
  actorUserId?: number;
  actorName?: string;
  beforeStatus?: string;
  afterStatus?: string;
  beforeAssigneeName?: string;
  afterAssigneeName?: string;
  beforePriority?: string;
  afterPriority?: string;
  beforeSlaHours?: number;
  afterSlaHours?: number;
  beforeDueTime?: string;
  afterDueTime?: string;
  beforeResultSummary?: string;
  afterResultSummary?: string;
  createTime?: string;
}

/**
 * 组织关系执行项创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface OrgRelationExecutionCreateRequest {
  tenantId?: string;
  sourceOrgUnitId: number;
  relationType: string;
  relationId?: number;
  executionTitle: string;
  executionContent?: string;
  priority?: string;
  slaHours?: number;
  dueTime?: string;
  assigneeUserId?: number | null;
}

/**
 * 组织关系执行项更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface OrgRelationExecutionUpdateRequest {
  status: string;
  assigneeUserId?: number | null;
  priority?: string;
  slaHours?: number;
  dueTime?: string;
  resultSummary?: string;
}

/**
 * 查询组织类型列表。
 *
 * @param tenantId 租户ID
 * @return 类型列表
 * @author 余炘洋
 * @date 2026-04-11
 */
export const fetchOrgUnitTypes = async (tenantId?: string): Promise<OrgUnitTypeView[]> => {
  const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
  return request<OrgUnitTypeView[]>(`/api/v1/admin/org/types${query}`);
};

/**
 * 创建组织类型。
 *
 * @param payload 请求体
 * @return 类型视图
 * @author 余炘洋
 * @date 2026-04-11
 */
export const createOrgUnitType = async (payload: OrgUnitTypeCreateRequest): Promise<OrgUnitTypeView> => {
  return request<OrgUnitTypeView>('/api/v1/admin/org/types', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新组织类型。
 *
 * @param id      类型ID
 * @param payload 请求体
 * @return 类型视图
 * @author 余炘洋
 * @date 2026-04-11
 */
export const updateOrgUnitType = async (id: number, payload: OrgUnitTypeUpdateRequest): Promise<OrgUnitTypeView> => {
  return request<OrgUnitTypeView>(`/api/v1/admin/org/types/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 删除组织类型。
 *
 * @param id 类型ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-04-11
 */
export const deleteOrgUnitType = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/admin/org/types/${id}`, {
    method: 'DELETE'
  });
};

/**
 * 查询组织树。
 *
 * @param tenantId 租户ID
 * @return 组织树
 * @author 余炘洋
 * @date 2026-04-11
 */
export const fetchOrgUnits = async (tenantId?: string): Promise<OrgUnitView[]> => {
  const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
  return request<OrgUnitView[]>(`/api/v1/admin/org/units${query}`);
};

/**
 * 创建组织节点。
 *
 * @param payload 请求体
 * @return 组织视图
 * @author 余炘洋
 * @date 2026-04-11
 */
export const createOrgUnit = async (payload: OrgUnitCreateRequest): Promise<OrgUnitView> => {
  return request<OrgUnitView>('/api/v1/admin/org/units', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新组织节点。
 *
 * @param id      节点ID
 * @param payload 请求体
 * @return 组织视图
 * @author 余炘洋
 * @date 2026-04-11
 */
export const updateOrgUnit = async (id: number, payload: OrgUnitUpdateRequest): Promise<OrgUnitView> => {
  return request<OrgUnitView>(`/api/v1/admin/org/units/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 删除组织节点。
 *
 * @param id 节点ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-04-11
 */
export const deleteOrgUnit = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/admin/org/units/${id}`, {
    method: 'DELETE'
  });
};

/**
 * 查询组织关系列表。
 *
 * @param tenantId 租户ID
 * @return 关系列表
 * @author 余炘洋
 * @date 2026-04-11
 */
export const fetchOrgRelations = async (tenantId?: string): Promise<OrgUnitRelationView[]> => {
  const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
  return request<OrgUnitRelationView[]>(`/api/v1/admin/org/relations${query}`);
};

/**
 * 查询组织关系消费工作台。
 *
 * @param tenantId 租户ID
 * @return 工作台视图
 * @author 余炘洋
 * @date 2026-04-12
 */
export const fetchOrgRelationWorkbench = async (tenantId?: string): Promise<OrgRelationWorkbenchView> => {
  const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
  return request<OrgRelationWorkbenchView>(`/api/v1/admin/org/workbench${query}`);
};

/**
 * 解析组织关系路由。
 *
 * @param sourceOrgUnitId 源组织ID
 * @param relationType    关系类型
 * @param tenantId        租户ID
 * @return 路由解析结果
 * @author 余炘洋
 * @date 2026-04-12
 */
export const fetchOrgRelationRouteResolve = async (
  sourceOrgUnitId: number,
  relationType: string,
  tenantId?: string
): Promise<OrgRelationRouteResolveView> => {
  const params = new URLSearchParams();
  params.set('sourceOrgUnitId', String(sourceOrgUnitId));
  params.set('relationType', relationType);
  if (tenantId) {
    params.set('tenantId', tenantId);
  }
  return request<OrgRelationRouteResolveView>(`/api/v1/admin/org/routes/resolve?${params.toString()}`);
};

/**
 * 查询组织关系执行台账。
 *
 * @param tenantId 租户ID
 * @param status 状态
 * @param relationType 关系类型
 * @return 执行台账
 * @author 余炘洋
 * @date 2026-04-12
 */
export const fetchOrgRelationExecutions = async (
  tenantId?: string,
  status?: string,
  relationType?: string
): Promise<OrgRelationExecutionView[]> => {
  const params = new URLSearchParams();
  if (tenantId) {
    params.set('tenantId', tenantId);
  }
  if (status) {
    params.set('status', status);
  }
  if (relationType) {
    params.set('relationType', relationType);
  }
  const query = params.toString();
  return request<OrgRelationExecutionView[]>(`/api/v1/admin/org/executions${query ? `?${query}` : ''}`);
};

/**
 * 创建组织关系执行项。
 *
 * @param payload 请求体
 * @return 执行项
 * @author 余炘洋
 * @date 2026-04-12
 */
export const createOrgRelationExecution = async (payload: OrgRelationExecutionCreateRequest): Promise<OrgRelationExecutionView> => {
  return request<OrgRelationExecutionView>('/api/v1/admin/org/executions', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新组织关系执行项。
 *
 * @param id 执行项ID
 * @param payload 请求体
 * @return 执行项
 * @author 余炘洋
 * @date 2026-04-12
 */
export const updateOrgRelationExecution = async (id: number, payload: OrgRelationExecutionUpdateRequest): Promise<OrgRelationExecutionView> => {
  return request<OrgRelationExecutionView>(`/api/v1/admin/org/executions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 查询组织关系执行轨迹。
 *
 * @param id 执行项ID
 * @return 执行轨迹
 * @author 余炘洋
 * @date 2026-04-12
 */
export const fetchOrgRelationExecutionTimeline = async (id: number): Promise<OrgRelationExecutionTimelineView[]> => {
  return request<OrgRelationExecutionTimelineView[]>(`/api/v1/admin/org/executions/${id}/timeline`);
};

/**
 * 查询组织模板列表。
 *
 * @return 模板列表
 * @author 余炘洋
 * @date 2026-04-11
 */
export const fetchOrgTemplates = async (): Promise<OrgTemplateView[]> => {
  return request<OrgTemplateView[]>('/api/v1/admin/org/templates');
};

/**
 * 应用组织模板。
 *
 * @param payload 请求体
 * @return 初始化结果
 * @author 余炘洋
 * @date 2026-04-11
 */
export const applyOrgTemplate = async (payload: OrgTemplateApplyRequest): Promise<string> => {
  return request<string>('/api/v1/admin/org/templates/apply', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 创建组织关系。
 *
 * @param payload 请求体
 * @return 关系视图
 * @author 余炘洋
 * @date 2026-04-11
 */
export const createOrgRelation = async (payload: OrgUnitRelationCreateRequest): Promise<OrgUnitRelationView> => {
  return request<OrgUnitRelationView>('/api/v1/admin/org/relations', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新组织关系。
 *
 * @param id      关系ID
 * @param payload 请求体
 * @return 关系视图
 * @author 余炘洋
 * @date 2026-04-11
 */
export const updateOrgRelation = async (id: number, payload: OrgUnitRelationUpdateRequest): Promise<OrgUnitRelationView> => {
  return request<OrgUnitRelationView>(`/api/v1/admin/org/relations/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 删除组织关系。
 *
 * @param id 关系ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-04-11
 */
export const deleteOrgRelation = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/admin/org/relations/${id}`, {
    method: 'DELETE'
  });
};
