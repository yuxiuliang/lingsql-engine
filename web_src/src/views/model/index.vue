<script setup lang="ts">
import {reactive, ref, shallowRef} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {AiModelConfig, createModelConfig, deleteModelConfig, fetchModelConfigs, updateModelConfig} from '@/api/config';
import {disableModel, enableModel, fetchModelHealth, ModelHealthView, probeModel} from '@/api/admin';

const modelConfigs = ref<AiModelConfig[]>([]);
const modelHealthList = ref<ModelHealthView[]>([]);
const loadingConfig = ref(false);
const loadingHealth = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const probingId = shallowRef<number | null>(null);

const form = reactive<AiModelConfig>({
  providerName: '',
  modelName: '',
  modelVersion: '',
  baseUrl: '',
  apiKey: '',
  isActive: 0,
  weight: 100,
  priority: 1,
  autoRecover: 1,
  cooldownSeconds: 300
});

/**
 * 创建空模型配置。
 *
 * @return 模型配置
 * @author 余炘洋
 * @date 2026-01-23
 */
const buildEmptyModel = (): AiModelConfig => ({
  providerName: '',
  modelName: '',
  modelVersion: '',
  baseUrl: '',
  apiKey: '',
  isActive: 0,
  weight: 100,
  priority: 1,
  autoRecover: 1,
  cooldownSeconds: 300
});

/**
 * 加载模型配置列表。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadModelConfigs = async () => {
  loadingConfig.value = true;
  try {
    modelConfigs.value = await fetchModelConfigs();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载模型配置失败');
  } finally {
    loadingConfig.value = false;
  }
};

/**
 * 加载模型健康状态。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadModelHealth = async () => {
  loadingHealth.value = true;
  try {
    modelHealthList.value = await fetchModelHealth();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载模型健康失败');
  } finally {
    loadingHealth.value = false;
  }
};

/**
 * 初始化新增表单。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleCreate = () => {
  editingId.value = null;
  Object.assign(form, buildEmptyModel());
  dialogVisible.value = true;
};

/**
 * 进入编辑模式。
 *
 * @param row 模型配置
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleEdit = (row: AiModelConfig) => {
  editingId.value = row.id || null;
  Object.assign(form, row);
  form.apiKey = '';
  dialogVisible.value = true;
};

/**
 * 保存模型配置。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleSave = async () => {
  if (!form.providerName || !form.modelName || !form.baseUrl) {
    ElMessage.warning('请填写模型供应商、名称与地址');
    return;
  }
  try {
    if (editingId.value) {
      await updateModelConfig(editingId.value, form);
      ElMessage.success('模型配置已更新，请验证后再启用');
    } else {
      await createModelConfig(form);
      ElMessage.success('模型配置已创建，请验证后再启用');
    }
    dialogVisible.value = false;
    await loadModelConfigs();
    await loadModelHealth();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  }
};

/**
 * 删除模型配置。
 *
 * @param row 模型配置
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleDelete = async (row: AiModelConfig) => {
  if (!row.id) {
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该模型配置？', '提示', {type: 'warning'});
    await deleteModelConfig(row.id);
    ElMessage.success('模型配置已删除');
    await loadModelConfigs();
    await loadModelHealth();
  } catch (error) {
    if (!isMessageBoxCancel(error)) {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
};

/**
 * 启用模型。
 *
 * @param row 模型健康记录
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleEnable = async (row: ModelHealthView) => {
  try {
    await enableModel(row.id);
    ElMessage.success('模型已启用');
    await loadModelHealth();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '启用失败');
  }
};

/**
 * 禁用模型。
 *
 * @param row 模型健康记录
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleDisable = async (row: ModelHealthView) => {
  try {
    await disableModel(row.id);
    ElMessage.success('模型已禁用');
    await loadModelHealth();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '禁用失败');
  }
};

const handleProbe = async (modelId?: number) => {
  if (!modelId || probingId.value !== null) {
    return;
  }
  probingId.value = modelId;
  try {
    const result = await probeModel(modelId);
    if (result.status === 'HEALTHY') {
      ElMessage.success(`模型验证通过，耗时 ${result.costMs} ms`);
    } else {
      ElMessage.error(result.message);
    }
    await loadModelHealth();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型验证失败');
  } finally {
    probingId.value = null;
  }
};

/**
 * 解析启用状态文本。
 *
 * @param value 状态值
 * @return 文本
 * @author 余炘洋
 * @date 2026-01-23
 */
const resolveActiveText = (value?: number) => {
  return value === 1 ? '已启用' : '已停用';
};

/**
 * 解析启用状态颜色。
 *
 * @param value 状态值
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-01-23
 */
const resolveActiveType = (value?: number) => {
  return value === 1 ? 'success' : 'info';
};

/**
 * 解析熔断状态文本。
 *
 * @param fused 是否熔断
 * @return 文本
 * @author 余炘洋
 * @date 2026-01-23
 */
const resolveHealthText = (probeStatus?: string, fused?: boolean) => {
  if (fused) {
    return '已熔断';
  }
  if (probeStatus === 'FAILED') {
    return '验证失败';
  }
  if (probeStatus !== 'HEALTHY') {
    return '未验证';
  }
  return '正常';
};

/**
 * 解析熔断状态颜色。
 *
 * @param fused 是否熔断
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-01-23
 */
const resolveHealthType = (probeStatus?: string, fused?: boolean) => {
  if (fused || probeStatus === 'FAILED') {
    return 'danger';
  }
  if (probeStatus !== 'HEALTHY') {
    return 'info';
  }
  return 'success';
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

loadModelConfigs();
loadModelHealth();
</script>

<template>
  <div class="model-page">
    <el-tabs>
      <el-tab-pane label="模型配置">
        <div class="toolbar">
          <el-button type="primary" @click="handleCreate">新增模型</el-button>
          <el-button @click="loadModelConfigs">刷新</el-button>
        </div>
        <el-table :data="modelConfigs" border v-loading="loadingConfig">
          <el-table-column prop="id" label="ID" width="80"/>
          <el-table-column prop="providerName" label="供应商" min-width="120"/>
          <el-table-column prop="modelName" label="模型名称" min-width="140"/>
          <el-table-column prop="modelVersion" label="模型版本" min-width="120"/>
          <el-table-column prop="baseUrl" label="地址" min-width="220"/>
          <el-table-column prop="weight" label="权重" width="80"/>
          <el-table-column prop="priority" label="优先级" width="90"/>
          <el-table-column prop="isActive" label="状态" width="100">
            <template #default="scope">
              <el-tag :type="resolveActiveType(scope.row.isActive)">{{ resolveActiveText(scope.row.isActive) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="250">
            <template #default="scope">
              <el-button
                size="small"
                type="primary"
                plain
                :loading="probingId === scope.row.id"
                @click="handleProbe(scope.row.id)"
              >验证</el-button>
              <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="健康状态">
        <div class="toolbar">
          <el-button @click="loadModelHealth">刷新</el-button>
        </div>
        <el-table :data="modelHealthList" border v-loading="loadingHealth">
          <el-table-column prop="id" label="ID" width="80"/>
          <el-table-column prop="providerName" label="供应商" min-width="120"/>
          <el-table-column prop="modelName" label="模型名称" min-width="140"/>
          <el-table-column prop="modelVersion" label="模型版本" min-width="120"/>
          <el-table-column prop="baseUrl" label="地址" min-width="220"/>
          <el-table-column prop="weight" label="权重" width="80"/>
          <el-table-column prop="priority" label="优先级" width="90"/>
          <el-table-column prop="failureCount" label="失败次数" width="90"/>
          <el-table-column prop="lastProbeCostMs" label="验证耗时(ms)" width="110"/>
          <el-table-column prop="fused" label="熔断状态" width="100">
            <template #default="scope">
              <el-tag :type="resolveHealthType(scope.row.probeStatus, scope.row.fused)">
                {{ resolveHealthText(scope.row.probeStatus, scope.row.fused) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="isActive" label="启用状态" width="100">
            <template #default="scope">
              <el-tag :type="resolveActiveType(scope.row.isActive)">{{ resolveActiveText(scope.row.isActive) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260">
            <template #default="scope">
              <el-button
                size="small"
                type="primary"
                plain
                :loading="probingId === scope.row.id"
                @click="handleProbe(scope.row.id)"
              >验证</el-button>
              <el-button size="small" type="success" @click="handleEnable(scope.row)">启用</el-button>
              <el-button size="small" type="warning" @click="handleDisable(scope.row)">禁用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" title="模型配置" width="560px" :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item label="供应商">
          <el-input v-model="form.providerName" placeholder="例如：Qwen"/>
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="form.modelName" placeholder="例如：Qwen-7B"/>
        </el-form-item>
        <el-form-item label="模型版本">
          <el-input v-model="form.modelVersion" placeholder="例如：2026-01"/>
        </el-form-item>
        <el-form-item label="模型地址">
          <el-input v-model="form.baseUrl" placeholder="例如：http://localhost:8000"/>
        </el-form-item>
        <el-form-item label="访问密钥">
          <el-input
            v-model="form.apiKey"
            type="password"
            :placeholder="editingId && form.apiKeyConfigured ? '留空保持原 API Key' : 'API Key'"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="1" :max="1000"/>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" :max="99"/>
        </el-form-item>
        <el-form-item label="自动恢复">
          <el-select v-model="form.autoRecover" style="width: 100%">
            <el-option :value="1" label="开启"/>
            <el-option :value="0" label="关闭"/>
          </el-select>
        </el-form-item>
        <el-form-item label="冷却时间(秒)">
          <el-input-number v-model="form.cooldownSeconds" :min="60" :max="3600"/>
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
.model-page {
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
