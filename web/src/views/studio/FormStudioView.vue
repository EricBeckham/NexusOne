<template>
  <main v-if="form" class="form-studio-page">
    <NxPageHeader :title="form.name" description="分别管理草稿、已发布和运行版本；填报会写入台账并启动审批。">
      <template #actions>
        <div class="nx-row">
          <NxStatus :label="form.draft_status" tone="warning" />
          <NxStatus :label="`Running v${form.running_version || 0}`" tone="success" />
          <router-link class="ghost" to="/p3/forms">返回列表</router-link>
        </div>
      </template>
    </NxPageHeader>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <div class="nx-tabs">
      <button :class="{ active: tab === 'fill' }" type="button" data-testid="form-fill-tab" @click="tab = 'fill'">填报 / 启动审批</button>
      <button :class="{ active: tab === 'designer' }" type="button" @click="tab = 'designer'">设计壳</button>
      <button :class="{ active: tab === 'publish' }" type="button" data-testid="form-publish-tab" @click="tab = 'publish'">发布</button>
    </div>

    <NxResult
      v-if="tab === 'fill' && pathResult"
      compact
      title="已提交并启动审批"
      :description="submitDescription"
      data-testid="form-submit-result"
    >
      <template #next>
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
        <button class="ghost" type="button" data-testid="form-submit-again" @click="resetFill">再填一条</button>
      </template>
    </NxResult>
    <pre v-if="tab === 'fill' && pathResult && devNotes" class="nx-code" data-testid="form-submit-dev-notes">{{ JSON.stringify(pathResult, null, 2) }}</pre>

    <form v-else-if="tab === 'fill'" class="nx-card" style="max-width:720px;" @submit.prevent="submitFill">
      <NxEmpty v-if="!runningFields.length" compact title="暂无运行版本" description="先到发布页校验并发布草稿，才能填报并启动审批。">
        <template #next><button class="ghost" type="button" @click="tab = 'publish'">去发布</button></template>
      </NxEmpty>
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
    </form>

    <div v-else-if="tab === 'designer'" class="nx-designer">
      <div class="pane palette">
        <h3>绑定</h3>
        <p class="nx-muted">表单只绑定 ledger_code + field_code。物理列名会被 P3.FORM.0004 拒绝。</p>
      </div>
      <div class="pane canvas">
        <h3>{{ previewLayout ? '草稿布局预览' : '草稿字段顺序' }}</h3>
        <p v-if="previewLayout" class="nx-muted">只读布局预览；字段修改请前往模板编辑器。</p>
        <div class="preview" :class="{ 'layout-preview': previewLayout }" data-testid="studio-field-preview">
          <NxEmpty v-if="!form.draft_fields?.length" compact title="暂无草稿字段" description="此处按顺序展示草稿字段绑定；请先在表单草稿中添加字段。" />
          <p v-for="(field, index) in form.draft_fields || []" :key="field.id || field.code" :style="previewLayout?.[index]" data-testid="studio-preview-field">
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
        <ol class="steps" aria-label="表单发布顺序">
          <li><span class="step-number">1</span><span><strong>草稿</strong><small>配置字段与绑定</small></span></li>
          <li><span class="step-number">2</span><span><strong>校验</strong><small>{{ validation?.ok ? "已通过" : "检查草稿规则" }}</small></span></li>
          <li><span class="step-number">3</span><span><strong>发布</strong><small>生成不可变版本</small></span></li>
          <li><span class="step-number">4</span><span><strong>运行</strong><small>{{ form.running_version ? `版本 ${form.running_version}` : "暂无运行版本" }}</small></span></li>
        </ol>
        <div class="nx-row" style="margin-top:12px;">
          <button class="ghost" type="button" data-testid="form-validate" @click="onValidate">校验草稿</button>
          <button class="primary" type="button" data-testid="form-publish" @click="onPublish">发布表单</button>
          <button class="ghost" type="button" data-testid="form-publish-log" @click="onLog">查看发布日志</button>
        </div>
      </div>
      <div class="nx-card">
        <h3>校验结果</h3>
        <NxEmpty v-if="!validation" compact title="尚未校验" description="点击左侧「校验草稿」后，结果会显示在这里。" />
        <template v-else>
          <NxStatus :label="validation.ok ? 'PASS' : 'FAIL'" :tone="validation.ok ? 'success' : 'danger'" />
          <ul>
            <li v-for="(issue, idx) in validation.issues || []" :key="idx">{{ issue.kind }} · {{ issue.message }}</li>
          </ul>
          <p v-if="validation.ok" class="nx-muted">无错误。Publish 会切换 running 指针（G5 由服务端保证失败回滚）。</p>
        </template>
        <p v-if="devNotes && publishResult" class="nx-code" style="margin-top:12px;">{{ JSON.stringify(publishResult, null, 2) }}</p>
      </div>
    </div>
  </main>
  <NxErrorState v-else-if="error" :title="error" retry-label="重试" @retry="reload" />
  <NxSkeleton v-else variant="form-block" label="正在加载表单工作室" />
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxResult, NxSkeleton } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import { nxMessage } from "../../feedback/nxMessage";
import { devNotesEnabled } from "../../utils/devNotes";
import { p3 } from "../../api/p3";
import { p2 } from "../../api/client";
import { session } from "../../api/session";

const route = useRoute();
const devNotes = devNotesEnabled();
const form = ref(null);
const tab = ref(route.query.tab === "publish" ? "publish" : "fill");
const error = ref("");
const validation = ref(null);
const publishResult = ref(null);
const pathResult = ref(null);
const values = reactive({});
const persons = ref([]);
const assigneeRef = ref("");
const processKey = ref("nexusSequentialApproval");

const runningFields = computed(() => form.value?.running_fields || []);
const submitDescription = computed(() => {
  const note = pathResult.value?.note;
  if (note) return String(note);
  if (pathResult.value?.todo?.id) return "审批已创建待办，可继续打开待办或查看业务记录。";
  return "提交已完成。可查看业务记录，或再填一条。";
});

// Thin read-only consumer: zero-based 12-column grid, persisted as options.layout.
// Never invent placement for legacy, mixed or invalid metadata: retain list order.
const previewLayout = computed(() => {
  const fields = form.value?.draft_fields || [];
  if (!fields.length) return null;
  const layouts = fields.map((field) => field.options?.layout || field.layout);
  if (!layouts.every((layout) => layout && [layout.x, layout.y, layout.w, layout.h].every(Number.isInteger)
    && layout.x >= 0 && layout.y >= 0 && layout.w > 0 && layout.h > 0
    && layout.x + layout.w <= 12 && layout.y + layout.h <= 100)) return null;
  return layouts.map(({ x, y, w, h }) => ({ gridColumn: `${x + 1} / span ${w}`, gridRow: `${y + 1} / span ${h}` }));
});

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
    error.value = "";
    nxMessage.success("已提交并启动审批", {
      traceId: result.trace_id || "",
    });
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
    nxMessage.error(`提交没有完成：${err.code || ""} ${err.message}`.trim(), {
      traceId: err.traceId || err.trace_id || "",
    });
  }
}

function resetFill() {
  pathResult.value = null;
  for (const field of runningFields.value) {
    values[field.code] = "";
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
.form-studio-page { display: grid; gap: var(--nx-space-4); }
.form-studio-page :deep(.page-header) { margin-bottom: 0; }
.steps { list-style: none; display: grid; gap: var(--nx-space-2); margin: var(--nx-space-3) 0; padding: 0; }
.steps li { display: flex; align-items: center; gap: var(--nx-space-3); padding: var(--nx-space-2) var(--nx-space-3); border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); }
.steps li strong, .steps li small { display: block; }
.steps li small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.step-number { display: grid; place-items: center; width: 28px; height: 28px; flex: none; border-radius: 50%; color: var(--nx-product); background: var(--nx-product-light); font-weight: 700; }
.preview { border: 1px dashed var(--nx-border); border-radius: var(--nx-radius-sm); padding: var(--nx-space-3); }
.layout-preview { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); grid-auto-rows: minmax(40px, auto); gap: var(--nx-space-2); overflow: auto; }
.layout-preview p { margin: 0; padding: var(--nx-space-2); min-width: 0; overflow-wrap: anywhere; border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); background: var(--nx-surface); }
</style>
