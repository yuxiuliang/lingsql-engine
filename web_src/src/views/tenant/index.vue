<script setup lang="ts">
import {computed, nextTick, ref, reactive} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {
  TenantDbConfig,
  createTenantConfig,
  deleteTenantConfig,
  fetchTenantConfigs,
  testTenantConnection,
  updateTenantConfig
} from '@/api/config';
import {fetchTenantSchema, refreshTenantSemantic, SchemaTableInfo, syncTenantSchema} from '@/api/admin';

const tenantConfigs = ref<TenantDbConfig[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const schemaTables = ref<SchemaTableInfo[]>([]);
const schemaTenantId = ref('');
const tableSelectVisible = ref(false);
const columnSelectVisible = ref(false);
const columnSelectMode = ref<'whitelist' | 'sensitive'>('whitelist');
const selectedTables = ref<string[]>([]);
const selectedColumns = ref<string[]>([]);
const tableTreeRef = ref<any>(null);
const columnTreeRef = ref<any>(null);

const form = reactive<TenantDbConfig>({
  tenantId: '',
  jdbcUrl: '',
  username: '',
  password: '',
  driverClass: 'com.mysql.cj.jdbc.Driver',
  isolationMode: 'PHYSICAL',
  logicColumn: 'tenant_id',
  whiteListTables: '',
  whiteListColumns: '',
  sensitiveColumns: '',
  status: 1
});

/**
 * 创建空租户配置。
 *
 * @return 租户配置
 * @author 余炘洋
 * @date 2026-01-23
 */
const buildEmptyTenant = (): TenantDbConfig => ({
  tenantId: '',
  jdbcUrl: '',
  username: '',
  password: '',
  driverClass: 'com.mysql.cj.jdbc.Driver',
  isolationMode: 'PHYSICAL',
  logicColumn: 'tenant_id',
  whiteListTables: '',
  whiteListColumns: '',
  sensitiveColumns: '',
  status: 1
});

/**
 * 加载租户配置列表。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadTenants = async () => {
  loading.value = true;
  try {
    tenantConfigs.value = await fetchTenantConfigs();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户配置失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 新增租户配置。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleCreate = () => {
  editingId.value = null;
  Object.assign(form, buildEmptyTenant());
  dialogVisible.value = true;
};

/**
 * 编辑租户配置。
 *
 * @param row 租户配置
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleEdit = (row: TenantDbConfig) => {
  editingId.value = row.id || null;
  Object.assign(form, row);
  dialogVisible.value = true;
};

/**
 * 保存租户配置。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleSave = async () => {
  if (!form.tenantId || !form.jdbcUrl) {
    ElMessage.warning('请填写租户ID与JDBC地址');
    return;
  }
  try {
    if (editingId.value) {
      await updateTenantConfig(editingId.value, form);
      ElMessage.success('租户配置已更新');
    } else {
      await createTenantConfig(form);
      ElMessage.success('租户配置已创建');
    }
    dialogVisible.value = false;
    await loadTenants();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  }
};

/**
 * 测试租户连接。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleTestConnection = async () => {
  if (!form.jdbcUrl) {
    ElMessage.warning('请填写 JDBC 地址');
    return;
  }
  try {
    const message = await testTenantConnection(form);
    ElMessage.success(message);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '连接测试失败');
  }
};

/**
 * 删除租户配置。
 *
 * @param row 租户配置
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleDelete = async (row: TenantDbConfig) => {
  if (!row.id) {
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该租户配置？', '提示', {type: 'warning'});
    await deleteTenantConfig(row.id);
    ElMessage.success('租户配置已删除');
    await loadTenants();
  } catch (error) {
    if (!isMessageBoxCancel(error)) {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
};

/**
 * 同步租户元数据。
 *
 * @param row 租户配置
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleSyncSchema = async (row: TenantDbConfig) => {
  if (!row.tenantId) {
    return;
  }
  try {
    const message = await syncTenantSchema(row.tenantId);
    ElMessage.success(message);
    await loadTenants();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '同步失败');
  }
};

/**
 * 刷新语义缓存。
 *
 * @param row 租户配置
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleRefreshSemantic = async (row: TenantDbConfig) => {
  if (!row.tenantId) {
    return;
  }
  try {
    const message = await refreshTenantSemantic(row.tenantId);
    ElMessage.success(message);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新失败');
  }
};

/**
 * 加载租户 Schema。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadSchema = async () => {
  const tenantId = form.tenantId?.trim();
  if (!tenantId) {
    ElMessage.warning('请先填写租户ID');
    return false;
  }
  if (schemaTenantId.value === tenantId && schemaTables.value.length) {
    return true;
  }
  try {
    schemaTables.value = await fetchTenantSchema(tenantId);
    schemaTenantId.value = tenantId;
    return true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载 Schema 失败');
    return false;
  }
};

/**
 * 打开表白名单选择。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const openTableSelect = async () => {
  const ready = await loadSchema();
  if (!ready) {
    return;
  }
  selectedTables.value = form.whiteListTables ? form.whiteListTables.split(',').map((item) => item.trim()).filter(Boolean) : [];
  tableSelectVisible.value = true;
  await nextTick();
  tableTreeRef.value?.setCheckedKeys(selectedTables.value.map((item) => `table:${item}`));
};

/**
 * 打开字段选择。
 *
 * @param mode 选择模式
 * @author 余炘洋
 * @date 2026-01-23
 */
const openColumnSelect = async (mode: 'whitelist' | 'sensitive') => {
  const ready = await loadSchema();
  if (!ready) {
    return;
  }
  columnSelectMode.value = mode;
  const source = mode === 'whitelist' ? form.whiteListColumns : form.sensitiveColumns;
  selectedColumns.value = source ? source.split(',').map((item) => item.trim()).filter(Boolean) : [];
  columnSelectVisible.value = true;
  await nextTick();
  columnTreeRef.value?.setCheckedKeys(selectedColumns.value.map((item) => `col:${item}`));
};

/**
 * 保存表白名单选择。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleTableSelectConfirm = () => {
  const keys: string[] = tableTreeRef.value?.getCheckedKeys(false) || [];
  const tables = keys
    .filter((key) => String(key).startsWith('table:'))
    .map((key) => String(key).replace('table:', ''));
  form.whiteListTables = tables.join(',');
  tableSelectVisible.value = false;
};

/**
 * 保存字段选择。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleColumnSelectConfirm = () => {
  const keys: string[] = columnTreeRef.value?.getCheckedKeys(false) || [];
  const columns = keys
    .filter((key) => String(key).startsWith('col:'))
    .map((key) => String(key).replace('col:', ''));
  const value = columns.join(',');
  if (columnSelectMode.value === 'whitelist') {
    form.whiteListColumns = value;
  } else {
    form.sensitiveColumns = value;
  }
  columnSelectVisible.value = false;
};

/**
 * 构建字段选项。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
/**
 * 构建表树数据。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const tableTreeData = computed(() => {
  return schemaTables.value.map((table) => ({
    id: `table:${table.tableName}`,
    label: table.tableComment ? `${table.tableName}（${table.tableComment}）` : table.tableName
  }));
});

/**
 * 构建字段树数据。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const columnTreeData = computed(() => {
  return schemaTables.value.map((table) => ({
    id: `table:${table.tableName}`,
    label: table.tableComment ? `${table.tableName}（${table.tableComment}）` : table.tableName,
    children: (table.columns || []).map((col) => ({
      id: `col:${table.tableName}.${col.columnName}`,
      label: col.columnComment ? `${col.columnName}（${col.columnComment}）` : col.columnName
    }))
  }));
});

/**
 * 解析租户状态文本。
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
 * 解析租户状态颜色。
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

loadTenants();
</script>

<template>
  <div class="tenant-page">
    <div class="toolbar">
      <el-button type="primary" @click="handleCreate">新增租户</el-button>
      <el-button @click="loadTenants">刷新</el-button>
    </div>
    <el-table :data="tenantConfigs" border v-loading="loading">
      <el-table-column prop="tenantId" label="租户ID" min-width="140"/>
      <el-table-column prop="jdbcUrl" label="JDBC地址" min-width="220"/>
      <el-table-column prop="isolationMode" label="隔离模式" width="100"/>
      <el-table-column prop="schemaSyncStatus" label="同步状态" width="90"/>
      <el-table-column prop="lastSyncTime" label="最近同步" min-width="160"/>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="scope">
          <el-tag :type="resolveStatusType(scope.row.status)">{{ resolveStatusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="运维操作" width="220">
        <template #default="scope">
          <el-button size="small" type="primary" @click="handleSyncSchema(scope.row)">同步元数据</el-button>
          <el-button size="small" @click="handleRefreshSemantic(scope.row)">刷新语义</el-button>
        </template>
      </el-table-column>
      <el-table-column label="配置操作" width="160">
        <template #default="scope">
          <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="租户配置" width="620px">
      <el-form label-width="120px">
        <el-form-item label="租户ID">
          <el-input v-model="form.tenantId" placeholder="请输入租户ID"/>
        </el-form-item>
        <el-form-item label="JDBC地址">
          <el-input v-model="form.jdbcUrl" placeholder="jdbc:mysql://host:port/db"/>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="数据库账号"/>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" placeholder="数据库密码" show-password/>
        </el-form-item>
        <el-form-item label="驱动类">
          <el-input v-model="form.driverClass" placeholder="com.mysql.cj.jdbc.Driver"/>
        </el-form-item>
        <el-form-item label="隔离模式">
          <el-select v-model="form.isolationMode" style="width: 100%">
            <el-option label="物理隔离" value="PHYSICAL"/>
            <el-option label="逻辑隔离" value="LOGICAL"/>
          </el-select>
        </el-form-item>
        <el-form-item label="逻辑隔离字段">
          <el-input v-model="form.logicColumn" placeholder="tenant_id"/>
        </el-form-item>
        <el-form-item label="表白名单">
          <el-input v-model="form.whiteListTables" placeholder="表名逗号分隔">
            <template #append>
              <el-button @click="openTableSelect">选择</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="字段白名单">
          <el-input v-model="form.whiteListColumns" placeholder="字段逗号分隔">
            <template #append>
              <el-button @click="openColumnSelect('whitelist')">选择</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="敏感字段">
          <el-input v-model="form.sensitiveColumns" placeholder="敏感字段逗号分隔">
            <template #append>
              <el-button @click="openColumnSelect('sensitive')">选择</el-button>
            </template>
          </el-input>
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
        <el-button @click="handleTestConnection">测试连接</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="tableSelectVisible" title="选择表白名单" width="520px">
      <el-tree
        ref="tableTreeRef"
        :data="tableTreeData"
        show-checkbox
        node-key="id"
        default-expand-all
        highlight-current
      />
      <template #footer>
        <el-button @click="tableSelectVisible = false">取消</el-button>
        <el-button type="primary" @click="handleTableSelectConfirm">确定</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="columnSelectVisible" title="选择字段" width="560px">
      <el-tree
        ref="columnTreeRef"
        :data="columnTreeData"
        show-checkbox
        node-key="id"
        default-expand-all
        highlight-current
      />
      <template #footer>
        <el-button @click="columnSelectVisible = false">取消</el-button>
        <el-button type="primary" @click="handleColumnSelectConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tenant-page {
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
