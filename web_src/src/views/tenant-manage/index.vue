<script setup lang="ts">
import {computed, reactive, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {
  createTenant,
  deleteTenant,
  fetchTenants,
  TenantCreateRequest,
  TenantUpdateRequest,
  TenantView,
  updateTenant
} from '@/api/tenant';
import {useAuthStore} from '@/store/auth';

const authStore = useAuthStore();
const tenants = ref<TenantView[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const initConfig = ref(false);
const planOptions = computed(() => {
  const options = new Set<string>();
  tenants.value.forEach((tenant) => {
    if (tenant.planName && tenant.planName.trim()) {
      options.add(tenant.planName.trim());
    }
  });
  return Array.from(options);
});

/**
 * 租户表单定义。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
interface TenantForm {
  tenantId: string;
  tenantName: string;
  status: number;
  remark: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  expireTime: string;
  planName: string;
  isolationMode: string;
  jdbcUrl: string;
  username: string;
  password: string;
  driverClass: string;
  logicColumn: string;
}

const form = reactive<TenantForm>({
  tenantId: '',
  tenantName: '',
  status: 1,
  remark: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  expireTime: '',
  planName: '',
  isolationMode: 'PHYSICAL',
  jdbcUrl: '',
  username: '',
  password: '',
  driverClass: 'com.mysql.cj.jdbc.Driver',
  logicColumn: 'tenant_id'
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
 * 初始化表单。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const resetForm = () => {
  form.tenantId = '';
  form.tenantName = '';
  form.status = 1;
  form.remark = '';
  form.contactName = '';
  form.contactPhone = '';
  form.contactEmail = '';
  form.expireTime = '';
  form.planName = '';
  form.isolationMode = 'PHYSICAL';
  form.jdbcUrl = '';
  form.username = '';
  form.password = '';
  form.driverClass = 'com.mysql.cj.jdbc.Driver';
  form.logicColumn = 'tenant_id';
};

/**
 * 加载租户列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadTenants = async () => {
  loading.value = true;
  try {
    tenants.value = await fetchTenants();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 打开新增弹窗。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleCreate = () => {
  editingId.value = null;
  initConfig.value = false;
  resetForm();
  dialogVisible.value = true;
};

/**
 * 打开编辑弹窗。
 *
 * @param row 租户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleEdit = (row: TenantView) => {
  editingId.value = row.id;
  initConfig.value = false;
  form.tenantId = row.tenantId;
  form.tenantName = row.tenantName;
  form.status = row.status ?? 1;
  form.remark = row.remark || '';
  form.contactName = row.contactName || '';
  form.contactPhone = row.contactPhone || '';
  form.contactEmail = row.contactEmail || '';
  form.expireTime = row.expireTime || '';
  form.planName = row.planName || '';
  dialogVisible.value = true;
};

/**
 * 构建新增请求体。
 *
 * @return 创建请求
 * @author 余炘洋
 * @date 2026-01-26
 */
const buildCreatePayload = (): TenantCreateRequest => {
  const payload: TenantCreateRequest = {
    tenantName: form.tenantName.trim(),
    status: form.status,
    remark: form.remark || undefined,
    contactName: form.contactName || undefined,
    contactPhone: form.contactPhone || undefined,
    contactEmail: form.contactEmail || undefined,
    expireTime: form.expireTime || undefined,
    planName: form.planName || undefined
  };
  if (initConfig.value) {
    payload.isolationMode = form.isolationMode;
    payload.jdbcUrl = form.jdbcUrl || undefined;
    payload.username = form.username || undefined;
    payload.password = form.password || undefined;
    payload.driverClass = form.driverClass || undefined;
    payload.logicColumn = form.logicColumn || undefined;
  }
  return payload;
};

/**
 * 构建更新请求体。
 *
 * @return 更新请求
 * @author 余炘洋
 * @date 2026-01-26
 */
const buildUpdatePayload = (): TenantUpdateRequest => {
  return {
    tenantName: form.tenantName.trim(),
    status: form.status,
    remark: form.remark || undefined,
    contactName: form.contactName || undefined,
    contactPhone: form.contactPhone || undefined,
    contactEmail: form.contactEmail || undefined,
    expireTime: form.expireTime || undefined,
    planName: form.planName || undefined
  };
};

/**
 * 保存租户。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSave = async () => {
  if (!form.tenantName.trim()) {
    ElMessage.warning('请填写租户名称');
    return;
  }
  try {
    if (editingId.value) {
      await updateTenant(editingId.value, buildUpdatePayload());
      ElMessage.success('租户已更新');
    } else {
      await createTenant(buildCreatePayload());
      ElMessage.success('租户已创建');
    }
    dialogVisible.value = false;
    await loadTenants();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  }
};

/**
 * 删除租户。
 *
 * @param row 租户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleDelete = async (row: TenantView) => {
  try {
    await ElMessageBox.confirm('确认删除该租户？', '提示', {type: 'warning'});
    await deleteTenant(row.id);
    ElMessage.success('租户已删除');
    await loadTenants();
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
 * @return 文案
 * @author 余炘洋
 * @date 2026-01-26
 */
const resolveStatusText = (status?: number) => {
  return status === 1 ? '启用' : '停用';
};

/**
 * 解析状态标签样式。
 *
 * @param status 状态值
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-01-26
 */
const resolveStatusType = (status?: number) => {
  return status === 1 ? 'success' : 'info';
};

/**
 * 判断是否为弹窗取消。
 *
 * @param error 异常对象
 * @return 是否取消
 * @author 余炘洋
 * @date 2026-01-26
 */
const isMessageBoxCancel = (error: unknown) => {
  return error === 'cancel' || error === 'close';
};

loadTenants();
</script>

<template>
  <div class="page">
    <div class="page-title">租户管理</div>
    <div class="page-desc">维护租户信息并执行初始化配置。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-actions">
          <el-button type="primary" :disabled="!isSysAdmin" @click="handleCreate">新增租户</el-button>
          <el-button @click="loadTenants">刷新</el-button>
        </div>
        <div class="toolbar-tip" v-if="!isSysAdmin">仅系统管理员可管理租户。</div>
      </div>

      <el-table :data="tenants" border v-loading="loading">
        <el-table-column prop="tenantId" label="租户ID" min-width="140"/>
        <el-table-column prop="tenantName" label="租户名称" min-width="160"/>
        <el-table-column prop="planName" label="套餐" min-width="120"/>
        <el-table-column prop="expireTime" label="到期时间" min-width="160"/>
        <el-table-column prop="contactName" label="联系人" min-width="120"/>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="scope">
            <el-tag :type="resolveStatusType(scope.row.status)">
              {{ resolveStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180"/>
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button size="small" :disabled="!isSysAdmin" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" :disabled="!isSysAdmin" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑租户' : '新增租户'" width="640px" :close-on-click-modal="false">
      <el-form label-width="120px">
        <el-form-item label="租户ID">
          <el-tag v-if="editingId" type="info">{{ form.tenantId || '未生成' }}</el-tag>
          <el-tag v-else type="info">系统自动生成</el-tag>
        </el-form-item>
        <el-form-item label="租户名称" required>
          <el-input v-model="form.tenantName" placeholder="请输入租户名称"/>
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" placeholder="联系人姓名"/>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="联系电话"/>
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="form.contactEmail" placeholder="邮箱"/>
        </el-form-item>
        <el-form-item label="到期时间">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            placeholder="请选择到期时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="套餐">
          <el-select v-model="form.planName" placeholder="请选择套餐" filterable clearable style="width: 100%">
            <el-option v-for="plan in planOptions" :key="plan" :label="plan" :value="plan"/>
          </el-select>
          <div class="form-tip">套餐来源于“套餐管理”页现有租户配置。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :value="1" label="启用"/>
            <el-option :value="0" label="停用"/>
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="备注信息"/>
        </el-form-item>
        <template v-if="!editingId">
          <el-divider content-position="left">初始化配置</el-divider>
          <el-form-item label="初始化数据源">
            <el-switch v-model="initConfig"/>
          </el-form-item>
          <template v-if="initConfig">
            <el-form-item label="隔离模式">
              <el-select v-model="form.isolationMode" style="width: 100%">
                <el-option label="物理隔离" value="PHYSICAL"/>
                <el-option label="逻辑隔离" value="LOGICAL"/>
              </el-select>
            </el-form-item>
            <el-form-item label="JDBC地址">
              <el-input v-model="form.jdbcUrl" placeholder="jdbc:mysql://host:port/db"/>
            </el-form-item>
            <el-form-item label="账号">
              <el-input v-model="form.username" placeholder="数据库账号"/>
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" placeholder="数据库密码" show-password/>
            </el-form-item>
            <el-form-item label="驱动类">
              <el-input v-model="form.driverClass" placeholder="com.mysql.cj.jdbc.Driver"/>
            </el-form-item>
            <el-form-item label="逻辑字段">
              <el-input v-model="form.logicColumn" placeholder="tenant_id"/>
            </el-form-item>
          </template>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
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

.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.toolbar-tip {
  color: #94a3b8;
  font-size: 13px;
}

.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 6px;
}
</style>
