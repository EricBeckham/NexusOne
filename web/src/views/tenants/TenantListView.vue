<template>
  <main class="tenant-list-page">
    <NxPageHeader
      eyebrow="星域 · 租户中心"
      title="租户目录"
      description="P1 平台范围租户列表。隔离由服务端 PlatformContext 守卫，不在前端拼 tenant_id 过滤。"
    >
      <template #actions><router-link class="primary" to="/p1/tenants/new">新建租户</router-link></template>
    </NxPageHeader>

    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <template v-else>
      <section class="filters nx-card" aria-label="租户筛选">
        <label class="search-field">
          <span>租户名称 / 编码</span>
          <input v-model="search" type="search" placeholder="搜索名称或编码" data-testid="tenant-search" />
        </label>
        <label>
          <span>状态</span>
          <select v-model="statusFilter" data-testid="tenant-status-filter">
            <option value="">全部状态</option>
            <option v-for="status in availableStatuses" :key="status" :value="status">{{ statusLabel(status) }}</option>
          </select>
        </label>
        <p class="filter-count" aria-live="polite">{{ loading ? "正在加载租户…" : `共 ${visibleRows.length} 个租户` }}</p>
      </section>
      <p class="data-note">管理员、用户数、应用数、行业、配额状态、创建时间与最近活动未包含在当前租户列表读模型中，显示“暂无”。</p>

      <section v-if="loading" class="nx-card loading" role="status">正在加载租户目录…</section>
      <NxEmpty v-else-if="!visibleRows.length" title="暂无租户" :description="emptyDescription" />
      <div v-else class="table-scroll nx-card">
        <table class="nx-table" data-testid="tenant-table">
          <thead><tr>
            <th>租户名称</th><th>租户编码</th><th>状态</th><th>管理员</th><th>用户数</th><th>应用数</th><th>配额状态</th><th>创建时间</th><th>最近活动</th><th>操作</th>
          </tr></thead>
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
        <div class="drawer-actions"><router-link class="primary" :to="`/p1/tenants/${selectedTenant.id}`">打开完整详情</router-link><button class="ghost" type="button" @click="closeDrawer">关闭</button></div>
      </aside>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatusTag } from "../../components";
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
const emptyDescription = computed(() => rows.value.length ? "没有符合当前筛选条件的租户，请调整筛选。" : "平台租户目录当前没有可展示的记录。");

function statusLabel(status) {
  return ({ ENABLED: "启用", DISABLED: "停用", PENDING: "待处理" })[status] || status || "暂无";
}
async function load() {
  loading.value = true; error.value = "";
  try { rows.value = (await p1.listTenants()) || []; }
  catch (err) { error.value = `${err.code || ""} ${err.message || "无法读取租户目录"}`.trim(); }
  finally { loading.value = false; }
}
function openDrawer(row) { selectedTenant.value = row; }
function closeDrawer() { selectedTenant.value = null; }
function onKey(event) { if (event.key === "Escape") closeDrawer(); }

onMounted(() => { load(); window.addEventListener("keydown", onKey); });
onUnmounted(() => window.removeEventListener("keydown", onKey));
</script>

<style scoped>
.filters { display: flex; align-items: flex-end; gap: var(--nx-space-4); margin-bottom: var(--nx-space-3); box-shadow: none; }
.filters label { display: flex; flex-direction: column; gap: var(--nx-space-1); min-width: 190px; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.filters .search-field { flex: 1; }
.filters input,.filters select { width: 100%; min-height: 38px; padding: var(--nx-space-2) var(--nx-space-3); color: var(--nx-text-primary); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); border-radius: 6px; }
.filter-count { margin: 0; color: var(--nx-text-secondary); white-space: nowrap; }
.data-note { margin: 0 0 var(--nx-space-3); color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.table-scroll { overflow-x: auto; padding: 0; }
.nx-table { min-width: 1120px; }
.nx-table th,.nx-table td { white-space: nowrap; }
.tenant-name,.text-action { padding: 0; color: var(--nx-primary); background: none; border: 0; font: inherit; cursor: pointer; }
.tenant-name { font-weight: 600; }
.actions-cell { display: flex; gap: var(--nx-space-3); }
.loading { color: var(--nx-text-secondary); }
.drawer-backdrop { position: fixed; inset: 0; z-index: 20; display: flex; justify-content: flex-end; background: color-mix(in srgb, var(--nx-text-primary) 28%, transparent); }
.tenant-drawer { display: flex; flex-direction: column; width: min(460px, 100vw); height: 100%; padding: var(--nx-space-6); overflow-y: auto; background: var(--nx-surface); box-shadow: var(--nx-shadow-dialog); }
.drawer-header { display: flex; justify-content: space-between; gap: var(--nx-space-4); padding-bottom: var(--nx-space-4); border-bottom: 1px solid var(--nx-divider); }
.eyebrow { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.drawer-header h2 { margin: var(--nx-space-1) 0 0; font-size: var(--nx-font-size-lg); }
.close-button { width: 36px; height: 36px; border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); color: var(--nx-text-secondary); background: var(--nx-surface); font-size: 22px; cursor: pointer; }
.drawer-status { display: flex; align-items: center; justify-content: space-between; padding: var(--nx-space-4) 0; border-bottom: 1px solid var(--nx-divider); }
.tenant-facts { display: grid; grid-template-columns: minmax(140px, .8fr) 1.2fr; gap: var(--nx-space-3); margin: var(--nx-space-4) 0; }
.tenant-facts dt { color: var(--nx-text-secondary); }
.tenant-facts dd { min-width: 0; margin: 0; overflow-wrap: anywhere; }
.drawer-actions { display: flex; gap: var(--nx-space-2); margin-top: auto; padding-top: var(--nx-space-4); }
.primary,.ghost { display: inline-flex; align-items: center; justify-content: center; min-height: 36px; padding: 0 var(--nx-space-3); border-radius: 6px; text-decoration: none; cursor: pointer; }
.primary { color: #fff; background: var(--nx-product); border: 1px solid var(--nx-product); }
.ghost { color: var(--nx-text-regular); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); }
@media(max-width:760px) { .filters { align-items: stretch; flex-direction: column; }.filters label { min-width: 0; }.filter-count { align-self: flex-start; } }
</style>
