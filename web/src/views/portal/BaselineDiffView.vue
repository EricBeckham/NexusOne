<template>
  <main class="baseline-diff" data-testid="baseline-diff">
    <NxPageHeader
      eyebrow="P3 · 星构"
      title="基线差异"
      description="平台版本已经往前走，而租户覆盖还停在旧基线上。逐项看完差异后再合并。平台原有字段不能删除，也不能改类型。"
    />
    <p v-if="error" role="alert" class="nx-error">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <NxSkeleton v-if="loading" variant="list-row" label="正在读取基线差异" />
    <p v-else-if="!rows.length" data-testid="baseline-empty">没有停在旧基线上的覆盖。</p>
    <article v-for="row in rows" :key="rowKey(row)" class="nx-card" data-testid="baseline-row">
      <h2>{{ typeLabel(row.objectType || row.object_type) }} · {{ row.objectCode || row.object_code }}</h2>
      <p>{{ row.baseVersion || row.base_version }} → {{ row.platformVersion || row.platform_version }}</p>
      <ul>
        <li v-for="change in row.changes || []" :key="change.path" data-testid="baseline-change">
          <code>{{ change.path }}</code>
          {{ kindLabel(change.kind) }}
          <span v-if="change.kind === 'CONFLICT'" class="choices">
            <label><input type="radio" :name="choiceName(row, change.path)" value="TENANT" v-model="choices[choiceName(row, change.path)]" /> 保留租户</label>
            <label><input type="radio" :name="choiceName(row, change.path)" value="PLATFORM" v-model="choices[choiceName(row, change.path)]" /> 采用平台</label>
          </span>
        </li>
      </ul>
      <p v-if="unresolved(row).length" data-testid="baseline-unresolved">还有 {{ unresolved(row).length }} 处冲突没有选择。</p>
      <button class="nx-btn nx-btn--primary" type="button" data-testid="baseline-merge" :disabled="busy || unresolved(row).length > 0" @click="merge(row)">合并到当前平台版本</button>
    </article>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { NxPageHeader, NxSkeleton } from "../../components";
import { baselineDrift, mergeBaseline } from "../../api/portal";

const TYPES = { FORM: "表单", PROCESS: "流程", ROLE: "角色" };
const KINDS = { TENANT_ONLY: "仅租户新增", PLATFORM_ONLY: "仅平台新增", CONFLICT: "两边不一致" };

const rows = ref([]);
const choices = reactive({});
const loading = ref(true);
const busy = ref(false);
const error = ref("");
const notice = ref("");

function typeLabel(type) { return TYPES[type] || type || "对象"; }
function kindLabel(kind) { return KINDS[kind] || kind; }
function rowKey(row) { return `${row.instanceId || row.instance_id}|${row.objectType || row.object_type}|${row.objectCode || row.object_code}`; }
function choiceName(row, path) { return `${rowKey(row)}|${path}`; }
function unresolved(row) {
  return (row.changes || []).filter((change) => change.kind === "CONFLICT" && !choices[choiceName(row, change.path)]);
}
async function load() {
  loading.value = true; error.value = "";
  try { rows.value = (await baselineDrift()) || []; }
  catch (failure) { error.value = failure?.message || "差异读取失败"; rows.value = []; }
  finally { loading.value = false; }
}
async function merge(row) {
  const picked = {};
  for (const change of row.changes || []) {
    if (change.kind === "CONFLICT") picked[change.path] = choices[choiceName(row, change.path)];
  }
  busy.value = true; error.value = ""; notice.value = "";
  try {
    await mergeBaseline(row.instanceId || row.instance_id, row.objectType || row.object_type, row.objectCode || row.object_code, picked);
    notice.value = "已合并到当前平台版本。";
    await load();
  } catch (failure) {
    const paths = failure?.body?.details?.paths || failure?.body?.paths;
    error.value = Array.isArray(paths) && paths.length ? `还有冲突未选择：${paths.join("、")}` : (failure?.message || "合并失败");
  } finally { busy.value = false; }
}
onMounted(load);
</script>

<style scoped>
.baseline-diff { display: flex; flex-direction: column; gap: var(--nx-space-4); }
.choices { display: inline-flex; gap: var(--nx-space-3); margin-left: var(--nx-space-3); }
.nx-error { color: var(--nx-danger); }
</style>
