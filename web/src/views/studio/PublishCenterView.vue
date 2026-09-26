<template>
  <main class="publish-center-page">
    <NxPageHeader
      eyebrow="星构 · 工作室"
      title="发布中心"
      description="只读查看当前租户的表单发布状态、版本和日志。本页不发起发布动作。"
      :crumbs="[{ label: '星构' }, { label: '发布中心' }]"
    >
      <template #actions>
        <button class="ghost" type="button" :disabled="loading" @click="loadList">{{ loading ? "加载中…" : "刷新" }}</button>
        <router-link class="primary" to="/p3/forms">表单列表</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState
      v-if="error"
      title="发布中心没有加载"
      :description="error.message || '请稍后重试'"
      :trace-id="error.traceId || ''"
      retry-label="重试"
      @retry="loadList"
    />
    <div v-else-if="loading" class="publish-skeleton" data-testid="publish-loading" role="status">
      <NxSkeleton v-for="n in 4" :key="n" variant="list-row" label="正在加载发布记录" />
    </div>
    <NxEmpty v-else-if="!items.length" title="暂无发布记录" description="当前租户还没有表单发布记录。可先到表单列表完成校验与发布。">
      <template #next>
        <router-link class="primary" to="/p3/forms">前往表单列表</router-link>
      </template>
    </NxEmpty>
    <div v-else class="table-scroll">
      <table class="nx-table nx-table--compact" data-testid="publish-table">
        <thead>
          <tr>
            <th scope="col">表单</th>
            <th scope="col">编码</th>
            <th scope="col">状态</th>
            <th scope="col">原状态</th>
            <th scope="col">运行版本</th>
            <th scope="col">最近日志</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id" :class="{ current: row.id === route.params.id }">
            <td><router-link class="text-link" :to="`/p3/publish-center/${row.id}`">{{ showText(row.name) }}</router-link></td>
            <td class="nx-code">{{ showText(row.form_code) }}</td>
            <td><NxStatusTag :status="row.status || ''" :label="showText(row.status)" /></td>
            <td>{{ showText(row.source_status) }}</td>
            <td>{{ showText(row.running_version) }}</td>
            <td>{{ logSummary(row.latest_log) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <NxErrorState
      v-if="detailError"
      title="发布详情没有加载"
      :description="detailError.message || '请稍后重试'"
      :trace-id="detailError.traceId || ''"
      retry-label="重试"
      @retry="loadDetail(String(route.params.id || ''))"
    />
    <section v-else-if="detail" class="nx-card detail" data-testid="publish-detail">
      <header class="detail-heading">
        <div>
          <h2>{{ showText(detail.name) }}</h2>
          <p class="nx-muted">{{ showText(detail.app_code) }} · {{ showText(detail.form_code) }}</p>
        </div>
      </header>
      <p data-testid="schema-notice">{{ showText(detail.schema_notice) }}</p>
      <h3>Schema 步骤</h3>
      <ol data-testid="schema-steps">
        <li v-for="step in detail.schema_steps || []" :key="step">{{ step }}</li>
      </ol>
      <p v-if="!detail.schema_steps?.length" class="nx-muted">暂无</p>
      <p class="nx-muted">{{ detail.schema_execution === false ? "这些步骤只是标签，当前不会执行。" : "暂无" }}</p>
      <h3>业务类型</h3>
      <p>{{ provisionText }}</p>
      <h3>版本</h3>
      <div v-if="detail.versions?.length" class="table-scroll">
        <table class="nx-table nx-table--compact">
          <thead>
            <tr><th scope="col">版本</th><th scope="col">状态</th><th scope="col">原状态</th><th scope="col">说明</th><th scope="col">时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="version in detail.versions" :key="version.version_no">
              <td>{{ showText(version.version_no) }}</td>
              <td><NxStatusTag :status="version.status || ''" :label="showText(version.status)" /></td>
              <td>{{ showText(version.source_status) }}</td>
              <td>{{ showText(version.notes) }}</td>
              <td>{{ showText(version.created_at) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="nx-muted">暂无</p>
      <h3>日志</h3>
      <div v-if="detail.logs?.length" class="table-scroll">
        <table class="nx-table nx-table--compact">
          <thead>
            <tr><th scope="col">版本</th><th scope="col">从</th><th scope="col">到</th><th scope="col">结果</th><th scope="col">说明</th><th scope="col">时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="log in detail.logs" :key="log.log_id">
              <td>{{ showText(log.version_no) }}</td>
              <td>{{ showText(log.from_status) }}</td>
              <td>{{ showText(log.to_status) }}</td>
              <td><NxStatusTag :status="log.outcome || ''" :label="showText(log.outcome)" /></td>
              <td>{{ showText(log.message) }}</td>
              <td>{{ showText(log.occurred_at) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="nx-muted">暂无</p>
    </section>
  </main>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton, NxStatusTag } from "../../components";
import { p3 } from "../../api/p3";
import { logSummary, showText } from "./publishDisplay";

const route = useRoute();
const items = ref([]);
const detail = ref(null);
const error = ref(null);
const detailError = ref(null);
const loading = ref(true);

const provisionText = computed(() => {
  const provision = detail.value?.provision;
  if (!provision) {
    return "暂无";
  }
  return `${showText(provision.display_name)}（${showText(provision.type_code)}）`;
});

async function loadList() {
  loading.value = true;
  error.value = null;
  try {
    const rows = await p3.listPublishCenter();
    items.value = Array.isArray(rows) ? rows : [];
  } catch (err) {
    error.value = err;
    items.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadDetail(id) {
  detailError.value = null;
  if (!id) {
    detail.value = null;
    return;
  }
  try {
    detail.value = await p3.getPublishCenter(id);
  } catch (err) {
    detailError.value = err;
    detail.value = null;
  }
}

watch(() => route.params.id, (id) => loadDetail(id ? String(id) : ""), { immediate: true });
loadList();
</script>

<style scoped>
.publish-center-page { display: grid; gap: var(--nx-space-4); }
.publish-center-page :deep(.page-header) { margin-bottom: 0; }
.publish-skeleton { display: grid; gap: var(--nx-space-2); }
.table-scroll {
  overflow-x: auto;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius);
  background: var(--nx-surface);
}
.nx-table { border: 0; }
.text-link { color: var(--nx-primary); text-decoration: none; font-weight: 600; }
.detail { display: grid; gap: var(--nx-space-3); }
.detail-heading h2 { margin: 0; font-size: var(--nx-font-size-lg); }
.detail h3 { margin: var(--nx-space-2) 0 0; font-size: var(--nx-font-size-md); }
.detail ol { margin: 0; padding-left: 1.2em; }
tr.current td { background: var(--nx-primary-light); }
.primary,
.ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--nx-control-height);
  padding: 0 var(--nx-space-3);
  border-radius: var(--nx-control-radius);
  text-decoration: none;
  cursor: pointer;
}
.primary { color: #fff; background: var(--nx-product); border: 1px solid var(--nx-product); }
.ghost { color: var(--nx-text-regular); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); }
.ghost:disabled { opacity: .55; cursor: not-allowed; }
</style>
