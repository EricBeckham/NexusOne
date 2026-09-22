<template>
  <div>
    <div class="nx-page-header">
      <h1>报表表面</h1>
      <p>
        查询走 <code>ledger/read</code>，导出走 <code>ledger.export.csv/submit</code>，
        与 list / AuthorizedExport 同一 PermissionEngine。不是物理 dump，不宣称产品 G2 关闭。
      </p>
    </div>
    <NxSourceBanner body="POST /api/v1/p3/reports/query 与 /export 先经 ReportSurfaceGuard。桌面台账 / 表单 / 待办页未改。物理 dump 仍开放。" />
    <div class="nx-card" style="margin-bottom:16px;" data-testid="g2-report-decision">
      <h3>Decision</h3>
      <p class="nx-muted">GET /api/v1/p3/reports/decision — 与 POST /permission/check 同一引擎。</p>
      <p v-if="queryDecision" class="nx-code" data-testid="g2-report-query-cell">
        query {{ JSON.stringify(queryDecision) }}
      </p>
      <p v-if="exportDecision" class="nx-code" data-testid="g2-report-export-cell">
        export {{ JSON.stringify(exportDecision) }}
      </p>
    </div>
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。报表查询 / 导出需要服务端签发的上下文。</p>
    <p v-if="error" class="nx-error" data-testid="g2-report-error">{{ error }}</p>
    <form class="nx-card" style="margin-bottom:16px;max-width:720px;" @submit.prevent="onQuery">
      <div class="nx-field">
        <label>台账</label>
        <select v-model="ledgerId" required data-testid="g2-report-ledger">
          <option value="" disabled>选择台账</option>
          <option v-for="item in ledgers" :key="item.id" :value="item.id">
            {{ item.name }} ({{ item.code }})
          </option>
        </select>
      </div>
      <div class="nx-row">
        <button class="primary" type="submit" :disabled="!ledgerId || busy" data-testid="g2-report-query">
          {{ busy ? "查询中…" : "查询" }}
        </button>
        <button class="ghost" type="button" :disabled="!ledgerId || exporting" data-testid="g2-report-export" @click="onExport">
          {{ exporting ? "导出中…" : "导出 Task" }}
        </button>
      </div>
    </form>
    <p v-if="exportResult" class="nx-muted" data-testid="g2-report-export-result">
      {{ exportResult.task_type || exportResult.taskType }} · physical_dump={{ exportResult.physical_dump }}
    </p>
    <p v-if="ran" class="nx-muted" data-testid="g2-report-total">共 {{ total }} 条</p>
    <table class="nx-table" data-testid="g2-report-table">
      <thead>
        <tr>
          <th v-for="col in columns" :key="col">{{ col }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="ran && !rows.length">
          <td :colspan="columns.length" class="nx-empty">没有匹配记录</td>
        </tr>
        <tr v-for="row in rows" :key="row.id || JSON.stringify(row)">
          <td v-for="col in columns" :key="col">{{ formatCell(row, col) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
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
  error.value = "";
  try {
    await decide(perm.check, REPORT_SURFACES.query.resource, REPORT_SURFACES.query.action);
    const body = await p3.queryReport({ ledger_id: ledgerId.value, filters: {}, page: 0, size: 20 });
    rows.value = body.items || [];
    total.value = body.total ?? rows.value.length;
    queryDecision.value = body.decision || queryDecision.value;
  } catch (err) {
    error.value = formatDeny(err);
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
