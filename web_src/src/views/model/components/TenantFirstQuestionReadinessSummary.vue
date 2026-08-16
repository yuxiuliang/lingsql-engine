<script setup lang="ts">
import {CircleCheck, WarningFilled} from '@element-plus/icons-vue';
import type {TenantFirstQuestionReadinessView} from '@/api/tenant-readiness';

interface Props {
  readiness?: TenantFirstQuestionReadinessView;
  loading: boolean;
}

defineProps<Props>();
</script>

<template>
  <div v-if="readiness" class="readiness-summary">
    <div class="readiness-summary__heading">
      <span>首问就绪</span>
      <el-tag :type="readiness.ready ? 'success' : 'danger'" size="small">
        {{ readiness.ready ? '可以问数' : '存在阻断' }}
      </el-tag>
    </div>
    <div class="readiness-summary__checks" v-loading="loading">
      <div
        v-for="check in readiness.checks"
        :key="check.code"
        class="readiness-summary__check"
        :class="{'is-passed': check.passed}"
      >
        <el-icon><component :is="check.passed ? CircleCheck : WarningFilled"/></el-icon>
        <span>{{ check.name }}</span>
        <el-tooltip v-if="!check.passed && check.failureReason" :content="check.failureReason">
          <span class="readiness-summary__reason">查看原因</span>
        </el-tooltip>
      </div>
    </div>
  </div>
</template>

<style scoped>
.readiness-summary {
  padding: 10px 12px;
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-extra-light);
}

.readiness-summary__heading,
.readiness-summary__checks,
.readiness-summary__check {
  display: flex;
  align-items: center;
}

.readiness-summary__heading {
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.readiness-summary__checks {
  flex-wrap: wrap;
  gap: 8px 16px;
}

.readiness-summary__check {
  gap: 5px;
  color: var(--el-color-danger);
  font-size: 13px;
}

.readiness-summary__check.is-passed {
  color: var(--el-color-success);
}

.readiness-summary__reason {
  color: var(--el-text-color-secondary);
  cursor: help;
}
</style>
