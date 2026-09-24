<template>
  <div>
    <NxPageHeader title="物理 dump / 原始导出" description="原始导出经过数据权限判定与字段投影，不等同于报表任务。" />
    <NxSourceBanner body="POST /api/v1/p3/dumps/export 先经 DumpSurfaceGuard。桌面台账 / 表单 / 待办 / 报表页未改。pg_dump 运维仍开放。" />
    <div class="nx-card" style="margin-bottom:16px;" data-testid="g2-dump-decision">
      <h3>Decision</h3>
      <p class="nx-muted">GET /api/v1/p3/dumps/decision — 与 POST /permission/check 同一引擎。</p>
      <p v-if="readDecision" class="nx-code" data-testid="g2-dump-read-cell">
        read {{ JSON.stringify(readDecision) }}
      </p>
      <p v-if="dumpDecision" class="nx-code" data-testid="g2-dump-export-cell">
        dump {{ JSON.stringify(dumpDecision) }}
      </p>
    </div>
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。物理 dump 需要服务端签发的上下文。</p>
    <p v-if="error" class="nx-error" data-testid="g2-dump-error">{{ error }}</p>
    <form class="nx-card" style="margin-bottom:16px;max-width:720px;" @submit.prevent="onDump">
      <div class="nx-field">
        <label>台账</label>
        <select v-model="ledgerId" required data-testid="g2-dump-ledger">
          <option value="" disabled>选择台账</option>
          <option v-for="item in ledgers" :key="item.id" :value="item.id">
            {{ item.name }} ({{ item.code }})
          </option>
        </select>
      </div>
      <div class="nx-row">
        <button class="primary" type="submit" :disabled="!ledgerId || busy" data-testid="g2-dump-export">
          {{ busy ? "导出中…" : "原始 CSV dump" }}
        </button>
      </div>
    </form>
    <p v-if="dumpResult" class="nx-muted" data-testid="g2-dump-result">
      {{ dumpResult.task_type || dumpResult.taskType }} · physical_dump={{ dumpResult.physical_dump }}
      · raw_export={{ dumpResult.raw_export }} · rows={{ dumpResult.row_count }}
    </p>
    <pre v-if="dumpResult?.csv" class="nx-code" data-testid="g2-dump-csv">{{ dumpResult.csv }}</pre>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { NxPageHeader } from "../../components";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { perm } from "../../api/client";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";
import { DUMP_SURFACES } from "../../permission/dumpCatalog";
import { decide, formatDeny } from "../../permission/dumpGuard";

const ledgers = ref([]);
const ledgerId = ref("");
const busy = ref(false);
const error = ref("");
const readDecision = ref(null);
const dumpDecision = ref(null);
const dumpResult = ref(null);

async function loadDecisions() {
  const body = await p3.dumpDecision();
  readDecision.value = body.read;
  dumpDecision.value = body.dump;
}

onMounted(async () => {
  try {
    await decide(perm.check, DUMP_SURFACES.dump.resource, DUMP_SURFACES.dump.action);
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

async function onDump() {
  busy.value = true;
  error.value = "";
  dumpResult.value = null;
  try {
    await decide(perm.check, DUMP_SURFACES.dump.resource, DUMP_SURFACES.dump.action);
    dumpResult.value = await p3.exportDump({
      ledger_id: ledgerId.value,
      filters: {},
      page: 0,
      size: 20,
    });
    dumpDecision.value = dumpResult.value.decision || dumpDecision.value;
    readDecision.value = dumpResult.value.read_decision || readDecision.value;
  } catch (err) {
    error.value = formatDeny(err);
  } finally {
    busy.value = false;
  }
}
</script>
