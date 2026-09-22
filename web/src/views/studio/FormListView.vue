<template>
  <div>
    <div class="nx-page-header">
      <h1>表单</h1>
      <p>基础表单列表。填报与 Draft → Validate → Publish → Running 走 M0-07 live API。</p>
    </div>
    <NxSourceBanner body="Live GET /api/v1/p3/forms。G5 由模块测试覆盖；本页不关闭 G18。" />
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。请先登录并完成租户切换。</p>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <table class="nx-table" data-testid="form-table">
      <thead>
        <tr>
          <th>表单</th>
          <th>绑定台账码</th>
          <th>Draft</th>
          <th>Running</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!rows.length">
          <td colspan="4" class="nx-empty">暂无表单或缺少 TenantContext</td>
        </tr>
        <tr v-for="row in rows" :key="row.id">
          <td><router-link :to="`/p3/forms/${row.id}`">{{ row.name }}</router-link></td>
          <td class="nx-code">{{ row.ledger_name || "—" }}</td>
          <td><NxStatus :label="row.draft_status" :tone="row.draft_status === 'VALIDATED' ? 'success' : 'warning'" /></td>
          <td>v{{ row.running_version || 0 }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";

const rows = ref([]);
const error = ref("");

onMounted(async () => {
  try {
    const body = await p3.listForms();
    rows.value = body.items || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});
</script>
