<template>
  <main class="binding-page">
    <NxPageHeader
      eyebrow="P4 · 平台"
      title="产品定义"
      description="把已发布的表单模板版本和流程版本组合成产品，再绑定 SKU，租户即可在应用目录订阅开通。此页仅平台管理员使用。"
    >
      <template #actions>
        <router-link class="ghost" to="/p1/form-templates">表单模板</router-link>
        <router-link class="ghost" to="/p3/workflow">流程设计器</router-link>
        <router-link class="ghost" to="/p4/market">应用目录</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <template v-else>
      <ol class="nx-card journey" aria-label="上架步骤" data-testid="binding-journey">
        <li v-for="(step, index) in journey" :key="step.id" :data-done="step.done" :data-testid="`journey-${step.id}`">
          <span class="journey-index">{{ step.done ? "✓" : index + 1 }}</span>
          <span class="journey-text">
            <strong>{{ step.title }}</strong>
            <small>{{ step.detail }}</small>
          </span>
          <router-link v-if="step.to && !step.done" class="journey-link" :to="step.to">{{ step.action }}</router-link>
        </li>
      </ol>

      <p v-if="notice" class="notice" role="status" data-testid="binding-notice">
        {{ notice.text }}
        <button v-if="notice.bindId" type="button" class="link-button" data-testid="notice-bind" @click="openBind(notice.bindId)">为它绑定 SKU</button>
        <router-link v-if="notice.toCatalog" to="/p4/market">去应用目录查看</router-link>
      </p>

      <section class="nx-card" aria-label="已有产品定义">
        <h2>已有定义</h2>
        <NxEmpty
          v-if="loaded && !definitions.length"
          title="暂无产品定义"
          description="在下方选择已发布的模板和流程，保存后即可绑定 SKU。"
          compact
        />
        <table v-else-if="definitions.length" class="nx-table" data-testid="product-definition-list">
          <thead>
            <tr><th>产品</th><th>状态</th><th>表单模板</th><th>流程</th><th>已绑定 SKU</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="row in definitions" :key="row.id" :class="{ binding: bindingFor === row.id }">
              <td><strong>{{ row.name || "暂无" }}</strong><code class="muted-code">{{ row.code }}</code></td>
              <td><NxStatusTag :status="row.status" :label="definitionStatus(row.status).label" :tone="definitionStatus(row.status).tone" /></td>
              <td>{{ templateLabel(row.formTemplateVersionId) }}</td>
              <td>{{ processLabel(row) }}</td>
              <td>
                <span v-if="boundSkus(row.id).length" class="chips"><span v-for="sku in boundSkus(row.id)" :key="sku.code">{{ sku.name || sku.code }}</span></span>
                <span v-else class="unbound">未绑定</span>
              </td>
              <td class="row-actions">
                <button type="button" class="ghost small" :data-testid="`bind-open-${row.code}`" @click="openBind(row.id)">绑定 SKU</button>
                <button v-if="row.status === 'DRAFT'" type="button" class="ghost small" @click="edit(row)">编辑</button>
              </td>
            </tr>
          </tbody>
        </table>

        <form v-if="bindingDefinition" class="bind-panel" data-testid="bind-panel" @submit.prevent="bindSku">
          <h3>为「{{ bindingDefinition.name || bindingDefinition.code }}」绑定 SKU</h3>
          <p class="nx-muted">绑定后，租户在应用目录订阅该 SKU 时会开通这个产品。一个 SKU 只能绑定一个产品。</p>
          <NxEmpty v-if="!skus.length" title="暂无 SKU" description="先在星市创建应用与 SKU，再回来绑定。" compact />
          <template v-else>
            <label>
              SKU
              <select v-model="bindSkuCode" data-testid="bind-sku" required>
                <option value="">请选择</option>
                <option v-for="sku in skus" :key="sku.code" :value="sku.code">{{ sku.name || sku.code }}（{{ sku.code }}）</option>
              </select>
            </label>
            <p v-if="rebindWarning" class="warn" data-testid="bind-rebind-warning">{{ rebindWarning }}</p>
            <p v-if="bindError" class="error" role="alert" data-testid="bind-error">{{ bindError }}</p>
            <div class="actions">
              <button class="primary" type="submit" data-testid="bind-submit" :disabled="binding || !bindSkuCode">{{ binding ? "绑定中…" : "确认绑定" }}</button>
              <button class="ghost" type="button" @click="closeBind">取消</button>
            </div>
          </template>
        </form>
      </section>

      <form class="nx-card binding-form" data-testid="product-definition-form" @submit.prevent="save">
        <h2>{{ editingId ? "编辑草稿" : "新建产品定义" }}</h2>
        <label>编码<input v-model="form.code" data-testid="definition-code" required maxlength="80" /></label>
        <label>名称<input v-model="form.name" data-testid="definition-name" required maxlength="160" /></label>
        <label>说明<textarea v-model="form.description" data-testid="definition-description" rows="2"></textarea></label>
        <label>
          已发布模板版本
          <select v-model="form.formTemplateVersionId" data-testid="definition-template" required :disabled="!publishedTemplates.length">
            <option value="">{{ publishedTemplates.length ? "请选择" : "暂无已发布模板" }}</option>
            <option v-for="item in publishedTemplates" :key="item.runningVersionId" :value="item.runningVersionId">
              {{ item.name || item.code }}（{{ item.code || "暂无" }}）
            </option>
          </select>
          <small v-if="loaded && !publishedTemplates.length" class="hint" data-testid="definition-template-empty">
            <template v-if="unpublishedTemplateCount">有 {{ unpublishedTemplateCount }} 个模板还是草稿或仅通过校验，发布后才会出现在这里。</template>
            <template v-else>还没有已发布的表单模板。</template>
            <router-link to="/p1/form-templates">去表单模板发布</router-link>
          </small>
        </label>
        <label>
          已发布流程版本
          <select v-model="form.processKey" data-testid="definition-process" required :disabled="!publishedProcesses.length">
            <option value="">{{ publishedProcesses.length ? "请选择" : "暂无已发布流程" }}</option>
            <option v-for="item in publishedProcesses" :key="`${item.key}@${item.version}`" :value="item.key">
              {{ item.name || item.key }} · {{ item.key }} · 版本 {{ item.version }}
            </option>
          </select>
          <small v-if="loaded && processUnavailable" class="hint" data-testid="definition-process-unavailable">
            {{ processUnavailable }}
          </small>
          <small v-else-if="loaded && !publishedProcesses.length" class="hint" data-testid="definition-process-empty">
            还没有已发布的流程。<router-link to="/p3/workflow">去流程设计器发布</router-link>
          </small>
        </label>
        <p class="permission-note" data-testid="definition-permission">{{ permissionNote }}</p>
        <p v-if="saveError" class="error" role="alert" data-testid="definition-save-error">{{ saveError }}</p>
        <p v-if="savedCode" class="saved" data-testid="definition-saved">已保存 {{ savedCode }}</p>
        <div class="actions">
          <button class="primary" type="submit" data-testid="definition-save" :disabled="saving || !canSave">
            {{ saving ? "保存中…" : "保存" }}
          </button>
          <button v-if="editingId" class="ghost" type="button" @click="resetForm">改为新建</button>
        </div>
      </form>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { p1, p4 } from "../../api/client";
import { p3 } from "../../api/p3";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatusTag } from "../../components";

const permissionNote = "租户成员可发起；审批人来自表单中的成员字段，且必须是本租户成员。";

const definitions = ref([]);
const publishedTemplates = ref([]);
const publishedProcesses = ref([]);
const unpublishedTemplateCount = ref(0);
const processUnavailable = ref("");
const skus = ref([]);
const skuBindings = ref({});
const loaded = ref(false);
const error = ref("");
const saveError = ref("");
const savedCode = ref("");
const saving = ref(false);
const editingId = ref("");
const notice = ref(null);
const bindingFor = ref("");
const bindSkuCode = ref("");
const binding = ref(false);
const bindError = ref("");
const form = reactive({
  code: "",
  name: "",
  description: "",
  formTemplateVersionId: "",
  processKey: "",
});

const canSave = computed(() => form.code.trim() && form.name.trim() && form.formTemplateVersionId && form.processKey);
const bindingDefinition = computed(() => definitions.value.find((row) => row.id === bindingFor.value) || null);
const boundCount = computed(() => Object.keys(skuBindings.value).length);
const rebindWarning = computed(() => {
  const current = skuBindings.value[bindSkuCode.value];
  if (!current || current.id === bindingFor.value) return "";
  return `该 SKU 当前绑定「${current.name || current.code}」，确认后将改为绑定本产品。`;
});
const journey = computed(() => [
  { id: "template", title: "发布表单模板", detail: `已发布 ${publishedTemplates.value.length} 个`, done: publishedTemplates.value.length > 0, to: "/p1/form-templates", action: "去发布" },
  { id: "process", title: "发布流程", detail: processUnavailable.value ? "需要租户身份才能读取" : `已发布 ${publishedProcesses.value.length} 个`, done: publishedProcesses.value.length > 0, to: "/p3/workflow", action: "去发布" },
  { id: "definition", title: "保存产品定义", detail: `已有 ${definitions.value.length} 个`, done: definitions.value.length > 0 },
  { id: "sku", title: "绑定 SKU 上架", detail: boundCount.value ? `已绑定 ${boundCount.value} 个 SKU` : "绑定后租户即可在目录订阅开通", done: boundCount.value > 0 },
]);

// The product's JSON is SNAKE_CASE (spring.jackson.property-naming-strategy); keep camelCase only as a fallback.
function pick(row, snake, camel) {
  return row?.[snake] ?? row?.[camel] ?? null;
}

function normalizeDefinition(row) {
  return {
    id: row?.id,
    code: row?.code || "",
    name: row?.name || "",
    description: row?.description || "",
    status: row?.status || "",
    formTemplateVersionId: pick(row, "form_template_version_id", "formTemplateVersionId"),
    processDefinitionKey: pick(row, "process_definition_key", "processDefinitionKey"),
    processDefinitionVersion: pick(row, "process_definition_version", "processDefinitionVersion"),
  };
}

function definitionStatus(status) {
  return ({ DRAFT: { label: "草稿", tone: "warning" }, PUBLISHED: { label: "已发布", tone: "success" } })[status]
    || { label: status || "暂无", tone: "neutral" };
}

function templateLabel(versionId) {
  if (!versionId) return "暂无";
  const template = publishedTemplates.value.find((item) => item.runningVersionId === versionId);
  return template ? (template.name || template.code) : `历史版本 ${String(versionId).slice(0, 8)}`;
}

function processLabel(row) {
  if (!row.processDefinitionKey) return "暂无";
  return `${row.processDefinitionKey} · 版本 ${row.processDefinitionVersion ?? "暂无"}`;
}

function boundSkus(definitionId) {
  return Object.entries(skuBindings.value)
    .filter(([, definition]) => definition.id === definitionId)
    .map(([code, definition]) => ({ code, name: definition.skuName }));
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
    form_template_version_id: form.formTemplateVersionId,
    process_definition_key: process?.key || null,
    process_definition_version: process ? Number(process.version) : null,
    default_permission_pack: { summary: permissionNote },
  };
}

function processUnavailableText(err) {
  const message = String(err?.message || "");
  if (/tenantcontext|tenant context/i.test(message) || Number(err?.status) === 403) {
    return "已发布流程按租户读取：当前没有租户身份。请以租户管理员身份登录（演示：林晓）后再选择流程。";
  }
  return `流程列表暂时读不到${err?.traceId ? `（追踪号 ${err.traceId}）` : ""}，可稍后刷新重试。`;
}

async function loadPublishedProcesses(drafts) {
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
  return processes;
}

async function loadBindings() {
  try {
    const [skuRows, catalog] = await Promise.all([p4.skus(), p4.catalog()]);
    skus.value = (Array.isArray(skuRows) ? skuRows : []).filter((sku) => sku?.code);
    const bindings = {};
    for (const item of Array.isArray(catalog) ? catalog : []) {
      for (const entry of Array.isArray(item?.skus) ? item.skus : []) {
        const sku = entry?.sku || entry;
        const definition = entry?.product_definition || entry?.productDefinition;
        if (sku?.code && definition?.id) bindings[sku.code] = { ...definition, skuName: sku.name || "" };
      }
    }
    skuBindings.value = bindings;
  } catch {
    // SKU lists are optional for creating definitions; binding shows its own empty state.
    skus.value = [];
    skuBindings.value = {};
  }
}

async function load() {
  loaded.value = false;
  error.value = "";
  try {
    // Process drafts are tenant-scoped (a platform operator without a tenant gets "TenantContext is required");
    // that must not take down templates and definitions, so it is read separately.
    processUnavailable.value = "";
    const [rows, templates, drafts] = await Promise.all([
      p4.productDefinitions(),
      p1.formTemplates(),
      p3.listProcessDrafts().catch((err) => {
        processUnavailable.value = processUnavailableText(err);
        return [];
      }),
    ]);
    definitions.value = (Array.isArray(rows) ? rows : []).map(normalizeDefinition);
    const allTemplates = (Array.isArray(templates) ? templates : [])
      .map((item) => ({ ...item, runningVersionId: pick(item, "running_version_id", "runningVersionId") }));
    publishedTemplates.value = allTemplates.filter((item) => item.status === "PUBLISHED" && item.runningVersionId);
    unpublishedTemplateCount.value = allTemplates.length - publishedTemplates.value.length;
    publishedProcesses.value = await loadPublishedProcesses(drafts);
    await loadBindings();
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
  notice.value = null;
  try {
    const body = payload();
    const saved = editingId.value
      ? await p4.updateProductDefinition(editingId.value, body)
      : await p4.createProductDefinition(body);
    savedCode.value = saved?.code || body.code;
    editingId.value = "";
    resetForm();
    await load();
    if (saved?.id) notice.value = { text: `已保存 ${savedCode.value}。下一步：绑定 SKU 后租户即可订阅开通。`, bindId: saved.id };
  } catch (err) {
    saveError.value = err.message || "保存失败";
  } finally {
    saving.value = false;
  }
}

function openBind(id) {
  bindingFor.value = id;
  bindSkuCode.value = "";
  bindError.value = "";
}

function closeBind() {
  bindingFor.value = "";
  bindSkuCode.value = "";
  bindError.value = "";
}

async function bindSku() {
  if (!bindingDefinition.value || !bindSkuCode.value || binding.value) return;
  binding.value = true;
  bindError.value = "";
  const target = bindingDefinition.value;
  const code = bindSkuCode.value;
  try {
    await p4.bindProductDefinitionSku(target.id, code);
    closeBind();
    await loadBindings();
    notice.value = { text: `已把 SKU ${code} 绑定到「${target.name || target.code}」。`, toCatalog: true };
  } catch (err) {
    bindError.value = err.message || "绑定失败";
  } finally {
    binding.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.binding-page { display: grid; gap: var(--nx-space-4); }
.binding-form, .nx-card h2 { margin: 0; }
.nx-card h2 { margin-bottom: var(--nx-space-3); font-size: 16px; }
.journey { list-style: none; margin: 0; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--nx-space-3); }
.journey li { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: start; gap: 4px var(--nx-space-2); padding: var(--nx-space-2); border-radius: var(--nx-radius-sm); }
.journey li[data-done="true"] { background: var(--nx-success-light); }
.journey-index { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; font-size: var(--nx-font-size-xs); font-weight: 700; color: var(--nx-text-secondary); background: var(--nx-divider); }
.journey li[data-done="true"] .journey-index { color: #fff; background: var(--nx-success); }
.journey-text { display: grid; gap: 2px; }
.journey-text strong { font-size: var(--nx-font-size-sm); color: var(--nx-text-primary); }
.journey-text small { font-size: var(--nx-font-size-xs); color: var(--nx-text-secondary); }
.journey-link { grid-column: 2; font-size: var(--nx-font-size-xs); }
.notice { margin: 0; padding: 10px 12px; border-radius: var(--nx-radius-sm); color: var(--nx-text-regular); background: var(--nx-info-light); display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.link-button { border: 0; padding: 0; background: none; color: var(--nx-primary); cursor: pointer; font: inherit; }
.muted-code { display: block; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.chips { display: flex; flex-wrap: wrap; gap: 4px; }
.chips span { padding: 1px 8px; border-radius: var(--nx-radius-pill); font-size: var(--nx-font-size-xs); color: var(--nx-product); background: var(--nx-product-light); }
.unbound { color: var(--nx-warning); font-size: var(--nx-font-size-sm); }
.row-actions { white-space: nowrap; text-align: right; }
.small { padding: 4px 10px; font-size: var(--nx-font-size-sm); }
tr.binding td { background: var(--nx-primary-light); }
.bind-panel { display: grid; gap: var(--nx-space-2); margin-top: var(--nx-space-3); padding: var(--nx-space-3); border: 1px solid var(--nx-primary); border-radius: var(--nx-radius-sm); background: var(--nx-surface); }
.bind-panel h3, .bind-panel p { margin: 0; }
.bind-panel h3 { font-size: var(--nx-font-size-md); }
.binding-form { display: grid; gap: var(--nx-space-3); max-width: 720px; }
.binding-form label, .bind-panel label { display: grid; gap: var(--nx-space-1); color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.binding-form input, .binding-form textarea, .binding-form select, .bind-panel select {
  width: 100%; min-height: 40px; padding: var(--nx-space-2) var(--nx-space-3);
  color: var(--nx-text-primary); background: var(--nx-surface);
  border: 1px solid var(--nx-border-strong); border-radius: 8px;
}
.binding-form select:disabled { background: var(--nx-bg-page); color: var(--nx-text-disabled); }
.hint { color: var(--nx-warning); font-size: var(--nx-font-size-xs); }
.permission-note { margin: 0; color: var(--nx-text-secondary); }
.warn { color: var(--nx-warning); font-size: var(--nx-font-size-sm); }
.error { margin: 0; color: var(--nx-danger); }
.saved { margin: 0; color: var(--nx-success); }
.actions { display: flex; gap: var(--nx-space-2); }
@media (max-width: 960px) { .journey { grid-template-columns: 1fr 1fr; } }
</style>
