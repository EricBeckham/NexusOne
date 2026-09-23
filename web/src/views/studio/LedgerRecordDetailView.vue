<template>
  <div v-if="ledger && record">
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>{{ ledger.name }} · 记录详情</h1>
        <p class="nx-code">{{ record.id }}</p>
      </div>
      <router-link class="ghost" :to="`/p3/ledgers/${ledger.id}`">返回记录列表</router-link>
    </div>
    <NxSourceBanner body="GET /api/v1/p3/ledgers/{ledgerId}/records/{recordId}；服务端按 TenantContext、PermissionEngine 与 DataScope 返回详情。" />
    <div class="nx-card" data-testid="record-detail">
      <dl class="detail-grid">
        <template v-for="item in details" :key="item.key">
          <dt>{{ item.label }}</dt>
          <dd :class="{ 'nx-code': item.system }">{{ formatValue(item.value) }}</dd>
        </template>
      </dl>
    </div>
  </div>
  <p v-else-if="error" class="nx-error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";

const route = useRoute();
const ledger = ref(null);
const record = ref(null);
const fields = ref([]);
const error = ref("");

const SYSTEM_FIELDS = ["id", "owner_id", "department_id", "created_at", "updated_at", "version"];
const details = computed(() => {
  const business = fields.value.map((field) => ({
    key: field.code,
    label: `${field.name}（${field.code}）`,
    value: record.value?.[field.code],
    system: false,
  }));
  const system = SYSTEM_FIELDS
    .filter((key) => record.value?.[key] !== undefined)
    .map((key) => ({ key, label: key, value: record.value[key], system: true }));
  return [...business, ...system];
});

function formatValue(value) {
  if (value == null || value === "") return "—";
  return typeof value === "object" ? JSON.stringify(value) : String(value);
}

onMounted(async () => {
  try {
    ledger.value = await p3.getLedger(route.params.id);
    const fieldBody = await p3.listFields(route.params.id);
    fields.value = fieldBody.items || [];
    record.value = await p3.getRecord(route.params.id, route.params.recordId);
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});
</script>

<style scoped>
.detail-grid { display: grid; grid-template-columns: minmax(140px, 220px) 1fr; gap: 12px 20px; margin: 0; }
.detail-grid dt { color: var(--nx-muted); }
.detail-grid dd { margin: 0; overflow-wrap: anywhere; }
</style>
