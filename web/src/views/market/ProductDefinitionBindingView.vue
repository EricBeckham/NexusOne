<template>
  <main class="binding-page">
    <NxPageHeader
      eyebrow="P4 · 平台"
      title="产品定义"
      description="把已发布的表单模板版本和流程版本绑定成产品。此页仅平台管理员使用。"
    />

    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <template v-else>
      <section class="nx-card" aria-label="已有产品定义">
        <h2>已有定义</h2>
        <NxEmpty v-if="loaded && !definitions.length" title="暂无" description="还没有产品定义。" />
        <table v-else-if="definitions.length" class="nx-table" data-testid="product-definition-list">
          <thead>
            <tr><th>编码</th><th>名称</th><th>状态</th><th>模板版本</th><th>流程</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="row in definitions" :key="row.id">
              <td>{{ row.code || "暂无" }}</td>
              <td>{{ row.name || "暂无" }}</td>
              <td>{{ row.status || "暂无" }}</td>
              <td>{{ row.formTemplateVersionId || "暂无" }}</td>
              <td>{{ processLabel(row) }}</td>
              <td>
                <button v-if="row.status === 'DRAFT'" type="button" @click="edit(row)">编辑</button>
                <span v-else>只读</span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <form class="nx-card binding-form" data-testid="product-definition-form" @submit.prevent="save">
        <h2>{{ editingId ? "编辑草稿" : "新建绑定" }}</h2>
        <label>编码<input v-model="form.code" data-testid="definition-code" required maxlength="80" /></label>
        <label>名称<input v-model="form.name" data-testid="definition-name" required maxlength="160" /></label>
        <label>说明<textarea v-model="form.description" data-testid="definition-description" rows="2"></textarea></label>
        <label>
          已发布模板版本
          <select v-model="form.formTemplateVersionId" data-testid="definition-template" required>
            <option value="">{{ publishedTemplates.length ? "请选择" : "暂无" }}</option>
            <option v-for="item in publishedTemplates" :key="item.runningVersionId" :value="item.runningVersionId">
              {{ item.name || item.code }}（{{ item.code || "暂无" }}）
            </option>
          </select>
        </label>
        <label>
          已发布流程版本
          <select v-model="form.processKey" data-testid="definition-process" required>
            <option value="">{{ publishedProcesses.length ? "请选择" : "暂无" }}</option>
            <option v-for="item in publishedProcesses" :key="`${item.key}@${item.version}`" :value="item.key">
              {{ item.name || item.key }} · {{ item.key }} · 版本 {{ item.version }}
            </option>
          </select>
        </label>
        <p class="permission-note" data-testid="definition-permission">{{ permissionNote }}</p>
        <p v-if="saveError" role="alert" data-testid="definition-save-error">{{ saveError }}</p>
        <p v-if="savedCode" data-testid="definition-saved">已保存 {{ savedCode }}</p>
        <div class="actions">
          <button class="primary" type="submit" data-testid="definition-save" :disabled="saving || !canSave">
            {{ saving ? "保存中…" : "保存" }}
          </button>
          <button v-if="editingId" type="button" @click="resetForm">改为新建</button>
        </div>
      </form>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { p1, p4 } from "../../api/client";
import { p3 } from "../../api/p3";
import { NxEmpty, NxErrorState, NxPageHeader } from "../../components";

const permissionNote = "租户成员可发起；审批人来自表单中的成员字段，且必须是本租户成员。";

const definitions = ref([]);
const publishedTemplates = ref([]);
const publishedProcesses = ref([]);
const loaded = ref(false);
const error = ref("");
const saveError = ref("");
const savedCode = ref("");
const saving = ref(false);
const editingId = ref("");
const form = reactive({
  code: "",
  name: "",
  description: "",
  formTemplateVersionId: "",
  processKey: "",
});

const canSave = computed(() => form.code.trim() && form.name.trim() && form.formTemplateVersionId && form.processKey);

function processLabel(row) {
  if (!row.processDefinitionKey) return "暂无";
  return `${row.processDefinitionKey} · 版本 ${row.processDefinitionVersion ?? "暂无"}`;
}

function selectedProcess() {
  return publishedProcesses.value.find((item) => item.key === form.processKey) || null;
}

function edit(row) {
  editingId.value = row.id;
  form.code = row.code || "";
  form.name = row.name || "";
  form.description = row.description || "";
  form.formTemplateVersionId = row.formTemplateVersionId || "";
  form.processKey = row.processDefinitionKey || "";
  saveError.value = "";
  savedCode.value = "";
}

function resetForm() {
  editingId.value = "";
  form.code = "";
  form.name = "";
  form.description = "";
  form.formTemplateVersionId = "";
  form.processKey = "";
}

function payload() {
  const process = selectedProcess();
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    description: form.description.trim() || null,
    formTemplateVersionId: form.formTemplateVersionId,
    processDefinitionKey: process?.key || null,
    processDefinitionVersion: process ? Number(process.version) : null,
    defaultPermissionPack: { summary: permissionNote },
  };
}

async function load() {
  loaded.value = false;
  error.value = "";
  try {
    const [rows, templates, drafts] = await Promise.all([
      p4.productDefinitions(),
      p1.formTemplates(),
      p3.listProcessDrafts(),
    ]);
    definitions.value = Array.isArray(rows) ? rows : [];
    publishedTemplates.value = (Array.isArray(templates) ? templates : [])
      .filter((item) => item.status === "PUBLISHED" && item.runningVersionId);
    const processes = [];
    for (const draft of Array.isArray(drafts) ? drafts : []) {
      if (!draft?.key) continue;
      try {
        const publication = await p3.getProcessPublication(draft.key);
        if (publication?.key && publication.version != null) processes.push(publication);
      } catch {
        // Unpublished drafts are not selectable.
      }
    }
    publishedProcesses.value = processes;
  } catch (err) {
    error.value = err.message || "产品定义暂时不可用";
  } finally {
    loaded.value = true;
  }
}

async function save() {
  if (!canSave.value) return;
  saving.value = true;
  saveError.value = "";
  savedCode.value = "";
  try {
    const body = payload();
    const saved = editingId.value
      ? await p4.updateProductDefinition(editingId.value, body)
      : await p4.createProductDefinition(body);
    savedCode.value = saved?.code || body.code;
    editingId.value = "";
    resetForm();
    await load();
  } catch (err) {
    saveError.value = err.message || "保存失败";
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.binding-page { display: grid; gap: var(--nx-space-4); }
.binding-form, .nx-card h2 { margin: 0; }
.binding-form { display: grid; gap: var(--nx-space-3); }
.binding-form label { display: grid; gap: var(--nx-space-1); color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.binding-form input, .binding-form textarea, .binding-form select {
  width: 100%; min-height: 40px; padding: var(--nx-space-2) var(--nx-space-3);
  color: var(--nx-text-primary); background: var(--nx-surface);
  border: 1px solid var(--nx-border-strong); border-radius: 8px;
}
.permission-note { margin: 0; color: var(--nx-text-secondary); }
.actions { display: flex; gap: var(--nx-space-2); }
</style>
