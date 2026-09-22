<template>
  <div>
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>租户目录</h1>
        <p>P1 平台范围列表。隔离由服务端 PlatformContext 守卫，不在前端拼 tenant_id 过滤。</p>
      </div>
      <router-link class="primary" to="/p1/tenants/new">创建租户</router-link>
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <table class="nx-table">
      <thead>
        <tr>
          <th>编码</th>
          <th>名称</th>
          <th>类型</th>
          <th>状态</th>
          <th>ID</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!rows.length">
          <td colspan="5" class="nx-muted">暂无租户或平台 API 不可用</td>
        </tr>
        <tr v-for="row in rows" :key="row.id">
          <td><router-link :to="`/p1/tenants/${row.id}`">{{ row.code }}</router-link></td>
          <td>{{ row.name }}</td>
          <td>{{ row.kind }}</td>
          <td>
            <NxStatus :label="row.status" :tone="row.status === 'ENABLED' ? 'success' : 'warning'" />
          </td>
          <td class="nx-code">{{ row.id }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import NxStatus from "../../components/NxStatus.vue";
import { p1 } from "../../api/client";

const rows = ref([]);
const error = ref("");

onMounted(async () => {
  try {
    rows.value = (await p1.listTenants()) || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});
</script>

<style>
.nx-table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid var(--nx-border); border-radius: 12px; overflow: hidden; }
.nx-table th, .nx-table td { text-align: left; padding: 10px 12px; border-bottom: 1px solid var(--nx-divider); }
.nx-table th { color: var(--nx-text-secondary); font-weight: 600; font-size: 12px; }
.primary { background: var(--nx-product); color: #fff; border-radius: 6px; padding: 8px 12px; }
</style>
