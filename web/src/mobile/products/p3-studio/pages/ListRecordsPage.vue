<template>
  <div>
    <div v-if="ledger" class="m-card">
      <h3>{{ ledger.name }}</h3>
      <p class="m-muted">{{ ledger.code }} · Schema v{{ ledger.running_schema_version }}</p>
    </div>
    <div class="m-field">
      <label>筛选</label>
      <input v-model="queryText" placeholder="单号 / 标题" data-testid="m-list-filter" />
    </div>
    <p v-if="error" class="m-error" data-testid="m-list-records-error">{{ error }}</p>
    <p v-if="!filtered.length && !error" class="m-muted">暂无记录。用已发布表单 submit-start 写入。</p>
    <router-link
      v-for="row in filtered"
      :key="row.id || JSON.stringify(row)"
      class="m-entity"
      :to="`/m/p3/lists/${ledgerId}/records/${row.id}`"
    >
      <strong>{{ recordTitle(row, fields) }}</strong>
      <div class="m-muted">{{ formatCell(row.status || row.approval_status) }} · {{ row.id }}</div>
    </router-link>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { p3 } from "../../../../api/p3";
import { formatCell, recordTitle } from "../../../runtime/display";

const route = useRoute();
const ledgerId = computed(() => String(route.params.id || ""));
const ledger = ref(null);
const fields = ref([]);
const records = ref([]);
const queryText = ref("");
const error = ref("");

const filtered = computed(() => {
  const q = queryText.value.trim();
  if (!q) {
    return records.value;
  }
  return records.value.filter((row) =>
    Object.values(row || {}).some((value) => String(value || "").includes(q)),
  );
});

onMounted(async () => {
  try {
    ledger.value = await p3.getLedger(ledgerId.value);
    const fieldBody = await p3.listFields(ledgerId.value);
    fields.value = fieldBody.items || [];
    const recBody = await p3.listRecords(ledgerId.value);
    records.value = recBody.items || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});
</script>
