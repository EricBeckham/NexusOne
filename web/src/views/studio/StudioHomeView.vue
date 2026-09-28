<template>
  <main class="studio-home">
    <NxPageHeader
      eyebrow="P3 · 星构"
      title="星构工作台"
      description="台账、表单、审批与通知的自用工作入口。"
    >
      <template #actions>
        <router-link class="primary" to="/p3/todos">打开待办</router-link>
      </template>
    </NxPageHeader>

    <!-- R11: grouped entry cards (same groups as the sidebar) instead of a row of eight buttons. -->
    <nav class="entry-groups quick-actions" aria-label="星构快捷入口">
      <section v-for="group in ENTRY_GROUPS" :key="group.title" class="entry-group nx-card" data-testid="studio-entry-group">
        <h2>{{ group.title }}</h2>
        <router-link v-for="entry in group.entries" :key="entry.to" class="entry" :to="entry.to" data-testid="studio-entry">
          <span class="entry-icon" aria-hidden="true"><NxIcon :name="entry.icon" :size="18" /></span>
          <span class="entry-text"><strong>{{ entry.label }}</strong><small>{{ entry.hint }}</small></span>
          <NxIcon class="entry-go" name="chevronRight" :size="16" />
        </router-link>
      </section>
    </nav>

    <div class="home-row">
      <NxPanelCard :title="apps?.length ? `应用 · ${apps.length} 个` : '应用'" description="当前租户已返回的应用。" data-testid="studio-apps">
        <template #actions>
          <router-link to="/p3/ledgers">查看台账</router-link>
        </template>
        <NxErrorState v-if="appError" :title="appError" :trace-id="appTrace" retry-label="重试" @retry="loadApps" />
        <NxSkeleton v-else-if="apps === null" variant="list-row" label="正在加载应用" data-testid="studio-apps-loading" />
        <NxEmpty v-else-if="apps && !apps.length" title="暂无应用" description="当前租户还没有应用。创建台账或表单后会出现在这里。" compact>
          <template #action><router-link class="ghost" to="/p3/ledgers">去创建台账</router-link></template>
        </NxEmpty>
        <table v-else-if="apps?.length" class="nx-table">
          <thead>
            <tr><th>名称</th><th>编码</th><th>类型</th><th>状态</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="app in apps.slice(0, 8)" :key="app.id || app.app_code">
              <td class="app-name">{{ app.display_name || "暂无" }}</td>
              <td class="nx-code app-code">{{ app.app_code || "暂无" }}</td>
              <td>{{ app.kind || "暂无" }}</td>
              <td><NxStatusTag :status="app.status || ''" :label="app.status ? undefined : '暂无'" /></td>
              <td class="row-links">
                <router-link to="/p3/ledgers">台账</router-link>
                <router-link to="/p3/forms">表单</router-link>
              </td>
            </tr>
          </tbody>
        </table>
      </NxPanelCard>

      <NxPanelCard title="当前会话" data-testid="studio-session">
        <dl class="session">
          <dt>租户</dt><dd class="nx-code">{{ session.tenantId || "暂无" }}</dd>
          <dt>账号</dt><dd>{{ session.email || "暂无" }}</dd>
          <dt>身份服务</dt>
          <dd><NxStatus v-if="health" :label="healthUp ? '运行正常' : health" :tone="healthUp ? 'success' : 'warning'" data-testid="studio-health" /><span v-else-if="!error">检查中…</span></dd>
        </dl>
        <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="loadHealth" />
      </NxPanelCard>
    </div>
  </main>
</template>
<script setup>
import { computed, onMounted, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxPanelCard, NxSkeleton, NxStatusTag } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import NxIcon from "../../icons/NxIcon.vue";
import { p2 } from "../../api/client";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";

const ENTRY_GROUPS = [
  {
    title: "工作台",
    entries: [
      { label: "可用应用", hint: "从已开通应用发起申请", to: "/p3/applications", icon: "file" },
      { label: "待办", hint: "审批我的待办与我的申请", to: "/p3/todos", icon: "check" },
      { label: "表单", hint: "填报与管理表单", to: "/p3/forms", icon: "file" },
      { label: "台账", hint: "业务记录与字段", to: "/p3/ledgers", icon: "table" },
      { label: "站内通知", hint: "审批与系统消息", to: "/p3/notifications", icon: "bell" },
      { label: "功能入口", hint: "已安装应用里可见的功能", to: "/p3/portal", icon: "grid" },
    ],
  },
  {
    title: "流程配置",
    entries: [
      { label: "流程设计器", hint: "顺序审批流程草稿", to: "/p3/workflow", icon: "workflow" },
      { label: "发布中心", hint: "发布记录与版本", to: "/p3/publish-center", icon: "upload" },
    ],
  },
  {
    title: "数据与审计",
    entries: [
      { label: "报表", hint: "按权限查询与导出", to: "/p3/reports", icon: "chart" },
      { label: "导出向导", hint: "分步导出台账数据", to: "/p3/exports/wizard", icon: "download" },
      { label: "物理导出", hint: "原始数据导出", to: "/p3/dumps", icon: "archive" },
      { label: "审计", hint: "审计哈希链追踪", to: "/p3/audit", icon: "shield" },
    ],
  },
];
const health = ref("");
const error = ref("");
const apps = ref(null);
const appError = ref("");
const appTrace = ref("");
const healthUp = computed(() => String(health.value).toUpperCase() === "UP");

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
.studio-home { display: grid; gap: var(--nx-space-4); }
.entry-groups { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--nx-space-4); align-items: start; }
.entry-group { display: grid; gap: 2px; padding: var(--nx-space-4); }
.entry-group h2 { margin: 0 0 var(--nx-space-2); color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); font-weight: 500; }
.entry { display: flex; align-items: center; gap: var(--nx-space-3); min-height: 48px; padding: 6px var(--nx-space-2); border-radius: var(--nx-radius-sm); color: inherit; text-decoration: none; }
.entry:hover { background: var(--nx-bg-page); }
.entry:hover .entry-go { color: var(--nx-product); }
.entry-icon { display: grid; flex: none; place-items: center; width: 32px; height: 32px; border-radius: 8px; background: var(--nx-product-light); color: var(--nx-product); }
.entry-text { display: grid; flex: 1; min-width: 0; }
.entry-text strong { color: var(--nx-text-primary); font-size: var(--nx-font-size-md); font-weight: 500; }
.entry-text small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.entry-go { flex: none; color: var(--nx-text-disabled); }
.home-row { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: var(--nx-space-4); align-items: start; }
.app-name { color: var(--nx-text-primary); font-weight: 500; }
.app-code { color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.row-links { display: flex; gap: var(--nx-space-3); }
.session { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: var(--nx-space-2) var(--nx-space-3); margin: 0; font-size: var(--nx-font-size-sm); }
.session dt { color: var(--nx-text-secondary); }
.session dd { margin: 0; overflow-wrap: anywhere; }
@media (max-width: 1100px) {
  .entry-groups, .home-row { grid-template-columns: 1fr; }
}
</style>
