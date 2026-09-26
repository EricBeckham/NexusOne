<template>
  <main class="api-catalog-page">
    <NxPageHeader
      eyebrow="星联 / 自用接口"
      title="自用 API 目录"
      description="只读元数据。没有调用量、成功率，也不提供凭证。"
    >
      <template v-if="isDetail" #actions>
        <router-link class="ghost" to="/p5/apis">返回列表</router-link>
      </template>
    </NxPageHeader>

    <NxNoPermission v-if="denied" title="暂无权限" :description="error || '当前身份不能查看自用 API 目录。'" />
    <NxErrorState v-else-if="error" :title="error" :trace-id="traceId" @retry="load" />

    <template v-else-if="isDetail">
      <NxEmpty v-if="loaded && !entry" title="暂无" description="接口没有返回这条目录记录。" />
      <section v-else-if="entry" class="nx-card">
        <div class="detail-head">
          <span class="method" :data-method="entry.httpMethod || 'OTHER'">{{ entry.httpMethod || "暂无" }}</span>
          <h2>{{ entry.name || "暂无" }}</h2>
        </div>
        <div class="tabs" role="tablist" aria-label="API 详情">
          <button
            v-for="item in tabs"
            :key="item.id"
            type="button"
            role="tab"
            :aria-selected="tab === item.id"
            :class="{ current: tab === item.id }"
            @click="setTab(item.id)"
          >{{ item.label }}</button>
        </div>
        <div v-if="tab === 'overview'" role="tabpanel">
          <dl>
            <div><dt>Path</dt><dd>{{ entry.path || "暂无" }}</dd></div>
            <div><dt>Version</dt><dd>{{ entry.version || "暂无" }}</dd></div>
            <div><dt>状态</dt><dd><NxStatusTag v-if="entry.status" :status="entry.status" :label="statusLabel(entry.status)" /><template v-else>暂无</template></dd></div>
            <div><dt>可见范围</dt><dd>{{ entry.shared ? "共享" : "本租户" }}</dd></div>
            <div><dt>说明</dt><dd>{{ entry.description || "暂无" }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ formatTime(entry.updatedAt) }}</dd></div>
          </dl>
        </div>
        <div v-else-if="tab === 'request'" role="tabpanel">
          <p>Method：{{ entry.httpMethod || "暂无" }}</p>
          <p>Path：{{ entry.path || "暂无" }}</p>
          <p>请求体：暂无</p>
        </div>
        <div v-else-if="tab === 'response'" role="tabpanel">
          <p>响应结构：暂无</p>
        </div>
        <div v-else role="tabpanel">
          <p>{{ entry.scope || "暂无" }}</p>
        </div>
      </section>
    </template>

    <template v-else>
      <NxFilterBar label="自用 API 筛选" testid="api-catalog" @reset="query = ''">
        <label class="nx-filter-field wide">搜索<input v-model.trim="query" type="search" placeholder="API 名称、Method 或 Path" aria-label="搜索 API" /></label>
        <template #summary>{{ loaded && rows ? `显示 ${visibleRows.length} / ${rows.length} 个 API` : "正在加载…" }}</template>
      </NxFilterBar>
      <NxEmpty v-if="loaded && rows && !rows.length" title="暂无自用 API" description="当前租户还没有可见的自用 API；请先在接入配置中登记接口。">
        <template #next><router-link class="ghost" to="/p5/connect">查看接入总览</router-link></template>
      </NxEmpty>
      <NxEmpty v-else-if="loaded && rows && !visibleRows.length" title="没有匹配的 API" description="调整搜索词或重置筛选后重试。" compact>
        <template #next><button class="ghost" type="button" @click="query = ''">重置筛选</button></template>
      </NxEmpty>
      <div v-else-if="visibleRows.length" class="table-scroll"><table class="nx-table nx-table--compact" data-testid="api-catalog-table">
        <thead>
          <tr>
            <th scope="col">API 名称</th>
            <th scope="col">Method</th>
            <th scope="col">Path</th>
            <th scope="col">Version</th>
            <th scope="col">Scope</th>
            <th scope="col">状态</th>
            <th scope="col">调用量</th>
            <th scope="col">成功率</th>
            <th scope="col">更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visibleRows" :key="row.id">
            <td><router-link :to="`/p5/apis/${row.id}`">{{ row.name || "暂无" }}</router-link></td>
            <td><span class="method" :data-method="row.httpMethod || 'OTHER'">{{ row.httpMethod || "暂无" }}</span></td>
            <td>{{ row.path || "暂无" }}</td>
            <td>{{ row.version || "暂无" }}</td>
            <td>{{ row.scope || "暂无" }}</td>
            <td><NxStatusTag v-if="row.status" :status="row.status" :label="statusLabel(row.status)" /><template v-else>暂无</template></td>
            <td data-testid="call-volume">暂无</td>
            <td data-testid="success-rate">暂无</td>
            <td>{{ formatTime(row.updatedAt) }}</td>
          </tr>
        </tbody>
      </table></div>
      <div v-else-if="!loaded" class="loading-list"><NxSkeleton v-for="index in 3" :key="index" variant="list-row" label="正在加载自用 API 目录" compact /></div>
    </template>
  </main>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { p5 } from "../../api/client";
import { NxEmpty, NxErrorState, NxFilterBar, NxNoPermission, NxPageHeader, NxSkeleton, NxStatusTag } from "../../components";
import { CATALOG_TABS, catalogEntry, catalogRows, formatTime } from "./catalogDisplay";

const route = useRoute();
const router = useRouter();
const rows = ref(null);
const entry = ref(null);
const loaded = ref(false);
const error = ref("");
const traceId = ref("");
const denied = ref(false);
const query = ref("");
const visibleRows = computed(() => {
  const term = query.value.toLocaleLowerCase();
  return (rows.value || []).filter((row) => !term || [row.name, row.httpMethod, row.path].some((value) => String(value || "").toLocaleLowerCase().includes(term)));
});

const tabs = [
  { id: "overview", label: "Overview" },
  { id: "request", label: "Request" },
  { id: "response", label: "Response" },
  { id: "scope", label: "Scope" },
];
const isDetail = computed(() => route.name === "p5-api");
const tab = computed(() => (CATALOG_TABS.includes(String(route.query.tab || "")) ? String(route.query.tab) : "overview"));

function statusLabel(status) {
  return ({ DRAFT: "草稿", ACTIVE: "启用", DEPRECATED: "废弃", RETIRED: "停用" })[status] || status || "暂无";
}

function setTab(id) {
  router.replace({ query: { ...route.query, tab: id } });
}

async function load() {
  error.value = "";
  traceId.value = "";
  denied.value = false;
  loaded.value = false;
  if (isDetail.value) {
    rows.value = null;
    entry.value = null;
    try {
      const body = await p5.apiCatalogEntry(route.params.id);
      const parsed = catalogEntry(body);
      if (!parsed) {
        error.value = "接口没有返回目录记录";
        return;
      }
      entry.value = parsed;
    } catch (err) {
      entry.value = null;
      if (err?.status === 404) {
        return;
      }
      if (err?.status === 403) {
        denied.value = true;
      }
      error.value = err?.message || "自用 API 加载失败";
      traceId.value = err?.traceId || "";
    } finally {
      loaded.value = true;
    }
    return;
  }
  entry.value = null;
  try {
    const body = await p5.apiCatalog();
    const parsed = catalogRows(body);
    if (!parsed) {
      error.value = "接口没有返回自用 API 目录";
      return;
    }
    rows.value = parsed;
  } catch (err) {
    rows.value = null;
    if (err?.status === 403) {
      denied.value = true;
    }
    error.value = err?.message || "自用 API 目录加载失败";
    traceId.value = err?.traceId || "";
  } finally {
    loaded.value = true;
  }
}

watch(() => [route.name, route.params.id], load, { immediate: true });
</script>

<style scoped>
.api-catalog-page { display: grid; gap: var(--nx-space-4); }
.api-catalog-page :deep(.page-header) { margin-bottom: 0; }
.table-scroll { overflow-x: auto; }
.table-scroll .nx-table { min-width: 1040px; }
.loading-list { display: grid; gap: var(--nx-space-2); }
.detail-head { display: flex; align-items: center; gap: 12px; }
.detail-head h2 { margin: 0; }
.method { display: inline-block; min-width: 64px; border-radius: 999px; padding: 2px 8px; font-size: 12px; font-weight: 700; text-align: center; color: var(--nx-text-secondary, #526176); background: var(--nx-surface-muted, #f2f4f7); }
.method[data-method="GET"] { color: var(--nx-info, #2e90fa); background: var(--nx-info-light, #eef6ff); }
.method[data-method="POST"] { color: var(--nx-success, #12b76a); background: var(--nx-success-light, #eafbf3); }
.method[data-method="PUT"] { color: var(--nx-warning, #f79009); background: var(--nx-warning-light, #fff6e8); }
.method[data-method="DELETE"] { color: var(--nx-danger, #f04438); background: var(--nx-danger-light, #fff0ef); }
.tabs { display: flex; gap: 8px; margin: 16px 0; }
.tabs button { border: 0; border-radius: 999px; padding: 6px 12px; background: transparent; cursor: pointer; }
.tabs button.current { background: var(--nx-info-light, #eef6ff); color: var(--nx-info, #2e90fa); }
dl { display: grid; gap: 8px; margin: 0; }
dl div { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: 8px; }
dt { color: var(--nx-text-muted, #718096); }
dd { margin: 0; }
</style>
