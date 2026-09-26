<template>
  <main class="binding-page">
    <NxPageHeader
      eyebrow="P4 · 平台"
      title="产品定义"
      description="把已发布的表单模板版本和流程版本组合成产品，再绑定 SKU，租户即可在应用目录订阅开通。此页仅平台管理员使用。"
    >
      <template #actions>
        <button
          class="ghost"
          type="button"
          data-testid="export-config"
          :disabled="exporting || !definitions.length"
          @click="exportConfig()"
        >{{ exporting ? "导出中…" : "导出配置" }}</button>
        <button
          class="ghost"
          type="button"
          data-testid="import-config"
          :aria-expanded="importOpen ? 'true' : 'false'"
          @click="openImport"
        >导入配置</button>
        <router-link class="primary" to="/p4/product-config-wizard" data-testid="open-config-wizard">配置向导</router-link>
        <router-link class="ghost" to="/p1/form-templates">表单模板</router-link>
        <router-link class="ghost" to="/p3/workflow">流程设计器</router-link>
        <router-link class="ghost" to="/p4/product-samples/ledger" data-testid="definition-sample-ledger">样例台账</router-link>
        <router-link class="ghost" to="/p4/market">应用目录</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <NxErrorState
      v-if="exportError"
      title="配置未能导出"
      :description="exportError"
      retry-label="重试导出"
      data-testid="export-config-error"
      @retry="exportConfig(exportTarget)"
    />

    <section
      v-if="importOpen"
      class="nx-card import-panel"
      aria-label="导入产品配置"
      data-testid="import-config-panel"
    >
      <div class="section-heading">
        <div>
          <h2>导入配置</h2>
          <p class="nx-muted">上传 format 1.0 配置包，选择冲突策略后先校验预览，再提交导入作业。</p>
        </div>
        <button class="ghost" type="button" data-testid="import-config-close" @click="closeImport">关闭</button>
      </div>

      <div class="import-fields">
        <label class="nx-field">
          <span>配置包（JSON）</span>
          <input
            data-testid="import-config-file"
            type="file"
            accept="application/json,.json"
            @change="onImportFile"
          />
        </label>
        <fieldset class="conflict-strategy" data-testid="import-conflict-strategy">
          <legend>冲突策略</legend>
          <label><input v-model="importConflict" type="radio" name="import-conflict" value="skip" />跳过已有（skip）</label>
          <label><input v-model="importConflict" type="radio" name="import-conflict" value="new-version" />创建新版本（new-version）</label>
          <label><input v-model="importConflict" type="radio" name="import-conflict" value="fail" />遇冲突整包失败（fail）</label>
        </fieldset>
        <div class="import-actions">
          <button
            class="primary"
            type="button"
            data-testid="import-config-validate"
            :disabled="!importFileName || importValidating || importSubmitting"
            @click="validateImport"
          >{{ importValidating ? "校验中…" : "校验并预览" }}</button>
          <button
            class="primary"
            type="button"
            data-testid="import-config-submit"
            :disabled="!importValidated || !!importIssues.length || importSubmitting || importValidating"
            @click="submitImport"
          >{{ importSubmitting ? "导入中…" : "开始导入" }}</button>
        </div>
      </div>

      <NxErrorState
        v-if="importError"
        title="配置包无法处理"
        :description="importError"
        :trace-id="importTraceId"
        retry-label=""
        data-testid="import-config-error"
      />

      <template v-else-if="importJob">
        <NxResult
          compact
          :tone="importJobTone"
          :title="importJobTitle"
          :description="importJobDescription"
          data-testid="import-config-job-result"
        >
          <template #next>
            <button class="ghost" type="button" data-testid="import-config-reset" @click="resetImport">重新选择文件</button>
            <button class="ghost" type="button" data-testid="import-config-reload" @click="load">刷新产品定义</button>
          </template>
        </NxResult>
        <div v-if="importJobResults.length" class="table-scroll">
          <table class="nx-table nx-table--compact" data-testid="import-job-results-table">
            <thead>
              <tr><th scope="col">资产</th><th scope="col">编号</th><th scope="col">结果</th><th scope="col">说明</th></tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in importJobResults" :key="`${row.resourceType}-${row.resourceKey}-${index}`">
                <td>{{ row.resourceType }}</td>
                <td><code>{{ row.resourceKey || "—" }}</code></td>
                <td>{{ row.outcome }}</td>
                <td>{{ row.detail || "—" }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else-if="importValidated">
        <NxResult
          v-if="!importIssues.length"
          compact
          tone="success"
          title="校验通过"
          :description="`冲突策略：${conflictLabel}。可以开始导入。`"
          data-testid="import-config-result"
        />
        <template v-else>
          <NxResult
            compact
            tone="warning"
            title="校验未通过"
            :description="`发现 ${importIssues.length} 处问题。请修正配置包后再导入。`"
            data-testid="import-config-result"
          />
          <div class="table-scroll">
            <table class="nx-table nx-table--compact" data-testid="import-validation-table">
              <thead>
                <tr><th scope="col">资产</th><th scope="col">编号</th><th scope="col">原因</th></tr>
              </thead>
              <tbody>
                <tr v-for="(issue, index) in importIssues" :key="`${issue.asset}-${issue.code}-${index}`">
                  <td>{{ issue.asset }}</td>
                  <td><code>{{ issue.code || "—" }}</code></td>
                  <td>{{ issue.reason }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </template>
    </section>

    <template v-if="!exportError">
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
        <div class="section-heading"><h2>已有定义</h2><span>{{ loaded ? `共 ${definitions.length} 个` : "正在加载…" }}</span></div>
        <NxEmpty
          v-if="loaded && !definitions.length"
          title="暂无产品定义"
          description="在下方选择已发布的模板和流程，保存后即可绑定 SKU。"
          compact
        ><template #next><a href="#new-definition" class="ghost">填写产品定义</a></template></NxEmpty>
        <div v-if="definitions.length" class="table-scroll"><table class="nx-table nx-table--compact" data-testid="product-definition-list">
          <thead>
            <tr><th scope="col">产品</th><th scope="col">状态</th><th scope="col">表单模板</th><th scope="col">流程</th><th scope="col">已绑定 SKU</th><th scope="col">操作</th></tr>
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
                <button type="button" class="ghost small" :data-testid="`export-config-${row.code}`" :disabled="exporting" @click="exportConfig(row)">导出配置</button>
                <button type="button" class="ghost small" :data-testid="`bind-open-${row.code}`" @click="openBind(row.id)">绑定 SKU</button>
                <button v-if="row.status === 'DRAFT'" type="button" class="ghost small" @click="edit(row)">编辑</button>
              </td>
            </tr>
          </tbody>
        </table></div>

        <form v-if="bindingDefinition" class="bind-panel" data-testid="bind-panel" @submit.prevent="bindSku">
          <h3>为「{{ bindingDefinition.name || bindingDefinition.code }}」绑定 SKU</h3>
          <p class="nx-muted">绑定后，租户在应用目录订阅该 SKU 时会开通这个产品。一个 SKU 只能绑定一个产品。</p>
          <NxEmpty v-if="!skus.length" title="暂无 SKU" description="先在星市创建应用与 SKU，再回来绑定。" compact>
            <template #next><router-link class="ghost" to="/p4/market">去应用目录</router-link></template>
          </NxEmpty>
          <template v-else>
            <label class="nx-field">
              <span>SKU<em class="nx-req" aria-hidden="true">*</em></span>
              <select v-model="bindSkuCode" data-testid="bind-sku" required :aria-invalid="!!bindError">
                <option value="">请选择</option>
                <option v-for="sku in skus" :key="sku.code" :value="sku.code">{{ sku.name || sku.code }}（{{ sku.code }}）</option>
              </select>
              <small v-if="bindError" class="nx-field-error" role="alert" data-testid="bind-error">{{ bindError }}</small>
            </label>
            <p v-if="rebindWarning" class="warn" data-testid="bind-rebind-warning">{{ rebindWarning }}</p>
            <div class="actions">
              <button class="primary" type="submit" data-testid="bind-submit" :disabled="binding || !bindSkuCode">{{ binding ? "绑定中…" : "确认绑定" }}</button>
              <button class="ghost" type="button" @click="closeBind">取消</button>
            </div>
          </template>
        </form>
      </section>

      <form id="new-definition" class="nx-card binding-form" data-testid="product-definition-form" @submit.prevent="save">
        <h2>{{ editingId ? "编辑草稿" : "新建产品定义" }}</h2>
        <label class="nx-field"><span>编码<em class="nx-req" aria-hidden="true">*</em></span><input v-model="form.code" data-testid="definition-code" required maxlength="80" :aria-invalid="touched.code && !form.code.trim()" @blur="touched.code = true" /><small v-if="touched.code && !form.code.trim()" class="nx-field-error">请填写产品编码</small></label>
        <label class="nx-field"><span>名称<em class="nx-req" aria-hidden="true">*</em></span><input v-model="form.name" data-testid="definition-name" required maxlength="160" :aria-invalid="touched.name && !form.name.trim()" @blur="touched.name = true" /><small v-if="touched.name && !form.name.trim()" class="nx-field-error">请填写产品名称</small></label>
        <label class="nx-field"><span>说明</span><textarea v-model="form.description" data-testid="definition-description" rows="2"></textarea></label>
        <label class="nx-field">
          <span>已发布模板版本<em class="nx-req" aria-hidden="true">*</em></span>
          <select
            v-model="form.formTemplateVersionId"
            data-testid="definition-template"
            required
            :disabled="templateSelectDisabled"
            :title="templateSelectDisabled ? templateDisabledReason : undefined"
            :aria-describedby="templateSelectDisabled ? 'definition-template-hint' : undefined"
          >
            <option value="">{{ publishedTemplates.length ? "请选择" : "暂无已发布模板" }}</option>
            <option v-for="item in publishedTemplates" :key="item.runningVersionId" :value="item.runningVersionId">
              {{ item.name || item.code }}（{{ item.code || "暂无" }}）
            </option>
          </select>
          <small v-if="loaded && templateSelectDisabled" id="definition-template-hint" class="hint" data-testid="definition-template-empty">
            {{ templateDisabledReason }}
            <router-link to="/p1/form-templates">去表单模板发布</router-link>
          </small>
        </label>
        <label class="nx-field">
          <span>已发布流程版本<em class="nx-req" aria-hidden="true">*</em></span>
          <select
            v-model="form.processKey"
            data-testid="definition-process"
            required
            :disabled="processSelectDisabled"
            :title="processSelectDisabled ? processDisabledReason : undefined"
            :aria-describedby="processSelectDisabled ? 'definition-process-hint' : undefined"
          >
            <option value="">{{ publishedProcesses.length ? "请选择" : "暂无已发布流程" }}</option>
            <option v-for="item in publishedProcesses" :key="`${item.key}@${item.version}`" :value="item.key">
              {{ item.name || item.key }} · {{ item.key }} · 版本 {{ item.version }}
            </option>
          </select>
          <small
            v-if="loaded && processSelectDisabled"
            id="definition-process-hint"
            class="hint"
            :data-testid="processUnavailable ? 'definition-process-unavailable' : 'definition-process-empty'"
          >
            {{ processDisabledReason }}
            <router-link v-if="!processUnavailable" to="/p3/workflow">去流程设计器发布</router-link>
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
import { NxEmpty, NxErrorState, NxPageHeader, NxResult, NxStatusTag } from "../../components";
import { nxMessage } from "../../feedback/nxMessage";

const CONFLICT_LABELS = {
  skip: "跳过已有（skip）",
  "new-version": "创建新版本（new-version）",
  fail: "遇冲突整包失败（fail）",
};

const permissionNote = "租户成员可发起；审批人来自表单中的成员字段，且必须是本租户成员。";

const definitions = ref([]);
const publishedTemplates = ref([]);
const publishedProcesses = ref([]);
const unpublishedTemplateCount = ref(0);
const unpublishedProcessDraftCount = ref(0);
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
const exporting = ref(false);
const exportError = ref("");
const exportTarget = ref(null);
const importOpen = ref(false);
const importConflict = ref("skip");
const importFileName = ref("");
const importPayload = ref(null);
const importValidating = ref(false);
const importValidated = ref(false);
const importSubmitting = ref(false);
const importError = ref("");
const importTraceId = ref("");
const importIssues = ref([]);
const importJob = ref(null);
const conflictLabel = computed(() => CONFLICT_LABELS[importConflict.value] || importConflict.value);
const importJobResults = computed(() => {
  const rows = importJob.value?.results ?? importJob.value?.Results ?? [];
  return (Array.isArray(rows) ? rows : []).map((row) => ({
    resourceType: row?.resource_type ?? row?.resourceType ?? "",
    resourceKey: row?.resource_key ?? row?.resourceKey ?? "",
    outcome: row?.outcome ?? "",
    detail: row?.detail ?? "",
  }));
});
const importJobStatus = computed(() => String(importJob.value?.status || "").toUpperCase());
const importJobTone = computed(() => (importJobStatus.value === "FAILED" ? "warning" : "success"));
const importJobTitle = computed(() => {
  if (importJobStatus.value === "FAILED") return "导入未完成";
  if (importJobStatus.value === "COMPLETED") return "导入完成";
  return "导入已受理";
});
const importJobDescription = computed(() => {
  const counts = importJob.value?.counts || {};
  const created = counts.created ?? counts.CREATED ?? 0;
  const skipped = counts.skipped ?? counts.SKIPPED ?? 0;
  const failed = counts.failed ?? counts.FAILED ?? 0;
  const jobId = importJob.value?.id || "";
  const err = importJob.value?.error || "";
  if (importJobStatus.value === "FAILED") {
    return err || `作业 ${jobId} 失败。冲突策略：${conflictLabel.value}。`;
  }
  return `作业 ${jobId} · 冲突策略：${conflictLabel.value} · 已创建 ${created} / 跳过 ${skipped} / 失败 ${failed}。`;
});
const form = reactive({
  code: "",
  name: "",
  description: "",
  formTemplateVersionId: "",
  processKey: "",
});
const touched = reactive({ code: false, name: false });

const canSave = computed(() => form.code.trim() && form.name.trim() && form.formTemplateVersionId && form.processKey);
const bindingDefinition = computed(() => definitions.value.find((row) => row.id === bindingFor.value) || null);
const boundCount = computed(() => Object.keys(skuBindings.value).length);
const templateSelectDisabled = computed(() => !publishedTemplates.value.length);
const processSelectDisabled = computed(() => !publishedProcesses.value.length);
const templateDisabledReason = computed(() => {
  if (unpublishedTemplateCount.value) {
    return `下拉已禁用：有 ${unpublishedTemplateCount.value} 个模板还是草稿或仅通过校验，发布后才会出现在这里。`;
  }
  return "下拉已禁用：还没有已发布的表单模板。";
});
const processDisabledReason = computed(() => {
  if (processUnavailable.value) return processUnavailable.value;
  if (unpublishedProcessDraftCount.value) {
    return `下拉已禁用：有 ${unpublishedProcessDraftCount.value} 个流程草稿尚未发布，发布后才会出现在这里。`;
  }
  return "下拉已禁用：当前租户还没有已发布的流程（classpath 只读目录不能直接绑定）。";
});
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
  touched.code = false;
  touched.name = false;
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
  const status = Number(err?.status);
  // Not signed in: the drafts API answers 401 P2.IAM.UNAUTHORIZED "missing bearer token" (J3 observation A).
  if (status === 401 || err?.code === "P2.IAM.UNAUTHORIZED") {
    return "已发布流程按租户读取：当前未登录租户账号。请以租户管理员身份登录（演示：林晓）后再选择流程。";
  }
  if (/tenantcontext|tenant context/i.test(message) || status === 403) {
    return "已发布流程按租户读取：当前没有租户身份。请以租户管理员身份登录（演示：林晓）后再选择流程。";
  }
  return `流程列表暂时读不到${err?.traceId ? `（追踪号 ${err.traceId}）` : ""}，可稍后刷新重试。`;
}

async function loadPublishedProcesses(drafts, catalog) {
  const keys = new Set();
  for (const draft of Array.isArray(drafts) ? drafts : []) {
    if (draft?.key) keys.add(draft.key);
  }
  // J3 断点 B：种子流程可能已部署到 Flowable，但草稿表为空；本页应与流程设计器「已发布」目录对齐。
  for (const item of Array.isArray(catalog) ? catalog : []) {
    const source = item?.source || "";
    if (item?.key && (source === "tenant-deployment" || item?.version != null || item?.tenant_id || item?.tenantId)) {
      keys.add(item.key);
    }
  }
  const processes = [];
  const seen = new Set();
  for (const key of keys) {
    try {
      const publication = await p3.getProcessPublication(key);
      if (publication?.key && publication.version != null && !seen.has(publication.key)) {
        seen.add(publication.key);
        processes.push(publication);
      }
    } catch {
      // Unpublished drafts / classpath-only catalog entries are not selectable.
    }
  }
  const draftKeys = (Array.isArray(drafts) ? drafts : []).filter((draft) => draft?.key).map((draft) => draft.key);
  unpublishedProcessDraftCount.value = draftKeys.filter((key) => !seen.has(key)).length;
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


async function exportConfig(row = null) {
  exportError.value = "";
  const target = row || (definitions.value.length === 1 ? definitions.value[0] : null);
  exportTarget.value = target;
  if (!target?.id) {
    exportError.value = definitions.value.length
      ? "请从下方列表选择产品，点击该行的「导出配置」。"
      : "还没有产品定义可导出。请先保存一个产品定义。";
    return;
  }
  exporting.value = true;
  try {
    const { blob, filename } = await p4.exportProductDefinition(target.id);
    if (!blob || blob.size === 0) {
      throw new Error("下载内容为空，未生成配置文件。");
    }
    const downloadName = filename || `${target.code || "product"}.product-config.json`;
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = downloadName;
    anchor.rel = "noopener";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
    nxMessage.success(`已导出「${target.name || target.code}」的 format 1.0 配置包`);
  } catch (err) {
    exportError.value = err?.message || "导出失败，请稍后重试";
  } finally {
    exporting.value = false;
  }
}

function openImport() {
  importOpen.value = true;
  exportError.value = "";
}

function closeImport() {
  importOpen.value = false;
}

function resetImport() {
  importFileName.value = "";
  importPayload.value = null;
  importValidated.value = false;
  importSubmitting.value = false;
  importError.value = "";
  importTraceId.value = "";
  importIssues.value = [];
  importJob.value = null;
}

async function onImportFile(event) {
  resetImport();
  const file = event.target?.files?.[0];
  if (!file) return;
  importFileName.value = file.name;
  try {
    const text = await file.text();
    importPayload.value = JSON.parse(text);
    importError.value = "";
  } catch (err) {
    importPayload.value = null;
    importError.value = err instanceof SyntaxError
      ? "文件不是合法 JSON，请选择 format 1.0 配置包。"
      : "读取文件失败，请重试。";
  }
}

/** Client-side format 1.0 pre-check before calling IM5 import. */
function validateProductConfigPackage(pack) {
  const issues = [];
  if (!pack || typeof pack !== "object") {
    return [{ asset: "包", code: "", reason: "根对象缺失" }];
  }
  const manifest = pack.manifest;
  if (!manifest || typeof manifest !== "object") {
    issues.push({ asset: "manifest", code: "", reason: "缺少清单" });
  } else {
    if (manifest.format !== "nexusone.product-config") {
      issues.push({ asset: "manifest", code: "format", reason: `不支持的 format：${manifest.format || "空"}` });
    }
    if (manifest.formatVersion !== "1.0") {
      issues.push({ asset: "manifest", code: "formatVersion", reason: `仅支持 formatVersion 1.0，当前为 ${manifest.formatVersion || "空"}` });
    }
  }
  if (!Array.isArray(pack.formTemplates) || !pack.formTemplates.length) {
    issues.push({ asset: "formTemplates", code: "", reason: "至少需要一份已发布表单模板快照" });
  }
  if (!Array.isArray(pack.processDefinitions) || !pack.processDefinitions.length) {
    issues.push({ asset: "processDefinitions", code: "", reason: "至少需要一份已发布流程定义" });
  }
  if (!pack.productDefinition || typeof pack.productDefinition !== "object") {
    issues.push({ asset: "productDefinition", code: "", reason: "缺少产品定义" });
  } else if (!pack.productDefinition.code) {
    issues.push({ asset: "productDefinition", code: "", reason: "产品定义缺少 code" });
  }
  if (!Array.isArray(pack.skuBindings)) {
    issues.push({ asset: "skuBindings", code: "", reason: "缺少 skuBindings 数组（可为 []）" });
  }
  return issues;
}

async function validateImport() {
  importValidating.value = true;
  importValidated.value = false;
  importError.value = "";
  importTraceId.value = "";
  importIssues.value = [];
  importJob.value = null;
  try {
    if (!importPayload.value) {
      importError.value = importFileName.value
        ? "配置包未能解析，请重新选择 JSON 文件。"
        : "请先选择配置包文件。";
      return;
    }
    await Promise.resolve();
    importIssues.value = validateProductConfigPackage(importPayload.value);
    importValidated.value = true;
    if (!importIssues.value.length) {
      nxMessage.info("校验通过，可以开始导入");
    }
  } finally {
    importValidating.value = false;
  }
}

async function submitImport() {
  if (!importPayload.value || importIssues.value.length) return;
  importSubmitting.value = true;
  importError.value = "";
  importTraceId.value = "";
  importJob.value = null;
  const idempotencyKey = (typeof crypto !== "undefined" && crypto.randomUUID)
    ? crypto.randomUUID()
    : `im7-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  try {
    const accepted = await p4.importProductDefinition(importPayload.value, importConflict.value, idempotencyKey);
    const jobId = accepted?.import_job_id ?? accepted?.importJobId;
    if (!jobId) {
      throw new Error("导入已受理，但未返回作业编号。");
    }
    const job = await p4.productDefinitionImportJob(jobId);
    importJob.value = job;
    const status = String(job?.status || "").toUpperCase();
    if (status === "FAILED") {
      nxMessage.error(job?.error || "导入失败");
    } else {
      nxMessage.success("导入完成");
      await load();
    }
  } catch (err) {
    importError.value = err?.message || "导入失败，请稍后重试";
    importTraceId.value = err?.traceId || "";
  } finally {
    importSubmitting.value = false;
  }
}
async function load() {
  loaded.value = false;
  error.value = "";
  try {
    // Process drafts / definitions are tenant-scoped; that must not take down templates and definitions.
    processUnavailable.value = "";
    unpublishedProcessDraftCount.value = 0;
    const [rows, templates, draftResult, catalogResult] = await Promise.all([
      p4.productDefinitions(),
      p1.formTemplates(),
      p3.listProcessDrafts().then((list) => ({ ok: true, list })).catch((err) => ({ ok: false, err, list: [] })),
      p3.listProcessDefinitions().then((list) => ({ ok: true, list })).catch((err) => ({ ok: false, err, list: [] })),
    ]);
    definitions.value = (Array.isArray(rows) ? rows : []).map(normalizeDefinition);
    const allTemplates = (Array.isArray(templates) ? templates : [])
      .map((item) => ({ ...item, runningVersionId: pick(item, "running_version_id", "runningVersionId") }));
    publishedTemplates.value = allTemplates.filter((item) => item.status === "PUBLISHED" && item.runningVersionId);
    unpublishedTemplateCount.value = allTemplates.length - publishedTemplates.value.length;
    if (!draftResult.ok && !catalogResult.ok) {
      processUnavailable.value = processUnavailableText(draftResult.err || catalogResult.err);
      publishedProcesses.value = [];
    } else {
      publishedProcesses.value = await loadPublishedProcesses(
        draftResult.ok ? draftResult.list : [],
        catalogResult.ok ? catalogResult.list : [],
      );
    }
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
.binding-page :deep(.page-header) { margin-bottom: 0; }
.import-panel .section-heading { align-items: flex-start; }
.import-panel .section-heading p { margin: var(--nx-space-1) 0 0; max-width: 48rem; }
.import-fields { display: grid; gap: var(--nx-space-4); margin-top: var(--nx-space-3); }
.conflict-strategy { margin: 0; padding: var(--nx-space-3); border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); display: grid; gap: var(--nx-space-2); }
.conflict-strategy legend { padding: 0 var(--nx-space-1); color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.conflict-strategy label { display: flex; align-items: center; gap: var(--nx-space-2); color: var(--nx-text-regular); font-size: var(--nx-font-size-sm); }
.import-actions { display: flex; gap: var(--nx-space-2); }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; gap: var(--nx-space-3); }
.section-heading span { color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.table-scroll { overflow-x: auto; }
.table-scroll .nx-table { min-width: 760px; }
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
.binding-form .nx-field, .bind-panel .nx-field { margin-bottom: 0; }
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
