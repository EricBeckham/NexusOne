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

  <template v-else>
    <section class="block">
      <h2>权益投影</h2>
      <NxErrorState v-if="entitlementError" :title="entitlementError" :trace-id="entitlementTrace" @retry="loadDetail" />
      <NxEmpty v-else-if="!hasProjection" title="暂无" description="这个租户没有权益投影。" />
      <div v-else class="nx-card">
        <p><span>来源</span><strong>{{ show(summary?.source) }}</strong></p>
        <p><span>权益包</span><strong>{{ show(summary?.packCode) }} {{ show(summary?.packVersion) }}</strong></p>
        <p><span>SKU</span><strong>{{ show(summary?.skuCode) }}</strong></p>
        <p><span>Schema</span><strong>{{ show(summary?.schema) }}</strong></p>
        <p><span>投影时间</span><strong>{{ show(summary?.projectedAt) }}</strong></p>
        <table v-if="items.length" class="nx-table">
          <thead><tr><th>项</th><th>启用</th><th>配额</th></tr></thead>
          <tbody>
            <tr v-for="item in items" :key="item.key">
              <td>{{ item.key }}</td>
              <td>{{ item.enabled === null ? "暂无" : item.enabled ? "是" : "否" }}</td>
              <td>{{ show(item.quota) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section class="block">
      <h2>配额用量</h2>
      <NxErrorState v-if="quotaError" :title="quotaError" :trace-id="quotaTrace" @retry="loadDetail" />
      <NxEmpty v-else-if="!quotas" title="暂无" description="接口没有返回配额列表。" />
      <NxEmpty v-else-if="!quotas.length" title="暂无" description="这个租户没有配额记录。" />
      <table v-else-if="quotas" class="nx-table">
        <thead>
          <tr><th>指标</th><th>已用</th><th>上限</th><th>预警线</th><th>超限策略</th><th>预警</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in quotas" :key="row.metric || row.used">
            <td>{{ show(row.metric) }}</td>
            <td>{{ show(row.used) }}</td>
            <td>{{ show(row.limit) }}</td>
            <td>{{ show(row.warnPercent) }}</td>
            <td>{{ show(row.policy) }}</td>
            <td>{{ row.warning === null ? "暂无" : row.warning ? "是" : "否" }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </template>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { p1 } from "../../api/client";
import { NxEmpty, NxErrorState, NxPageHeader } from "../../components";
import { entitlementItems, entitlementSummary, quotaRows, show } from "./entitlementDisplay";

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

onMounted(load);
watch(tenantId, load);
</script>

<style scoped>
.block { margin-top: 20px; }
.block h2 { margin: 0 0 12px; font-size: 16px; }
.nx-card p { display: flex; justify-content: space-between; gap: 12px; margin: 0 0 8px; }
.nx-card span { color: var(--nx-text-secondary); }
.nx-card .nx-table { margin-top: 12px; }
.ghost {
  border: 1px solid var(--nx-border-strong);
  border-radius: 6px;
  padding: 8px 12px;
  color: var(--nx-text-regular);
}
</style>
