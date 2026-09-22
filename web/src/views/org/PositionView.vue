<template>
  <div>
    <div class="nx-page-header">
      <h1>岗位与任职</h1>
      <p>Position ≠ Role。Role / DataScope 要等 M0-05。这里只接 M0-04 岗位与任职 API。</p>
    </div>
    <NxTodo body="角色授权、Permission Matrix、PolicySnapshot 页面已放在「权限」菜单，目前是明确 TODO，不调用不存在的权限引擎。" />
    <div class="nx-grid" style="grid-template-columns: 1fr 1fr;">
      <form class="nx-card" @submit.prevent="createPos">
        <h3>新建岗位</h3>
        <div class="nx-field"><label>编码</label><input v-model="pos.code" required /></div>
        <div class="nx-field"><label>名称</label><input v-model="pos.name" required /></div>
        <div class="nx-field"><label>类别</label><input v-model="pos.category" /></div>
        <button class="primary" type="submit">保存岗位</button>
      </form>
      <form class="nx-card" @submit.prevent="assign">
        <h3>任职</h3>
        <div class="nx-field"><label>人员 ID</label><input v-model="emp.person_id" required /></div>
        <div class="nx-field"><label>部门 ID</label><input v-model="emp.department_id" required /></div>
        <div class="nx-field"><label>岗位 ID</label><input v-model="emp.position_id" required /></div>
        <button class="primary" type="submit">分配</button>
      </form>
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <table class="nx-table" style="margin-top:16px;">
      <thead><tr><th>编码</th><th>名称</th><th>类别</th><th>tenant</th></tr></thead>
      <tbody>
        <tr v-for="row in positions" :key="row.id">
          <td>{{ row.code }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.category }}</td>
          <td class="nx-code">{{ row.tenant_id || row.tenantId }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import NxTodo from "../../components/NxTodo.vue";
import { p2 } from "../../api/client";

const positions = ref([]);
const error = ref("");
const pos = reactive({ code: "", name: "", category: "ic", duties: "" });
const emp = reactive({ person_id: "", department_id: "", position_id: "", kind: "SECONDARY" });

async function reload() {
  positions.value = (await p2.positions()) || [];
}

onMounted(async () => {
  try {
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function createPos() {
  try {
    await p2.createPosition(pos);
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}

async function assign() {
  try {
    await p2.assignEmployment(emp);
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}
</script>

<style scoped>
.primary { background: var(--nx-product); color: #fff; border: 0; border-radius: 6px; padding: 8px 12px; }
</style>
