<template>
  <div>
    <div class="nx-page-header">
      <h1>导出向导</h1>
      <p>
        G4 leftover：<code>POST /api/v1/p3/exports/wizard/begin</code> 与
        <code>/complete</code> 把 <code>p3.export.begun</code> /
        <code>p3.export.completed</code>（或 deny）写入同一哈希链。
        不是产品导出 UI，不宣称产品 G4 关闭。
      </p>
    </div>
    <NxSourceBanner body="复用 ReportSurfaceGuard + AuthorizedExport。桌面台账 / 表单 / 待办 / 报表 / dump 页未改。AuditPort 同事务入链未改。" />
    <div class="nx-card" style="margin-bottom:16px;" data-testid="g4-export-wizard-decision">
      <h3>Decision</h3>
      <p class="nx-muted">GET /api/v1/p3/exports/wizard/decision — decide only，不写链。</p>
      <p v-if="exportDecision" class="nx-code" data-testid="g4-export-wizard-export-cell">
        export {{ JSON.stringify(exportDecision) }}
      </p>
    </div>
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。导出向导需要服务端签发的上下文。</p>
    <p v-if="error" class="nx-error" data-testid="g4-export-wizard-error">{{ error }}</p>
    <div class="nx-card" style="margin-bottom:16px;max-width:720px;">
      <div class="nx-row">
        <button class="primary" type="button" :disabled="Boolean(busy)" data-testid="g4-export-wizard-begin" @click="onBegin">
          {{ busy === "begin" ? "开始中…" : "开始导出" }}
        </button>
        <button class="ghost" type="button" :disabled="!wizardId || Boolean(busy)" data-testid="g4-export-wizard-complete" @click="onComplete">
          {{ busy === "complete" ? "完成中…" : "完成导出" }}
        </button>
      </div>
      <p v-if="wizardId" class="nx-muted" data-testid="g4-export-wizard-id">wizard_id={{ wizardId }}</p>
      <p v-if="beginResult" class="nx-muted" data-testid="g4-export-wizard-begin-result">
        begin {{ beginResult.step }} · completed={{ beginResult.completed }}
      </p>
      <p v-if="completeResult" class="nx-muted" data-testid="g4-export-wizard-complete-result">
        complete {{ completeResult.step }} · task_type={{ completeResult.task_type || completeResult.taskType }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { perm } from "../../api/client";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";
import { REPORT_SURFACES } from "../../permission/reportCatalog";
import { decide, formatDeny } from "../../permission/reportGuard";

const exportDecision = ref(null);
const wizardId = ref("");
const beginResult = ref(null);
const completeResult = ref(null);
const busy = ref("");
const error = ref("");

async function loadDecision() {
  const body = await p3.exportWizardDecision();
  exportDecision.value = body.export;
}

onMounted(async () => {
  try {
    await decide(perm.check, REPORT_SURFACES.export.resource, REPORT_SURFACES.export.action);
    await loadDecision();
  } catch (err) {
    error.value = formatDeny(err);
    try {
      await loadDecision();
    } catch {
      /* isolation-only hosts may also deny decision */
    }
  }
});

async function onBegin() {
  busy.value = "begin";
  error.value = "";
  beginResult.value = null;
  completeResult.value = null;
  try {
    await decide(perm.check, REPORT_SURFACES.export.resource, REPORT_SURFACES.export.action);
    beginResult.value = await p3.beginExportWizard({
      payload: JSON.stringify({ surface: "export.wizard" }),
    });
    wizardId.value = beginResult.value.wizard_id || beginResult.value.wizardId || "";
    exportDecision.value = beginResult.value.decision || exportDecision.value;
  } catch (err) {
    error.value = formatDeny(err);
  } finally {
    busy.value = "";
  }
}

async function onComplete() {
  busy.value = "complete";
  error.value = "";
  completeResult.value = null;
  try {
    await decide(perm.check, REPORT_SURFACES.export.resource, REPORT_SURFACES.export.action);
    completeResult.value = await p3.completeExportWizard({ wizard_id: wizardId.value });
    exportDecision.value = completeResult.value.decision || exportDecision.value;
  } catch (err) {
    error.value = formatDeny(err);
  } finally {
    busy.value = "";
  }
}
</script>
