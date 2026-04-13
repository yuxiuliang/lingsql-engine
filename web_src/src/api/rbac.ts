import {request} from '@/utils/request';
import type {MenuView} from '@/api/auth';

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
 * 用户视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface UserView {
  id: number;
  tenantId: string;
  username: string;
  nickname?: string;
  status?: number;
  isExperience?: number;
  lastLoginTime?: string;
  roleIds?: number[];
  orgUnitIds?: number[];
  primaryOrgUnitId?: number;
  orgUnitNames?: string[];
  dataScope?: DataScopeRule;
  effectiveDataScope?: DataScopeRule;
  effectiveDataScopeSource?: string;
}

/**
 * 用户创建请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface UserCreateRequest {
  tenantId?: string;
  username: string;
  password: string;
  nickname?: string;
  status?: number;
  isExperience?: number;
  roleIds?: number[];
  orgUnitIds?: number[];
  primaryOrgUnitId?: number;
  dataScope?: DataScopeRule;
}

/**
 * 用户更新请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface UserUpdateRequest {
  nickname?: string;
  status?: number;
  isExperience?: number;
  roleIds?: number[];
  orgUnitIds?: number[];
  primaryOrgUnitId?: number;
  dataScope?: DataScopeRule;
}

/**
 * 密码重置请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface UserPasswordResetRequest {
  newPassword: string;
}

/**
 * 角色视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface RoleView {
  id: number;
  tenantId?: string;
  roleCode: string;
  roleName: string;
  status?: number;
  permIds?: number[];
  dataScope?: DataScopeRule;
}

/**
 * 角色创建请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface RoleCreateRequest {
  tenantId?: string;
  roleCode: string;
  roleName: string;
  status?: number;
  permIds?: number[];
  dataScope?: DataScopeRule;
}

/**
 * 角色更新请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface RoleUpdateRequest {
  roleName?: string;
  status?: number;
  permIds?: number[];
  dataScope?: DataScopeRule;
}

/**
 * 权限视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface PermissionView {
  id: number;
  permCode: string;
  permName: string;
  permType: string;
  status?: number;
}

/**
 * 租户权限池请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface TenantPermissionRequest {
  permIds?: number[];
}

/**
 * 数据权限规则。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface DataScopeRule {
  scopeType?: string;
  scopeColumn?: string;
  scopeRule?: string;
  relationTypes?: string[];
}

/**
 * 体验账号策略。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface ExperiencePolicyView {
  userId?: number;
  menuWhitelist?: string[];
  dataScopeOverride?: DataScopeRule;
  status?: number;
}

/**
 * 体验账号策略请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
export interface ExperiencePolicyRequest {
  menuWhitelist?: string[];
  dataScopeOverride?: DataScopeRule;
  enabled?: boolean;
}

/**
 * 菜单治理视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface MenuAdminView {
  id?: number;
  tenantId?: string;
  menuCode?: string;
  menuName?: string;
  parentId?: number;
  parentMenuCode?: string;
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  visible?: number;
  status?: number;
  permCode?: string;
  orgTypeCodes?: string[];
  orgUnitCodes?: string[];
  orgTags?: string[];
  requiredRelationTypes?: string[];
  children?: MenuAdminView[];
}

/**
 * 菜单规则更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface MenuRuleUpdateRequest {
  orgTypeCodes?: string[];
  orgUnitCodes?: string[];
  orgTags?: string[];
  requiredRelationTypes?: string[];
}

/**
 * 租户菜单覆盖视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface TenantMenuAdminView {
  id?: number;
  overrideId?: number;
  tenantId?: string;
  menuCode?: string;
  menuName?: string;
  parentId?: number;
  parentMenuCode?: string;
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  permCode?: string;
  visible?: number;
  status?: number;
  orgTypeCodes?: string[];
  orgUnitCodes?: string[];
  orgTags?: string[];
  requiredRelationTypes?: string[];
  overrideActive?: boolean;
  baseVisible?: number;
  baseStatus?: number;
  baseOrgTypeCodes?: string[];
  baseOrgUnitCodes?: string[];
  baseOrgTags?: string[];
  baseRequiredRelationTypes?: string[];
  overrideVisible?: number | null;
  overrideStatus?: number | null;
  overrideOrgTypeCodes?: string[];
  overrideOrgUnitCodes?: string[];
  overrideOrgTags?: string[];
  overrideRequiredRelationTypes?: string[];
  children?: TenantMenuAdminView[];
}

/**
 * 租户菜单覆盖更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface TenantMenuRuleUpdateRequest {
  tenantId?: string;
  visible?: number | null;
  status?: number | null;
  orgTypeCodes?: string[];
  orgUnitCodes?: string[];
  orgTags?: string[];
  requiredRelationTypes?: string[];
}

/**
 * 菜单命中预览请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface MenuPreviewRequest {
  tenantId?: string;
  permissionCodes?: string[];
  orgTypeCodes?: string[];
  orgUnitCodes?: string[];
  orgTags?: string[];
  relationTypes?: string[];
  experience?: boolean;
  menuWhitelist?: string[];
}

/**
 * 菜单影响差异项。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface MenuImpactDiffItemView {
  menuCode?: string;
  menuName?: string;
  path?: string;
}

/**
 * 菜单影响分析请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface MenuImpactPreviewRequest {
  tenantId?: string;
  menuId?: number;
  menuCode?: string;
  globalRule?: MenuRuleUpdateRequest;
  tenantRule?: TenantMenuRuleUpdateRequest;
  previewContext?: MenuPreviewRequest;
}

/**
 * 菜单影响分析结果。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface MenuImpactPreviewView {
  tenantId?: string;
  menuCode?: string;
  menuName?: string;
  tenantOverrideMode?: boolean;
  baselineMenuCount?: number;
  targetMenuCount?: number;
  addedMenuCount?: number;
  removedMenuCount?: number;
  baselineMatched?: boolean;
  targetMatched?: boolean;
  baselineRuleSummary?: string;
  targetRuleSummary?: string;
  changedDimensions?: string[];
  impactSummary?: string;
  addedMenus?: MenuImpactDiffItemView[];
  removedMenus?: MenuImpactDiffItemView[];
}

/**
 * 菜单发布单视图。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface MenuReleaseView {
  id?: number;
  tenantId?: string;
  releaseType?: string;
  releaseTitle?: string;
  menuId?: number;
  menuCode?: string;
  menuName?: string;
  baselineRuleSummary?: string;
  targetRuleSummary?: string;
  impactSummary?: string;
  changedDimensions?: string[];
  addedMenuCount?: number;
  removedMenuCount?: number;
  changeRemark?: string;
  releaseStatus?: string;
  requestedByUserId?: number;
  requestedByName?: string;
  reviewedByUserId?: number;
  reviewedByName?: string;
  reviewComment?: string;
  reviewedTime?: string;
  appliedByUserId?: number;
  appliedByName?: string;
  appliedTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 菜单发布单创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface MenuReleaseCreateRequest {
  tenantId?: string;
  releaseTitle?: string;
  changeRemark?: string;
  impactRequest: MenuImpactPreviewRequest;
}

/**
 * 菜单发布单审核请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface MenuReleaseReviewRequest {
  decision: string;
  reviewComment?: string;
}

/**
 * 菜单发布单回滚请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface MenuReleaseRollbackRequest {
  releaseTitle?: string;
  changeRemark?: string;
}

/**
 * 数据权限命中组织预览项。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface DataScopePreviewOrgView {
  orgUnitId?: number;
  unitCode?: string;
  unitName?: string;
  typeCode?: string;
  hitSource?: string;
}

/**
 * 数据权限角色候选预览项。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface DataScopePreviewRoleCandidateView {
  roleId?: number;
  roleCode?: string;
  roleName?: string;
  dataScope?: DataScopeRule;
  selected?: boolean;
  summary?: string;
  relationTypes?: string[];
}

/**
 * 数据权限命中预览结果。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface DataScopePreviewView {
  effectiveDataScope?: DataScopeRule;
  effectiveSource?: string;
  summary?: string;
  description?: string;
  directOrgCodes?: string[];
  directOrgSubtreeCodes?: string[];
  relationDirectScopes?: Record<string, string[]>;
  relationSubtreeScopes?: Record<string, string[]>;
  matchedOrgCount?: number;
  matchedOrgTruncated?: boolean;
  matchedOrgs?: DataScopePreviewOrgView[];
  roleCandidates?: DataScopePreviewRoleCandidateView[];
  warnings?: string[];
  affectedRoleCount?: number;
  affectedUserCount?: number;
  impactSummary?: string;
  sampleSqlValid?: boolean;
  sampleSqlMessage?: string;
  sampleRewrittenSql?: string;
  diff?: {
    baselineSummary?: string;
    targetSummary?: string;
    baselineMatchedOrgCount?: number;
    targetMatchedOrgCount?: number;
    addedOrgCount?: number;
    removedOrgCount?: number;
    unchangedOrgCount?: number;
    addedOrgs?: DataScopePreviewOrgView[];
    removedOrgs?: DataScopePreviewOrgView[];
  };
}

/**
 * 角色数据权限预览请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface RoleDataScopePreviewRequest {
  tenantId?: string;
  dataScope?: DataScopeRule;
  roleId?: number;
  baselineDataScope?: DataScopeRule;
  orgUnitIds?: number[];
  sampleSql?: string;
}

/**
 * 角色数据权限发布单视图。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface RoleScopeReleaseView {
  id?: number;
  tenantId?: string;
  roleId?: number;
  roleCode?: string;
  roleName?: string;
  releaseTitle?: string;
  baselineSummary?: string;
  targetSummary?: string;
  impactSummary?: string;
  matchedOrgCount?: number;
  affectedUserCount?: number;
  addedOrgCount?: number;
  removedOrgCount?: number;
  changeRemark?: string;
  releaseStatus?: string;
  requestedByUserId?: number;
  requestedByName?: string;
  reviewedByUserId?: number;
  reviewedByName?: string;
  reviewComment?: string;
  reviewedTime?: string;
  appliedByUserId?: number;
  appliedByName?: string;
  appliedTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 角色数据权限发布单创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface RoleScopeReleaseCreateRequest {
  tenantId?: string;
  releaseTitle?: string;
  changeRemark?: string;
  previewRequest: RoleDataScopePreviewRequest;
}

/**
 * 角色数据权限发布单审核请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface RoleScopeReleaseReviewRequest {
  decision: string;
  reviewComment?: string;
}

/**
 * 角色数据权限发布单回滚请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface RoleScopeReleaseRollbackRequest {
  releaseTitle?: string;
  changeRemark?: string;
}

/**
 * 用户数据权限预览请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
export interface UserDataScopePreviewRequest {
  tenantId?: string;
  dataScope?: DataScopeRule;
  userId?: number;
  baselineDataScope?: DataScopeRule;
  orgUnitIds?: number[];
  baselineOrgUnitIds?: number[];
  roleIds?: number[];
  baselineRoleIds?: number[];
  sampleSql?: string;
}

/**
 * 用户数据权限发布单视图。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface UserScopeReleaseView {
  id?: number;
  tenantId?: string;
  userId?: number;
  username?: string;
  nickname?: string;
  releaseTitle?: string;
  baselineSummary?: string;
  targetSummary?: string;
  impactSummary?: string;
  matchedOrgCount?: number;
  affectedRoleCount?: number;
  affectedUserCount?: number;
  addedOrgCount?: number;
  removedOrgCount?: number;
  changeRemark?: string;
  releaseStatus?: string;
  requestedByUserId?: number;
  requestedByName?: string;
  reviewedByUserId?: number;
  reviewedByName?: string;
  reviewComment?: string;
  reviewedTime?: string;
  appliedByUserId?: number;
  appliedByName?: string;
  appliedTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 用户数据权限发布单创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface UserScopeReleaseCreateRequest {
  tenantId?: string;
  releaseTitle?: string;
  changeRemark?: string;
  previewRequest: UserDataScopePreviewRequest;
}

/**
 * 用户数据权限发布单审核请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface UserScopeReleaseReviewRequest {
  decision: string;
  reviewComment?: string;
}

/**
 * 用户数据权限发布单回滚请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
export interface UserScopeReleaseRollbackRequest {
  releaseTitle?: string;
  changeRemark?: string;
}

/**
 * 查询用户列表。
 *
 * @param params 查询参数
 * @return 用户分页
 * @author 余炘洋
 * @date 2026-01-26
 */
export const fetchUsers = async (params: {tenantId?: string; keyword?: string; page: number; size: number}): Promise<PageResult<UserView>> => {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size)
  });
  if (params.tenantId) {
    query.set('tenantId', params.tenantId);
  }
  if (params.keyword) {
    query.set('keyword', params.keyword);
  }
  return request<PageResult<UserView>>(`/api/v1/admin/users?${query.toString()}`);
};

/**
 * 创建用户。
 *
 * @param payload 请求体
 * @return 用户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
export const createUser = async (payload: UserCreateRequest): Promise<UserView> => {
  return request<UserView>('/api/v1/admin/users', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新用户。
 *
 * @param id 用户ID
 * @param payload 请求体
 * @return 用户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
export const updateUser = async (id: number, payload: UserUpdateRequest): Promise<UserView> => {
  return request<UserView>(`/api/v1/admin/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 预览用户数据权限命中范围。
 *
 * @param payload 请求体
 * @return 预览结果
 * @author 余炘洋
 * @date 2026-04-12
 */
export const previewUserDataScope = async (payload: UserDataScopePreviewRequest): Promise<DataScopePreviewView> => {
  return request<DataScopePreviewView>('/api/v1/admin/users/preview-data-scope', {
    method: 'POST',
    body: JSON.stringify(payload || {})
  });
};

/**
 * 查询用户数据权限发布单列表。
 *
 * @param tenantId 租户ID
 * @param status 状态
 * @return 发布单列表
 * @author 余炘洋
 * @date 2026-04-13
 */
export const fetchUserScopeReleaseRecords = async (tenantId?: string, status?: string): Promise<UserScopeReleaseView[]> => {
  const query = new URLSearchParams();
  if (tenantId) {
    query.set('tenantId', tenantId);
  }
  if (status) {
    query.set('status', status);
  }
  const queryString = query.toString();
  return request<UserScopeReleaseView[]>(`/api/v1/admin/users/scope-releases${queryString ? `?${queryString}` : ''}`);
};

/**
 * 创建用户数据权限发布单。
 *
 * @param payload 请求体
 * @return 发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const createUserScopeReleaseRecord = async (payload: UserScopeReleaseCreateRequest): Promise<UserScopeReleaseView> => {
  return request<UserScopeReleaseView>('/api/v1/admin/users/scope-releases', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 审核用户数据权限发布单。
 *
 * @param id 发布单ID
 * @param payload 请求体
 * @return 发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const reviewUserScopeReleaseRecord = async (id: number, payload: UserScopeReleaseReviewRequest): Promise<UserScopeReleaseView> => {
  return request<UserScopeReleaseView>(`/api/v1/admin/users/scope-releases/${id}/review`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 应用用户数据权限发布单。
 *
 * @param id 发布单ID
 * @return 发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const applyUserScopeReleaseRecord = async (id: number): Promise<UserScopeReleaseView> => {
  return request<UserScopeReleaseView>(`/api/v1/admin/users/scope-releases/${id}/apply`, {
    method: 'PUT'
  });
};

/**
 * 基于已应用发布单生成用户数据权限回滚单。
 *
 * @param id 发布单ID
 * @param payload 请求体
 * @return 新发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const createRollbackUserScopeReleaseRecord = async (
  id: number,
  payload: UserScopeReleaseRollbackRequest
): Promise<UserScopeReleaseView> => {
  return request<UserScopeReleaseView>(`/api/v1/admin/users/scope-releases/${id}/rollback`, {
    method: 'POST',
    body: JSON.stringify(payload || {})
  });
};

/**
 * 重置用户密码。
 *
 * @param id 用户ID
 * @param payload 请求体
 * @return 重置结果
 * @author 余炘洋
 * @date 2026-01-26
 */
export const resetUserPassword = async (id: number, payload: UserPasswordResetRequest): Promise<string> => {
  return request<string>(`/api/v1/admin/users/${id}/password`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 查询角色列表。
 *
 * @param tenantId 租户ID
 * @return 角色列表
 * @author 余炘洋
 * @date 2026-01-26
 */
export const fetchRoles = async (tenantId?: string): Promise<RoleView[]> => {
  const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
  return request<RoleView[]>(`/api/v1/admin/roles${query}`);
};

/**
 * 创建角色。
 *
 * @param payload 请求体
 * @return 角色信息
 * @author 余炘洋
 * @date 2026-01-26
 */
export const createRole = async (payload: RoleCreateRequest): Promise<RoleView> => {
  return request<RoleView>('/api/v1/admin/roles', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 更新角色。
 *
 * @param id 角色ID
 * @param payload 请求体
 * @return 角色信息
 * @author 余炘洋
 * @date 2026-01-26
 */
export const updateRole = async (id: number, payload: RoleUpdateRequest): Promise<RoleView> => {
  return request<RoleView>(`/api/v1/admin/roles/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 预览角色数据权限命中范围。
 *
 * @param payload 请求体
 * @return 预览结果
 * @author 余炘洋
 * @date 2026-04-12
 */
export const previewRoleDataScope = async (payload: RoleDataScopePreviewRequest): Promise<DataScopePreviewView> => {
  return request<DataScopePreviewView>('/api/v1/admin/roles/preview-data-scope', {
    method: 'POST',
    body: JSON.stringify(payload || {})
  });
};

/**
 * 查询角色数据权限发布单列表。
 *
 * @param tenantId 租户ID
 * @param status 状态
 * @return 发布单列表
 * @author 余炘洋
 * @date 2026-04-13
 */
export const fetchRoleScopeReleaseRecords = async (tenantId?: string, status?: string): Promise<RoleScopeReleaseView[]> => {
  const query = new URLSearchParams();
  if (tenantId) {
    query.set('tenantId', tenantId);
  }
  if (status) {
    query.set('status', status);
  }
  const queryString = query.toString();
  return request<RoleScopeReleaseView[]>(`/api/v1/admin/roles/scope-releases${queryString ? `?${queryString}` : ''}`);
};

/**
 * 创建角色数据权限发布单。
 *
 * @param payload 请求体
 * @return 发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const createRoleScopeReleaseRecord = async (payload: RoleScopeReleaseCreateRequest): Promise<RoleScopeReleaseView> => {
  return request<RoleScopeReleaseView>('/api/v1/admin/roles/scope-releases', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 审核角色数据权限发布单。
 *
 * @param id 发布单ID
 * @param payload 请求体
 * @return 发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const reviewRoleScopeReleaseRecord = async (id: number, payload: RoleScopeReleaseReviewRequest): Promise<RoleScopeReleaseView> => {
  return request<RoleScopeReleaseView>(`/api/v1/admin/roles/scope-releases/${id}/review`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 应用角色数据权限发布单。
 *
 * @param id 发布单ID
 * @return 发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const applyRoleScopeReleaseRecord = async (id: number): Promise<RoleScopeReleaseView> => {
  return request<RoleScopeReleaseView>(`/api/v1/admin/roles/scope-releases/${id}/apply`, {
    method: 'PUT'
  });
};

/**
 * 基于已应用发布单生成角色数据权限回滚单。
 *
 * @param id 发布单ID
 * @param payload 请求体
 * @return 新发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const createRollbackRoleScopeReleaseRecord = async (
  id: number,
  payload: RoleScopeReleaseRollbackRequest
): Promise<RoleScopeReleaseView> => {
  return request<RoleScopeReleaseView>(`/api/v1/admin/roles/scope-releases/${id}/rollback`, {
    method: 'POST',
    body: JSON.stringify(payload || {})
  });
};

/**
 * 查询权限列表。
 *
 * @return 权限列表
 * @author 余炘洋
 * @date 2026-01-26
 */
export const fetchPermissions = async (): Promise<PermissionView[]> => {
  return request<PermissionView[]>('/api/v1/admin/permissions');
};

/**
 * 查询租户权限池。
 *
 * @param tenantId 租户ID
 * @return 权限列表
 * @author 余炘洋
 * @date 2026-01-26
 */
export const fetchTenantPermissionPool = async (tenantId: string): Promise<PermissionView[]> => {
  return request<PermissionView[]>(`/api/v1/admin/permissions/pool/${encodeURIComponent(tenantId)}`);
};

/**
 * 更新租户权限池。
 *
 * @param tenantId 租户ID
 * @param payload 请求体
 * @return 更新结果
 * @author 余炘洋
 * @date 2026-01-26
 */
export const updateTenantPermissionPool = async (tenantId: string, payload: TenantPermissionRequest): Promise<string> => {
  return request<string>(`/api/v1/admin/permissions/pool/${encodeURIComponent(tenantId)}`,
    {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
};

/**
 * 查询体验账号策略。
 *
 * @param userId 用户ID
 * @return 策略信息
 * @author 余炘洋
 * @date 2026-01-26
 */
export const fetchExperiencePolicy = async (userId: number): Promise<ExperiencePolicyView> => {
  return request<ExperiencePolicyView>(`/api/v1/rbac/experience/${userId}`);
};

/**
 * 保存体验账号策略。
 *
 * @param userId 用户ID
 * @param payload 请求体
 * @return 策略信息
 * @author 余炘洋
 * @date 2026-01-26
 */
export const saveExperiencePolicy = async (userId: number, payload: ExperiencePolicyRequest): Promise<ExperiencePolicyView> => {
  return request<ExperiencePolicyView>(`/api/v1/rbac/experience/${userId}`,
    {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
};

/**
 * 查询菜单治理规则树。
 *
 * @return 菜单规则树
 * @author 余炘洋
 * @date 2026-04-12
 */
export const fetchMenuRules = async (): Promise<MenuAdminView[]> => {
  return request<MenuAdminView[]>('/api/v1/admin/menus');
};

/**
 * 更新菜单组织适配规则。
 *
 * @param id 菜单ID
 * @param payload 请求体
 * @return 更新后的菜单
 * @author 余炘洋
 * @date 2026-04-12
 */
export const updateMenuRule = async (id: number, payload: MenuRuleUpdateRequest): Promise<MenuAdminView> => {
  return request<MenuAdminView>(`/api/v1/admin/menus/${id}/rule`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 查询租户级菜单覆盖树。
 *
 * @param tenantId 租户ID
 * @return 菜单覆盖树
 * @author 余炘洋
 * @date 2026-04-12
 */
export const fetchTenantMenuRules = async (tenantId?: string): Promise<TenantMenuAdminView[]> => {
  const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
  return request<TenantMenuAdminView[]>(`/api/v1/admin/menus/tenant${query}`);
};

/**
 * 更新租户级菜单覆盖。
 *
 * @param menuCode 菜单编码
 * @param payload 请求体
 * @return 更新后的租户菜单
 * @author 余炘洋
 * @date 2026-04-12
 */
export const updateTenantMenuRule = async (menuCode: string, payload: TenantMenuRuleUpdateRequest): Promise<TenantMenuAdminView> => {
  return request<TenantMenuAdminView>(`/api/v1/admin/menus/tenant/${encodeURIComponent(menuCode)}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 清除租户级菜单覆盖。
 *
 * @param menuCode 菜单编码
 * @param tenantId 租户ID
 * @return 清除结果
 * @author 余炘洋
 * @date 2026-04-12
 */
export const clearTenantMenuRule = async (menuCode: string, tenantId?: string): Promise<string> => {
  const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
  return request<string>(`/api/v1/admin/menus/tenant/${encodeURIComponent(menuCode)}${query}`, {
    method: 'DELETE'
  });
};

/**
 * 预览指定组织上下文命中的菜单树。
 *
 * @param payload 请求体
 * @return 菜单树
 * @author 余炘洋
 * @date 2026-04-12
 */
export const previewMenuTree = async (payload: MenuPreviewRequest): Promise<MenuView[]> => {
  return request<MenuView[]>('/api/v1/admin/menus/preview', {
    method: 'POST',
    body: JSON.stringify(payload || {})
  });
};

/**
 * 预览菜单规则变更影响。
 *
 * @param payload 请求体
 * @return 影响分析结果
 * @author 余炘洋
 * @date 2026-04-13
 */
export const previewMenuImpact = async (payload: MenuImpactPreviewRequest): Promise<MenuImpactPreviewView> => {
  return request<MenuImpactPreviewView>('/api/v1/admin/menus/impact', {
    method: 'POST',
    body: JSON.stringify(payload || {})
  });
};

/**
 * 查询菜单发布单列表。
 *
 * @param tenantId 租户ID
 * @param status 发布状态
 * @return 发布单列表
 * @author 余炘洋
 * @date 2026-04-13
 */
export const fetchMenuReleaseRecords = async (tenantId?: string, status?: string): Promise<MenuReleaseView[]> => {
  const query = new URLSearchParams();
  if (tenantId) {
    query.set('tenantId', tenantId);
  }
  if (status) {
    query.set('status', status);
  }
  const queryString = query.toString();
  return request<MenuReleaseView[]>(`/api/v1/admin/menus/releases${queryString ? `?${queryString}` : ''}`);
};

/**
 * 创建菜单发布单。
 *
 * @param payload 请求体
 * @return 发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const createMenuReleaseRecord = async (payload: MenuReleaseCreateRequest): Promise<MenuReleaseView> => {
  return request<MenuReleaseView>('/api/v1/admin/menus/releases', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 审核菜单发布单。
 *
 * @param id 发布单ID
 * @param payload 请求体
 * @return 发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const reviewMenuReleaseRecord = async (id: number, payload: MenuReleaseReviewRequest): Promise<MenuReleaseView> => {
  return request<MenuReleaseView>(`/api/v1/admin/menus/releases/${id}/review`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
};

/**
 * 应用菜单发布单。
 *
 * @param id 发布单ID
 * @return 发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const applyMenuReleaseRecord = async (id: number): Promise<MenuReleaseView> => {
  return request<MenuReleaseView>(`/api/v1/admin/menus/releases/${id}/apply`, {
    method: 'PUT'
  });
};

/**
 * 基于已应用发布单生成回滚单。
 *
 * @param id 发布单ID
 * @param payload 请求体
 * @return 新生成的回滚发布单
 * @author 余炘洋
 * @date 2026-04-13
 */
export const createRollbackMenuReleaseRecord = async (
  id: number,
  payload: MenuReleaseRollbackRequest
): Promise<MenuReleaseView> => {
  return request<MenuReleaseView>(`/api/v1/admin/menus/releases/${id}/rollback`, {
    method: 'POST',
    body: JSON.stringify(payload || {})
  });
};
