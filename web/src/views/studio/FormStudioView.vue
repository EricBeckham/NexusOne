<template>
  <div v-if="form">
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>{{ form.name }}</h1>
        <p>
          Draft / Published / Running 必须分开。G18 填报走
          <code>POST /api/v1/p3/path/submit-start</code>
          （物理台账行 + Flowable），不是只写 form_submission。
        </p>
      </div>
      <div class="nx-row">
        <NxStatus :label="form.draft_status" tone="warning" />
        <NxStatus :label="`Running v${form.running_version || 0}`" tone="success" />
        <router-link class="ghost" to="/p3/forms">返回列表</router-link>
      </div>
    </div>
    <NxSourceBanner body="Live M0-07 validate / publish。填报默认 submit-start，并带 Idempotency-Key（G3 重复提交不写第二行）。G5 回滚由服务端事务覆盖。" />
    <p v-if="error" class="nx-error">{{ error }}</p>
    <p v-if="notice" class="nx-muted">{{ notice }}</p>
    <div class="nx-tabs">
      <button :class="{ active: tab === 'fill' }" type="button" data-testid="form-fill-tab" @click="tab = 'fill'">填报 / 启动审批</button>
      <button :class="{ active: tab === 'designer' }" type="button" @click="tab = 'designer'">设计壳</button>
      <button :class="{ active: tab === 'publish' }" type="button" data-testid="form-publish-tab" @click="tab = 'publish'">发布</button>
    </div>

    <form v-if="tab === 'fill'" class="nx-card" style="max-width:720px;" @submit.prevent="submitFill">
      <p v-if="!runningFields.length" class="nx-muted">没有 running 快照。先 Validate / Publish，或当前租户还没有运行版本。</p>
      <div v-for="field in runningFields" :key="field.id || field.code" class="nx-field">
        <label>{{ field.name }}</label>
        <textarea v-if="field.type === 'longtext' || field.type === 'textarea'" v-model="values[field.code]" rows="3" />
        <input
          v-else
          v-model="values[field.code]"
          :type="inputType(field.type)"
          :required="field.required"
        />
      </div>
      <div class="nx-row">
        <div class="nx-field">
          <label>审批人（PERSON）</label>
          <select v-model="assigneeRef" data-testid="form-assignee">
            <option value="">当前登录人</option>
            <option v-for="person in persons" :key="person.id" :value="person.id">
              {{ person.display_name || person.displayName || person.email }} ({{ person.id }})
            </option>
          </select>
        </div>
        <div class="nx-field">
          <label>process_key</label>
          <input v-model="processKey" class="nx-code" />
        </div>
      </div>
      <button class="primary" type="submit" data-testid="form-fill-submit" :disabled="!runningFields.length">
        提交并启动审批
      </button>
      <div v-if="pathResult" class="nx-row" style="margin-top:12px;" data-testid="approval-start-links">
        <router-link
          v-if="pathResult.todo?.id"
          class="primary"
          :to="`/p3/todos/${pathResult.todo.id}?box=inbox`"
          data-testid="approval-open-todo"
        >打开待办</router-link>
        <router-link
          v-if="pathResult.ledger_id && pathResult.record_id"
          class="ghost"
          :to="`/p3/ledgers/${pathResult.ledger_id}/records/${pathResult.record_id}`"
          data-testid="approval-open-record"
        >查看业务记录</router-link>
      </div>
      <pre v-if="pathResult" class="nx-code" style="margin-top:12px;">{{ JSON.stringify(pathResult, null, 2) }}</pre>
    </form>

    <div v-else-if="tab === 'designer'" class="nx-designer">
      <div class="pane palette">
        <h3>绑定</h3>
        <p class="nx-muted">表单只绑定 ledger_code + field_code。物理列名会被 P3.FORM.0004 拒绝。</p>
      </div>
      <div class="pane canvas">
        <h3>Canvas · Draft bindings</h3>
        <div class="preview">
          <p v-for="field in form.draft_fields || []" :key="field.id || field.code">
            <strong>{{ field.name }}</strong>
            <span class="nx-muted"> {{ field.code }} · {{ field.type }}</span>
          </p>
        </div>
      </div>
      <div class="pane props">
        <p>校验：Validation Rule / Business Rule / Field Rule 分开展示（M0-07）。</p>
        <p class="nx-muted">不能边编辑边修改 Running。</p>
      </div>
    </div>

    <div v-else class="nx-grid" style="grid-template-columns: 1fr 1fr;">
      <div class="nx-card">
        <h3>发布中心</h3>
        <ol class="steps">
          <li>Draft</li>
          <li>Validate</li>
          <li>Publish</li>
          <li>Running</li>
        </ol>
        <div class="nx-row" style="margin-top:12px;">
          <button class="ghost" type="button" data-testid="form-validate" @click="onValidate">Validate</button>
          <button class="primary" type="button" data-testid="form-publish" @click="onPublish">Publish</button>
          <button class="ghost" type="button" data-testid="form-publish-log" @click="onLog">查看 publish-log</button>
        </div>
      </div>
      <div class="nx-card">
        <h3>Validation Result</h3>
        <p v-if="!validation">尚未校验</p>
        <template v-else>
          <NxStatus :label="validation.ok ? 'PASS' : 'FAIL'" :tone="validation.ok ? 'success' : 'danger'" />
          <ul>
            <li v-for="(issue, idx) in validation.issues || []" :key="idx">{{ issue.kind }} · {{ issue.message }}</li>
          </ul>
          <p v-if="validation.ok" class="nx-muted">无错误。Publish 会切换 running 指针（G5 由服务端保证失败回滚）。</p>
        </template>
        <p v-if="publishResult" class="nx-code" style="margin-top:12px;">{{ JSON.stringify(publishResult, null, 2) }}</p>
      </div>
    </div>
  </div>
  <p v-else-if="error" class="nx-error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";
import { p2 } from "../../api/client";
import { session } from "../../api/session";

const route = useRoute();
const form = ref(null);
const tab = ref(route.query.tab === "publish" ? "publish" : "fill");
const error = ref("");
const notice = ref("");
const validation = ref(null);
const publishResult = ref(null);
const pathResult = ref(null);
const values = reactive({});
const persons = ref([]);
const assigneeRef = ref("");
const processKey = ref("nexusSequentialApproval");

const runningFields = computed(() => form.value?.running_fields || []);

function inputType(type) {
  const normalized = String(type || "").toLowerCase();
  if (normalized === "number" || normalized === "numeric" || normalized === "integer" || normalized === "money") {
    return "number";
  }
  if (normalized === "date" || normalized === "timestamptz") {
    return "date";
  }
  return "text";
}

async function reload() {
  form.value = await p3.getForm(route.params.id);
  for (const field of form.value.running_fields || []) {
    if (values[field.code] === undefined) {
      values[field.code] = "";
    }
  }
}

onMounted(async () => {
  try {
    await reload();
    try {
      persons.value = (await p2.persons()) || [];
    } catch {
      persons.value = [];
    }
    if (!assigneeRef.value && session.personId) {
      assigneeRef.value = session.personId;
    }
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function submitFill() {
  try {
    const payload = {};
    for (const field of runningFields.value) {
      payload[field.code] = values[field.code];
    }
    const result = await p3.submitStart(route.params.id, {
      values: payload,
      process_key: processKey.value,
      assignee_type: "PERSON",
      assignee_ref: assigneeRef.value || session.personId,
    });
    pathResult.value = result;
    notice.value = `submit-start 完成。record ${result.record_id || "?"} · todo ${result.todo?.id || "?"} · trace ${result.trace_id || ""}。${result.note || ""}`;
    error.value = "";
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}

async function onValidate() {
  validation.value = await p3.validateForm(route.params.id);
  await reload();
}

async function onPublish() {
  try {
    publishResult.value = await p3.publishForm(route.params.id);
    await reload();
    error.value = "";
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
    publishResult.value = err.body || { message: err.message };
  }
}

async function onLog() {
  publishResult.value = await p3.simulatePublishFailure(route.params.id);
}
</script>

<style scoped>
.steps { margin: 8px 0 0 18px; }
.preview { border: 1px dashed var(--nx-border); border-radius: 8px; padding: 12px; }
</style>
