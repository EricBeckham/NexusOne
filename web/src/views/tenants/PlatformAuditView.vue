<template>
  <NxPageHeader eyebrow="星域 / 平台治理" title="平台审计中心" description="检索平台审计事件。结果为只读摘要，不包含事件详情载荷。哈希链校验只返回是否完整、校验条数和失败原因。" />

  <NxPanelCard title="哈希链" eyebrow="平台校验" description="校验当前平台审计链，不展示事件载荷。">
    <button class="primary" type="button" data-testid="verify-chain" :disabled="verifying" @click="verifyChain">{{ verifying ? "校验中…" : "校验哈希链" }}</button>
    <p v-if="chainError" class="nx-error" role="alert">{{ chainError }}</p>
    <dl v-else-if="chainResult" class="chain-result">
      <div><dt>结果</dt><dd>{{ chainResult.intact ? "链完整" : "链已破坏" }}</dd></div>
      <div><dt>校验条数</dt><dd>{{ chainResult.checked == null ? "暂无" : chainResult.checked }}</dd></div>
      <div><dt>失败原因</dt><dd>{{ chainResult.failure || "暂无" }}</dd></div>
    </dl>
  </NxPanelCard>

  <NxPanelCard title="筛选条件" eyebrow="Filter">
    <form class="filters" @submit.prevent="applyFilters">
      <label>开始时间<input v-model="filters.from" aria-label="开始时间" type="datetime-local" /></label>
      <label>结束时间<input v-model="filters.to" aria-label="结束时间" type="datetime-local" /></label>
      <label>租户 ID<input v-model.trim="filters.tenantId" aria-label="租户 ID" type="text" autocomplete="off" /></label>
      <label>Actor ID<input v-model.trim="filters.actor" aria-label="Actor ID" type="text" autocomplete="off" /></label>
      <label>事件 / Action<input v-model.trim="filters.action" aria-label="事件 / Action" type="text" autocomplete="off" /></label>
      <label>资源类型或 ID<input v-model.trim="filters.resource" aria-label="资源类型或 ID" type="text" autocomplete="off" /></label>
      <label>结果
        <select v-model="filters.result" aria-label="结果">
          <option value="">全部</option>
          <option value="ACCEPTED">成功</option>
          <option value="REJECTED">拒绝</option>
        </select>
      </label>
      <label>Trace ID<input v-model.trim="filters.traceId" aria-label="Trace ID" type="text" autocomplete="off" /></label>
      <div class="filter-actions">
        <button class="primary" type="submit" :disabled="loading">查询</button>
        <button class="ghost" type="button" :disabled="loading" @click="clearFilters">清空</button>
      </div>
    </form>
  </NxPanelCard>

  <NxPanelCard title="审计事件" eyebrow="只读检索" :description="result ? `共 ${result.total} 条 · 第 ${result.page} 页` : '暂无检索结果'">
    <NxErrorState v-if="error" :title="error" :trace-id="traceId" @retry="load" />
    <NxEmpty v-else-if="result && !result.items.length" title="暂无" description="没有符合当前筛选条件的审计事件。" />
    <div v-else-if="result" class="table-wrap">
      <table class="nx-table audit-table">
        <thead><tr><th>时间</th><th>租户</th><th>Actor</th><th>事件</th><th>资源</th><th>结果</th><th>Trace ID</th><th></th></tr></thead>
        <tbody>
          <tr v-for="event in result.items" :key="event.eventId">
            <td>{{ formatTime(event.occurredAt) }}</td>
            <td>{{ show(event.tenantId) }}</td>
            <td>{{ show(event.actorId) }}</td>
            <td>{{ show(event.action) }}</td>
            <td>{{ resourceLabel(event) }}</td>
            <td><span class="result-pill" :data-result="event.result">{{ resultLabel(event.result) }}</span></td>
            <td class="trace-cell">{{ show(event.traceId) }}</td>
            <td><button class="text-button" type="button" @click="selected = event">详情</button></td>
          </tr>
        </tbody>
      </table>
      <NxListFooter
        :page="result.page"
        :page-count="pageCount"
        :total="result.total"
        :loading="loading"
        aria-label="审计结果分页"
        @prev="goPage(result.page - 1)"
        @next="goPage(result.page + 1)"
      />
    </div>
    <p v-else-if="loading" class="loading-note" role="status">正在检索审计事件…</p>
  </NxPanelCard>

  <div v-if="selected" class="drawer-backdrop" @click.self="selected = null">
    <aside class="drawer" role="dialog" aria-modal="true" aria-label="审计事件摘要详情">
      <header class="drawer-header">
        <div><p class="drawer-eyebrow">只读摘要</p><h2>审计事件</h2></div>
        <button class="ghost" type="button" aria-label="关闭详情" @click="selected = null">关闭</button>
      </header>
      <dl class="event-facts">
        <div><dt>Event ID</dt><dd>{{ show(selected.eventId) }}</dd></div>
        <div><dt>发生时间</dt><dd>{{ formatTime(selected.occurredAt) }}</dd></div>
        <div><dt>租户 ID</dt><dd>{{ show(selected.tenantId) }}</dd></div>
        <div><dt>Actor ID</dt><dd>{{ show(selected.actorId) }}</dd></div>
        <div><dt>Action</dt><dd>{{ show(selected.action) }}</dd></div>
        <div><dt>资源类型</dt><dd>{{ show(selected.resourceType) }}</dd></div>
        <div><dt>资源 ID</dt><dd>{{ show(selected.resourceId) }}</dd></div>
        <div><dt>结果</dt><dd>{{ resultLabel(selected.result) }}</dd></div>
        <div><dt>Trace ID</dt><dd>{{ show(selected.traceId) }}</dd></div>
        <div><dt>平台上下文</dt><dd>{{ selected.platformContext === true ? "是" : selected.platformContext === false ? "否" : "暂无" }}</dd></div>
      </dl>
      <p class="privacy-note">为保护审计载荷，详情面板不展示 details、凭据或敏感 Payload。</p>
    </aside>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { audit } from "../../api/client";
import { NxEmpty, NxErrorState, NxListFooter, NxPageHeader, NxPanelCard } from "../../components";

const PAGE_SIZE = 20;
const route = useRoute();
const router = useRouter();
const filters = reactive({ from: "", to: "", tenantId: "", actor: "", action: "", resource: "", result: "", traceId: "" });
const result = ref(null);
const error = ref("");
const traceId = ref("");
const loading = ref(false);
const selected = ref(null);
const verifying = ref(false);
const chainResult = ref(null);
const chainError = ref("");
const pageCount = computed(() => Math.max(1, Math.ceil((result.value?.total || 0) / PAGE_SIZE)));

function queryValue(value) {
  return Array.isArray(value) ? String(value[0] || "") : String(value || "");
}

function dateInput(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (part) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function instant(value) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString();
}

function syncFromUrl() {
  filters.from = dateInput(queryValue(route.query.from));
  filters.to = dateInput(queryValue(route.query.to));
  filters.tenantId = queryValue(route.query.tenantId);
  filters.actor = queryValue(route.query.actor);
  filters.action = queryValue(route.query.action);
  filters.resource = queryValue(route.query.resource);
  filters.result = queryValue(route.query.result);
  filters.traceId = queryValue(route.query.traceId);
}

function currentPage() {
  const page = Number.parseInt(queryValue(route.query.page), 10);
  return Number.isSafeInteger(page) && page > 0 ? page : 1;
}

function urlQuery(page = 1) {
  const query = { page: String(page), size: String(PAGE_SIZE) };
  const values = {
    from: instant(filters.from), to: instant(filters.to), tenantId: filters.tenantId,
    actor: filters.actor, action: filters.action, resource: filters.resource,
    result: filters.result, traceId: filters.traceId,
  };
  for (const [key, value] of Object.entries(values)) if (value) query[key] = value;
  return query;
}

function apiFilters() {
  return {
    ...urlQuery(currentPage()),
  };
}

async function verifyChain() {
  verifying.value = true;
  chainError.value = "";
  try {
    const response = await audit.verifyChain();
    const checked = Number(response?.checked);
    chainResult.value = {
      intact: response?.intact === true,
      checked: Number.isFinite(checked) ? checked : null,
      failure: typeof response?.failure === "string" ? response.failure : "",
    };
  } catch (err) {
    chainResult.value = null;
    chainError.value = err?.message || "哈希链校验失败";
  } finally {
    verifying.value = false;
  }
}

async function load() {
  const generation = Symbol("audit-search");
  load.generation = generation;
  loading.value = true;
  error.value = "";
  traceId.value = "";
  result.value = null;
  try {
    const response = await audit.platformEvents(apiFilters());
    if (load.generation !== generation) return;
    result.value = {
      items: Array.isArray(response?.items) ? response.items : [],
      total: Number.isFinite(Number(response?.total)) ? Number(response.total) : 0,
      page: Number(response?.page) || currentPage(),
    };
  } catch (err) {
    if (load.generation !== generation) return;
    error.value = err?.message || "审计事件加载失败";
    traceId.value = err?.traceId || "";
  } finally {
    if (load.generation === generation) loading.value = false;
  }
}

async function navigate(page) {
  await router.push({ name: "p1-audit", query: urlQuery(page) });
}

function applyFilters() {
  return navigate(1);
}

function clearFilters() {
  Object.keys(filters).forEach((key) => { filters[key] = ""; });
  return navigate(1);
}

function goPage(page) {
  return navigate(Math.max(1, page));
}

function show(value) {
  return value === null || value === undefined || value === "" ? "暂无" : String(value);
}

function formatTime(value) {
  if (!value) return "暂无";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "暂无" : date.toLocaleString("zh-CN", { hour12: false });
}

function resourceLabel(event) {
  const type = event.resourceType || "";
  const id = event.resourceId || "";
  return type && id ? `${type} / ${id}` : show(type || id);
}

function resultLabel(value) {
  return ({ ACCEPTED: "成功", REJECTED: "拒绝" })[value] || show(value);
}

onMounted(() => {
  syncFromUrl();
  load();
});
watch(() => route.fullPath, () => {
  syncFromUrl();
  load();
});
</script>

<style scoped>
.filters { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 14px; align-items: end; }
.filters label { display: flex; flex-direction: column; gap: 6px; color: var(--nx-text-secondary); font-size: 12px; }
.filters input, .filters select { width: 100%; min-height: 38px; box-sizing: border-box; padding: 7px 10px; color: var(--nx-text-primary); background: var(--nx-surface); border: 1px solid var(--nx-border); border-radius: 7px; font: inherit; }
.filter-actions { display: flex; gap: 8px; }
.primary, .ghost, .text-button { min-height: 36px; padding: 0 12px; border-radius: 7px; cursor: pointer; }
.primary { color: #fff; background: var(--nx-product, #246bfd); border: 1px solid var(--nx-product, #246bfd); }
.ghost { color: var(--nx-text-regular); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); }
.text-button { min-height: auto; padding: 4px 6px; color: var(--nx-product, #246bfd); background: transparent; border: 0; }
button:disabled { opacity: .55; cursor: not-allowed; }
.table-wrap { overflow-x: auto; }
.audit-table { min-width: 1000px; }
.chain-result { display: grid; gap: 8px; margin: 12px 0 0; }
.chain-result div { display: grid; grid-template-columns: 96px 1fr; gap: 8px; }
.chain-result dd { margin: 0; overflow-wrap: anywhere; }
.trace-cell { max-width: 180px; overflow-wrap: anywhere; }
.result-pill { display: inline-flex; padding: 3px 8px; border-radius: 999px; color: #58677c; background: #edf1f6; font-size: 12px; white-space: nowrap; }
.result-pill[data-result="ACCEPTED"] { color: #18794e; background: #e7f7ee; }
.result-pill[data-result="REJECTED"] { color: #b42318; background: #fff0ee; }
.drawer-backdrop { position: fixed; z-index: 1200; inset: 0; display: flex; justify-content: flex-end; background: rgb(12 25 45 / 38%); }
.drawer { width: min(520px, 100%); height: 100%; box-sizing: border-box; overflow-y: auto; padding: 26px; background: var(--nx-surface, #fff); box-shadow: -12px 0 36px rgb(20 37 63 / 16%); }
.drawer-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; padding-bottom: 20px; border-bottom: 1px solid var(--nx-border); }
.drawer-header h2 { margin: 0; color: var(--nx-text-primary); font-size: 20px; }
.drawer-eyebrow { margin: 0 0 6px; color: var(--nx-text-secondary); font-size: 12px; }
.event-facts { display: grid; grid-template-columns: 1fr; margin: 0; }
.event-facts div { display: grid; grid-template-columns: 125px minmax(0, 1fr); gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--nx-border); }
.event-facts dt { color: var(--nx-text-secondary); }
.event-facts dd { margin: 0; color: var(--nx-text-primary); overflow-wrap: anywhere; }
.privacy-note { margin-top: 20px; padding: 12px; color: var(--nx-text-secondary); background: var(--nx-bg-soft, #f5f8fc); border-radius: 8px; font-size: 12px; line-height: 1.6; }
.loading-note { padding: 24px 0; color: var(--nx-text-secondary); text-align: center; }
@media (max-width: 900px) { .filters { grid-template-columns: repeat(2, minmax(140px, 1fr)); } }
@media (max-width: 560px) { .filters { grid-template-columns: 1fr; } }
</style>
