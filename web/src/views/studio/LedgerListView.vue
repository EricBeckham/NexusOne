<template>
  <div>
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>台账</h1>
        <p>P3 一台账一物理表。本页是列表壳；SchemaService DDL 仍属 M0-06。</p>
      </div>
    </div>
    <NxSourceBanner body="MOCK 列表。真实 GET /api/v1/p3/ledgers 尚未实现。本包不关闭 G18。" />
    <div class="nx-card" style="margin-bottom:16px;">
      <div class="nx-muted">自建台账 Quota</div>
      <strong>{{ quotaLabel }}</strong>
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <table class="nx-table" data-testid="ledger-table">
      <thead>
        <tr>
          <th>台账名</th>
          <th>代码</th>
          <th>来源</th>
          <th>记录数</th>
          <th>字段数</th>
          <th>Schema Version</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!rows.length">
          <td colspan="7" class="nx-empty">{{ empty }}</td>
        </tr>
        <tr v-for="row in rows" :key="row.id">
          <td><router-link :to="`/p3/ledgers/${row.id}`">{{ row.name }}</router-link></td>
          <td class="nx-code">{{ row.code }}</td>
          <td>{{ row.source }}</td>
          <td>{{ row.record_count }}</td>
          <td>{{ row.field_count }}</td>
          <td>draft {{ row.draft_schema_version }} / running {{ row.running_schema_version }}</td>
          <td><NxStatus :label="row.status" :tone="row.status === 'RUNNING' ? 'success' : 'warning'" /></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3, p3Source } from "../../api/p3";

const rows = ref([]);
const quota = ref(null);
const error = ref("");

const quotaLabel = computed(() => {
  if (!quota.value) {
    return "—";
  }
  return `已用 ${quota.value.used} / 上限 ${quota.value.limit}`;
});

const empty = computed(() =>
  p3Source() === "LIVE" ? "真实 API 不可用或列表为空" : "暂无台账",
);

onMounted(async () => {
  try {
    const body = await p3.listLedgers();
    rows.value = body.items || body || [];
    quota.value = rows.value[0]?.quota || null;
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});
</script>
