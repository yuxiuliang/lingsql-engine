<script setup lang="ts">
import {nextTick, ref, watch} from 'vue';
import {ElMessage} from 'element-plus';
import {askQuestion, ChatResult} from '@/api/chat';

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

const question = ref('');
const loading = ref(false);
const messages = ref<ChatMessage[]>([]);
const messageSeed = ref(1);
const drawerVisible = ref(false);
const drawerSql = ref('');
const messageContainerRef = ref<HTMLDivElement | null>(null);

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
  return !!sql && sql !== 'ERROR:NO_SCHEMA_MATCH';
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
    messages.value.push({
      id: nextMessageId(),
      role: 'assistant',
      content: result.answer,
      sql: result.sql,
      data: result.data || []
    });
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

watch(
  () => messages.value.length,
  () => {
    scrollToBottom();
  }
);
</script>

<template>
  <div class="chat-page">
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

    <el-drawer v-model="drawerVisible" direction="rtl" size="36%" :with-header="false">
      <div class="sql-drawer">
        <div class="sql-drawer-title">执行 SQL</div>
        <pre class="sql-block">{{ drawerSql }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.chat-page {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  background: #eef2f7;
  border-radius: 16px;
  padding: 14px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 8px 130px 8px;
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  width: 38px;
  height: 38px;
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
  background: #fff;
  padding: 14px 16px;
  border-radius: 16px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
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
  color: #64748b;
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
  background: #fff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
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

.sql-drawer {
  height: 100%;
  display: flex;
  flex-direction: column;
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
</style>
