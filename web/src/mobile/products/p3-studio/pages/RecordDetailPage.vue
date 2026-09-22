<template>
  <div>
    <div class="m-card">
      <h3>{{ title }}</h3>
      <p class="m-muted">记录详情。FieldPolicy 由服务端投影，本页不重算。</p>
      <MPermDecision :decision="decision" :cell="H5_SURFACES.listRead" />
    </div>
    <p v-if="error" class="m-error" data-testid="m-record-error">{{ error }}</p>
    <div v-if="record" class="m-card" data-testid="m-record-detail">
      <div v-for="[key, value] in entries" :key="key" class="m-field">
        <label>{{ key }}</label>
        <p class="m-muted">{{ formatCell(value) }}</p>
      </div>
    </div>
    <router-link class="m-btn ghost" :to="`/m/p3/lists/${ledgerId}`">返回列表</router-link>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { p3 } from "../../../../api/p3";
import { SYSTEM_SKIP, formatCell, recordTitle } from "../../../runtime/display";
import MPermDecision from "../../../permission/MPermDecision.vue";
import { H5_SURFACES } from "../../../permission/surfaceCatalog";
import { useH5Surface } from "../../../permission/useH5Surface";

const route = useRoute();
const ledgerId = computed(() => String(route.params.id || ""));
const recordId = computed(() => String(route.params.recordId || ""));
const record = ref(null);
const { decision, error, requireCell, capture } = useH5Surface();
const title = computed(() => recordTitle(record.value));
const entries = computed(() =>
  Object.entries(record.value || {}).filter(([key]) => !SYSTEM_SKIP.has(key)),
);

onMounted(async () => {
  try {
    await requireCell(H5_SURFACES.listRead);
    record.value = await p3.getRecord(ledgerId.value, recordId.value);
  } catch (err) {
    record.value = null;
    capture(err);
  }
});
</script>
