<template>
  <main>
    <NxPageHeader
      eyebrow="P5 · NEXUS CONNECT"
      title="星联工作台"
      description="自用连接器、API Client、Webhook、调用日志、自用 API 目录与凭证。"
    />

    <nav class="quick-actions" aria-label="星联快捷入口">
      <router-link class="primary" to="/p5/connect">接入总览</router-link>
      <router-link class="ghost" to="/p5/apis">自用 API 目录</router-link>
      <router-link class="ghost" to="/p5/clients">API Client</router-link>
      <router-link class="ghost" to="/p5/webhooks">Webhook</router-link>
      <router-link class="ghost" to="/p5/connectors">Connector</router-link>
      <router-link class="ghost" to="/p5/credentials">Credential</router-link>
      <router-link class="ghost" to="/p5/call-logs">调用日志</router-link>
    </nav>

    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <DashboardLayers>
      <template #kpi>
        <section v-if="!error" class="stats" aria-label="星联运行摘要">
          <NxStatCard label="可用连接器" :value="loading ? null : enabledConnectorCount" />
          <NxStatCard label="运行记录" :value="loading ? null : runs.length" />
          <NxStatCard label="最近运行结果" :value="loading ? null : latestRunStatus" :tone="latestRunTone" />
        </section>
      </template>
      <template #charts>
        <NxPanelCard title="调用趋势" description="没有趋势聚合接口。">
          <NxChartPlaceholder title="调用趋势" />
        </NxPanelCard>
        <NxPanelCard title="连接器构成" description="没有构成聚合接口。">
          <NxChartPlaceholder title="连接器构成" />
        </NxPanelCard>
        <NxPanelCard title="运行状态" description="没有状态分布接口。">
          <NxChartPlaceholder title="运行状态" />
        </NxPanelCard>
      </template>
      <template #lists>
        <NxPanelCard title="自用连接器">
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
        </NxPanelCard>
        <NxPanelCard title="运行摘要">
          <p v-if="loading" class="nx-muted">正在加载运行记录…</p>
          <NxActivityTimeline v-else-if="!error" :items="runActivity" />
        </NxPanelCard>
        <NxPanelCard title="运行健康">
          <NxHealthList :items="[]" />
        </NxPanelCard>
      </template>
      <template #table>
        <NxPanelCard title="连接器" description="只展示已返回的连接器。">
          <template #actions>
            <router-link to="/p5/connectors">查看全部</router-link>
          </template>
          <NxEmpty v-if="!loading && !connectors.length && !error" title="暂无" description="当前没有可展示的连接器。" compact />
          <table v-else-if="connectors.length" class="nx-table nx-table--compact">
            <thead>
              <tr><th scope="col">名称</th><th scope="col">编码</th><th scope="col">类型</th><th scope="col">状态</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in connectors.slice(0, 8)" :key="item.code">
                <td>{{ item.name || "暂无" }}</td>
                <td>{{ item.code || "暂无" }}</td>
                <td>{{ item.type || "暂无" }}</td>
                <td><NxStatusTag :status="item.active ? 'ACTIVE' : 'DISABLED'" :label="item.active ? '启用' : '停用'" /></td>
              </tr>
            </tbody>
          </table>
        </NxPanelCard>
      </template>
    </DashboardLayers>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import {
  NxActivityTimeline,
  NxChartPlaceholder,
  NxEmpty,
  NxErrorState,
  NxHealthList,
  NxPageHeader,
  NxPanelCard,
  NxStatCard,
  NxStatusTag,
} from "../../components";
import { p5 } from "../../api/client";
import DashboardLayers from "../DashboardLayers.vue";

const connectors = ref([]);
const runs = ref([]);
const error = ref("");
const loading = ref(true);
const enabledConnectorCount = computed(() => connectors.value.filter((item) => item.active).length);
const latestRun = computed(() => runs.value[0] || null);
const latestRunStatus = computed(() => latestRun.value?.status || null);
const latestRunTone = computed(() => {
  if (!latestRun.value) return "neutral";
  return ["SUCCEEDED", "SUCCESS", "COMPLETED"].includes(String(latestRun.value.status).toUpperCase())
    ? "success"
    : ["FAILED", "ERROR"].includes(String(latestRun.value.status).toUpperCase()) ? "danger" : "warning";
});
const runActivity = computed(() => runs.value.slice(0, 8).map((run) => ({
  id: run.id,
  title: run.connectorCode || run.connector_code || "运行",
  description: run.status || "",
})));

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
.quick-actions { display: flex; flex-wrap: wrap; gap: var(--nx-space-3); margin-bottom: var(--nx-space-4); }
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--nx-space-4);
}
ul { list-style: none; margin: 0; padding: 0; }
li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--nx-space-3);
  padding: var(--nx-space-3) 0;
  border-top: 1px solid var(--nx-divider);
}
li > div { display: flex; flex-direction: column; gap: var(--nx-space-1); }
.code { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
</style>
