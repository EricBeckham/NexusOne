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
    <p class="nx-muted later-note">当前仅支持开始、用户任务、结束节点的基础编辑与结构校验；发布前必须保存并通过校验。</p>
    <NxErrorState
      v-if="error"
      title="流程定义没有加载"
      :description="error.message || '请稍后重试'"
      :trace-id="error.traceId || ''"
      retry-label="重试"
      @retry="reload"
    />
    <NxEmpty v-else-if="!loading && !definition && !definitions.length" title="暂无流程定义" description="类路径里还没有可查看的流程。" />
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
        <h2>{{ showText(definition?.name) }}</h2>
        <p class="nx-muted">来源 {{ showText(definition?.source) }} · {{ showText(definition?.resource) }}</p>
        <NxEmpty v-if="definition && !graph.nodes.length" compact title="暂无节点" />
        <svg
          v-else-if="definition"
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
          <dd>{{ showText(selected.type) }}</dd>
          <template v-if="selected.type === 'userTask'">
            <dt>审批人引用</dt>
            <dd v-if="isEditing"><input v-model="selected.assignee" data-testid="node-assignee" aria-label="审批人引用" placeholder="例如 ${assignee}" /></dd>
            <dd v-else>{{ showText(selected.assignee) }}</dd>
          </template>
        </dl>
        <p v-else class="nx-muted">暂无</p>
        <div v-if="isEditing" class="node-actions" data-testid="node-actions">
          <h3>添加节点</h3>
          <button type="button" class="ghost" @click="addNode('startEvent')">开始</button>
          <button type="button" class="ghost" @click="addNode('userTask')">用户任务</button>
          <button type="button" class="ghost" @click="addNode('endEvent')">结束</button>
        </div>
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
        </section>
        <p v-if="publishError" class="publish-error" role="alert" data-testid="process-publish-error">
          发布失败：{{ publishError.message || "服务端未能发布此流程" }}
        </p>
        <h3>相关连线</h3>
        <ul v-if="selectedFlows.length">
          <li v-for="flow in selectedFlows" :key="flow.id || `${flow.source_ref}-${flow.target_ref}`">
            {{ showText(flow.source_ref) }} → {{ showText(flow.target_ref) }}
            <span>条件 {{ showText(flow.condition) }}</span>
          </li>
        </ul>
        <p v-else class="nx-muted">暂无</p>
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
import { p3 } from "../../api/p3";
import { addDraftNode, createBlankDraft, parseDraftXml, serializeDraftXml } from "./processDraft";
import { layoutDefinition, showText } from "./processLayout";

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
const canPublish = computed(() => isPersistedDraft.value && !dirty.value && validationResult.value?.valid === true && !publishing.value);

const graph = computed(() => layoutDefinition(definition.value || {}));
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
  dirty.value = true;
  validationResult.value = null;
}

function addNode(type) {
  if (!definition.value) return;
  const existingIds = new Set(definition.value.nodes.map((node) => node.id));
  definition.value = addDraftNode(definition.value, type);
  selectedId.value = definition.value.nodes.find((node) => !existingIds.has(node.id))?.id || selectedId.value;
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
svg { display: block; min-width: 100%; }
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
@media (max-width: 960px) {
  .shell { grid-template-columns: 1fr; }
}
</style>
