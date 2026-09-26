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
        :title="sampleError"
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
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton } from "../../components";
import { p4 } from "../../api/client";

/** PC8+: open a read-only sample for the current product (ledger; print and report follow in PC10/PC12). */
const KINDS = {
  ledger: {
    title: "样例台账",
    description: "按当前产品绑定的表单字段生成的台账样例：看得到列，平台侧不含租户记录。",
    read: (id) => p4.productLedgerSample(id),
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

const columns = computed(() => (Array.isArray(sample.value?.columns) ? sample.value.columns.filter((c) => c?.key) : []));
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
</style>