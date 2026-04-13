<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {AuditLogView, fetchAuditLogs, PageResult} from '@/api/audit';

const authStore = useAuthStore();
const logs = ref<AuditLogView[]>([]);
const loading = ref(false);
const total = ref(0);
const page = ref(1);
const size = ref(10);

const searchForm = reactive({
  userId: '',
  action: '',
  dateRange: [] as [Date, Date] | []
});

/**
 * 判断是否系统管理员。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const isSysAdmin = computed(() => {
  return (authStore.roles || []).some((role) => ['SYS_ADMIN', 'SUPER_ADMIN'].includes(role));
});

/**
 * 当前选中租户ID。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const activeTenantId = computed(() => {
  return isSysAdmin.value ? authStore.activeTenantId : authStore.tenantId;
});

/**
 * 当前租户范围展示文本。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const tenantScopeLabel = computed(() => {
  if (isSysAdmin.value) {
    if (!activeTenantId.value) {
      return '全部';
    }
    return authStore.activeTenantName || activeTenantId.value;
  }
  return authStore.tenantName || authStore.tenantId || '当前租户';
});

/**
 * 格式化日期。
 *
 * @param value 日期
 * @return 格式化字符串
 * @author 余炘洋
 * @date 2026-01-26
 */
const formatDateTime = (value: Date): string => {
  const pad = (num: number) => String(num).padStart(2, '0');
  const year = value.getFullYear();
  const month = pad(value.getMonth() + 1);
  const day = pad(value.getDate());
  const hour = pad(value.getHours());
  const minute = pad(value.getMinutes());
  const second = pad(value.getSeconds());
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
};

/**
 * 获取查询时间范围。
 *
 * @return 起止时间
 * @author 余炘洋
 * @date 2026-01-26
 */
const resolveTimeRange = () => {
  if (searchForm.dateRange && searchForm.dateRange.length === 2) {
    const [start, end] = searchForm.dateRange as [Date, Date];
    return {
      startTime: formatDateTime(start),
      endTime: formatDateTime(end)
    };
  }
  return {startTime: undefined, endTime: undefined};
};

/**
 * 加载审计日志。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadLogs = async () => {
  loading.value = true;
  try {
    const {startTime, endTime} = resolveTimeRange();
    const result: PageResult<AuditLogView> = await fetchAuditLogs({
      tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
      userId: searchForm.userId ? Number(searchForm.userId) : undefined,
      action: searchForm.action || undefined,
      startTime,
      endTime,
      page: page.value,
      size: size.value
    });
    logs.value = result.list || [];
    total.value = result.total || 0;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载审计日志失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 处理分页大小变化。
 *
 * @param val 新大小
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSizeChange = (val: number) => {
  size.value = val;
  loadLogs();
};

/**
 * 处理页码变化。
 *
 * @param val 新页码
 * @author 余炘洋
 * @date 2026-01-26
 */
const handlePageChange = (val: number) => {
  page.value = val;
  loadLogs();
};

/**
 * 执行查询。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSearch = () => {
  page.value = 1;
  loadLogs();
};

watch(activeTenantId, () => {
  if (isSysAdmin.value) {
    handleSearch();
  }
});

loadLogs();
</script>

<template>
  <div class="page">
    <div class="page-title">审计日志</div>
    <div class="page-desc">检索管理操作日志。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <div class="tenant-scope">
            当前范围：
            <el-tag size="small" :type="activeTenantId ? 'success' : 'info'">{{ tenantScopeLabel }}</el-tag>
          </div>
          <el-input v-model="searchForm.userId" placeholder="用户ID" style="width: 120px"/>
          <el-input v-model="searchForm.action" placeholder="动作关键词" style="width: 160px"/>
          <el-date-picker
            v-model="searchForm.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="loadLogs">刷新</el-button>
        </div>
      </div>

      <el-table :data="logs" border v-loading="loading">
        <el-table-column prop="createTime" label="时间" min-width="160"/>
        <el-table-column prop="tenantId" label="租户" min-width="120"/>
        <el-table-column prop="username" label="账号" min-width="120"/>
        <el-table-column prop="action" label="动作" min-width="140"/>
        <el-table-column prop="resource" label="资源" min-width="120"/>
        <el-table-column prop="summary" label="摘要" min-width="260" show-overflow-tooltip/>
        <el-table-column prop="success" label="结果" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.success === 1 ? 'success' : 'danger'">
              {{ scope.row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="200"/>
        <el-table-column prop="detail" label="详情" min-width="320" show-overflow-tooltip/>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  padding: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 6px;
}

.page-desc {
  color: #64748b;
  margin-bottom: 16px;
}

.card {
  border-radius: 12px;
}

.toolbar {
  margin-bottom: 16px;
}

.filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.tenant-scope {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
