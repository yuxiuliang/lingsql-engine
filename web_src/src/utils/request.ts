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

interface RequestOptions extends RequestInit {
  skipAuthRefresh?: boolean;
}

interface RefreshPayload {
  token: string;
  refreshToken?: string;
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
const LOGIN_PATH = '/api/v1/auth/login';
const REFRESH_PATH = '/api/v1/auth/refresh';
const REFRESH_TOKEN_HEADER = 'X-Refresh-Token';
const TOKEN_EXPIRED_MARK = 'TOKEN_EXPIRED';
const REFRESH_LOG_INTERVAL_MS = 60000;
const REFRESH_METRIC_EVENT = 'lingsql:auth-refresh';

let refreshPromise: Promise<RefreshPayload | null> | null = null;
let lastRefreshMetricAt = 0;

const parseJsonSafely = async <T>(response: Response): Promise<T | null> => {
  const contentType = response.headers.get('Content-Type') || '';
  if (!contentType.includes('application/json')) {
    return null;
  }
  try {
    return (await response.json()) as T;
  } catch (error) {
    return null;
  }
};

const shouldAttemptRefresh = (
  path: string,
  options: RequestOptions,
  status: number,
  payload: ApiResponse<unknown> | null,
  refreshToken: string
): boolean => {
  if (options.skipAuthRefresh) {
    return false;
  }
  if (!refreshToken) {
    return false;
  }
  if (path.startsWith(LOGIN_PATH) || path.startsWith(REFRESH_PATH)) {
    return false;
  }
  if (status !== 401) {
    return false;
  }
  if (!payload?.message) {
    return false;
  }
  return payload.message.toUpperCase() === TOKEN_EXPIRED_MARK;
};

const reportRefreshMetric = (success: boolean, durationMs: number, reason?: string) => {
  const now = Date.now();
  if (now - lastRefreshMetricAt < REFRESH_LOG_INTERVAL_MS) {
    return;
  }
  lastRefreshMetricAt = now;
  const detail = {
    success,
    durationMs,
    reason: reason || '',
    timestamp: now
  };
  if (import.meta.env.DEV) {
    console.info('[auth-refresh]', detail);
  }
  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    try {
      window.dispatchEvent(new CustomEvent(REFRESH_METRIC_EVENT, {detail}));
    } catch (error) {
      // ignore event failures
    }
  }
};

const refreshSessionOnce = async (refreshToken: string): Promise<RefreshPayload | null> => {
  if (!refreshToken) {
    return null;
  }
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const startedAt = Date.now();
      try {
        const headers = new Headers();
        headers.set('Content-Type', 'application/json');
        headers.set(REFRESH_TOKEN_HEADER, refreshToken);
        const response = await fetch(`${BASE_URL}${REFRESH_PATH}`, {
          method: 'POST',
          headers
        });
        const payload = await parseJsonSafely<ApiResponse<RefreshPayload>>(response);
        if (!response.ok) {
          reportRefreshMetric(false, Date.now() - startedAt, `http_${response.status}`);
          return null;
        }
        if (!payload || payload.code !== 200 || !payload.data?.token) {
          reportRefreshMetric(false, Date.now() - startedAt, 'payload_invalid');
          return null;
        }
        reportRefreshMetric(true, Date.now() - startedAt);
        return payload.data;
      } catch (error: any) {
        reportRefreshMetric(false, Date.now() - startedAt, error?.message || 'network_error');
        return null;
      }
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
};

/**
 * 发起标准化请求并解析业务结果。
 *
 * @param path 请求路径
 * @param options 请求配置
 * @return 业务数据
 * @author 余炘洋
 * @date 2026-01-23
 */
export const request = async <T>(path: string, options: RequestOptions = {}): Promise<T> => {
  const authStore = useAuthStore(pinia);
  const tenantId = authStore.isSysAdmin ? (authStore.activeTenantId || '') : (authStore.activeTenantId || authStore.tenantId || '');
  const headers = new Headers(options.headers || {});
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (authStore.token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${authStore.token}`);
  }
  if (tenantId) {
    headers.set('X-Tenant-Id', encodeURIComponent(tenantId));
  }
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers
  });
  const payload = await parseJsonSafely<ApiResponse<T>>(response);
  if (shouldAttemptRefresh(path, options, response.status, payload as ApiResponse<unknown> | null, authStore.refreshToken)) {
    const refreshed = await refreshSessionOnce(authStore.refreshToken);
    if (refreshed) {
      authStore.updateToken(refreshed.token, refreshed.refreshToken || '');
      const retryOptions: RequestOptions = {...options, skipAuthRefresh: true};
      if (retryOptions.headers) {
        const retryHeaders = new Headers(retryOptions.headers);
        retryHeaders.delete('Authorization');
        retryOptions.headers = retryHeaders;
      }
      return request<T>(path, retryOptions);
    }
  }
  if (!response.ok) {
    if (response.status === 401) {
      authStore.logout();
      redirectToLogin();
      throw new Error(payload?.message || '未登录或登录已过期');
    }
    if (response.status === 403) {
      throw new Error(payload?.message || '无权限访问');
    }
    throw new Error(payload?.message || `接口请求失败: ${response.status}`);
  }
  if (!payload) {
    throw new Error('接口返回异常');
  }
  if (payload.code === 401) {
    authStore.logout();
    redirectToLogin();
    throw new Error(payload.message || '未登录或登录已过期');
  }
  if (payload.code === 403) {
    throw new Error(payload.message || '无权限访问');
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
