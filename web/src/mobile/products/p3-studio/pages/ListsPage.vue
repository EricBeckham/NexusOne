<template>
  <div>
    <div class="m-card">
      <h3>业务列表</h3>
      <p class="m-muted">桌面 Table → H5 Record Card。本页不创建台账、不改字段。</p>
    </div>
    <p v-if="!session.contextId" class="m-error">没有 TenantContext。列表需要已签发上下文。</p>
    <p v-if="error" class="m-error" data-testid="m-lists-error">{{ error }}</p>
    <p v-if="!rows.length && !error" class="m-muted" data-testid="m-lists-empty">当前租户没有台账。</p>
    <router-link
      v-for="row in rows"
      :key="row.id"
      class="m-entity"
      :to="`/m/p3/lists/${row.id}`"
      :data-testid="`m-list-${row.id}`"
    >
      <strong>{{ row.name }}</strong>
      <div class="m-muted">{{ row.code }} · {{ row.status || "—" }} · 字段 {{ row.field_count ?? "—" }}</div>
    </router-link>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p3 } from "../../../../api/p3";
import { session } from "../../../../api/session";

const rows = ref([]);
const error = ref("");

onMounted(async () => {
  try {
    const body = await p3.listLedgers();
    rows.value = body.items || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});
</script>
