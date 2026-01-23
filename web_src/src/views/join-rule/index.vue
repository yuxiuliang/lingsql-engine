<script setup lang="ts">
import {ref, reactive} from 'vue';
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
  loading.value = true;
  try {
    joinRules.value = await fetchJoinRules();
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
  editingId.value = null;
  Object.assign(form, buildEmptyJoinRule());
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
  try {
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
  const tenantId = form.tenantId?.trim() || authStore.tenantId;
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

loadJoinRules();
</script>

<template>
  <div class="join-rule-page">
    <div class="toolbar">
      <el-button type="primary" @click="handleCreate">新增规则</el-button>
      <el-button @click="loadJoinRules">刷新</el-button>
    </div>
    <el-table :data="joinRules" border v-loading="loading">
      <el-table-column prop="tenantId" label="租户ID" min-width="120"/>
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
          <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="Join 规则" width="560px">
      <el-form label-width="120px">
        <el-form-item label="租户ID">
          <el-input v-model="form.tenantId" placeholder="留空表示全局"/>
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
</style>
