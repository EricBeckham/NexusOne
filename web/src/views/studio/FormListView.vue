<template>
  <div>
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>表单</h1>
        <p>创建 DRAFT，再 Validate / Publish。填报走 <code>/api/v1/p3/path/submit-start</code>，不是只写 form_submission。</p>
      </div>
      <button class="primary" type="button" data-testid="form-create-toggle" @click="showCreate = !showCreate">
        {{ showCreate ? "收起创建" : "创建表单" }}
      </button>
    </div>
    <NxSourceBanner body="Live GET /api/v1/p3/forms。创建绑定所选台账的 field_code。G5 由模块测试覆盖；本页不关闭 G18。" />
    <form v-if="showCreate" class="nx-card" style="margin-bottom:16px;" data-testid="form-create-form" @submit.prevent="onCreate">
      <h3>创建表单</h3>
      <p class="nx-muted">绑定所选台账的业务字段（跳过 approval_status）。Validate 要求台账字段已存在。</p>
      <div class="nx-row">
        <div class="nx-field">
          <label>绑定台账</label>
          <select v-model="draft.ledger_id" required>
            <option value="" disabled>选择台账</option>
            <option v-for="ledger in ledgers" :key="ledger.id" :value="ledger.id">
              {{ ledger.name }} ({{ ledger.code }})
            </option>
          </select>
        </div>
        <div class="nx-field"><label>表单编码</label><input v-model="draft.form_code" placeholder="leave_form" required /></div>
        <div class="nx-field"><label>表单名称</label><input v-model="draft.name" placeholder="请假单" required /></div>
      </div>
      <p v-if="!ledgers.length" class="nx-error">当前租户没有台账，请先去台账页创建。</p>
      <button class="primary" type="submit" data-testid="form-create-submit" :disabled="!ledgers.length">创建 DRAFT</button>
    </form>
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。请先登录并完成租户切换。</p>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <table class="nx-table" data-testid="form-table">
      <thead>
        <tr>
          <th>表单</th>
          <th>绑定台账码</th>
          <th>Draft</th>
          <th>Running</th>
          <th>动作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!rows.length">
          <td colspan="5" class="nx-empty">暂无表单。用上方「创建表单」写入 M0-07 DRAFT。</td>
        </tr>
        <tr v-for="row in rows" :key="row.id">
          <td><router-link :to="`/p3/forms/${row.id}`">{{ row.name }}</router-link></td>
          <td class="nx-code">{{ row.ledger_name || "—" }}</td>
          <td><NxStatus :label="row.draft_status" :tone="row.draft_status === 'VALIDATED' ? 'success' : 'warning'" /></td>
          <td>v{{ row.running_version || 0 }}</td>
          <td class="nx-row">
            <router-link class="ghost" :to="`/p3/forms/${row.id}?tab=publish`">Validate / Publish</router-link>
            <router-link class="ghost" :to="`/p3/forms/${row.id}?tab=fill`">填报</router-link>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";
import { session } from "../../api/session";

const router = useRouter();
const rows = ref([]);
const ledgers = ref([]);
const apps = ref([]);
const error = ref("");
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

onMounted(async () => {
  try {
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
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
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
</script>
