<template>
  <div>
    <div class="nx-page-header">
      <h1>待办</h1>
      <p>我的待办 / 已办 / 我发起。办理动作只改 MOCK 夹具，没有 Flowable 实例。</p>
    </div>
    <NxSourceBanner body="MOCK 待办收件箱。真实 GET /api/v1/p3/todos 与顺序审批等待 M0-08。G18 审批段未关闭。" />
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
      <input v-model="query" placeholder="单号 / 申请人 / 事由" />
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <div class="nx-grid" style="grid-template-columns: 1fr 360px;">
      <table class="nx-table" data-testid="todo-table">
        <thead>
          <tr>
            <th>流程</th>
            <th>业务单号</th>
            <th>申请人</th>
            <th>金额</th>
            <th>到达</th>
            <th>优先级</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!filtered.length">
            <td colspan="7" class="nx-empty">此箱为空</td>
          </tr>
          <tr
            v-for="row in filtered"
            :key="row.id"
            :class="{ pick: selected?.id === row.id }"
            @click="open(row.id)"
          >
            <td>{{ row.title }}</td>
            <td class="nx-code">{{ row.biz_no }}</td>
            <td>{{ row.applicant }}</td>
            <td>{{ row.amount }}</td>
            <td>{{ row.arrived_at }}</td>
            <td><NxStatus :label="row.priority" :tone="row.priority === 'HIGH' ? 'danger' : 'info'" /></td>
            <td><NxStatus :label="row.status" :tone="tone(row.status)" /></td>
          </tr>
        </tbody>
      </table>
      <div class="nx-card">
        <h3>办理</h3>
        <template v-if="selected">
          <p><strong>{{ selected.title }}</strong></p>
          <p class="nx-muted">{{ selected.process }} · {{ selected.biz_no }}</p>
          <p v-if="selected.record">事由 {{ selected.record.title }} / {{ selected.record.amount }}</p>
          <div class="nx-field"><label>意见</label><textarea v-model="comment" rows="3" /></div>
          <div class="nx-row">
            <button v-if="box === 'inbox'" class="primary" type="button" data-testid="todo-approve" @click="act('approve')">同意</button>
            <button v-if="box === 'inbox'" class="danger" type="button" @click="act('reject')">驳回</button>
            <button v-if="box === 'initiated' && selected.status === 'PENDING'" class="ghost" type="button" @click="act('withdraw')">撤回</button>
          </div>
          <p v-if="selected.comment" class="nx-muted">上次意见：{{ selected.comment }}</p>
        </template>
        <p v-else class="nx-muted">选择一条待办。转办 / 会签不在 M0。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";

const route = useRoute();
const router = useRouter();
const boxes = [
  { id: "inbox", label: "我的待办" },
  { id: "done", label: "已办" },
  { id: "initiated", label: "我发起" },
];
const box = ref(route.query.box || "inbox");
const rows = ref([]);
const selected = ref(null);
const comment = ref("");
const query = ref("");
const error = ref("");

const filtered = computed(() => {
  const q = query.value.trim();
  if (!q) return rows.value;
  return rows.value.filter((row) =>
    [row.title, row.biz_no, row.applicant].some((part) => String(part || "").includes(q)),
  );
});

function tone(status) {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED" || status === "WITHDRAWN") return "danger";
  return "warning";
}

async function reload() {
  const body = await p3.listTodos(box.value);
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
  selected.value = await p3.getTodo(id);
  comment.value = "";
}

async function act(kind) {
  try {
    if (kind === "approve") selected.value = await p3.approveTodo(selected.value.id, comment.value);
    if (kind === "reject") selected.value = await p3.rejectTodo(selected.value.id, comment.value);
    if (kind === "withdraw") selected.value = await p3.withdrawTodo(selected.value.id, comment.value);
    await reload();
  } catch (err) {
    error.value = err.message;
  }
}
</script>

<style scoped>
tr { cursor: pointer; }
tr.pick td { background: var(--nx-primary-light); }
</style>
