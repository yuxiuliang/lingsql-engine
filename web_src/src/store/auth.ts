import {defineStore} from 'pinia';
import type {MenuView} from '@/api/auth';

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
  tenantId: string;
  tenantName: string;
  activeTenantId: string;
  activeTenantName: string;
  username: string;
  nickname: string;
  roles: string[];
  permissions: string[];
  menus: MenuView[];
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
    tenantId: localStorage.getItem('tenantId') || '',
    tenantName: localStorage.getItem('tenantName') || '',
    activeTenantId: localStorage.getItem('activeTenantId') || '',
    activeTenantName: localStorage.getItem('activeTenantName') || '',
    username: localStorage.getItem('username') || '',
    nickname: localStorage.getItem('nickname') || '',
    roles: readArray('roles'),
    permissions: readArray('permissions'),
    menus: []
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
    }
  },
  actions: {
    /**
     * 更新登录状态。
     *
     * @param token 登录令牌
     * @param tenantId 租户标识
     * @param username 用户名称
     * @author 余炘洋
     * @date 2026-01-23
     */
    login(
      token: string,
      tenantId: string,
      username: string,
      nickname = '',
      roles: string[] = [],
      permissions: string[] = [],
      tenantName = ''
    ) {
      this.token = token;
      this.tenantId = tenantId;
      this.tenantName = tenantName || '';
      this.username = username;
      this.nickname = nickname;
      this.roles = roles;
      this.permissions = permissions;
      if (this.isSysAdmin) {
        this.activeTenantId = '';
        this.activeTenantName = '';
      } else {
        this.activeTenantId = tenantId;
        this.activeTenantName = tenantName || tenantId;
      }
      localStorage.setItem('token', token);
      localStorage.setItem('tenantId', tenantId);
      localStorage.setItem('tenantName', tenantName || '');
      localStorage.setItem('username', username);
      localStorage.setItem('nickname', nickname || '');
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
      this.tenantId = '';
      this.tenantName = '';
      this.activeTenantId = '';
      this.activeTenantName = '';
      this.username = '';
      this.nickname = '';
      this.roles = [];
      this.permissions = [];
      this.menus = [];
      localStorage.removeItem('token');
      localStorage.removeItem('tenantId');
      localStorage.removeItem('tenantName');
      localStorage.removeItem('activeTenantId');
      localStorage.removeItem('activeTenantName');
      localStorage.removeItem('username');
      localStorage.removeItem('nickname');
      localStorage.removeItem('roles');
      localStorage.removeItem('permissions');
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
