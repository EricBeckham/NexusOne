<template>
  <div>
    <NxPageHeader
      eyebrow="星构"
      title="流程设计器"
      description="只读查看已部署流程。节点位置按连线排列，不保存草稿。"
    >
      <template #actions>
        <button type="button" class="ghost" disabled data-testid="process-save">保存（后续）</button>
      </template>
    </NxPageHeader>
    <p class="nx-muted later-note">节点编辑、撤销、校验和发布定义仍是后续。本页不能改流程。</p>
    <NxErrorState
      v-if="error"
      title="流程定义没有加载"
      :description="error.message || '请稍后重试'"
      :trace-id="error.traceId || ''"
      retry-label="重试"
      @retry="reload"
    />
    <NxEmpty v-else-if="!loading && !definitions.length" title="暂无流程定义" description="类路径里还没有可查看的流程。" />
    <div v-else class="shell">
      <aside class="nx-card list" data-testid="process-list">
        <h2>流程</h2>
        <button
          v-for="item in definitions"
          :key="item.key"
          type="button"
          class="item"
          :class="{ current: item.key === definition?.key }"
          @click="openDefinition(item.key)"
        >
          <strong>{{ showText(item.name) }}</strong>
          <span>{{ showText(item.key) }}</span>
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
          aria-label="流程只读图"
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
        <dl v-if="selected">
          <dt>标识</dt>
          <dd>{{ showText(selected.id) }}</dd>
          <dt>名称</dt>
          <dd>{{ showText(selected.name) }}</dd>
          <dt>类型</dt>
          <dd>{{ showText(selected.type) }}</dd>
        </dl>
        <p v-else class="nx-muted">暂无</p>
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
import { layoutDefinition, showText } from "./processLayout";

const route = useRoute();
const router = useRouter();
const definitions = ref([]);
const definition = ref(null);
const selectedId = ref("");
const error = ref(null);
const loading = ref(false);

const graph = computed(() => layoutDefinition(definition.value || {}));
const selected = computed(() => graph.value.nodes.find((node) => node.id === selectedId.value) || null);
const selectedFlows = computed(() => {
  const id = selectedId.value;
  return (definition.value?.flows || []).filter((flow) => flow.source_ref === id || flow.target_ref === id);
});

async function reload() {
  loading.value = true;
  error.value = null;
  try {
    const listed = await p3.listProcessDefinitions();
    definitions.value = Array.isArray(listed) ? listed : [];
    const key = route.params.key ? String(route.params.key) : "";
    if (!key) {
      definition.value = null;
      selectedId.value = "";
      if (definitions.value[0]?.key) {
        await router.replace(`/p3/workflow/${encodeURIComponent(definitions.value[0].key)}`);
      }
      return;
    }
    definition.value = await p3.getProcessDefinition(key);
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
