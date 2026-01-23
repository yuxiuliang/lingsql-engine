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

