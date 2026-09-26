<template>
  <main class="product-sample" data-testid="product-sample" :data-kind="kind">
    <NxPageHeader eyebrow="P4 · 平台配置" :title="meta.title" :description="meta.description" :crumbs="crumbs">
      <template #actions>
        <router-link class="ghost" :to="wizardLink" data-testid="sample-back-wizard">返回产品配置向导</router-link>
      </template>
    </NxPageHeader>

    <NxSkeleton v-if="loading" variant="list-row" label="正在读取样例" data-testid="sample-skeleton" />

    <NxErrorState
      v-else-if="error"
      data-testid="sample-error"
      :title="error"
      :trace-id="traceId"
      retry-label="重试"
      @retry="load"
    />

    <NxEmpty
      v-else-if="!definitions.length"
      data-testid="sample-no-product"
      title="还没有产品定义"
      description="样例按当前产品生成。先保存一个产品定义（选用已发布的表单模板），再回来打开样例。"
    >
      <template #next>
        <router-link class="primary" to="/p4/product-definitions">去产品定义</router-link>
      </template>
    </NxEmpty>

    <template v-else>
      <section class="sample-product nx-card" data-testid="sample-product">
        <label for="sample-product-select">当前产品</label>
        <select id="sample-product-select" v-model="selectedId" data-testid="sample-product-select" @change="onProductChange">
          <option v-for="definition in definitions" :key="definition.id" :value="definition.id">
            {{ definition.name }}（{{ definition.code }}）
          </option>
        </select>
        <span class="nx-muted">只读样例，不是编辑器。</span>
      </section>

      <NxErrorState
        v-if="sampleError"
        data-testid="sample-read-error"
        :title="sampleErrorTitle"
        :description="sampleErrorDescription"
        :trace-id="sampleTraceId"
        retry-label="重试"
        @retry="loadSample"
      />

      <section v-else-if="kind === 'ledger' && sample" class="sample-card nx-card" data-testid="sample-ledger">
        <header class="sample-meta">
          <h2>{{ sample.product_name || "当前产品" }} · 样例台账</h2>
          <p v-if="sample.form_template_name" class="nx-muted" data-testid="sample-ledger-source">
            列来自表单「{{ sample.form_template_name }}」v{{ sample.form_template_version ?? "?" }}，共 {{ columns.length }} 列。
          </p>
        </header>

        <NxEmpty
          v-if="!columns.length"
          compact
          data-testid="sample-ledger-no-columns"
          title="暂无样例列"
          :description="sample.note || '读不到产品绑定的表单版本。'"
        >
          <template #next>
            <router-link class="primary" to="/p1/form-templates">去表单模板</router-link>
          </template>
        </NxEmpty>

        <div v-else class="table-scroll">
          <table class="nx-table nx-table--compact" data-testid="sample-ledger-table">
            <thead>
              <tr>
                <th v-for="column in columns" :key="column.key" :data-testid="`sample-ledger-col-${column.key}`">
                  {{ column.label }}<em v-if="column.required" class="nx-req" aria-label="必填">*</em>
                  <small class="col-type">{{ typeLabel(column.type) }}</small>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td :colspan="columns.length" class="empty-row" data-testid="sample-ledger-empty-row">
                  <strong>暂无记录</strong>
                  <span class="nx-muted">{{ sample.note }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="kind === 'print' && sample" class="sample-card nx-card" data-testid="sample-print">
        <header class="sample-meta">
          <h2>{{ sample.template_name || "内置样例版式" }}</h2>
          <p class="nx-muted" data-testid="sample-print-meta">
            {{ paperLabel }} · {{ sample.note || "内置样例版式，字段值留空，只用于预览打印效果。" }}
          </p>
        </header>

        <NxResult
          compact
          tone="info"
          data-testid="sample-print-later"
          title="完整打印设计器 · later"
          description="拖拽排版、页眉页脚引擎不在本波。当前只提供内置样例预览；配置映射与纸张枚举若已接通，仍不会变成完整设计器。"
        />

        <NxEmpty
          v-if="!printSections.length"
          compact
          data-testid="sample-print-no-sections"
          title="暂无打印分区"
          :description="sample.note || '读不到可套用的表单字段分区。请先在产品定义绑定已发布的表单模板，再回来预览。'"
        >
          <template #next>
            <router-link class="primary" to="/p4/product-definitions">去产品定义</router-link>
          </template>
        </NxEmpty>

        <div v-else class="paper-stage">
          <article class="paper" :class="orientationClass" aria-label="打印预览" data-testid="sample-print-paper">
            <h3 class="paper-title">{{ sample.title || sample.product_name }}</h3>
            <section
              v-for="section in printSections"
              :key="section.key"
              class="paper-section"
              :data-testid="`sample-print-section-${section.key}`"
            >
              <h4>{{ section.label }}</h4>
              <NxEmpty
                v-if="!section.fields.length"
                compact
                class="paper-empty-shell"
                :data-testid="`sample-print-section-empty-${section.key}`"
                title="本分区暂无字段"
                description="表单未提供可映射到此槽位的字段，留空预览。"
              />
              <dl v-else>
                <div v-for="field in section.fields" :key="field.key" class="paper-field" :class="{ wide: field.type === 'TEXTAREA' }">
                  <dt>{{ field.label }}</dt>
                  <dd aria-label="留空"></dd>
                </div>
              </dl>
            </section>
          </article>
        </div>
      </section>

      <section v-else-if="kind === 'report' && sample" class="sample-card nx-card" data-testid="sample-report">
        <header class="sample-meta">
          <h2>{{ sample.product_name || "当前产品" }} · 统计样例</h2>
          <p class="nx-muted" data-testid="sample-report-meta">
            {{ asOfLabel ? `截至 ${asOfLabel}` : "当前" }} · {{ sample.note || "只统计平台现有记录的当前数量。" }}
          </p>
        </header>
        <ul class="metric-grid" data-testid="sample-report-metrics">
          <li v-for="metric in metrics" :key="metric.key" class="metric" :data-testid="`sample-report-metric-${metric.key}`">
            <span class="metric-label">{{ metric.label }}</span>
            <strong class="metric-value" :class="{ unknown: metric.value == null }">{{ metric.value == null ? "暂无" : metric.value }}</strong>
            <small class="nx-muted">{{ metric.note }}</small>
          </li>
        </ul>
        <p class="trend-note" data-testid="sample-report-trend">
          <strong>趋势</strong>
          <span>{{ sample.trend_available === true ? "趋势读口已提供，本样例页仍只展示当前数量。" : sample.trend_note || "趋势暂无。" }}</span>
        </p>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxResult, NxSkeleton } from "../../components";
import { p4 } from "../../api/client";

/** PC8+: open a read-only sample for the current product (ledger PC8, print PC10, report PC12). */
const KINDS = {
  ledger: {
    title: "样例台账",
    description: "按当前产品绑定的表单字段生成的台账样例：看得到列，平台侧不含租户记录。",
    read: (id) => p4.productLedgerSample(id),
  },
  print: {
    title: "打印模板样例",
    description: "内置版式套用当前产品的表单字段，只读预览打印效果。完整拖拽设计器标 later，本页不提供。",
    read: (id) => p4.productPrintSample(id),
  },
  report: {
    title: "统计报表样例",
    description: "当前产品的几项真实计数；没有按时间聚合的数据，所以不画趋势。",
    read: (id) => p4.productReportSample(id),
  },
};

const TYPE_LABELS = {
  TEXT: "文本",
  TEXTAREA: "多行文本",
  NUMBER: "数字",
  MONEY: "金额",
  DATE: "日期",
  SELECT: "选项",
  PERSON: "人员",
  ATTACHMENT: "附件",
};

const route = useRoute();
const router = useRouter();
const kind = computed(() => (KINDS[route.meta?.sample] ? route.meta.sample : "ledger"));
const meta = computed(() => KINDS[kind.value]);

const loading = ref(true);
const error = ref("");
const traceId = ref("");
const definitions = ref([]);
const selectedId = ref("");
const sample = ref(null);
const sampleError = ref("");
const sampleTraceId = ref("");

const printSections = computed(() => (Array.isArray(sample.value?.sections) ? sample.value.sections : [])
  .filter((section) => section?.key)
  .map((section) => ({ ...section, fields: Array.isArray(section.fields) ? section.fields.filter((f) => f?.key) : [] })));
const paperLabel = computed(() => {
  const paper = sample.value?.paper || "A4";
  return `${paper} ${sample.value?.orientation === "LANDSCAPE" ? "横向" : "纵向"}`;
});
const orientationClass = computed(() => (sample.value?.orientation === "LANDSCAPE" ? "landscape" : "portrait"));
// PC12: a count the backend could not make arrives as null or an absent key — both read as 暂无, never 0.
const metrics = computed(() => (Array.isArray(sample.value?.metrics) ? sample.value.metrics : [])
  .filter((metric) => metric?.key)
  .map((metric) => ({ ...metric, value: typeof metric.value === "number" ? metric.value : null })));
const asOfLabel = computed(() => {
  const at = sample.value?.as_of ? new Date(sample.value.as_of) : null;
  return at && !Number.isNaN(at.getTime()) ? at.toLocaleString("zh-CN", { hour12: false }) : "";
});
const columns = computed(() => (Array.isArray(sample.value?.columns) ? sample.value.columns.filter((c) => c?.key) : []));
const sampleErrorTitle = computed(() => {
  if (kind.value === "print") return "打印样例暂时无法读取";
  return sampleError.value || "样例暂时无法读取";
});
const sampleErrorDescription = computed(() => {
  if (!sampleError.value || kind.value !== "print") return "";
  return `${sampleError.value}。这是内置样例预览，不是完整打印设计器；修好读口后可重试。`;
});
const wizardLink = computed(() => ({ path: "/p4/product-config-wizard", query: selectedId.value ? { definition: selectedId.value } : {} }));
const crumbs = computed(() => [
  { label: "产品配置向导", to: wizardLink.value },
  { label: meta.value.title },
]);

function typeLabel(type) {
  return TYPE_LABELS[String(type || "").toUpperCase()] || type || "文本";
}

async function loadSample() {
  sample.value = null;
  sampleError.value = "";
  sampleTraceId.value = "";
  const id = selectedId.value;
  if (!id) return;
  try {
    const result = await meta.value.read(id);
    if (selectedId.value === id) sample.value = result;
  } catch (err) {
    if (selectedId.value !== id) return;
    sampleError.value = err?.message || "样例暂时无法读取";
    sampleTraceId.value = err?.traceId || "";
  }
}

function onProductChange() {
  router.replace({ query: { ...route.query, definition: selectedId.value } });
  loadSample();
}

async function load() {
  loading.value = true;
  error.value = "";
  traceId.value = "";
  try {
    const rows = await p4.productDefinitions();
    definitions.value = (Array.isArray(rows) ? rows : []).filter((row) => row?.id);
  } catch (err) {
    error.value = err?.message || "产品定义暂时无法读取";
    traceId.value = err?.traceId || "";
    loading.value = false;
    return;
  }
  const wanted = String(route.query.definition || "");
  selectedId.value = definitions.value.some((row) => row.id === wanted) ? wanted : definitions.value[0]?.id || "";
  await loadSample();
  loading.value = false;
}

watch(kind, () => { if (!loading.value) loadSample(); });
onMounted(load);
</script>

<style scoped>
.product-sample {
  display: grid;
  gap: var(--nx-space-4);
}
.sample-product {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--nx-space-2) var(--nx-space-3);
  padding: var(--nx-space-3) var(--nx-space-4);
  font-size: var(--nx-font-size-sm);
}
.sample-product label {
  font-weight: 600;
}
.sample-product select {
  min-width: 240px;
  max-width: 100%;
}
.sample-card {
  display: grid;
  gap: var(--nx-space-3);
  padding: var(--nx-space-4) var(--nx-space-5);
}
.sample-meta h2 {
  margin: 0;
  font-size: var(--nx-font-size-lg);
}
.sample-meta p {
  margin: var(--nx-space-1) 0 0;
  font-size: var(--nx-font-size-sm);
}
.table-scroll {
  overflow: auto;
}
.col-type {
  display: block;
  font-weight: 400;
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-xs);
}
.empty-row {
  text-align: center;
  padding: var(--nx-space-5) var(--nx-space-3);
}
.empty-row strong,
.empty-row span {
  display: block;
}
.empty-row span {
  margin-top: var(--nx-space-1);
  font-size: var(--nx-font-size-sm);
}
.metric-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--nx-space-3);
}
.metric {
  display: grid;
  gap: var(--nx-space-1);
  padding: var(--nx-space-3) var(--nx-space-4);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
}
.metric-label {
  font-size: var(--nx-font-size-sm);
  color: var(--nx-text-secondary);
}
.metric-value {
  font-size: 24px;
  line-height: 1.2;
}
.metric-value.unknown {
  font-size: var(--nx-font-size-lg);
  color: var(--nx-text-secondary);
}
.metric small {
  font-size: var(--nx-font-size-xs);
}
.trend-note {
  display: flex;
  gap: var(--nx-space-2);
  margin: 0;
  padding: var(--nx-space-3) var(--nx-space-4);
  border-radius: var(--nx-radius-sm);
  background: var(--nx-divider);
  font-size: var(--nx-font-size-sm);
}
.paper-stage {
  display: flex;
  justify-content: center;
  padding: var(--nx-space-4);
  background: var(--nx-divider);
  border-radius: var(--nx-radius-sm);
  overflow: auto;
}
.paper {
  width: min(100%, 560px);
  aspect-ratio: 210 / 297;
  padding: 36px 40px;
  background: #fff;
  color: #1f2329;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow: hidden;
}
.paper.landscape {
  width: min(100%, 760px);
  aspect-ratio: 297 / 210;
}
.paper-title {
  margin: 0;
  text-align: center;
  font-size: 18px;
  letter-spacing: 2px;
}
.paper-section h4 {
  margin: 0 0 8px;
  padding-bottom: 4px;
  border-bottom: 1.5px solid #1f2329;
  font-size: 13px;
}
.paper-section dl {
  margin: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 24px;
}
.paper-field {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  font-size: 12px;
}
.paper-field.wide {
  grid-column: 1 / -1;
  min-height: 48px;
}
.paper-field dt {
  white-space: nowrap;
}
.paper-field dd {
  flex: 1;
  margin: 0;
  min-height: 16px;
  border-bottom: 1px solid #8f959e;
}
.paper-empty-shell {
  margin: 4px 0 0;
  padding: var(--nx-space-2) 0;
}
</style>