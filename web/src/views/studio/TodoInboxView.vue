<template>
  <div>
    <NxPageHeader title="待办" description="集中查看我的待办、已办、我发起的流程和我的申请，并完成同意、驳回或撤回。" />
    <p v-if="!session.contextId" class="nx-error">还没有进入租户。请先登录并切换到要办理的租户。</p>
    <div class="nx-tabs">
      <button
        v-for="item in boxes"
        :key="item.id"
        :class="{ active: box === item.id }"
        type="button"
        @click="switchBox(item.id)"
      >{{ item.label }}</button>
    </div>
    <NxFilterBar label="待办筛选" testid="todo-filter" class="todo-filter" @reset="query = ''">
      <label class="nx-filter-field">关键词<input id="todo-filter" v-model="query" type="search" placeholder="单号 / 任务名" data-testid="todo-filter-input" /></label>
      <template #summary>{{ loading ? "正在加载…" : `共 ${filtered.length} 条` }}</template>
    </NxFilterBar>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <p v-if="notice" class="nx-muted">{{ notice }}</p>
    <div class="nx-grid inbox-layout">
      <div v-if="loading" class="todo-skeleton" data-testid="todo-loading" role="status">
        <NxSkeleton v-for="n in 4" :key="n" variant="list-row" label="正在加载待办" />
      </div>
      <NxEmpty v-else-if="!filtered.length" :title="emptyCopy.title" :description="emptyCopy.description" data-testid="todo-empty">
        <template v-if="emptyCopy.to || emptyCopy.box || emptyCopy.clear" #action>
          <router-link v-if="emptyCopy.to" class="primary" :to="emptyCopy.to" data-testid="todo-empty-action">{{ emptyCopy.action }}</router-link>
          <button v-else-if="emptyCopy.box" class="primary" type="button" data-testid="todo-empty-action" @click="switchBox(emptyCopy.box)">{{ emptyCopy.action }}</button>
          <button v-else class="ghost" type="button" data-testid="todo-empty-action" @click="query = ''">{{ emptyCopy.action }}</button>
        </template>
      </NxEmpty>
      <table v-else class="nx-table" data-testid="todo-table">
        <thead>
          <tr>
            <th>{{ columns.name }}</th>
            <th>{{ columns.no }}</th>
            <th>{{ columns.time }}</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in filtered"
            :key="row.id"
            :class="{ pick: selected?.id === row.id }"
            :aria-selected="selected?.id === row.id"
            tabindex="0"
            data-testid="todo-row"
            @click="open(row.id)"
            @keydown.enter.prevent="open(row.id)"
          >
            <td class="cell-title">{{ row.title || "未命名" }}</td>
            <td class="nx-code cell-code" :title="row.biz_no || undefined" data-testid="todo-row-no">{{ shortRef(row.biz_no) }}</td>
            <td class="cell-time">{{ formatTime(row.arrived_at) }}</td>
            <td><NxStatus :label="rowStatus(row).label" :tone="rowStatus(row).tone" /></td>

          </tr>
        </tbody>
      </table>
      <div class="nx-card detail">
        <h3>{{ box === 'inbox' ? "办理" : "详情" }}</h3>
        <template v-if="selected">
          <p class="detail-title"><strong>{{ selected.title }}</strong></p>
          <dl v-if="box === 'requests'" class="detail-meta" data-testid="request-detail-meta">
            <dt>申请编号</dt><dd class="nx-code">{{ selected.biz_no || "暂无" }}</dd>
            <dt>提交时间</dt><dd>{{ formatTime(selected.arrived_at) }}</dd>
            <dt>状态</dt><dd><NxStatus :label="rowStatus(selected).label" :tone="rowStatus(selected).tone" /></dd>
          </dl>
          <dl v-else class="detail-meta">
            <dt>流程</dt><dd>{{ selected.process || "暂无" }}</dd>
            <dt>业务单号</dt><dd class="nx-code">{{ selected.biz_no || "暂无" }}</dd>
            <dt v-if="selected.approval_status">审批状态</dt>
            <dd v-if="selected.approval_status"><NxStatus :label="statusLabel(selected.approval_status)" :tone="tone(selected.approval_status)" /></dd>
            <dt v-if="selected.instance_id">流程实例</dt><dd v-if="selected.instance_id" class="nx-code instance">{{ selected.instance_id }}</dd>
          </dl>
          <router-link
            v-if="selected.ledger_id && selected.record_id"
            :to="`/p3/ledgers/${selected.ledger_id}/records/${selected.record_id}`"
            data-testid="todo-record-link"
          >查看业务记录</router-link>
          <div v-if="box === 'requests'" class="comment-box" data-testid="application-comment">
            <span class="comment-label">审批意见</span>
            <p :class="{ 'nx-muted': !selected.comment }">{{ selected.comment || "暂无意见" }}</p>
          </div>
          <div v-if="box === 'inbox'" class="nx-field"><label>意见</label><textarea v-model="comment" rows="3" data-testid="todo-comment" /></div>
          <div v-if="box === 'inbox' || (box === 'initiated' && isPending(selected))" class="nx-row detail-actions">
            <button v-if="box === 'inbox'" class="nx-btn nx-btn--primary" type="button" data-testid="todo-approve" :disabled="busy" @click="act('approve')">同意</button>
            <button v-if="box === 'inbox'" class="nx-btn nx-btn--danger" type="button" data-testid="todo-reject" :disabled="busy" @click="act('reject')">驳回</button>
            <button v-if="box === 'initiated' && isPending(selected)" class="nx-btn nx-btn--default" type="button" data-testid="todo-withdraw" :disabled="busy" @click="act('withdraw')">撤回</button>
          </div>
          <p v-if="devNotes && session.lastTraceId" class="nx-muted" data-testid="todo-last-trace" style="margin-top:8px;">
            最近 trace
            <router-link :to="`/p3/audit?trace=${encodeURIComponent(session.lastTraceId)}`">{{ session.lastTraceId }}</router-link>
          </p>
        </template>
        <p v-else class="nx-muted">从左侧列表选择一条记录查看详情。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxEmpty, NxFilterBar, NxPageHeader, NxSkeleton } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";
import { todoStatus } from "./todoStatus";
import { devNotesEnabled } from "../../utils/devNotes";

const devNotes = devNotesEnabled();

const route = useRoute();
const router = useRouter();
const boxes = [
  { id: "inbox", label: "我的待办" },
  { id: "done", label: "已办" },
  { id: "initiated", label: "我发起" },
  { id: "requests", label: "我的申请" },
];
const box = ref(route.query.box || "inbox");
const rows = ref([]);
const selected = ref(null);
const comment = ref("");
// §11: the keyword lives in the URL (?q=) next to ?box= so a reload keeps the filter.
const query = ref(typeof route.query.q === "string" ? route.query.q : "");
const error = ref("");
const notice = ref("");
const busy = ref(false);
const loading = ref(true);

const filtered = computed(() => {
  const q = query.value.trim();
  if (!q) return rows.value;
  return rows.value.filter((row) =>
    [row.title, row.biz_no, row.process].some((part) => String(part || "").includes(q)),
  );
});

const emptyCopy = computed(() => {
  // Every empty state gives a reason and a next step (M4).
  if (query.value.trim() && rows.value.length) {
    return { title: "没有匹配项", description: "没有单号或任务名包含这个筛选词。", clear: true, action: "清空筛选" };
  }
  if (box.value === "requests") {
    return {
      title: "暂无申请",
      description: "从已开通产品发起后，进行中、通过和驳回会显示在这里。",
      to: "/p4/installations",
      action: "前往我的已开通",
    };
  }
  if (box.value === "done") {
    return { title: "暂无已办", description: "在「我的待办」同意或驳回后，办结项会出现在这里。", box: "inbox", action: "查看我的待办" };
  }
  if (box.value === "initiated") {
    return { title: "暂无我发起", description: "从「我的已开通」发起申请后，进行中的流程会出现在这里。", to: "/p4/installations", action: "去发起申请" };
  }
  return {
    title: "暂无待办",
    description: "有人发起申请并选你为审批人后，待办会出现在这里；你也可以从「我的已开通」发起申请。",
    to: "/p4/installations",
    action: "去发起申请",
  };
});

function tone(status) {
  return todoStatus(status).tone;
}

function statusLabel(status) {
  return todoStatus(status).label;
}

function rowStatus(row) {
  // 我的申请 without a status is a just-created application, so it reads as 进行中.
  return todoStatus(row.status, box.value === "requests" ? "进行中" : "暂无");
}


// Q11: headers per box; 我发起 rows carry the instance start (created_at / started_at), so it reads 发起时间.
const COLUMNS = {
  inbox: { name: "任务", no: "业务单号", time: "到达时间" },
  done: { name: "任务", no: "业务单号", time: "到达时间" },
  initiated: { name: "流程", no: "业务单号", time: "发起时间" },
  requests: { name: "申请", no: "申请编号", time: "提交时间" },
};
const columns = computed(() => COLUMNS[box.value] || COLUMNS.inbox);

// Long references (product-application:<uuid>) are shortened in the table; the title and the detail keep the full value.
function shortRef(value) {
  const text = String(value || "");
  if (!text) return "暂无";
  if (text.length <= 20) return text;
  const tail = text.includes(":") ? text.slice(text.lastIndexOf(":") + 1) : text;
  return `${tail.slice(0, 8)}…`;
}

function formatTime(value) {
  if (!value) return "暂无";
  const text = String(value);
  if (!text.includes("T")) return text;
  const date = new Date(text);
  return Number.isNaN(date.getTime()) ? text : date.toLocaleString("zh-CN", { hour12: false });
}

function isPending(row) {
  const value = String(row?.status || "").toUpperCase();
  return value === "PENDING" || value === "RUNNING" || value === "CREATED";
}

async function reload() {
  loading.value = true;
  try {
    const body = box.value === "requests" ? await p3.listProductApplications() : await p3.listTodos(box.value);
    rows.value = body.items || [];
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  try {
    await reload();
    if (route.params.id) {
      await open(route.params.id);
    }
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

function switchBox(next) {
  box.value = next;
  selected.value = null;
  const q = query.value.trim();
  router.replace({ path: "/p3/todos", query: q ? { box: next, q } : { box: next } });
  reload().catch((err) => {
    error.value = err.message;
  });
}

watch(query, (value) => {
  const q = value.trim();
  if ((typeof route.query.q === "string" ? route.query.q : "") === q) return;
  const next = { ...route.query };
  if (q) next.q = q;
  else delete next.q;
  router.replace({ query: next });
});

async function open(id) {
  try {
    selected.value = await p3.getTodo(id);
  } catch {
    selected.value = rows.value.find((row) => row.id === id) || null;
  }
  comment.value = "";
}

async function act(kind) {
  if (busy.value || !selected.value) return;
  busy.value = true;
  try {
    if (kind === "approve") selected.value = await p3.approveTodo(selected.value.id, comment.value, selected.value.biz_no);
    if (kind === "reject") selected.value = await p3.rejectTodo(selected.value.id, comment.value, selected.value.biz_no);
    if (kind === "withdraw") {
      selected.value = await p3.withdrawTodo(selected.value.id, comment.value, selected.value.instance_id);
    }
    const labels = { approve: "同意", reject: "驳回", withdraw: "撤回" };
    const actionLabel = kind === "withdraw" ? "已 withdraw（撤回）并办结" : `已${labels[kind]}并办结`;
    const approvalStatus = selected.value?.approval_status;
    notice.value = approvalStatus
      ? `${actionLabel}。审批状态：${statusLabel(approvalStatus)}（approval_status=${approvalStatus}）`
      : `${actionLabel}。`;
    await reload();
    error.value = "";
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
/* Q10: rows on the Q1 density tokens (nx-table); the selected row carries a product-color bar. */
.todo-filter { margin-bottom: var(--nx-space-4); }
tbody tr { cursor: pointer; }
tbody tr:hover td { background: var(--nx-surface-hover); }
tbody tr:focus-visible { outline: 2px solid var(--nx-primary-light); outline-offset: -2px; }
tr.pick td { background: var(--nx-product-light, var(--nx-primary-light)); }
tr.pick td:first-child { box-shadow: inset 3px 0 0 var(--nx-product, var(--nx-primary)); }
.cell-title { color: var(--nx-text-primary); font-weight: 500; }
.cell-code, .cell-time { color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); white-space: nowrap; }
.detail-actions { gap: var(--nx-space-2); }
.todo-skeleton { display: grid; gap: var(--nx-space-2); }
.inbox-layout { grid-template-columns: minmax(0, 1fr) 360px; align-items: start; }
.detail { position: sticky; top: 16px; display: grid; gap: 12px; }
.detail h3, .detail p { margin: 0; }
.detail-title { font-size: var(--nx-font-size-md); }
.detail-meta { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 8px 12px; margin: 0; font-size: var(--nx-font-size-sm); }
.detail-meta dt { color: var(--nx-text-secondary); }
.detail-meta dd { margin: 0; overflow-wrap: anywhere; }
.detail-meta .instance { color: var(--nx-text-secondary); }
.detail textarea { width: 100%; box-sizing: border-box; resize: vertical; }
.comment-box { display: grid; gap: 4px; padding: 10px 12px; border-radius: var(--nx-radius-sm); background: var(--nx-bg-page); }
.comment-box p { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.comment-label { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
@media (max-width: 1100px) {
  .inbox-layout { grid-template-columns: 1fr; }
  .detail { position: static; }
}
</style>
