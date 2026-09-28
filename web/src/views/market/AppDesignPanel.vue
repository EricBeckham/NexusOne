<template>
  <section class="nx-card app-design" data-testid="app-design" aria-label="应用设计">
    <h2>应用设计</h2>
    <p v-if="error" role="alert" class="nx-error">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>

    <div data-testid="category-editor">
      <h3>应用分类</h3>
      <ul v-if="categories.length" class="category-list">
        <li v-for="category in categories" :key="category.code">
          <button type="button" class="ghost" :data-testid="`category-${category.code}`" @click="editCategory(category)">
            {{ category.name }} <code>{{ category.code }}</code> · 排序 {{ category.sortOrder }}
          </button>
        </li>
      </ul>
      <p v-else class="nx-muted">还没有分类。</p>
      <form novalidate @submit.prevent="saveCategory">
        <label>分类编码<input v-model.trim="draft.code" required pattern="[a-z][a-z0-9_]{0,23}" :readonly="editing" data-testid="category-code" /></label>
        <label>名称<input v-model.trim="draft.name" required data-testid="category-name" /></label>
        <label>说明<input v-model.trim="draft.description" data-testid="category-description" /></label>
        <label>排序<input v-model.number="draft.sortOrder" type="number" required data-testid="category-sort" /></label>
        <div class="actions">
          <button class="primary" type="button" :disabled="busy" data-testid="category-save" @click="saveCategory">{{ editing ? "保存分类" : "新建分类" }}</button>
          <button v-if="editing" class="ghost" type="button" :disabled="busy" data-testid="category-delete" @click="removeCategory">删除分类</button>
          <button v-if="editing" class="ghost" type="button" @click="resetCategory">改为新建</button>
        </div>
      </form>
      <form v-if="app" @submit.prevent="assignCategory">
        <h3>为 {{ app.name }} 指定分类</h3>
        <label>分类
          <select v-model="appCategory" data-testid="app-category">
            <option value="">不指定</option>
            <option v-for="category in categories" :key="category.code" :value="category.code">{{ category.name }}</option>
          </select>
        </label>
        <button class="primary" type="button" :disabled="busy" data-testid="app-category-save" @click="assignCategory">保存应用分类</button>
      </form>
    </div>

    <div data-testid="manifest-editor">
      <h3>版本清单</h3>
      <p v-if="!versions.length" class="nx-muted">选择应用包并创建版本后，可以在这里编辑清单。</p>
      <template v-else>
        <label>版本
          <select v-model="versionId" data-testid="manifest-version">
            <option v-for="version in versions" :key="version.id" :value="version.id">{{ version.version }} · {{ version.status }}</option>
          </select>
        </label>
        <label>清单 JSON
          <textarea v-model="manifestText" rows="16" spellcheck="false" data-testid="manifest-json" />
        </label>
        <div class="actions">
          <button class="ghost" type="button" :disabled="busy || !versionId" data-testid="manifest-validate" @click="validateManifest">校验清单</button>
          <button class="primary" type="button" :disabled="busy || !draftVersion" data-testid="manifest-save" @click="saveManifest">保存清单</button>
        </div>
        <p v-if="!draftVersion" class="nx-muted">只有草稿版本可以改清单。已发布版本保持不变。</p>
        <p v-if="checked && !problems.length" data-testid="manifest-valid">清单校验通过，没有待改问题。</p>
        <ul v-if="problems.length" data-testid="manifest-problems">
          <li v-for="(problem, index) in problems" :key="`${problem.path}-${problem.reason}-${index}`" data-testid="manifest-problem">
            <code>{{ problem.path || "/" }}</code>
            {{ reasonLabel(problem.reason) }}
            <code>{{ problem.reason }}</code>
          </li>
        </ul>
      </template>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { p4 } from "../../api/client";

const props = defineProps({
  app: { type: Object, default: null },
  versions: { type: Array, default: () => [] },
});

const REASONS = {
  required: "缺少必填内容",
  required_array: "这里必须是列表",
  required_object: "这里必须是对象",
  invalid_schema: "清单格式标识不正确",
  invalid_code: "编码格式不正确",
  invalid_type: "字段类型不正确",
  invalid_purpose: "表单用途不正确",
  invalid_layout: "布局不正确",
  invalid_kind: "入口类型不正确",
  invalid_action: "授权动作不正确",
  invalid_scope: "授权范围不正确",
  invalid_assignee_type: "办理人类型不正确",
  invalid_entry_key: "入口键不正确",
  invalid_json: "清单不是合法的 JSON",
  duplicate_code: "编码重复",
  duplicate_or_reserved_code: "编码重复或为保留字",
  unknown_ref: "引用了不存在的对象",
  unknown_or_wrong_purpose_ref: "节点表单不存在或不是审批表单",
  wrong_purpose_ref: "流程绑定的不是填写表单",
  kind_purpose_mismatch: "入口类型与表单用途不一致",
  process_form_mismatch: "入口流程与表单不一致",
  write_requires_fill: "写授权只能给填写入口",
  app_code_mismatch: "应用编码与版本不一致",
  version_mismatch: "版本号与版本记录不一致",
  category_mismatch: "分类与应用当前分类不一致",
};

const categories = ref([]);
const editing = ref(false);
const draft = reactive({ code: "", name: "", description: "", sortOrder: 0, version: 1 });
const appCategory = ref("");
const versionId = ref("");
const manifestText = ref("");
const problems = ref([]);
const checked = ref(false);
const busy = ref(false);
const error = ref("");
const notice = ref("");

const draftVersion = computed(() => props.versions.find((version) => version.id === versionId.value)?.status === "DRAFT");

function reasonLabel(reason) { return REASONS[reason] || reason || "校验未通过"; }
function camel(row) {
  if (!row || typeof row !== "object") return row;
  return {
    ...row,
    sortOrder: row.sortOrder ?? row.sort_order ?? 0,
    version: row.version ?? 1,
  };
}
function resetCategory() {
  editing.value = false;
  draft.code = ""; draft.name = ""; draft.description = ""; draft.sortOrder = 0; draft.version = 1;
}
function editCategory(category) {
  editing.value = true;
  draft.code = category.code;
  draft.name = category.name || "";
  draft.description = category.description || "";
  draft.sortOrder = category.sortOrder ?? 0;
  draft.version = category.version ?? 1;
}
function parseManifest() {
  try { return { ok: true, value: JSON.parse(manifestText.value) }; }
  catch { return { ok: false, problems: [{ path: "", reason: "invalid_json" }] }; }
}
function blankManifest() {
  const version = props.versions.find((item) => item.id === versionId.value);
  return {
    schema: "nexus.app-manifest.v1",
    app_code: props.app?.code || "",
    version: version?.version || "",
    category_code: appCategory.value || "",
    name: props.app?.name || "",
    ledgers: [], forms: [], processes: [], role_pack: [], entries: [],
  };
}

async function loadCategories() {
  categories.value = (await p4.appCategories() || []).map(camel);
}
async function saveCategory() {
  busy.value = true; error.value = ""; notice.value = "";
  try {
    const body = { name: draft.name, description: draft.description || null, sort_order: Number(draft.sortOrder) || 0 };
    if (editing.value) {
      await p4.updateAppCategory(draft.code, { ...body, version: draft.version });
      notice.value = "分类已保存。";
    } else {
      await p4.createAppCategory(draft.code, body);
      notice.value = "分类已新建。";
    }
    await loadCategories();
    const saved = categories.value.find((category) => category.code === draft.code);
    if (saved) editCategory(saved);
  } catch (failure) { error.value = failure?.message || "分类保存失败"; }
  finally { busy.value = false; }
}
async function removeCategory() {
  busy.value = true; error.value = ""; notice.value = "";
  try {
    await p4.deleteAppCategory(draft.code);
    notice.value = "分类已删除。";
    resetCategory();
    await loadCategories();
  } catch (failure) { error.value = failure?.message || "分类删除失败"; }
  finally { busy.value = false; }
}
async function assignCategory() {
  busy.value = true; error.value = ""; notice.value = "";
  try {
    await p4.setAppCategory(props.app.id, appCategory.value || null);
    notice.value = appCategory.value ? "应用分类已保存。" : "已清除应用分类。";
  } catch (failure) { error.value = failure?.message || "应用分类保存失败"; }
  finally { busy.value = false; }
}
async function loadManifest(id) {
  checked.value = false; problems.value = [];
  if (!id || typeof p4.manifest !== "function") { manifestText.value = JSON.stringify(blankManifest(), null, 2); return; }
  try {
    const snapshot = await p4.manifest(id);
    const body = snapshot?.manifest;
    manifestText.value = JSON.stringify(body || blankManifest(), null, 2);
  } catch (failure) {
    error.value = failure?.message || "清单读取失败";
    manifestText.value = JSON.stringify(blankManifest(), null, 2);
  }
}
async function validateManifest() {
  const parsed = parseManifest();
  checked.value = true;
  if (!parsed.ok) { problems.value = parsed.problems; notice.value = ""; return; }
  busy.value = true; error.value = ""; notice.value = "";
  try {
    const result = await p4.validateManifest(versionId.value, parsed.value);
    problems.value = Array.isArray(result?.problems) ? result.problems : [];
    notice.value = problems.value.length ? `共 ${problems.value.length} 个校验问题。` : "";
  } catch (failure) {
    problems.value = Array.isArray(failure?.body?.problems) ? failure.body.problems : [];
    if (!problems.value.length) error.value = failure?.message || "校验失败";
  } finally { busy.value = false; }
}
async function saveManifest() {
  const parsed = parseManifest();
  checked.value = true;
  if (!parsed.ok) { problems.value = parsed.problems; return; }
  busy.value = true; error.value = ""; notice.value = "";
  try {
    const saved = await p4.saveManifest(versionId.value, parsed.value);
    if (saved?.manifest) manifestText.value = JSON.stringify(saved.manifest, null, 2);
    problems.value = [];
    notice.value = "清单已保存。";
  } catch (failure) {
    problems.value = Array.isArray(failure?.body?.problems) ? failure.body.problems : [];
    notice.value = problems.value.length ? `共 ${problems.value.length} 个校验问题。` : "";
    if (!problems.value.length) error.value = failure?.message || "清单保存失败";
  } finally { busy.value = false; }
}

watch(() => props.versions, (rows) => {
  if (!rows?.some((version) => version.id === versionId.value)) {
    versionId.value = rows?.find((version) => version.status === "DRAFT")?.id || rows?.[0]?.id || "";
  }
}, { immediate: true });
watch(versionId, (id) => { if (id) loadManifest(id); }, { immediate: true });
onMounted(loadCategories);
</script>

<style scoped>
.app-design { display: flex; flex-direction: column; gap: var(--nx-space-4); margin-block: var(--nx-space-6); }
form, .actions { display: flex; flex-wrap: wrap; gap: var(--nx-space-3); align-items: end; }
label { display: flex; flex-direction: column; gap: var(--nx-form-label-gap); min-width: 12rem; }
textarea { width: 100%; font-family: ui-monospace, monospace; }
.category-list { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); padding: 0; list-style: none; }
.nx-error { color: var(--nx-danger); }
</style>
