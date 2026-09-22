<template>
  <div>
    <div class="m-tabs">
      <button
        v-for="item in boxes"
        :key="item.id"
        type="button"
        :class="{ active: box === item.id }"
        :data-testid="`m-todo-box-${item.id}`"
        @click="switchBox(item.id)"
      >
        {{ item.label }}
      </button>
    </div>
    <div class="m-field">
      <label>筛选</label>
      <input v-model="queryText" placeholder="单号 / 任务名" data-testid="m-todo-filter" />
    </div>
    <MPermDecision :decision="decision" :cell="H5_SURFACES.todoRead" />
    <p v-if="!session.contextId" class="m-error">没有 TenantContext。办理需要已签发上下文。</p>
    <p v-if="error" class="m-error" data-testid="m-todos-error">{{ error }}</p>
    <p v-if="!filtered.length && !error" class="m-muted" data-testid="m-todos-empty">此箱为空。</p>
    <router-link
      v-for="row in filtered"
      :key="row.id"
      class="m-entity"
      :to="{ path: `/m/p3/todos/${row.id}`, query: { box } }"
      :data-testid="`m-todo-${row.id}`"
    >
      <strong>{{ row.title }}</strong>
      <div class="m-muted">{{ row.biz_no || "—" }} · {{ row.arrived_at || "—" }}</div>
      <div class="m-muted">{{ row.status || "—" }}</div>
    </router-link>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { p3 } from "../../../../api/p3";
import { session } from "../../../../api/session";
import MPermDecision from "../../../permission/MPermDecision.vue";
import { H5_SURFACES } from "../../../permission/surfaceCatalog";
import { useH5Surface } from "../../../permission/useH5Surface";

const route = useRoute();
const router = useRouter();
const boxes = [
  { id: "inbox", label: "待办" },
  { id: "done", label: "已办" },
  { id: "initiated", label: "我发起" },
];
const box = ref(route.query.box || "inbox");
const rows = ref([]);
const queryText = ref("");
const { decision, error, requireCell, capture } = useH5Surface();

const filtered = computed(() => {
  const q = queryText.value.trim();
  if (!q) {
    return rows.value;
  }
  return rows.value.filter((row) =>
    [row.title, row.biz_no, row.process].some((part) => String(part || "").includes(q)),
  );
});

async function reload() {
  await requireCell(H5_SURFACES.todoRead);
  const body = await p3.listTodos(box.value);
  rows.value = body.items || [];
}

function switchBox(next) {
  box.value = next;
  router.replace({ path: "/m/p3/todos", query: { box: next } });
}

watch(
  () => route.query.box,
  (next) => {
    box.value = next || "inbox";
    reload().catch((err) => {
      rows.value = [];
      capture(err);
    });
  },
);

onMounted(async () => {
  try {
    await reload();
  } catch (err) {
    rows.value = [];
    capture(err);
  }
});
</script>
