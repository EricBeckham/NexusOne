<template>
  <div>
    <NxPageHeader
      eyebrow="星权"
      title="组织架构"
      description="Tree 只包含当前 TenantContext 下的部门。跨租户 parent_id 会被服务端拒绝。"
    />
    <form class="nx-card" @submit.prevent="create">
      <div class="nx-row">
        <div class="nx-field"><label>编码</label><input v-model="form.code" required /></div>
        <div class="nx-field"><label>名称</label><input v-model="form.name" required /></div>
        <div class="nx-field"><label>上级 ID（可空）</label><input v-model="form.parent_id" /></div>
        <button class="primary" type="submit">新增部门</button>
      </div>
    </form>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <div class="nx-card" style="margin-top:16px;">
      <div v-for="row in flat" :key="row.id" class="node" :style="{ paddingLeft: row.depth * 16 + 'px' }">
        <strong>{{ row.name }}</strong>
        <span class="nx-muted"> · {{ row.code }}</span>
      </div>
      <p v-if="!flat.length" class="nx-muted">空组织或尚未签发上下文</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { p2 } from "../../api/client";
import { NxPageHeader } from "../../components";
import { ensureContext, session } from "../../api/session";

const tree = ref([]);
const error = ref("");
const form = reactive({ code: "", name: "", parent_id: "" });

function flatten(nodes, depth = 0) {
  const out = [];
  for (const node of nodes || []) {
    const dept = node.department || node;
    out.push({ id: dept.id, name: dept.name, code: dept.code, depth });
    out.push(...flatten(node.children || [], depth + 1));
  }
  return out;
}

const flat = computed(() => flatten(tree.value));

async function reload() {
  tree.value = (await p2.departments()) || [];
}

onMounted(async () => {
  try {
    if (session.accessToken && !session.contextId) {
      await ensureContext();
    }
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function create() {
  try {
    await p2.createDepartment({
      code: form.code,
      name: form.name,
      parent_id: form.parent_id || null,
      sort_order: 0,
    });
    form.code = "";
    form.name = "";
    form.parent_id = "";
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}
</script>

<style scoped>
.primary { background: var(--nx-product); color: #fff; border: 0; border-radius: 6px; padding: 8px 12px; }
.node { padding: 6px 0; }
.kids { margin-left: 16px; }
</style>
