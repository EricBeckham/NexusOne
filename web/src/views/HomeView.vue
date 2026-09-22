<template>
  <div>
    <div class="nx-page-header">
      <h1>工作台</h1>
        <p>P0+M0+G18 已在 main 关闭。当前 S0+S1 PG 门禁：台账页可开通请假 / 报销 / 出差三类业务，待办「我发起」可撤回。</p>
    </div>
    <NxTodo body="G18 请假切片已关闭。G7 三类（请假/报销/出差）走同一套 ledger/form/path。G4 / G2 / G1 余量仍单独验收，不在本页宣称 S0+S1 整包通过。" />
    <div class="nx-row" style="margin-bottom:16px;">
      <router-link class="primary" to="/p3/ledgers">台账</router-link>
      <router-link class="ghost" to="/p3/forms">表单</router-link>
      <router-link class="ghost" to="/p3/todos">待办</router-link>
      <router-link class="ghost" to="/p3/notifications">通知</router-link>
      <router-link class="ghost" to="/p3/audit">审计</router-link>
      <router-link class="ghost" to="/p3/reports">报表</router-link>
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
