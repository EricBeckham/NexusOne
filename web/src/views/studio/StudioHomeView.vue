<template>
  <main>
    <NxPageHeader
      eyebrow="P3 · NEXUS STUDIO"
      title="星构工作台"
      description="台账、表单、审批与通知的自用工作入口。"
    />

    <NxTodo body="流程设计器与发布中心仍在侧栏标为后续。应用列表只使用已有应用接口，没有应用时显示暂无。" />

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

    <section class="apps" aria-label="应用">
      <NxStatCard label="应用" :value="appCount" unit="个" />
      <NxErrorState v-if="appError" :title="appError" :trace-id="appTrace" retry-label="重试" @retry="loadApps" />
      <NxEmpty v-else-if="apps && !apps.length" title="暂无" description="当前租户没有应用。" />
      <table v-else-if="apps?.length" class="nx-table">
        <thead>
          <tr><th>编码</th><th>名称</th><th>类型</th><th>状态</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="app in apps" :key="app.id || app.app_code">
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
    </section>

    <section class="stats" aria-label="当前会话状态">
      <NxStatCard label="当前租户上下文" :value="session.tenantId || null" />
      <NxStatCard label="会话账号" :value="session.email || null" />
      <NxStatCard label="平台操作" :value="session.platformMode ? '开启' : '关闭'" :tone="session.platformMode ? 'warning' : 'neutral'" />
      <NxStatCard label="隔离规则" value="服务端 TenantContext" tone="success" />
    </section>

    <p v-if="health" class="health">IAM health: {{ health }}</p>
    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="loadHealth" />
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatCard } from "../../components";
import NxTodo from "../../components/NxTodo.vue";
import { p2 } from "../../api/client";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";

const health = ref("");
const error = ref("");
const apps = ref(null);
const appError = ref("");
const appTrace = ref("");
const appCount = computed(() => (apps.value && apps.value.length ? apps.value.length : null));

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
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--nx-space-4);
}
.apps { display: grid; gap: var(--nx-space-4); margin-bottom: var(--nx-space-4); }
.row-links { display: flex; gap: var(--nx-space-3); }
.health { margin-top: var(--nx-space-4); color: var(--nx-text-secondary); }
</style>
