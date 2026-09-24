<template>
  <main>
    <NxPageHeader
      eyebrow="P3 · NEXUS STUDIO"
      title="星构工作台"
      description="台账、表单、审批与通知的自用工作入口。"
    />

    <NxTodo body="应用工作台正式首页、流程设计器与发布中心安排在后续任务；当前入口均连接已交付能力。" />

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
import { onMounted, ref } from "vue";
import { NxErrorState, NxPageHeader, NxStatCard } from "../../components";
import NxTodo from "../../components/NxTodo.vue";
import { p2 } from "../../api/client";
import { session } from "../../api/session";

const health = ref("");
const error = ref("");

async function loadHealth() {
  error.value = "";
  try {
    const body = await p2.health();
    health.value = body.status || JSON.stringify(body);
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(loadHealth);
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
.health { margin-top: var(--nx-space-4); color: var(--nx-text-secondary); }
</style>
