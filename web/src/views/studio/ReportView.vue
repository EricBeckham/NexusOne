<template>
  <div class="report-page">
    <NxPageHeader title="报表" description="按当前租户权限查询台账，并创建经过字段投影的导出任务。" />
    <NxSourceBanner body="POST /api/v1/p3/reports/query 与 /export 先经 ReportSurfaceGuard。桌面台账 / 表单 / 待办页未改。物理 dump 仍开放。" />
    <p v-if="!session.contextId" class="nx-error">还没有进入租户。报表查询和导出需要先登录并切换租户。</p>

    <!-- R15: permissions as status tags; the raw decision JSON is a dev note (O9). -->
    <section class="permissions" aria-label="报表权限" data-testid="g2-report-decision">
      <span class="perm"><span class="perm-label">查询权限</span><NxStatus :label="decisionLabel(queryDecision)" :tone="decisionTone(queryDecision)" data-testid="report-query-permission" /></span>
      <span class="perm"><span class="perm-label">导出权限</span><NxStatus :label="decisionLabel(exportDecision)" :tone="decisionTone(exportDecision)" data-testid="report-export-permission" /></span>
      <template v-if="devNotes">
        <p v-if="queryDecision" class="nx-code dev-note" data-testid="g2-report-query-cell">query {{ JSON.stringify(queryDecision) }}</p>
        <p v-if="exportDecision" class="nx-code dev-note" data-testid="g2-report-export-cell">export {{ JSON.stringify(exportDecision) }}</p>
      </template>
    </section>
    <p v-if="error" class="nx-error" role="alert" data-testid="g2-report-error">{{ error }}</p>

    <NxFilterBar label="报表查询" testid="g2-report" searchable class="report-filter" @search="ledgerId && !busy && onQuery()" @reset="onReset">
      <label class="nx-filter-field">台账
        <select v-model="ledgerId" required data-testid="g2-report-ledger">
          <option value="" disabled>选择台账</option>
          <option v-for="item in ledgers" :key="item.id" :value="item.id">
            {{ item.name }} ({{ item.code }})
          </option>
        </select>
      </label>
      <template v-if="ran && !busy && !queryFailed" #summary><span data-testid="g2-report-total">共 {{ total }} 条</span></template>
    </NxFilterBar>

    <section class="nx-card results" aria-label="查询结果">
      <header class="results-head">
        <h2>查询结果</h2>
        <div class="results-actions">
          <span v-if="exportResult" class="export-result" data-testid="g2-report-export-result">
            <NxStatus label="已创建导出任务" tone="success" />
            <code>{{ exportResult.task_type || exportResult.taskType }}</code>
            <span v-if="devNotes" class="nx-muted">physical_dump={{ exportResult.physical_dump }}</span>
          </span>
          <button class="nx-btn nx-btn--default" type="button" :disabled="!ledgerId || exporting" data-testid="g2-report-export" @click="onExport">
            {{ exporting ? "导出中…" : "创建导出任务" }}
          </button>
        </div>
      </header>
      <div v-if="busy" class="results-skeleton" data-testid="report-loading">
        <NxSkeleton v-for="n in 3" :key="n" variant="list-row" label="正在查询报表" />
      </div>
      <NxEmpty
        v-else-if="!ran"
        compact
        title="选择台账后查询"
        :description="ledgers.length ? '查询结果按当前账号的数据权限和字段投影返回。' : '当前租户还没有台账，请先创建台账。'"
        data-testid="report-idle"
      >
        <template v-if="!ledgers.length" #action><router-link class="ghost" to="/p3/ledgers">去创建台账</router-link></template>
      </NxEmpty>
      <NxEmpty v-else-if="queryFailed" compact title="查询没有完成" description="请按上方的错误信息处理后重新查询；这不代表台账没有记录。" data-testid="report-failed" />
      <NxEmpty v-else-if="!rows.length" compact title="没有匹配记录" description="该台账在当前权限范围内没有可见记录。" data-testid="report-empty" />
      <div v-else class="table-scroll">
        <table class="nx-table" data-testid="g2-report-table">
          <thead>
            <tr>
              <th v-for="col in columns" :key="col">{{ col }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id || JSON.stringify(row)">
              <td v-for="col in columns" :key="col">{{ formatCell(row, col) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from "vue";
import { NxEmpty, NxFilterBar, NxPageHeader, NxSkeleton } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import { devNotesEnabled } from "../../utils/devNotes";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { perm } from "../../api/client";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";
import { REPORT_SURFACES } from "../../permission/reportCatalog";
import { decide, formatDeny } from "../../permission/reportGuard";

const ledgers = ref([]);
const ledgerId = ref("");
const rows = ref([]);
const total = ref(0);
const ran = ref(false);
const busy = ref(false);
const exporting = ref(false);
const error = ref("");
const queryDecision = ref(null);
const exportDecision = ref(null);
const exportResult = ref(null);
// A failed query must not read as "没有匹配记录 / 共 0 条".
const queryFailed = ref(false);
const devNotes = devNotesEnabled();

function decisionLabel(decision) {
  if (!decision) return "检查中…";
  return decision.allowed ? "允许" : `未获授权${decision.reason ? `（${decision.reason}）` : ""}`;
}
function decisionTone(decision) {
  if (!decision) return "neutral";
  return decision.allowed ? "success" : "danger";
}

// §11: reset returns to the default (first ledger) and clears the previous result.
function onReset() {
  ledgerId.value = ledgers.value[0]?.id || "";
  rows.value = [];
  total.value = 0;
  ran.value = false;
  queryFailed.value = false;
  exportResult.value = null;
  error.value = "";
}

const SYSTEM_SKIP = new Set(["tenant_id", "deleted_at", "ext_data", "schema_version"]);

const columns = computed(() => {
  const keys = new Set();
  for (const row of rows.value) {
    Object.keys(row || {}).forEach((key) => {
      if (!SYSTEM_SKIP.has(key) && key !== "_source") {
        keys.add(key);
      }
    });
  }
  return keys.size ? [...keys] : ["id"];
});

function formatCell(row, col) {
  const value = row?.[col];
  if (value == null) {
    return "—";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return value;
}

async function loadDecisions() {
  const body = await p3.reportDecision();
  queryDecision.value = body.query;
  exportDecision.value = body.export;
}

onMounted(async () => {
  try {
    await decide(perm.check, REPORT_SURFACES.query.resource, REPORT_SURFACES.query.action);
    await loadDecisions();
    const body = await p3.listLedgers();
    ledgers.value = body.items || [];
    if (!ledgerId.value && ledgers.value.length) {
      ledgerId.value = ledgers.value[0].id;
    }
  } catch (err) {
    error.value = formatDeny(err);
    try {
      await loadDecisions();
    } catch {
      /* decision endpoint may also deny isolation-only hosts */
    }
  }
});

async function onQuery() {
  busy.value = true;
  ran.value = true;
  queryFailed.value = false;
  error.value = "";
  try {
    await decide(perm.check, REPORT_SURFACES.query.resource, REPORT_SURFACES.query.action);
    const body = await p3.queryReport({ ledger_id: ledgerId.value, filters: {}, page: 0, size: 20 });
    rows.value = body.items || [];
    total.value = body.total ?? rows.value.length;
    queryDecision.value = body.decision || queryDecision.value;
  } catch (err) {
    error.value = formatDeny(err);
    queryFailed.value = true;
    rows.value = [];
    total.value = 0;
  } finally {
    busy.value = false;
  }
}

async function onExport() {
  exporting.value = true;
  error.value = "";
  exportResult.value = null;
  try {
    await decide(perm.check, REPORT_SURFACES.export.resource, REPORT_SURFACES.export.action);
    exportResult.value = await p3.exportReport({
      ledger_id: ledgerId.value,
      payload: JSON.stringify({ ledger: ledgerId.value, surface: "report" }),
    });
    exportDecision.value = exportResult.value.decision || exportDecision.value;
  } catch (err) {
    error.value = formatDeny(err);
  } finally {
    exporting.value = false;
  }
}
</script>

<style scoped>
.report-page { display: grid; gap: var(--nx-space-4); }
.permissions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--nx-space-5); font-size: var(--nx-font-size-sm); }
.perm { display: inline-flex; align-items: center; gap: var(--nx-space-2); }
.perm-label { color: var(--nx-text-secondary); }
.dev-note { flex-basis: 100%; margin: 0; }
.report-filter :deep(select) { min-width: 240px; }
.results { display: grid; gap: var(--nx-space-3); }
.results-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--nx-space-3); }
.results-head h2 { margin: 0; font-size: var(--nx-font-size-md); }
.results-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--nx-space-3); }
.export-result { display: inline-flex; align-items: center; gap: var(--nx-space-2); font-size: var(--nx-font-size-sm); }
.results-skeleton { display: grid; gap: var(--nx-space-2); }
.table-scroll { overflow-x: auto; }
</style>