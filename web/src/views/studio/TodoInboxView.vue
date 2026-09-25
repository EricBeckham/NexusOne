<template>
  <div>
    <NxPageHeader title="待办" description="集中查看我的待办、已办和我发起的流程，并完成同意、驳回或撤回。" />
    <NxSourceBanner body="同意或驳回会把意见一并提交。我的申请展示进行中、通过、驳回和意见。" />
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。请先登录并完成租户切换。</p>
    <div class="nx-tabs">
      <button
        v-for="item in boxes"
        :key="item.id"
        :class="{ active: box === item.id }"
        type="button"
        @click="switchBox(item.id)"
      >{{ item.label }}</button>
    </div>
    <div class="nx-field" style="max-width:280px;">
      <label>筛选</label>
      <input v-model="query" placeholder="单号 / 任务名" />
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <p v-if="notice" class="nx-muted">{{ notice }}</p>
    <div class="nx-grid" style="grid-template-columns: 1fr 360px;">
      <table class="nx-table" data-testid="todo-table">
        <thead>
          <tr>
            <th>流程</th>
            <th>业务单号</th>
            <th>到达</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!filtered.length">
            <td colspan="4" class="nx-empty">{{ box === "requests" ? "暂无" : "此箱为空。先在已发布表单上 submit-start。" }}</td>
          </tr>
          <tr
            v-for="row in filtered"
            :key="row.id"
            :class="{ pick: selected?.id === row.id }"
            @click="open(row.id)"
          >
            <td>{{ row.title }}</td>
            <td class="nx-code">{{ row.biz_no }}</td>
            <td>{{ row.arrived_at }}</td>
            <td><NxStatus :label="box === 'requests' ? applicationStatus(row.status) : row.status" :tone="tone(row.status)" /></td>
          </tr>
        </tbody>
      </table>
      <div class="nx-card">
        <h3>办理</h3>
        <template v-if="selected">
          <p><strong>{{ selected.title }}</strong></p>
          <p class="nx-muted">{{ selected.process }} · {{ selected.biz_no }}</p>
          <p class="nx-muted">instance {{ selected.instance_id }}</p>
          <p v-if="selected.approval_status" class="nx-muted">approval_status {{ selected.approval_status }}</p>
          <router-link
            v-if="selected.ledger_id && selected.record_id"
            :to="`/p3/ledgers/${selected.ledger_id}/records/${selected.record_id}`"
            data-testid="todo-record-link"
          >查看业务记录</router-link>
          <p v-if="box === 'requests'" data-testid="application-comment">意见 {{ selected.comment || "暂无" }}</p>
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
        <p v-else class="nx-muted">选择一条待办。转办 / 会签不在 M0。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxPageHeader } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
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
    notice.value = `${actionLabel}。approval_status=${selected.value?.approval_status || "—"}`;
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
tr.pick td { background: var(--nx-primary-light); }
</style>
