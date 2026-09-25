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
    <div class="nx-field filter">
      <label for="todo-filter">筛选</label>
      <input id="todo-filter" v-model="query" placeholder="单号 / 任务名" />
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <p v-if="notice" class="nx-muted">{{ notice }}</p>
    <div class="nx-grid inbox-layout">
      <NxEmpty v-if="!filtered.length" :title="emptyCopy.title" :description="emptyCopy.description" data-testid="todo-empty">
        <template v-if="emptyCopy.to" #action>
          <router-link class="primary" :to="emptyCopy.to">{{ emptyCopy.action }}</router-link>
        </template>
      </NxEmpty>
      <table v-else class="nx-table" data-testid="todo-table">
        <thead>
          <tr>
            <th>{{ box === "requests" ? "申请" : "流程" }}</th>
            <th>{{ box === "requests" ? "申请编号" : "业务单号" }}</th>
            <th>{{ box === "requests" ? "提交时间" : "到达" }}</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in filtered"
            :key="row.id"
            :class="{ pick: selected?.id === row.id }"
            @click="open(row.id)"
          >
            <td>{{ row.title }}</td>
            <td class="nx-code">{{ row.biz_no }}</td>
            <td>{{ formatTime(row.arrived_at) }}</td>
            <td><NxStatus :label="box === 'requests' ? applicationStatus(row.status) : statusLabel(row.status)" :tone="tone(row.status)" /></td>
          </tr>
        </tbody>
      </table>
      <div class="nx-card detail">
        <h3>{{ box === 'inbox' ? "办理" : "详情" }}</h3>
        <template v-if="selected">
          <p class="detail-title"><strong>{{ selected.title }}</strong></p>
          <dl class="detail-meta">
            <dt>流程</dt><dd>{{ selected.process || "暂无" }}</dd>
            <dt>业务单号</dt><dd class="nx-code">{{ selected.biz_no || "暂无" }}</dd>
            <dt v-if="selected.approval_status">审批状态</dt>
            <dd v-if="selected.approval_status"><NxStatus :label="statusLabel(selected.approval_status)" :tone="tone(selected.approval_status)" /></dd>
            <dt>流程实例</dt><dd class="nx-code instance">{{ selected.instance_id || "暂无" }}</dd>
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
          <div class="nx-row">
            <button v-if="box === 'inbox'" class="primary" type="button" data-testid="todo-approve" :disabled="busy" @click="act('approve')">同意</button>
            <button v-if="box === 'inbox'" class="danger" type="button" data-testid="todo-reject" :disabled="busy" @click="act('reject')">驳回</button>
            <button v-if="box === 'initiated' && isPending(selected)" class="ghost" type="button" data-testid="todo-withdraw" :disabled="busy" @click="act('withdraw')">撤回</button>
          </div>
          <p v-if="session.lastTraceId" class="nx-muted" style="margin-top:8px;">
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
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxEmpty, NxPageHeader } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";

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
const query = ref("");
const error = ref("");
const notice = ref("");
const busy = ref(false);

const filtered = computed(() => {
  const q = query.value.trim();
  if (!q) return rows.value;
  return rows.value.filter((row) =>
    [row.title, row.biz_no, row.process].some((part) => String(part || "").includes(q)),
  );
});

const emptyCopy = computed(() => {
  if (query.value.trim() && rows.value.length) {
    return { title: "没有匹配项", description: "请调整筛选词后重试。", to: "", action: "" };
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
    return { title: "暂无已办", description: "同意或驳回后，办结项会出现在这里。", to: "", action: "" };
  }
  if (box.value === "initiated") {
    return { title: "暂无我发起", description: "从已发布流程发起后，记录会出现在这里。", to: "", action: "" };
  }
  return { title: "暂无待办", description: "当前租户还没有待你处理的申请。", to: "", action: "" };
});

function tone(status) {
  const value = String(status || "").toUpperCase();
  if (value === "APPROVED" || value === "APPROVE" || value === "DONE") return "success";
  if (value === "REJECTED" || value === "REJECT" || value === "WITHDRAWN") return "danger";
  return "warning";
}

function applicationStatus(status) {
  const value = String(status || "").toUpperCase();
  if (value === "RUNNING") return "进行中";
  if (value === "APPROVED") return "通过";
  if (value === "REJECTED") return "驳回";
  return status || "进行中";
}

const STATUS_LABELS = {
  PENDING: "待处理",
  CREATED: "待处理",
  RUNNING: "进行中",
  IN_APPROVAL: "审批中",
  APPROVE: "已同意",
  APPROVED: "已通过",
  REJECT: "已驳回",
  REJECTED: "已驳回",
  WITHDRAWN: "已撤回",
  DONE: "已办结",
};

function formatTime(value) {
  if (!value) return "暂无";
  const text = String(value);
  if (!text.includes("T")) return text;
  const date = new Date(text);
  return Number.isNaN(date.getTime()) ? text : date.toLocaleString("zh-CN", { hour12: false });
}

function statusLabel(status) {
  return STATUS_LABELS[String(status || "").toUpperCase()] || status || "暂无";
}

function isPending(row) {
  const value = String(row?.status || "").toUpperCase();
  return value === "PENDING" || value === "RUNNING" || value === "CREATED";
}

async function reload() {
  const body = box.value === "requests" ? await p3.listProductApplications() : await p3.listTodos(box.value);
  rows.value = body.items || [];
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
  router.replace({ path: "/p3/todos", query: { box: next } });
  reload().catch((err) => {
    error.value = err.message;
  });
}

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
tr { cursor: pointer; }
tbody tr:hover td { background: var(--nx-surface-hover); }
tr.pick td { background: var(--nx-primary-light); }
.filter { max-width: 280px; }
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
