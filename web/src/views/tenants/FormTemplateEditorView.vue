<template>
  <NxPageHeader
    eyebrow="星域 / 表单模板"
    :title="creating ? '新建表单模板' : (name || '表单模板')"
    description="从左侧拖入字段，拖动卡片调整顺序，点选字段在右侧修改属性。"
  >
    <template #actions>
      <router-link class="ghost" to="/p1/form-templates">返回列表</router-link>
      <button class="ghost" type="button" data-testid="template-validate" :disabled="creating || validating" @click="validate">
        {{ validating ? "校验中…" : "校验" }}
      </button>
      <button class="ghost" type="button" data-testid="template-publish" :disabled="creating || publishing" @click="openPublish">发布</button>
      <button class="primary" type="button" data-testid="template-save" :disabled="saving" @click="save">
        {{ saving ? "保存中…" : "保存草稿" }}
      </button>
    </template>
  </NxPageHeader>

  <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="reload" />

  <section class="statusbar" aria-live="polite">
    <span class="status-chip" :data-tone="statusTone" data-testid="template-status">状态：{{ statusLabel(status) }}</span>
    <span class="meta-text">{{ fields.length }} 个字段</span>
    <span v-if="dirty" class="dirty" data-testid="template-dirty">有未保存的修改<template v-if="!creating">；校验与发布针对已保存的草稿</template></span>
    <span v-if="validation?.valid" class="ok" data-testid="template-validate-ok">校验通过</span>
    <span v-if="publishedVersion != null" class="ok" data-testid="template-publish-version">已发布版本 {{ publishedVersion }}</span>
    <router-link v-if="publishedVersion != null" to="/p4/product-definitions" data-testid="template-next-bind">下一步：绑定到产品</router-link>
    <span v-if="publishError" class="field-error" role="alert" data-testid="template-publish-error">{{ publishError }}</span>
  </section>
  <ul v-if="formErrors.length" class="errors" data-testid="template-validate-errors">
    <li v-for="(message, index) in formErrors" :key="index">{{ message }}</li>
  </ul>

  <div class="designer">
    <aside class="nx-card palette" data-testid="field-palette" aria-label="字段类型">
      <h2>字段类型</h2>
      <p class="hint">拖入画布，或点击添加到末尾</p>
      <section v-for="group in FIELD_GROUPS" :key="group.id" class="group">
        <h3>{{ group.label }}</h3>
        <div class="tiles">
          <button
            v-for="type in typesIn(group.id)"
            :key="type.value"
            type="button"
            class="tile"
            draggable="true"
            :data-group="group.id"
            :data-type="type.value"
            data-testid="palette-item"
            :title="`${type.label}：${type.hint}`"
            @dragstart="onPaletteDragStart($event, type.value)"
            @dragend="endDrag"
            @click="appendType(type.value)"
          >
            <span class="badge"><NxIcon :name="type.icon" :size="18" /></span>
            <span class="tile-label">{{ type.label }}</span>
          </button>
        </div>
      </section>
    </aside>

    <section
      class="nx-card canvas"
      data-testid="template-fields"
      :class="{ receiving: drag }"
      aria-label="表单画布"
      @dragover.prevent="onCanvasDragOver"
      @dragleave="onCanvasDragLeave"
      @drop.prevent="onDrop"
    >
      <header class="canvas-head">
        <div>
          <h2>画布</h2>
          <p class="hint">字段自上而下依次展示给申请人</p>
        </div>
        <button class="ghost small" type="button" data-testid="add-field" @click="addBlankField">
          <NxIcon name="plus" :size="16" />添加字段
        </button>
      </header>

      <div v-if="!fields.length" class="dropzone" :class="{ active: drag }" data-testid="template-fields-empty">
        <span class="dropzone-icon"><NxIcon name="layers" :size="24" /></span>
        <strong>暂无字段</strong>
        <span>从左侧拖入字段类型开始设计</span>
      </div>

      <ol v-else class="field-list" @click.self="selectedUid = null">
        <template v-for="(field, index) in fields" :key="field.uid">
          <li v-if="dropIndex === index" class="drop-line" data-testid="drop-indicator" aria-hidden="true" />
          <li
            class="field-card"
            :class="{ selected: field.uid === selectedUid, dragging: drag?.uid === field.uid, invalid: fieldErrors(field).length }"
            draggable="true"
            tabindex="0"
            role="button"
            :aria-pressed="field.uid === selectedUid"
            :aria-label="`字段 ${field.label || '未命名字段'}`"
            data-testid="template-field"
            @click="select(field)"
            @keydown.enter.self.prevent="select(field)"
            @keydown.space.self.prevent="select(field)"
            @dragstart="onCardDragStart($event, field)"
            @dragend="endDrag"
            @dragover.prevent.stop="onCardDragOver($event, index)"
            @drop.prevent.stop="onDrop"
          >
            <span class="grip" aria-hidden="true"><NxIcon name="grip" :size="18" /></span>
            <div class="card-body">
              <div class="card-head">
                <span class="field-name">{{ field.label || "未命名字段" }}<em v-if="field.required" aria-label="必填">*</em></span>
                <span class="type-chip"><NxIcon :name="fieldType(field.type).icon" :size="14" />{{ fieldType(field.type).label }}</span>
                <code class="field-key" :class="{ missing: !field.key }">{{ field.key || "未设置 key" }}</code>
              </div>
              <div class="control" aria-hidden="true"><FieldControl :field="field" /></div>
              <p v-for="(message, messageIndex) in fieldErrors(field)" :key="messageIndex" class="field-error" data-testid="field-error">{{ message }}</p>
            </div>
            <div class="card-actions">
              <button type="button" class="icon-btn" data-testid="move-up" aria-label="上移" title="上移" :disabled="index === 0" @click.stop="shift(index, -1)">
                <NxIcon name="arrowUp" :size="16" />
              </button>
              <button type="button" class="icon-btn" data-testid="move-down" aria-label="下移" title="下移" :disabled="index === fields.length - 1" @click.stop="shift(index, 1)">
                <NxIcon name="arrowDown" :size="16" />
              </button>
              <button type="button" class="icon-btn danger-hover" data-testid="remove-field" aria-label="删除字段" title="删除" @click.stop="removeField(index)">
                <NxIcon name="trash" :size="16" />
              </button>
            </div>
          </li>
        </template>
        <li v-if="dropIndex === fields.length" class="drop-line" data-testid="drop-indicator" aria-hidden="true" />
      </ol>
    </section>

    <aside class="nx-card inspector" aria-label="属性与预览">
      <div class="nx-tabs" role="tablist">
        <button v-for="item in TABS" :key="item.id" type="button" role="tab" :aria-selected="tab === item.id"
          :class="{ active: tab === item.id }" :data-testid="`tab-${item.id}`" @click="tab = item.id">{{ item.label }}</button>
      </div>

      <div v-show="tab === 'field'" data-testid="field-properties" role="tabpanel">
        <template v-if="selected">
          <label class="nx-field">名称<input v-model="selected.label" data-testid="field-label" maxlength="160" /></label>
          <label class="nx-field">字段 key
            <input v-model="selected.key" class="nx-code" data-testid="field-key" maxlength="80" />
            <small class="hint">小写字母开头，仅小写字母、数字、下划线；同一模板内唯一</small>
          </label>
          <label class="nx-field">类型
            <select v-model="selected.type" data-testid="field-type">
              <option v-for="item in FIELD_TYPES" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label class="switch">
            <input v-model="selected.required" type="checkbox" data-testid="field-required" />
            <span class="track" aria-hidden="true" />
            <span>必填</span>
          </label>
          <div v-if="selected.type === 'SELECT'" class="options" data-testid="field-options">
            <p class="section-title">选项</p>
            <div v-for="(option, optionIndex) in selected.options" :key="optionIndex" class="option-row">
              <input v-model="option.label" placeholder="显示名" aria-label="选项显示名" data-testid="option-label" />
              <input v-model="option.value" placeholder="值" aria-label="选项值" class="nx-code" data-testid="option-value" />
              <button class="icon-btn danger-hover" type="button" aria-label="删除选项" data-testid="remove-option" @click="selected.options.splice(optionIndex, 1)">
                <NxIcon name="trash" :size="16" />
              </button>
            </div>
            <button class="ghost small" type="button" data-testid="add-option" @click="selected.options.push({ label: '', value: '' })">
              <NxIcon name="plus" :size="16" />添加选项
            </button>
          </div>
          <p v-for="(message, messageIndex) in fieldErrors(selected)" :key="`e${messageIndex}`" class="field-error" data-testid="field-prop-error">{{ message }}</p>
          <button class="ghost small remove" type="button" data-testid="remove-selected" @click="removeField(fields.indexOf(selected))">
            <NxIcon name="trash" :size="16" />删除此字段
          </button>
        </template>
        <div v-else class="empty-hint" data-testid="field-properties-empty">
          <NxIcon name="card" :size="24" />
          <p>在画布中点选一个字段以编辑属性</p>
        </div>
      </div>

      <div v-show="tab === 'template'" role="tabpanel" data-testid="template-settings">
        <label class="nx-field">编码<input v-model="code" class="nx-code" data-testid="template-code" maxlength="80" /></label>
        <label class="nx-field">名称<input v-model="name" data-testid="template-name" maxlength="160" /></label>
        <label class="nx-field">说明<textarea v-model="description" data-testid="template-description" rows="3" /></label>
      </div>

      <div v-show="tab === 'preview'" role="tabpanel" class="preview" data-testid="template-preview">
        <div class="devices" role="group" aria-label="预览宽度">
          <button v-for="item in DEVICES" :key="item.id" type="button" :class="{ active: device === item.id }"
            :aria-pressed="device === item.id" :data-testid="`preview-device-${item.id}`" @click="device = item.id">{{ item.label }}</button>
        </div>
        <div class="frame" :data-device="device">
          <p v-if="!fields.length" class="nx-muted" data-testid="template-preview-empty">暂无字段</p>
          <fieldset v-else disabled>
            <legend>{{ name || "未命名模板" }}</legend>
            <label v-for="field in fields" :key="field.uid" class="preview-field" data-testid="preview-field">
              <span>{{ field.label || "未命名字段" }}<em v-if="field.required"> *</em></span>
              <FieldControl :field="field" />
            </label>
          </fieldset>
        </div>
      </div>
    </aside>
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
import NxIcon from "../../icons/NxIcon.vue";
import FieldControl from "./form-template/FieldControl.vue";
import {
  FIELD_GROUPS, FIELD_TYPES, blankField, fieldType, filledOptions, insertAt, moveToGap, nextUid,
  normalizeOptions, paletteField,
} from "./form-template/fieldTypes";

const TABS = [
  { id: "field", label: "字段属性" },
  { id: "template", label: "模板设置" },
  { id: "preview", label: "预览" },
];
const DEVICES = [
  { id: "desktop", label: "桌面" },
  { id: "tablet", label: "平板" },
  { id: "mobile", label: "手机" },
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
const selectedUid = ref(null);
const tab = ref("template");
const device = ref("desktop");
const drag = ref(null);
const dropIndex = ref(null);
const savedSnapshot = ref("");

const selected = computed(() => fields.value.find((field) => field.uid === selectedUid.value) || null);
const dirty = computed(() => JSON.stringify(payload()) !== savedSnapshot.value);
const statusTone = computed(() => ({ VALIDATED: "info", PUBLISHED: "success", ARCHIVED: "neutral" })[status.value] || "warning");

function statusLabel(value) {
  return ({ DRAFT: "草稿", VALIDATED: "已校验", PUBLISHED: "已发布", ARCHIVED: "已归档" })[value] || value || "暂无";
}

function typesIn(group) {
  return FIELD_TYPES.filter((type) => type.group === group);
}

function select(field) {
  selectedUid.value = field.uid;
  tab.value = "field";
}

function addField(field, index = fields.value.length) {
  fields.value = insertAt(fields.value, index, field);
  select(field);
}

function appendType(type) {
  addField(paletteField(type, fields.value));
}

function addBlankField() {
  addField(blankField());
}

function removeField(index) {
  if (index < 0) return;
  const [removed] = fields.value.splice(index, 1);
  if (removed?.uid === selectedUid.value) selectedUid.value = null;
}

function shift(index, delta) {
  fields.value = moveToGap(fields.value, index, delta < 0 ? index - 1 : index + 2);
}

function setTransfer(event, effect, text) {
  const transfer = event.dataTransfer;
  if (!transfer) return;
  transfer.effectAllowed = effect;
  try {
    transfer.setData("text/plain", text);
  } catch {
    // Some browsers restrict setData outside dragstart; the in-memory drag state is authoritative.
  }
}

function onPaletteDragStart(event, type) {
  drag.value = { kind: "new", type };
  setTransfer(event, "copy", type);
}

function onCardDragStart(event, field) {
  drag.value = { kind: "move", uid: field.uid };
  setTransfer(event, "move", field.key || field.uid);
}

function onCardDragOver(event, index) {
  if (!drag.value) return;
  const rect = event.currentTarget.getBoundingClientRect();
  const before = rect.height > 0 && event.clientY < rect.top + rect.height / 2;
  dropIndex.value = before ? index : index + 1;
  if (event.dataTransfer) event.dataTransfer.dropEffect = drag.value.kind === "new" ? "copy" : "move";
}

function onCanvasDragOver(event) {
  if (!drag.value) return;
  if (event.target?.closest?.(".drop-line")) return;
  dropIndex.value = fields.value.length;
}

function onCanvasDragLeave(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) dropIndex.value = null;
}

function onDrop() {
  const current = drag.value;
  const at = dropIndex.value ?? fields.value.length;
  endDrag();
  if (!current) return;
  if (current.kind === "new") {
    addField(paletteField(current.type, fields.value), at);
    return;
  }
  const from = fields.value.findIndex((field) => field.uid === current.uid);
  fields.value = moveToGap(fields.value, from, at);
}

function endDrag() {
  drag.value = null;
  dropIndex.value = null;
}

function fieldErrors(field) {
  const errors = validation.value?.errors;
  if (!Array.isArray(errors) || !field.key) return [];
  return errors.filter((item) => item?.fieldKey === field.key || item?.field_key === field.key)
    .map((item) => item.message)
    .filter(Boolean);
}

const formErrors = computed(() => {
  const errors = validation.value?.errors;
  if (!Array.isArray(errors)) return [];
  return errors.filter((item) => !item?.fieldKey && !item?.field_key)
    .map((item) => item.message)
    .filter(Boolean);
});

function applyTemplate(row) {
  code.value = row?.code || "";
  name.value = row?.name || "";
  description.value = row?.description || "";
  status.value = row?.status || "DRAFT";
  fields.value = (Array.isArray(row?.fields) ? row.fields : []).map((field) => ({
    uid: nextUid(),
    key: field.key || "",
    label: field.label || "",
    type: field.type || "TEXT",
    required: field.required === true,
    options: normalizeOptions(field.options),
  }));
  if (!fields.value.some((field) => field.uid === selectedUid.value)) selectedUid.value = null;
  savedSnapshot.value = JSON.stringify(payload());
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
  selectedUid.value = null;
  if (creating.value) {
    applyTemplate(null);
    tab.value = "template";
    return;
  }
  try {
    applyTemplate(await p1.formTemplate(route.params.id));
    tab.value = fields.value.length ? "preview" : "template";
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
  const keepSelectedKey = selected.value?.key;
  try {
    const saved = creating.value
      ? await p1.createFormTemplate(payload())
      : await p1.updateFormTemplate(route.params.id, payload());
    validation.value = null;
    publishError.value = "";
    publishedVersion.value = null;
    applyTemplate(saved);
    const reselect = keepSelectedKey && fields.value.find((field) => field.key === keepSelectedKey);
    if (reselect) selectedUid.value = reselect.uid;
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
.statusbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  font-size: var(--nx-font-size-sm);
}
.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: var(--nx-radius-pill);
  font-weight: 600;
  color: var(--nx-warning);
  background: var(--nx-warning-light);
}
.status-chip[data-tone="info"] { color: var(--nx-info); background: var(--nx-info-light); }
.status-chip[data-tone="success"] { color: var(--nx-success); background: var(--nx-success-light); }
.status-chip[data-tone="neutral"] { color: var(--nx-text-secondary); background: var(--nx-divider); }
.meta-text { color: var(--nx-text-secondary); }
.dirty { color: var(--nx-warning); }
.ok { color: var(--nx-success); font-weight: 600; }
.errors {
  margin: 0 0 16px;
  padding: 10px 12px 10px 30px;
  border-radius: var(--nx-radius-sm);
  color: var(--nx-danger);
  background: var(--nx-danger-light);
}
.field-error { margin: 4px 0 0; color: var(--nx-danger); font-size: var(--nx-font-size-sm); }

.designer {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}
.designer h2 { margin: 0; font-size: 16px; font-weight: 600; color: var(--nx-text-primary); }
.hint { margin: 4px 0 0; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }

.palette, .inspector { position: sticky; top: 16px; }
.palette { padding: 16px; }
.group { margin-top: 16px; }
.group h3 {
  margin: 0 0 8px;
  font-size: var(--nx-font-size-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--nx-text-secondary);
}
.tiles { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.tile {
  --tone: var(--nx-primary);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
  background: var(--nx-surface);
  color: var(--nx-text-primary);
  font: inherit;
  font-size: var(--nx-font-size-sm);
  text-align: left;
  cursor: grab;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}
.tile[data-group="data"] { --tone: var(--nx-chart-purple); }
.tile[data-group="business"] { --tone: var(--nx-chart-green); }
.tile:hover, .tile:focus-visible {
  border-color: var(--tone);
  box-shadow: var(--nx-shadow-card);
  transform: translateY(-1px);
  outline: none;
}
.tile:active { cursor: grabbing; }
.badge {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--nx-radius-sm);
  color: var(--tone);
  background: color-mix(in srgb, var(--tone) 12%, white);
}
.tile-label { font-weight: 600; }

.canvas { min-height: 480px; padding: 16px; background: var(--nx-bg-page); transition: box-shadow 0.15s; }
.canvas.receiving { box-shadow: 0 0 0 2px var(--nx-primary-light), var(--nx-shadow-card); }
.canvas-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 12px; }
.small { display: inline-flex; align-items: center; gap: 4px; padding: 6px 10px; font-size: var(--nx-font-size-sm); }

.dropzone {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 56px 16px;
  border: 2px dashed var(--nx-border-strong);
  border-radius: var(--nx-radius);
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-sm);
  text-align: center;
  background: var(--nx-surface);
  transition: border-color 0.15s, background 0.15s;
}
.dropzone strong { color: var(--nx-text-primary); font-size: var(--nx-font-size-md); }
.dropzone.active { border-color: var(--nx-primary); background: var(--nx-primary-light); }
.dropzone-icon {
  display: grid;
  place-items: center;
  width: var(--nx-state-icon);
  height: var(--nx-state-icon);
  border-radius: 50%;
  color: var(--nx-primary);
  background: var(--nx-primary-light);
}

.field-list { list-style: none; margin: 0; padding: 0 0 24px; display: grid; gap: 10px; }
.field-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  padding: 12px;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
  background: var(--nx-surface);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s;
}
.field-card:hover { border-color: var(--nx-border-strong); box-shadow: var(--nx-shadow-card); }
.field-card:focus-visible { outline: 2px solid var(--nx-primary); outline-offset: 2px; }
.field-card.selected { border-color: var(--nx-primary); box-shadow: 0 0 0 3px var(--nx-primary-light); }
.field-card.invalid { border-color: var(--nx-danger); }
.field-card.dragging { opacity: 0.4; }
.grip { color: var(--nx-text-disabled); cursor: grab; padding-top: 2px; }
.card-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 8px; }
.field-name { font-weight: 600; color: var(--nx-text-primary); }
.field-name em { color: var(--nx-danger); font-style: normal; margin-left: 2px; }
.type-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border-radius: var(--nx-radius-pill);
  font-size: var(--nx-font-size-xs);
  color: var(--nx-text-regular);
  background: var(--nx-divider);
}
.field-key { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: var(--nx-font-size-xs); color: var(--nx-text-secondary); }
.field-key.missing { color: var(--nx-warning); }
.control { pointer-events: none; }
.card-actions { display: flex; gap: 2px; opacity: 0.55; transition: opacity 0.15s; }
.field-card:hover .card-actions, .field-card.selected .card-actions, .field-card:focus-within .card-actions { opacity: 1; }
.icon-btn {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--nx-text-secondary);
  cursor: pointer;
}
.icon-btn:hover:not(:disabled) { color: var(--nx-primary); background: var(--nx-primary-light); }
.icon-btn.danger-hover:hover:not(:disabled) { color: var(--nx-danger); background: var(--nx-danger-light); }
.icon-btn:disabled { color: var(--nx-text-disabled); cursor: not-allowed; }
.drop-line {
  position: relative;
  height: 3px;
  margin: -6px 0;
  border-radius: 2px;
  background: var(--nx-primary);
}
.drop-line::before {
  content: "";
  position: absolute;
  left: -4px;
  top: -3px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--nx-primary);
}

.inspector { padding: 4px 16px 16px; max-height: calc(100vh - 32px); overflow: auto; }
.inspector .nx-tabs { position: sticky; top: 0; z-index: 1; background: var(--nx-surface); padding-top: 12px; }
.inspector textarea { resize: vertical; }
.switch { display: flex; width: fit-content; align-items: center; gap: 8px; margin-bottom: 16px; cursor: pointer; color: var(--nx-text-regular); }
.switch input { position: absolute; opacity: 0; width: 1px; height: 1px; }
.track {
  position: relative;
  width: 34px;
  height: 20px;
  border-radius: var(--nx-radius-pill);
  background: var(--nx-border-strong);
  transition: background 0.15s;
}
.track::after {
  content: "";
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 20%);
  transition: transform 0.15s;
}
.switch input:checked + .track { background: var(--nx-primary); }
.switch input:checked + .track::after { transform: translateX(14px); }
.switch input:focus-visible + .track { outline: 2px solid var(--nx-primary); outline-offset: 2px; }
.section-title { margin: 0; font-size: var(--nx-font-size-xs); font-weight: 600; color: var(--nx-text-secondary); }
.options { display: grid; gap: 8px; margin-bottom: 16px; }
.option-row { display: grid; grid-template-columns: 1fr 1fr auto; gap: 6px; align-items: center; }
.option-row input {
  min-width: 0;
  border: 1px solid var(--nx-border-strong);
  border-radius: 6px;
  padding: 6px 8px;
  font: inherit;
  font-size: var(--nx-font-size-sm);
}
.options .small, .remove { justify-self: start; }
.remove { display: flex; width: fit-content; margin-top: 8px; color: var(--nx-danger); border-color: color-mix(in srgb, var(--nx-danger) 35%, white); }
.remove:hover { background: var(--nx-danger-light); }
.empty-hint {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 40px 8px;
  color: var(--nx-text-secondary);
  text-align: center;
  font-size: var(--nx-font-size-sm);
}
.empty-hint p { margin: 0; }

.devices {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  margin-bottom: 12px;
  border-radius: var(--nx-radius-sm);
  background: var(--nx-divider);
}
.devices button {
  border: 0;
  border-radius: 6px;
  padding: 4px 12px;
  background: transparent;
  color: var(--nx-text-secondary);
  font: inherit;
  font-size: var(--nx-font-size-sm);
  cursor: pointer;
}
.devices button.active { background: var(--nx-surface); color: var(--nx-text-primary); box-shadow: var(--nx-shadow-card); font-weight: 600; }
.frame {
  margin: 0 auto;
  padding: 16px;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius);
  background: var(--nx-surface);
  max-width: 100%;
  transition: width 0.2s;
}
.frame[data-device="tablet"] { width: 88%; }
.frame[data-device="mobile"] { width: 72%; border-radius: 20px; }
.frame fieldset { border: 0; margin: 0; padding: 0; min-width: 0; display: grid; gap: 16px; }
.frame legend { padding: 0; margin-bottom: 12px; font-weight: 600; color: var(--nx-text-primary); }
.preview-field { display: grid; gap: 6px; font-size: var(--nx-font-size-sm); color: var(--nx-text-regular); }
.preview-field em { color: var(--nx-danger); font-style: normal; }

.confirm-backdrop { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 16px; background: rgb(0 0 0 / 48%); }
.confirm-dialog { width: min(100%, 440px); display: grid; gap: 12px; }
.confirm-dialog h2, .confirm-dialog p { margin: 0; }
.confirm-actions { display: flex; justify-content: flex-end; gap: 8px; }

@media (max-width: 1200px) {
  .designer { grid-template-columns: 220px minmax(0, 1fr); }
  .inspector { grid-column: 1 / -1; position: static; max-height: none; }
}
@media (max-width: 760px) {
  .designer { grid-template-columns: 1fr; }
  .palette { position: static; }
  .tiles { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); }
}
</style>
