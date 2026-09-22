<template>
  <div v-if="tenant">
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>{{ tenant.name }}</h1>
        <p class="nx-code">{{ tenant.code }} · {{ tenant.id }}</p>
      </div>
      <div class="nx-row">
        <NxStatus :label="tenant.status" :tone="tenant.status === 'ENABLED' ? 'success' : 'warning'" />
        <button v-if="tenant.status === 'ENABLED'" class="danger" @click="stop">停用</button>
        <button v-else class="primary" @click="start">启用</button>
      </div>
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <div class="nx-grid" style="grid-template-columns: 2fr 1fr;">
      <div class="nx-card">
        <h3>基础配置</h3>
        <div class="nx-field"><label>显示名</label><input v-model="config.display_name" /></div>
        <div class="nx-field"><label>Locale</label><input v-model="config.locale" /></div>
        <div class="nx-field"><label>时区</label><input v-model="config.timezone" /></div>
        <div class="nx-field"><label>币种</label><input v-model="config.currency" /></div>
        <button class="primary" @click="saveConfig">保存配置</button>
      </div>
      <div>
        <div class="nx-card">
          <h3>权益投影</h3>
          <p>source {{ entitlement?.source }}</p>
          <p>pack {{ entitlement?.pack_code }} {{ entitlement?.pack_version }}</p>
        </div>
        <div class="nx-card" style="margin-top:16px;">
          <h3>Quota</h3>
          <p v-for="q in quotas" :key="q.metric">
            {{ q.metric }} {{ q.used }}/{{ q.limit }}
            <NxStatus v-if="q.warning" label="WARN" tone="warning" />
          </p>
        </div>
      </div>
    </div>
    <div class="nx-card" style="margin-top:16px;">
      <h3>审计（P1 暂存，M0-11 替换）</h3>
      <table class="nx-table">
        <thead><tr><th>时间</th><th>类型</th><th>结果</th><th>trace</th></tr></thead>
        <tbody>
          <tr v-for="ev in audit" :key="ev.event_id || ev.eventId">
            <td>{{ ev.occurred_at || ev.occurredAt }}</td>
            <td>{{ ev.event_type || ev.eventType }}</td>
            <td>{{ ev.outcome }}</td>
            <td class="nx-code">{{ ev.trace_id || ev.traceId }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import NxStatus from "../../components/NxStatus.vue";
import { p1 } from "../../api/client";

const route = useRoute();
const tenant = ref(null);
const config = ref({});
const entitlement = ref(null);
const quotas = ref([]);
const audit = ref([]);
const error = ref("");

async function reload() {
  const id = route.params.id;
  tenant.value = await p1.getTenant(id);
  config.value = await p1.getConfig(id);
  entitlement.value = await p1.entitlement(id);
  quotas.value = await p1.quotas(id);
  audit.value = await p1.audit(id);
}

onMounted(async () => {
  try {
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function saveConfig() {
  try {
    config.value = await p1.putConfig(route.params.id, config.value);
  } catch (err) {
    error.value = err.message;
  }
}

async function stop() {
  try {
    tenant.value = await p1.disable(route.params.id, "m0-12-ui");
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}

async function start() {
  tenant.value = await p1.enable(route.params.id);
}
</script>

<style scoped>
.primary, .danger { border: 0; border-radius: 6px; padding: 8px 12px; color: #fff; cursor: pointer; }
.primary { background: var(--nx-product); }
.danger { background: var(--nx-danger); }
</style>
