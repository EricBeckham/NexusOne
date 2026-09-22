<template>
  <div v-if="ledger">
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>{{ ledger.name }}</h1>
        <p class="nx-code">{{ ledger.code }} · {{ ledger.physical_table }}</p>
      </div>
      <div class="nx-row">
        <NxStatus :label="`Schema v${ledger.running_schema_version}`" tone="success" />
        <router-link class="ghost" to="/p3/ledgers">返回列表</router-link>
      </div>
    </div>
    <NxSourceBanner body="记录走 GET /api/v1/p3/ledgers/{id}/records。新增字段走 POST .../fields。G18 业务行由已发布表单的 submit-start 写入。" />
    <p v-if="error" class="nx-error">{{ error }}</p>
    <div class="nx-tabs">
      <button :class="{ active: tab === 'records' }" type="button" @click="tab = 'records'">记录</button>
      <button :class="{ active: tab === 'designer' }" type="button" data-testid="ledger-designer-tab" @click="tab = 'designer'">字段设计</button>
    </div>

    <table v-if="tab === 'records'" class="nx-table">
      <thead>
        <tr>
          <th v-for="col in recordColumns" :key="col">{{ col }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!records.length">
          <td :colspan="recordColumns.length" class="nx-empty">暂无记录</td>
        </tr>
        <tr v-for="row in records" :key="row.id || JSON.stringify(row)">
          <td v-for="col in recordColumns" :key="col">{{ formatCell(row, col) }}</td>
        </tr>
      </tbody>
    </table>

    <div v-else>
      <div class="toolbar nx-row" style="margin-bottom:12px;">
        <span class="nx-muted">Palette 映射到 M0-06 FieldType（TEXT / NUMERIC / BOOLEAN / TIMESTAMPTZ）。不能边编辑边改 Running。</span>
      </div>
      <div class="nx-designer">
        <div class="pane palette">
          <h3>基础 / 业务</h3>
          <button
            v-for="kind in palette"
            :key="kind.type"
            class="nx-chip-btn"
            type="button"
            @click="add(kind)"
          >{{ kind.label }}</button>
        </div>
        <div class="pane canvas">
          <h3>字段</h3>
          <button
            v-for="field in fields"
            :key="field.id || field.code"
            class="nx-chip-btn"
            :class="{ active: selected?.id === field.id }"
            type="button"
            @click="selected = field"
          >
            <strong>{{ field.name }}</strong>
            <span class="nx-muted"> · {{ field.code }} · {{ field.type }}</span>
          </button>
          <h3 class="sys">系统字段（只读）</h3>
          <p v-for="field in system" :key="field.code" class="nx-muted">{{ field.code }}</p>
        </div>
        <div class="pane props">
          <h3>属性</h3>
          <template v-if="selected">
            <div class="nx-field"><label>名称</label><input :value="selected.name" disabled /></div>
            <div class="nx-field"><label>编码</label><input :value="selected.code" disabled /></div>
            <div class="nx-field"><label>类型</label><input :value="selected.type" disabled /></div>
            <p class="nx-muted">字段更新 API 未提供。要改定义请走后端演进接口（POST fields 只追加）。</p>
          </template>
          <p v-else class="nx-muted">选择一个字段</p>
        </div>
      </div>
    </div>
  </div>
  <p v-else-if="error" class="nx-error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";

const route = useRoute();
const ledger = ref(null);
const fields = ref([]);
const system = ref([]);
const records = ref([]);
const selected = ref(null);
const tab = ref("records");
const error = ref("");

const palette = [
  { type: "text", label: "文本 → TEXT" },
  { type: "number", label: "数字 → NUMERIC" },
  { type: "boolean", label: "布尔 → BOOLEAN" },
  { type: "date", label: "日期 → TIMESTAMPTZ" },
];

const SYSTEM_SKIP = new Set(["tenant_id", "deleted_at", "ext_data", "schema_version"]);

const recordColumns = computed(() => {
  const fromFields = (fields.value || []).map((field) => field.code).filter(Boolean);
  if (fromFields.length) {
    return ["id", ...fromFields, "status"].filter((col, idx, all) => all.indexOf(col) === idx);
  }
  const keys = new Set();
  for (const row of records.value) {
    Object.keys(row || {}).forEach((key) => {
      if (!SYSTEM_SKIP.has(key) && key !== "_source") {
        keys.add(key);
      }
    });
  }
  return keys.size ? [...keys] : ["id"];
});

function formatCell(row, col) {
  const value = row?.[col];
  if (value == null) {
    return "—";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return value;
}

async function reload() {
  const id = route.params.id;
  ledger.value = await p3.getLedger(id);
  const fieldBody = await p3.listFields(id);
  fields.value = fieldBody.items || [];
  system.value = fieldBody.system || [];
  const recBody = await p3.listRecords(id);
  records.value = recBody.items || [];
}

onMounted(async () => {
  try {
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function add(kind) {
  try {
    const body = await p3.addField(route.params.id, {
      type: kind.type,
      name: `新${kind.label.split(" ")[0]}`,
      code: `${kind.type}_${Date.now().toString().slice(-4)}`,
    });
    ledger.value = body.ledger;
    selected.value = body.field;
    const fieldBody = await p3.listFields(route.params.id);
    fields.value = fieldBody.items || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}
</script>

<style scoped>
.sys { margin-top: 20px; }
</style>
