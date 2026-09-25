<template>
  <main class="start-page">
    <NxPageHeader eyebrow="P4 · NEXUS MARKET" title="发起申请" description="按已开通时钉住的模板填写。无支付。提交后进入我的申请。" />
    <p v-if="error" class="nx-error" data-testid="start-error">{{ error }}</p>
    <ul v-if="fieldErrors.length" class="nx-error">
      <li v-for="item in fieldErrors" :key="`${item.fieldKey}-${item.code}`">{{ item.message }}</li>
    </ul>
    <div v-if="loading" class="nx-card" role="status">正在加载模板…</div>
    <NxEmpty v-else-if="!fields.length" title="暂无" description="这个已开通产品没有可填写的模板版本。" />
    <form v-else class="nx-card" @submit.prevent="submit">
      <div v-for="field in fields" :key="field.key" class="nx-field">
        <label :for="`field-${field.key}`">{{ field.label || field.key }}</label>
        <textarea v-if="field.type === 'TEXTAREA'" :id="`field-${field.key}`" v-model="text[field.key]" rows="3" :required="field.required" />
        <select v-else-if="field.type === 'SELECT'" :id="`field-${field.key}`" v-model="text[field.key]" :required="field.required">
          <option value="">请选择</option>
          <option v-for="option in field.options || []" :key="option" :value="option">{{ option }}</option>
        </select>
        <select v-else-if="field.type === 'PERSON'" :id="`field-${field.key}`" v-model="text[field.key]" :required="field.required" data-testid="approver-select">
          <option value="">请选择本租户成员</option>
          <option v-for="person in people" :key="person.id" :value="person.id">{{ person.displayName || person.display_name || person.id }}</option>
        </select>
        <input v-else-if="field.type === 'ATTACHMENT'" :id="`field-${field.key}`" type="file" @change="upload(field.key, $event)" />
        <input v-else-if="field.type === 'DATE'" :id="`field-${field.key}`" v-model="text[field.key]" type="date" :required="field.required" />
        <input v-else-if="field.type === 'NUMBER' || field.type === 'MONEY'" :id="`field-${field.key}`" v-model="text[field.key]" type="number" step="any" :required="field.required" />
        <input v-else :id="`field-${field.key}`" v-model="text[field.key]" type="text" :required="field.required" />
        <small v-if="field.type === 'ATTACHMENT' && text[field.key]" class="nx-muted">已上传 {{ text[field.key] }}</small>
      </div>
      <button class="primary" type="submit" :disabled="busy">{{ busy ? "提交中…" : "提交" }}</button>
    </form>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
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
