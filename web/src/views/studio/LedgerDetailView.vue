<template>
  <div v-if="ledger">
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>{{ ledger.name }}</h1>
        <p class="nx-code">{{ ledger.code }} · {{ ledger.physical_table }}</p>
      </div>
      <div class="nx-row">
        <NxStatus :label="`Draft v${ledger.draft_schema_version}`" tone="warning" />
        <NxStatus :label="`Running v${ledger.running_schema_version}`" tone="success" />
        <router-link class="ghost" to="/p3/ledgers">返回列表</router-link>
      </div>
    </div>
    <NxSourceBanner body="字段设计是壳：改 Draft，不改 Running。真实 SchemaService / 每台账一表 DDL 等待 M0-06。" />
    <p v-if="error" class="nx-error">{{ error }}</p>
    <div class="nx-tabs">
      <button :class="{ active: tab === 'records' }" type="button" @click="tab = 'records'">记录</button>
      <button :class="{ active: tab === 'designer' }" type="button" data-testid="ledger-designer-tab" @click="tab = 'designer'">字段设计</button>
    </div>

    <table v-if="tab === 'records'" class="nx-table">
      <thead>
        <tr>
          <th>事由</th>
          <th>金额</th>
          <th>申请人</th>
          <th>部门</th>
          <th>日期</th>
          <th>类别</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!records.length">
          <td colspan="7" class="nx-empty">暂无记录</td>
        </tr>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.title }}</td>
          <td>{{ row.amount }}</td>
          <td>{{ row.applicant }}</td>
          <td>{{ row.department }}</td>
          <td>{{ row.claim_date }}</td>
          <td>{{ row.category }}</td>
          <td>
            <NxStatus :label="row.status" :tone="tone(row.status)" />
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else>
      <div class="toolbar nx-row" style="margin-bottom:12px;">
        <span class="nx-muted">Palette → Canvas → Property。不能边编辑边改 Running。</span>
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
          <h3>Draft 字段</h3>
          <button
            v-for="field in fields"
            :key="field.id"
            class="nx-chip-btn"
            :class="{ active: selected?.id === field.id }"
            type="button"
            @click="selected = field"
          >
            <strong>{{ field.name }}</strong>
            <span class="nx-muted"> · {{ field.code }} · {{ field.type }}</span>
            <NxStatus v-if="!field.running" label="Draft only" tone="warning" />
          </button>
          <h3 class="sys">系统字段（只读）</h3>
          <p v-for="field in system" :key="field.code" class="nx-muted">{{ field.code }}</p>
        </div>
        <div class="pane props">
          <h3>属性</h3>
          <template v-if="selected">
            <div class="nx-field"><label>名称</label><input v-model="selected.name" /></div>
            <div class="nx-field"><label>编码</label><input :value="selected.code" disabled /></div>
            <div class="nx-field"><label>类型</label><input :value="selected.type" disabled /></div>
            <label class="nx-row"><input v-model="selected.required" type="checkbox" /> 必填</label>
            <button class="primary" type="button" style="margin-top:12px;" @click="save">写入 Draft</button>
          </template>
          <p v-else class="nx-muted">选择一个字段</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
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
  { type: "text", label: "文本" },
  { type: "longtext", label: "长文本" },
  { type: "number", label: "数字" },
  { type: "money", label: "金额" },
  { type: "date", label: "日期" },
  { type: "boolean", label: "布尔" },
  { type: "enum", label: "枚举" },
  { type: "person", label: "人员" },
  { type: "department", label: "部门" },
];

function tone(status) {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  return "warning";
}

async function reload() {
  const id = route.params.id;
  ledger.value = await p3.getLedger(id);
  const fieldBody = await p3.listFields(id);
  fields.value = fieldBody.items || [];
  system.value = fieldBody.system || [];
  const recBody = await p3.listRecords(id);
  records.value = recBody.items || recBody || [];
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
      name: `新${kind.label}`,
      code: `${kind.type}_${Date.now().toString().slice(-4)}`,
    });
    ledger.value = body.ledger;
    selected.value = body.field;
    const fieldBody = await p3.listFields(route.params.id);
    fields.value = fieldBody.items || [];
  } catch (err) {
    error.value = err.message;
  }
}

async function save() {
  if (!selected.value) return;
  try {
    const body = await p3.updateField(route.params.id, selected.value.id, {
      name: selected.value.name,
      required: selected.value.required,
    });
    ledger.value = body.ledger;
  } catch (err) {
    error.value = err.message;
  }
}
</script>

<style scoped>
.sys { margin-top: 20px; }
</style>
