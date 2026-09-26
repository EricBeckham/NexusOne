<template>
  <div class="export-wizard">
    <NxPageHeader title="导出向导" description="引导创建并完成受权限保护的导出任务，操作结果写入同一审计链。" />
    <NxSourceBanner body="复用 ReportSurfaceGuard + AuthorizedExport。桌面台账 / 表单 / 待办 / 报表 / dump 页未改。AuditPort 同事务入链未改。" />

    <NxErrorState
      v-if="!session.contextId"
      title="还没有进入租户"
      description="导出需要先登录并切换到对应租户。"
      retry-label=""
      data-testid="export-wizard-no-tenant"
    />
    <NxErrorState
      v-else-if="loadFailed"
      :title="loadErrorTitle"
      :description="loadErrorDescription"
      retry-label="重试"
      data-testid="export-wizard-load-error"
      @retry="reload"
    />

    <section v-else class="nx-card wizard" aria-label="导出步骤">
      <!-- R14: step bar derived from the existing decision / begin / complete state. -->
      <ol class="steps" data-testid="export-steps">
        <li v-for="(step, index) in steps" :key="step.id" class="step" :data-state="step.state" :aria-current="step.state === 'current' ? 'step' : undefined">
          <span class="step-dot" aria-hidden="true">
            <NxIcon v-if="step.state === 'done'" name="check" :size="14" />
            <template v-else>{{ index + 1 }}</template>
          </span>
          <span class="step-text"><strong>{{ step.label }}</strong><small>{{ step.hint }}</small></span>
        </li>
      </ol>

      <div class="wizard-body">
        <div class="decision" data-testid="g4-export-wizard-decision">
          <span class="body-label">导出权限</span>
          <NxStatus v-if="exportDecision" :label="decisionLabel" :tone="exportDecision.allowed ? 'success' : 'danger'" data-testid="export-decision-status" />
          <span v-else class="nx-muted">检查中…</span>
          <p v-if="devNotes && exportDecision" class="nx-code dev-note" data-testid="g4-export-wizard-export-cell">export {{ JSON.stringify(exportDecision) }}</p>
        </div>
        <NxErrorState
          v-if="error"
          :title="error"
          description=""
          retry-label=""
          data-testid="g4-export-wizard-error"
        />
        <dl v-if="wizardId || completeResult" class="results">
          <template v-if="wizardId">
            <dt>导出任务</dt><dd class="nx-code" data-testid="g4-export-wizard-id">{{ wizardId }}</dd>
          </template>
          <template v-if="beginResult">
            <dt>已开始</dt><dd data-testid="g4-export-wizard-begin-result">{{ beginResult.completed ? "已完成" : "等待完成" }}</dd>
          </template>
          <template v-if="completeResult">
            <dt>结果</dt>
            <dd data-testid="g4-export-wizard-complete-result">
              <NxStatus label="已完成" tone="success" />
              <span v-if="completeResult.task_type || completeResult.taskType" class="task-type">任务类型 <code>{{ completeResult.task_type || completeResult.taskType }}</code></span>
            </dd>
          </template>
        </dl>
      </div>

      <!-- One Primary: whichever step comes next. -->
      <div class="wizard-actions">
        <button :class="nextStep === 'begin' ? 'nx-btn nx-btn--primary' : 'nx-btn nx-btn--default'" type="button" :disabled="Boolean(busy)" data-testid="g4-export-wizard-begin" @click="onBegin">
          {{ busy === "begin" ? "开始中…" : wizardId ? "重新开始" : "开始导出" }}
        </button>
        <button :class="nextStep === 'complete' ? 'nx-btn nx-btn--primary' : 'nx-btn nx-btn--default'" type="button" :disabled="!wizardId || Boolean(busy)" data-testid="g4-export-wizard-complete" @click="onComplete">
          {{ busy === "complete" ? "完成中…" : "完成导出" }}
        </button>
      </div>
    </section>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from "vue";
import { NxErrorState, NxPageHeader } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import NxIcon from "../../icons/NxIcon.vue";
import { devNotesEnabled } from "../../utils/devNotes";
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
const loadFailed = ref(false);
const loadErrorTitle = ref("无法检查导出权限");
const loadErrorDescription = ref("请稍后重试。");
const devNotes = devNotesEnabled();

const decisionLabel = computed(() => {
  const decision = exportDecision.value;
  if (!decision) return "";
  return decision.allowed ? "允许导出" : `未获授权${decision.reason ? `（${decision.reason}）` : ""}`;
});
// The Primary follows the flow: start first, then complete once a wizard exists; nothing after completion.
const nextStep = computed(() => {
  if (completeResult.value) return "";
  return wizardId.value ? "complete" : "begin";
});
const steps = computed(() => {
  const allowed = exportDecision.value?.allowed === true;
  const begun = Boolean(wizardId.value);
  const done = Boolean(completeResult.value);
  const state = (isDone, isCurrent) => (isDone ? "done" : isCurrent ? "current" : "todo");
  // A denied (or pending) check stays the current step; 开始导出 only becomes current once export is allowed.
  return [
    { id: "check", label: "权限检查", hint: "按报表导出权限判定", state: state(allowed || begun, !allowed && !begun) },
    { id: "begin", label: "开始导出", hint: "创建导出任务", state: state(begun, allowed && !begun) },
    { id: "complete", label: "完成导出", hint: "生成导出并写入审计", state: state(done, begun && !done) },
  ];
});

async function loadDecision() {
  const body = await p3.exportWizardDecision();
  exportDecision.value = body.export;
}

async function reload() {
  loadFailed.value = false;
  error.value = "";
  try {
    await decide(perm.check, REPORT_SURFACES.export.resource, REPORT_SURFACES.export.action);
    await loadDecision();
  } catch (err) {
    try {
      await loadDecision();
    } catch (decisionErr) {
      loadFailed.value = true;
      loadErrorTitle.value = formatDeny(decisionErr) || formatDeny(err) || "无法检查导出权限";
      loadErrorDescription.value = "权限检查没有完成。可重试，或确认当前账号具备导出权限。";
    }
  }
}

onMounted(reload);

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

<style scoped>
.wizard { display: grid; gap: var(--nx-space-5); max-width: 760px; padding-bottom: 0; }
.steps { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--nx-space-3); margin: 0; padding: 0; list-style: none; }
.step { display: flex; align-items: flex-start; gap: var(--nx-space-2); padding-top: var(--nx-space-3); border-top: 3px solid var(--nx-divider, var(--nx-border)); }
.step[data-state="done"] { border-top-color: var(--nx-success); }
.step[data-state="current"] { border-top-color: var(--nx-product, var(--nx-primary)); }
.step-dot { display: grid; flex: none; place-items: center; width: 24px; height: 24px; border-radius: 50%; background: var(--nx-bg-page); color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); font-weight: 600; }
.step[data-state="done"] .step-dot { background: var(--nx-success-light); color: var(--nx-success); }
.step[data-state="current"] .step-dot { background: var(--nx-product, var(--nx-primary)); color: var(--nx-surface); }
.step-text { display: grid; gap: 2px; }
.step-text strong { color: var(--nx-text-primary); font-size: var(--nx-font-size-sm); font-weight: 600; }
.step[data-state="todo"] .step-text strong { color: var(--nx-text-secondary); font-weight: 500; }
.step-text small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.wizard-body { display: grid; gap: var(--nx-form-field-gap, 20px); }
.decision { display: flex; flex-wrap: wrap; align-items: center; gap: var(--nx-space-3); }
.body-label, .results dt { color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.dev-note { flex-basis: 100%; margin: 0; }
.results { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: var(--nx-space-3) var(--nx-space-4); margin: 0; }
.results dd { display: flex; flex-wrap: wrap; align-items: center; gap: var(--nx-space-3); margin: 0; overflow-wrap: anywhere; }
.task-type { color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.wizard-actions { display: flex; justify-content: flex-end; gap: var(--nx-space-2); margin: 0 calc(-1 * var(--nx-space-5, 20px)); padding: var(--nx-space-3) var(--nx-space-5, 20px); border-top: 1px solid var(--nx-divider, var(--nx-border)); }
.export-wizard :deep([data-testid='g4-export-wizard-error']) { padding: var(--nx-space-4); align-items: flex-start; text-align: left; }
@media (max-width: 720px) { .steps { grid-template-columns: 1fr; } }
</style>
