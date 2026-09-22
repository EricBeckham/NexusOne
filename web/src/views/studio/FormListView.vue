<template>
  <div>
    <div class="nx-page-header">
      <h1>表单</h1>
      <p>基础表单列表。填报与 Draft → Validate → Publish → Running 在详情壳中演示。</p>
    </div>
    <NxSourceBanner body="MOCK 表单。真实 GET /api/v1/p3/forms 等待 M0-07。G5 未关闭。" />
    <p v-if="error" class="nx-error">{{ error }}</p>
    <table class="nx-table" data-testid="form-table">
      <thead>
        <tr>
          <th>表单</th>
          <th>台账</th>
          <th>Draft</th>
          <th>Published</th>
          <th>Running</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!rows.length">
          <td colspan="5" class="nx-empty">暂无表单或真实 API 不可用</td>
        </tr>
        <tr v-for="row in rows" :key="row.id">
          <td><router-link :to="`/p3/forms/${row.id}`">{{ row.name }}</router-link></td>
          <td>{{ row.ledger_name }}</td>
          <td><NxStatus :label="row.draft_status" :tone="row.draft_status === 'RUNNING' ? 'success' : 'warning'" /></td>
          <td>v{{ row.published_version }}</td>
          <td>v{{ row.running_version }}</td>
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

const rows = ref([]);
const error = ref("");

onMounted(async () => {
  try {
    const body = await p3.listForms();
    rows.value = body.items || body || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});
</script>
