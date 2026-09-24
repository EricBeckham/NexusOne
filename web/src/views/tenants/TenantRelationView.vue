<template>
  <NxPageHeader
    eyebrow="星域 / 租户关系"
    title="租户关系"
    description="只读父子关系。本页不改挂租户，也不展示跨租户业务数据。"
  />

  <NxNoPermission v-if="denied" title="暂无权限" :description="error || '当前身份不能查看租户关系。'" />
  <NxErrorState v-else-if="error" :title="error" :trace-id="traceId" @retry="loadTree" />

  <div v-else class="split">
    <aside class="tree-pane" aria-label="租户树">
      <label class="search">
        搜索
        <input :value="query" type="search" placeholder="名称或编码" @input="onSearch" />
      </label>
      <p v-if="loading" class="nx-muted" role="status">正在加载租户关系…</p>
      <NxEmpty v-else-if="roots && !roots.length" title="暂无" description="还没有可展示的租户关系。" />
      <NxEmpty v-else-if="roots && !rows.length" title="暂无" description="没有匹配的租户。" />
      <div v-else-if="rows.length" class="nodes" role="tree">
        <button
          v-for="row in rows"
          :key="row.id"
          type="button"
          role="treeitem"
          class="node"
          :class="{ current: row.id === selectedId }"
          :aria-current="row.id === selectedId ? 'true' : undefined"
          :style="{ paddingLeft: `${12 + row.depth * 16}px` }"
          @click="select(row.id)"
        >
          <span
            v-if="lazy && row.hasChildren"
            class="twist"
            @click.stop="toggle(row.id)"
          >{{ row.open ? "收起" : "展开" }}</span>
          <span class="name">{{ row.name || "暂无" }}</span>
          <span class="code">{{ row.code || "暂无" }}</span>
        </button>
      </div>
    </aside>

    <section class="detail-pane" data-testid="relation-detail">
      <NxEmpty v-if="!selectedId" title="暂无" description="请在左侧选择一个租户。" />
      <NxNoPermission v-else-if="detailDenied" title="暂无权限" :description="detailError || '不能查看这个租户的关系。'" />
      <NxErrorState v-else-if="detailError" :title="detailError" :trace-id="detailTrace" @retry="loadDetail(selectedId)" />
      <template v-else-if="selected || ancestors || children">
        <h2>{{ selected?.name || "暂无" }}</h2>
        <dl>
          <div><dt>编码</dt><dd>{{ selected?.code || "暂无" }}</dd></div>
          <div><dt>状态</dt><dd><NxStatusTag v-if="selected?.status" :status="selected.status" /><template v-else>暂无</template></dd></div>
          <div><dt>上级</dt><dd>{{ parentLabel }}</dd></div>
        </dl>
        <h3>祖先</h3>
        <p v-if="detailLoading" class="nx-muted">正在加载祖先…</p>
        <p v-else-if="ancestors && !ancestors.length">暂无</p>
        <ol v-else-if="ancestors?.length">
          <li v-for="item in ancestors" :key="item.id">{{ item.name || "暂无" }} · {{ item.code || "暂无" }}</li>
        </ol>
        <h3>子节点</h3>
        <p v-if="detailLoading" class="nx-muted">正在加载子节点…</p>
        <p v-else-if="children && !children.length" data-testid="relation-children-empty">暂无</p>
        <ul v-else-if="children?.length" data-testid="relation-children">
          <li v-for="item in children" :key="item.id">{{ item.name || "暂无" }} · {{ item.code || "暂无" }}</li>
        </ul>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { p1 } from "../../api/client";
import { NxEmpty, NxErrorState, NxNoPermission, NxPageHeader, NxStatusTag } from "../../components";
import {
  LAZY_TREE_LIMIT,
  filterTree,
  findNode,
  nodeCount,
  relationList,
  treeRoots,
  visibleRows,
} from "./relationDisplay";

const route = useRoute();
const router = useRouter();
const roots = ref(null);
const loading = ref(false);
const error = ref("");
const traceId = ref("");
const denied = ref(false);
const ancestors = ref(null);
const children = ref(null);
const detailLoading = ref(false);
const detailError = ref("");
const detailTrace = ref("");
const detailDenied = ref(false);
const expanded = ref(new Set());

const selectedId = computed(() => (route.params.id ? String(route.params.id) : ""));
const query = computed(() => String(route.query.q || ""));
const visibleTree = computed(() => filterTree(roots.value || [], query.value));
const lazy = computed(() => nodeCount(roots.value || []) > LAZY_TREE_LIMIT);
const rows = computed(() => visibleRows(visibleTree.value, { lazy: lazy.value, expanded: expanded.value }));
const selected = computed(() => findNode(roots.value || [], selectedId.value));
const parentLabel = computed(() => {
  const nearest = ancestors.value?.[0];
  if (!nearest) {
    return "暂无";
  }
  return `${nearest.name || "暂无"} · ${nearest.code || "暂无"}`;
});

function onSearch(event) {
  const value = event.target.value.trim();
  const next = { ...route.query };
  if (value) {
    next.q = value;
  } else {
    delete next.q;
  }
  router.replace({ query: next });
}

function select(id) {
  router.push({ name: "p1-relation", params: { id }, query: route.query });
}

function toggle(id) {
  const next = new Set(expanded.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  expanded.value = next;
}

async function loadTree() {
  loading.value = true;
  error.value = "";
  traceId.value = "";
  denied.value = false;
  roots.value = null;
  try {
    const body = await p1.tenantTree();
    const parsed = treeRoots(body);
    if (!parsed) {
      error.value = "接口没有返回租户关系树";
      return;
    }
    roots.value = parsed;
  } catch (err) {
    roots.value = null;
    if (err?.status === 403) {
      denied.value = true;
    }
    error.value = err?.message || "租户关系加载失败";
    traceId.value = err?.traceId || "";
  } finally {
    loading.value = false;
  }
}

async function loadDetail(id) {
  ancestors.value = null;
  children.value = null;
  detailError.value = "";
  detailTrace.value = "";
  detailDenied.value = false;
  if (!id) {
    return;
  }
  detailLoading.value = true;
  try {
    const [childBody, ancestorBody] = await Promise.all([
      p1.tenantChildren(id),
      p1.tenantAncestors(id),
    ]);
    if (String(route.params.id || "") !== id) {
      return;
    }
    const childRows = relationList(childBody);
    const ancestorRows = relationList(ancestorBody);
    if (!childRows || !ancestorRows) {
      detailError.value = "接口没有返回关系节点";
      return;
    }
    children.value = childRows;
    ancestors.value = ancestorRows;
  } catch (err) {
    if (String(route.params.id || "") !== id) {
      return;
    }
    children.value = null;
    ancestors.value = null;
    if (err?.status === 403) {
      detailDenied.value = true;
    }
    detailError.value = err?.message || "关系详情加载失败";
    detailTrace.value = err?.traceId || "";
  } finally {
    detailLoading.value = false;
  }
}

onMounted(loadTree);
watch(selectedId, (id) => {
  loadDetail(id);
}, { immediate: true });
</script>

<style scoped>
.split { display: grid; grid-template-columns: minmax(260px, 320px) minmax(0, 1fr); gap: 16px; align-items: start; }
.tree-pane, .detail-pane { min-width: 0; padding: 16px; border: 1px solid var(--nx-border, #e5eaf2); border-radius: 14px; background: var(--nx-surface, #fff); }
.search { display: grid; gap: 6px; margin-bottom: 12px; color: var(--nx-text-muted, #718096); font-size: 12px; }
.search input { width: 100%; box-sizing: border-box; }
.nodes { display: grid; gap: 4px; }
.node { display: flex; gap: 8px; align-items: baseline; width: 100%; border: 0; border-radius: 8px; background: transparent; text-align: left; padding-top: 8px; padding-bottom: 8px; padding-right: 8px; cursor: pointer; }
.node.current { background: var(--nx-info-light, #eef6ff); }
.name { color: var(--nx-text, #172b4d); font-weight: 650; }
.code, .twist { color: var(--nx-text-muted, #718096); font-size: 12px; }
.detail-pane h2, .detail-pane h3 { margin: 0 0 8px; }
.detail-pane h3 { margin-top: 16px; }
.detail-pane dl { display: grid; gap: 8px; margin: 0; }
.detail-pane dl div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 8px; }
.detail-pane dt { color: var(--nx-text-muted, #718096); }
.detail-pane dd, .detail-pane ol, .detail-pane ul { margin: 0; }
@media (max-width: 768px) {
  .split { grid-template-columns: 1fr; }
}
</style>
