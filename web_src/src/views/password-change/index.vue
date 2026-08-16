<script setup lang="ts">
import {reactive, shallowRef} from 'vue';
import {useRouter} from 'vue-router';
import {ElMessage, type FormInstance, type FormRules} from 'element-plus';
import {changePassword, fetchMenus, logout as logoutApi} from '@/api/auth';
import {useAuthStore} from '@/store/auth';

interface PasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const router = useRouter();
const authStore = useAuthStore();
const formRef = shallowRef<FormInstance>();
const submitting = shallowRef(false);
const form = reactive<PasswordForm>({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const passwordPattern = /^(?=.*\p{L})(?=.*\p{Nd}).{8,32}$/u;
const rules: FormRules<PasswordForm> = {
  currentPassword: [{required: true, message: '请输入当前密码', trigger: 'blur'}],
  newPassword: [
    {required: true, message: '请输入新密码', trigger: 'blur'},
    {
      validator: (_rule, value, callback) => {
        if (!passwordPattern.test(value)) {
          callback(new Error('密码需为 8-32 位且同时包含字母和数字'));
          return;
        }
        if (value === form.currentPassword) {
          callback(new Error('新密码不能与当前密码相同'));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    {required: true, message: '请再次输入新密码', trigger: 'blur'},
    {
      validator: (_rule, value, callback) => {
        callback(value === form.newPassword ? undefined : new Error('两次输入的新密码不一致'));
      },
      trigger: 'blur'
    }
  ]
};

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || submitting.value) {
    return;
  }
  submitting.value = true;
  try {
    const response = await changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    });
    authStore.applyLoginResponse(response);
    if (!authStore.isFullSession) {
      throw new Error('密码已修改，但完整会话换发失败，请重新登录');
    }
    authStore.setMenus(await fetchMenus());
    ElMessage.success('密码修改成功');
    await router.replace('/chat');
  } catch (error: any) {
    ElMessage.error(error?.message || '密码修改失败');
  } finally {
    submitting.value = false;
  }
};

const handleLogout = async () => {
  try {
    await logoutApi();
  } finally {
    authStore.logout();
    await router.replace('/login');
  }
};
</script>

<template>
  <main class="password-page">
    <section class="password-panel" aria-labelledby="password-title">
      <div class="panel-heading">
        <div class="brand-mark" aria-hidden="true">语</div>
        <div>
          <h1 id="password-title">修改初始密码</h1>
          <p>完成修改后才能进入系统。密码需为 8-32 位，并同时包含字母和数字。</p>
        </div>
      </div>

      <el-alert
        title="当前会话仅允许修改密码"
        type="warning"
        :closable="false"
        show-icon
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input
            v-model="form.currentPassword"
            type="password"
            autocomplete="current-password"
            show-password
            maxlength="128"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            autocomplete="new-password"
            show-password
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            autocomplete="new-password"
            show-password
            maxlength="32"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <div class="form-actions">
          <el-button text @click="handleLogout">退出登录</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            保存并进入系统
          </el-button>
        </div>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.password-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #f4f7fb;
}

.password-panel {
  width: min(100%, 440px);
  padding: 28px;
  border: 1px solid #dfe5ee;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
}

.panel-heading {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.brand-mark {
  flex: 0 0 38px;
  display: grid;
  place-items: center;
  height: 38px;
  border-radius: 9px;
  color: #ffffff;
  font-size: 19px;
  font-weight: 700;
  background: #2563eb;
}

h1 {
  margin: 0;
  color: #172033;
  font-size: 20px;
  line-height: 1.4;
}

p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.password-panel :deep(.el-alert) {
  margin-bottom: 18px;
}

.password-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

@media (max-width: 520px) {
  .password-page {
    align-items: start;
    padding: 16px;
  }

  .password-panel {
    margin-top: 8vh;
    padding: 22px 18px;
  }
}
</style>
