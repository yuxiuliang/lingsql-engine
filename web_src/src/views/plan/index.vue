<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {ElMessage} from 'element-plus';
import {fetchTenants, TenantView} from '@/api/tenant';

const loading = ref(false);
const tenants = ref<TenantView[]>([]);
const keyword = ref('');

/**
 * 加载租户套餐数据。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const loadTenants = async () => {
  loading.value = true;
  try {
    tenants.value = await fetchTenants();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载套餐数据失败');
  } finally {
    loading.value = false;
  }
};

const filteredTenants = computed(() => {
  const key = keyword.value.trim().toLowerCase();
  if (!key) {
    return tenants.value;
  }
  return tenants.value.filter((item) => {
    return [item.tenantName, item.tenantId, item.planName, item.serviceLevel]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(key));
  });
});

const planSummary = computed(() => {
  const summaryMap = new Map<string, {planName: string; tenantCount: number; activeCount: number; totalBalance: number}>();
  tenants.value.forEach((tenant) => {
    const planName = tenant.planName?.trim() || '未设置';
    const entry = summaryMap.get(planName) || {
      planName,
      tenantCount: 0,
      activeCount: 0,
      totalBalance: 0
    };
    entry.tenantCount += 1;
    if (tenant.status === 1) {
      entry.activeCount += 1;
    }
    entry.totalBalance += tenant.quotaBalance || 0;
    summaryMap.set(planName, entry);
  });
  return Array.from(summaryMap.values()).sort((a, b) => b.tenantCount - a.tenantCount);
});

const totalTenantCount = computed(() => tenants.value.length);
const activeTenantCount = computed(() => tenants.value.filter((tenant) => tenant.status === 1).length);
const planTypeCount = computed(() => planSummary.value.length);
const totalBalance = computed(() => tenants.value.reduce((sum, item) => sum + (item.quotaBalance || 0), 0));

/**
 * 解析到期状态。
 *
 * @param expireTime 到期时间
 * @return 状态
 * @author 余炘洋
 * @date 2026-01-27
 */
const resolveExpireState = (expireTime?: string) => {
  if (!expireTime) {
    return 'unknown';
  }
  const parsed = new Date(expireTime);
  if (Number.isNaN(parsed.getTime())) {
    return 'unknown';
  }
  const now = new Date();
  const diffMs = parsed.getTime() - now.getTime();
  if (diffMs < 0) {
    return 'expired';
  }
  const diffDays = diffMs / (1000 * 60 * 60 * 24);
  return diffDays <= 7 ? 'soon' : 'normal';
};

onMounted(loadTenants);
</script>

<template>
  <div class="page">
    <div class="page-title">套餐管理</div>
    <div class="page-desc">查看租户套餐分布、服务等级与余额概览。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索租户/套餐/等级" style="width: 240px"/>
        <el-button @click="loadTenants">刷新</el-button>
      </div>
      <div class="summary-grid">
        <div class="summary-card">
          <div class="summary-label">租户总数</div>
          <div class="summary-value">{{ totalTenantCount }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">启用租户</div>
          <div class="summary-value">{{ activeTenantCount }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">套餐类型数</div>
          <div class="summary-value">{{ planTypeCount }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">余额合计</div>
          <div class="summary-value">{{ totalBalance }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="section-title">套餐分布</div>
      <el-table :data="planSummary" border v-loading="loading">
        <el-table-column prop="planName" label="套餐" min-width="160"/>
        <el-table-column prop="tenantCount" label="租户数" min-width="120"/>
        <el-table-column prop="activeCount" label="启用租户" min-width="120"/>
        <el-table-column prop="totalBalance" label="余额合计" min-width="120"/>
      </el-table>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="section-title">租户清单</div>
      <el-table :data="filteredTenants" border v-loading="loading">
        <el-table-column prop="tenantName" label="租户名称" min-width="160"/>
        <el-table-column prop="tenantId" label="租户ID" min-width="160"/>
        <el-table-column prop="planName" label="套餐" min-width="120"/>
        <el-table-column prop="serviceLevel" label="服务等级" min-width="120"/>
        <el-table-column prop="quotaBalance" label="余额" min-width="100"/>
        <el-table-column prop="expireTime" label="到期时间" min-width="160">
          <template #default="scope">
            <span v-if="!scope.row.expireTime">-</span>
            <el-tag v-else-if="resolveExpireState(scope.row.expireTime) !== 'normal'" type="danger">
              {{ scope.row.expireTime }}
            </el-tag>
            <span v-else>{{ scope.row.expireTime }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
              {{ scope.row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
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
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}

.summary-card {
  background: #f8fafc;
  border-radius: 12px;
  padding: 16px;
}

.summary-label {
  color: #64748b;
  font-size: 13px;
}

.summary-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 6px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
</style>
