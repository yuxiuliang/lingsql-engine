<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {syncTenantSchema} from '@/api/admin';
import {
  aiSuggestMetadata,
  fetchMetadataSchema,
  fetchMetadataTable,
  MetadataFieldView,
  MetadataTableInfo,
  MetadataTableView,
  updateIsolationField,
  updateMetadataField
} from '@/api/metadata';

/**
 * 树节点定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
interface TreeNode {
  id: string;
  label: string;
  type: 'tenant' | 'schema' | 'table';
  tableName?: string;
  children?: TreeNode[];
}

const authStore = useAuthStore();
const schemaLoading = ref(false);
const tableLoading = ref(false);
const schemaTables = ref<MetadataTableInfo[]>([]);
const treeData = ref<TreeNode[]>([]);
const activeTable = ref<MetadataTableView | null>(null);
const activeTableName = ref('');
const savedRowKeys = ref<Set<string>>(new Set());

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
 * 构建树形结构。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const buildTree = () => {
  const tenantId = activeTenantId.value || '未选择租户';
  const tenantName = activeTenantName.value || tenantId;
  const groups: Record<string, MetadataTableInfo[]> = {};
  schemaTables.value.forEach((table) => {
    const schemaName = table.schemaName || 'default';
    if (!groups[schemaName]) {
      groups[schemaName] = [];
    }
    groups[schemaName].push(table);
  });
  const schemaNodes = Object.keys(groups).map((schemaName) => ({
    id: `schema:${schemaName}`,
    label: schemaName,
    type: 'schema' as const,
    children: groups[schemaName].map((table) => ({
      id: `table:${table.tableName}`,
      label: formatTableLabel(table.tableName, table.tableComment),
      type: 'table' as const,
      tableName: table.tableName
    }))
  }));
  treeData.value = [
    {
      id: `tenant:${tenantId}`,
      label: `租户：${tenantName}`,
      type: 'tenant',
      children: schemaNodes
    }
  ];
};

/**
 * 加载 Schema 缓存。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadSchema = async () => {
  if (!hasTenantSelected.value || !activeTenantId.value) {
    return;
  }
  schemaLoading.value = true;
  try {
    schemaTables.value = await fetchMetadataSchema(activeTenantId.value);
    buildTree();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载 Schema 失败');
  } finally {
    schemaLoading.value = false;
  }
};

/**
 * 同步 Schema。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleSyncSchema = async () => {
  if (!hasTenantSelected.value || !activeTenantId.value) {
    ElMessage.warning('请先选择租户');
    return;
  }
  try {
    const message = await syncTenantSchema(activeTenantId.value);
    ElMessage.success(message);
    await loadSchema();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '同步失败');
  }
};

/**
 * 加载表详情。
 *
 * @param tableName 表名
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadTableDetail = async (tableName: string) => {
  if (!activeTenantId.value) {
    return;
  }
  tableLoading.value = true;
  activeTableName.value = tableName;
  try {
    activeTable.value = await fetchMetadataTable(activeTenantId.value, tableName);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载表详情失败');
  } finally {
    tableLoading.value = false;
  }
};

/**
 * 点击树节点。
 *
 * @param node 树节点
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleNodeClick = (node: TreeNode) => {
  if (node.type === 'table' && node.tableName) {
    loadTableDetail(node.tableName);
  }
};

/**
 * 保存业务注释。
 *
 * @param row 字段行
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleSaveComment = async (row: MetadataFieldView) => {
  if (!activeTenantId.value || !activeTableName.value) {
    return;
  }
  try {
    await updateMetadataField(activeTenantId.value, activeTableName.value, row.columnName, row.businessComment || '');
    ElMessage.success('已保存');
    highlightRow(row.columnName);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  }
};

/**
 * 更新隔离字段。
 *
 * @param row 字段行
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleIsolationChange = async (row: MetadataFieldView) => {
  if (!activeTenantId.value || !activeTableName.value) {
    return;
  }
  try {
    await updateIsolationField(activeTenantId.value, activeTableName.value, row.columnName, row.isolatedField || false);
    if (row.isolatedField) {
      activeTable.value?.columns.forEach((item) => {
        if (item.columnName !== row.columnName) {
          item.isolatedField = false;
        }
      });
    }
    ElMessage.success('已更新隔离字段');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新失败');
  }
};

/**
 * AI 语义解析。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleAiSuggest = async () => {
  if (!activeTenantId.value || !activeTable.value) {
    ElMessage.warning('请先选择表');
    return;
  }
  try {
    const response = await aiSuggestMetadata(
      activeTenantId.value,
      activeTable.value.tableName,
      activeTable.value.columns.map((item) => ({
        name: item.columnName,
        comment: item.columnComment
      }))
    );
    const suggestions = response.suggestions || [];
    if (!suggestions.length) {
      ElMessage.warning('暂无可用建议');
      return;
    }
    suggestions.forEach((item) => {
      const target = activeTable.value?.columns.find((col) => col.columnName === item.columnName);
      if (target) {
        target.businessComment = item.comment;
      }
    });
    ElMessage.success('AI 建议已填充，请手动保存生效');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '解析失败');
  }
};

/**
 * 行高亮规则。
 *
 * @param row 行数据
 * @author 余炘洋
 * @date 2026-01-23
 */
const rowClassName = ({row}: {row: MetadataFieldView}) => {
  const classes: string[] = [];
  if (!row.businessComment) {
    classes.push('missing-comment');
  }
  if (savedRowKeys.value.has(row.columnName)) {
    classes.push('saved-flash');
  }
  return classes.join(' ');
};

/**
 * 业务注释乱码检测。
 *
 * @param comment 注释文本
 * @return 是否乱码
 * @author 余炘洋
 * @date 2026-01-23
 */
const isGarbled = (comment?: string) => {
  if (!comment) {
    return false;
  }
  return comment.includes('�') || comment.includes('Ã') || comment.includes('â') || comment.includes('Â');
};

/**
 * 格式化注释内容。
 *
 * @param comment 注释文本
 * @return 展示文本
 * @author 余炘洋
 * @date 2026-01-23
 */
const formatComment = (comment?: string) => {
  if (!comment || isGarbled(comment)) {
    return '未配置';
  }
  return comment;
};

/**
 * 生成表名展示文本。
 *
 * @param tableName 表名
 * @param comment 注释
 * @return 展示文本
 * @author 余炘洋
 * @date 2026-01-23
 */
const formatTableLabel = (tableName: string, comment?: string) => {
  const displayComment = formatComment(comment);
  if (displayComment === '未配置') {
    return tableName;
  }
  return `${tableName}（${displayComment}）`;
};

/**
 * 保存后高亮行。
 *
 * @param columnName 字段名
 * @author 余炘洋
 * @date 2026-01-23
 */
const highlightRow = (columnName: string) => {
  savedRowKeys.value.add(columnName);
  setTimeout(() => {
    savedRowKeys.value.delete(columnName);
  }, 900);
};

const tableTitle = computed(() => {
  if (!activeTable.value) {
    return '请选择表';
  }
  const table = activeTable.value;
  return formatTableLabel(table.tableName, table.tableComment);
});

onMounted(() => {
  loadSchema();
});

watch(activeTenantId, () => {
  activeTable.value = null;
  activeTableName.value = '';
  schemaTables.value = [];
  treeData.value = [];
  if (hasTenantSelected.value) {
    loadSchema();
  }
});
</script>

<template>
  <div class="metadata-wrapper">
    <div v-if="!hasTenantSelected" class="metadata-empty">
      <el-empty description="请在顶部选择要管理的租户"/>
    </div>
    <div v-else class="metadata-page">
      <aside class="metadata-sidebar">
        <div class="sidebar-header">
          <div class="sidebar-title">元数据字典</div>
          <div class="sidebar-subtitle">缓存视图</div>
        </div>
        <el-button class="sync-button" @click="handleSyncSchema">一键同步</el-button>
        <el-tree
          v-loading="schemaLoading"
          class="schema-tree"
          :data="treeData"
          node-key="id"
          default-expand-all
          highlight-current
          @node-click="handleNodeClick"
        />
      </aside>
      <section class="metadata-content">
        <div class="content-header">
          <div class="content-title">{{ tableTitle }}</div>
          <div class="content-actions">
            <el-button type="primary" @click="handleAiSuggest">AI 语义解析</el-button>
          </div>
        </div>
        <el-table
          v-loading="tableLoading"
          :data="activeTable?.columns || []"
          border
          class="metadata-table"
          :row-class-name="rowClassName"
        >
          <el-table-column prop="columnName" label="字段名" min-width="140"/>
          <el-table-column label="类型" min-width="120">
            <template #default="{row}">
              <span class="data-type">{{ row.dataType }}</span>
            </template>
          </el-table-column>
          <el-table-column label="原始注释" min-width="200">
            <template #default="{row}">
              <span>{{ formatComment(row.columnComment) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="业务注释" min-width="240">
            <template #default="{row}">
              <el-input v-model="row.businessComment" placeholder="填写业务注释" size="small"/>
            </template>
          </el-table-column>
          <el-table-column label="隔离字段" width="120">
            <template #default="{row}">
              <el-switch v-model="row.isolatedField" @change="() => handleIsolationChange(row)"/>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{row}">
              <el-button size="small" @click="handleSaveComment(row)">保存</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<style scoped>
.metadata-wrapper {
  background: transparent;
}

.metadata-empty {
  padding: 40px 0;
  background: #ffffff;
  border-radius: 16px;
}

.metadata-page {
  height: calc(100vh - 120px);
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
  padding: 14px;
  background: #f8fafc;
  border-radius: 16px;
}

.metadata-sidebar {
  background: #ffffff;
  border-radius: 16px;
  padding: 14px;
  color: #1f2937;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
}

.sidebar-subtitle {
  font-size: 12px;
  color: #64748b;
}

.sync-button {
  border: none;
  background: rgba(64, 158, 255, 0.12);
  color: #1f2937;
}

.schema-tree {
  background: transparent;
  color: #334155;
}

.schema-tree :deep(.el-tree-node__content) {
  color: #334155;
}

.metadata-content {
  background: #ffffff;
  border-radius: 16px;
  padding: 14px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.content-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.metadata-table :deep(.el-table__header th) {
  background: #f1f5f9;
  color: #334155;
  font-weight: 600;
}

.metadata-table :deep(.el-table__row.missing-comment td) {
  background: #fff9db;
}

.metadata-table :deep(.el-table__row.saved-flash td) {
  animation: saveFlash 0.9s ease;
}

.data-type {
  font-size: 12px;
  color: #94a3b8;
}

@keyframes saveFlash {
  0% {
    background: rgba(34, 197, 94, 0.25);
  }
  100% {
    background: transparent;
  }
}

@media (max-width: 980px) {
  .metadata-page {
    grid-template-columns: 1fr;
  }
}
</style>
