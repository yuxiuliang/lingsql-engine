import {useAuthStore} from '@/store/auth';
import {pinia} from '@/store';
import router from '@/router';

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
export const request = async <T>(path: string, options: RequestInit = {}): Promise<T> => {
  const authStore = useAuthStore(pinia);
  const token = authStore.token || '';
  const tenantId = authStore.isSysAdmin ? (authStore.activeTenantId || '') : (authStore.activeTenantId || authStore.tenantId || '');
  const headers = new Headers(options.headers || {});
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  if (tenantId) {
    headers.set('X-Tenant-Id', tenantId);
  }
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers
  });
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      authStore.logout();
      await router.push('/login');
      throw new Error('未登录或无权限');
    }
    throw new Error(`接口请求失败: ${response.status}`);
  }
  const payload = (await response.json()) as ApiResponse<T>;
  if (payload.code === 401 || payload.code === 403) {
    authStore.logout();
    await router.push('/login');
    throw new Error(payload.message || '未登录或无权限');
  }
  if (payload.code !== 200) {
    throw new Error(payload.message || '接口返回失败');
  }
  return payload.data;
};
