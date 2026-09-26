<template>
  <div class="ledger-list">
    <NxPageHeader title="台账" description="创建租户应用及其业务台账，并维护字段与记录。">
      <template #actions>
        <!-- One Primary per area: while the create panel is open, its submit is the Primary. -->
        <button :class="showCreate ? 'ghost' : 'primary'" type="button" data-testid="ledger-create-toggle" @click="showCreate = !showCreate">
          {{ showCreate ? "收起创建" : "创建台账" }}
        </button>
      </template>
    </NxPageHeader>
    <NxSourceBanner body="T3-E 使用 Live M0-06：POST applications → POST applications/{id}/ledgers。业务模板入口继续保留，但不是本任务验收路径。" />
    <p class="quota" data-testid="ledger-quota">
      <span class="quota-label">自建台账额度</span>
      <strong>{{ quotaLabel }}</strong>
      <span v-if="quota" class="quota-bar" aria-hidden="true"><span :style="{ width: quotaPercent }" /></span>
    </p>
    <form v-if="showCreate" class="nx-card create-card" data-testid="ledger-create-form" @submit.prevent="onCreate">
      <h2>创建台账</h2>
      <label class="nx-field mode-field">
        <span>创建方式</span>
        <select v-model="creationMode" data-testid="ledger-create-mode">
          <option value="custom">自建应用 + 一条台账</option>
          <option value="template">请假 / 报销 / 出差模板</option>
        </select>
      </label>
      <div v-if="creationMode === 'custom'" class="create-section" data-testid="custom-ledger-fields">
        <div class="create-grid four">
          <label class="nx-field"><span>应用代码<em class="nx-req" aria-hidden="true">*</em></span><input v-model="custom.appCode" required data-testid="application-code" placeholder="crm" /></label>
          <label class="nx-field"><span>应用名称<em class="nx-req" aria-hidden="true">*</em></span><input v-model="custom.appName" required data-testid="application-name" placeholder="客户管理" /></label>
          <label class="nx-field"><span>台账代码<em class="nx-req" aria-hidden="true">*</em></span><input v-model="custom.ledgerCode" required data-testid="ledger-code" placeholder="customers" /></label>
          <label class="nx-field"><span>台账名称<em class="nx-req" aria-hidden="true">*</em></span><input v-model="custom.ledgerName" required data-testid="ledger-name" placeholder="客户台账" /></label>
        </div>
        <h3>首个字段</h3>
        <div class="create-grid four">
          <label class="nx-field"><span>字段代码<em class="nx-req" aria-hidden="true">*</em></span><input v-model="custom.fieldCode" required data-testid="ledger-field-code" /></label>
          <label class="nx-field"><span>字段名称<em class="nx-req" aria-hidden="true">*</em></span><input v-model="custom.fieldName" required data-testid="ledger-field-name" /></label>
          <label class="nx-field">
            <span>字段类型</span>
            <select v-model="custom.fieldType" data-testid="ledger-field-type">
              <option value="text">文本</option><option value="number">数字</option><option value="boolean">布尔</option><option value="date">日期时间</option>
            </select>
          </label>
          <label class="check"><input v-model="custom.required" type="checkbox" />必填字段</label>
        </div>
      </div>
      <div v-else class="create-section">
        <div class="create-grid">
          <label class="nx-field">
            <span>业务类型</span>
            <select v-model="draft.type_code" data-testid="business-type-select">
              <option v-for="item in types" :key="item.code" :value="item.code">
                {{ item.display_name }} ({{ item.code }})
              </option>
            </select>
          </label>
          <label class="check"><input v-model="draft.publish" type="checkbox" />开通后直接校验并发布表单</label>
        </div>
        <p v-if="selectedType" class="nx-muted">{{ selectedType.description }} 字段：{{ fieldSummary }}</p>
      </div>
      <div class="create-actions">
        <button class="nx-btn nx-btn--default" type="button" @click="showCreate = false">取消</button>
        <button class="nx-btn nx-btn--primary" type="submit" data-testid="ledger-create-submit">
          {{ creationMode === "custom" ? "创建应用和台账" : "开通此类业务" }}
        </button>
      </div>
    </form>
    <p v-if="!session.contextId" class="nx-error">还没有进入租户。请先登录并切换到要管理台账的租户。</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="error" class="nx-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="list-skeleton" data-testid="ledger-loading">
      <NxSkeleton v-for="n in 3" :key="n" variant="list-row" label="正在加载台账" />
    </div>
    <NxEmpty
      v-else-if="!rows.length && !error"
      class="nx-card"
      :title="session.contextId ? '还没有台账' : '需要先进入租户'"
      :description="session.contextId ? '台账保存业务记录。可以自建应用和台账，或用请假 / 报销 / 出差模板一键开通。' : '台账按当前租户读取，请先登录并切换租户。'"
      data-testid="ledger-empty"
    >
      <template v-if="session.contextId && !showCreate" #action>
        <button class="primary" type="button" @click="showCreate = true">创建台账</button>
      </template>
    </NxEmpty>
    <table v-else-if="rows.length" class="nx-table" data-testid="ledger-table">
      <thead>
        <tr>
          <th>台账</th>
          <th>代码</th>
          <th>应用</th>
          <th>来源</th>
          <th class="num">字段</th>
          <th>结构版本</th>
          <th>状态</th>
          <th class="actions-col"><span class="sr-only">操作</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id" data-testid="ledger-row">
          <td><router-link class="ledger-name" :to="`/p3/ledgers/${row.id}`">{{ row.name }}</router-link></td>
          <td class="nx-code cell-secondary">{{ row.code }}</td>
          <td>{{ applicationName(row.application_id) }}</td>
          <td class="cell-secondary">{{ sourceLabel(row.source) }}</td>
          <td class="num">{{ row.field_count ?? "暂无" }}</td>
          <td class="cell-secondary">{{ row.running_schema_version ? `v${row.running_schema_version}` : "暂无" }}</td>
          <td><NxStatusTag :status="row.status || ''" :label="ledgerStatus(row.status).label" :tone="ledgerStatus(row.status).tone" /></td>
          <td class="actions-col">
            <router-link class="nx-btn nx-btn--text open-link" :to="`/p3/ledgers/${row.id}`" :aria-label="`查看台账 ${row.name}`">
              查看<NxIcon name="chevronRight" :size="14" />
            </router-link>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { NxEmpty, NxPageHeader, NxSkeleton, NxStatusTag } from "../../components";
import NxIcon from "../../icons/NxIcon.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";
import { p1 } from "../../api/client";
import { session } from "../../api/session";

const router = useRouter();
const rows = ref([]);
const applications = ref([]);
const quota = ref(null);
const error = ref("");
const loading = ref(true);
const notice = ref("");
const showCreate = ref(false);
const creationMode = ref("custom");
const types = ref([]);
const draft = reactive({
  type_code: "leave",
  publish: true,
});
const custom = reactive({
  appCode: "",
  appName: "",
  ledgerCode: "",
  ledgerName: "",
  fieldCode: "title",
  fieldName: "标题",
  fieldType: "text",
  required: true,
});

const selectedType = computed(() => types.value.find((item) => item.code === draft.type_code));
const fieldSummary = computed(() =>
  (selectedType.value?.fields || []).map((field) => field.display_name || field.field_code).join(" / "),
);

const quotaLabel = computed(() => {
  if (!quota.value) {
    return "暂无额度信息";
  }
  return `已用 ${quota.value.used} / 上限 ${quota.value.limit}`;
});

const quotaPercent = computed(() => {
  const { used, limit } = quota.value || {};
  if (!limit) return "0%";
  return `${Math.min(100, Math.round((Number(used) / Number(limit)) * 100))}%`;
});

// R13: readable source and status; unknown codes stay visible as-is.
// LedgerStatus (DRAFT / READY / DISABLED); source is mapLedger's scope (TENANT_CUSTOM / APP_SHARED).
const LEDGER_STATUS = { READY: ["就绪", "success"], DRAFT: ["草稿", "neutral"], DISABLED: ["已停用", "neutral"] };
function ledgerStatus(status) {
  const [label, tone] = LEDGER_STATUS[String(status || "").toUpperCase()] || [status || "暂无", "neutral"];
  return { label, tone };
}
function sourceLabel(source) {
  return ({ TENANT_CUSTOM: "租户自建", APP_SHARED: "应用共享" })[String(source || "").toUpperCase()] || source || "暂无";
}

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
  const [ledgerBody, applicationBody, typeBody] = await Promise.all([
    p3.listLedgers(),
    p3.listApplications(),
    p3.listBusinessTypes().catch(() => ({ items: [] })),
  ]);
  rows.value = ledgerBody.items || [];
  applications.value = applicationBody.items || [];
  types.value = typeBody.items || [];
  if (types.value.length && !types.value.some((item) => item.code === draft.type_code)) {
    draft.type_code = types.value[0].code;
  }
  await loadQuota();
}

function applicationName(id) {
  const application = applications.value.find((item) => item.id === id);
  return application?.display_name || application?.app_code || "暂无";
}

onMounted(async () => {
  try {
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  } finally {
    loading.value = false;
  }
});

async function onCreate() {
  error.value = "";
  try {
    if (creationMode.value === "custom") {
      const application = await p3.createApplication({
        app_code: custom.appCode,
        display_name: custom.appName,
        kind: "CUSTOM",
      });
      const ledger = await p3.createLedger({
        application_id: application.id,
        ledger_code: custom.ledgerCode,
        display_name: custom.ledgerName,
        scope: "TENANT",
        fields: [{
          code: custom.fieldCode,
          name: custom.fieldName,
          type: custom.fieldType,
          required: custom.required,
        }],
      });
      notice.value = `已创建应用 ${application.display_name} 和台账 ${ledger.name}。`;
      showCreate.value = false;
      await reload();
      await router.push(`/p3/ledgers/${ledger.id}`);
      return;
    }
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

<style scoped>
.quota { display: flex; align-items: center; gap: var(--nx-space-3); margin: 0 0 var(--nx-space-4); font-size: var(--nx-font-size-sm); }
.quota-label { color: var(--nx-text-secondary); }
.quota strong { color: var(--nx-text-primary); font-weight: 500; }
.quota-bar { width: 120px; height: 6px; overflow: hidden; border-radius: 999px; background: var(--nx-divider, var(--nx-border)); }
.quota-bar span { display: block; height: 100%; background: var(--nx-product); }
.create-card { display: grid; gap: var(--nx-space-3); margin-bottom: var(--nx-space-4); }
.create-card h2 { margin: 0; font-size: var(--nx-font-size-md); }
.create-card h3 { margin: var(--nx-space-2) 0 0; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); font-weight: 500; }
.create-card p { margin: 0; }
.mode-field { max-width: 280px; margin-bottom: 0; }
.create-section { display: grid; gap: var(--nx-space-3); }
.create-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 var(--nx-space-4); align-items: end; }
.create-grid.four { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.create-grid .nx-field { margin-bottom: 0; }
.create-card select, .create-card input:not([type="checkbox"]) { box-sizing: border-box; width: 100%; min-height: var(--nx-control-height, 36px); }
.check { display: flex; align-items: center; gap: var(--nx-space-2); min-height: var(--nx-control-height, 36px); color: var(--nx-text-regular); font-size: var(--nx-font-size-sm); }
.create-actions { display: flex; justify-content: flex-end; gap: var(--nx-space-2); padding-top: var(--nx-space-3); border-top: 1px solid var(--nx-divider, var(--nx-border)); }
.notice { margin: 0 0 var(--nx-space-3); color: var(--nx-success); }
.list-skeleton { display: grid; gap: var(--nx-space-2); }
.ledger-name { color: var(--nx-text-primary); font-weight: 500; }
.ledger-name:hover { color: var(--nx-primary); }
.cell-secondary { color: var(--nx-text-secondary); }
.num { text-align: right; }
.actions-col { text-align: right; }
.open-link { display: inline-flex; align-items: center; gap: 2px; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
@media (max-width: 1000px) { .create-grid.four, .create-grid { grid-template-columns: 1fr; } }
</style>