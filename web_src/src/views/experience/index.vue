<script setup lang="ts">
import {reactive, ref} from 'vue';
import {ElMessage} from 'element-plus';
import {ExperiencePolicyRequest, ExperiencePolicyView, fetchExperiencePolicy, saveExperiencePolicy} from '@/api/rbac';

const loading = ref(false);
const userIdInput = ref('');
const form = reactive({
  menuWhitelistText: '',
  scopeType: 'TENANT',
  scopeColumn: '',
  scopeRule: '',
  enabled: true,
  status: 1
});

/**
 * 解析菜单白名单。
 *
 * @param raw 原始输入
 * @return 菜单列表
 * @author 余炘洋
 * @date 2026-01-26
 */
const parseMenuWhitelist = (raw: string): string[] => {
  return raw
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
};

/**
 * 加载体验账号策略。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleLoad = async () => {
  const userId = Number(userIdInput.value);
  if (!userId) {
    ElMessage.warning('请输入有效的用户ID');
    return;
  }
  loading.value = true;
  try {
    const data = await fetchExperiencePolicy(userId);
    applyView(data);
    ElMessage.success('已加载体验账号策略');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 保存体验账号策略。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSave = async () => {
  const userId = Number(userIdInput.value);
  if (!userId) {
    ElMessage.warning('请输入有效的用户ID');
    return;
  }
  const payload: ExperiencePolicyRequest = {
    menuWhitelist: parseMenuWhitelist(form.menuWhitelistText),
    dataScopeOverride: {
      scopeType: form.scopeType || undefined,
      scopeColumn: form.scopeColumn || undefined,
      scopeRule: form.scopeRule || undefined
    },
    enabled: form.enabled
  };
  loading.value = true;
  try {
    const data = await saveExperiencePolicy(userId, payload);
    applyView(data);
    ElMessage.success('策略已保存');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 应用策略视图。
 *
 * @param view 视图数据
 * @author 余炘洋
 * @date 2026-01-26
 */
const applyView = (view: ExperiencePolicyView) => {
  form.menuWhitelistText = (view.menuWhitelist || []).join(',');
  form.scopeType = view.dataScopeOverride?.scopeType || 'TENANT';
  form.scopeColumn = view.dataScopeOverride?.scopeColumn || '';
  form.scopeRule = view.dataScopeOverride?.scopeRule || '';
  form.status = view.status ?? 1;
  form.enabled = view.status == null ? true : view.status === 1;
};
</script>

<template>
  <div class="page">
    <div class="page-title">体验账号</div>
    <div class="page-desc">配置体验账号菜单白名单与数据权限覆盖。</div>

    <el-card class="card" shadow="never">
      <div class="form-row">
        <el-input v-model="userIdInput" placeholder="请输入用户ID" style="max-width: 220px"/>
        <el-button type="primary" :loading="loading" @click="handleLoad">加载策略</el-button>
      </div>

      <el-form label-width="110px" class="form">
        <el-form-item label="菜单白名单">
          <el-input
            v-model="form.menuWhitelistText"
            placeholder="多个菜单编码用逗号分隔"
          />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.scopeType" style="width: 100%">
            <el-option label="租户" value="TENANT"/>
            <el-option label="全部" value="ALL"/>
            <el-option label="仅自己" value="SELF"/>
            <el-option label="自定义" value="CUSTOM"/>
          </el-select>
        </el-form-item>
        <el-form-item label="范围字段">
          <el-input v-model="form.scopeColumn" placeholder="SELF 时可填写字段名"/>
        </el-form-item>
        <el-form-item label="自定义规则">
          <el-input v-model="form.scopeRule" placeholder="CUSTOM 时填写 SQL 片段"/>
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用"/>
        </el-form-item>
      </el-form>

      <div class="actions">
        <el-button type="primary" :loading="loading" @click="handleSave">保存策略</el-button>
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

.form-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.form {
  margin-top: 12px;
}

.actions {
  display: flex;
  justify-content: flex-end;
}
</style>
