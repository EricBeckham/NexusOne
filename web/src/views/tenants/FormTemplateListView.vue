<template>
  <main class="template-list-page">
    <NxPageHeader
      eyebrow="星域 / 平台配置"
      title="表单模板"
      description="维护平台模板草稿，校验并发布后可供产品定义与配置向导选用。这里不是租户台账表单。"
      :crumbs="crumbs"
    >
      <template #actions>
        <button class="ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <router-link class="ghost" to="/p4/product-config-wizard" data-testid="template-open-wizard">配置向导</router-link>
        <router-link class="primary" to="/p1/form-templates/new">新建模板</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="load" />
    <template v-else>
      <div class="list-toolbar nx-card" data-testid="template-list-toolbar">
        <div class="list-meta" aria-live="polite">
          <h2>模板目录</h2>
          <span data-testid="template-list-summary">{{ summaryText }}</span>
        </div>
        <div class="status-chips" role="group" aria-label="按发布态筛选">
          <button
            v-for="chip in STATUS_CHIPS"
            :key="chip.id"
            type="button"
            class="status-chip"
            :class="{ active: statusFilter === chip.id }"
            :data-testid="`template-filter-${chip.id}`"
            :aria-pressed="statusFilter === chip.id"
            @click="statusFilter = chip.id"
          >
            {{ chip.label }}
            <em v-if="!loading" class="chip-count">{{ chipCount(chip.id) }}</em>
          </button>
        </div>
      </div>

      <NxEmpty
        v-if="!loading && !rows.length"
        data-testid="template-list-empty"
        title="还没有表单模板"
        description="新建并发布模板后，可在产品配置向导的「配表单」步骤中继续完成流程与 SKU 绑定。"
      >
        <template #next>
          <router-link class="primary" to="/p1/form-templates/new">新建模板</router-link>
          <router-link class="ghost" to="/p4/product-config-wizard">打开配置向导</router-link>
        </template>
      </NxEmpty>

      <NxEmpty
        v-else-if="!loading && !filteredRows.length"
        data-testid="template-list-filter-empty"
        title="没有符合筛选的模板"
        description="试试切换到「全部」，或新建一份草稿。"
        compact
      >
        <template #next>
          <button type="button" class="ghost" data-testid="template-filter-reset" @click="statusFilter = 'all'">查看全部</button>
        </template>
      </NxEmpty>

      <div v-else class="table-scroll">
        <table class="nx-table nx-table--compact template-table" data-testid="template-list">
          <thead>
            <tr>
              <th scope="col">名称</th>
              <th scope="col">编码</th>
              <th scope="col">发布态</th>
              <th scope="col">版本</th>
              <th scope="col">字段数</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6" class="loading-cell">
                <NxSkeleton v-for="index in 3" :key="index" variant="list-row" label="正在加载模板目录" compact />
              </td>
            </tr>
            <template v-else>
              <tr v-for="row in filteredRows" :key="row.id" :data-status="normalizeStatus(row.status)">
                <td class="template-name">{{ row.name || "未命名模板" }}</td>
                <td class="nx-code">{{ row.code || "暂无" }}</td>
                <td>
                  <NxStatusTag
                    :status="normalizeStatus(row.status)"
                    :label="statusLabel(row.status)"
                    :tone="statusTone(row.status)"
                    :data-testid="`template-status-${normalizeStatus(row.status) || 'none'}`"
                  />
                </td>
                <td class="version-cell" data-testid="template-version">{{ versionText(row) }}</td>
                <td>{{ Array.isArray(row.fields) ? row.fields.length : "暂无" }}</td>
                <td>
                  <router-link class="text-action" :to="`/p1/form-templates/${row.id}`">
                    {{ normalizeStatus(row.status) === "PUBLISHED" ? "查看" : "编辑" }}
                  </router-link>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { p1 } from "../../api/client";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton, NxStatusTag } from "../../components";

const STATUS_CHIPS = [
  { id: "all", label: "全部" },
  { id: "DRAFT", label: "草稿" },
  { id: "VALIDATED", label: "已校验" },
  { id: "PUBLISHED", label: "已发布" },
  { id: "ARCHIVED", label: "已归档" },
];

const rows = ref([]);
const loading = ref(true);
const error = ref("");
const traceId = ref("");
const statusFilter = ref("all");
const crumbs = [
  { label: "配置向导", to: "/p4/product-config-wizard" },
  { label: "表单模板" },
];

function normalizeStatus(status) {
  return String(status || "").trim().toUpperCase();
}

function statusLabel(status) {
  return (
    { DRAFT: "草稿", VALIDATED: "已校验", PUBLISHED: "已发布", ARCHIVED: "已归档" }[normalizeStatus(status)]
    || status
    || "暂无"
  );
}

function statusTone(status) {
  return (
    { DRAFT: "neutral", VALIDATED: "info", PUBLISHED: "success", ARCHIVED: "neutral" }[normalizeStatus(status)]
    || "neutral"
  );
}

function versionText(row) {
  const version = row?.publishedVersion ?? row?.version ?? row?.latestVersion;
  if (version === 0 || version) return `v${version}`;
  return normalizeStatus(row?.status) === "PUBLISHED" ? "已发布" : "—";
}

function chipCount(id) {
  if (id === "all") return rows.value.length;
  return rows.value.filter((row) => normalizeStatus(row.status) === id).length;
}

const filteredRows = computed(() => {
  if (statusFilter.value === "all") return rows.value;
  return rows.value.filter((row) => normalizeStatus(row.status) === statusFilter.value);
});

const summaryText = computed(() => {
  if (loading.value) return "正在加载模板…";
  const published = chipCount("PUBLISHED");
  const drafts = chipCount("DRAFT");
  return `共 ${rows.value.length} 个 · 已发布 ${published} · 草稿 ${drafts}`;
});

async function load() {
  loading.value = true;
  error.value = "";
  traceId.value = "";
  try {
    const body = await p1.formTemplates();
    rows.value = Array.isArray(body) ? body : [];
  } catch (err) {
    rows.value = [];
    error.value = err?.message || "表单模板加载失败";
    traceId.value = err?.traceId || "";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.template-list-page { display: grid; gap: var(--nx-space-4); }
.template-list-page :deep(.page-header) { margin-bottom: 0; }
.list-toolbar {
  display: grid;
  gap: var(--nx-space-3);
  padding: var(--nx-space-4);
}
.list-meta { display: flex; align-items: baseline; justify-content: space-between; gap: var(--nx-space-3); flex-wrap: wrap; }
.list-meta h2 { margin: 0; font-size: var(--nx-font-size-md); }
.list-meta span { color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.status-chips { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); }
.status-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--nx-space-1);
  min-height: 32px;
  padding: 0 var(--nx-space-3);
  border-radius: 999px;
  border: 1px solid var(--nx-border-strong);
  background: var(--nx-surface);
  color: var(--nx-text-regular);
  font-size: var(--nx-font-size-sm);
  cursor: pointer;
}
.status-chip.active {
  border-color: var(--nx-product);
  color: var(--nx-product);
  background: var(--nx-primary-light, color-mix(in srgb, var(--nx-product) 12%, white));
  font-weight: 600;
}
.chip-count {
  font-style: normal;
  color: var(--nx-text-secondary);
  font-variant-numeric: tabular-nums;
}
.status-chip.active .chip-count { color: var(--nx-product); }
.table-scroll { overflow-x: auto; border: 1px solid var(--nx-border); border-radius: var(--nx-radius); background: var(--nx-surface); }
.template-table { min-width: 640px; border: 0; }
.template-table :deep(th),
.template-table :deep(td) {
  height: var(--nx-table-row-height-compact);
  padding: var(--nx-space-2) var(--nx-table-cell-padding-x);
  vertical-align: middle;
}
.template-name { font-weight: 600; }
.version-cell { color: var(--nx-text-secondary); font-variant-numeric: tabular-nums; }
.loading-cell { padding: var(--nx-space-2) !important; }
.loading-cell :deep(.nx-skeleton) { margin-bottom: var(--nx-space-2); border: 0; }
.text-action { color: var(--nx-product); text-decoration: none; font-weight: 600; }
.text-action:hover { text-decoration: underline; }
.primary,.ghost { display: inline-flex; align-items: center; justify-content: center; min-height: var(--nx-control-height); padding: 0 var(--nx-space-3); border-radius: var(--nx-control-radius); text-decoration: none; cursor: pointer; }
.primary { color: #fff; background: var(--nx-product); border: 1px solid var(--nx-product); }
.ghost { color: var(--nx-text-regular); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); }
.ghost:disabled { opacity: .55; cursor: not-allowed; }
</style>