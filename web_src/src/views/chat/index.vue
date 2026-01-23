<script setup lang="ts">
import {nextTick, onMounted, ref, watch} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {
  askQuestion,
  ChatHistoryDetail,
  ChatHistoryItem,
  ChatResult,
  deleteChatHistory,
  fetchChatHistory,
  fetchChatHistoryDetail,
  renameChatHistory
} from '@/api/chat';
import {useChatStore} from '@/store/chat';

/**
 * 对话消息定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
interface ChatMessage {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  sql?: string;
  data?: Array<Record<string, unknown>>;
}

const chatStore = useChatStore();
const question = ref('');
const loading = ref(false);
const historyLoading = ref(false);
const messages = ref<ChatMessage[]>([]);
const messageSeed = ref(1);
const drawerVisible = ref(false);
const drawerSql = ref('');
const messageContainerRef = ref<HTMLDivElement | null>(null);
const activeHistoryId = ref<number | null>(null);
const historyKeyword = ref(chatStore.keyword || '');
const historyPage = ref(chatStore.page || 1);
const historySize = ref(chatStore.size || 10);

/**
 * 生成唯一消息ID。
 *
 * @return 消息ID
 * @author 余炘洋
 * @date 2026-01-23
 */
const nextMessageId = () => {
  const value = messageSeed.value;
  messageSeed.value += 1;
  return value;
};

/**
 * 判断是否需要展示 SQL 入口。
 *
 * @param sql SQL 文本
 * @return 是否展示
 * @author 余炘洋
 * @date 2026-01-23
 */
const shouldShowSql = (sql?: string) => {
  return !!sql && sql !== 'ERROR:NO_SCHEMA_MATCH' && !sql.startsWith('ERROR:');
};

/**
 * 判断是否为异常记录。
 *
 * @param sql SQL 文本
 * @return 是否异常
 * @author 余炘洋
 * @date 2026-01-23
 */
const isErrorSql = (sql?: string) => {
  return !!sql && sql.startsWith('ERROR:');
};

/**
 * 打开 SQL 抽屉展示。
 *
 * @param sql SQL 文本
 * @author 余炘洋
 * @date 2026-01-23
 */
const openSqlDrawer = (sql?: string) => {
  if (!sql) {
    return;
  }
  drawerSql.value = sql;
  drawerVisible.value = true;
};

/**
 * 解析表格列名。
 *
 * @param rows 数据行
 * @return 列名列表
 * @author 余炘洋
 * @date 2026-01-23
 */
const resolveColumns = (rows?: Array<Record<string, unknown>>): string[] => {
  if (!rows || rows.length === 0) {
    return [];
  }
  return Object.keys(rows[0]);
};

/**
 * 滚动到最新消息。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const scrollToBottom = async () => {
  await nextTick();
  const container = messageContainerRef.value;
  if (container) {
    container.scrollTop = container.scrollHeight;
  }
};

/**
 * 初始化历史记录。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadHistory = async () => {
  try {
    const history = await fetchChatHistory({
      page: historyPage.value,
      size: historySize.value,
      keyword: historyKeyword.value.trim()
    });
    chatStore.setHistoryPage({
      list: history.list,
      total: history.total,
      page: history.page,
      size: history.size,
      keyword: historyKeyword.value.trim()
    });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载历史记录失败');
  }
};

/**
 * 创建新对话。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const startNewChat = () => {
  messages.value = [];
  question.value = '';
  activeHistoryId.value = null;
  drawerVisible.value = false;
  drawerSql.value = '';
};

/**
 * 加载历史记录详情。
 *
 * @param item 历史记录
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadHistoryDetail = async (item: ChatHistoryItem) => {
  historyLoading.value = true;
  activeHistoryId.value = item.id;
  try {
    const detail: ChatHistoryDetail = await fetchChatHistoryDetail(item.id);
    messages.value = [
      {id: nextMessageId(), role: 'user', content: detail.question},
      {
        id: nextMessageId(),
        role: 'assistant',
        content: detail.answer,
        sql: detail.sql,
        data: detail.data || []
      }
    ];
    await scrollToBottom();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载历史详情失败');
  } finally {
    historyLoading.value = false;
  }
};

/**
 * 发送问题并追加对话结果。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleSend = async () => {
  const content = question.value.trim();
  if (!content) {
    ElMessage.warning('请输入问题');
    return;
  }
  messages.value.push({id: nextMessageId(), role: 'user', content});
  question.value = '';
  loading.value = true;
  await scrollToBottom();
  try {
    const result: ChatResult = await askQuestion(content);
    const assistantMessage: ChatMessage = {
      id: nextMessageId(),
      role: 'assistant',
      content: result.answer,
      sql: result.sql,
      data: result.data || []
    };
    messages.value.push(assistantMessage);
    if (result.historyId && historyPage.value === 1 && !historyKeyword.value.trim()) {
      chatStore.prependHistory({
        id: result.historyId,
        question: content,
        answer: result.answer,
        sql: result.sql,
        createTime: new Date().toISOString()
      });
    } else {
      await loadHistory();
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '请求失败';
    messages.value.push({
      id: nextMessageId(),
      role: 'assistant',
      content: `调用失败：${message}`
    });
  } finally {
    loading.value = false;
    await scrollToBottom();
  }
};

/**
 * 重命名历史记录。
 *
 * @param item 历史记录
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleRename = async (item: ChatHistoryItem) => {
  try {
    const {value} = await ElMessageBox.prompt('请输入新的标题', '重命名历史记录', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: item.question || '',
      inputPlaceholder: '输入新标题'
    });
    if (!value || !value.trim()) {
      ElMessage.warning('标题不能为空');
      return;
    }
    const updated = await renameChatHistory(item.id, value.trim());
    chatStore.renameHistory(item.id, updated.question);
    ElMessage.success('历史记录已更新');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '更新失败');
    }
  }
};

/**
 * 删除历史记录。
 *
 * @param item 历史记录
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleDelete = async (item: ChatHistoryItem) => {
  try {
    await ElMessageBox.confirm('确认删除该历史记录？', '提示', {type: 'warning'});
    await deleteChatHistory(item.id);
    chatStore.removeHistory(item.id);
    if (activeHistoryId.value === item.id) {
      startNewChat();
    }
    if (chatStore.historyList.length === 0 && historyPage.value > 1) {
      historyPage.value -= 1;
      await loadHistory();
    }
    ElMessage.success('历史记录已删除');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
};

/**
 * 截断问题文本。
 *
 * @param text 文本
 * @return 截断文本
 * @author 余炘洋
 * @date 2026-01-23
 */
const truncateQuestion = (text: string) => {
  if (!text) {
    return '';
  }
  return text.length > 15 ? `${text.slice(0, 15)}...` : text;
};

/**
 * 执行历史搜索。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleSearch = async () => {
  historyPage.value = 1;
  await loadHistory();
};

/**
 * 切换分页。
 *
 * @param page 页码
 * @author 余炘洋
 * @date 2026-01-23
 */
const handlePageChange = async (page: number) => {
  historyPage.value = page;
  await loadHistory();
};

watch(
  () => messages.value.length,
  () => {
    scrollToBottom();
  }
);

onMounted(() => {
  loadHistory();
});
</script>

<template>
  <div class="chat-shell">
    <aside class="history-panel">
      <div class="history-header">
        <div>
          <div class="history-title">最近记录</div>
          <div class="history-subtitle">工作流历史</div>
        </div>
        <el-button class="new-chat-button" @click="startNewChat">+ 开启新对话</el-button>
      </div>
      <div class="history-search">
        <el-input
          v-model="historyKeyword"
          size="small"
          clearable
          placeholder="搜索历史问题"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button size="small" class="search-button" @click="handleSearch">搜索</el-button>
      </div>
      <div class="history-list">
        <div
          v-for="item in chatStore.historyList"
          :key="item.id"
          class="history-item"
          :class="{active: item.id === activeHistoryId}"
          @click="loadHistoryDetail(item)"
        >
          <div class="history-text">{{ truncateQuestion(item.question) }}</div>
          <el-tag v-if="isErrorSql(item.sql)" size="small" type="danger" effect="dark">异常</el-tag>
          <div class="history-actions">
            <button class="icon-button" @click.stop="handleRename(item)" title="重命名">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M4 17.25V20h2.75L19.81 6.94l-2.75-2.75L4 17.25zm15.71-9.04a1 1 0 0 0 0-1.41l-2.5-2.5a1 1 0 0 0-1.41 0l-1.83 1.83 3.91 3.91 1.83-1.83z"
                />
              </svg>
            </button>
            <button class="icon-button danger" @click.stop="handleDelete(item)" title="删除">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M6 7h12l-1 13H7L6 7zm3-3h6l1 2H8l1-2z"
                />
              </svg>
            </button>
          </div>
        </div>
        <el-empty v-if="!chatStore.historyList.length" description="暂无记录"/>
      </div>
      <el-pagination
        class="history-pagination"
        small
        :total="chatStore.total"
        :page-size="historySize"
        :current-page="historyPage"
        layout="prev, pager, next"
        @current-change="handlePageChange"
      />
    </aside>

    <section class="chat-panel">
      <div ref="messageContainerRef" class="chat-messages">
        <div v-for="message in messages" :key="message.id" class="bubble-row" :class="message.role">
          <div class="bubble-avatar">
            <div class="avatar-circle" :class="message.role">{{ message.role === 'user' ? 'U' : '语' }}</div>
          </div>
          <div class="bubble">
            <div class="bubble-role">{{ message.role === 'user' ? '用户' : '语数' }}</div>
            <div class="bubble-content">{{ message.content }}</div>

            <template v-if="message.role === 'assistant' && shouldShowSql(message.sql)">
              <div class="sql-toggle" @click="openSqlDrawer(message.sql)">查看执行 SQL</div>
            </template>

            <template v-if="message.role === 'assistant' && message.data && message.data.length">
              <div class="table-section">
                <div class="table-title">查询结果</div>
                <el-table class="result-table" :data="message.data" border :max-height="360">
                  <el-table-column
                    v-for="column in resolveColumns(message.data)"
                    :key="column"
                    :prop="column"
                    :label="column"
                    min-width="120"
                  />
                </el-table>
              </div>
            </template>
          </div>
        </div>

        <el-empty v-if="!messages.length && !loading" description="暂无对话记录"/>
      </div>

      <div class="chat-input-wrapper">
        <div v-if="loading" class="loading-tip">
          <span class="loading-spinner"></span>
          <span>语数正在检索数据库...</span>
        </div>
        <el-card class="chat-input-card">
          <el-input
            v-model="question"
            type="textarea"
            :autosize="{minRows: 2, maxRows: 6}"
            placeholder="请输入你的问题，回车发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="handleSend"
          />
          <div class="chat-input-actions">
            <el-button type="primary" :loading="loading" :disabled="!question.trim()" @click="handleSend">
              发送
            </el-button>
          </div>
        </el-card>
      </div>

      <div v-if="historyLoading" class="history-mask">
        <div class="history-mask-card">正在加载历史记录...</div>
      </div>

      <el-drawer v-model="drawerVisible" direction="rtl" size="36%" :with-header="false">
        <div class="sql-drawer">
          <div class="sql-drawer-title">执行 SQL</div>
          <pre class="sql-block">{{ drawerSql }}</pre>
        </div>
      </el-drawer>
    </section>
  </div>
</template>

<style scoped>
.chat-shell {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
  padding: 14px;
  background: radial-gradient(circle at top, #111827, #0f172a 60%);
  border-radius: 18px;
  overflow: hidden;
}

.history-panel {
  background: rgba(15, 23, 42, 0.75);
  border-radius: 16px;
  padding: 14px;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.4);
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.history-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-title {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}

.history-subtitle {
  font-size: 12px;
  color: #94a3b8;
}

.new-chat-button {
  width: 100%;
  border: none;
  background: linear-gradient(90deg, rgba(64, 158, 255, 0.85), rgba(103, 194, 58, 0.85));
  color: #fff;
  box-shadow: 0 10px 20px rgba(64, 158, 255, 0.3);
}

.history-search {
  display: flex;
  gap: 6px;
}

.history-search :deep(.el-input__wrapper) {
  background: rgba(30, 41, 59, 0.7);
  border: none;
  box-shadow: none;
}

.history-search :deep(.el-input__inner) {
  color: #e2e8f0;
}

.search-button {
  border: none;
  background: rgba(148, 163, 184, 0.18);
  color: #e2e8f0;
}

.history-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
  min-height: 0;
}

.history-pagination {
  margin-top: 4px;
  display: flex;
  justify-content: center;
}

.history-pagination :deep(.btn-prev),
.history-pagination :deep(.btn-next),
.history-pagination :deep(.el-pager li) {
  background: rgba(30, 41, 59, 0.7);
  border: none;
  color: #e2e8f0;
}

.history-pagination :deep(.el-pager li.is-active) {
  background: rgba(59, 130, 246, 0.5);
}

.history-item {
  background: rgba(30, 41, 59, 0.7);
  border-radius: 12px;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #e2e8f0;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  position: relative;
}

.history-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.4);
}

.history-item.active {
  background: rgba(59, 130, 246, 0.25);
}

.history-text {
  flex: 1;
  font-size: 12px;
}

.history-actions {
  display: flex;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.history-item:hover .history-actions {
  opacity: 1;
}

.icon-button {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: none;
  background: rgba(148, 163, 184, 0.15);
  color: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.icon-button svg {
  width: 14px;
  height: 14px;
  fill: currentColor;
}

.icon-button.danger {
  color: #f87171;
}

.chat-panel {
  position: relative;
  background: rgba(248, 250, 252, 0.04);
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  padding: 12px;
  min-height: 0;
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 8px 120px 8px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}

.bubble-row {
  display: flex;
  gap: 12px;
}

.bubble-row.user {
  flex-direction: row-reverse;
}

.bubble-avatar {
  display: flex;
  align-items: flex-start;
}

.avatar-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: #fff;
  background: #4f46e5;
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.2);
}

.avatar-circle.assistant {
  background: #0ea5e9;
}

.avatar-circle.user {
  background: #64748b;
}

.bubble {
  max-width: 76%;
  background: rgba(255, 255, 255, 0.95);
  padding: 14px 16px;
  border-radius: 16px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
}

.bubble-role {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.bubble-content {
  font-size: 14px;
  line-height: 1.7;
  color: #0f172a;
  white-space: pre-wrap;
}

.sql-toggle {
  margin-top: 10px;
  font-size: 12px;
  color: #2563eb;
  cursor: pointer;
}

.table-section {
  margin-top: 14px;
}

.table-title {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.result-table {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.result-table :deep(.el-table__header-wrapper) {
  background: #f1f5f9;
}

.result-table :deep(.el-table__header th) {
  background: #f1f5f9;
  color: #334155;
  font-weight: 600;
  border: none;
}

.result-table :deep(.el-table__body td) {
  border: none;
  color: #475569;
}

.result-table :deep(.el-table__row) {
  background: #ffffff;
}

.result-table :deep(.el-table__row:hover) {
  background: #f8fafc;
}

.loading-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #cbd5f5;
  font-size: 13px;
  padding: 6px 8px;
}

.loading-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid #cbd5f5;
  border-top-color: #6366f1;
  animation: spin 1s linear infinite;
}

.chat-input-wrapper {
  position: sticky;
  bottom: 0;
  padding-top: 8px;
}

.chat-input-card {
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.15);
  border-radius: 16px;
  border: none;
}

.chat-input-card :deep(.el-card__body) {
  padding: 16px;
}

.chat-input-actions {
  margin-top: 10px;
  text-align: right;
}

.history-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
}

.history-mask-card {
  padding: 12px 20px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.8);
  color: #e2e8f0;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.4);
}

.sql-drawer {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.sql-drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 12px;
}

.sql-block {
  background: #0f172a;
  color: #e2e8f0;
  padding: 14px;
  border-radius: 12px;
  font-size: 12px;
  overflow-x: auto;
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.2);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 980px) {
  .chat-shell {
    grid-template-columns: 1fr;
  }

  .history-panel {
    order: 2;
  }
}
</style>
