<template>
  <div>
    <div class="nx-page-header">
      <h1>组织架构</h1>
      <p>维护当前租户的部门树。上级部门和负责人均从本租户数据中选择。</p>
    </div>
    <form class="nx-card" data-testid="department-form" @submit.prevent="create">
      <div class="form-grid">
        <div class="nx-field"><label>编码</label><input v-model.trim="form.code" required /></div>
        <div class="nx-field"><label>名称</label><input v-model.trim="form.name" required /></div>
        <div class="nx-field">
          <label>上级部门</label>
          <select v-model="form.parentId">
            <option value="">无（根部门）</option>
            <option v-for="row in activeDepartments" :key="row.id" :value="row.id">{{ indent(row.depth) }}{{ row.name }}</option>
          </select>
        </div>
        <div class="nx-field">
          <label>负责人</label>
          <select v-model="form.leaderPersonId">
            <option value="">未指定</option>
            <option v-for="person in activePersons" :key="person.id" :value="person.id">{{ person.displayName }}</option>
          </select>
        </div>
        <div class="nx-field"><label>排序</label><input v-model.number="form.sortOrder" type="number" /></div>
        <button class="primary submit" type="submit" :disabled="busy">新增部门</button>
      </div>
    </form>
    <p v-if="error" class="nx-error" data-testid="department-error">{{ error }}</p>
    <p v-if="message" class="ok" data-testid="department-message">{{ message }}</p>
    <div class="nx-card tree-card">
      <div v-for="row in flat" :key="row.id" class="node" :style="{ paddingLeft: row.depth * 22 + 'px' }">
        <span class="branch" aria-hidden="true">{{ row.depth ? '└' : '●' }}</span>
        <span>
          <strong>{{ row.name }}</strong>
          <small>{{ row.code }}</small>
        </span>
        <span class="status" :class="row.status.toLowerCase()">{{ row.status === "ACTIVE" ? "启用" : "已停用" }}</span>
        <button
          v-if="row.status === 'ACTIVE'"
          class="link danger-link"
          type="button"
          :data-testid="`disable-department-${row.id}`"
          :disabled="busy"
          @click="disable(row)"
        >停用</button>
      </div>
      <p v-if="!flat.length" class="nx-empty">暂无部门，请先创建根部门</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { p2 } from "../../api/client";
import { ensureContext, session } from "../../api/session";

const tree = ref([]);
const persons = ref([]);
const error = ref("");
const message = ref("");
const busy = ref(false);
const form = reactive({ code: "", name: "", parentId: "", leaderPersonId: "", sortOrder: 0 });

function flatten(nodes, depth = 0) {
  return (nodes || []).flatMap((node) => {
    const department = node.department || node;
    return [{ ...department, depth }, ...flatten(node.children || [], depth + 1)];
  });
}

const flat = computed(() => flatten(tree.value));
const activeDepartments = computed(() => flat.value.filter((row) => row.status === "ACTIVE"));
const activePersons = computed(() => persons.value.filter((row) => row.status === "ACTIVE"));
const indent = (depth) => "　".repeat(depth);

async function reload() {
  [tree.value, persons.value] = await Promise.all([p2.departments(), p2.persons()]);
}

onMounted(async () => {
  try {
    if (session.accessToken && !session.contextId) await ensureContext();
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`.trim();
  }
});

async function create() {
  await run(async () => {
    await p2.createDepartment({
      code: form.code,
      name: form.name,
      parentId: form.parentId || null,
      leaderPersonId: form.leaderPersonId || null,
      sortOrder: Number(form.sortOrder) || 0,
    });
    Object.assign(form, { code: "", name: "", parentId: "", leaderPersonId: "", sortOrder: 0 });
    message.value = "部门已创建";
  });
}

async function disable(row) {
  await run(async () => {
    await p2.disableDepartment(row.id);
    message.value = `部门“${row.name}”已停用`;
  });
}

async function run(action) {
  busy.value = true;
  error.value = "";
  message.value = "";
  try {
    await action();
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`.trim();
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.form-grid { display: grid; grid-template-columns: repeat(5, minmax(120px, 1fr)); gap: 12px; align-items: end; }
.submit { margin-bottom: 12px; }
.tree-card { margin-top: 16px; }
.node { min-height: 48px; display: grid; grid-template-columns: 18px minmax(180px, 1fr) auto 52px; gap: 10px; align-items: center; border-bottom: 1px solid var(--nx-divider); }
.node:last-child { border-bottom: 0; }
.node small { display: block; color: var(--nx-text-secondary); }
.branch { color: var(--nx-text-disabled); }
.status { padding: 2px 8px; border-radius: 999px; font-size: 12px; }
.status.active { color: var(--nx-success); background: var(--nx-success-light); }
.status.disabled { color: var(--nx-text-secondary); background: var(--nx-bg-page); }
.link { border: 0; background: transparent; cursor: pointer; }
.danger-link { color: var(--nx-danger); }
.ok { color: var(--nx-success); }
@media (max-width: 1000px) { .form-grid { grid-template-columns: 1fr 1fr; } }
</style>
