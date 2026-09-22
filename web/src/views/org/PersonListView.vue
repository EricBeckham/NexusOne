<template>
  <div>
    <div class="nx-page-header">
      <h1>用户目录</h1>
      <p>列表来自当前服务端签发的租户上下文。切换租户后本表必须换成另一套 Person 行。</p>
    </div>
    <p v-if="!session.contextId" class="nx-error">没有 context。请先登录并完成租户切换。</p>
    <form class="nx-card" style="margin-bottom:16px;" @submit.prevent="create">
      <div class="nx-row">
        <div class="nx-field"><label>姓名</label><input v-model="form.display_name" required /></div>
        <div class="nx-field"><label>工号</label><input v-model="form.employee_no" /></div>
        <div class="nx-field"><label>邮箱</label><input v-model="form.email" type="email" /></div>
        <button class="primary" type="submit">新增人员</button>
      </div>
    </form>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <table class="nx-table">
      <thead>
        <tr><th>姓名</th><th>工号</th><th>邮箱</th><th>状态</th><th>tenant_id（只读回显）</th></tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.display_name || row.displayName }}</td>
          <td>{{ row.employee_no || row.employeeNo }}</td>
          <td>{{ row.email }}</td>
          <td><NxStatus :label="row.status" :tone="row.status === 'ACTIVE' ? 'success' : 'neutral'" /></td>
          <td class="nx-code">{{ row.tenant_id || row.tenantId }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import NxStatus from "../../components/NxStatus.vue";
import { p2 } from "../../api/client";
import { ensureContext, session } from "../../api/session";

const rows = ref([]);
const error = ref("");
const form = reactive({ display_name: "", employee_no: "", email: "" });

async function reload() {
  rows.value = (await p2.persons()) || [];
}

onMounted(async () => {
  try {
    if (session.accessToken && !session.contextId) {
      await ensureContext();
    }
    if (session.contextId) {
      await reload();
    }
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function create() {
  try {
    await p2.createPerson(form);
    form.display_name = "";
    form.employee_no = "";
    form.email = "";
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}
</script>

<style scoped>
.primary { background: var(--nx-product); color: #fff; border: 0; border-radius: 6px; padding: 8px 12px; height: 38px; align-self: end; }
</style>
