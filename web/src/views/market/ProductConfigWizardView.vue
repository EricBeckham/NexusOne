<template>
  <main class="product-config-wizard" data-testid="product-config-wizard">
    <NxPageHeader
      eyebrow="P4 · 平台配置"
      title="产品配置向导"
      description="按顺序完成：配表单 → 配流程 → 绑 SKU → 发布。就绪态来自产品定义读口；未确认的步骤不打勾。"
      :crumbs="crumbs"
    >
      <template #actions>
        <button class="ghost" type="button" :disabled="loading" data-testid="wizard-refresh" @click="load">刷新</button>
        <router-link class="ghost" to="/p4/product-definitions">产品定义</router-link>
        <router-link class="ghost" to="/p1/form-templates">表单模板</router-link>
        <router-link class="ghost" to="/p3/workflow">流程设计器</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState
      v-if="error"
      data-testid="wizard-error"
      :title="error"
      :trace-id="traceId"
      retry-label="重试"
      @retry="load"
    />

    <template v-else>
      <section v-if="definitions.length" class="wizard-product nx-card" data-testid="wizard-product">
        <label for="wizard-product-select">当前产品</label>
        <select id="wizard-product-select" v-model="selectedDefinitionId" data-testid="wizard-product-select" @change="onProductChange">
          <option v-for="definition in definitions" :key="definition.id" :value="definition.id">
            {{ definition.name }}（{{ definition.code }}）
          </option>
        </select>
        <span v-if="readinessError" class="readiness-error" data-testid="wizard-readiness-error">
          步骤状态暂时无法读取，按「待开始」显示。
          <button class="ghost" type="button" @click="loadReadiness">重试</button>
        </span>
        <span v-else-if="readiness?.complete" class="nx-muted" data-testid="wizard-complete">四步都已就绪。</span>
        <nav class="wizard-samples" aria-label="当前产品样例" data-testid="wizard-samples">
          <span class="nx-muted">样例：</span>
          <router-link :to="{ path: '/p4/product-samples/ledger', query: { definition: selectedDefinitionId } }" data-testid="wizard-sample-ledger">台账</router-link>
          <router-link :to="{ path: '/p4/product-samples/print', query: { definition: selectedDefinitionId } }" data-testid="wizard-sample-print">打印</router-link>
        </nav>
      </section>

      <ol class="wizard-steps" aria-label="产品配置步骤" data-testid="wizard-steps">
        <li
          v-for="(step, index) in steps"
          :key="step.id"
          class="wizard-step"
          :class="{ active: activeStepId === step.id }"
          :data-status="step.status"
          :data-testid="`wizard-step-${step.id}`"
        >
          <button type="button" class="step-hit" @click="activeStepId = step.id">
            <span class="step-index">{{ index + 1 }}</span>
            <span class="step-body">
              <strong>{{ step.title }}</strong>
              <small>{{ stepStatusLabel(step.status) }}</small>
            </span>
          </button>
        </li>
      </ol>

      <NxSkeleton v-if="loading" variant="list-row" label="正在读取配置摘要" data-testid="wizard-skeleton" />

      <section v-else class="wizard-panel nx-card" data-testid="wizard-panel" :aria-labelledby="`step-heading-${activeStep.id}`">
        <h2 :id="`step-heading-${activeStep.id}`">{{ activeStep.title }}</h2>
        <p class="nx-muted">{{ activeStep.blurb }}</p>
        <p class="status-line" data-testid="wizard-step-status">
          当前状态：<strong>{{ stepStatusLabel(activeStep.status) }}</strong>
          <span v-if="activeStep.detail" class="nx-muted" data-testid="wizard-step-detail">{{ activeStep.detail }}</span>
          <span v-else-if="!definitions.length" class="nx-muted">（还没有产品定义，步骤都待开始）</span>
        </p>

        <NxEmpty
          v-if="activeEmpty"
          :data-testid="`wizard-empty-${activeStep.id}`"
          :title="activeEmpty.title"
          :description="activeEmpty.description"
          compact
        >
          <template #next>
            <router-link class="primary" :to="activeEmpty.to">{{ activeEmpty.action }}</router-link>
          </template>
        </NxEmpty>

        <template v-else-if="activeStep.id === 'bind'">
          <p class="hint-ok" data-testid="wizard-hint-ok">{{ activeHintOk }}</p>
          <div class="bind-shell" data-testid="wizard-bind-panel">
            <div class="table-scroll">
              <table class="nx-table nx-table--compact" data-testid="wizard-definition-list">
                <thead>
                  <tr><th>产品</th><th>状态</th><th>操作</th></tr>
                </thead>
                <tbody>
                  <tr v-for="row in definitions" :key="row.id">
                    <td>
                      <strong>{{ row.name || "暂无" }}</strong>
                      <div class="nx-muted">{{ row.code || "暂无" }}</div>
                    </td>
                    <td>{{ row.status || "暂无" }}</td>
                    <td>
                      <button
                        type="button"
                        class="ghost small"
                        :data-testid="`wizard-bind-open-${row.code || row.id}`"
                        @click="openBind(row.id)"
                      >
                        绑定 SKU
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <p v-if="bindNotice" class="bind-notice" role="status" data-testid="wizard-bind-notice">{{ bindNotice }}</p>

            <form
              v-if="bindingDefinition"
              class="bind-form"
              data-testid="wizard-bind-form"
              @submit.prevent="submitBind"
            >
              <h3>为「{{ bindingDefinition.name || bindingDefinition.code }}」绑定 SKU</h3>
              <label class="nx-field">
                <span>SKU<em class="nx-req" aria-hidden="true">*</em></span>
                <select v-model="bindSkuCode" data-testid="wizard-bind-sku" required>
                  <option value="">请选择</option>
                  <option v-for="sku in skus" :key="sku.code" :value="sku.code">
                    {{ sku.name || sku.code }}（{{ sku.code }}）
                  </option>
                </select>
                <small v-if="bindError" class="nx-field-error" role="alert" data-testid="wizard-bind-error">{{ bindError }}</small>
              </label>
              <div class="panel-actions">
                <button class="primary" type="submit" data-testid="wizard-bind-submit" :disabled="binding || !bindSkuCode">
                  {{ binding ? "绑定中…" : "确认绑定" }}
                </button>
                <button class="ghost" type="button" @click="closeBind">取消</button>
              </div>
            </form>
          </div>
        </template>

        <p v-else class="hint-ok" data-testid="wizard-hint-ok">{{ activeHintOk }}</p>

        <div class="panel-actions">
          <router-link v-if="activeStep.to" class="primary" :to="activeStep.to" :data-testid="`wizard-cta-${activeStep.id}`">
            {{ activeStep.cta }}
          </router-link>
          <router-link class="ghost" to="/p4/product-definitions" data-testid="wizard-open-definitions">打开已有产品定义</router-link>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton } from "../../components";
import { p1, p4 } from "../../api/client";
import { p3 } from "../../api/p3";

/** PC1/PC3: fixed four steps. PC7: status comes from the PC6 readiness read for the selected product only. */
const STEP_DEFS = [
  {
    id: "form",
    title: "配表单",
    blurb: "在平台表单模板中创建草稿、校验并发布版本，供产品定义选用。",
    to: "/p1/form-templates",
    cta: "去表单模板",
  },
  {
    id: "process",
    title: "配流程",
    blurb: "在流程设计器中配置并发布审批流程版本。",
    to: "/p3/workflow",
    cta: "去流程设计器",
  },
  {
    id: "bind",
    title: "绑 SKU",
    blurb: "保存产品定义后，将 SKU 绑定到该产品，租户才可在应用目录开通。",
    to: "/p4/product-definitions",
    cta: "去产品定义绑定",
  },
  {
    id: "publish",
    title: "发布",
    blurb: "确认表单、流程与 SKU 绑定就绪后，产品即可出现在应用目录供订阅开通。",
    to: "/p3/publish-center",
    cta: "打开发布中心",
  },
];

const crumbs = [
  { label: "产品定义", to: "/p4/product-definitions" },
  { label: "产品配置向导" },
];

// PC6 step keys differ from the wizard ids in one place: the backend calls the bind step "sku".
const READINESS_KEY = { form: "form", process: "process", bind: "sku", publish: "publish" };
const KNOWN_STATUS = new Set(["DONE", "NOT_READY", "UNKNOWN"]);

const route = useRoute();
const router = useRouter();
const selectedDefinitionId = ref("");
const readiness = ref(null);
const readinessError = ref(false);

const steps = computed(() => STEP_DEFS.map((step) => {
  const remote = readiness.value?.steps?.find((item) => item?.key === READINESS_KEY[step.id]);
  const status = KNOWN_STATUS.has(remote?.status) ? remote.status : "PENDING";
  return { ...step, status, detail: remote && status !== "PENDING" ? remote.detail || "" : "" };
}));
const activeStepId = ref(STEP_DEFS[0].id);
const activeStep = computed(() => steps.value.find((step) => step.id === activeStepId.value) || steps.value[0]);

const loading = ref(true);
const error = ref("");
const traceId = ref("");
const publishedTemplateCount = ref(0);
const publishedProcessCount = ref(0);
const definitionCount = ref(0);
const skuCount = ref(0);
const processHint = ref("");
const definitions = ref([]);
const skus = ref([]);
const bindingFor = ref("");
const bindSkuCode = ref("");
const bindError = ref("");
const bindNotice = ref("");
const binding = ref(false);

const bindingDefinition = computed(() => definitions.value.find((row) => row.id === bindingFor.value) || null);

const activeEmpty = computed(() => {
  const id = activeStep.value.id;
  if (id === "form" && publishedTemplateCount.value === 0) {
    return {
      title: "还没有已发布的表单模板",
      description: "先新建模板草稿，校验通过后再发布版本，产品定义才能选用。",
      to: "/p1/form-templates/new",
      action: "新建表单模板",
    };
  }
  if (id === "process" && publishedProcessCount.value === 0) {
    return {
      title: processHint.value || "还没有已发布的流程",
      description: processHint.value
        ? "流程列表需要可用的租户或平台身份。登录后可在流程设计器发布。"
        : "在流程设计器中配置审批节点并发布后，才能写入产品定义。",
      to: "/p3/workflow",
      action: "去流程设计器",
    };
  }
  if (id === "bind" && (definitionCount.value === 0 || skuCount.value === 0)) {
    return {
      title: definitionCount.value === 0 ? "还没有产品定义" : "还没有可绑定的 SKU",
      description: definitionCount.value === 0
        ? "先保存产品定义（选用已发布模板与流程），再为它绑定 SKU。"
        : "先在星市创建应用与 SKU，再回到产品定义页完成绑定。",
      to: definitionCount.value === 0 ? "/p4/product-definitions" : "/p4/skus",
      action: definitionCount.value === 0 ? "去新建产品定义" : "去商品与 SKU",
    };
  }
  if (id === "publish" && definitionCount.value === 0) {
    return {
      title: "尚未有可发布的产品配置",
      description: "完成表单、流程与产品定义后，再通过发布中心或应用目录确认上架效果。",
      to: "/p4/product-definitions",
      action: "去产品定义",
    };
  }
  return null;
});

const activeHintOk = computed(() => {
  const id = activeStep.value.id;
  if (id === "form") return `已发现 ${publishedTemplateCount.value} 个已发布表单模板（摘要，非完成勾选）。`;
  if (id === "process") return `已发现 ${publishedProcessCount.value} 个已发布流程（摘要，非完成勾选）。`;
  if (id === "bind") return `已有 ${definitionCount.value} 个产品定义、${skuCount.value} 个 SKU 可供绑定（摘要）。`;
  return `已有 ${definitionCount.value} 个产品定义；请在发布中心或应用目录确认上架效果。`;
});

function stepStatusLabel(status) {
  if (status === "DONE") return "已完成";
  if (status === "NOT_READY") return "未就绪";
  if (status === "UNKNOWN") return "无法确认";
  return "待开始";
}

async function loadReadiness() {
  readiness.value = null;
  readinessError.value = false;
  const id = selectedDefinitionId.value;
  if (!id) return;
  try {
    const result = await p4.productConfigReadiness(id);
    if (selectedDefinitionId.value === id) readiness.value = result;
  } catch {
    if (selectedDefinitionId.value === id) readinessError.value = true;
  }
}

function onProductChange() {
  router.replace({ query: { ...route.query, definition: selectedDefinitionId.value } });
  loadReadiness();
}

function normalizeDefinition(row) {
  return {
    id: row?.id,
    code: row?.code || "",
    name: row?.name || "",
    status: row?.status || "",
  };
}

function openBind(id) {
  bindingFor.value = id;
  bindSkuCode.value = "";
  bindError.value = "";
  bindNotice.value = "";
}

function closeBind() {
  bindingFor.value = "";
  bindSkuCode.value = "";
  bindError.value = "";
}

async function submitBind() {
  if (!bindingDefinition.value || !bindSkuCode.value || binding.value) return;
  binding.value = true;
  bindError.value = "";
  bindNotice.value = "";
  const target = bindingDefinition.value;
  const code = bindSkuCode.value;
  try {
    await p4.bindProductDefinitionSku(target.id, code);
    closeBind();
    bindNotice.value = `已把 SKU ${code} 绑定到「${target.name || target.code}」。可在产品定义页查看详情。`;
    await load();
  } catch (err) {
    bindError.value = err?.message || "绑定失败";
  } finally {
    binding.value = false;
  }
}

async function load() {
  loading.value = true;
  error.value = "";
  traceId.value = "";
  processHint.value = "";
  try {
    const [templates, definitionRows, skuRows] = await Promise.all([
      p1.formTemplates(),
      p4.productDefinitions(),
      p4.skus(),
    ]);
    const templateRows = Array.isArray(templates) ? templates : [];
    publishedTemplateCount.value = templateRows.filter((row) => String(row?.status || "").toUpperCase() === "PUBLISHED").length;
    definitions.value = (Array.isArray(definitionRows) ? definitionRows : []).map(normalizeDefinition).filter((row) => row.id);
    definitionCount.value = definitions.value.length;
    const wanted = String(route.query.definition || "");
    selectedDefinitionId.value = definitions.value.some((row) => row.id === wanted) ? wanted : definitions.value[0]?.id || "";
    skus.value = (Array.isArray(skuRows) ? skuRows : []).filter((sku) => sku?.code);
    skuCount.value = skus.value.length;

  } catch (err) {
    error.value = err?.message || "配置摘要暂时不可用";
    traceId.value = err?.traceId || "";
    loading.value = false;
    return;
  }

  try {
    const defs = await p3.listProcessDefinitions();
    publishedProcessCount.value = Array.isArray(defs) ? defs.length : 0;
  } catch (err) {
    publishedProcessCount.value = 0;
    processHint.value = "暂时读不到已发布流程";
  }
  await loadReadiness();
  loading.value = false;
}

onMounted(load);
</script>

<style scoped>
.product-config-wizard {
  display: grid;
  gap: var(--nx-space-4);
}
.wizard-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--nx-space-3);
}
.wizard-step {
  margin: 0;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
  background: var(--nx-surface);
}
.wizard-step.active {
  border-color: var(--nx-product);
  box-shadow: 0 0 0 1px var(--nx-product-light);
}
.wizard-step[data-status="PENDING"] .step-index {
  background: var(--nx-divider);
  color: var(--nx-text-secondary);
}
.wizard-step[data-status="DONE"] .step-index {
  background: var(--nx-success);
  color: #fff;
}
.wizard-step[data-status="NOT_READY"] .step-index,
.wizard-step[data-status="UNKNOWN"] .step-index {
  background: var(--nx-divider);
  color: var(--nx-text-secondary);
}
.wizard-step[data-status="UNKNOWN"] .step-body small {
  color: var(--nx-warning, var(--nx-text-secondary));
}
.wizard-product {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--nx-space-2) var(--nx-space-3);
  padding: var(--nx-space-3) var(--nx-space-4);
  font-size: var(--nx-font-size-sm);
}
.wizard-product label {
  font-weight: 600;
}
.wizard-product select {
  min-width: 240px;
  max-width: 100%;
}
.wizard-samples {
  display: inline-flex;
  gap: var(--nx-space-2);
  margin-left: auto;
}
.readiness-error {
  color: var(--nx-text-secondary);
}
.step-hit {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--nx-space-2);
  width: 100%;
  padding: var(--nx-space-3);
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: inherit;
}
.step-index {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-size: var(--nx-font-size-xs);
  font-weight: 700;
}
.step-body {
  display: grid;
  gap: 2px;
}
.step-body strong {
  font-size: var(--nx-font-size-sm);
}
.step-body small {
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-xs);
}
.wizard-panel {
  display: grid;
  gap: var(--nx-space-3);
  padding: var(--nx-space-4) var(--nx-space-5);
}
.wizard-panel h2 {
  margin: 0;
  font-size: var(--nx-font-size-lg);
}
.status-line .nx-muted {
  margin-left: var(--nx-space-2);
}
.status-line, .hint-ok {
  margin: 0;
  font-size: var(--nx-font-size-sm);
}
.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nx-space-2);
}
.bind-shell { display: grid; gap: var(--nx-space-3); }
.bind-form { display: grid; gap: var(--nx-space-3); padding-top: var(--nx-space-3); border-top: 1px solid var(--nx-divider); }
.bind-form h3 { margin: 0; font-size: var(--nx-font-size-md); }
.bind-notice { margin: 0; padding: var(--nx-space-2); border-radius: var(--nx-radius-sm); background: var(--nx-success-light); color: var(--nx-success); font-size: var(--nx-font-size-sm); }
.table-scroll { overflow: auto; }
@media (max-width: 900px) {
  .wizard-steps {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
