<script setup lang="ts">
import {ref, reactive} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {
  SemanticLexiconItem,
  createLexiconItem,
  deleteLexiconItem,
  fetchLexiconItems,
  updateLexiconItem
} from '@/api/config';

const lexiconItems = ref<SemanticLexiconItem[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);

const form = reactive<SemanticLexiconItem>({
  tenantId: '',
  term: '',
  mapping: '',
  description: '',
  status: 1
});

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
  loading.value = true;
  try {
    lexiconItems.value = await fetchLexiconItems();
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
  editingId.value = null;
  Object.assign(form, buildEmptyLexicon());
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
  try {
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

loadLexicon();
</script>

<template>
  <div class="lexicon-page">
    <div class="toolbar">
      <el-button type="primary" @click="handleCreate">新增术语</el-button>
      <el-button @click="loadLexicon">刷新</el-button>
    </div>
    <el-table :data="lexiconItems" border v-loading="loading">
      <el-table-column prop="tenantId" label="租户ID" min-width="120"/>
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
          <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="语义字典" width="560px">
      <el-form label-width="120px">
        <el-form-item label="租户ID">
          <el-input v-model="form.tenantId" placeholder="留空表示全局"/>
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
  </div>
</template>

<style scoped>
.lexicon-page {
  background: #fff;
  padding: 16px;
  border-radius: 8px;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
