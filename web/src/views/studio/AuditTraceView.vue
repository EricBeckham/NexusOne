<template>
  <div>
    <NxPageHeader title="审计哈希链" description="按当前租户与 Trace ID 只读查询数据和审批事件，不修改审计链。" />
    <NxSourceBanner body="M0-11 platform.audit_record。submit-start / approve 会带 X-Trace-Id。空列表表示该 trace 在本租户没有事件，不是 MOCK。" />
    <form class="nx-card" style="margin-bottom:16px;max-width:720px;" @submit.prevent="load">
      <div class="nx-field">
        <label>trace_id（UUID / OTEL）</label>
        <input v-model="traceId" data-testid="audit-trace-input" placeholder="0199a1c0-0000-7000-8000-…" />
      </div>
      <button class="primary" type="submit" data-testid="audit-trace-submit">按 trace 查询</button>
    </form>
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。请先登录并完成租户切换。</p>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <p v-if="result" class="nx-muted">{{ result.events?.length || 0 }} 条 · tenant {{ result.tenant_id }}</p>
    <table v-if="result" class="nx-table" data-testid="audit-table">
      <thead>
        <tr>
          <th>#</th>
          <th>event_type</th>
          <th>category</th>
          <th>outcome</th>
          <th>resource</th>
          <th>hash</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!(result.events || []).length">
          <td colspan="6" class="nx-empty">该 trace 在当前租户没有 audit_record</td>
        </tr>
        <tr v-for="row in result.events || []" :key="row.event_id">
          <td>{{ row.chain_index }}</td>
          <td class="nx-code">{{ row.event_type }}</td>
          <td>{{ row.category }}</td>
          <td><NxStatus :label="row.outcome" :tone="row.outcome === 'ACCEPTED' ? 'success' : 'danger'" /></td>
          <td class="nx-code">{{ row.resource_type }} {{ row.resource_id }}</td>
          <td class="nx-code">{{ String(row.event_hash || "").slice(0, 16) }}…</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { NxPageHeader } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { audit } from "../../api/client";
import { session } from "../../api/session";

const route = useRoute();
const traceId = ref(route.query.trace || session.lastTraceId || "");
const result = ref(null);
const error = ref("");

async function load() {
  error.value = "";
  result.value = null;
  if (!traceId.value.trim()) {
    error.value = "需要 trace_id";
    return;
  }
  try {
    result.value = await audit.byTrace(traceId.value.trim());
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}

onMounted(async () => {
  if (traceId.value) {
    await load();
  }
});
</script>
