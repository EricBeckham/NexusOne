<template>
  <main>
    <NxPageHeader
      eyebrow="P5 · NEXUS CONNECT"
      title="星联工作台"
      description="查看自用连接器及其运行情况，管理入口保留在接入总览。"
    >
      <template #actions>
        <router-link class="primary" to="/p5/connect">打开接入总览</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <section v-else class="stats" aria-label="星联运行摘要">
      <NxStatCard label="可用连接器" :value="enabledConnectorCount" />
      <NxStatCard label="运行记录" :value="runs.length" />
      <NxStatCard label="最近运行结果" :value="latestRunStatus" :tone="latestRunTone" />
    </section>

    <section class="nx-card connector-list">
      <h2>自用连接器</h2>
      <NxEmpty
        v-if="!loading && !connectors.length && !error"
        title="暂无可用连接器"
        description="连接器启用后会显示在这里。"
        compact
      />
      <ul v-else-if="connectors.length">
        <li v-for="item in connectors" :key="item.code">
          <div>
            <strong>{{ item.name }}</strong>
            <span class="code">{{ item.code }} · {{ item.type }}</span>
          </div>
          <NxStatusTag :status="item.active ? 'ACTIVE' : 'DISABLED'" />
        </li>
      </ul>
      <p v-else-if="loading" class="nx-muted">正在加载连接器…</p>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatCard, NxStatusTag } from "../../components";
import { p5 } from "../../api/client";

const connectors = ref([]);
const runs = ref([]);
const error = ref("");
const loading = ref(false);
const enabledConnectorCount = computed(() => connectors.value.filter((item) => item.active).length);
const latestRun = computed(() => runs.value[0] || null);
const latestRunStatus = computed(() => latestRun.value?.status || null);
const latestRunTone = computed(() => {
  if (!latestRun.value) return "neutral";
  return ["SUCCEEDED", "SUCCESS", "COMPLETED"].includes(String(latestRun.value.status).toUpperCase())
    ? "success"
    : ["FAILED", "ERROR"].includes(String(latestRun.value.status).toUpperCase()) ? "danger" : "warning";
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    [connectors.value, runs.value] = await Promise.all([p5.connectors(), p5.connectorRuns()]);
  } catch (err) {
    error.value = err.message || "星联数据暂时不可用";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--nx-space-4);
  margin-bottom: var(--nx-space-4);
}
.connector-list h2 { margin: var(--nx-space-0); font-size: var(--nx-font-size-md); }
ul { list-style: none; margin: var(--nx-space-3) var(--nx-space-0) var(--nx-space-0); padding: var(--nx-space-0); }
li { display: flex; align-items: center; justify-content: space-between; gap: var(--nx-space-3); padding: var(--nx-space-3) var(--nx-space-0); border-top: 1px solid var(--nx-divider); }
li > div { display: flex; flex-direction: column; gap: var(--nx-space-1); }
.code { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
</style>
