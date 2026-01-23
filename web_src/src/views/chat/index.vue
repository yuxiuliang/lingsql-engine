<script setup lang="ts">
import {ref} from 'vue';
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
  const first = rows[0];
  return Object.keys(first);
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
  }
};
</script>

<template>
  <div class="chat-page">
    <el-card class="chat-input-card">
      <div class="chat-input-title">请描述你的问题</div>
      <el-input
        v-model="question"
        type="textarea"
        :rows="3"
        placeholder="例如：近30天订单总量与增长率"
        @keyup.enter="handleSend"
      />
      <div class="chat-input-actions">
        <el-button type="primary" :loading="loading" @click="handleSend">发送</el-button>
      </div>
    </el-card>

    <div class="chat-messages">
      <el-card v-for="message in messages" :key="message.id" class="message-card">
        <div class="message-role" :class="message.role">{{ message.role === 'user' ? '用户' : '语数' }}</div>
        <div class="message-content">{{ message.content }}</div>
        <template v-if="message.role === 'assistant' && message.sql">
          <div class="message-section">
            <div class="section-title">生成SQL</div>
            <pre class="sql-block">{{ message.sql }}</pre>
          </div>
        </template>
        <template v-if="message.role === 'assistant' && message.data && message.data.length">
          <div class="message-section">
            <div class="section-title">查询结果</div>
            <el-table :data="message.data" size="small" border>
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
      </el-card>
      <el-empty v-if="!messages.length && !loading" description="暂无对话记录"/>
      <div v-if="loading" class="loading-tip">语数正在思考中...</div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-input-card {
  padding-bottom: 12px;
}

.chat-input-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}

.chat-input-actions {
  margin-top: 12px;
  text-align: right;
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-card {
  background: #fff;
}

.message-role {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-block;
  margin-bottom: 8px;
}

.message-role.user {
  background: #e0f2fe;
  color: #0369a1;
}

.message-role.assistant {
  background: #ecfccb;
  color: #365314;
}

.message-content {
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 8px;
}

.message-section {
  margin-top: 12px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.sql-block {
  background: #0f172a;
  color: #e2e8f0;
  padding: 10px 12px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 12px;
}

.loading-tip {
  text-align: center;
  color: #6b7280;
}
</style>
