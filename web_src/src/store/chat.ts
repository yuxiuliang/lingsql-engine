import {defineStore} from 'pinia';
import {ChatHistoryItem} from '@/api/chat';

/**
 * 聊天历史状态定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface ChatState {
  historyList: ChatHistoryItem[];
  total: number;
  page: number;
  size: number;
  keyword: string;
}

/**
 * 聊天历史状态管理。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export const useChatStore = defineStore('chat', {
  state: (): ChatState => ({
    historyList: [],
    total: 0,
    page: 1,
    size: 10,
    keyword: ''
  }),
  actions: {
    /**
     * 设置历史记录列表。
     *
     * @param list 历史记录
     * @author 余炘洋
     * @date 2026-01-23
     */
    setHistory(list: ChatHistoryItem[]) {
      this.historyList = Array.isArray(list) ? list : [];
    },

    /**
     * 设置分页历史记录。
     *
     * @param payload 分页数据
     * @author 余炘洋
     * @date 2026-01-23
     */
    setHistoryPage(payload: {list: ChatHistoryItem[]; total: number; page: number; size: number; keyword?: string}) {
      this.historyList = payload.list || [];
      this.total = payload.total || 0;
      this.page = payload.page || 1;
      this.size = payload.size || 10;
      this.keyword = payload.keyword || '';
    },

    /**
     * 插入最新历史记录。
     *
     * @param item 历史记录
     * @author 余炘洋
     * @date 2026-01-23
     */
    prependHistory(item: ChatHistoryItem) {
      this.historyList.unshift(item);
      this.total += 1;
      if (this.historyList.length > this.size) {
        this.historyList.pop();
      }
    },

    /**
     * 移除历史记录。
     *
     * @param id 记录ID
     * @author 余炘洋
     * @date 2026-01-23
     */
    removeHistory(id: number) {
      const before = this.historyList.length;
      this.historyList = this.historyList.filter((item) => item.id !== id);
      if (this.historyList.length < before) {
        this.total = Math.max(0, this.total - 1);
      }
    },

    /**
     * 更新历史记录问题标题。
     *
     * @param id 记录ID
     * @param title 新标题
     * @author 余炘洋
     * @date 2026-01-23
     */
    renameHistory(id: number, title: string) {
      const target = this.historyList.find((item) => item.id === id);
      if (target) {
        target.question = title;
      }
    }
  }
});
