<template>
  <div>
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>台账</h1>
        <p>P3 一台账一物理表。G7 三类业务走 <code>POST /api/v1/p3/business-types/{code}/provision</code>，列表走 <code>GET /api/v1/p3/ledgers</code>。</p>
      </div>
      <button class="primary" type="button" data-testid="ledger-create-toggle" @click="showCreate = !showCreate">
        {{ showCreate ? "收起创建" : "创建台账" }}
      </button>
    </div>
    <NxSourceBanner body="Live M0-06 + G7 catalog。空列表表示当前租户还没有台账，不是 MOCK。开通请假 / 报销 / 出差会同时建台账和表单。" />
    <div class="nx-card" style="margin-bottom:16px;">
      <div class="nx-muted">自建台账 Quota（P1 /api/v1/p1/tenants/{id}/quotas，非 ledger payload）</div>
      <strong>{{ quotaLabel }}</strong>
    </div>
    <form v-if="showCreate" class="nx-card" style="margin-bottom:16px;" data-testid="ledger-create-form" @submit.prevent="onCreate">
      <h3>创建台账</h3>
      <p class="nx-muted">G7 三类真实业务（请假 / 报销 / 出差）走 catalog provision：台账 + 表单 + 可选 Publish。也可继续手建应用与台账。</p>
      <div class="nx-row">
        <div class="nx-field">
          <label>业务类型</label>
          <select v-model="draft.type_code" data-testid="business-type-select">
            <option v-for="item in types" :key="item.code" :value="item.code">
              {{ item.display_name }} ({{ item.code }})
            </option>
          </select>
        </div>
        <label class="nx-field" style="flex-direction:row;align-items:center;gap:8px;margin-top:22px;">
          <input v-model="draft.publish" type="checkbox" />
          Validate + Publish
        </label>
      </div>
      <p v-if="selectedType" class="nx-muted">{{ selectedType.description }} 字段：{{ fieldSummary }}</p>
      <button class="primary" type="submit" data-testid="ledger-create-submit">开通此类业务</button>
    </form>
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。请先登录并完成租户切换。</p>
    <p v-if="notice" class="nx-muted">{{ notice }}</p>
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
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";
import { p1 } from "../../api/client";
import { session } from "../../api/session";

const router = useRouter();
const rows = ref([]);
const quota = ref(null);
const error = ref("");
const notice = ref("");
const showCreate = ref(false);
const types = ref([]);
const draft = reactive({
  type_code: "leave",
  publish: true,
});

const selectedType = computed(() => types.value.find((item) => item.code === draft.type_code));
const fieldSummary = computed(() =>
  (selectedType.value?.fields || []).map((field) => field.display_name || field.field_code).join(" / "),
);

const quotaLabel = computed(() => {
  if (!quota.value) {
    return session.tenantId ? "未能读取 P1 quota（需要平台操作头）" : "—";
  }
  return `已用 ${quota.value.used} / 上限 ${quota.value.limit}`;
});

const empty = computed(() =>
  session.contextId ? "当前租户没有台账。用上方「创建台账」写入 M0-06。" : "需要 TenantContext",
);

async function loadQuota() {
  if (!session.tenantId) {
    return;
  }
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

async function reload() {
  const [ledgerBody, typeBody] = await Promise.all([
    p3.listLedgers(),
    p3.listBusinessTypes().catch(() => ({ items: [] })),
  ]);
  rows.value = ledgerBody.items || [];
  types.value = typeBody.items || [];
  if (types.value.length && !types.value.some((item) => item.code === draft.type_code)) {
    draft.type_code = types.value[0].code;
  }
  await loadQuota();
}

onMounted(async () => {
  try {
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function onCreate() {
  error.value = "";
  try {
    const created = await p3.provisionBusinessType(draft.type_code, { publish: Boolean(draft.publish) });
    showCreate.value = false;
    notice.value = `已开通 ${created.display_name || draft.type_code}。台账 ${created.ledger_code || "?"} · 表单 ${created.form_code || "?"}。`;
    await reload();
    if (created.form_id) {
      await router.push(`/p3/forms/${created.form_id}?tab=publish`);
    }
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}
</script>
