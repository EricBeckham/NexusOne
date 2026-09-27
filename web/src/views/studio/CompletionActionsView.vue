<template>
  <main class="completion-actions-page">
    <NxPageHeader
      eyebrow="星构 · 流程配置"
      title="办结后动作"
      description="审批办结后，自动往另一张台账插入一行。只在同一租户内执行；动作失败不影响审批结果，可在执行记录里重试。"
      :crumbs="[{ label: '星构' }, { label: '办结后动作' }]"
    >
      <template #actions>
        <button class="ghost" type="button" :disabled="loading" @click="load">{{ loading ? "加载中…" : "刷新" }}</button>
        <button class="primary" type="button" data-testid="action-new" @click="startNew">新建动作</button>
      </template>
    </NxPageHeader>

    <NxErrorState
      v-if="error"
      title="办结后动作没有加载"
      :description="error"
      retry-label="重试"
      data-testid="actions-error"
      @retry="load"
    />
    <div v-else-if="loading" role="status" data-testid="actions-loading">
      <NxSkeleton v-for="n in 3" :key="n" variant="list-row" label="正在加载办结后动作" />
    </div>
    <template v-else>
      <NxEmpty
        v-if="!actions.length && !editing"
        title="还没有办结后动作"
        description="例如：「费用报销」审批通过后，在「付款台账」插入一行待付款。"
        data-testid="actions-empty"
      />
      <table v-if="actions.length" class="nx-table" data-testid="actions-table">
        <thead>
          <tr><th>名称</th><th>源台账 · 流程</th><th>触发</th><th>目标台账</th><th>状态</th><th /></tr>
        </thead>
        <tbody>
          <tr v-for="item in actions" :key="item.id" :data-testid="`action-row-${item.id}`">
            <td>{{ item.name }}</td>
            <td><span class="nx-code">{{ ledgerLabel(item.sourceLedgerCode) }}</span> · <span class="nx-code">{{ item.processKey }}</span></td>
            <td>{{ triggerLabel(item.trigger) }}</td>
            <td><span class="nx-code">{{ ledgerLabel(item.targetLedgerCode) }}</span></td>
            <td><NxStatusTag :status="item.enabled ? 'ENABLED' : 'DISABLED'" :label="item.enabled ? '启用' : '停用'" /></td>
            <td><button class="text-action" type="button" @click="edit(item)">编辑</button></td>
          </tr>
        </tbody>
      </table>
    </template>

    <section v-if="editing" class="nx-card editor" data-testid="action-editor">
      <h2>{{ draft.id ? "编辑动作" : "新建动作" }}</h2>
      <div class="grid">
        <label>名称<input v-model.trim="draft.name" data-testid="action-name" maxlength="128" /></label>
        <label>触发
          <select v-model="draft.trigger" data-testid="action-trigger">
            <option v-for="item in TRIGGERS" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
        <label>源台账（提交表单写入的台账）
          <select v-model="draft.sourceLedgerCode" data-testid="action-source">
            <option value="">请选择</option>
            <option v-for="item in readyLedgers" :key="item.code" :value="item.code">{{ item.name || item.code }}</option>
          </select>
        </label>
        <label>流程
          <select v-if="processKeys.length" v-model="draft.processKey" data-testid="action-process">
            <option value="">请选择</option>
            <option v-for="key in processKeys" :key="key" :value="key">{{ key }}</option>
          </select>
          <input v-else v-model.trim="draft.processKey" data-testid="action-process" placeholder="流程 key" />
        </label>
        <label>目标台账
          <select v-model="draft.targetLedgerCode" data-testid="action-target" @change="resetMapping">
            <option value="">请选择</option>
            <option v-for="item in targetChoices" :key="item.code" :value="item.code">{{ item.name || item.code }}</option>
          </select>
        </label>
        <label class="inline"><input v-model="draft.enabled" type="checkbox" data-testid="action-enabled" /> 启用</label>
      </div>

      <h3>字段映射</h3>
      <p v-if="!draft.targetLedgerCode" class="nx-muted">先选择目标台账。</p>
      <table v-else class="nx-table mapping" data-testid="action-mapping">
        <thead><tr><th>目标字段</th><th>来源</th><th>取值</th></tr></thead>
        <tbody>
          <tr v-for="row in draft.fieldMapping" :key="row.target" :data-testid="`mapping-${row.target}`">
            <td>{{ fieldLabel(targetFields, row.target) }}<span v-if="isRequired(row.target)" class="required" title="必填">*</span></td>
            <td>
              <select v-model="row.kind" :data-testid="`mapping-kind-${row.target}`" @change="row.value = ''">
                <option v-for="item in SOURCE_KINDS" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </td>
            <td>
              <select v-if="row.kind === 'FIELD'" v-model="row.value" :data-testid="`mapping-value-${row.target}`">
                <option value="">请选择</option>
                <option v-for="field in sourceFields" :key="field.code" :value="field.code">{{ field.name || field.code }}</option>
              </select>
              <select v-else-if="row.kind === 'SYSTEM'" v-model="row.value" :data-testid="`mapping-value-${row.target}`">
                <option value="">请选择</option>
                <option v-for="item in SYSTEM_VALUES" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
              <input v-else-if="row.kind === 'CONST'" v-model="row.value" :data-testid="`mapping-value-${row.target}`" />
              <span v-else class="nx-muted">—</span>
            </td>
          </tr>
        </tbody>
      </table>

      <ul v-if="problems.length" class="problems" data-testid="action-problems">
        <li v-for="item in problems" :key="item">{{ item }}</li>
      </ul>
      <p v-if="saveError" class="field-error" data-testid="action-save-error">{{ saveError }}</p>

      <div class="actions">
        <button class="primary" type="button" :disabled="saving" data-testid="action-save" @click="save">{{ saving ? "保存中…" : "保存" }}</button>
        <button class="ghost" type="button" @click="cancel">取消</button>
      </div>

      <div v-if="draft.id" class="preview" data-testid="action-preview">
        <h3>试算</h3>
        <p class="nx-muted">输入一张源单的记录编号，预览将插入的一行。不会写入目标台账。</p>
        <input v-model.trim="previewRecordId" placeholder="源单记录编号" data-testid="action-preview-record" />
        <button class="ghost" type="button" data-testid="action-preview-run" @click="runPreview">试算</button>
        <p v-if="previewError" class="field-error">{{ previewError }}</p>
        <dl v-if="previewRow" class="preview-row" data-testid="action-preview-row">
          <template v-for="(value, key) in previewRow" :key="key">
            <dt>{{ fieldLabel(targetFields, key) }}</dt><dd>{{ value ?? "（空）" }}</dd>
          </template>
        </dl>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton, NxStatusTag } from "../../components";
import { p3 } from "../../api/p3";
import { explainPlatformRead } from "../../api/platformReadError";
import {
  NOT_MAPPABLE, SOURCE_KINDS, SYSTEM_VALUES, TRIGGERS, actionProblems, completionActions,
} from "../../api/completionActions";

const actions = ref([]);
const ledgers = ref([]);
const processKeys = ref([]);
const loading = ref(true);
const error = ref("");
const editing = ref(false);
const saving = ref(false);
const saveError = ref("");
const problems = ref([]);
const previewRecordId = ref("");
const previewRow = ref(null);
const previewError = ref("");
const draft = reactive(emptyDraft());

function emptyDraft() {
  return { id: null, name: "", sourceLedgerCode: "", processKey: "", trigger: "APPROVED", targetLedgerCode: "",
    fieldMapping: [], enabled: true, version: 1 };
}

const readyLedgers = computed(() => ledgers.value.filter((item) => item.status === "READY"));
const targetChoices = computed(() => readyLedgers.value.filter((item) => item.code !== draft.sourceLedgerCode));
const sourceFields = computed(() => fieldsOf(draft.sourceLedgerCode));
const targetFields = computed(() => fieldsOf(draft.targetLedgerCode).filter((field) => !NOT_MAPPABLE.has(field.code)));

function fieldsOf(code) {
  return (ledgers.value.find((item) => item.code === code)?.fields || []).filter((field) => field.code);
}

function fieldLabel(fields, code) {
  const field = fields.find((item) => item.code === code);
  return field?.name ? `${field.name}（${code}）` : code;
}

function isRequired(code) {
  return Boolean(targetFields.value.find((field) => field.code === code)?.required);
}

function ledgerLabel(code) {
  return ledgers.value.find((item) => item.code === code)?.name || code;
}

function triggerLabel(value) {
  return TRIGGERS.find((item) => item.value === value)?.label || value;
}

/** One mapping row per mappable target field, keeping what is already configured. */
function resetMapping() {
  const existing = new Map(draft.fieldMapping.map((row) => [row.target, row]));
  draft.fieldMapping = targetFields.value.map((field) => existing.get(field.code) || { target: field.code, kind: "", value: "" });
}

function startNew() {
  Object.assign(draft, emptyDraft());
  resetState();
  editing.value = true;
}

function edit(item) {
  Object.assign(draft, { ...emptyDraft(), ...item, fieldMapping: item.fieldMapping.map((row) => ({ ...row })) });
  resetMapping();
  resetState();
  editing.value = true;
}

function cancel() {
  editing.value = false;
  resetState();
}

function resetState() {
  saveError.value = "";
  problems.value = [];
  previewRow.value = null;
  previewError.value = "";
  previewRecordId.value = "";
}

async function save() {
  saving.value = true;
  saveError.value = "";
  problems.value = [];
  try {
    const saved = draft.id ? await completionActions.update(draft.id, draft) : await completionActions.create(draft);
    Object.assign(draft, saved);
    resetMapping();
    await loadActions();
  } catch (err) {
    problems.value = actionProblems(err);
    saveError.value = err?.status === 409 ? "动作已被他人修改，请刷新后再改。" : explainPlatformRead(err, "保存失败");
  } finally {
    saving.value = false;
  }
}

async function runPreview() {
  previewError.value = "";
  previewRow.value = null;
  if (!previewRecordId.value) {
    previewError.value = "请输入源单记录编号。";
    return;
  }
  try {
    previewRow.value = await completionActions.preview(draft.id, previewRecordId.value, null);
  } catch (err) {
    previewError.value = explainPlatformRead(err, "试算失败");
  }
}

async function loadActions() {
  actions.value = await completionActions.list();
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [listed, ledgerBody, defs] = await Promise.all([
      completionActions.list(),
      p3.listLedgers(),
      p3.listProcessDefinitions().catch(() => []),
    ]);
    actions.value = listed;
    ledgers.value = ledgerBody?.items || [];
    processKeys.value = [...new Set((Array.isArray(defs) ? defs : [])
      .map((row) => row?.key || row?.process_key || row?.processKey)
      .filter(Boolean))];
  } catch (err) {
    error.value = err?.status === 403 ? "当前账号没有配置办结后动作的权限（需租户管理员）。" : explainPlatformRead(err, "加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.completion-actions-page { display: grid; gap: var(--nx-space-3); }
.editor { display: grid; gap: var(--nx-space-3); }
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: var(--nx-space-3); }
.grid label { display: grid; gap: 4px; font-size: var(--nx-font-size-sm); }
.grid label.inline { display: flex; align-items: center; gap: 8px; }
.required { color: var(--nx-danger, #d4380d); margin-left: 2px; }
.problems { margin: 0; padding-left: 18px; color: var(--nx-danger, #d4380d); }
.actions { display: flex; gap: var(--nx-space-2); }
.preview { display: grid; gap: var(--nx-space-2); border-top: 1px solid var(--nx-border); padding-top: var(--nx-space-3); }
.preview-row { display: grid; grid-template-columns: max-content 1fr; gap: 4px 16px; margin: 0; }
</style>
