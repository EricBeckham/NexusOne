<template>
  <div v-if="form">
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>{{ form.name }}</h1>
        <p>Draft / Published / Running 必须分开。本壳不改真实 Running 版本。</p>
      </div>
      <div class="nx-row">
        <NxStatus :label="form.draft_status" tone="warning" />
        <NxStatus :label="`Running v${form.running_version}`" tone="success" />
        <router-link class="ghost" to="/p3/forms">返回列表</router-link>
      </div>
    </div>
    <NxSourceBanner body="填报 / 发布是 MOCK 壳。校验失败不污染 Running 只在夹具里演示。G5 / G18 表单段未关闭。" />
    <p v-if="error" class="nx-error">{{ error }}</p>
    <p v-if="notice" class="nx-muted">{{ notice }}</p>
    <div class="nx-tabs">
      <button :class="{ active: tab === 'fill' }" type="button" data-testid="form-fill-tab" @click="tab = 'fill'">填报</button>
      <button :class="{ active: tab === 'designer' }" type="button" @click="tab = 'designer'">设计壳</button>
      <button :class="{ active: tab === 'publish' }" type="button" data-testid="form-publish-tab" @click="tab = 'publish'">发布</button>
    </div>

    <form v-if="tab === 'fill'" class="nx-card" style="max-width:720px;" @submit.prevent="submitFill">
      <div v-for="field in runningFields" :key="field.id" class="nx-field">
        <label>{{ field.name }}</label>
        <select v-if="field.type === 'enum'" v-model="values[field.code]">
          <option disabled value="">请选择</option>
          <option v-for="opt in field.options || []" :key="opt" :value="opt">{{ opt }}</option>
        </select>
        <textarea v-else-if="field.type === 'longtext'" v-model="values[field.code]" rows="3" />
        <input
          v-else
          v-model="values[field.code]"
          :type="inputType(field.type)"
          :required="field.required"
        />
      </div>
      <button class="primary" type="submit" data-testid="form-fill-submit">提交填报（MOCK）</button>
    </form>

    <div v-else-if="tab === 'designer'" class="nx-designer">
      <div class="pane palette">
        <h3>基础</h3>
        <p v-for="item in ['单行文本', '金额', '日期', '人员', '部门', '枚举']" :key="item" class="nx-chip-btn">{{ item }}</p>
        <h3>布局</h3>
        <p class="nx-muted">分组 / 两列（M0 仅展示）</p>
      </div>
      <div class="pane canvas">
        <h3>Canvas · Desktop</h3>
        <div class="preview">
          <p v-for="field in form.draft_fields || []" :key="field.id">
            <strong>{{ field.name }}</strong>
            <span class="nx-muted"> {{ field.type }}</span>
          </p>
        </div>
      </div>
      <div class="pane props">
        <div class="nx-tabs" style="border:0;margin:0 0 8px;">
          <span class="nx-muted">属性 / 校验 / 权限</span>
        </div>
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
          <button class="ghost" type="button" @click="onValidate">Validate</button>
          <button class="primary" type="button" data-testid="form-publish" @click="onPublish">Publish</button>
          <button class="danger" type="button" @click="onFail">模拟失败回滚</button>
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
          <p v-if="validation.ok" class="nx-muted">无错误。Publish 仍是 MOCK，不写物理 Schema。</p>
        </template>
        <p v-if="publishResult" class="nx-code" style="margin-top:12px;">{{ JSON.stringify(publishResult, null, 2) }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { p3 } from "../../api/p3";

const route = useRoute();
const form = ref(null);
const tab = ref(route.query.tab === "publish" ? "publish" : "fill");
const error = ref("");
const notice = ref("");
const validation = ref(null);
const publishResult = ref(null);
const values = reactive({
  title: "",
  amount: "",
  applicant: "",
  department: "",
  claim_date: "",
  category: "",
  remark: "",
});

const runningFields = computed(() => form.value?.running_fields || []);

function inputType(type) {
  if (type === "money" || type === "number") return "number";
  if (type === "date") return "date";
  return "text";
}

async function reload() {
  form.value = await p3.getForm(route.params.id);
  validation.value = form.value.last_validation || null;
}

onMounted(async () => {
  try {
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function submitFill() {
  try {
    const result = await p3.fillForm(route.params.id, { ...values });
    notice.value = `已写入 MOCK 记录 ${result.record?.id}，并生成待办 ${result.todo?.id}。未发起真实流程。`;
  } catch (err) {
    error.value = err.message;
  }
}

async function onValidate() {
  validation.value = await p3.validateForm(route.params.id);
  await reload();
}

async function onPublish() {
  publishResult.value = await p3.publishForm(route.params.id);
  await reload();
}

async function onFail() {
  publishResult.value = await p3.simulatePublishFailure(route.params.id);
  await reload();
}
</script>

<style scoped>
.steps { margin: 8px 0 0 18px; }
.preview { border: 1px dashed var(--nx-border); border-radius: 8px; padding: 12px; }
</style>
