<template>
  <div class="form-list">
    <NxPageHeader title="表单" description="创建、校验并发布表单；运行版本可直接填报并启动审批。">
      <template #actions>
        <!-- One Primary per area: while the create panel is open, its own submit is the Primary. -->
        <button :class="showCreate ? 'ghost' : 'primary'" type="button" data-testid="form-create-toggle" @click="showCreate = !showCreate">
          {{ showCreate ? "收起创建" : "创建表单" }}
        </button>
      </template>
    </NxPageHeader>
    <NxSourceBanner body="Live GET /api/v1/p3/forms。创建绑定所选台账的 field_code。G5 由模块测试覆盖。" />
    <form v-if="showCreate" class="nx-card create-card" data-testid="form-create-form" @submit.prevent="onCreate">
      <h2>创建表单</h2>
      <p class="nx-muted">表单字段来自所选台账的业务字段（审批状态字段除外）。创建后为草稿，校验通过即可发布。</p>
      <div class="create-grid">
        <label class="nx-field">
          <span>绑定台账<em class="nx-req" aria-hidden="true">*</em></span>
          <select v-model="draft.ledger_id" required data-testid="form-ledger-select">
            <option value="" disabled>选择台账</option>
            <option v-for="ledger in ledgers" :key="ledger.id" :value="ledger.id">
              {{ ledger.name }} ({{ ledger.code }})
            </option>
          </select>
        </label>
        <label class="nx-field"><span>表单编码<em class="nx-req" aria-hidden="true">*</em></span><input v-model="draft.form_code" placeholder="leave_form" required data-testid="form-code" /></label>
        <label class="nx-field"><span>表单名称<em class="nx-req" aria-hidden="true">*</em></span><input v-model="draft.name" placeholder="请假单" required data-testid="form-name" /></label>
      </div>
      <p v-if="!ledgers.length" class="nx-field-error">当前租户没有台账，请先去<router-link to="/p3/ledgers">台账</router-link>页创建。</p>
      <div class="create-actions">
        <button class="nx-btn nx-btn--default" type="button" @click="showCreate = false">取消</button>
        <button class="nx-btn nx-btn--primary" type="submit" data-testid="form-create-submit" :disabled="!ledgers.length">创建草稿</button>
      </div>
    </form>
    <p v-if="!session.contextId" class="nx-error">还没有进入租户。请先登录并切换到要管理表单的租户。</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="error" class="nx-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="list-skeleton" data-testid="form-loading">
      <NxSkeleton v-for="n in 3" :key="n" variant="list-row" label="正在加载表单" />
    </div>
    <NxEmpty
      v-else-if="!rows.length && !error"
      class="nx-card"
      title="还没有表单"
      :description="ledgers.length ? '表单绑定台账字段，发布后即可填报并启动审批。' : '表单需要绑定台账，请先创建台账再创建表单。'"
      data-testid="form-empty"
    >
      <template v-if="!showCreate" #action>
        <button v-if="ledgers.length" class="primary" type="button" @click="showCreate = true">创建表单</button>
        <router-link v-else class="primary" to="/p3/ledgers">去创建台账</router-link>
      </template>
    </NxEmpty>
    <table v-else-if="rows.length" class="nx-table" data-testid="form-table">
      <thead>
        <tr>
          <th>表单</th>
          <th>绑定台账</th>
          <th>草稿状态</th>
          <th>运行版本</th>
          <th class="actions-col">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td><router-link class="form-name" :to="`/p3/forms/${row.id}`">{{ row.name }}</router-link></td>
          <td class="cell-secondary">{{ row.ledger_name || "暂无" }}</td>
          <td><NxStatusTag :status="row.draft_status || ''" /></td>
          <td><NxStatus :label="row.running_version ? `运行 v${row.running_version}` : '未发布'" :tone="row.running_version ? 'success' : 'neutral'" /></td>
          <td class="row-actions">
            <button
              v-if="!row.running_version"
              class="nx-btn nx-btn--text"
              type="button"
              data-testid="form-quick-publish"
              @click="validateAndPublish(row)"
            >校验并发布</button>
            <router-link class="nx-btn nx-btn--text" :to="`/p3/forms/${row.id}?tab=publish`">发布设置</router-link>
            <router-link class="nx-btn nx-btn--text" :to="`/p3/forms/${row.id}?tab=fill`">填报</router-link>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { NxEmpty, NxPageHeader, NxSkeleton, NxStatusTag } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";

const router = useRouter();
const rows = ref([]);
const ledgers = ref([]);
const apps = ref([]);
const error = ref("");
const loading = ref(true);
const notice = ref("");
const showCreate = ref(false);
const draft = reactive({
  ledger_id: "",
  form_code: "leave_form",
  name: "请假单",
});

function widgetType(field) {
  const type = String(field.type || "").toLowerCase();
  if (type === "number" || type === "numeric" || type === "integer" || type === "money") return "NUMBER";
  if (type === "boolean") return "BOOLEAN";
  if (type === "date" || type === "timestamptz") return "DATE";
  if (type === "longtext" || type === "textarea") return "TEXTAREA";
  if (type === "person") return "PERSON";
  return "TEXT";
}

async function reload() {
  const [forms, ledgerBody, appBody] = await Promise.all([
    p3.listForms(),
    p3.listLedgers(),
    p3.listApplications(),
  ]);
  rows.value = forms.items || [];
  ledgers.value = ledgerBody.items || [];
  apps.value = appBody.items || [];
  if (ledgers.value.length && !draft.ledger_id) {
    draft.ledger_id = ledgers.value[0].id;
  }
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
    const ledger = ledgers.value.find((item) => item.id === draft.ledger_id);
    if (!ledger) {
      throw new Error("请选择台账");
    }
    const detail = await p3.getLedger(ledger.id);
    const app = apps.value.find((item) => item.id === (detail.application_id || ledger.application_id));
    const fields = (detail.fields || []).filter((field) => field.code && field.code !== "approval_status");
    if (!fields.length) {
      throw new Error("台账没有可绑定字段。请先在台账详情里追加字段。");
    }
    const bindings = fields.map((field, index) => ({
      widget_code: field.code,
      ledger_code: ledger.code,
      field_code: field.code,
      required: Boolean(field.required),
      widget_type: widgetType(field),
      ordinal: index,
    }));
    const created = await p3.createForm({
      app_code: app?.app_code || ledger.code,
      form_code: draft.form_code,
      name: draft.name,
      bindings,
    });
    router.push(`/p3/forms/${created.id}?tab=publish`);
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}

async function validateAndPublish(row) {
  error.value = "";
  notice.value = "";
  try {
    const validation = await p3.validateForm(row.id);
    if (!validation.ok) {
      const message = (validation.issues || []).map((item) => item.message).join("；") || "校验失败";
      throw new Error(message);
    }
    const published = await p3.publishForm(row.id);
    await reload();
    notice.value = `${row.name} 已发布为运行版本 v${published.running_version || 1}，列表已刷新。`;
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}
</script>

<style scoped>
.create-card { display: grid; gap: var(--nx-space-3); margin-bottom: var(--nx-space-4); }
.create-card h2 { margin: 0; font-size: var(--nx-font-size-md); }
.create-card > p { margin: 0; }
.create-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 var(--nx-space-4); }
.create-grid .nx-field { margin-bottom: 0; }
.create-grid select, .create-grid input { box-sizing: border-box; width: 100%; min-height: var(--nx-control-height, 36px); }
.create-actions { display: flex; justify-content: flex-end; gap: var(--nx-space-2); padding-top: var(--nx-space-3); border-top: 1px solid var(--nx-divider, var(--nx-border)); }
.notice { margin: 0 0 var(--nx-space-3); color: var(--nx-success); }
.list-skeleton { display: grid; gap: var(--nx-space-2); }
.form-name { color: var(--nx-text-primary); font-weight: 500; }
.form-name:hover { color: var(--nx-primary); }
.cell-secondary { color: var(--nx-text-secondary); }
.actions-col { text-align: right; }
.row-actions { display: flex; justify-content: flex-end; gap: var(--nx-space-1); }
@media (max-width: 900px) { .create-grid { grid-template-columns: 1fr; } }
</style>