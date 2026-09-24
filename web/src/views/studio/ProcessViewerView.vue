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
        <button v-if="isEditing" type="button" class="primary" data-testid="process-save" :disabled="saving" @click="saveDraft">
          {{ saving ? "保存中…" : "保存草稿" }}
        </button>
      </template>
    </NxPageHeader>
    <p class="nx-muted later-note">当前仅支持开始、用户任务、结束节点的基础编辑与草稿保存；不包含网关编辑、校验或发布。</p>
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
const saving = ref(false);
const selectedId = ref("");
const error = ref(null);
const loading = ref(false);

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
      isDraft.value = true;
      isEditing.value = true;
    } else {
      definition.value = await p3.getProcessDefinition(key);
      isDraft.value = false;
      isEditing.value = false;
    }
    const nodes = definition.value?.nodes || [];
    selectedId.value = nodes.some((node) => node.id === selectedId.value) ? selectedId.value : (nodes[0]?.id || "");
  } catch (err) {
    error.value = err;
    definition.value = null;
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
  selectedId.value = "approveTask";
}

function editAsDraft() {
  if (!definition.value) return;
  isDraft.value = true;
  isEditing.value = true;
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
    const existing = definitions.value.find((item) => item.key === definition.value.key);
    if (existing) existing.name = definition.value.name;
    else definitions.value.unshift({ key: definition.value.key, name: definition.value.name, source: "draft" });
  } catch (err) {
    error.value = err;
  } finally {
    saving.value = false;
  }
}

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
