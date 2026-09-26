<template>
  <main class="print-designer" data-testid="print-designer">
    <NxPageHeader
      eyebrow="P4 · 平台配置"
      title="打印设计器"
      description="按分区查看打印槽位与版面；基于内置样例版式，不是商用排版引擎。"
      :crumbs="crumbs"
    >
      <template #actions>
        <router-link class="ghost" :to="sampleLink" data-testid="designer-back-sample">返回打印样例</router-link>
      </template>
    </NxPageHeader>

    <NxSkeleton v-if="loading" variant="list-row" label="正在读取打印版式" data-testid="designer-skeleton" />

    <NxErrorState
      v-else-if="error"
      data-testid="designer-error"
      :title="error"
      :trace-id="traceId"
      retry-label="重试"
      @retry="load"
    />

    <NxEmpty
      v-else-if="!definitions.length"
      data-testid="designer-no-product"
      title="还没有产品定义"
      description="打印版式按当前产品的表单生成。先保存一个产品定义，再回来设计。"
    >
      <template #next>
        <router-link class="primary" to="/p4/product-definitions">去产品定义</router-link>
      </template>
    </NxEmpty>

    <template v-else>
      <section class="designer-product nx-card" data-testid="designer-product">
        <label for="designer-product-select">当前产品</label>
        <select id="designer-product-select" v-model="selectedId" data-testid="designer-product-select" @change="onProductChange">
          <option v-for="definition in definitions" :key="definition.id" :value="definition.id">
            {{ definition.name }}（{{ definition.code }}）
          </option>
        </select>
        <span v-if="designer" class="nx-muted" data-testid="designer-template">
          {{ paperText }}
        </span>
      </section>

      <NxErrorState
        v-if="sampleError"
        data-testid="designer-read-error"
        :title="sampleError"
        :trace-id="sampleTraceId"
        retry-label="重试"
        @retry="loadDesigner"
      />

      <NxEmpty
        v-else-if="designer && !slots.length"
        data-testid="designer-no-slots"
        title="暂无打印槽位"
        description="读不到可套用的表单字段。请先给产品绑定已发布的表单模板。"
      >
        <template #next>
          <router-link class="primary" to="/p4/product-definitions">去产品定义</router-link>
        </template>
      </NxEmpty>

      <div v-else-if="designer" class="designer-body">
        <aside class="designer-slots nx-card" aria-label="槽位列表" data-testid="designer-slot-list">
          <h2>槽位</h2>
          <section v-for="section in designer.sections" :key="section.key" class="slot-group">
            <h3>{{ section.label }}<small class="nx-muted">{{ section.slots.length }}</small></h3>
            <ul>
              <li v-for="slot in section.slots" :key="slot.id">
                <button
                  type="button"
                  class="slot-item"
                  :class="{ selected: selectedSlotId === slot.id }"
                  :aria-pressed="selectedSlotId === slot.id"
                  :data-testid="`designer-slot-${slot.id}`"
                  @click="selectSlot(slot.id)"
                >
                  <span>{{ slot.label }}</span>
                  <small>{{ slot.source === "system" ? "系统属性" : typeLabel(slot.type) }}</small>
                </button>
              </li>
            </ul>
          </section>
        </aside>

        <div class="designer-stage" data-testid="designer-stage">
          <article class="designer-paper" :class="designer.orientation === 'LANDSCAPE' ? 'landscape' : 'portrait'" aria-label="画板">
            <h3 class="paper-title">{{ designer.title }}</h3>
            <section
              v-for="section in designer.sections"
              :key="section.key"
              class="paper-zone"
              :data-testid="`designer-zone-${section.key}`"
            >
              <h4>{{ section.label }}</h4>
              <div class="zone-slots">
                <button
                  v-for="slot in section.slots"
                  :key="slot.id"
                  type="button"
                  class="zone-slot"
                  :class="{ selected: selectedSlotId === slot.id, wide: slot.type === 'TEXTAREA' }"
                  :data-testid="`designer-board-${slot.id}`"
                  @click="selectSlot(slot.id)"
                >
                  <span class="zone-label">{{ slot.label }}</span>
                  <span class="zone-line" aria-hidden="true"></span>
                </button>
                <p v-if="!section.slots.length" class="zone-empty">本分区暂无槽位</p>
              </div>
            </section>
          </article>
        </div>

        <aside class="designer-props nx-card" aria-label="槽位属性" data-testid="designer-props">
          <h2>属性</h2>
          <dl v-if="selectedSlot" data-testid="designer-props-detail">
            <div><dt>槽位</dt><dd>{{ selectedSlot.label }}</dd></div>
            <div><dt>分区</dt><dd>{{ sectionLabel(selectedSlot.section) }}</dd></div>
            <div><dt>内容来源</dt><dd>{{ selectedSlot.source === "system" ? "申请单系统属性" : "产品表单字段" }}</dd></div>
            <div><dt>字段</dt><dd>{{ selectedSlot.fieldKey }} · {{ typeLabel(selectedSlot.type) }}</dd></div>
          </dl>
          <p v-else class="nx-muted" data-testid="designer-props-empty">在左侧或画板上选一个槽位。</p>
        </aside>
      </div>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton } from "../../components";
import { p4 } from "../../api/client";
import { allSlots, designerFromSample, typeLabel } from "./printDesigner";

/** CF-RD1: designer canvas shell over the PC11 built-in print layout (slot list · board zones · properties). */
const route = useRoute();
const router = useRouter();

const loading = ref(true);
const error = ref("");
const traceId = ref("");
const definitions = ref([]);
const selectedId = ref("");
const designer = ref(null);
const sampleError = ref("");
const sampleTraceId = ref("");
const selectedSlotId = ref("");

const slots = computed(() => allSlots(designer.value));
const paperText = computed(() => {
  const paper = `${designer.value?.paper || "A4"} ${designer.value?.orientation === "LANDSCAPE" ? "横向" : "纵向"}`;
  const name = designer.value?.templateName || "";
  return name.includes(paper) ? name : `${name} · ${paper}`;
});
const selectedSlot = computed(() => slots.value.find((slot) => slot.id === selectedSlotId.value) || null);
const productQuery = computed(() => (selectedId.value ? { definition: selectedId.value } : {}));
const sampleLink = computed(() => ({ path: "/p4/product-samples/print", query: productQuery.value }));
const crumbs = computed(() => [
  { label: "产品配置向导", to: { path: "/p4/product-config-wizard", query: productQuery.value } },
  { label: "打印模板样例", to: sampleLink.value },
  { label: "打印设计器" },
]);

function sectionLabel(key) {
  return designer.value?.sections.find((section) => section.key === key)?.label || key;
}

function selectSlot(id) {
  selectedSlotId.value = id;
}

async function loadDesigner() {
  designer.value = null;
  sampleError.value = "";
  sampleTraceId.value = "";
  selectedSlotId.value = "";
  const id = selectedId.value;
  if (!id) return;
  try {
    const sample = await p4.productPrintSample(id);
    if (selectedId.value !== id) return;
    designer.value = designerFromSample(sample);
    selectedSlotId.value = slots.value[0]?.id || "";
  } catch (err) {
    if (selectedId.value !== id) return;
    sampleError.value = err?.message || "打印版式暂时无法读取";
    sampleTraceId.value = err?.traceId || "";
  }
}

function onProductChange() {
  router.replace({ query: { ...route.query, definition: selectedId.value } });
  loadDesigner();
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
  await loadDesigner();
  loading.value = false;
}

onMounted(load);
</script>

<style scoped>
.print-designer {
  display: grid;
  gap: var(--nx-space-4);
}
.designer-product {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--nx-space-2) var(--nx-space-3);
  padding: var(--nx-space-3) var(--nx-space-4);
  font-size: var(--nx-font-size-sm);
}
.designer-product label {
  font-weight: 600;
}
.designer-product select {
  min-width: 240px;
  max-width: 100%;
}
.designer-body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 260px;
  gap: var(--nx-space-3);
  align-items: start;
}
.designer-slots,
.designer-props {
  padding: var(--nx-space-3);
  display: grid;
  gap: var(--nx-space-2);
}
.designer-slots h2,
.designer-props h2 {
  margin: 0;
  font-size: var(--nx-font-size-md);
}
.slot-group h3 {
  display: flex;
  justify-content: space-between;
  margin: var(--nx-space-2) 0 var(--nx-space-1);
  font-size: var(--nx-font-size-sm);
}
.slot-group ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 2px;
}
.slot-item {
  display: flex;
  justify-content: space-between;
  gap: var(--nx-space-2);
  width: 100%;
  padding: 6px var(--nx-space-2);
  border: 1px solid transparent;
  border-radius: var(--nx-radius-sm);
  background: transparent;
  color: inherit;
  text-align: left;
  font-size: var(--nx-font-size-sm);
  cursor: pointer;
}
.slot-item small {
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-xs);
}
.slot-item.selected,
.zone-slot.selected {
  border-color: var(--nx-product);
  background: var(--nx-product-light);
}
.designer-stage {
  display: flex;
  justify-content: center;
  padding: var(--nx-space-4);
  background: var(--nx-divider);
  border-radius: var(--nx-radius-sm);
  overflow: auto;
}
.designer-paper {
  width: min(100%, 520px);
  aspect-ratio: 210 / 297;
  padding: 32px 36px;
  background: #fff;
  color: #1f2329;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: hidden;
}
.designer-paper.landscape {
  width: min(100%, 720px);
  aspect-ratio: 297 / 210;
}
.paper-title {
  margin: 0;
  text-align: center;
  font-size: 17px;
  letter-spacing: 2px;
}
.paper-zone {
  padding: 6px;
  border: 1px dashed #c9cdd4;
  border-radius: 4px;
}
.paper-zone h4 {
  margin: 0 0 6px;
  font-size: 12px;
  color: #646a73;
}
.zone-slots {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 16px;
}
.zone-slot {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 4px;
  border: 1px solid transparent;
  border-radius: 3px;
  background: transparent;
  color: inherit;
  font-size: 12px;
  cursor: pointer;
}
.zone-slot.wide {
  grid-column: 1 / -1;
  min-height: 40px;
}
.zone-label {
  white-space: nowrap;
}
.zone-line {
  flex: 1;
  min-height: 14px;
  border-bottom: 1px solid #8f959e;
}
.zone-empty {
  margin: 0;
  font-size: 12px;
  color: #8f959e;
}
.designer-props dl {
  margin: 0;
  display: grid;
  gap: var(--nx-space-2);
  font-size: var(--nx-font-size-sm);
}
.designer-props dt {
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-xs);
}
.designer-props dd {
  margin: 0;
}
@media (max-width: 1100px) {
  .designer-body {
    grid-template-columns: 1fr;
  }
}
</style>