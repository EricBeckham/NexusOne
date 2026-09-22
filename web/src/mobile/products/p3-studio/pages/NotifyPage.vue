<template>
  <div>
    <div class="m-card">
      <h3>通知跳转</h3>
      <p class="m-muted">点击通知进入安全 /m/* 运行页。桌面 /p3、Token、tenant_id、外链会被丢掉。</p>
    </div>
    <p v-if="!session.contextId" class="m-error">没有 TenantContext。列表为空不一定是故障。</p>
    <p v-if="error" class="m-error" data-testid="m-notify-error">{{ error }}</p>
    <p v-if="jumpHint" class="m-muted" data-testid="m-notify-jump-hint">深链目标 {{ jumpHint }}</p>
    <button
      v-if="jumpHint"
      class="m-btn primary"
      type="button"
      data-testid="m-notify-apply-jump"
      @click="applyJump(jumpHint)"
    >
      打开深链
    </button>
    <div style="height:8px" />
    <p v-if="!rows.length && !error" class="m-muted" data-testid="m-notify-empty">当前租户没有站内通知。</p>
    <button
      v-for="row in rows"
      :key="row.notification_id || row.id"
      class="m-entity"
      type="button"
      :data-testid="`m-notify-${row.notification_id || row.id}`"
      @click="openRow(row)"
    >
      <strong>{{ row.title }}</strong>
      <div class="m-muted">{{ row.body }}</div>
      <div class="m-muted">{{ row.template_code }} · {{ row.status }} → {{ resolveNotifyJump(row) }}</div>
    </button>
    <div class="m-card">
      <h3>演示跳转（stub 会话可用）</h3>
      <button class="m-btn ghost" type="button" data-testid="m-notify-demo-todo" @click="demo('todo')">
        跳转待办箱
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" data-testid="m-notify-demo-form" @click="demo('form')">
        跳转表单运行
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" data-testid="m-notify-demo-list" @click="demo('list')">
        跳转列表
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" data-testid="m-notify-demo-query" @click="demo('query')">
        跳转查询
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { notify } from "../../../../api/client";
import { session } from "../../../../api/session";
import { resolveNotifyJump, safeNotifyJump } from "../../../runtime/notifyJump";

const route = useRoute();
const router = useRouter();
const rows = ref([]);
const error = ref("");
const jumpHint = computed(() => (route.query.jump ? safeNotifyJump(String(route.query.jump)) : ""));

onMounted(async () => {
  try {
    const body = await notify.list();
    rows.value = Array.isArray(body) ? body : body?.items || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

function applyJump(path) {
  router.push(safeNotifyJump(path));
}

async function openRow(row) {
  const dest = resolveNotifyJump(row);
  const id = row.notification_id || row.id;
  if (id && row.status !== "READ") {
    try {
      await notify.read(id);
    } catch {
      /* mark-read is best effort; jump still happens */
    }
  }
  applyJump(dest);
}

function demo(kind) {
  const samples = {
    todo: { template_code: "approval.task.assigned" },
    form: { form_id: "demo-form-id" },
    list: { ledger_id: "demo-ledger-id" },
    query: { jump: "/m/p3/query" },
  };
  applyJump(resolveNotifyJump(samples[kind]));
}
</script>
