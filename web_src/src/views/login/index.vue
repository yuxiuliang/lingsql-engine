<script setup lang="ts">
import {reactive} from 'vue';
import {useRouter} from 'vue-router';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';

const router = useRouter();
const authStore = useAuthStore();

const form = reactive({
  username: '',
  tenantId: '',
  token: ''
});

/**
 * 提交登录表单并写入缓存。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleLogin = () => {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名');
    return;
  }
  if (!form.tenantId.trim()) {
    ElMessage.warning('请输入租户ID');
    return;
  }
  if (!form.token.trim()) {
    ElMessage.warning('请输入令牌');
    return;
  }
  authStore.login(form.token.trim(), form.tenantId.trim(), form.username.trim());
  ElMessage.success('登录成功');
  router.push('/chat');
};
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-title">语数管理控制台</div>
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名"/>
        </el-form-item>
        <el-form-item label="租户ID">
          <el-input v-model="form.tenantId" placeholder="请输入租户ID"/>
        </el-form-item>
        <el-form-item label="访问令牌">
          <el-input v-model="form.token" placeholder="请输入令牌" show-password/>
        </el-form-item>
        <el-button type="primary" class="login-button" @click="handleLogin">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a, #1e3a8a);
}

.login-card {
  width: 360px;
  padding: 12px 8px;
}

.login-title {
  text-align: center;
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 20px;
}

.login-button {
  width: 100%;
}
</style>
