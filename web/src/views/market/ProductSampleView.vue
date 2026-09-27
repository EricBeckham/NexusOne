<template>
  <main class="product-sample" data-testid="product-sample" :data-kind="kind">
    <NxPageHeader eyebrow="P4 · 平台配置" :title="meta.title" :description="meta.description" :crumbs="crumbs">
      <template #actions>
        <router-link
          v-if="kind === 'print'"
          class="ghost"
          :to="{ path: '/p4/product-samples/print/designer', query: selectedId ? { definition: selectedId } : {} }"
          data-testid="sample-open-designer"
        >打开设计器</router-link>
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

        <section class="print-config" aria-label="打印配置" data-testid="sample-print-config">
          <NxErrorState
            v-if="mappingError"
            title="打印映射暂时无法读取"
            :description="mappingError"
            :trace-id="mappingTraceId"
            retry-label="重试"
            data-testid="sample-print-mapping-error"
            @retry="loadPrintMapping"
          />
          <template v-else>
            <NxResult
              v-if="mappingConflict"
              compact
              tone="warning"
              data-testid="sample-print-mapping-conflict"
              title="服务器上的映射已经变了，本次没有保存"
              :description="mappingConflictText"
            >
              <template #next>
                <button class="primary" type="button" data-testid="sample-print-conflict-reload" :disabled="mappingSaving" @click="reloadMappingFromServer">载入服务器版本</button>
                <button class="ghost" type="button" data-testid="sample-print-conflict-overwrite" :disabled="mappingSaving" @click="overwriteMapping">用我的修改覆盖</button>
              </template>
            </NxResult>
            <div class="print-paper-row">
              <label class="nx-field">
                <span>纸张</span>
                <select v-model="printPaper" data-testid="sample-print-paper-size">
                  <option v-for="paper in paperSizes" :key="paper.code" :value="paper.code">{{ paper.label || paper.code }}</option>
                </select>
              </label>
              <label class="nx-field">
                <span>方向</span>
                <select v-model="printOrientation" data-testid="sample-print-orientation">
                  <option v-for="item in orientations" :key="item.code" :value="item.code">{{ item.label || item.code }}</option>
                </select>
              </label>
              <button
                class="ghost"
                type="button"
                data-testid="sample-print-mapping-save"
                :disabled="mappingSaving || !selectedId || Boolean(mappingConflict)"
                @click="savePrintMapping()"
              >{{ mappingSaving ? "保存中…" : "保存映射" }}</button>
            </div>
            <p v-if="mappingSaveMessage" class="nx-muted" data-testid="sample-print-mapping-saved">{{ mappingSaveMessage }}</p>
            <NxEmpty
              v-if="!mappingRows.length"
              compact
              data-testid="sample-print-mapping-empty"
              title="暂无字段映射"
              description="当前产品还没有可映射的表单字段。绑定已发布表单后会出现字段→槽位表。"
            />
            <div v-else class="table-scroll">
              <table class="nx-table nx-table--compact" data-testid="sample-print-mapping-table">
                <thead>
                  <tr><th scope="col">表单字段</th><th scope="col">打印槽位</th></tr>
                </thead>
                <tbody>
                  <tr v-for="row in mappingRows" :key="row.fieldKey" :data-testid="`sample-print-map-${row.fieldKey}`">
                    <td><code>{{ row.fieldKey }}</code><small class="nx-muted">{{ row.fieldLabel }}</small></td>
                    <td>
                      <select v-model="row.slotId" :data-testid="`sample-print-slot-${row.fieldKey}`">
                        <option value="">未映射（落入表单区）</option>
                        <option v-for="slot in printSlots" :key="slot.id" :value="slot.id">{{ slot.label }}（{{ slot.id }}）</option>
                      </select>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </section>

        <NxEmpty
          v-if="!previewSections.length"
          compact
          data-testid="sample-print-no-sections"
          title="暂无打印分区"
          :description="sample.note || '读不到可套用的表单字段分区。请先在产品定义绑定已发布的表单模板，再回来预览。'"
        >
          <template #next>
            <router-link class="primary" to="/p4/product-definitions">去产品定义</router-link>
          </template>
        </NxEmpty>

        <div v-else class="paper-stage" data-testid="sample-print-readonly-preview">
          <p class="preview-banner nx-muted" data-testid="sample-print-preview-note">
            只读 HTML 预览 · 字段值留空 · 非拖拽设计器
          </p>
          <p v-if="fromDesigner" class="preview-banner preview-from-designer" data-testid="sample-print-from-designer">
            来自设计器 · 按{{ previewBasis }}生成，不含设计器里未保存的修改。
            <router-link :to="designerLink" data-testid="sample-print-back-designer">返回设计器</router-link>
          </p>
          <article class="paper" :class="orientationClass" aria-label="打印只读预览" data-testid="sample-print-paper">
            <h3 class="paper-title">{{ sample.title || sample.product_name }}</h3>
            <section
              v-for="section in previewSections"
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
                <div
                  v-for="field in section.fields"
                  :key="field.key"
                  class="paper-field"
                  :class="{ wide: field.type === 'TEXTAREA' || field.wide }"
                  :data-testid="`sample-print-preview-field-${field.key}`"
                >
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
import { computed, nextTick, onMounted, ref, watch } from "vue";
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
const printContract = ref(null);
const printPaper = ref("A4");
const printOrientation = ref("PORTRAIT");
const mappingRows = ref([]);
const mappingError = ref("");
const mappingTraceId = ref("");
const mappingSaving = ref(false);
const mappingSaveMessage = ref("");
const mappingConflict = ref(null);
const mappingConflictText = computed(() => {
  if (!mappingConflict.value) return "";
  const theirs = typeof mappingConflict.value.server?.revision === "number" ? mappingConflict.value.server.revision : null;
  const mine = mappingConflict.value.basedOn;
  return `你基于 ${mine == null ? "未知修订" : `r${mine}`} 修改，服务器现在是 ${theirs == null ? "未知修订" : `r${theirs}`}。为避免静默覆盖，本次没有保存；可以载入服务器版本，或明确用你的修改覆盖。`;
});
// CF-RD4: the designer opens this preview with ?from=designer; say which saved mapping the paper shows.
const mappingSource = ref("");
const mappingRevision = ref(null);
const fromDesigner = computed(() => kind.value === "print" && route.query.from === "designer");
const designerLink = computed(() => ({
  path: "/p4/product-samples/print/designer",
  query: selectedId.value ? { definition: selectedId.value } : {},
}));
const previewBasis = computed(() => {
  if (mappingSource.value === "STORED") {
    return mappingRevision.value == null ? "已保存的映射" : `已保存的映射（修订 r${mappingRevision.value}）`;
  }
  if (mappingSource.value === "IN_MEMORY") {
    return mappingRevision.value == null ? "服务器进程内已保存的映射" : `服务器进程内已保存的映射（修订 r${mappingRevision.value}）`;
  }
  if (mappingSource.value === "DEFAULT") return "契约默认映射（设计器尚未保存过）";
  return "打印样例分区（映射暂时读不到）";
});

const printSections = computed(() => (Array.isArray(sample.value?.sections) ? sample.value.sections : [])
  .filter((section) => section?.key)
  .map((section) => ({ ...section, fields: Array.isArray(section.fields) ? section.fields.filter((f) => f?.key) : [] })));
const paperSizes = computed(() => {
  const rows = printContract.value?.paper_sizes ?? printContract.value?.paperSizes ?? [];
  return Array.isArray(rows) && rows.length ? rows : [{ code: "A4", label: "A4" }, { code: "A5", label: "A5" }, { code: "LETTER", label: "Letter" }];
});
const orientations = computed(() => {
  const rows = printContract.value?.orientations ?? [];
  return Array.isArray(rows) && rows.length ? rows : [{ code: "PORTRAIT", label: "纵向" }, { code: "LANDSCAPE", label: "横向" }];
});
const printSlots = computed(() => {
  const template = printContract.value?.builtin_template ?? printContract.value?.builtinTemplate ?? {};
  const slots = template.slots ?? [];
  return (Array.isArray(slots) ? slots : []).filter((slot) => slot?.id);
});
/** CF-R2: readonly HTML preview prefers mapping-driven sections; falls back to sample sections. */
const previewSections = computed(() => {
  const fieldsByKey = new Map();
  for (const section of printSections.value) {
    for (const field of section.fields || []) {
      if (field?.key) fieldsByKey.set(field.key, { ...field, sectionKey: section.key, sectionLabel: section.label });
    }
  }
  if (!mappingRows.value.length || !printSlots.value.length) {
    return printSections.value.map((section) => ({
      key: section.key,
      label: section.label,
      fields: section.fields.map((field) => ({ ...field, wide: field.type === "TEXTAREA" })),
    }));
  }
  const spillId = printContract.value?.mapping_rules?.unmapped_spill_slot_id
    ?? printContract.value?.mappingRules?.unmappedSpillSlotId
    ?? "form.fields";
  const bySlot = new Map();
  for (const row of mappingRows.value) {
    const field = fieldsByKey.get(row.fieldKey);
    if (!field) continue;
    const slotId = row.slotId || spillId;
    if (!bySlot.has(slotId)) bySlot.set(slotId, []);
    bySlot.get(slotId).push({
      key: field.key,
      label: field.label,
      type: field.type,
      wide: field.type === "TEXTAREA" || slotId === spillId,
    });
  }
  const sectionOrder = [];
  const sectionMap = new Map();
  const sampleSectionKeys = new Set(printSections.value.map((section) => section.key));
  for (const slot of printSlots.value) {
    const sectionKey = slot.section || String(slot.id).split(".")[0] || "form";
    const fields = bySlot.get(slot.id) || [];
    if (!fields.length && !sampleSectionKeys.has(sectionKey)) continue;
    if (!sectionMap.has(sectionKey)) {
      sectionMap.set(sectionKey, {
        key: sectionKey,
        label: ({ header: "单据信息", form: "申请内容", approval: "审批" })[sectionKey] || sectionKey,
        fields: [],
      });
      sectionOrder.push(sectionKey);
    }
    for (const field of fields) {
      sectionMap.get(sectionKey).fields.push(field);
    }
  }
  // Preserve explicit empty sample sections (honest empty shell).
  for (const section of printSections.value) {
    if (!sectionMap.has(section.key)) {
      sectionMap.set(section.key, {
        key: section.key,
        label: section.label,
        fields: section.fields.map((field) => ({ ...field, wide: field.type === "TEXTAREA" })),
      });
      sectionOrder.push(section.key);
    } else if (!sectionMap.get(section.key).fields.length && !section.fields.length) {
      // keep empty
    }
  }
  return sectionOrder.map((key) => sectionMap.get(key)).filter(Boolean);
});
const paperLabel = computed(() => `${printPaper.value || sample.value?.paper || "A4"} ${printOrientation.value === "LANDSCAPE" ? "横向" : "纵向"}`);
const orientationClass = computed(() => (printOrientation.value === "LANDSCAPE" ? "landscape" : "portrait"));
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

function collectPrintFields() {
  const fields = [];
  const seen = new Set();
  for (const section of printSections.value) {
    for (const field of section.fields || []) {
      if (!field?.key || seen.has(field.key)) continue;
      seen.add(field.key);
      fields.push({ key: field.key, label: field.label || field.key });
    }
  }
  return fields;
}

function applyMappingPayload(mapping) {
  const maps = mapping?.mappings ?? [];
  const byField = new Map(
    (Array.isArray(maps) ? maps : []).map((row) => [
      row.field_key ?? row.fieldKey,
      row.slot_id ?? row.slotId ?? "",
    ]),
  );
  mappingRows.value = collectPrintFields().map((field) => ({
    fieldKey: field.key,
    fieldLabel: field.label,
    slotId: byField.get(field.key) || "",
  }));
  printPaper.value = mapping?.paper || sample.value?.paper || "A4";
  printOrientation.value = mapping?.orientation || sample.value?.orientation || "PORTRAIT";
  mappingSource.value = mapping?.source || "";
  mappingRevision.value = typeof mapping?.revision === "number" ? mapping.revision : null;
}

async function ensurePrintContract() {
  if (printContract.value) return printContract.value;
  printContract.value = await p4.printMappingContract();
  return printContract.value;
}

async function loadPrintMapping() {
  mappingError.value = "";
  mappingTraceId.value = "";
  mappingSaveMessage.value = "";
  mappingConflict.value = null;
  const id = selectedId.value;
  if (!id || kind.value !== "print") return;
  try {
    await ensurePrintContract();
    const mapping = await p4.productPrintMapping(id);
    if (selectedId.value === id) applyMappingPayload(mapping);
  } catch (err) {
    if (selectedId.value !== id) return;
    mappingError.value = err?.message || "打印映射暂时无法读取";
    mappingTraceId.value = err?.traceId || "";
    mappingRows.value = collectPrintFields().map((field) => ({
      fieldKey: field.key,
      fieldLabel: field.label,
      slotId: "",
    }));
  }
}

async function savePrintMapping(basedOn = mappingRevision.value) {
  const id = selectedId.value;
  if (!id || mappingSaving.value) return;
  mappingSaving.value = true;
  mappingSaveMessage.value = "";
  mappingError.value = "";
  const body = {
    paper: printPaper.value,
    orientation: printOrientation.value,
    mappings: mappingRows.value
      .filter((row) => row.slotId)
      .map((row) => ({ field_key: row.fieldKey, slot_id: row.slotId })),
  };
  if (basedOn != null) body.expected_revision = basedOn;
  try {
    const saved = await p4.saveProductPrintMapping(id, body);
    if (selectedId.value !== id) return;
    mappingConflict.value = null;
    applyMappingPayload(saved);
    // DPO-L3: say where it went; the server reports STORED (database) or IN_MEMORY (lost on restart).
    mappingSaveMessage.value = saved?.source === "IN_MEMORY"
      ? "映射已保存（进程内覆盖，重启后恢复默认）。"
      : `映射已保存到数据库${typeof saved?.revision === "number" ? `（修订 r${saved.revision}）` : ""}，重启后仍在。`;
  } catch (err) {
    if (selectedId.value !== id) return;
    if (err?.status === 409) {
      let server = null;
      try {
        server = await p4.productPrintMapping(id);
      } catch {
        server = null;
      }
      mappingConflict.value = { basedOn, server };
    } else {
      mappingError.value = err?.message || "保存映射失败";
      mappingTraceId.value = err?.traceId || "";
    }
  } finally {
    mappingSaving.value = false;
  }
}

function reloadMappingFromServer() {
  if (mappingConflict.value?.server) applyMappingPayload(mappingConflict.value.server);
  mappingConflict.value = null;
}

function overwriteMapping() {
  const theirs = typeof mappingConflict.value?.server?.revision === "number" ? mappingConflict.value.server.revision : null;
  mappingConflict.value = null;
  savePrintMapping(theirs);
}

async function loadSample() {
  sample.value = null;
  sampleError.value = "";
  sampleTraceId.value = "";
  mappingRows.value = [];
  mappingError.value = "";
  mappingSaveMessage.value = "";
  const id = selectedId.value;
  if (!id) return;
  try {
    const result = await meta.value.read(id);
    if (selectedId.value === id) sample.value = result;
    if (kind.value === "print" && selectedId.value === id) await loadPrintMapping();
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
  if (fromDesigner.value) {
    await nextTick();
    document.querySelector("[data-testid='sample-print-readonly-preview']")?.scrollIntoView?.({ block: "start" });
  }
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
  flex-direction: column;
  align-items: center;
  gap: var(--nx-space-2);
  padding: var(--nx-space-4);
  background: var(--nx-divider);
  border-radius: var(--nx-radius-sm);
  overflow: auto;
}
.preview-banner {
  margin: 0;
  width: 100%;
  text-align: center;
  font-size: var(--nx-font-size-sm);
}
.preview-from-designer {
  color: var(--nx-text);
}
.preview-from-designer a {
  margin-left: var(--nx-space-2);
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
.print-config {
  display: grid;
  gap: var(--nx-space-3);
  padding: var(--nx-space-3);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
  background: var(--nx-surface-secondary, var(--nx-surface));
}
.print-paper-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nx-space-3);
  align-items: end;
}
.print-paper-row .nx-field { min-width: 140px; }
.print-config :deep(small.nx-muted) { display: block; margin-top: 2px; }
</style>