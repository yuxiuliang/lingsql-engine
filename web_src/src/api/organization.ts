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
