import {useAuthStore} from '@/store/auth';
import {pinia} from '@/store';

/**
 * 统一请求响应结构定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

interface RefreshLoginResponse {
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
}

/**
 * 构建请求基础地址。
 *
 * @return 基础地址
 * @author 余炘洋
 * @date 2026-01-23
 */
const resolveBaseUrl = (): string => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;
  return baseUrl ? String(baseUrl).replace(/\/$/, '') : '';
};

const BASE_URL = resolveBaseUrl();

/**
 * 发起标准化请求并解析业务结果。
 *
 * @param path 请求路径
 * @param options 请求配置
 * @return 业务数据
 * @author 余炘洋
 * @date 2026-01-23
 */
let refreshPromise: Promise<boolean> | null = null;

export const request = async <T>(path: string, options: RequestInit = {}, retry = true): Promise<T> => {
  const authStore = useAuthStore(pinia);
  const tenantId = authStore.isSysAdmin ? (authStore.activeTenantId || '') : (authStore.activeTenantId || authStore.tenantId || '');
  const headers = new Headers(options.headers || {});
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (tenantId) {
    headers.set('X-Tenant-Id', encodeURIComponent(tenantId));
  }
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers,
    credentials: options.credentials || 'include'
  });
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      const refreshed = retry ? await tryRefresh(path) : false;
      if (refreshed) {
        return request<T>(path, options, false);
      }
      authStore.logout();
      redirectToLogin();
      throw new Error('未登录或无权限');
    }
    throw new Error(`接口请求失败: ${response.status}`);
  }
  const payload = (await response.json()) as ApiResponse<T>;
  if (payload.code === 401 || payload.code === 403) {
    const refreshed = retry ? await tryRefresh(path) : false;
    if (refreshed) {
      return request<T>(path, options, false);
    }
    authStore.logout();
    redirectToLogin();
    throw new Error(payload.message || '未登录或无权限');
  }
  if (payload.code !== 200) {
    throw new Error(payload.message || '接口返回失败');
  }
  return payload.data;
};

const redirectToLogin = () => {
  if (typeof window === 'undefined') {
    return;
  }
  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
};

const tryRefresh = async (path: string): Promise<boolean> => {
  if (path.startsWith('/api/v1/auth/login') || path.startsWith('/api/v1/auth/refresh')) {
    return false;
  }
  if (refreshPromise) {
    return refreshPromise;
  }
  refreshPromise = doRefresh();
  const result = await refreshPromise;
  refreshPromise = null;
  return result;
};

const doRefresh = async (): Promise<boolean> => {
  const authStore = useAuthStore(pinia);
  try {
    const response = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      credentials: 'include'
    });
    if (!response.ok) {
      return false;
    }
    const payload = (await response.json()) as ApiResponse<RefreshLoginResponse>;
    if (payload.code !== 200 || !payload.data) {
      return false;
    }
    const data = payload.data;
    authStore.login(
      data.token,
      data.userId,
      data.tenantId,
      data.username,
      data.nickname || '',
      data.experience || false,
      data.roles || [],
      data.permissions || [],
      data.tenantName || ''
    );
    return true;
  } catch {
    return false;
  }
};
