<template>
  <section class="nx-card content-card" data-testid="exception-grants">
    <div class="section-heading">
      <div>
        <h2>例外授权</h2>
        <p class="nx-muted">直接给个人的授权和角色，不经过岗位。按 D5 保留为例外通道；常规权限请通过「岗位任职」给岗位绑定角色。</p>
      </div>
      <button class="primary" type="button" :disabled="loading" @click="load">刷新</button>
    </div>

    <form class="filters" data-testid="exception-filters" @submit.prevent="load">
      <label class="nx-field">
        <span>类型</span>
        <select v-model="filter.kind">
          <option value="">全部</option>
          <option value="GRANT">个人授权 / 拒绝</option>
          <option value="ROLE">个人角色</option>
        </select>
      </label>
      <label class="nx-field">
        <span>关键字</span>
        <input v-model.trim="filter.q" type="search" placeholder="人员、资源、动作或角色" />
      </label>
      <label class="check">
        <input v-model="filter.includeSystem" type="checkbox" />
        显示系统自动分配（成员种子、产品包）
      </label>
      <button type="submit" :disabled="loading">筛选</button>
    </form>

    <p v-if="actionError" class="nx-error" data-testid="exception-action-error">{{ actionError }}</p>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <p v-else-if="loading" class="nx-muted">正在加载…</p>
    <NxEmpty v-else-if="!items.length" title="没有例外授权" description="当前筛选条件下，没有直接挂在个人身上的授权或角色。" compact />
    <div v-else class="table-scroll">
      <table class="nx-table" data-testid="exception-table">
        <thead><tr><th>人员</th><th>类型</th><th>内容</th><th>数据范围</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="item in items" :key="`${item.kind}-${item.id}`" :data-exception="item.id">
            <td>{{ item.person_name || item.person_id || "暂无" }}</td>
            <td>
              <span class="badge" :class="{ system: item.system }">{{ item.system ? "系统" : "例外" }}</span>
              {{ item.kind === "ROLE" ? "个人角色" : item.effect === "DENY" ? "个人拒绝" : "个人授权" }}
            </td>
            <td>
              <template v-if="item.kind === 'ROLE'">{{ item.role_name || item.role_code }} <span class="nx-code">{{ item.role_code }}</span></template>
              <template v-else><span class="nx-code">{{ item.resource }}</span> / {{ item.action }}</template>
            </td>
            <td>{{ item.scope || "—" }}</td>
            <td>
              <button v-if="!item.system" type="button" class="link danger" :disabled="revoking === item.id" @click="revoke(item)">撤销</button>
              <span v-else class="nx-muted">由系统维护</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { NxEmpty } from "../../components";
import { perm } from "../../api/client";

const items = ref([]);
const loading = ref(false);
const error = ref("");
const actionError = ref("");
const revoking = ref("");
const filter = reactive({ kind: "", q: "", includeSystem: false });

function message(err) {
  if (err?.status === 403 || err?.code === "P2.PERM.0003") return "你没有执行此操作的权限。";
  return `${err?.code || ""} ${err?.message || "操作失败"}`.trim();
}

async function load() {
  loading.value = true;
  error.value = "";
  actionError.value = "";
  try {
    items.value = (await perm.exceptions({ ...filter })) || [];
  } catch (err) {
    items.value = [];
    error.value = message(err);
  } finally {
    loading.value = false;
  }
}

async function revoke(item) {
  revoking.value = item.id;
  actionError.value = "";
  try {
    if (item.kind === "ROLE") await perm.revokeExceptionAssignment(item.id);
    else await perm.revokeExceptionGrant(item.id);
    items.value = items.value.filter((row) => row.id !== item.id);
  } catch (err) {
    actionError.value = message(err); // keep the list on screen; only the action failed
  } finally {
    revoking.value = "";
  }
}

onMounted(load);
</script>

<style scoped>
.filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; margin: 12px 0; }
.check { display: flex; gap: 6px; align-items: center; }
.badge { display: inline-block; border-radius: 10px; padding: 0 8px; font-size: 12px; background: var(--nx-warning-bg, #fef0c7); color: var(--nx-warning, #b54708); }
.badge.system { background: var(--nx-surface-muted, #f2f4f7); color: var(--nx-text-muted, #667085); }
.link { background: none; border: 0; cursor: pointer; padding: 0; }
.danger { color: var(--nx-danger, #b42318); }
</style>
