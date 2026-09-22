<template>
  <div>
    <div class="nx-page-header">
      <h1>工作台</h1>
      <p>M0-12 已接租户（M0-03）、组织/身份（M0-04）、权限快照（M0-05）与 P3 台账/表单/待办 live API。G18 全链尚未关闭。</p>
    </div>
    <NxTodo body="台账 / 表单 / 待办走 /api/v1/p3/* 与 /api/v1/process/*，权限走 /api/v1/permission。需要服务端签发的 X-Nexus-Context-Id。不要把列表 200 当成 G18 业务走通。" />
    <div class="nx-row" style="margin-bottom:16px;">
      <router-link class="primary" to="/p3/ledgers">台账</router-link>
      <router-link class="ghost" to="/p3/forms">表单</router-link>
      <router-link class="ghost" to="/p3/todos">待办</router-link>
      <router-link class="ghost" to="/p2/permissions/roles">权限</router-link>
    </div>
    <div class="nx-grid kpi">
      <div class="nx-card">
        <div class="nx-muted">当前租户上下文</div>
        <strong>{{ session.tenantId || "未签发" }}</strong>
      </div>
      <div class="nx-card">
        <div class="nx-muted">会话账号</div>
        <strong>{{ session.email || "—" }}</strong>
      </div>
      <div class="nx-card">
        <div class="nx-muted">平台操作</div>
        <strong>{{ session.platformMode ? "开启" : "关闭" }}</strong>
      </div>
      <div class="nx-card">
        <div class="nx-muted">隔离规则</div>
        <strong>不发送 X-Tenant-Id</strong>
      </div>
    </div>
    <p v-if="health" class="nx-muted" style="margin-top:16px;">IAM health: {{ health }}</p>
    <p v-if="error" class="nx-error">{{ error }}</p>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import NxTodo from "../components/NxTodo.vue";
import { p2 } from "../api/client";
import { session } from "../api/session";

const health = ref("");
const error = ref("");

onMounted(async () => {
  try {
    const body = await p2.health();
    health.value = body.status || JSON.stringify(body);
  } catch (err) {
    error.value = err.message;
  }
});
</script>
