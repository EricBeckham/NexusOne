<template>
  <main>
    <NxPageHeader
      eyebrow="P5 · NEXUS CONNECT"
      title="调用日志"
      description="只显示调用元数据。请求体和响应体不在此列出。"
    />

    <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="load" />
    <template v-else>
      <NxStatCard label="调用记录" :value="count" unit="条" />
      <form class="filters" @submit.prevent>
        <label>时间<input v-model="filters.time" /></label>
        <label>Client<input v-model="filters.client" /></label>
        <label>API<input v-model="filters.api" /></label>
        <label>Method<input v-model="filters.method" /></label>
        <label>状态<input v-model="filters.status" /></label>
        <label>Trace ID<input v-model="filters.trace" /></label>
      </form>
      <NxEmpty v-if="rows && !filtered.length" title="暂无" description="没有可显示的调用记录。" />
      <table v-else-if="filtered.length" class="nx-table">
        <thead>
          <tr><th>时间</th><th>Client</th><th>Method</th><th>API</th><th>状态</th><th>Scope</th><th>Trace ID</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in filtered" :key="row.id || `${row.traceId}-${row.createdAt}`">
            <td>{{ show(row.createdAt) }}</td>
            <td>{{ show(row.clientId) }}</td>
            <td><NxStatusTag v-if="row.httpMethod" :status="row.httpMethod" :label="row.httpMethod" :tone="methodTone(row.httpMethod)" /><template v-else>暂无</template></td>
            <td>{{ show(row.apiPath) }}</td>
            <td><NxStatusTag v-if="row.status" :status="row.status" /><template v-else>暂无</template></td>
            <td>{{ show(row.scope) }}</td>
            <td>{{ show(row.traceId) }}</td>
          </tr>
        </tbody>
      </table>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatCard, NxStatusTag } from "../../components";
import { p5 } from "../../api/client";
import { asRows, callLogFields, methodTone, show } from "./connectDisplay";

const rows = ref(null);
const error = ref("");
const traceId = ref("");
const filters = reactive({ time: "", client: "", api: "", method: "", status: "", trace: "" });

const listed = computed(() => (rows.value || []).map(callLogFields).filter(Boolean));
const count = computed(() => (rows.value && rows.value.length ? rows.value.length : null));
const filtered = computed(() => listed.value.filter((row) => {
  const time = filters.time.trim();
  const client = filters.client.trim();
  const api = filters.api.trim();
  const method = filters.method.trim().toUpperCase();
  const status = filters.status.trim().toUpperCase();
  const trace = filters.trace.trim();
  if (time && !String(row.createdAt || "").includes(time)) return false;
  if (client && !String(row.clientId || "").includes(client)) return false;
  if (api && !String(row.apiPath || "").includes(api)) return false;
  if (method && String(row.httpMethod || "").toUpperCase() !== method) return false;
  if (status && String(row.status || "").toUpperCase() !== status) return false;
  if (trace && !String(row.traceId || "").includes(trace)) return false;
  return true;
}));

async function load() {
  error.value = "";
  traceId.value = "";
  try {
    const body = await p5.callLogs();
    rows.value = asRows(body);
    if (!rows.value) {
      error.value = "接口没有返回调用日志";
    }
  } catch (err) {
    rows.value = null;
    error.value = err.message || "加载失败";
    traceId.value = err.traceId || "";
  }
}

onMounted(load);
</script>

<style scoped>
.filters { display: flex; flex-wrap: wrap; gap: var(--nx-space-3); margin: var(--nx-space-4) 0; }
.filters label { display: grid; gap: 4px; color: var(--nx-text-secondary); font-size: 12px; }
.filters input { min-width: 140px; }
</style>
