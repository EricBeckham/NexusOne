<template>
  <div>
    <NxPageHeader
      eyebrow="星构"
      title="发布中心"
      description="只读查看当前租户的表单发布状态、版本和日志。"
    />
    <NxErrorState
      v-if="error"
      title="发布中心没有加载"
      :description="error.message || '请稍后重试'"
      :trace-id="error.traceId || ''"
      retry-label="重试"
      @retry="loadList"
    />
    <NxEmpty v-else-if="!loading && !items.length" title="暂无发布记录" description="当前租户还没有表单发布记录。" />
    <table v-else class="nx-table" data-testid="publish-table">
      <thead>
        <tr>
          <th>表单</th>
          <th>编码</th>
          <th>状态</th>
          <th>原状态</th>
          <th>运行版本</th>
          <th>最近日志</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in items" :key="row.id" :class="{ current: row.id === route.params.id }">
          <td><router-link :to="`/p3/publish-center/${row.id}`">{{ showText(row.name) }}</router-link></td>
          <td>{{ showText(row.form_code) }}</td>
          <td><NxStatusTag :status="row.status || ''" :label="showText(row.status)" /></td>
          <td>{{ showText(row.source_status) }}</td>
          <td>{{ showText(row.running_version) }}</td>
          <td>{{ logSummary(row.latest_log) }}</td>
        </tr>
      </tbody>
    </table>
    <NxErrorState
      v-if="detailError"
      title="发布详情没有加载"
      :description="detailError.message || '请稍后重试'"
      :trace-id="detailError.traceId || ''"
      retry-label="重试"
      @retry="loadDetail(String(route.params.id || ''))"
    />
    <section v-else-if="detail" class="nx-card detail" data-testid="publish-detail">
      <h2>{{ showText(detail.name) }}</h2>
      <p class="nx-muted">{{ showText(detail.app_code) }} · {{ showText(detail.form_code) }}</p>
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
      <table v-if="detail.versions?.length" class="nx-table">
        <thead>
          <tr><th>版本</th><th>状态</th><th>原状态</th><th>说明</th><th>时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="version in detail.versions" :key="version.version_no">
            <td>{{ showText(version.version_no) }}</td>
            <td>{{ showText(version.status) }}</td>
            <td>{{ showText(version.source_status) }}</td>
            <td>{{ showText(version.notes) }}</td>
            <td>{{ showText(version.created_at) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="nx-muted">暂无</p>
      <h3>日志</h3>
      <table v-if="detail.logs?.length" class="nx-table">
        <thead>
          <tr><th>版本</th><th>从</th><th>到</th><th>结果</th><th>说明</th><th>时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="log in detail.logs" :key="log.log_id">
            <td>{{ showText(log.version_no) }}</td>
            <td>{{ showText(log.from_status) }}</td>
            <td>{{ showText(log.to_status) }}</td>
            <td>{{ showText(log.outcome) }}</td>
            <td>{{ showText(log.message) }}</td>
            <td>{{ showText(log.occurred_at) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="nx-muted">暂无</p>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatusTag } from "../../components";
import { p3 } from "../../api/p3";
import { logSummary, showText } from "./publishDisplay";

const route = useRoute();
const items = ref([]);
const detail = ref(null);
const error = ref(null);
const detailError = ref(null);
const loading = ref(false);

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
.detail { margin-top: var(--nx-space-4); display: grid; gap: var(--nx-space-2); }
.detail h2, .detail h3 { margin: 0; }
.detail ol { margin: 0; padding-left: 1.2em; }
tr.current td { background: var(--nx-primary-light); }
</style>
