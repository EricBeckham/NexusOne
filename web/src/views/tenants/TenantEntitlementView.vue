<template>
  <NxPageHeader eyebrow="星域" title="权益与配额" :description="description">
    <template v-if="tenantId" #actions>
      <router-link class="ghost" :to="`/p1/tenants/${tenantId}`">租户详情</router-link>
    </template>
  </NxPageHeader>

  <template v-if="!tenantId">
    <NxErrorState v-if="listError" :title="listError" :trace-id="listTrace" @retry="loadList" />
    <NxEmpty v-else-if="tenants && !tenants.length" title="暂无" description="还没有可查看的租户。" />
    <table v-else-if="tenants" class="nx-table">
      <thead>
        <tr><th>编码</th><th>名称</th><th>状态</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="row in tenants" :key="row.id">
          <td>{{ show(row.code) }}</td>
          <td>{{ show(row.name) }}</td>
          <td>{{ show(row.status) }}</td>
          <td>
            <router-link v-if="row.id" :to="`/p1/tenants/${row.id}/entitlement`">查看</router-link>
            <span v-else>暂无</span>
          </td>
        </tr>
      </tbody>
    </table>
  </template>

  <div v-else class="layers">
    <NxPanelCard title="Runtime Entitlement" eyebrow="权益投影" description="只读现有投影。本页不提供 SKU 编辑。">
      <NxErrorState v-if="entitlementError" :title="entitlementError" :trace-id="entitlementTrace" @retry="loadDetail" />
      <NxEmpty v-else-if="!hasProjection" title="暂无" description="这个租户没有权益投影。" />
      <template v-else>
        <dl class="facts">
          <div><dt>生效来源</dt><dd>{{ show(summary?.source) }}</dd></div>
          <div><dt>生效时间</dt><dd>{{ show(summary?.projectedAt) }}</dd></div>
          <div><dt>权益包</dt><dd>{{ show(summary?.packCode) }} {{ show(summary?.packVersion) }}</dd></div>
          <div><dt>SKU</dt><dd data-testid="sku-readonly">{{ show(summary?.skuCode) }}</dd></div>
          <div><dt>Schema</dt><dd>{{ show(summary?.schema) }}</dd></div>
        </dl>
        <div v-if="items.length" class="card-grid">
          <article v-for="item in items" :key="item.key" class="quota-card">
            <h3>{{ item.key }}</h3>
            <dl>
              <div><dt>启用</dt><dd>{{ item.enabled === null ? "暂无" : item.enabled ? "是" : "否" }}</dd></div>
              <div><dt>上限</dt><dd>{{ show(item.quota) }}</dd></div>
              <div><dt>使用量</dt><dd>暂无</dd></div>
            </dl>
          </article>
        </div>
      </template>
    </NxPanelCard>

    <NxPanelCard title="Quota / Usage" eyebrow="配额用量" description="已用和上限来自配额接口。接口没有来源或时间时显示暂无。">
      <NxErrorState v-if="quotaError" :title="quotaError" :trace-id="quotaTrace" @retry="loadDetail" />
      <NxEmpty v-else-if="!quotas" title="暂无" description="接口没有返回配额列表。" />
      <NxEmpty v-else-if="!quotas.length" title="暂无" description="这个租户没有配额记录。" />
      <div v-else class="card-grid">
        <article v-for="row in quotas" :key="row.metric || row.used" class="quota-card" data-testid="quota-card">
          <h3>{{ show(row.metric) }}</h3>
          <div
            v-if="usagePercent(row.used, row.limit) !== null"
            class="meter"
            :data-tone="meterTone(row)"
            role="meter"
            :aria-valuenow="usagePercent(row.used, row.limit)"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-label="`${show(row.metric)} 已用占上限`"
          >
            <span :style="{ width: `${usagePercent(row.used, row.limit)}%` }" />
          </div>
          <p v-else>暂无</p>
          <dl>
            <div><dt>已用</dt><dd>{{ show(row.used) }}</dd></div>
            <div><dt>上限</dt><dd>{{ show(row.limit) }}</dd></div>
            <div><dt>预警线</dt><dd>{{ show(row.warnPercent) }}</dd></div>
            <div><dt>超限策略</dt><dd>{{ show(row.policy) }}</dd></div>
            <div><dt>预警</dt><dd>{{ row.warning === null ? "暂无" : row.warning ? "是" : "否" }}</dd></div>
            <div><dt>来源</dt><dd>暂无</dd></div>
            <div><dt>时间</dt><dd>暂无</dd></div>
          </dl>
        </article>
      </div>
    </NxPanelCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { p1 } from "../../api/client";
import { NxEmpty, NxErrorState, NxPageHeader, NxPanelCard } from "../../components";
import { entitlementItems, entitlementSummary, quotaRows, show, usagePercent } from "./entitlementDisplay";

const route = useRoute();
const tenantId = computed(() => (route.name === "tenant-entitlement" ? route.params.id : ""));
const description = computed(() => (
  tenantId.value ? `租户 ${tenantId.value}` : "选择租户查看权益投影和配额用量。"
));
const hasProjection = computed(() => (
  items.value.length > 0 || Object.values(summary.value || {}).some(Boolean)
));

const tenants = ref(null);
const listError = ref("");
const listTrace = ref("");
const summary = ref(null);
const items = ref([]);
const quotas = ref(null);
const entitlementError = ref("");
const entitlementTrace = ref("");
const quotaError = ref("");
const quotaTrace = ref("");

function fail(err) {
  return { message: err?.message || "加载失败", trace: err?.traceId || "" };
}

async function loadList() {
  listError.value = "";
  listTrace.value = "";
  tenants.value = null;
  try {
    const rows = await p1.listTenants();
    tenants.value = Array.isArray(rows) ? rows : [];
  } catch (err) {
    const failed = fail(err);
    listError.value = failed.message;
    listTrace.value = failed.trace;
  }
}

async function loadDetail() {
  entitlementError.value = "";
  entitlementTrace.value = "";
  quotaError.value = "";
  quotaTrace.value = "";
  summary.value = null;
  items.value = [];
  quotas.value = null;
  const id = tenantId.value;
  const [entitlement, quota] = await Promise.allSettled([p1.entitlement(id), p1.quotas(id)]);
  if (entitlement.status === "fulfilled") {
    summary.value = entitlementSummary(entitlement.value);
    items.value = entitlementItems(entitlement.value);
  } else {
    const failed = fail(entitlement.reason);
    entitlementError.value = failed.message;
    entitlementTrace.value = failed.trace;
  }
  if (quota.status === "fulfilled") {
    quotas.value = quotaRows(quota.value);
  } else {
    const failed = fail(quota.reason);
    quotaError.value = failed.message;
    quotaTrace.value = failed.trace;
  }
}

function load() {
  if (tenantId.value) {
    return loadDetail();
  }
  return loadList();
}

function meterTone(row) {
  if (row.warning === true) {
    return "warning";
  }
  if (Number.isFinite(row.used) && Number.isFinite(row.limit) && row.used > row.limit) {
    return "danger";
  }
  return "product";
}

onMounted(load);
watch(tenantId, load);
</script>

<style scoped>
.layers { display: grid; gap: var(--nx-space-4); margin-top: var(--nx-space-4); }
.facts, .quota-card dl {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 140px), 1fr));
  gap: var(--nx-space-3);
  margin: 0;
}
.facts div, .quota-card dl div { min-width: 0; }
dt { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
dd { margin: var(--nx-space-1) 0 0; color: var(--nx-text-primary); font-weight: 650; }
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr));
  gap: var(--nx-space-4);
  margin-top: var(--nx-space-4);
}
.quota-card {
  min-width: 0;
  padding: var(--nx-space-4);
  background: var(--nx-bg-page);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius);
}
.quota-card h3 { margin: 0 0 var(--nx-space-3); font-size: var(--nx-font-size-md); }
.meter {
  height: 8px;
  margin-bottom: var(--nx-space-3);
  overflow: hidden;
  background: var(--nx-divider);
  border-radius: var(--nx-radius-pill);
}
.meter > span { display: block; height: 100%; background: var(--nx-product); }
.meter[data-tone="warning"] > span { background: var(--nx-warning); }
.meter[data-tone="danger"] > span { background: var(--nx-danger); }
.ghost {
  border: 1px solid var(--nx-border-strong);
  border-radius: 6px;
  padding: 8px 12px;
  color: var(--nx-text-regular);
}
</style>
