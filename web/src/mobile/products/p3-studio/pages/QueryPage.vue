<template>
  <div>
    <div class="m-card">
      <h3>查询</h3>
      <p class="m-muted">GET /api/v1/p3/ledgers/{id}/records 字段等值。不发明第二套 Query AST。</p>
    </div>
    <form class="m-card" @submit.prevent="onRun">
      <div class="m-field">
        <label>台账</label>
        <select v-model="ledgerId" required data-testid="m-query-ledger">
          <option value="" disabled>选择台账</option>
          <option v-for="item in ledgers" :key="item.id" :value="item.id">
            {{ item.name }} ({{ item.code }})
          </option>
        </select>
      </div>
      <div v-for="field in queryFields" :key="field.code" class="m-field">
        <label>{{ field.name || field.code }}</label>
        <input v-model="filters[field.code]" :placeholder="field.code" :data-testid="`m-query-field-${field.code}`" />
      </div>
      <button class="m-btn primary" type="submit" :disabled="!ledgerId || busy" data-testid="m-query-run">
        {{ busy ? "查询中…" : "查询" }}
      </button>
    </form>
    <p v-if="error" class="m-error" data-testid="m-query-error">{{ error }}</p>
    <p v-if="ran && !rows.length && !error" class="m-muted" data-testid="m-query-empty">没有匹配记录。</p>
    <p v-if="ran" class="m-muted" data-testid="m-query-total">共 {{ total }} 条</p>
    <router-link
      v-for="row in rows"
      :key="row.id || JSON.stringify(row)"
      class="m-entity"
      :to="`/m/p3/lists/${ledgerId}/records/${row.id}`"
    >
      <strong>{{ recordTitle(row, queryFields) }}</strong>
      <div class="m-muted">{{ formatCell(row.status || row.approval_status) }}</div>
    </router-link>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { p3 } from "../../../../api/p3";
import { formatCell, recordTitle } from "../../../runtime/display";
import { buildRecordQuery, isLedgerFieldCode } from "../../../runtime/recordQuery";

const route = useRoute();
const router = useRouter();
const ledgers = ref([]);
const fields = ref([]);
const ledgerId = ref(String(route.query.ledger || ""));
const filters = reactive({});
const rows = ref([]);
const total = ref(0);
const error = ref("");
const busy = ref(false);
const ran = ref(false);

const queryFields = computed(() =>
  (fields.value || []).filter((field) => isLedgerFieldCode(field.code)).slice(0, 6),
);

function syncFiltersFromRoute() {
  for (const field of queryFields.value) {
    filters[field.code] = route.query[field.code] || "";
  }
}

async function loadFields(id) {
  if (!id) {
    fields.value = [];
    return;
  }
  const detail = await p3.getLedger(id);
  fields.value = detail.fields || [];
  syncFiltersFromRoute();
}

onMounted(async () => {
  try {
    const body = await p3.listLedgers();
    ledgers.value = body.items || [];
    if (!ledgerId.value && ledgers.value.length) {
      ledgerId.value = ledgers.value[0].id;
    }
    if (ledgerId.value) {
      await loadFields(ledgerId.value);
    }
    if (ledgerId.value && Object.keys(route.query).some((key) => isLedgerFieldCode(key))) {
      await onRun();
    }
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

watch(ledgerId, async (id) => {
  try {
    await loadFields(id);
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function onRun() {
  error.value = "";
  busy.value = true;
  ran.value = true;
  try {
    const query = buildRecordQuery(filters);
    const next = { ledger: ledgerId.value, ...query };
    await router.replace({ path: "/m/p3/query", query: next });
    const body = await p3.listRecords(ledgerId.value, query);
    rows.value = body.items || [];
    total.value = body.total ?? rows.value.length;
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
    rows.value = [];
    total.value = 0;
  } finally {
    busy.value = false;
  }
}
</script>
