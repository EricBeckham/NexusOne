<template>
  <main>
    <NxPageHeader
      eyebrow="P5 · NEXUS CONNECT"
      title="Connector"
      description="自用连接器。最近同步时间来自已有运行记录，没有记录时显示暂无。"
    />

    <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="load" />
    <template v-else>
      <NxStatCard label="连接器" :value="count" unit="个" />
      <NxEmpty v-if="connectors && !connectors.length" title="暂无" description="当前没有可用连接器。" />
      <div v-else-if="cards.length" class="cards">
        <article v-for="card in cards" :key="card.code || card.name" class="nx-card">
          <header>
            <h2>{{ show(card.name) }}</h2>
            <NxStatusTag v-if="card.active != null" :status="card.active ? 'ACTIVE' : 'DISABLED'" />
            <span v-else>暂无</span>
          </header>
          <dl>
            <div><dt>编码</dt><dd>{{ show(card.code) }}</dd></div>
            <div><dt>类型</dt><dd>{{ show(card.type) }}</dd></div>
            <div><dt>最近运行</dt><dd>{{ show(card.startedAt) }}</dd></div>
            <div><dt>运行状态</dt><dd><NxStatusTag v-if="card.runStatus" :status="card.runStatus" /><template v-else>暂无</template></dd></div>
            <div><dt>错误</dt><dd>{{ show(card.runError) }}</dd></div>
          </dl>
          <button class="ghost" type="button" :disabled="!card.code || running === card.code" @click="run(card.code)">立即运行</button>
        </article>
      </div>
      <NxErrorState v-if="runError" :title="runError" :trace-id="runTrace" retry-label="重试" @retry="load" />
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatCard, NxStatusTag } from "../../components";
import { p5 } from "../../api/client";
import { asRows, connectorFields, latestRun, runFields, show } from "./connectDisplay";

const connectors = ref(null);
const runs = ref(null);
const error = ref("");
const traceId = ref("");
const runError = ref("");
const runTrace = ref("");
const running = ref("");

const count = computed(() => (connectors.value && connectors.value.length ? connectors.value.length : null));
const cards = computed(() => (connectors.value || []).map((row) => {
  const fields = connectorFields(row);
  if (!fields) return null;
  const recent = runFields(latestRun(runs.value, fields.code));
  return {
    ...fields,
    startedAt: recent?.startedAt || null,
    runStatus: recent?.status || null,
    runError: recent?.error || null,
  };
}).filter(Boolean));

async function load() {
  error.value = "";
  traceId.value = "";
  runError.value = "";
  runTrace.value = "";
  const [connectorResult, runResult] = await Promise.allSettled([p5.connectors(), p5.connectorRuns()]);
  if (connectorResult.status === "rejected") {
    connectors.value = null;
    runs.value = null;
    error.value = connectorResult.reason?.message || "加载失败";
    traceId.value = connectorResult.reason?.traceId || "";
    return;
  }
  connectors.value = asRows(connectorResult.value);
  if (!connectors.value) {
    error.value = "接口没有返回连接器列表";
    runs.value = null;
    return;
  }
  if (runResult.status === "rejected") {
    runs.value = null;
    runError.value = runResult.reason?.message || "运行记录加载失败";
    runTrace.value = runResult.reason?.traceId || "";
    return;
  }
  runs.value = asRows(runResult.value);
  if (!runs.value) {
    runError.value = "接口没有返回运行记录";
  }
}

async function run(code) {
  running.value = code;
  runError.value = "";
  runTrace.value = "";
  try {
    await p5.runConnector(code);
    await load();
  } catch (err) {
    runError.value = err.message || "运行失败";
    runTrace.value = err.traceId || "";
  } finally {
    running.value = "";
  }
}

onMounted(load);
</script>

<style scoped>
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: var(--nx-space-4); margin-top: var(--nx-space-4); }
article header { display: flex; justify-content: space-between; gap: var(--nx-space-3); align-items: center; }
article h2 { margin: 0; font-size: 16px; }
dl { display: grid; gap: var(--nx-space-2); margin: var(--nx-space-3) 0; }
dl div { display: grid; grid-template-columns: 88px 1fr; gap: var(--nx-space-3); }
dt { color: var(--nx-text-secondary); }
dd { margin: 0; overflow-wrap: anywhere; }
</style>
