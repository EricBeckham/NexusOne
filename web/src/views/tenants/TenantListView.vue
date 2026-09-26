<template>
  <main class="tenant-list-page">
    <NxPageHeader
      eyebrow="星域 · 租户中心"
      title="租户目录"
      description="P1 平台范围租户列表。隔离由服务端 PlatformContext 守卫，不在前端拼 tenant_id 过滤。"
      :crumbs="[{ label: '星域' }, { label: '租户目录' }]"
    >
      <template #actions>
        <button class="ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <router-link class="primary" to="/p1/tenants/new">新建租户</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <template v-else>
      <NxFilterBar label="租户筛选" testid="tenant" @reset="resetFilters">
        <label class="nx-filter-field wide">
          租户名称 / 编码
          <input v-model="search" type="search" placeholder="搜索名称或编码" data-testid="tenant-search" />
        </label>
        <label class="nx-filter-field">
          状态
          <select v-model="statusFilter" data-testid="tenant-status-filter">
            <option value="">全部状态</option>
            <option v-for="status in availableStatuses" :key="status" :value="status">{{ statusLabel(status) }}</option>
          </select>
        </label>
        <template #summary>{{ loading ? "正在加载租户…" : `共 ${visibleRows.length} 个租户` }}</template>
      </NxFilterBar>
      <p class="data-note">管理员、用户数、应用数、行业、配额状态、创建时间与最近活动未包含在当前租户列表读模型中，显示“暂无”。</p>

      <div v-if="loading" class="tenant-skeleton" data-testid="tenant-loading" role="status">
        <NxSkeleton v-for="n in 4" :key="n" variant="list-row" label="正在加载租户目录" />
      </div>
      <NxEmpty v-else-if="!visibleRows.length" title="暂无租户" :description="emptyDescription" data-testid="tenant-empty">
        <template #next>
          <router-link v-if="!rows.length" class="primary" to="/p1/tenants/new">新建租户</router-link>
          <button v-else class="ghost" type="button" data-testid="tenant-empty-reset" @click="resetFilters">清空筛选</button>
        </template>
      </NxEmpty>
      <div v-else class="table-scroll">
        <table class="nx-table nx-table--compact" data-testid="tenant-table">
          <thead>
            <tr>
              <th scope="col">租户名称</th>
              <th scope="col">租户编码</th>
              <th scope="col">状态</th>
              <th scope="col">管理员</th>
              <th scope="col">用户数</th>
              <th scope="col">应用数</th>
              <th scope="col">配额状态</th>
              <th scope="col">创建时间</th>
              <th scope="col">最近活动</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visibleRows" :key="row.id" :data-testid="`tenant-row-${row.id}`">
              <td><button class="tenant-name" type="button" @click="openDrawer(row)">{{ row.name || "暂无" }}</button></td>
              <td class="nx-code">{{ row.code || "暂无" }}</td>
              <td><NxStatusTag :status="row.status" :label="statusLabel(row.status)" /></td>
              <td>暂无</td><td>暂无</td><td>暂无</td><td>暂无</td><td>暂无</td><td>暂无</td>
              <td class="actions-cell">
                <button class="text-action" type="button" @click="openDrawer(row)">详情</button>
                <router-link class="text-action" :to="`/p1/tenants/${row.id}`">完整详情</router-link>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <div v-if="selectedTenant" class="drawer-backdrop" data-testid="tenant-drawer-backdrop" @click.self="closeDrawer" @keydown.esc="closeDrawer">
      <aside class="tenant-drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title" data-testid="tenant-drawer">
        <header class="drawer-header">
          <div><span class="eyebrow">租户摘要</span><h2 id="drawer-title">{{ selectedTenant.name || "暂无" }}</h2></div>
          <button class="close-button" type="button" aria-label="关闭详情" @click="closeDrawer">×</button>
        </header>
        <div class="drawer-status"><span>状态</span><NxStatusTag :status="selectedTenant.status" :label="statusLabel(selectedTenant.status)" /></div>
        <dl class="tenant-facts">
          <dt>租户编码</dt><dd class="nx-code">{{ selectedTenant.code || "暂无" }}</dd>
          <dt>租户 ID</dt><dd class="nx-code">{{ selectedTenant.id }}</dd>
          <dt>法定名称</dt><dd>{{ selectedTenant.legalName || "暂无" }}</dd>
          <dt>配置版本</dt><dd>{{ selectedTenant.version ?? "暂无" }}</dd>
          <dt>管理员 / 用户 / 应用</dt><dd>暂无读模型</dd>
          <dt>配额 / 创建时间 / 最近活动</dt><dd>暂无读模型</dd>
        </dl>
        <div class="drawer-actions">
          <router-link class="primary" :to="`/p1/tenants/${selectedTenant.id}`">打开完整详情</router-link>
          <button class="ghost" type="button" @click="closeDrawer">关闭</button>
        </div>
      </aside>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { NxEmpty, NxErrorState, NxFilterBar, NxPageHeader, NxSkeleton, NxStatusTag } from "../../components";
import { p1 } from "../../api/client";

const rows = ref([]);
const loading = ref(true);
const error = ref("");
const search = ref("");
const statusFilter = ref("");
const selectedTenant = ref(null);
const availableStatuses = computed(() => [...new Set(rows.value.map((row) => row.status).filter(Boolean))].sort());
const visibleRows = computed(() => {
  const term = search.value.trim().toLocaleLowerCase();
  return rows.value.filter((row) => {
    const matchesSearch = !term || `${row.name || ""} ${row.code || ""}`.toLocaleLowerCase().includes(term);
    return matchesSearch && (!statusFilter.value || row.status === statusFilter.value);
  });
});
const emptyDescription = computed(() => (
  rows.value.length
    ? "没有符合当前筛选条件的租户，请调整筛选。"
    : "平台租户目录当前没有可展示的记录。创建第一个租户后，可开始配置组织和业务应用。"
));

function statusLabel(status) {
  return ({ ENABLED: "启用", DISABLED: "停用", PENDING: "待处理" })[status] || status || "暂无";
}

function resetFilters() {
  search.value = "";
  statusFilter.value = "";
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    rows.value = (await p1.listTenants()) || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message || "无法读取租户目录"}`.trim();
  } finally {
    loading.value = false;
  }
}

function openDrawer(row) { selectedTenant.value = row; }
function closeDrawer() { selectedTenant.value = null; }
function onKey(event) { if (event.key === "Escape") closeDrawer(); }

onMounted(() => { load(); window.addEventListener("keydown", onKey); });
onUnmounted(() => window.removeEventListener("keydown", onKey));
</script>

<style scoped>
.tenant-list-page { display: grid; gap: var(--nx-space-3); }
.tenant-list-page :deep(.page-header) { margin-bottom: 0; }
.data-note { margin: 0; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.tenant-skeleton { display: grid; gap: var(--nx-space-2); }
.table-scroll {
  overflow-x: auto;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius);
  background: var(--nx-surface);
}
.nx-table { min-width: 1120px; border: 0; }
.nx-table th,
.nx-table td { white-space: nowrap; }
.tenant-name,
.text-action {
  padding: 0;
  color: var(--nx-primary);
  background: none;
  border: 0;
  font: inherit;
  cursor: pointer;
  text-decoration: none;
}
.tenant-name { font-weight: 600; }
.actions-cell { display: flex; gap: var(--nx-space-3); }
.drawer-backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  justify-content: flex-end;
  background: color-mix(in srgb, var(--nx-text-primary) 28%, transparent);
}
.tenant-drawer {
  display: flex;
  flex-direction: column;
  width: min(460px, 100vw);
  height: 100%;
  padding: var(--nx-space-6);
  overflow-y: auto;
  background: var(--nx-surface);
  box-shadow: var(--nx-shadow-dialog);
}
.drawer-header {
  display: flex;
  justify-content: space-between;
  gap: var(--nx-space-4);
  padding-bottom: var(--nx-space-4);
  border-bottom: 1px solid var(--nx-divider);
}
.eyebrow { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.drawer-header h2 { margin: var(--nx-space-1) 0 0; font-size: var(--nx-font-size-lg); }
.close-button {
  width: var(--nx-control-height);
  height: var(--nx-control-height);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
  color: var(--nx-text-secondary);
  background: var(--nx-surface);
  font-size: 22px;
  cursor: pointer;
}
.drawer-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--nx-space-4) 0;
  border-bottom: 1px solid var(--nx-divider);
}
.tenant-facts {
  display: grid;
  grid-template-columns: minmax(140px, .8fr) 1.2fr;
  gap: var(--nx-space-3);
  margin: var(--nx-space-4) 0;
}
.tenant-facts dt { color: var(--nx-text-secondary); }
.tenant-facts dd { min-width: 0; margin: 0; overflow-wrap: anywhere; }
.drawer-actions { display: flex; gap: var(--nx-space-2); margin-top: auto; padding-top: var(--nx-space-4); }
.primary,
.ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--nx-control-height);
  padding: 0 var(--nx-space-3);
  border-radius: var(--nx-control-radius);
  text-decoration: none;
  cursor: pointer;
}
.primary { color: #fff; background: var(--nx-product); border: 1px solid var(--nx-product); }
.ghost { color: var(--nx-text-regular); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); }
.ghost:disabled { opacity: .55; cursor: not-allowed; }
</style>
