import {defineStore} from 'pinia';
import type {AuthFlow, LoginResponse, MenuView, RelationScopeMap, UserProfileView} from '@/api/auth';

const AUTH_FLOW_KEY = 'authFlow';
const AUTHENTICATED_FLOW: AuthFlow = 'AUTHENTICATED';
const PASSWORD_CHANGE_REQUIRED_FLOW: AuthFlow = 'PASSWORD_CHANGE_REQUIRED';

const readAuthFlow = (): AuthFlow => {
  return localStorage.getItem(AUTH_FLOW_KEY) === PASSWORD_CHANGE_REQUIRED_FLOW
    ? PASSWORD_CHANGE_REQUIRED_FLOW
    : AUTHENTICATED_FLOW;
};

/**
 * 读取本地数组缓存。
 *
 * @param key 缓存键
 * @return 数组
 * @author 余炘洋
 * @date 2026-01-26
 */
const readArray = (key: string): string[] => {
  const raw = localStorage.getItem(key);
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
};

/**
 * 读取本地关系语义缓存。
 *
 * @param key 缓存键
 * @return 关系语义映射
 * @author 余炘洋
 * @date 2026-04-11
 */
const readRelationScopeMap = (key: string): RelationScopeMap => {
  const raw = localStorage.getItem(key);
  if (!raw) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {};
    }
    return Object.entries(parsed).reduce<RelationScopeMap>((acc, [relationType, codes]) => {
      if (!relationType || !Array.isArray(codes)) {
        return acc;
      }
      acc[relationType] = codes.filter((item): item is string => typeof item === 'string' && !!item.trim());
      return acc;
    }, {});
  } catch (error) {
    return {};
  }
};

/**
 * 认证状态定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface AuthState {
  token: string;
  refreshToken: string;
  userId: number;
  tenantId: string;
  tenantName: string;
  activeTenantId: string;
  activeTenantName: string;
  username: string;
  nickname: string;
  experience: boolean;
  roles: string[];
  permissions: string[];
  relationScopes: RelationScopeMap;
  menus: MenuView[];
  authFlow: AuthFlow;
  sessionReady: boolean;
}

/**
 * 认证状态管理。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('token') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    userId: Number(localStorage.getItem('userId') || 0),
    tenantId: localStorage.getItem('tenantId') || '',
    tenantName: localStorage.getItem('tenantName') || '',
    activeTenantId: localStorage.getItem('activeTenantId') || '',
    activeTenantName: localStorage.getItem('activeTenantName') || '',
    username: localStorage.getItem('username') || '',
    nickname: localStorage.getItem('nickname') || '',
    experience: localStorage.getItem('experience') === '1',
    roles: readArray('roles'),
    permissions: readArray('permissions'),
    relationScopes: readRelationScopeMap('relationScopes'),
    menus: [],
    authFlow: readAuthFlow(),
    sessionReady: false
  }),
  getters: {
    /**
     * 判断是否系统管理员。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    isSysAdmin: (state): boolean => {
      return (state.roles || []).some((role) => ['SYS_ADMIN', 'SUPER_ADMIN'].includes(role));
    },
    /**
     * 判断是否已完成会话鉴权。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    isAuthenticated: (state): boolean => {
      return state.sessionReady
        && !!state.token
        && !!state.userId
        && state.authFlow === AUTHENTICATED_FLOW;
    },
    /**
     * 判断当前是否为仅允许改密的受限会话。
     *
     * @author 余炘洋
     * @date 2026-08-16
     */
    isRestrictedSession: (state): boolean => {
      return !!state.token
        && !!state.userId
        && state.authFlow === PASSWORD_CHANGE_REQUIRED_FLOW;
    },
    /**
     * 判断当前是否为可访问业务功能的完整会话。
     *
     * @author 余炘洋
     * @date 2026-08-16
     */
    isFullSession(): boolean {
      return this.isAuthenticated;
    },
    /**
     * 判断是否具备指定权限。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    hasPermission: (state) => {
      return (permissionCode: string): boolean => {
        if (!permissionCode) {
          return false;
        }
        return (state.permissions || []).includes(permissionCode);
      };
    }
  },
  actions: {
    /**
     * 更新登录状态。
     *
     * @param token 登录令牌
     * @param userId 用户ID
     * @param tenantId 租户标识
     * @param username 用户名称
     * @author 余炘洋
     * @date 2026-01-23
     */
    applyLoginResponse(response: LoginResponse) {
      const restricted = response.authFlow === PASSWORD_CHANGE_REQUIRED_FLOW;
      this.token = response.token || '';
      this.refreshToken = restricted ? '' : (response.refreshToken || '');
      this.userId = response.userId || 0;
      this.tenantId = response.tenantId;
      this.tenantName = response.tenantName || '';
      this.username = response.username;
      this.nickname = response.nickname || '';
      this.experience = restricted ? false : !!response.experience;
      this.roles = restricted ? [] : (response.roles || []);
      this.permissions = restricted ? [] : (response.permissions || []);
      this.relationScopes = restricted ? {} : (response.relationScopes || {});
      this.menus = [];
      this.authFlow = response.authFlow;
      this.sessionReady = true;
      if (this.isSysAdmin) {
        this.activeTenantId = '';
        this.activeTenantName = '';
      } else {
        this.activeTenantId = response.tenantId;
        this.activeTenantName = response.tenantName || response.tenantId;
      }
      localStorage.setItem('userId', String(this.userId || 0));
      localStorage.setItem('token', this.token || '');
      if (this.refreshToken) {
        localStorage.setItem('refreshToken', this.refreshToken);
      } else {
        localStorage.removeItem('refreshToken');
      }
      localStorage.setItem('tenantId', response.tenantId);
      localStorage.setItem('tenantName', response.tenantName || '');
      localStorage.setItem('username', response.username);
      localStorage.setItem('nickname', response.nickname || '');
      localStorage.setItem('experience', this.experience ? '1' : '0');
      localStorage.setItem('roles', JSON.stringify(this.roles || []));
      localStorage.setItem('permissions', JSON.stringify(this.permissions || []));
      localStorage.setItem('relationScopes', JSON.stringify(this.relationScopes || {}));
      localStorage.setItem('activeTenantId', this.activeTenantId || '');
      localStorage.setItem('activeTenantName', this.activeTenantName || '');
      localStorage.setItem(AUTH_FLOW_KEY, this.authFlow);
    },
    /**
     * 将当前令牌标记为仅允许修改密码的受限会话。
     *
     * @author 余炘洋
     * @date 2026-08-16
     */
    requirePasswordChange() {
      this.authFlow = PASSWORD_CHANGE_REQUIRED_FLOW;
      this.refreshToken = '';
      this.roles = [];
      this.permissions = [];
      this.relationScopes = {};
      this.menus = [];
      this.sessionReady = true;
      localStorage.setItem(AUTH_FLOW_KEY, this.authFlow);
      localStorage.removeItem('refreshToken');
      localStorage.setItem('roles', '[]');
      localStorage.setItem('permissions', '[]');
      localStorage.setItem('relationScopes', '{}');
    },
    /**
     * 从本地凭据恢复受限会话，不触发受限接口之外的请求。
     *
     * @author 余炘洋
     * @date 2026-08-16
     */
    restoreRestrictedSession() {
      if (this.isRestrictedSession) {
        this.sessionReady = true;
      }
    },
    /**
     * 更新菜单树。
     *
     * @param menus 菜单树
     * @author 余炘洋
     * @date 2026-01-23
     */
    setMenus(menus: MenuView[]) {
      this.menus = menus || [];
    },
    /**
     * 退出登录并清理缓存。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    logout() {
      this.token = '';
      this.refreshToken = '';
      this.userId = 0;
      this.tenantId = '';
      this.tenantName = '';
      this.activeTenantId = '';
      this.activeTenantName = '';
      this.username = '';
      this.nickname = '';
      this.experience = false;
      this.roles = [];
      this.permissions = [];
      this.relationScopes = {};
      this.menus = [];
      this.authFlow = AUTHENTICATED_FLOW;
      this.sessionReady = false;
      localStorage.removeItem('userId');
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('tenantId');
      localStorage.removeItem('tenantName');
      localStorage.removeItem('activeTenantId');
      localStorage.removeItem('activeTenantName');
      localStorage.removeItem('username');
      localStorage.removeItem('nickname');
      localStorage.removeItem('experience');
      localStorage.removeItem('roles');
      localStorage.removeItem('permissions');
      localStorage.removeItem('relationScopes');
      localStorage.removeItem(AUTH_FLOW_KEY);
    },
    /**
     * 用后端会话信息刷新状态。
     *
     * @param profile 会话信息
     * @author 余炘洋
     * @date 2026-01-27
     */
    applyProfile(profile: UserProfileView) {
      if (!profile || !profile.userId) {
        return;
      }
      this.userId = profile.userId || 0;
      this.tenantId = profile.tenantId || '';
      this.username = profile.username || '';
      this.nickname = profile.nickname || '';
      this.experience = !!profile.experience;
      this.roles = profile.roles || [];
      this.permissions = profile.permissions || [];
      this.relationScopes = profile.relationScopes || {};
      this.authFlow = AUTHENTICATED_FLOW;
      if (this.isSysAdmin) {
        if (!this.activeTenantId) {
          this.activeTenantId = '';
          this.activeTenantName = '';
        }
      } else {
        this.activeTenantId = this.tenantId;
        if (!this.tenantName) {
          this.tenantName = this.tenantId;
        }
        this.activeTenantName = this.tenantName;
      }
      localStorage.setItem('userId', String(this.userId || 0));
      localStorage.setItem('tenantId', this.tenantId || '');
      localStorage.setItem('username', this.username || '');
      localStorage.setItem('nickname', this.nickname || '');
      localStorage.setItem('experience', this.experience ? '1' : '0');
      localStorage.setItem('roles', JSON.stringify(this.roles || []));
      localStorage.setItem('permissions', JSON.stringify(this.permissions || []));
      localStorage.setItem('relationScopes', JSON.stringify(this.relationScopes || {}));
      localStorage.setItem('activeTenantId', this.activeTenantId || '');
      localStorage.setItem('activeTenantName', this.activeTenantName || '');
      localStorage.setItem(AUTH_FLOW_KEY, this.authFlow);
    },
    /**
     * 更新访问令牌与刷新令牌。
     *
     * @param token 访问令牌
     * @param refreshToken 刷新令牌
     * @author 余炘洋
     * @date 2026-01-29
     */
    updateToken(token: string, refreshToken = '') {
      this.token = token || '';
      this.refreshToken = refreshToken || '';
      localStorage.setItem('token', this.token || '');
      if (this.refreshToken) {
        localStorage.setItem('refreshToken', this.refreshToken);
      } else {
        localStorage.removeItem('refreshToken');
      }
    },
    /**
     * 更新租户标识。
     *
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-01-23
     */
    updateTenant(tenantId: string) {
      this.tenantId = tenantId;
      localStorage.setItem('tenantId', tenantId);
    },
    /**
     * 更新租户名称。
     *
     * @param tenantName 租户名称
     * @author 余炘洋
     * @date 2026-01-26
     */
    updateTenantName(tenantName: string) {
      this.tenantName = tenantName || '';
      localStorage.setItem('tenantName', this.tenantName);
      if (!this.isSysAdmin) {
        this.activeTenantName = this.tenantName || this.tenantId;
        localStorage.setItem('activeTenantName', this.activeTenantName);
      }
    },
    /**
     * 更新当前选择租户。
     *
     * @param tenantId 租户ID
     * @param tenantName 租户名称
     * @author 余炘洋
     * @date 2026-01-26
     */
    setActiveTenant(tenantId: string, tenantName = '') {
      if (this.isSysAdmin) {
        this.activeTenantId = tenantId;
        this.activeTenantName = tenantName || '';
      } else {
        this.activeTenantId = this.tenantId;
        this.activeTenantName = this.tenantName || this.tenantId;
      }
      localStorage.setItem('activeTenantId', this.activeTenantId || '');
      localStorage.setItem('activeTenantName', this.activeTenantName || '');
    }
  }
});
