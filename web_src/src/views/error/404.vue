<script setup lang="ts">
import {useRouter} from 'vue-router';

const router = useRouter();

/**
 * 返回首页。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleBack = () => {
  router.push('/');
};
</script>

<template>
  <div class="error-page">
    <div class="error-title">404</div>
    <div class="error-desc">页面不存在</div>
    <el-button type="primary" @click="handleBack">返回首页</el-button>
  </div>
</template>

<style scoped>
.error-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #f8fafc;
}

.error-title {
  font-size: 48px;
  font-weight: 700;
  color: #1f2937;
}

.error-desc {
  font-size: 16px;
  color: #6b7280;
}
</style>
