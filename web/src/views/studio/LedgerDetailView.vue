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
    <NxSourceBanner body="T3-E 记录增删改查统一走 /api/v1/p3/ledgers/{id}/records；更新携带 version 做乐观锁，查询继续由服务端绑定 TenantContext 与 DataScope。" />
    <div v-if="readDecision" class="nx-card" style="margin-bottom:16px;" data-testid="g2-ledger-decision">
      <h3>ledger/read 判定</h3>
      <p class="nx-muted">POST /api/v1/permission/check — 与记录 list/detail 同一引擎，不是前端拼的权限码。</p>
      <p class="nx-code">{{ JSON.stringify(readDecision) }}</p>
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <div class="nx-tabs">
      <button :class="{ active: tab === 'records' }" type="button" @click="tab = 'records'">记录</button>
      <button :class="{ active: tab === 'designer' }" type="button" data-testid="ledger-designer-tab" @click="tab = 'designer'">字段设计</button>
    </div>

    <div v-if="tab === 'records'">
      <div class="nx-row" style="justify-content:flex-end;margin-bottom:12px;">
        <button class="primary" type="button" data-testid="record-create-toggle" @click="beginCreate">新增记录</button>
      </div>
      <form v-if="recordEditor" class="nx-card" style="margin-bottom:16px;" data-testid="record-editor" @submit.prevent="saveRecord">
        <h3>{{ editingRecordId ? "编辑记录" : "新增记录" }}</h3>
        <div class="nx-row">
          <div v-for="field in fields" :key="field.code" class="nx-field">
            <label>{{ field.name }}（{{ field.code }}）</label>
            <input
              v-if="field.type !== 'boolean'"
              v-model="recordDraft[field.code]"
              :type="field.type === 'number' || field.type === 'numeric' || field.type === 'integer' ? 'number' : 'text'"
              :required="field.required"
              :data-testid="`record-field-${field.code}`"
            />
            <select v-else v-model="recordDraft[field.code]" :required="field.required" :data-testid="`record-field-${field.code}`">
              <option :value="true">是</option><option :value="false">否</option>
            </select>
          </div>
        </div>
        <div class="nx-row">
          <button class="primary" type="submit" data-testid="record-save">保存</button>
          <button class="ghost" type="button" @click="cancelRecord">取消</button>
        </div>
      </form>
      <p v-if="notice" class="nx-muted">{{ notice }}</p>
      <table class="nx-table" data-testid="record-table">
      <thead>
        <tr>
          <th v-for="col in recordColumns" :key="col">{{ col }}</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!records.length">
          <td :colspan="recordColumns.length + 1" class="nx-empty">暂无记录</td>
        </tr>
        <tr v-for="row in records" :key="row.id || JSON.stringify(row)">
          <td v-for="col in recordColumns" :key="col">
            <router-link v-if="col === 'id'" :to="`/p3/ledgers/${route.params.id}/records/${row.id}`" data-testid="record-detail-link">
              {{ formatCell(row, col) }}
            </router-link>
            <template v-else>{{ formatCell(row, col) }}</template>
          </td>
          <td>
            <button class="ghost" type="button" data-testid="record-edit" @click="beginEdit(row)">编辑</button>
            <button class="danger" type="button" data-testid="record-delete" @click="removeRecord(row)">删除</button>
          </td>
        </tr>
      </tbody>
      </table>
    </div>

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
import { perm } from "../../api/client";

const route = useRoute();
const ledger = ref(null);
const fields = ref([]);
const system = ref([]);
const records = ref([]);
const selected = ref(null);
const tab = ref("records");
const error = ref("");
const readDecision = ref(null);
const recordEditor = ref(false);
const editingRecordId = ref("");
const editingVersion = ref(null);
const recordDraft = ref({});
const notice = ref("");

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

function valuesFromDraft() {
  const values = {};
  for (const field of fields.value) {
    const value = recordDraft.value[field.code];
    if (value === "" || value == null) {
      continue;
    }
    values[field.code] = ["number", "numeric", "integer"].includes(field.type) ? Number(value) : value;
  }
  return values;
}

function beginCreate() {
  editingRecordId.value = "";
  editingVersion.value = null;
  recordDraft.value = Object.fromEntries(fields.value.map((field) => [field.code, field.type === "boolean" ? false : ""]));
  recordEditor.value = true;
  notice.value = "";
}

function beginEdit(row) {
  editingRecordId.value = row.id;
  editingVersion.value = row.version;
  recordDraft.value = Object.fromEntries(fields.value.map((field) => [field.code, row[field.code] ?? (field.type === "boolean" ? false : "")]));
  recordEditor.value = true;
  notice.value = "";
}

function cancelRecord() {
  recordEditor.value = false;
  editingRecordId.value = "";
  recordDraft.value = {};
}

async function saveRecord() {
  error.value = "";
  try {
    if (editingRecordId.value) {
      await p3.updateRecord(route.params.id, editingRecordId.value, valuesFromDraft(), editingVersion.value);
      notice.value = "记录已更新。";
    } else {
      await p3.createRecord(route.params.id, valuesFromDraft());
      notice.value = "记录已创建。";
    }
    cancelRecord();
    const recBody = await p3.listRecords(route.params.id);
    records.value = recBody.items || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}

async function removeRecord(row) {
  if (!globalThis.confirm(`确认删除记录 ${row.id}？`)) {
    return;
  }
  error.value = "";
  try {
    await p3.deleteRecord(route.params.id, row.id);
    notice.value = "记录已删除。";
    const recBody = await p3.listRecords(route.params.id);
    records.value = recBody.items || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}

async function reload() {
  const id = route.params.id;
  ledger.value = await p3.getLedger(id);
  const fieldBody = await p3.listFields(id);
  fields.value = fieldBody.items || [];
  system.value = fieldBody.system || [];
  const recBody = await p3.listRecords(id);
  records.value = recBody.items || [];
  try {
    readDecision.value = await perm.check({ resource: "ledger", action: "read" });
  } catch (err) {
    readDecision.value = err.body || { allowed: false, message: err.message };
  }
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
.danger { color: #b42318; background: transparent; border: 1px solid #fecdca; border-radius: 6px; padding: 6px 10px; margin-left: 6px; }
</style>
