<template>
  <section class="runs" data-testid="runs-panel">
    <div class="runs-head">
      <h3>{{ actionId ? "执行记录" : "最近执行记录" }}</h3>
      <button class="ghost" type="button" :disabled="loading" data-testid="runs-refresh" @click="load">{{ loading ? "加载中…" : "刷新" }}</button>
    </div>
    <p v-if="error" class="field-error" data-testid="runs-error">{{ error }}</p>
    <p v-else-if="!loading && !runs.length" class="nx-muted" data-testid="runs-empty">还没有执行记录。审批办结且触发条件满足后，这里会出现记录。</p>
    <table v-else-if="runs.length" class="nx-table" data-testid="runs-table">
      <thead>
        <tr><th>源单</th><th>审批结果</th><th>状态</th><th>生成的行</th><th>尝试</th><th>错误类别</th><th>更新时间</th><th /></tr>
      </thead>
      <tbody>
        <tr v-for="run in runs" :key="run.id" :data-testid="`run-row-${run.id}`">
          <td><span class="nx-code">{{ run.sourceLedgerCode }}</span> · <span class="nx-code">{{ short(run.sourceRecordId) }}</span></td>
          <td>{{ run.outcome === "APPROVED" ? "通过" : run.outcome === "REJECTED" ? "驳回" : run.outcome }}</td>
          <td><NxStatusTag :status="(RUN_STATUS[run.status] || RUN_STATUS.PENDING).tone" :label="(RUN_STATUS[run.status] || RUN_STATUS.PENDING).label" /></td>
          <td><span v-if="run.targetRecordId" class="nx-code">{{ short(run.targetRecordId) }}</span><span v-else class="nx-muted">—</span></td>
          <td>{{ run.attempts }}</td>
          <td><span v-if="run.errorCode" class="nx-code">{{ run.errorCode }}</span><span v-else class="nx-muted">—</span></td>
          <td>{{ run.updatedAt ? formatTime(run.updatedAt) : "—" }}</td>
          <td>
            <button
              v-if="run.status === 'FAILED'"
              class="text-action"
              type="button"
              :disabled="retrying === run.id"
              :data-testid="`run-retry-${run.id}`"
              @click="retry(run)"
            >{{ retrying === run.id ? "已提交…" : "重试" }}</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="notice" class="nx-muted" data-testid="runs-notice">{{ notice }}</p>
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from "vue";
import { NxStatusTag } from "../../components";
import { RUN_STATUS, completionActions } from "../../api/completionActions";
import { explainPlatformRead } from "../../api/platformReadError";

const props = defineProps({ actionId: { type: String, default: "" } });

const runs = ref([]);
const loading = ref(false);
const error = ref("");
const notice = ref("");
const retrying = ref("");

function short(id) {
  return id ? String(id).slice(0, 8) : "";
}

function formatTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString("zh-CN", { hour12: false });
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    runs.value = await completionActions.runs(props.actionId || null);
  } catch (err) {
    error.value = explainPlatformRead(err, "执行记录暂时无法加载");
  } finally {
    loading.value = false;
  }
}

async function retry(run) {
  retrying.value = run.id;
  notice.value = "";
  error.value = "";
  try {
    await completionActions.retry(run.id);
    notice.value = "已重新提交，后台执行后刷新即可看到结果。";
  } catch (err) {
    error.value = err?.status === 409 ? "这条记录已成功，或动作已停用（请先启用再重试）。" : explainPlatformRead(err, "重试失败");
  } finally {
    retrying.value = "";
  }
}

watch(() => props.actionId, load);
onMounted(load);
defineExpose({ load });
</script>

<style scoped>
.runs { display: grid; gap: var(--nx-space-2); }
.runs-head { display: flex; align-items: center; justify-content: space-between; }
.runs-head h3 { margin: 0; }
</style>
