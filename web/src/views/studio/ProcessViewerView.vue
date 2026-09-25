<template>
  <div>
    <NxPageHeader
      eyebrow="星构"
      title="流程设计器"
      description="编辑租户内流程草稿；已发布目录仅供查看，改动不会部署或发布。"
    >
      <template #actions>
        <button type="button" class="ghost" data-testid="process-new" @click="newDraft">新建草稿</button>
        <button v-if="!isEditing" type="button" class="ghost" data-testid="process-edit-copy" :disabled="!definition" @click="editAsDraft">创建可编辑草稿</button>
        <button
          v-if="isPersistedDraft"
          type="button"
          class="ghost"
          data-testid="process-validate"
          :disabled="dirty || saving || validating"
          @click="validateDraft"
        >{{ validating ? "校验中…" : "校验草稿" }}</button>
        <button
          v-if="canPublish"
          type="button"
          class="primary"
          data-testid="process-publish"
          :disabled="publishing"
          @click="publishConfirm = true"
        >发布流程</button>
        <button v-if="isEditing" type="button" class="primary" data-testid="process-save" :disabled="saving" @click="saveDraft">
          {{ saving ? "保存中…" : "保存草稿" }}
        </button>
      </template>
    </NxPageHeader>
    <p class="nx-muted later-note">顺序审批：审批节点按从上到下的顺序依次流转，可增删、拖拽排序；不支持分支与并行。发布前须保存并通过校验。</p>
    <NxErrorState
      v-if="error && signInRequired"
      title="需要登录租户账号"
      description="流程与草稿按租户读取：当前没有登录的租户会话。请先以租户账号登录（演示：林晓），再回到流程设计器。"
      :trace-id="error.traceId || ''"
      data-testid="process-signin-required"
    >
      <template #action>
        <router-link class="primary" to="/login" data-testid="process-signin-link">去登录</router-link>
      </template>
    </NxErrorState>
    <NxErrorState
      v-else-if="error"
      title="流程定义没有加载"
      :description="error.message || '请稍后重试'"
      :trace-id="error.traceId || ''"
      retry-label="重试"
      @retry="reload"
    />
    <NxEmpty
      v-else-if="!loading && !definition && !definitions.length"
      title="暂无流程"
      description="这个租户还没有流程或草稿。新建一个草稿，从一个顺序审批节点开始配置。"
      data-testid="process-empty"
    >
      <template #action>
        <button type="button" class="primary" data-testid="process-empty-new" @click="newDraft">新建草稿</button>
      </template>
    </NxEmpty>
    <div v-else class="shell">
      <aside class="nx-card list" data-testid="process-list">
        <h2>流程与草稿</h2>
        <button
          v-for="item in definitions"
          :key="item.key"
          type="button"
          class="item"
          :class="{ current: item.key === definition?.key }"
          @click="openDefinition(item.key)"
        >
          <strong>{{ showText(item.name) }}</strong>
          <span>{{ showText(item.key) }} · {{ item.source === "draft" ? "草稿" : "已发布只读" }}</span>
        </button>
      </aside>
      <section class="nx-card canvas" data-testid="process-canvas">
        <header class="canvas-head">
          <div>
            <h2>{{ showText(definition?.name) }}</h2>
            <p class="nx-muted">{{ isEditing ? "可编辑草稿" : "已发布只读" }}<template v-if="devNotes && definition?.resource"> · {{ definition.resource }}</template></p>
          </div>
          <span v-if="definition && sequence.sequential" class="mode-chip" data-testid="flow-mode">顺序审批 · {{ sequence.steps.length }} 个审批节点</span>
        </header>
        <p v-if="draftState" class="draft-state" :data-tone="draftState.tone" data-testid="draft-state">{{ draftState.text }}</p>
        <NxEmpty
          v-if="definition && !graph.nodes.length"
          compact
          title="暂无节点"
          :description="isEditing ? '这份草稿没有可解析的节点。新建草稿会从「开始 → 审批 → 结束」起步。' : '这个流程没有可展示的节点。可以新建草稿重新配置。'"
          data-testid="process-nodes-empty"
        >
          <template #action>
            <button type="button" class="ghost" data-testid="process-nodes-empty-new" @click="newDraft">新建草稿</button>
          </template>
        </NxEmpty>
        <ol
          v-else-if="definition && sequence.sequential"
          class="flow"
          :class="{ editing: isEditing }"
          data-testid="flow-steps"
          @dragover.prevent
          @dragleave="onFlowDragLeave"
          @drop.prevent="onStepDrop"
        >
          <li
            v-if="sequence.start"
            class="terminal start"
            :class="{ current: selectedId === sequence.start.id }"
            data-testid="process-node"
            role="button"
            tabindex="0"
            @click="selectedId = sequence.start.id"
            @keydown.enter.prevent="selectedId = sequence.start.id"
          >
            <span class="terminal-icon"><NxIcon name="play" :size="14" /></span>
            <span><strong>{{ showText(sequence.start.name || "开始") }}</strong><small>发起人提交申请</small></span>
          </li>
          <template v-for="(step, index) in sequence.steps" :key="step.id">
            <li class="connector" aria-hidden="false">
              <span class="line" />
              <button v-if="isEditing" type="button" class="insert" data-testid="insert-step" :aria-label="`在第 ${index + 1} 个节点前插入审批节点`" title="插入审批节点" @click="insertStep(index)">
                <NxIcon name="plus" :size="14" />
              </button>
            </li>
            <li v-if="dropIndex === index" class="drop-line" data-testid="step-drop-indicator" aria-hidden="true" />
            <li
              class="step"
              :class="{ current: selectedId === step.id, dragging: draggingId === step.id, invalid: nodeErrors(step.id).length }"
              :draggable="isEditing"
              data-testid="process-node"
              role="button"
              tabindex="0"
              :aria-label="`第 ${index + 1} 个审批节点 ${step.name || ''}`"
              @click="selectedId = step.id"
              @keydown.enter.self.prevent="selectedId = step.id"
              @dragstart="onStepDragStart($event, step)"
              @dragend="endStepDrag"
              @dragover.prevent.stop="onStepDragOver($event, index)"
              @drop.prevent.stop="onStepDrop"
            >
              <span v-if="isEditing" class="grip" aria-hidden="true"><NxIcon name="grip" :size="18" /></span>
              <span class="num">{{ index + 1 }}</span>
              <span class="step-body">
                <small class="step-kind">第 {{ index + 1 }} 步 · 审批</small>
                <strong>{{ step.name || "未命名审批节点" }}</strong>
                <span class="assignee" :class="{ missing: !step.assignee }">
                  <NxIcon name="user" :size="14" />{{ step.assignee ? assigneeLabel(step.assignee) : "未设置审批人" }}
                </span>
                <span v-for="(message, messageIndex) in nodeErrors(step.id)" :key="messageIndex" class="step-error" data-testid="step-error">{{ message }}</span>
              </span>
              <span v-if="isEditing" class="step-actions">
                <button type="button" class="icon-btn" data-testid="step-up" aria-label="上移" title="上移" :disabled="index === 0" @click.stop="moveStep(index, index - 1)"><NxIcon name="arrowUp" :size="16" /></button>
                <button type="button" class="icon-btn" data-testid="step-down" aria-label="下移" title="下移" :disabled="index === sequence.steps.length - 1" @click.stop="moveStep(index, index + 2)"><NxIcon name="arrowDown" :size="16" /></button>
                <button type="button" class="icon-btn danger-hover" data-testid="step-remove" aria-label="删除审批节点" title="删除" :disabled="sequence.steps.length === 1" @click.stop="removeStep(index)"><NxIcon name="trash" :size="16" /></button>
              </span>
            </li>
          </template>
          <li v-if="!sequence.steps.length" class="steps-empty" data-testid="flow-steps-empty">
            <strong>还没有审批节点</strong>
            <small>{{ isEditing ? "点下方「添加审批节点」，申请会按顺序交给每个节点的审批人。" : "此流程发起后会直接结束，不经过审批。" }}</small>
          </li>
          <li v-if="dropIndex === sequence.steps.length" class="drop-line" data-testid="step-drop-indicator" aria-hidden="true" />
          <li class="connector">
            <span class="line" />
            <button v-if="isEditing" type="button" class="insert" data-testid="insert-step" aria-label="在末尾添加审批节点" title="添加审批节点" @click="insertStep(sequence.steps.length)">
              <NxIcon name="plus" :size="14" />
            </button>
          </li>
          <li
            v-if="sequence.end"
            class="terminal end"
            :class="{ current: selectedId === sequence.end.id }"
            data-testid="process-node"
            role="button"
            tabindex="0"
            @click="selectedId = sequence.end.id"
            @keydown.enter.prevent="selectedId = sequence.end.id"
          >
            <span class="terminal-icon"><NxIcon name="check" :size="14" /></span>
            <span><strong>{{ showText(sequence.end.name || "结束") }}</strong><small>全部通过后结束，结果回写申请</small></span>
          </li>
          <li v-else class="terminal end missing-end"><span class="terminal-icon"><NxIcon name="check" :size="14" /></span><span><strong>结束</strong><small>编辑时自动补齐</small></span></li>
        </ol>
        <button v-if="definition && sequence.sequential && isEditing" type="button" class="ghost add-step" data-testid="add-step" @click="insertStep(sequence.steps.length)">
          <NxIcon name="plus" :size="16" />添加审批节点
        </button>
        <p v-if="definition && graph.nodes.length && !sequence.sequential" class="nonsequential" data-testid="process-nonsequential">
          此流程{{ sequence.reason }}，顺序编辑器不支持，仅可查看。
        </p>
        <svg
          v-if="definition && graph.nodes.length && !sequence.sequential"
          class="graph"
          :viewBox="`0 0 ${graph.width} ${graph.height}`"
          :width="graph.width"
          :height="graph.height"
          role="group"
          aria-label="流程图"
        >
          <line
            v-for="flow in graph.flows"
            :key="flow.id || `${flow.source_ref}-${flow.target_ref}`"
            :x1="flow.x1"
            :y1="flow.y1"
            :x2="flow.x2"
            :y2="flow.y2"
          />
          <g
            v-for="node in graph.nodes"
            :key="node.id"
            class="node"
            :class="{ current: node.id === selectedId }"
            data-testid="process-node"
            @click="selectedId = node.id"
          >
            <rect :x="node.x" :y="node.y" :width="graph.nodeWidth" :height="graph.nodeHeight" rx="8" />
            <text :x="node.x + 12" :y="node.y + 24">{{ showText(node.name) }}</text>
            <text :x="node.x + 12" :y="node.y + 42" class="type">{{ showText(node.type) }}</text>
          </g>
        </svg>
      </section>
      <aside class="nx-card props" data-testid="process-properties">
        <h2>节点属性</h2>
        <label v-if="isEditing" class="draft-name">草稿名称<input v-model="definition.name" data-testid="draft-name" aria-label="草稿名称" /></label>
        <dl v-if="selected">
          <dt>标识</dt>
          <dd>{{ showText(selected.id) }}</dd>
          <dt>名称</dt>
          <dd v-if="isEditing && editableType(selected.type)"><input v-model="selected.name" data-testid="node-name" aria-label="节点名称" /></dd>
          <dd v-else>{{ showText(selected.name) }}</dd>
          <dt>类型</dt>
          <dd data-testid="node-type">{{ nodeTypeLabel(selected.type) }}</dd>
          <template v-if="selected.type === 'userTask'">
            <dt>审批人引用</dt>
            <dd v-if="isEditing">
              <input v-model="selected.assignee" data-testid="node-assignee" aria-label="审批人引用" placeholder="例如 ${assignee}" />
              <span class="presets">
                <button v-for="preset in ASSIGNEE_PRESETS" :key="preset.value" type="button" class="preset"
                  :class="{ active: selected.assignee === preset.value }" data-testid="assignee-preset"
                  @click="selected.assignee = preset.value">{{ preset.label }}</button>
              </span>
              <small class="preset-hint">目前发起时只选一位审批人；多个节点都用它时会由同一人依次审批。</small>
            </dd>
            <dd v-else>{{ selected.assignee ? assigneeLabel(selected.assignee) : "暂无" }}</dd>
          </template>
        </dl>
        <p v-else class="nx-muted" data-testid="process-properties-empty">点选画布中的节点，查看或修改它的属性。</p>
        <p v-if="isEditing && selected && nodeErrors(selected.id).length" class="step-error" data-testid="node-error">{{ nodeErrors(selected.id).join("；") }}</p>
        <section v-if="validationResult" class="validation-result" data-testid="process-validation" :data-valid="validationResult.valid">
          <strong>{{ validationResult.valid ? "校验通过" : "校验未通过" }}</strong>
          <p v-if="validationResult.valid" class="nx-muted">当前草稿满足基础结构要求。</p>
          <ul v-else>
            <li v-for="(item, index) in validationResult.errors || []" :key="`${item.code}-${index}`">
              <strong>{{ item.code }}</strong> {{ item.message }}
              <span v-if="item.node_id || item.flow_id">（{{ item.node_id || item.flow_id }}）</span>
            </li>
          </ul>
        </section>
        <p v-if="validationError" class="validation-error" role="alert" data-testid="process-validation-error">
          校验请求失败：{{ validationError.message || "请稍后重试" }}
        </p>
        <section v-if="publication" class="publication-result" data-testid="process-publication">
          <strong>已发布</strong>
          <span v-if="publication.version">最近发布版本 v{{ publication.version }}</span>
          <span v-else>服务端已确认发布</span>
          <router-link to="/p4/product-definitions" data-testid="process-next-bind">下一步：绑定到产品</router-link>
        </section>
        <p v-if="publishError" class="publish-error" role="alert" data-testid="process-publish-error">
          发布失败：{{ publishError.message || "服务端未能发布此流程" }}
        </p>
        <template v-if="!sequence.sequential">
        <h3>相关连线</h3>
        <ul v-if="selectedFlows.length">
          <li v-for="flow in selectedFlows" :key="flow.id || `${flow.source_ref}-${flow.target_ref}`">
            {{ showText(flow.source_ref) }} → {{ showText(flow.target_ref) }}
            <span>条件 {{ showText(flow.condition) }}</span>
          </li>
        </ul>
        <p v-else class="nx-muted">暂无</p>
        </template>
      </aside>
    </div>
    <div v-if="publishConfirm" class="confirm-backdrop" data-testid="process-publish-confirm-backdrop">
      <section class="confirm-dialog nx-card" role="dialog" aria-modal="true" aria-labelledby="process-publish-confirm-title" data-testid="process-publish-confirm">
        <h2 id="process-publish-confirm-title">确认发布流程？</h2>
        <p>将当前已保存且校验通过的草稿发布为租户可用版本。正在运行的流程实例不会迁移。</p>
        <div class="confirm-actions">
          <button type="button" class="ghost" data-testid="process-publish-cancel" :disabled="publishing" @click="publishConfirm = false">取消</button>
          <button type="button" class="primary" data-testid="process-publish-confirm-submit" :disabled="publishing" @click="publishDraft">
            {{ publishing ? "发布中…" : "确认发布" }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader } from "../../components";
import NxIcon from "../../icons/NxIcon.vue";
import { p3 } from "../../api/p3";
import { insertAt, moveToGap } from "../../utils/listMove";
import { createBlankDraft, parseDraftXml, serializeDraftXml } from "./processDraft";
import { layoutDefinition, showText } from "./processLayout";
import { fromSequence, newStep, toSequence } from "./sequentialFlow";
import { devNotesEnabled } from "../../utils/devNotes";

// Only `assignee` is set as a process variable at start (SequentialApprovalService); offer nothing else.
const ASSIGNEE_PRESETS = [
  { value: "${assignee}", label: "发起时选择的审批人" },
];

// The classpath resource path is a developer note (O9).
const devNotes = devNotesEnabled();
const route = useRoute();
const router = useRouter();
const definitions = ref([]);
const definition = ref(null);
const isDraft = ref(false);
const isEditing = ref(false);
const isPersistedDraft = ref(false);
const dirty = ref(false);
const saving = ref(false);
const validating = ref(false);
const publishing = ref(false);
const publishConfirm = ref(false);
const publication = ref(null);
const publishError = ref(null);
const validationResult = ref(null);
const validationError = ref(null);
const selectedId = ref("");
const error = ref(null);
const loading = ref(false);
// No tenant session: process APIs answer 401 P2.IAM.UNAUTHORIZED "missing bearer token" (M2); retrying cannot help.
const signInRequired = computed(() => Number(error.value?.status) === 401 || error.value?.code === "P2.IAM.UNAUTHORIZED");
const canPublish = computed(() => isPersistedDraft.value && !dirty.value && validationResult.value?.valid === true && !publishing.value);

const graph = computed(() => layoutDefinition(definition.value || {}));
const sequence = computed(() => toSequence(definition.value || {}));
const draggingId = ref("");
const dropIndex = ref(null);
const selected = computed(() => definition.value?.nodes?.find((node) => node.id === selectedId.value) || null);
const selectedFlows = computed(() => {
  const id = selectedId.value;
  return (definition.value?.flows || []).filter((flow) => flow.source_ref === id || flow.target_ref === id);
});

async function reload() {
  loading.value = true;
  error.value = null;
  try {
    const [listed, savedDrafts] = await Promise.all([p3.listProcessDefinitions(), p3.listProcessDrafts()]);
    const catalog = Array.isArray(listed) ? listed : [];
    const draftList = Array.isArray(savedDrafts) ? savedDrafts : [];
    definitions.value = [
      ...draftList.map((draft) => ({ key: draft.key, name: draft.name, source: "draft" })),
      ...catalog.filter((item) => !draftList.some((draft) => draft.key === item.key)),
    ];
    const key = route.params.key ? String(route.params.key) : "";
    if (!key) {
      definition.value = null;
      isPersistedDraft.value = false;
      dirty.value = false;
      validationResult.value = null;
      publication.value = null;
      publishError.value = null;
      publishConfirm.value = false;
      selectedId.value = "";
      if (definitions.value[0]?.key) {
        await router.replace(`/p3/workflow/${encodeURIComponent(definitions.value[0].key)}`);
      }
      return;
    }
    const saved = draftList.find((draft) => draft.key === key);
    if (saved) {
      const draft = await p3.getProcessDraft(key);
      definition.value = { ...parseDraftXml(draft.bpmn_xml || draft.bpmnXml), key, name: draft.name, source: "draft" };
      try {
        publication.value = await p3.getProcessPublication(key);
      } catch (publicationLookupError) {
        if (publicationLookupError.status !== 404) throw publicationLookupError;
        publication.value = null;
      }
      isDraft.value = true;
      isEditing.value = true;
      isPersistedDraft.value = true;
    } else {
      definition.value = await p3.getProcessDefinition(key);
      isDraft.value = false;
      isEditing.value = false;
      isPersistedDraft.value = false;
    }
    dirty.value = false;
    validationResult.value = null;
    validationError.value = null;
    publishError.value = null;
    publishConfirm.value = false;
    const nodes = definition.value?.nodes || [];
    selectedId.value = nodes.some((node) => node.id === selectedId.value) ? selectedId.value : (nodes[0]?.id || "");
  } catch (err) {
    error.value = err;
    definition.value = null;
    isPersistedDraft.value = false;
    dirty.value = false;
    publication.value = null;
    publishError.value = null;
    publishConfirm.value = false;
  } finally {
    loading.value = false;
  }
}

function openDefinition(key) {
  if (!key || key === route.params.key) {
    return;
  }
  router.push(`/p3/workflow/${encodeURIComponent(key)}`);
}

function newDraft() {
  const key = `draft-${Date.now()}`;
  definition.value = createBlankDraft(key);
  isDraft.value = true;
  isEditing.value = true;
  isPersistedDraft.value = false;
  dirty.value = true;
  validationResult.value = null;
  publication.value = null;
  publishError.value = null;
  selectedId.value = "approveTask";
}

function editAsDraft() {
  if (!definition.value) return;
  isDraft.value = true;
  isEditing.value = true;
  isPersistedDraft.value = false;
  if (sequence.value.sequential) definition.value = fromSequence(definition.value, sequence.value.steps);
  dirty.value = true;
  validationResult.value = null;
}

function setSteps(steps) {
  definition.value = fromSequence(definition.value, steps);
}

function insertStep(index) {
  if (!definition.value || !sequence.value.sequential) return;
  const step = newStep(definition.value);
  setSteps(insertAt(sequence.value.steps, index, step));
  selectedId.value = step.id;
}

function removeStep(index) {
  const steps = sequence.value.steps;
  if (steps.length <= 1) return;
  const removed = steps[index];
  setSteps(steps.filter((_, i) => i !== index));
  if (removed?.id === selectedId.value) selectedId.value = sequence.value.steps[Math.min(index, sequence.value.steps.length - 1)]?.id || "";
}

function moveStep(from, gap) {
  const steps = sequence.value.steps;
  const moved = moveToGap(steps, from, gap);
  if (moved !== steps) setSteps(moved);
}

function onStepDragStart(event, step) {
  if (!isEditing.value) return;
  draggingId.value = step.id;
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = "move";
    try {
      event.dataTransfer.setData("text/plain", step.id);
    } catch {
      // In-memory drag state is authoritative.
    }
  }
}

function onStepDragOver(event, index) {
  if (!draggingId.value) return;
  const rect = event.currentTarget.getBoundingClientRect();
  const before = rect.height > 0 && event.clientY < rect.top + rect.height / 2;
  dropIndex.value = before ? index : index + 1;
  if (event.dataTransfer) event.dataTransfer.dropEffect = "move";
}

function onFlowDragLeave(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) dropIndex.value = null;
}

function onStepDrop() {
  const id = draggingId.value;
  const gap = dropIndex.value;
  endStepDrag();
  if (!id || gap == null) return;
  moveStep(sequence.value.steps.findIndex((step) => step.id === id), gap);
}

function endStepDrag() {
  draggingId.value = "";
  dropIndex.value = null;
}

function nodeErrors(id) {
  const errors = validationResult.value?.errors;
  if (!Array.isArray(errors)) return [];
  return errors.filter((item) => item?.node_id === id || item?.nodeId === id).map((item) => item.message).filter(Boolean);
}

const NODE_TYPES = { startEvent: "开始", userTask: "审批节点", endEvent: "结束" };

function nodeTypeLabel(type) {
  return NODE_TYPES[type] ? `${NODE_TYPES[type]}（${type}）` : showText(type);
}

// N4: say where the draft is in 保存 → 校验 → 发布, so a disabled button is never unexplained.
const draftState = computed(() => {
  if (!definition.value) return null;
  if (!isEditing.value) return { tone: "neutral", text: "已发布只读：点「创建可编辑草稿」在副本上修改，原流程不受影响。" };
  if (!isPersistedDraft.value) return { tone: "warning", text: "未保存的草稿：先「保存草稿」，保存后才能校验和发布。" };
  if (dirty.value) return { tone: "warning", text: "有未保存的修改：保存后再校验，校验针对已保存的版本。" };
  if (publication.value && validationResult.value?.valid !== true) return { tone: "success", text: "已保存，并有已发布版本；再次发布前需重新校验。" };
  if (validationResult.value?.valid === true) return { tone: "success", text: "已保存并校验通过：可以发布。" };
  if (validationResult.value) return { tone: "danger", text: "校验未通过：按节点上的提示修改，保存后再校验。" };
  return { tone: "info", text: "已保存：下一步「校验草稿」。" };
});

function assigneeLabel(value) {
  return ASSIGNEE_PRESETS.find((preset) => preset.value === value)?.label || value;
}

function editableType(type) {
  return ["startEvent", "userTask", "endEvent"].includes(type);
}

async function saveDraft() {
  if (!definition.value || saving.value) return;
  saving.value = true;
  error.value = null;
  try {
    const saved = await p3.saveProcessDraft(definition.value.key, {
      name: definition.value.name,
      bpmn_xml: serializeDraftXml(definition.value),
    });
    definition.value = { ...definition.value, name: saved.name || definition.value.name, source: "draft" };
    isDraft.value = true;
    isEditing.value = true;
    isPersistedDraft.value = true;
    dirty.value = false;
    validationResult.value = null;
    validationError.value = null;
    publishError.value = null;
    const existing = definitions.value.find((item) => item.key === definition.value.key);
    if (existing) existing.name = definition.value.name;
    else definitions.value.unshift({ key: definition.value.key, name: definition.value.name, source: "draft" });
  } catch (err) {
    error.value = err;
  } finally {
    saving.value = false;
  }
}

async function validateDraft() {
  if (!definition.value || !isPersistedDraft.value || dirty.value || validating.value) return;
  validating.value = true;
  validationError.value = null;
  validationResult.value = null;
  try {
    validationResult.value = await p3.validateProcessDraft(definition.value.key);
  } catch (err) {
    validationError.value = err;
  } finally {
    validating.value = false;
  }
}

async function publishDraft() {
  if (!canPublish.value || !definition.value || publishing.value) return;
  publishing.value = true;
  publishError.value = null;
  try {
    publication.value = await p3.publishProcessDraft(definition.value.key);
    publishConfirm.value = false;
  } catch (err) {
    publishError.value = err;
  } finally {
    publishing.value = false;
  }
}

watch(definition, () => {
  validationResult.value = null;
  validationError.value = null;
  publishError.value = null;
  if (dirty.value) publishConfirm.value = false;
  if (isEditing.value) dirty.value = true;
}, { deep: true, flush: "sync" });

watch(() => route.params.key, reload, { immediate: true });
</script>

<style scoped>
.later-note { margin-top: 0; }
.draft-state { margin: 0 0 var(--nx-space-3); padding: var(--nx-space-2) var(--nx-space-3); border-radius: var(--nx-radius-sm); font-size: var(--nx-font-size-sm); background: var(--nx-info-light); color: var(--nx-info); }
.draft-state[data-tone="warning"] { background: var(--nx-warning-light); color: var(--nx-warning); }
.draft-state[data-tone="success"] { background: var(--nx-success-light); color: var(--nx-success); }
.draft-state[data-tone="danger"] { background: var(--nx-danger-light); color: var(--nx-danger); }
.draft-state[data-tone="neutral"] { background: var(--nx-divider); color: var(--nx-text-regular); }
.step-kind { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.steps-empty { display: grid; gap: 4px; justify-items: center; padding: var(--nx-space-4); border: 1px dashed var(--nx-border); border-radius: var(--nx-radius-sm); text-align: center; }
.steps-empty small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.shell {
  display: grid;
  grid-template-columns: minmax(min(100%, 200px), 240px) minmax(0, 1fr) minmax(min(100%, 220px), 280px);
  gap: var(--nx-space-4);
  align-items: start;
}
.list, .props { display: grid; gap: var(--nx-space-2); }
.draft-name { display: grid; gap: var(--nx-space-1); }
.node-actions { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); }
.node-actions h3 { width: 100%; }
.validation-result { display: grid; gap: var(--nx-space-2); padding: var(--nx-space-2); border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); }
.validation-result[data-valid="true"] { border-color: var(--nx-success, #238636); }
.validation-result ul { margin: 0; padding-left: 1.1em; display: grid; gap: var(--nx-space-1); }
.validation-result li span, .validation-error, .publish-error { color: var(--nx-text-secondary); }
.publication-result { display: grid; gap: var(--nx-space-1); padding: var(--nx-space-2); border: 1px solid var(--nx-success, #238636); border-radius: var(--nx-radius-sm); }
.confirm-backdrop { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: var(--nx-space-4); background: rgb(0 0 0 / 48%); }
.confirm-dialog { width: min(100%, 440px); display: grid; gap: var(--nx-space-3); }
.confirm-dialog h2, .confirm-dialog p { margin: 0; }
.confirm-actions { display: flex; justify-content: flex-end; gap: var(--nx-space-2); }
.list h2, .canvas h2, .props h2, .props h3 { margin: 0; font-size: var(--nx-font-size-md); }
.item {
  display: grid;
  gap: 2px;
  padding: var(--nx-space-2);
  text-align: left;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
  background: transparent;
  color: inherit;
}
.item.current { border-color: var(--nx-product); }
.item span, .props li span, .type { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.canvas { overflow: auto; }
svg.graph { display: block; min-width: 100%; }
line { stroke: var(--nx-border-strong, var(--nx-border)); stroke-width: 1.5; }
.node { cursor: pointer; }
.node rect { fill: var(--nx-surface); stroke: var(--nx-border); }
.node.current rect { stroke: var(--nx-product); stroke-width: 2; }
text { fill: var(--nx-text-primary); font-size: 13px; }
.props dl { display: grid; grid-template-columns: 64px minmax(0, 1fr); gap: var(--nx-space-1) var(--nx-space-2); margin: 0; }
.props dt { color: var(--nx-text-secondary); }
.props dd { margin: 0; overflow-wrap: anywhere; }
.props ul { margin: 0; padding-left: 1.1em; display: grid; gap: var(--nx-space-2); }
.props li { display: grid; }
.canvas-head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--nx-space-3); margin-bottom: var(--nx-space-4); }
.canvas-head p { margin: 4px 0 0; font-size: var(--nx-font-size-xs); }
.mode-chip {
  flex: none;
  padding: 2px 10px;
  border-radius: var(--nx-radius-pill);
  font-size: var(--nx-font-size-xs);
  font-weight: 600;
  color: var(--nx-product);
  background: var(--nx-product-light);
}
.flow {
  list-style: none;
  margin: 0 auto;
  padding: var(--nx-space-4);
  max-width: 560px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  border-radius: var(--nx-radius);
  background: var(--nx-bg-page);
}
.terminal {
  display: flex;
  align-items: center;
  gap: var(--nx-space-3);
  align-self: center;
  min-width: 240px;
  padding: 8px 16px 8px 8px;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-pill);
  background: var(--nx-surface);
  cursor: pointer;
}
.terminal strong, .step-body strong { display: block; color: var(--nx-text-primary); font-size: var(--nx-font-size-md); }
.terminal small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.terminal-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: #fff;
  background: var(--nx-success);
}
.terminal.end .terminal-icon { background: var(--nx-text-secondary); }
.terminal.current, .step.current { border-color: var(--nx-product); box-shadow: 0 0 0 3px var(--nx-product-light); }
.missing-end { opacity: 0.6; cursor: default; }
.connector { position: relative; display: grid; place-items: center; height: 36px; }
.connector .line { position: absolute; top: 0; bottom: 0; left: 50%; width: 2px; background: var(--nx-border-strong); }
.connector .line::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: -4px;
  border: 5px solid transparent;
  border-top-color: var(--nx-border-strong);
  border-bottom: 0;
}
.insert {
  position: relative;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 1px solid var(--nx-border-strong);
  border-radius: 50%;
  background: var(--nx-surface);
  color: var(--nx-text-secondary);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s, border-color 0.15s;
}
.flow:hover .insert, .insert:focus-visible { opacity: 1; }
.insert:hover { color: var(--nx-product); border-color: var(--nx-product); }
.step {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--nx-space-3);
  padding: 12px;
  border: 1px solid var(--nx-border);
  border-left: 3px solid var(--nx-product);
  border-radius: var(--nx-radius-sm);
  background: var(--nx-surface);
  box-shadow: var(--nx-shadow-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s;
}
.flow:not(.editing) .step { grid-template-columns: auto minmax(0, 1fr); }
.step:focus-visible, .terminal:focus-visible { outline: 2px solid var(--nx-product); outline-offset: 2px; }
.step.invalid { border-color: var(--nx-danger); border-left-color: var(--nx-danger); }
.step.dragging { opacity: 0.4; }
.grip { color: var(--nx-text-disabled); cursor: grab; }
.num {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  font-size: var(--nx-font-size-xs);
  font-weight: 700;
  color: var(--nx-product);
  background: var(--nx-product-light);
}
.step-body { display: grid; gap: 4px; min-width: 0; }
.assignee { display: inline-flex; align-items: center; gap: 4px; color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.assignee.missing { color: var(--nx-warning); }
.step-error { color: var(--nx-danger); font-size: var(--nx-font-size-sm); margin: 0; }
.step-actions { display: flex; gap: 2px; opacity: 0.55; transition: opacity 0.15s; }
.step:hover .step-actions, .step.current .step-actions, .step:focus-within .step-actions { opacity: 1; }
.icon-btn {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--nx-text-secondary);
  cursor: pointer;
}
.icon-btn:hover:not(:disabled) { color: var(--nx-product); background: var(--nx-product-light); }
.icon-btn.danger-hover:hover:not(:disabled) { color: var(--nx-danger); background: var(--nx-danger-light); }
.icon-btn:disabled { color: var(--nx-text-disabled); cursor: not-allowed; }
.drop-line { height: 3px; margin: 2px 0; border-radius: 2px; background: var(--nx-product); }
.add-step { display: flex; align-items: center; gap: 4px; margin: var(--nx-space-3) auto 0; }
.nonsequential {
  margin: 0 0 var(--nx-space-3);
  padding: 8px 12px;
  border-radius: var(--nx-radius-sm);
  color: var(--nx-warning);
  background: var(--nx-warning-light);
  font-size: var(--nx-font-size-sm);
}
.presets { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.preset {
  padding: 2px 10px;
  border: 1px solid var(--nx-border-strong);
  border-radius: var(--nx-radius-pill);
  background: var(--nx-surface);
  color: var(--nx-text-regular);
  font-size: var(--nx-font-size-xs);
  cursor: pointer;
}
.preset.active, .preset:hover { border-color: var(--nx-product); color: var(--nx-product); background: var(--nx-product-light); }
.preset-hint { display: block; margin-top: 6px; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.props input { width: 100%; box-sizing: border-box; border: 1px solid var(--nx-border-strong); border-radius: 6px; padding: 6px 8px; font: inherit; }
@media (max-width: 960px) {
  .shell { grid-template-columns: 1fr; }
}
</style>
