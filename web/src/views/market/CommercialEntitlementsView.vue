<template>
  <main>
    <NxPageHeader eyebrow="P4 · 星市" title="商业权益" description="只读投影当前租户的订阅、SKU 权益和安装状态。本页不编辑运行时投影。">
      <template v-if="isDetail" #actions>
        <router-link class="ghost" to="/p4/entitlements">返回列表</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="load" />

    <template v-else-if="isDetail">
      <NxEmpty v-if="loaded && !item" title="暂无" description="接口没有返回这条商业权益。" />
      <section v-else-if="item" class="nx-card detail">
        <h2>{{ show(item.skuName) }}</h2>
        <dl>
          <div><dt>SKU</dt><dd>{{ show(item.skuCode) }}</dd></div>
          <div><dt>订阅状态</dt><dd><NxStatusTag :status="item.subscriptionStatus || ''" :label="statusLabel(item.subscriptionStatus)" /></dd></div>
          <div><dt>安装状态</dt><dd><NxStatusTag :status="item.installationStatus || ''" :label="statusLabel(item.installationStatus)" /></dd></div>
        </dl>
        <h3>权益项</h3>
        <p v-if="!item.lines.length" class="nx-muted">暂无权益项</p>
        <table v-else class="nx-table">
          <thead><tr><th>权益项</th><th>是否启用</th><th>配额</th></tr></thead>
          <tbody>
            <tr v-for="line in item.lines" :key="line.key">
              <td>{{ show(line.key) }}</td>
              <td>{{ line.enabled === true ? "启用" : line.enabled === false ? "停用" : "暂无" }}</td>
              <td>{{ quotaLabel(line.quota) }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>

    <template v-else>
      <NxEmpty v-if="rows && !rows.length" title="暂无" description="当前租户还没有可投影的商业权益。" />
      <table v-else-if="listed.length" class="nx-table">
        <thead><tr><th>SKU</th><th>订阅状态</th><th>安装状态</th><th>权益项</th><th></th></tr></thead>
        <tbody>
          <tr v-for="row in listed" :key="row.subscriptionId || row.skuCode">
            <td>{{ show(row.skuName) }}<div class="nx-muted">{{ show(row.skuCode) }}</div></td>
            <td><NxStatusTag :status="row.subscriptionStatus || ''" :label="statusLabel(row.subscriptionStatus)" /></td>
            <td><NxStatusTag :status="row.installationStatus || ''" :label="statusLabel(row.installationStatus)" /></td>
            <td>{{ row.lines.length ? row.lines.map((line) => line.key).join("、") : "暂无" }}</td>
            <td><router-link v-if="row.subscriptionId" :to="`/p4/entitlements/${row.subscriptionId}`">查看</router-link><template v-else>暂无</template></td>
          </tr>
        </tbody>
      </table>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatusTag } from "../../components";
import { p4 } from "../../api/client";

const route = useRoute();
const rows = ref(null);
const detail = ref(null);
const loaded = ref(false);
const error = ref("");
const traceId = ref("");

const isDetail = computed(() => route.name === "p4-entitlement");
const listed = computed(() => (rows.value || []).map(fields).filter(Boolean));
const item = computed(() => fields(detail.value));

function pick(row, names) {
  for (const name of names) {
    const value = row?.[name];
    if (value != null && value !== "") return value;
  }
  return null;
}

function fields(row) {
  if (!row || typeof row !== "object" || Array.isArray(row)) return null;
  const lines = Array.isArray(row.lines) ? row.lines : [];
  return {
    subscriptionId: pick(row, ["subscription_id", "subscriptionId"]),
    skuCode: pick(row, ["sku_code", "skuCode"]),
    skuName: pick(row, ["sku_name", "skuName"]),
    subscriptionStatus: pick(row, ["subscription_status", "subscriptionStatus"]),
    installationStatus: pick(row, ["installation_status", "installationStatus"]),
    lines: lines.filter((line) => line && typeof line === "object").map((line) => ({
      key: pick(line, ["key"]),
      enabled: typeof line.enabled === "boolean" ? line.enabled : null,
      quota: Number.isInteger(line.quota) ? line.quota : null,
    })),
  };
}

function show(value) {
  return value == null || value === "" ? "暂无" : String(value);
}

function statusLabel(status) {
  if (status === "ACTIVE") return "有效";
  if (status === "CANCELLED") return "已取消";
  if (status === "INSTALLED") return "已安装";
  if (status === "UNINSTALLED") return "已卸载";
  return show(status);
}

function quotaLabel(quota) {
  return Number.isInteger(quota) ? String(quota) : "暂无";
}

async function load() {
  error.value = "";
  traceId.value = "";
  loaded.value = false;
  if (isDetail.value) {
    rows.value = null;
    try {
      detail.value = await p4.commercialEntitlement(route.params.id);
    } catch (err) {
      detail.value = null;
      error.value = err.message || "加载失败";
      traceId.value = err.traceId || "";
    } finally {
      loaded.value = true;
    }
    return;
  }
  detail.value = null;
  try {
    const body = await p4.commercialEntitlements();
    rows.value = Array.isArray(body) ? body : null;
    if (!rows.value) error.value = "接口没有返回商业权益列表";
  } catch (err) {
    rows.value = null;
    error.value = err.message || "加载失败";
    traceId.value = err.traceId || "";
  } finally {
    loaded.value = true;
  }
}

watch(() => route.fullPath, load);
onMounted(load);
</script>

<style scoped>
.detail { margin-top: var(--nx-space-4); }
.detail h2, .detail h3 { margin-top: 0; font-size: 16px; }
dl { display: grid; gap: var(--nx-space-3); margin: 0 0 16px; }
dl div { display: grid; grid-template-columns: 120px 1fr; gap: var(--nx-space-3); }
dt { color: var(--nx-text-secondary); }
dd { margin: 0; overflow-wrap: anywhere; }
</style>
