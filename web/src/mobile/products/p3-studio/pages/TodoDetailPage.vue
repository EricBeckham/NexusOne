<template>
  <div>
    <div v-if="todo" class="m-card" data-testid="m-todo-detail">
      <h3>{{ todo.title }}</h3>
      <p class="m-muted">{{ todo.process }} · {{ todo.biz_no || "—" }}</p>
      <p class="m-muted">状态 {{ todo.status || "—" }} · instance {{ todo.instance_id || "—" }}</p>
      <p v-if="todo.approval_status" class="m-muted">approval_status {{ todo.approval_status }}</p>
    </div>
    <p v-if="error" class="m-error" data-testid="m-todo-detail-error">{{ error }}</p>
    <p v-if="notice" class="m-ok" data-testid="m-todo-detail-ok">{{ notice }}</p>
    <div v-if="todo" class="m-card">
      <div class="m-field">
        <label>意见（仅本页，API 无 comment 字段）</label>
        <textarea v-model="comment" rows="3" data-testid="m-todo-comment" />
      </div>
    </div>
    <div v-if="todo" class="m-sticky">
      <button
        v-if="box === 'inbox'"
        class="m-btn primary"
        type="button"
        :disabled="busy"
        data-testid="m-todo-approve"
        @click="act('approve')"
      >
        同意
      </button>
      <button
        v-if="box === 'inbox'"
        class="m-btn danger"
        type="button"
        :disabled="busy"
        data-testid="m-todo-reject"
        @click="act('reject')"
      >
        驳回
      </button>
      <button
        v-if="box === 'initiated' && isPendingTodo(todo)"
        class="m-btn ghost"
        type="button"
        :disabled="busy"
        data-testid="m-todo-withdraw"
        @click="act('withdraw')"
      >
        撤回
      </button>
      <router-link class="m-btn ghost" :to="{ path: '/m/p3/todos', query: { box } }">返回待办箱</router-link>
    </div>
    <p v-else-if="!error" class="m-muted">正在加载待办…</p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { p3 } from "../../../../api/p3";
import { isPendingTodo } from "../../../runtime/display";

const route = useRoute();
const todo = ref(null);
const comment = ref("");
const error = ref("");
const notice = ref("");
const busy = ref(false);
const box = computed(() => route.query.box || "inbox");

onMounted(async () => {
  try {
    todo.value = await p3.getTodo(route.params.id);
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
    todo.value = { id: route.params.id, title: "待办", status: "PENDING", instance_id: "" };
  }
});

async function act(kind) {
  if (!todo.value?.id) {
    return;
  }
  error.value = "";
  notice.value = "";
  busy.value = true;
  try {
    if (kind === "approve") {
      todo.value = await p3.approveTodo(todo.value.id);
    }
    if (kind === "reject") {
      todo.value = await p3.rejectTodo(todo.value.id);
    }
    if (kind === "withdraw") {
      todo.value = await p3.withdrawTodo(todo.value.id, comment.value, todo.value.instance_id);
    }
    notice.value = `已 ${kind}。approval_status=${todo.value?.approval_status || "—"}`;
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  } finally {
    busy.value = false;
  }
}
</script>
