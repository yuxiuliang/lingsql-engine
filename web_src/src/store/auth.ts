import {defineStore} from 'pinia';

/**
 * 认证状态定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface AuthState {
  token: string;
  tenantId: string;
  username: string;
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
    username: localStorage.getItem('username') || ''
  }),
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
    login(token: string, tenantId: string, username: string) {
      this.token = token;
      this.tenantId = tenantId;
      this.username = username;
      localStorage.setItem('token', token);
      localStorage.setItem('tenantId', tenantId);
      localStorage.setItem('username', username);
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
      this.username = '';
      localStorage.removeItem('token');
      localStorage.removeItem('tenantId');
      localStorage.removeItem('username');
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
    }
  }
});

