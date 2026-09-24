<template>
  <main>
    <NxPageHeader
      eyebrow="P3 · NEXUS STUDIO"
      title="星构工作台"
      description="台账、表单、审批与通知的自用工作入口。"
    />

    <nav class="quick-actions" aria-label="星构快捷入口">
      <router-link class="primary" to="/p3/ledgers">台账</router-link>
      <router-link class="ghost" to="/p3/forms">表单</router-link>
      <router-link class="ghost" to="/p3/todos">待办</router-link>
      <router-link class="ghost" to="/p3/notifications">通知</router-link>
      <router-link class="ghost" to="/p3/audit">审计</router-link>
      <router-link class="ghost" to="/p3/reports">报表</router-link>
      <router-link class="ghost" to="/p3/dumps">物理 dump</router-link>
      <router-link class="ghost" to="/p3/exports/wizard">导出向导</router-link>
    </nav>

    <DashboardLayers>
      <template #kpi>
        <NxKpiRow aria-label="星构指标" :items="kpiItems" />
      </template>
      <template #charts>
        <NxPanelCard title="应用趋势" description="没有趋势聚合接口。">
          <NxChartPlaceholder title="应用趋势" />
        </NxPanelCard>
        <NxPanelCard title="应用构成" description="没有构成聚合接口。">
          <NxChartPlaceholder title="应用构成" />
        </NxPanelCard>
        <NxPanelCard title="应用状态" description="没有状态分布接口。">
          <NxChartPlaceholder title="应用状态" />
        </NxPanelCard>
      </template>
      <template #lists>
        <NxPanelCard title="待处理">
          <NxTodoList :items="[]" />
        </NxPanelCard>
        <NxPanelCard title="近期活动">
          <NxActivityTimeline :items="[]" />
        </NxPanelCard>
        <NxPanelCard title="运行健康" description="会话信息是次级状态，不计入指标。">
          <p v-if="health" class="health">IAM health: {{ health }}</p>
          <p>当前租户 {{ session.tenantId || "暂无" }}</p>
          <p>会话账号 {{ session.email || "暂无" }}</p>
          <p>平台操作 {{ session.platformMode ? "开启" : "关闭" }}</p>
          <p>隔离规则由服务端 TenantContext 执行。</p>
          <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="loadHealth" />
        </NxPanelCard>
      </template>
      <template #table>
        <NxPanelCard title="应用" description="只展示已返回的应用。">
          <template #actions>
            <router-link to="/p3/ledgers">查看全部</router-link>
          </template>
          <NxErrorState v-if="appError" :title="appError" :trace-id="appTrace" retry-label="重试" @retry="loadApps" />
          <NxEmpty v-else-if="apps && !apps.length" title="暂无" description="当前租户没有应用。" compact />
          <table v-else-if="apps?.length" class="nx-table">
            <thead>
              <tr><th>编码</th><th>名称</th><th>类型</th><th>状态</th><th></th></tr>
            </thead>
            <tbody>
              <tr v-for="app in apps.slice(0, 8)" :key="app.id || app.app_code">
                <td>{{ app.app_code || "暂无" }}</td>
                <td>{{ app.display_name || "暂无" }}</td>
                <td>{{ app.kind || "暂无" }}</td>
                <td>{{ app.status || "暂无" }}</td>
                <td class="row-links">
                  <router-link to="/p3/ledgers">台账</router-link>
                  <router-link to="/p3/forms">表单</router-link>
                </td>
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
  NxKpiRow,
  NxPageHeader,
  NxPanelCard,
  NxTodoList,
} from "../../components";
import { p2 } from "../../api/client";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";
import DashboardLayers from "../DashboardLayers.vue";

const health = ref("");
const error = ref("");
const apps = ref(null);
const appError = ref("");
const appTrace = ref("");
const appCount = computed(() => (apps.value && apps.value.length ? apps.value.length : null));
const kpiItems = computed(() => [
  { id: "apps", label: "应用", value: appCount.value, unit: "个" },
]);

async function loadHealth() {
  error.value = "";
  try {
    const body = await p2.health();
    health.value = body.status || JSON.stringify(body);
  } catch (err) {
    error.value = err.message;
  }
}

async function loadApps() {
  appError.value = "";
  appTrace.value = "";
  try {
    const body = await p3.listApplications();
    apps.value = Array.isArray(body?.items) ? body.items : null;
    if (!apps.value) {
      appError.value = "接口没有返回应用列表";
    }
  } catch (err) {
    apps.value = null;
    appError.value = err.message || "加载失败";
    appTrace.value = err.traceId || "";
  }
}

onMounted(() => {
  loadHealth();
  loadApps();
});
</script>

<style scoped>
.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nx-space-3);
  margin-bottom: var(--nx-space-4);
}
.row-links { display: flex; gap: var(--nx-space-3); }
.health { color: var(--nx-text-secondary); }
p { margin: 0 0 var(--nx-space-2); }
</style>
