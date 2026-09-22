<template>
  <div>
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>台账</h1>
        <p>P3 一台账一物理表。列表来自 <code>GET /api/v1/p3/ledgers</code>。需要已签发的 TenantContext。</p>
      </div>
    </div>
    <NxSourceBanner body="Live M0-06。空列表表示当前租户还没有台账，不是 MOCK。G18 业务走通未关闭。" />
    <div class="nx-card" style="margin-bottom:16px;">
      <div class="nx-muted">自建台账 Quota（P1 /api/v1/p1/tenants/{id}/quotas，非 ledger payload）</div>
      <strong>{{ quotaLabel }}</strong>
    </div>
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。请先登录并完成租户切换。</p>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <table class="nx-table" data-testid="ledger-table">
      <thead>
        <tr>
          <th>台账名</th>
          <th>代码</th>
          <th>来源</th>
          <th>字段数</th>
          <th>Schema Version</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!rows.length">
          <td colspan="6" class="nx-empty">{{ empty }}</td>
        </tr>
        <tr v-for="row in rows" :key="row.id">
          <td><router-link :to="`/p3/ledgers/${row.id}`">{{ row.name }}</router-link></td>
          <td class="nx-code">{{ row.code }}</td>
          <td>{{ row.source }}</td>
          <td>{{ row.field_count }}</td>
          <td>v{{ row.running_schema_version }}</td>
          <td><NxStatus :label="row.status" :tone="row.status === 'READY' || row.status === 'RUNNING' ? 'success' : 'warning'" /></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";
import { p1 } from "../../api/client";
import { session } from "../../api/session";

const rows = ref([]);
const quota = ref(null);
const error = ref("");

const quotaLabel = computed(() => {
  if (!quota.value) {
    return session.tenantId ? "未能读取 P1 quota（需要平台操作头）" : "—";
  }
  return `已用 ${quota.value.used} / 上限 ${quota.value.limit}`;
});

const empty = computed(() =>
  session.contextId ? "当前租户没有台账" : "需要 TenantContext",
);

onMounted(async () => {
  try {
    if (session.tenantId) {
      try {
        const quotas = await p1.quotas(session.tenantId);
        const custom = (quotas || []).find((item) => (item.metric || item.Metric) === "CUSTOM_LEDGER");
        if (custom) {
          quota.value = { used: custom.used, limit: custom.limit };
        }
      } catch {
        quota.value = null;
      }
    }
    const body = await p3.listLedgers();
    rows.value = body.items || [];
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});
</script>
