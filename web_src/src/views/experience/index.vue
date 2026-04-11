<script setup lang="ts">
import {computed, nextTick, onMounted, reactive, ref, watch} from 'vue';
import {ElMessage} from 'element-plus';
import {fetchMenus, MenuView} from '@/api/auth';
import {ExperiencePolicyRequest, ExperiencePolicyView, fetchExperiencePolicy, saveExperiencePolicy} from '@/api/rbac';

const loading = ref(false);
const menuLoading = ref(false);
const userIdInput = ref('');
const menuFilter = ref('');
const menuTree = ref<MenuView[]>([]);
const menuTreeRef = ref();
const menuCheckedKeys = ref<string[]>([]);
const form = reactive({
  scopeType: 'TENANT',
  scopeColumn: '',
  scopeRule: '',
  enabled: true,
  status: 1
});

/**
 * 收集菜单编码。
 *
 * @param menus 菜单列表
 * @return 编码列表
 * @author 余炘洋
 * @date 2026-01-27
 */
const collectMenuCodes = (menus: MenuView[]): string[] => {
  const result: string[] = [];
  const traverse = (list: MenuView[]) => {
    list.forEach((menu) => {
      if (menu.menuCode) {
        result.push(menu.menuCode);
      }
      if (menu.children && menu.children.length > 0) {
        traverse(menu.children);
      }
    });
  };
  traverse(menus);
  return Array.from(new Set(result));
};

/**
 * 获取已勾选菜单编码。
 *
 * @return 编码列表
 * @author 余炘洋
 * @date 2026-01-27
 */
const resolveCheckedMenus = (): string[] => {
  if (!menuTreeRef.value) {
    return menuCheckedKeys.value;
  }
  const leafKeys = menuTreeRef.value.getCheckedKeys(true);
  return Array.isArray(leafKeys) ? leafKeys.map((item) => String(item)) : [];
};

/**
 * 应用菜单白名单。
 *
 * @param codes 菜单编码
 * @author 余炘洋
 * @date 2026-01-27
 */
const applyMenuWhitelist = (codes: string[]) => {
  menuCheckedKeys.value = codes;
  nextTick(() => {
    if (menuTreeRef.value) {
      menuTreeRef.value.setCheckedKeys(codes || []);
    }
  });
};

/**
 * 加载菜单树。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const loadMenus = async () => {
  menuLoading.value = true;
  try {
    menuTree.value = await fetchMenus();
    applyMenuWhitelist(menuCheckedKeys.value);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载菜单失败');
  } finally {
    menuLoading.value = false;
  }
};

/**
 * 菜单过滤函数。
 *
 * @param value 关键词
 * @param data 菜单节点
 * @return 是否命中
 * @author 余炘洋
 * @date 2026-01-27
 */
const filterMenuNode = (value: string, data: MenuView) => {
  if (!value) {
    return true;
  }
  const keyword = value.toLowerCase();
  const text = `${data.menuName || ''} ${data.menuCode || ''}`.toLowerCase();
  return text.includes(keyword);
};

/**
 * 全选菜单。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleSelectAllMenus = () => {
  const codes = collectMenuCodes(menuTree.value);
  applyMenuWhitelist(codes);
};

/**
 * 清空菜单。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleClearMenus = () => {
  applyMenuWhitelist([]);
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
    menuWhitelist: resolveCheckedMenus(),
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
  applyMenuWhitelist(view.menuWhitelist || []);
  form.scopeType = view.dataScopeOverride?.scopeType || 'TENANT';
  form.scopeColumn = view.dataScopeOverride?.scopeColumn || '';
  form.scopeRule = view.dataScopeOverride?.scopeRule || '';
  form.status = view.status ?? 1;
  form.enabled = view.status == null ? true : view.status === 1;
};

watch(menuFilter, (value) => {
  if (menuTreeRef.value) {
    menuTreeRef.value.filter(value);
  }
});

const handleMenuCheck = () => {
  menuCheckedKeys.value = resolveCheckedMenus();
};

const menuCountLabel = computed(() => `${menuCheckedKeys.value.length} 项已选`);

onMounted(loadMenus);
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
          <div class="menu-panel">
            <div class="menu-toolbar">
              <el-input v-model="menuFilter" placeholder="搜索菜单名称/编码" style="max-width: 260px"/>
              <div class="menu-actions">
                <el-tag size="small" type="info">{{ menuCountLabel }}</el-tag>
                <el-button size="small" @click="handleSelectAllMenus">全选</el-button>
                <el-button size="small" @click="handleClearMenus">清空</el-button>
              </div>
            </div>
            <el-tree
              ref="menuTreeRef"
              v-loading="menuLoading"
              :data="menuTree"
              node-key="menuCode"
              show-checkbox
              default-expand-all
              :filter-node-method="filterMenuNode"
              :props="{children: 'children', label: 'menuName'}"
              class="menu-tree"
              @check="handleMenuCheck"
            >
              <template #default="{data}">
                <span class="menu-node">
                  <span class="menu-name">{{ data.menuName || '未命名菜单' }}</span>
                  <span v-if="data.menuCode" class="menu-code">{{ data.menuCode }}</span>
                </span>
              </template>
            </el-tree>
          </div>
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.scopeType" style="width: 100%">
            <el-option label="租户" value="TENANT"/>
            <el-option label="全部" value="ALL"/>
            <el-option label="指定组织" value="ORG"/>
            <el-option label="组织及下级" value="ORG_SUBTREE"/>
            <el-option label="仅自己" value="SELF"/>
            <el-option label="自定义" value="CUSTOM"/>
          </el-select>
        </el-form-item>
        <el-form-item label="范围字段">
          <el-input v-model="form.scopeColumn" placeholder="SELF/ORG/ORG_SUBTREE 时可填写字段名，如 creator_id 或 org_code"/>
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

.menu-panel {
  width: 100%;
}

.menu-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.menu-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.menu-tree {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  max-height: 360px;
  overflow: auto;
  background: #f8fafc;
}

.menu-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.menu-code {
  font-size: 12px;
  color: #64748b;
}
</style>
