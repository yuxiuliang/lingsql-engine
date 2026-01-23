import {request} from '@/utils/request';

/**
 * 问答请求体定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface ChatRequest {
  question: string;
}

/**
 * 问答结果定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface ChatResult {
  answer: string;
  data: Array<Record<string, unknown>>;
  sql: string;
  historyId?: number;
}

/**
 * 历史记录定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface ChatHistoryItem {
  id: number;
  question: string;
  answer: string;
  sql?: string;
  createTime?: string;
  data?: Array<Record<string, unknown>>;
}

/**
 * 分页结果定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

/**
 * 历史记录详情定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface ChatHistoryDetail {
  id: number;
  question: string;
  answer: string;
  sql?: string;
  data: Array<Record<string, unknown>>;
  createTime?: string;
}

/**
 * 发送问答请求。
 *
 * @param question 问题内容
 * @return 问答结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const askQuestion = async (question: string): Promise<ChatResult> => {
  return request<ChatResult>('/api/v1/chat/ask', {
    method: 'POST',
    body: JSON.stringify({question})
  });
};

/**
 * 查询历史记录。
 *
 * @return 历史记录列表
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchChatHistory = async (params: {page: number; size: number; keyword?: string}): Promise<PageResult<ChatHistoryItem>> => {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
    keyword: params.keyword ? params.keyword : ''
  });
  return request<PageResult<ChatHistoryItem>>(`/api/v1/chat/history?${query.toString()}`);
};

/**
 * 查询历史记录详情。
 *
 * @param id 记录ID
 * @return 历史详情
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchChatHistoryDetail = async (id: number): Promise<ChatHistoryDetail> => {
  return request<ChatHistoryDetail>(`/api/v1/chat/history/${id}`);
};

/**
 * 重命名历史记录。
 *
 * @param id 记录ID
 * @param question 新标题
 * @return 更新结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const renameChatHistory = async (id: number, question: string): Promise<ChatHistoryItem> => {
  return request<ChatHistoryItem>(`/api/v1/chat/history/${id}`, {
    method: 'PUT',
    body: JSON.stringify({question})
  });
};

/**
 * 删除历史记录。
 *
 * @param id 记录ID
 * @return 删除结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const deleteChatHistory = async (id: number): Promise<string> => {
  return request<string>(`/api/v1/chat/history/${id}`, {
    method: 'DELETE'
  });
};
