<template>
  <main class="template-list-page">
    <NxPageHeader
      eyebrow="星域 / 平台配置"
      title="表单模板"
      description="维护平台模板草稿，校验并发布可供产品定义选用的版本。这里不是租户台账表单。"
    >
      <template #actions>
        <button class="ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <router-link class="primary" to="/p1/form-templates/new">新建模板</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="load" />
    <template v-else>
      <div class="list-meta" aria-live="polite">
        <h2>模板目录</h2>
        <span>{{ loading ? "正在加载模板…" : `共 ${rows.length} 个模板` }}</span>
      </div>
      <NxEmpty v-if="!loading && !rows.length" title="还没有表单模板" description="新建模板草稿后，可在编辑器校验并发布。">
        <template #next><router-link class="primary" to="/p1/form-templates/new">新建模板</router-link></template>
      </NxEmpty>
      <div v-else class="table-scroll">
        <table class="nx-table nx-table--compact" data-testid="template-list">
          <thead><tr><th scope="col">名称</th><th scope="col">编码</th><th scope="col">状态</th><th scope="col">字段数</th><th scope="col">操作</th></tr></thead>
          <tbody>
            <tr v-if="loading"><td colspan="5" class="loading-cell"><NxSkeleton v-for="index in 3" :key="index" variant="list-row" label="正在加载模板目录" compact /></td></tr>
            <template v-else>
              <tr v-for="row in rows" :key="row.id">
                <td class="template-name">{{ row.name || "未命名模板" }}</td>
                <td class="nx-code">{{ row.code || "暂无" }}</td>
                <td><NxStatusTag :status="row.status || ''" :label="statusLabel(row.status)" /></td>
                <td>{{ Array.isArray(row.fields) ? row.fields.length : "暂无" }}</td>
                <td><router-link class="text-action" :to="`/p1/form-templates/${row.id}`">编辑</router-link></td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </main>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p1 } from "../../api/client";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton, NxStatusTag } from "../../components";

const rows = ref([]);
const loading = ref(true);
const error = ref("");
const traceId = ref("");

function statusLabel(status) {
  return ({ DRAFT: "草稿", VALIDATED: "已校验", PUBLISHED: "已发布", ARCHIVED: "已归档" })[status] || status || "暂无";
}

async function load() {
  loading.value = true;
  error.value = "";
  traceId.value = "";
  try {
    const body = await p1.formTemplates();
    rows.value = Array.isArray(body) ? body : [];
  } catch (err) {
    rows.value = [];
    error.value = err?.message || "表单模板加载失败";
    traceId.value = err?.traceId || "";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.template-list-page { display: grid; gap: var(--nx-space-4); }
.template-list-page :deep(.page-header) { margin-bottom: 0; }
.list-meta { display: flex; align-items: baseline; justify-content: space-between; gap: var(--nx-space-3); }
.list-meta h2 { margin: 0; font-size: var(--nx-font-size-md); }
.list-meta span { color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.table-scroll { overflow-x: auto; border: 1px solid var(--nx-border); border-radius: var(--nx-radius); }
.nx-table { min-width: 560px; border: 0; }
.template-name { font-weight: 600; }
.loading-cell { padding: var(--nx-space-2) !important; }
.loading-cell :deep(.nx-skeleton) { margin-bottom: var(--nx-space-2); border: 0; }
.text-action { color: var(--nx-product); text-decoration: none; font-weight: 600; }
.text-action:hover { text-decoration: underline; }
.primary,.ghost { display: inline-flex; align-items: center; justify-content: center; min-height: var(--nx-control-height); padding: 0 var(--nx-space-3); border-radius: var(--nx-control-radius); text-decoration: none; cursor: pointer; }
.primary { color: #fff; background: var(--nx-product); border: 1px solid var(--nx-product); }
.ghost { color: var(--nx-text-regular); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); }
.ghost:disabled { opacity: .55; cursor: not-allowed; }
</style>
