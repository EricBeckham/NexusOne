<template>
  <NxPageHeader
    eyebrow="星域 / 表单模板"
    :title="creating ? '新建表单模板' : (name || '表单模板')"
    description="左侧维护字段列表，右侧只读预览。不做拖拽设计器。"
  >
    <template #actions>
      <router-link class="ghost" to="/p1/form-templates">返回列表</router-link>
    </template>
  </NxPageHeader>

  <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="reload" />

  <section class="nx-card meta">
    <label class="nx-field">编码<input v-model="code" data-testid="template-code" maxlength="80" /></label>
    <label class="nx-field">名称<input v-model="name" data-testid="template-name" maxlength="160" /></label>
    <label class="nx-field">说明<textarea v-model="description" data-testid="template-description" rows="2" /></label>
    <p class="nx-muted" data-testid="template-status">状态：{{ statusLabel(status) }}</p>
    <div class="nx-row">
      <button class="primary" type="button" data-testid="template-save" :disabled="saving" @click="save">
        {{ saving ? "保存中…" : "保存草稿" }}
      </button>
      <button class="ghost" type="button" data-testid="template-validate" :disabled="creating || validating" @click="validate">
        {{ validating ? "校验中…" : "校验" }}
      </button>
      <button class="ghost" type="button" data-testid="template-publish" :disabled="creating || publishing" @click="openPublish">发布</button>
    </div>
    <p v-if="validation?.valid" class="ok" data-testid="template-validate-ok">校验通过</p>
    <p v-if="publishedVersion != null" class="ok" data-testid="template-publish-version">已发布版本 {{ publishedVersion }}</p>
    <p v-if="publishError" class="field-error" role="alert" data-testid="template-publish-error">{{ publishError }}</p>
    <ul v-if="formErrors.length" class="errors" data-testid="template-validate-errors">
      <li v-for="(message, index) in formErrors" :key="index">{{ message }}</li>
    </ul>
  </section>

  <div class="editor">
    <section class="nx-card" data-testid="template-fields">
      <h2>字段</h2>
      <p v-if="!fields.length" class="nx-muted" data-testid="template-fields-empty">暂无字段</p>
      <article v-for="(field, index) in fields" :key="index" class="field" data-testid="template-field">
        <label class="nx-field">名称<input v-model="field.label" data-testid="field-label" maxlength="160" /></label>
        <label class="nx-field">key<input v-model="field.key" data-testid="field-key" maxlength="80" /></label>
        <label class="nx-field">类型
          <select v-model="field.type" data-testid="field-type">
            <option v-for="item in FIELD_TYPES" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
        <label class="check"><input v-model="field.required" type="checkbox" data-testid="field-required" /> 必填</label>
        <p v-for="(message, messageIndex) in fieldErrors(field)" :key="messageIndex" class="field-error" data-testid="field-error">{{ message }}</p>
        <div v-if="field.type === 'SELECT'" class="options" data-testid="field-options">
          <p>选项</p>
          <div v-for="(option, optionIndex) in field.options" :key="optionIndex" class="option-row">
            <input v-model="option.label" placeholder="显示名" data-testid="option-label" />
            <input v-model="option.value" placeholder="值" data-testid="option-value" />
            <button class="ghost" type="button" @click="field.options.splice(optionIndex, 1)">删除选项</button>
          </div>
          <button class="ghost" type="button" data-testid="add-option" @click="field.options.push({ label: '', value: '' })">添加选项</button>
        </div>
        <div class="nx-row">
          <button class="ghost" type="button" :disabled="index === 0" @click="moveField(index, -1)">上移</button>
          <button class="ghost" type="button" :disabled="index === fields.length - 1" @click="moveField(index, 1)">下移</button>
          <button class="ghost" type="button" data-testid="remove-field" @click="fields.splice(index, 1)">删除</button>
        </div>
      </article>
      <button class="ghost" type="button" data-testid="add-field" @click="addField">添加字段</button>
    </section>

    <section class="nx-card preview" data-testid="template-preview">
      <h2>预览</h2>
      <p v-if="!fields.length" class="nx-muted" data-testid="template-preview-empty">暂无字段</p>
      <fieldset v-else disabled>
        <label v-for="(field, index) in fields" :key="index" class="nx-field" data-testid="preview-field">
          <span>{{ field.label || "未命名字段" }}<template v-if="field.required"> *</template></span>
          <textarea v-if="field.type === 'TEXTAREA'" rows="3" :placeholder="field.key" />
          <select v-else-if="field.type === 'SELECT'">
            <option v-if="!filledOptions(field).length" value="">暂无选项</option>
            <option v-for="option in filledOptions(field)" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <select v-else-if="field.type === 'PERSON'">
            <option value="">选择成员</option>
          </select>
          <input v-else-if="field.type === 'ATTACHMENT'" type="file" />
          <input v-else-if="field.type === 'NUMBER'" type="number" :placeholder="field.key" />
          <input v-else-if="field.type === 'DATE'" type="date" />
          <input v-else-if="field.type === 'MONEY'" type="text" inputmode="decimal" placeholder="0.00" />
          <input v-else type="text" :placeholder="field.key" />
        </label>
      </fieldset>
    </section>
  </div>

  <div v-if="publishConfirm" class="confirm-backdrop" data-testid="template-publish-confirm">
    <section class="confirm-dialog nx-card" role="dialog" aria-modal="true" aria-labelledby="template-publish-title">
      <h2 id="template-publish-title">确认发布？</h2>
      <p>发布后生成不可变版本，已订阅租户不会自动升级。</p>
      <div class="confirm-actions">
        <button type="button" class="ghost" data-testid="template-publish-cancel" :disabled="publishing" @click="publishConfirm = false">取消</button>
        <button type="button" class="primary" data-testid="template-publish-submit" :disabled="publishing" @click="publish">
          {{ publishing ? "发布中…" : "确认发布" }}
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { p1 } from "../../api/client";
import { NxErrorState, NxPageHeader } from "../../components";

const FIELD_TYPES = [
  { value: "TEXT", label: "文本" },
  { value: "TEXTAREA", label: "多行文本" },
  { value: "NUMBER", label: "数字" },
  { value: "MONEY", label: "金额" },
  { value: "DATE", label: "日期" },
  { value: "SELECT", label: "单选下拉" },
  { value: "PERSON", label: "成员选择" },
  { value: "ATTACHMENT", label: "附件" },
];

const route = useRoute();
const router = useRouter();
const creating = computed(() => route.name === "form-template-new");
const code = ref("");
const name = ref("");
const description = ref("");
const status = ref("DRAFT");
const fields = ref([]);
const error = ref("");
const traceId = ref("");
const saving = ref(false);
const validating = ref(false);
const validation = ref(null);
const publishing = ref(false);
const publishConfirm = ref(false);
const publishError = ref("");
const publishedVersion = ref(null);

function statusLabel(value) {
  return ({ DRAFT: "草稿", VALIDATED: "已校验", PUBLISHED: "已发布", ARCHIVED: "已归档" })[value] || value || "暂无";
}

function blankField() {
  return { key: "", label: "", type: "TEXT", required: false, options: [] };
}

function addField() {
  fields.value.push(blankField());
}

function moveField(index, delta) {
  const next = index + delta;
  if (next < 0 || next >= fields.value.length) return;
  const copy = fields.value.slice();
  const [item] = copy.splice(index, 1);
  copy.splice(next, 0, item);
  fields.value = copy;
}

function normalizeOptions(options) {
  if (!Array.isArray(options)) return [];
  return options.map((item) => {
    if (item && typeof item === "object") {
      return { label: String(item.label ?? ""), value: String(item.value ?? "") };
    }
    const text = item == null ? "" : String(item);
    return { label: text, value: text };
  });
}

function fieldErrors(field) {
  const errors = validation.value?.errors;
  if (!Array.isArray(errors) || !field.key) return [];
  return errors.filter((error) => error?.fieldKey === field.key || error?.field_key === field.key)
    .map((error) => error.message)
    .filter(Boolean);
}

const formErrors = computed(() => {
  const errors = validation.value?.errors;
  if (!Array.isArray(errors)) return [];
  return errors.filter((error) => !error?.fieldKey && !error?.field_key)
    .map((error) => error.message)
    .filter(Boolean);
});

function filledOptions(field) {
  return (field.options || []).filter((option) => option.label || option.value).map((option) => ({
    label: option.label || option.value,
    value: option.value || option.label,
  }));
}

function applyTemplate(row) {
  code.value = row?.code || "";
  name.value = row?.name || "";
  description.value = row?.description || "";
  status.value = row?.status || "DRAFT";
  fields.value = (Array.isArray(row?.fields) ? row.fields : []).map((field) => ({
    key: field.key || "",
    label: field.label || "",
    type: field.type || "TEXT",
    required: field.required === true,
    options: normalizeOptions(field.options),
  }));
}

function payload() {
  return {
    code: code.value,
    name: name.value,
    description: description.value || null,
    fields: fields.value.map((field) => ({
      key: field.key,
      label: field.label,
      type: field.type,
      required: field.required === true,
      options: field.type === "SELECT" ? filledOptions(field) : null,
    })),
  };
}

async function load() {
  error.value = "";
  traceId.value = "";
  if (creating.value) {
    code.value = "";
    name.value = "";
    description.value = "";
    status.value = "DRAFT";
    fields.value = [];
    return;
  }
  try {
    applyTemplate(await p1.formTemplate(route.params.id));
  } catch (err) {
    error.value = err?.message || "表单模板加载失败";
    traceId.value = err?.traceId || "";
  }
}

function openPublish() {
  if (creating.value || publishing.value) return;
  publishError.value = "";
  publishConfirm.value = true;
}

async function publish() {
  if (creating.value || publishing.value) return;
  publishing.value = true;
  publishError.value = "";
  try {
    const result = await p1.publishFormTemplate(route.params.id);
    publishedVersion.value = result?.version ?? null;
    status.value = "PUBLISHED";
    publishConfirm.value = false;
  } catch (err) {
    publishError.value = err?.message || "发布失败";
    publishConfirm.value = false;
  } finally {
    publishing.value = false;
  }
}

async function reload() {
  validation.value = null;
  publishError.value = "";
  publishedVersion.value = null;
  await load();
}

async function validate() {
  if (creating.value || validating.value) return;
  validating.value = true;
  error.value = "";
  traceId.value = "";
  validation.value = null;
  try {
    const result = await p1.validateFormTemplate(route.params.id);
    validation.value = result;
    if (result?.status) status.value = result.status;
  } catch (err) {
    error.value = err?.message || "校验失败";
    traceId.value = err?.traceId || "";
  } finally {
    validating.value = false;
  }
}

async function save() {
  saving.value = true;
  error.value = "";
  traceId.value = "";
  try {
    const saved = creating.value
      ? await p1.createFormTemplate(payload())
      : await p1.updateFormTemplate(route.params.id, payload());
    validation.value = null;
    publishError.value = "";
    publishedVersion.value = null;
    applyTemplate(saved);
    if (creating.value && saved?.id) {
      await router.replace(`/p1/form-templates/${saved.id}`);
    }
  } catch (err) {
    error.value = err?.message || "保存失败";
    traceId.value = err?.traceId || "";
  } finally {
    saving.value = false;
  }
}

watch(() => route.fullPath, () => {
  if (!saving.value) load();
});
onMounted(load);
</script>

<style scoped>
.meta { display: grid; gap: 12px; margin-bottom: 16px; }
.editor { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr); gap: 16px; align-items: start; }
.field { display: grid; gap: 8px; margin: 12px 0; padding-top: 12px; border-top: 1px solid var(--nx-border, #e5eaf2); }
.check { display: flex; gap: 8px; align-items: center; }
.ok { margin: 0; color: var(--nx-success, #18794e); font-weight: 700; }
.confirm-backdrop { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 16px; background: rgb(0 0 0 / 48%); }
.confirm-dialog { width: min(100%, 440px); display: grid; gap: 12px; }
.confirm-dialog h2, .confirm-dialog p { margin: 0; }
.confirm-actions { display: flex; justify-content: flex-end; gap: 8px; }
.errors, .field-error { margin: 0; color: var(--nx-danger, #b42318); }
.errors { padding-left: 18px; }
.options { display: grid; gap: 8px; }
.option-row { display: grid; grid-template-columns: 1fr 1fr auto; gap: 8px; }
.preview fieldset { border: 0; margin: 0; padding: 0; }
@media (max-width: 900px) {
  .editor { grid-template-columns: 1fr; }
}
</style>
