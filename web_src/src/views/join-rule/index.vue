<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {createJoinRule, deleteJoinRule, fetchJoinRules, JoinRuleItem, updateJoinRule} from '@/api/config';
import {fetchTenantSchema, SchemaTableInfo} from '@/api/admin';
import {useAuthStore} from '@/store/auth';

const joinRules = ref<JoinRuleItem[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const schemaTables = ref<SchemaTableInfo[]>([]);
const authStore = useAuthStore();

const form = reactive<JoinRuleItem>({
  tenantId: '',
  leftTable: '',
  rightTable: '',
  onExpr: '',
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
 * @param row Join 规则
 * @return 是否允许操作
 * @author 余炘洋
 * @date 2026-01-26
 */
const canManageRow = (row: JoinRuleItem) => {
  if (isSysAdmin.value) {
    return true;
  }
  return !!row.tenantId;
};

/**
 * 构建空 Join 规则。
 *
 * @return Join 规则
 * @author 余炘洋
 * @date 2026-01-23
 */
const buildEmptyJoinRule = (): JoinRuleItem => ({
  tenantId: '',
  leftTable: '',
  rightTable: '',
  onExpr: '',
  description: '',
  status: 1
});

/**
 * 加载 Join 规则列表。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadJoinRules = async () => {
  if (!hasTenantSelected.value) {
    joinRules.value = [];
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    joinRules.value = await fetchJoinRules(activeTenantId.value || undefined);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载 Join 规则失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 新增 Join 规则。
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
  Object.assign(form, buildEmptyJoinRule());
  form.tenantId = activeTenantId.value || '';
  loadSchema();
  dialogVisible.value = true;
};

/**
 * 编辑 Join 规则。
 *
 * @param row Join 规则
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleEdit = (row: JoinRuleItem) => {
  if (!canManageRow(row)) {
    ElMessage.warning('无权限编辑全局 Join 规则');
    return;
  }
  editingId.value = row.id || null;
  Object.assign(form, row);
  loadSchema();
  dialogVisible.value = true;
};

/**
 * 保存 Join 规则。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleSave = async () => {
  if (!form.leftTable || !form.rightTable || !form.onExpr) {
    ElMessage.warning('请填写左右表与 Join 条件');
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
      await updateJoinRule(editingId.value, form);
      ElMessage.success('Join 规则已更新');
    } else {
      await createJoinRule(form);
      ElMessage.success('Join 规则已创建');
    }
    dialogVisible.value = false;
    await loadJoinRules();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  }
};

/**
 * 删除 Join 规则。
 *
 * @param row Join 规则
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleDelete = async (row: JoinRuleItem) => {
  if (!row.id) {
    return;
  }
  if (!canManageRow(row)) {
    ElMessage.warning('无权限删除全局 Join 规则');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该 Join 规则？', '提示', {type: 'warning'});
    await deleteJoinRule(row.id);
    ElMessage.success('Join 规则已删除');
    await loadJoinRules();
  } catch (error) {
    if (!isMessageBoxCancel(error)) {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
};

/**
 * 加载 Schema 列表。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadSchema = async () => {
  const tenantId = activeTenantId.value;
  if (!tenantId) {
    return;
  }
  try {
    schemaTables.value = await fetchTenantSchema(tenantId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载表结构失败');
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
  schemaTables.value = [];
  loadJoinRules();
});

loadJoinRules();
</script>

<template>
  <div class="join-rule-page">
    <div v-if="!hasTenantSelected" class="empty-state">
      <el-empty description="请在顶部选择要管理的租户"/>
    </div>
    <template v-else>
      <div class="toolbar">
        <el-button type="primary" @click="handleCreate">新增规则</el-button>
        <el-button @click="loadJoinRules">刷新</el-button>
      </div>
      <el-table :data="joinRules" border v-loading="loading">
        <el-table-column label="范围" min-width="120">
          <template #default="scope">
            <el-tag :type="scope.row.tenantId ? 'success' : 'info'">
              {{ scope.row.tenantId ? '租户' : '全局' }}
            </el-tag>
            <span v-if="scope.row.tenantId" class="tenant-id">{{ scope.row.tenantId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="leftTable" label="左表" min-width="140"/>
        <el-table-column prop="rightTable" label="右表" min-width="140"/>
        <el-table-column prop="onExpr" label="Join 条件" min-width="220"/>
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

      <el-dialog v-model="dialogVisible" title="Join 规则" width="560px" :close-on-click-modal="false">
        <el-form label-width="120px">
          <el-form-item label="所属租户">
            <el-tag :type="form.tenantId ? 'success' : 'info'">{{ formTenantLabel }}</el-tag>
          </el-form-item>
          <el-form-item label="左表">
            <el-select v-model="form.leftTable" filterable style="width: 100%" placeholder="请选择左表">
              <el-option
                v-for="table in schemaTables"
                :key="table.tableName"
                :label="table.tableComment ? `${table.tableName}（${table.tableComment}）` : table.tableName"
                :value="table.tableName"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="右表">
            <el-select v-model="form.rightTable" filterable style="width: 100%" placeholder="请选择右表">
              <el-option
                v-for="table in schemaTables"
                :key="table.tableName"
                :label="table.tableComment ? `${table.tableName}（${table.tableComment}）` : table.tableName"
                :value="table.tableName"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="Join 条件">
            <el-input v-model="form.onExpr" placeholder="例如：a.id = b.a_id"/>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" placeholder="业务说明"/>
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
.join-rule-page {
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
