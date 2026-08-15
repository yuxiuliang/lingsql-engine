<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {
  askQuestion,
  ChatHistoryDetail,
  ChatHistoryItem,
  ChatHistoryMessage,
  ChatResult,
  ChatTraceStep,
  deleteChatHistory,
  fetchChatHistory,
  fetchChatHistoryDetail,
  renameChatHistory
} from '@/api/chat';
import {fetchChatRuntimeConfig} from '@/api/config';
import {useChatStore} from '@/store/chat';
import ResultInsightPanel from '@/views/chat/ResultInsightPanel.vue';

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
  trace?: ChatTraceStep[];
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
const activeConversationId = ref<string | null>(null);
const currentConversationId = ref<string | null>(null);
const historyKeyword = ref(chatStore.keyword || '');
const historyPage = ref(chatStore.page || 1);
const historySize = ref(chatStore.size || 10);
const isNarrowScreen = ref(false);
const historyRequestSeq = ref(0);
const expandedTraceMessageIds = ref<number[]>([]);

/**
 * 问题最大长度。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const resolveMaxQuestionLength = () => {
  const rawValue = Number(import.meta.env.VITE_CHAT_MAX_QUESTION_LENGTH);
  if (Number.isFinite(rawValue) && rawValue > 0) {
    return Math.floor(rawValue);
  }
  return 2000;
};
const maxQuestionLength = ref(resolveMaxQuestionLength());

/**
 * 问题输入提示文案。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const questionPlaceholder = computed(() => {
  return `请输入你的问题，回车发送，Shift+Enter 换行（最多${maxQuestionLength.value}字）`;
});

/**
 * 加载聊天运行时配置。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadChatRuntimeConfig = async () => {
  try {
    const config = await fetchChatRuntimeConfig();
    if (config?.maxQuestionLength && config.maxQuestionLength > 0) {
      maxQuestionLength.value = config.maxQuestionLength;
    }
  } catch (error) {
    ElMessage.warning('加载输入限制失败，已使用默认配置');
  }
};

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
 * 将历史详情转换为前端消息列表。
 *
 * @param detail 历史详情
 * @return 消息列表
 * @author 余炘洋
 * @date 2026-04-10
 */
const buildMessagesFromDetail = (detail: ChatHistoryDetail): ChatMessage[] => {
  const historyMessages = detail.messages || [];
  if (historyMessages.length > 0) {
    return historyMessages.map((message: ChatHistoryMessage) => ({
      id: nextMessageId(),
      role: message.role,
      content: message.content,
      sql: message.sql,
      data: message.data || [],
      trace: message.trace || []
    }));
  }
  return [
    {id: nextMessageId(), role: 'user', content: detail.question},
    {
      id: nextMessageId(),
      role: 'assistant',
      content: detail.answer,
      sql: detail.sql,
      data: detail.data || [],
      trace: detail.trace || []
    }
  ];
};

/**
 * 默认展开最后一条带执行轨迹的助手消息。
 *
 * @author 余炘洋
 * @date 2026-04-10
 */
const expandLatestTraceMessage = () => {
  const target = [...messages.value]
    .reverse()
    .find((message) => message.role === 'assistant' && shouldShowTrace(message.trace));
  expandedTraceMessageIds.value = target ? [target.id] : [];
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
 * 判断是否存在执行轨迹。
 *
 * @param trace 执行轨迹
 * @return 是否存在
 * @author 余炘洋
 * @date 2026-04-07
 */
const shouldShowTrace = (trace?: ChatTraceStep[]) => {
  return !!trace && trace.length > 0;
};

/**
 * 判断是否触发自愈重试。
 *
 * @param trace 执行轨迹
 * @return 是否触发
 * @author 余炘洋
 * @date 2026-04-07
 */
const hasSelfHealing = (trace?: ChatTraceStep[]) => {
  return (trace || []).some((step) => (step.attempt || 1) > 1);
};

/**
 * 判断轨迹是否展开。
 *
 * @param messageId 消息ID
 * @return 是否展开
 * @author 余炘洋
 * @date 2026-04-07
 */
const isTraceExpanded = (messageId: number) => {
  return expandedTraceMessageIds.value.includes(messageId);
};

/**
 * 展开执行轨迹。
 *
 * @param messageId 消息ID
 * @author 余炘洋
 * @date 2026-04-07
 */
const expandTrace = (messageId: number) => {
  if (isTraceExpanded(messageId)) {
    return;
  }
  expandedTraceMessageIds.value = [...expandedTraceMessageIds.value, messageId];
};

/**
 * 切换执行轨迹面板。
 *
 * @param messageId 消息ID
 * @author 余炘洋
 * @date 2026-04-07
 */
const toggleTrace = (messageId: number) => {
  if (isTraceExpanded(messageId)) {
    expandedTraceMessageIds.value = expandedTraceMessageIds.value.filter((id) => id !== messageId);
    return;
  }
  expandTrace(messageId);
};

/**
 * 获取最终轨迹步骤。
 *
 * @param trace 执行轨迹
 * @return 最终步骤
 * @author 余炘洋
 * @date 2026-04-07
 */
const resolveFinalTraceStep = (trace?: ChatTraceStep[]) => {
  return [...(trace || [])].reverse().find((step) => step.type === 'FINAL');
};

/**
 * 解析轨迹摘要。
 *
 * @param trace 执行轨迹
 * @return 摘要文本
 * @author 余炘洋
 * @date 2026-04-07
 */
const resolveTraceSummary = (trace?: ChatTraceStep[]) => {
  if (!shouldShowTrace(trace)) {
    return '';
  }
  if (hasSelfHealing(trace)) {
    const finalStep = resolveFinalTraceStep(trace);
    return finalStep?.status === 'SUCCESS' ? '已自愈' : '已重试';
  }
  return '首轮完成';
};

/**
 * 解析轨迹面板副标题。
 *
 * @param trace 执行轨迹
 * @return 副标题
 * @author 余炘洋
 * @date 2026-04-07
 */
const resolveTraceCaption = (trace?: ChatTraceStep[]) => {
  if (!shouldShowTrace(trace)) {
    return '';
  }
  return hasSelfHealing(trace) ? '包含一次自动纠错流程' : '首轮链路已直接完成';
};

/**
 * 解析步骤类型标签。
 *
 * @param type 步骤类型
 * @return 标签
 * @author 余炘洋
 * @date 2026-04-07
 */
const resolveTraceTypeLabel = (type?: string) => {
  if (type === 'THOUGHT') {
    return '思考';
  }
  if (type === 'ACTION') {
    return '行动';
  }
  if (type === 'OBSERVATION') {
    return '观察';
  }
  if (type === 'FINAL') {
    return '结果';
  }
  return '轨迹';
};

/**
 * 解析步骤状态标签。
 *
 * @param status 状态
 * @return 标签
 * @author 余炘洋
 * @date 2026-04-07
 */
const resolveTraceStatusLabel = (status?: string) => {
  if (status === 'SUCCESS') {
    return '完成';
  }
  if (status === 'FAILURE') {
    return '异常';
  }
  return '处理中';
};

/**
 * 解析步骤状态样式类。
 *
 * @param status 状态
 * @return 样式类
 * @author 余炘洋
 * @date 2026-04-07
 */
const resolveTraceStatusClass = (status?: string) => {
  if (status === 'FAILURE') {
    return 'failure';
  }
  if (status === 'SUCCESS') {
    return 'success';
  }
  return 'info';
};

/**
 * 解析步骤类型样式类。
 *
 * @param type 类型
 * @return 样式类
 * @author 余炘洋
 * @date 2026-04-07
 */
const resolveTraceTypeClass = (type?: string) => {
  return (type || 'TRACE').toLowerCase();
};

/**
 * 解析轮次标签。
 *
 * @param attempt 轮次
 * @return 标签
 * @author 余炘洋
 * @date 2026-04-07
 */
const resolveAttemptLabel = (attempt?: number) => {
  if (!attempt || attempt <= 1) {
    return '首轮';
  }
  return `纠错第${attempt}轮`;
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
  historyRequestSeq.value += 1;
  historyLoading.value = false;
  messages.value = [];
  expandedTraceMessageIds.value = [];
  question.value = '';
  activeConversationId.value = null;
  currentConversationId.value = null;
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
  const requestSeq = historyRequestSeq.value + 1;
  historyRequestSeq.value = requestSeq;
  historyLoading.value = true;
  activeConversationId.value = item.conversationId;
  try {
    const detail: ChatHistoryDetail = await fetchChatHistoryDetail(item.id);
    if (requestSeq !== historyRequestSeq.value || activeConversationId.value !== item.conversationId) {
      return;
    }
    currentConversationId.value = detail.conversationId || item.conversationId;
    messages.value = buildMessagesFromDetail(detail);
    expandLatestTraceMessage();
    await scrollToBottom();
  } catch (error) {
    if (requestSeq === historyRequestSeq.value) {
      ElMessage.error(error instanceof Error ? error.message : '加载历史详情失败');
    }
  } finally {
    if (requestSeq === historyRequestSeq.value) {
      historyLoading.value = false;
    }
  }
};

/**
 * 发送问题并追加对话结果。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleSend = async () => {
  if (loading.value) {
    return;
  }
  const content = question.value.trim();
  if (!content) {
    ElMessage.warning('请输入问题');
    return;
  }
  if (content.length > maxQuestionLength.value) {
    ElMessage.warning(`问题长度不能超过${maxQuestionLength.value}字`);
    return;
  }
  messages.value.push({id: nextMessageId(), role: 'user', content});
  question.value = '';
  loading.value = true;
  await scrollToBottom();
  try {
    const result: ChatResult = await askQuestion({
      question: content,
      conversationId: currentConversationId.value || undefined
    });
    const assistantMessage: ChatMessage = {
      id: nextMessageId(),
      role: 'assistant',
      content: result.answer,
      sql: result.sql,
      data: result.data || [],
      trace: result.trace || []
    };
    messages.value.push(assistantMessage);
    currentConversationId.value = result.conversationId || currentConversationId.value;
    activeConversationId.value = currentConversationId.value;
    if (shouldShowTrace(assistantMessage.trace)) {
      expandTrace(assistantMessage.id);
    }
    await loadHistory();
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
    }) as { value: string };
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
    if (activeConversationId.value === item.conversationId) {
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

/**
 * 历史记录项键盘交互处理。
 *
 * @param event 键盘事件
 * @param item 历史记录
 * @author 余炘洋
 * @date 2026-02-28
 */
const handleHistoryItemKeydown = (event: KeyboardEvent, item: ChatHistoryItem) => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    loadHistoryDetail(item);
  }
};

/**
 * 同步视口状态。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
const syncViewport = () => {
  isNarrowScreen.value = window.innerWidth <= 980;
};

const drawerSize = computed(() => (isNarrowScreen.value ? '90%' : '36%'));

watch(
  () => messages.value.length,
  () => {
    scrollToBottom();
  }
);

onMounted(() => {
  loadHistory();
  loadChatRuntimeConfig();
  syncViewport();
  window.addEventListener('resize', syncViewport);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport);
});
</script>

<template>
  <div class="chat-shell">
    <aside class="history-panel">
      <div class="history-header">
        <div>
          <div class="history-title">最近会话</div>
          <div class="history-subtitle">按对话归档</div>
        </div>
        <el-button class="new-chat-button" @click="startNewChat">+ 开启新对话</el-button>
      </div>
      <div class="history-search">
        <el-input
          v-model="historyKeyword"
          size="small"
          clearable
          placeholder="搜索会话标题"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button size="small" class="search-button" @click="handleSearch">搜索</el-button>
      </div>
      <div class="history-list">
        <div
          v-for="item in chatStore.historyList"
          :key="item.conversationId"
          class="history-item"
          :class="{active: item.conversationId === activeConversationId}"
          role="button"
          tabindex="0"
          :aria-current="item.conversationId === activeConversationId ? 'true' : 'false'"
          :aria-label="`打开历史记录：${truncateQuestion(item.question)}`"
          @click="loadHistoryDetail(item)"
          @keydown="handleHistoryItemKeydown($event, item)"
        >
          <div class="history-text">{{ truncateQuestion(item.question) }}</div>
          <el-tag v-if="isErrorSql(item.sql)" size="small" type="danger" effect="dark">异常</el-tag>
          <div class="history-actions">
            <button class="icon-button" type="button" @click.stop="handleRename(item)" title="重命名" aria-label="重命名历史记录">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M4 17.25V20h2.75L19.81 6.94l-2.75-2.75L4 17.25zm15.71-9.04a1 1 0 0 0 0-1.41l-2.5-2.5a1 1 0 0 0-1.41 0l-1.83 1.83 3.91 3.91 1.83-1.83z"
                />
              </svg>
            </button>
            <button class="icon-button danger" type="button" @click.stop="handleDelete(item)" title="删除" aria-label="删除历史记录">
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

            <div
              v-if="message.role === 'assistant' && (shouldShowSql(message.sql) || shouldShowTrace(message.trace))"
              class="assistant-actions"
            >
              <button v-if="shouldShowSql(message.sql)" type="button" class="sql-toggle" @click="openSqlDrawer(message.sql)">
                查看执行 SQL
              </button>
              <button v-if="shouldShowTrace(message.trace)" type="button" class="trace-toggle" @click="toggleTrace(message.id)">
                {{ isTraceExpanded(message.id) ? '收起执行轨迹' : '查看执行轨迹' }}
              </button>
              <span v-if="shouldShowTrace(message.trace)" class="trace-chip" :class="resolveTraceStatusClass(resolveFinalTraceStep(message.trace)?.status)">
                {{ resolveTraceSummary(message.trace) }}
              </span>
            </div>

            <div
              v-if="message.role === 'assistant' && shouldShowTrace(message.trace) && isTraceExpanded(message.id)"
              class="trace-section"
            >
              <div class="trace-panel-header">
                <div>
                  <div class="trace-panel-title">智能链路</div>
                  <div class="trace-panel-caption">{{ resolveTraceCaption(message.trace) }}</div>
                </div>
              </div>
              <div class="trace-timeline">
                <div
                  v-for="(step, index) in message.trace"
                  :key="`${message.id}-${index}-${step.title}`"
                  class="trace-step"
                  :class="[resolveTraceStatusClass(step.status), resolveTraceTypeClass(step.type)]"
                >
                  <div class="trace-marker"></div>
                  <div class="trace-card">
                    <div class="trace-card-header">
                      <span class="trace-type">{{ resolveTraceTypeLabel(step.type) }}</span>
                      <span class="trace-title">{{ step.title }}</span>
                      <span class="trace-attempt">{{ resolveAttemptLabel(step.attempt) }}</span>
                      <span class="trace-status" :class="resolveTraceStatusClass(step.status)">
                        {{ resolveTraceStatusLabel(step.status) }}
                      </span>
                    </div>
                    <div class="trace-content">{{ step.content }}</div>
                  </div>
                </div>
              </div>
            </div>

            <template v-if="message.role === 'assistant' && message.data && message.data.length">
              <div class="table-section">
                <ResultInsightPanel :rows="message.data"/>
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
            :autosize="{minRows: 6, maxRows: 10}"
            :maxlength="maxQuestionLength"
            show-word-limit
            :placeholder="questionPlaceholder"
            @keydown.enter.exact.prevent="handleSend"
          />
          <div class="chat-input-actions">
            <el-button type="primary" :loading="loading" :disabled="loading || !question.trim()" @click="handleSend">
              发送
            </el-button>
          </div>
        </el-card>
      </div>

      <div v-if="historyLoading" class="history-mask">
        <div class="history-mask-card">正在加载历史记录...</div>
      </div>

      <el-drawer v-model="drawerVisible" direction="rtl" :size="drawerSize" :with-header="false">
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
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 16px;
  overflow: hidden;
}

.history-panel {
  background: #ffffff;
  border-radius: 16px;
  padding: 14px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
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
  color: #1f2937;
}

.history-subtitle {
  font-size: 12px;
  color: #64748b;
}

.new-chat-button {
  width: 100%;
  border: none;
  background: #409EFF;
  color: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.history-search {
  display: flex;
  gap: 6px;
}

.history-search :deep(.el-input__wrapper) {
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  box-shadow: none;
}

.history-search :deep(.el-input__inner) {
  color: #1f2937;
}

.search-button {
  border: none;
  background: #e2e8f0;
  color: #334155;
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
  background: #f1f5f9;
  border: none;
  color: #475569;
}

.history-pagination :deep(.el-pager li.is-active) {
  background: #409EFF;
  color: #fff;
}

.history-item {
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid transparent;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #334155;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  position: relative;
}

.history-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.history-item:focus-visible {
  border-color: #3b82f6;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.22);
}

.history-item.active {
  background: rgba(64, 158, 255, 0.12);
  border-color: rgba(37, 99, 235, 0.32);
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

.history-item:focus-within .history-actions {
  opacity: 1;
}

.icon-button {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: none;
  background: #e2e8f0;
  color: #475569;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease;
}

.icon-button:hover {
  background: #cbd5e1;
}

.icon-button:focus-visible {
  background: #dbeafe;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.3);
  transform: translateY(-1px);
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
  background: #ffffff;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  padding: 12px;
  min-height: 0;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
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
  background: #409EFF;
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
  color: #64748b;
  margin-bottom: 6px;
}

.bubble-content {
  font-size: 14px;
  line-height: 1.7;
  color: #1f2937;
  white-space: pre-wrap;
}

.sql-toggle {
  appearance: none;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12px;
  color: #2563eb;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.assistant-actions {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.sql-toggle,
.trace-toggle {
  appearance: none;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12px;
  color: #2563eb;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.sql-toggle:focus-visible,
.trace-toggle:focus-visible {
  border-radius: 6px;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.25);
}

.trace-chip {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  background: #e0f2fe;
  color: #0369a1;
}

.trace-chip.success {
  background: #dcfce7;
  color: #166534;
}

.trace-chip.failure {
  background: #fee2e2;
  color: #b91c1c;
}

.trace-section {
  margin-top: 14px;
  padding: 14px;
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgba(14, 165, 233, 0.12) 0%, rgba(59, 130, 246, 0.06) 48%, rgba(255, 255, 255, 0.92) 100%);
  border: 1px solid rgba(125, 211, 252, 0.55);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.45);
}

.trace-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.trace-panel-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.trace-panel-caption {
  margin-top: 4px;
  font-size: 12px;
  color: #475569;
}

.trace-timeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trace-step {
  display: grid;
  grid-template-columns: 14px 1fr;
  gap: 10px;
  align-items: start;
}

.trace-marker {
  width: 10px;
  height: 10px;
  margin-top: 12px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 0 4px rgba(125, 211, 252, 0.22);
}

.trace-step.failure .trace-marker {
  background: #f87171;
  box-shadow: 0 0 0 4px rgba(248, 113, 113, 0.18);
}

.trace-step.final .trace-marker {
  background: #22c55e;
}

.trace-card {
  position: relative;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(203, 213, 225, 0.72);
  box-shadow: 0 8px 20px rgba(148, 163, 184, 0.14);
}

.trace-card::before {
  content: '';
  position: absolute;
  left: -18px;
  top: 16px;
  width: 2px;
  height: calc(100% + 12px);
  background: linear-gradient(180deg, rgba(125, 211, 252, 0.72), rgba(148, 163, 184, 0));
}

.trace-step:last-child .trace-card::before {
  display: none;
}

.trace-card-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.trace-type {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 42px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.trace-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.trace-attempt {
  font-size: 11px;
  color: #64748b;
}

.trace-status {
  margin-left: auto;
  font-size: 11px;
  font-weight: 700;
  color: #0284c7;
}

.trace-status.success {
  color: #15803d;
}

.trace-status.failure {
  color: #dc2626;
}

.trace-content {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: #334155;
  white-space: pre-wrap;
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
  border: 2px solid #cbd5e1;
  border-top-color: #409EFF;
  animation: spin 1s linear infinite;
}

.chat-input-wrapper {
  position: sticky;
  bottom: 0;
  padding-top: 8px;
}

.chat-input-card {
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
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
  background: rgba(15, 23, 42, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
}

.history-mask-card {
  padding: 12px 20px;
  border-radius: 12px;
  background: #ffffff;
  color: #1f2937;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
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
  background: #111827;
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
    padding: 10px;
  }

  .history-panel {
    order: 2;
    max-height: 42vh;
  }

  .chat-panel {
    order: 1;
  }

  .history-actions {
    opacity: 1;
  }

  .bubble {
    max-width: 88%;
  }

  .chat-input-card :deep(.el-card__body) {
    padding: 12px;
  }
}

@media (max-width: 640px) {
  .chat-shell {
    gap: 10px;
  }

  .history-panel,
  .chat-panel {
    border-radius: 12px;
    padding: 10px;
  }
}
</style>
