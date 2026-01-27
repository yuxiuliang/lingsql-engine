<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {
  createLexiconItem,
  deleteLexiconItem,
  fetchLexiconItems,
  SemanticLexiconItem,
  updateLexiconItem
} from '@/api/config';
import {useAuthStore} from '@/store/auth';

const lexiconItems = ref<SemanticLexiconItem[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const authStore = useAuthStore();

const form = reactive<SemanticLexiconItem>({
  tenantId: '',
  term: '',
  mapping: '',
  description: '',
  status: 1
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
 * 当前租户名称。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const activeTenantName = computed(() => {
  return isSysAdmin.value ? authStore.activeTenantName : authStore.tenantName;
});

/**
 * 判断是否已选择租户。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const hasTenantSelected = computed(() => {
  return !isSysAdmin.value || !!authStore.activeTenantId;
});

/**
 * 解析表单租户展示名称。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const formTenantLabel = computed(() => {
  if (!form.tenantId) {
    return '全局';
  }
  if (form.tenantId === activeTenantId.value && activeTenantName.value) {
    return activeTenantName.value;
  }
  return form.tenantId;
});

/**
 * 判断是否允许操作记录。
 *
 * @param row 语义字典
 * @return 是否允许操作
 * @author 余炘洋
 * @date 2026-01-26
 */
const canManageRow = (row: SemanticLexiconItem) => {
  if (isSysAdmin.value) {
    return true;
  }
  return !!row.tenantId;
};

/**
 * 构建空语义字典配置。
 *
 * @return 语义字典
 * @author 余炘洋
 * @date 2026-01-23
 */
const buildEmptyLexicon = (): SemanticLexiconItem => ({
  tenantId: '',
  term: '',
  mapping: '',
  description: '',
  status: 1
});

/**
 * 加载语义字典列表。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadLexicon = async () => {
  if (!hasTenantSelected.value) {
    lexiconItems.value = [];
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    lexiconItems.value = await fetchLexiconItems(activeTenantId.value || undefined);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载语义字典失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 新增语义字典。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleCreate = () => {
  if (!hasTenantSelected.value) {
    ElMessage.warning('请先选择要管理的租户');
    return;
  }
  editingId.value = null;
  Object.assign(form, buildEmptyLexicon());
  form.tenantId = activeTenantId.value || '';
  dialogVisible.value = true;
};

/**
 * 编辑语义字典。
 *
 * @param row 语义字典
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleEdit = (row: SemanticLexiconItem) => {
  if (!canManageRow(row)) {
    ElMessage.warning('无权限编辑全局术语');
    return;
  }
  editingId.value = row.id || null;
  Object.assign(form, row);
  dialogVisible.value = true;
};

/**
 * 保存语义字典。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleSave = async () => {
  if (!form.term || !form.mapping) {
    ElMessage.warning('请填写术语和映射表达式');
    return;
  }
  if (!editingId.value && !activeTenantId.value) {
    ElMessage.warning('请先选择租户');
    return;
  }
  try {
    if (!editingId.value) {
      form.tenantId = activeTenantId.value || '';
    }
    if (editingId.value) {
      await updateLexiconItem(editingId.value, form);
      ElMessage.success('语义字典已更新');
    } else {
      await createLexiconItem(form);
      ElMessage.success('语义字典已创建');
    }
    dialogVisible.value = false;
    await loadLexicon();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  }
};

/**
 * 删除语义字典。
 *
 * @param row 语义字典
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleDelete = async (row: SemanticLexiconItem) => {
  if (!row.id) {
    return;
  }
  if (!canManageRow(row)) {
    ElMessage.warning('无权限删除全局术语');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该语义字典？', '提示', {type: 'warning'});
    await deleteLexiconItem(row.id);
    ElMessage.success('语义字典已删除');
    await loadLexicon();
  } catch (error) {
    if (!isMessageBoxCancel(error)) {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
};

/**
 * 解析状态文本。
 *
 * @param status 状态值
 * @return 文本
 * @author 余炘洋
 * @date 2026-01-23
 */
const resolveStatusText = (status?: number) => {
  return status === 1 ? '启用' : '停用';
};

/**
 * 解析状态颜色。
 *
 * @param status 状态值
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-01-23
 */
const resolveStatusType = (status?: number) => {
  return status === 1 ? 'success' : 'info';
};

/**
 * 判断是否为确认弹窗取消。
 *
 * @param error 异常对象
 * @return 是否取消
 * @author 余炘洋
 * @date 2026-01-23
 */
const isMessageBoxCancel = (error: unknown) => {
  return error === 'cancel' || error === 'close';
};

watch(activeTenantId, () => {
  dialogVisible.value = false;
  loadLexicon();
});

loadLexicon();
</script>

<template>
  <div class="lexicon-page">
    <div v-if="!hasTenantSelected" class="empty-state">
      <el-empty description="请在顶部选择要管理的租户"/>
    </div>
    <template v-else>
      <div class="toolbar">
        <el-button type="primary" @click="handleCreate">新增术语</el-button>
        <el-button @click="loadLexicon">刷新</el-button>
      </div>
      <el-table :data="lexiconItems" border v-loading="loading">
        <el-table-column label="范围" min-width="120">
          <template #default="scope">
            <el-tag :type="scope.row.tenantId ? 'success' : 'info'">
              {{ scope.row.tenantId ? '租户' : '全局' }}
            </el-tag>
            <span v-if="scope.row.tenantId" class="tenant-id">{{ scope.row.tenantId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="term" label="术语" min-width="140"/>
        <el-table-column prop="mapping" label="映射表达式" min-width="220"/>
        <el-table-column prop="description" label="描述" min-width="180"/>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="scope">
            <el-tag :type="resolveStatusType(scope.row.status)">{{ resolveStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button size="small" :disabled="!canManageRow(scope.row)" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" :disabled="!canManageRow(scope.row)" @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="dialogVisible" title="语义字典" width="560px" :close-on-click-modal="false">
        <el-form label-width="120px">
          <el-form-item label="所属租户">
            <el-tag :type="form.tenantId ? 'success' : 'info'">{{ formTenantLabel }}</el-tag>
          </el-form-item>
          <el-form-item label="术语">
            <el-input v-model="form.term" placeholder="例如：订单金额"/>
          </el-form-item>
          <el-form-item label="映射表达式">
            <el-input v-model="form.mapping" placeholder="例如：sum(order_amount)"/>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" placeholder="说明口径"/>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option :value="1" label="启用"/>
              <el-option :value="0" label="停用"/>
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.lexicon-page {
  background: #ffffff;
  padding: 16px;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.empty-state {
  padding: 24px 0;
}

.tenant-id {
  margin-left: 8px;
  color: #64748b;
  font-size: 12px;
}
</style>
