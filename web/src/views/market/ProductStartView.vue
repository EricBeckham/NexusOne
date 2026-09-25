<template>
  <main class="start-page">
    <NxPageHeader eyebrow="P4 · 星市" title="发起申请" description="按已开通时钉住的模板填写。无支付。提交后进入我的申请。" />
    <p v-if="error" class="nx-error" data-testid="start-error">{{ error }}</p>
    <ul v-if="unmatchedErrors.length" class="field-errors" data-testid="start-field-errors">
      <li v-for="item in unmatchedErrors" :key="`${item.fieldKey}-${item.code}`">{{ item.message }}</li>
    </ul>
    <div v-if="loading" class="nx-card start-form" role="status">正在加载模板…</div>
    <NxEmpty
      v-else-if="!fields.length"
      :title="error ? '模板暂时不可用' : '暂无可填写的模板'"
      :description="error ? '请返回已开通列表后重试。' : '这个已开通产品没有可填写的模板版本。'"
    >
      <template #action>
        <button class="ghost" type="button" data-testid="start-back" @click="cancel">返回我的已开通</button>
      </template>
    </NxEmpty>
    <form v-else class="nx-card start-form" data-testid="start-form" @submit.prevent="submit">
      <div v-for="field in fields" :key="field.key" class="nx-field">
        <label :for="`field-${field.key}`">
          {{ field.label || field.key }}
          <abbr v-if="field.required" class="req" title="必填">*</abbr>
        </label>
        <textarea v-if="field.type === 'TEXTAREA'" :id="`field-${field.key}`" v-model="text[field.key]" rows="3" :required="field.required" :aria-invalid="errorsFor(field.key).length ? 'true' : undefined" />
        <select v-else-if="field.type === 'SELECT'" :id="`field-${field.key}`" v-model="text[field.key]" :required="field.required" :aria-invalid="errorsFor(field.key).length ? 'true' : undefined">
          <option value="">请选择</option>
          <option v-for="option in field.options || []" :key="option" :value="option">{{ option }}</option>
        </select>
        <select v-else-if="field.type === 'PERSON'" :id="`field-${field.key}`" v-model="text[field.key]" :required="field.required" data-testid="approver-select" :aria-invalid="errorsFor(field.key).length ? 'true' : undefined">
          <option value="">请选择本租户成员</option>
          <option v-for="person in people" :key="person.id" :value="person.id">{{ person.displayName || person.display_name || person.id }}</option>
        </select>
        <input v-else-if="field.type === 'ATTACHMENT'" :id="`field-${field.key}`" type="file" @change="upload(field.key, $event)" />
        <input v-else-if="field.type === 'DATE'" :id="`field-${field.key}`" v-model="text[field.key]" type="date" :required="field.required" :aria-invalid="errorsFor(field.key).length ? 'true' : undefined" />
        <input v-else-if="field.type === 'NUMBER' || field.type === 'MONEY'" :id="`field-${field.key}`" v-model="text[field.key]" type="number" step="any" :required="field.required" :aria-invalid="errorsFor(field.key).length ? 'true' : undefined" />
        <input v-else :id="`field-${field.key}`" v-model="text[field.key]" type="text" :required="field.required" :aria-invalid="errorsFor(field.key).length ? 'true' : undefined" />
        <small v-if="field.type === 'PERSON' && !people.length" class="nx-muted">本租户暂无可用成员</small>
        <small v-if="field.type === 'ATTACHMENT' && text[field.key]" class="nx-muted" data-testid="attachment-uploaded">已上传附件</small>
        <p v-for="item in errorsFor(field.key)" :key="`${item.fieldKey}-${item.code}`" class="field-error" :data-testid="`field-error-${field.key}`">{{ item.message }}</p>
      </div>
      <div class="form-actions">
        <button class="ghost" type="button" data-testid="start-cancel" :disabled="busy" @click="cancel">取消</button>
        <button class="primary" type="submit" :disabled="busy">
          <span v-if="busy" class="spinner" aria-hidden="true"></span>
          {{ busy ? "提交中…" : "提交申请" }}
        </button>
      </div>
    </form>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxEmpty, NxPageHeader } from "../../components";
import { p2 } from "../../api/client";
import { p3 } from "../../api/p3";
import { files, readAsBase64 } from "../../api/files";

const route = useRoute();
const router = useRouter();
const fields = ref([]);
const people = ref([]);
const text = reactive({});
const loading = ref(true);
const busy = ref(false);
const error = ref("");
const fieldErrors = ref([]);

const unmatchedErrors = computed(() => {
  const keys = new Set(fields.value.map((field) => field.key));
  return fieldErrors.value.filter((item) => !keys.has(item.fieldKey));
});

function errorsFor(key) {
  return fieldErrors.value.filter((item) => item.fieldKey === key);
}

onMounted(async () => {
  try {
    const [form, persons] = await Promise.all([
      p3.productApplicationForm(route.params.id),
      p2.persons().catch(() => []),
    ]);
    fields.value = form?.fields || [];
    people.value = (persons || []).filter((person) => String(person.status || "ACTIVE").toUpperCase() === "ACTIVE");
    if (!fields.value.length) error.value = "";
  } catch (err) {
    error.value = err.message || "模板暂时不可用";
    fields.value = [];
  } finally {
    loading.value = false;
  }
});

async function upload(key, event) {
  const file = event.target.files?.[0];
  if (!file) return;
  error.value = "";
  try {
    const contentBase64 = await readAsBase64(file);
    const uploaded = await files.upload({
      businessType: "product-application",
      fileName: file.name,
      contentType: file.type || "application/octet-stream",
      contentBase64,
    });
    text[key] = uploaded.file_id || uploaded.fileId;
  } catch (err) {
    error.value = err.message || "附件上传失败";
  }
}

function values() {
  const body = {};
  for (const field of fields.value) {
    const raw = text[field.key];
    if (raw === undefined || raw === null || raw === "") continue;
    if (field.type === "NUMBER" || field.type === "MONEY") body[field.key] = Number(raw);
    else body[field.key] = raw;
  }
  return body;
}

function cancel() {
  router.push("/p4/installations");
}

async function submit() {
  busy.value = true;
  error.value = "";
  fieldErrors.value = [];
  try {
    await p3.createProductApplication(route.params.id, values());
    await router.push({ path: "/p3/todos", query: { box: "requests" } });
  } catch (err) {
    error.value = err.message || "提交失败";
    fieldErrors.value = Array.isArray(err.body?.errors) ? err.body.errors : [];
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.start-form { max-width: 720px; }
.start-form .nx-field { margin-bottom: 20px; }
.start-form .nx-field input,
.start-form .nx-field select,
.start-form .nx-field textarea { width: 100%; min-height: 36px; }
.req { margin-left: 4px; color: var(--nx-danger); text-decoration: none; }
.field-error { margin: 0; color: var(--nx-danger); font-size: 12px; }
.field-errors { max-width: 720px; margin: 0; padding-left: 18px; color: var(--nx-danger); font-size: 13px; }
.form-actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.form-actions .ghost,
.form-actions .primary { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 36px; }
.form-actions .primary:disabled,
.form-actions .ghost:disabled { cursor: not-allowed; opacity: 0.55; }
.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.45);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
