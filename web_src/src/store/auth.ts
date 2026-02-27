import {defineStore} from 'pinia';
import type {MenuView, UserProfileView} from '@/api/auth';

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
  menus: MenuView[];
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
    refreshToken: '',
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
    menus: [],
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
      return state.sessionReady && !!state.userId;
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
    login(
      token: string,
      userId: number,
      tenantId: string,
      username: string,
      nickname = '',
      experience = false,
      roles: string[] = [],
      permissions: string[] = [],
      tenantName = '',
      refreshToken = ''
    ) {
      this.token = token || '';
      this.refreshToken = refreshToken || '';
      this.userId = userId || 0;
      this.tenantId = tenantId;
      this.tenantName = tenantName || '';
      this.username = username;
      this.nickname = nickname;
      this.experience = experience;
      this.roles = roles;
      this.permissions = permissions;
      this.sessionReady = true;
      if (this.isSysAdmin) {
        this.activeTenantId = '';
        this.activeTenantName = '';
      } else {
        this.activeTenantId = tenantId;
        this.activeTenantName = tenantName || tenantId;
      }
      localStorage.setItem('userId', String(this.userId || 0));
      localStorage.setItem('token', this.token || '');
      localStorage.removeItem('refreshToken');
      localStorage.setItem('tenantId', tenantId);
      localStorage.setItem('tenantName', tenantName || '');
      localStorage.setItem('username', username);
      localStorage.setItem('nickname', nickname || '');
      localStorage.setItem('experience', this.experience ? '1' : '0');
      localStorage.setItem('roles', JSON.stringify(roles || []));
      localStorage.setItem('permissions', JSON.stringify(permissions || []));
      localStorage.setItem('activeTenantId', this.activeTenantId || '');
      localStorage.setItem('activeTenantName', this.activeTenantName || '');
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
      this.menus = [];
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
      localStorage.setItem('activeTenantId', this.activeTenantId || '');
      localStorage.setItem('activeTenantName', this.activeTenantName || '');
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
      localStorage.removeItem('refreshToken');
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
