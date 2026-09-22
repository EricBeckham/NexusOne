<template>
  <div>
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>台账</h1>
        <p>P3 一台账一物理表。创建走 <code>POST /api/v1/p3/applications/{id}/ledgers</code>，列表走 <code>GET /api/v1/p3/ledgers</code>。</p>
      </div>
      <button class="primary" type="button" data-testid="ledger-create-toggle" @click="showCreate = !showCreate">
        {{ showCreate ? "收起创建" : "创建台账" }}
      </button>
    </div>
    <NxSourceBanner body="Live M0-06。空列表表示当前租户还没有台账，不是 MOCK。本页可创建应用与台账；G18 业务走通仍待 Eric 浏览器复走。" />
    <div class="nx-card" style="margin-bottom:16px;">
      <div class="nx-muted">自建台账 Quota（P1 /api/v1/p1/tenants/{id}/quotas，非 ledger payload）</div>
      <strong>{{ quotaLabel }}</strong>
    </div>
    <form v-if="showCreate" class="nx-card" style="margin-bottom:16px;" data-testid="ledger-create-form" @submit.prevent="onCreate">
      <h3>创建台账</h3>
      <p class="nx-muted">没有应用时先 POST /api/v1/p3/applications。默认带 title / amount / remark / approval_status 字段，便于 G18 submit-start。</p>
      <div class="nx-row">
        <div class="nx-field">
          <label>应用</label>
          <select v-model="draft.application_id">
            <option value="">新建应用</option>
            <option v-for="app in apps" :key="app.id" :value="app.id">{{ app.display_name }} ({{ app.app_code }})</option>
          </select>
        </div>
        <div v-if="!draft.application_id" class="nx-field">
          <label>新应用编码</label>
          <input v-model="draft.app_code" placeholder="hr" required />
        </div>
        <div v-if="!draft.application_id" class="nx-field">
          <label>新应用名称</label>
          <input v-model="draft.app_name" placeholder="人事" />
        </div>
      </div>
      <div class="nx-row">
        <div class="nx-field"><label>台账编码</label><input v-model="draft.ledger_code" placeholder="leave" required /></div>
        <div class="nx-field"><label>台账名称</label><input v-model="draft.display_name" placeholder="请假" required /></div>
      </div>
      <button class="primary" type="submit" data-testid="ledger-create-submit">创建</button>
    </form>
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
import { computed, onMounted, reactive, ref } from "vue";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";
import { p1 } from "../../api/client";
import { session } from "../../api/session";

const rows = ref([]);
const apps = ref([]);
const quota = ref(null);
const error = ref("");
const showCreate = ref(false);
const draft = reactive({
  application_id: "",
  app_code: "app",
  app_name: "业务应用",
  ledger_code: "leave",
  display_name: "请假台账",
});

const DEFAULT_FIELDS = [
  { field_code: "title", display_name: "标题", field_type: "TEXT", required: true },
  { field_code: "amount", display_name: "数量", field_type: "NUMERIC", required: false },
  { field_code: "remark", display_name: "备注", field_type: "TEXT", required: false },
  { field_code: "approval_status", display_name: "审批状态", field_type: "TEXT", required: false },
];

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
  const [ledgerBody, appBody] = await Promise.all([p3.listLedgers(), p3.listApplications()]);
  rows.value = ledgerBody.items || [];
  apps.value = appBody.items || [];
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
    let applicationId = draft.application_id;
    if (!applicationId) {
      const created = await p3.createApplication({
        app_code: draft.app_code,
        display_name: draft.app_name || draft.app_code,
      });
      applicationId = created.id;
    }
    await p3.createLedger({
      application_id: applicationId,
      ledger_code: draft.ledger_code,
      display_name: draft.display_name,
      fields: DEFAULT_FIELDS,
    });
    showCreate.value = false;
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}
</script>
