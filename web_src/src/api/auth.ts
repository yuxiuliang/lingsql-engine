import {request} from '@/utils/request';

/**
 * 登录后的认证流程。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
export type AuthFlow = 'AUTHENTICATED' | 'PASSWORD_CHANGE_REQUIRED';

/**
 * 关系语义映射定义。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
export interface RelationScopeMap {
  [relationType: string]: string[];
}

/**
 * 登录请求定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface LoginRequest {
  /**
   * 租户ID（必填）。
   *
   * @author 余炘洋
   * @date 2026-01-26
   */
  tenantId: string;
  username: string;
  password: string;
}

/**
 * 登录响应定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface LoginResponse {
  token: string;
  expiresIn: number;
  refreshToken?: string;
  refreshExpiresIn?: number;
  userId: number;
  tenantId: string;
  tenantName?: string;
  username: string;
  nickname?: string;
  experience?: boolean;
  roles?: string[];
  permissions?: string[];
  relationScopes?: RelationScopeMap;
  authFlow: AuthFlow;
}

/**
 * 修改当前用户密码请求。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/**
 * 菜单视图定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface MenuView {
  id?: number;
  menuCode?: string;
  menuName?: string;
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  visible?: number;
  children?: MenuView[];
}

/**
 * 用户信息定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface UserProfileView {
  userId?: number;
  tenantId?: string;
  username?: string;
  nickname?: string;
  experience?: boolean;
  roles?: string[];
  permissions?: string[];
  dataScope?: {
    scopeType?: string;
    scopeColumn?: string;
    scopeRule?: string;
  };
  relationScopes?: RelationScopeMap;
}

/**
 * 登录接口。
 *
 * @param payload 登录请求
 * @return 登录响应
 * @author 余炘洋
 * @date 2026-01-23
 */
export const login = async (payload: LoginRequest): Promise<LoginResponse> => {
  return request<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
};

/**
 * 修改当前用户密码并换发完整登录会话。
 *
 * @param payload 密码修改请求
 * @return 完整登录响应
 * @author 余炘洋
 * @date 2026-08-16
 */
export const changePassword = async (payload: ChangePasswordRequest): Promise<LoginResponse> => {
  return request<LoginResponse>('/api/v1/auth/password', {
    method: 'PUT',
    body: JSON.stringify(payload),
    skipAuthRefresh: true
  });
};

/**
 * 获取菜单树。
 *
 * @return 菜单树
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchMenus = async (): Promise<MenuView[]> => {
  return request<MenuView[]>('/api/v1/auth/menus');
};

/**
 * 获取当前用户信息。
 *
 * @return 用户信息
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchProfile = async (): Promise<UserProfileView> => {
  return request<UserProfileView>('/api/v1/auth/me');
};

/**
 * 退出登录。
 *
 * @return 退出结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const logout = async (): Promise<string> => {
  return request<string>('/api/v1/auth/logout', {
    method: 'POST'
  });
};
