<template>
  <main class="portal-config" data-testid="portal-config">
    <NxPageHeader
      eyebrow="P3 · 星构"
      title="门户配置"
      description="调整成员门户的菜单分组、排序、显隐，以及首页模块。功能入口键写成 fn:应用:表单。隐藏后成员门户不再显示该项。"
    />

    <NxErrorState
      v-if="loadError"
      title="门户配置暂时打不开"
      :description="loadError"
      :trace-id="traceId"
      retry-label="重试"
      @retry="load"
    />
    <NxSkeleton v-else-if="loading" variant="list-row" label="正在加载门户配置" data-testid="portal-config-loading" />
    <template v-else>
      <section class="nx-card" aria-label="菜单">
        <header class="section-head">
          <h2>菜单</h2>
          <div class="actions">
            <button class="nx-btn nx-btn--default" type="button" data-testid="add-group" @click="addGroup">添加分组</button>
            <button class="nx-btn nx-btn--default" type="button" data-testid="add-entry" @click="addEntry">添加入口</button>
          </div>
        </header>
        <p v-if="!items.length" class="hint">还没有菜单。先加一个分组，再把入口放进去。</p>
        <ul v-else class="rows">
          <li v-for="row in rows" :key="row.item.id" class="row" :style="{ marginLeft: `${row.depth * 20}px` }" data-testid="menu-row">
            <label class="nx-field">
              <span>名称<em class="nx-req" aria-hidden="true">*</em></span>
              <input v-model="row.item.label" data-testid="menu-label" />
            </label>
            <label class="nx-field">
              <span>功能入口键</span>
              <input v-model="row.item.entry_key" placeholder="分组留空" data-testid="menu-entry" />
            </label>
            <label class="nx-field">
              <span>分区</span>
              <input v-model="row.item.section" data-testid="menu-section" />
            </label>
            <label class="nx-field">
              <span>上级分组</span>
              <select :value="row.item.parent_id || ''" data-testid="menu-parent" @change="setParent(row.item, $event.target.value)">
                <option value="">无</option>
                <option v-for="group in parentChoices(row.item)" :key="group.id" :value="group.id">{{ group.label || "未命名分组" }}</option>
              </select>
            </label>
            <label class="check">
              <input v-model="row.item.hidden" type="checkbox" data-testid="menu-hidden" />
              隐藏
            </label>
            <div class="actions">
              <button class="nx-btn nx-btn--text" type="button" data-testid="menu-up" @click="items = moveSibling(items, row.item.id, -1)">上移</button>
              <button class="nx-btn nx-btn--text" type="button" data-testid="menu-down" @click="items = moveSibling(items, row.item.id, 1)">下移</button>
              <button class="nx-btn nx-btn--text" type="button" data-testid="menu-remove" @click="removeItem(row.item.id)">移除</button>
            </div>
          </li>
        </ul>
        <ul v-if="menuIssues.length" class="nx-field-errors" data-testid="menu-problems">
          <li v-for="issue in menuIssues" :key="issue">{{ problemText(issue) }}</li>
        </ul>
      </section>

      <section class="nx-card" aria-label="首页模块">
        <header class="section-head">
          <h2>首页模块</h2>
          <button class="nx-btn nx-btn--default" type="button" data-testid="add-module" @click="addModule">添加模块</button>
        </header>
        <p v-if="!modules.length" class="hint">还没有首页模块。</p>
        <ul v-else class="rows">
          <li v-for="(module, index) in modules" :key="index" class="row" data-testid="module-row">
            <label class="nx-field">
              <span>编码<em class="nx-req" aria-hidden="true">*</em></span>
              <input v-model="module.code" data-testid="module-code" />
            </label>
            <label class="nx-field">
              <span>标题<em class="nx-req" aria-hidden="true">*</em></span>
              <input v-model="module.title" data-testid="module-title" />
            </label>
            <label class="check">
              <input v-model="module.hidden" type="checkbox" data-testid="module-hidden" />
              隐藏
            </label>
            <div class="actions">
              <button class="nx-btn nx-btn--text" type="button" data-testid="module-up" @click="modules = moveByIndex(modules, index, -1)">上移</button>
              <button class="nx-btn nx-btn--text" type="button" data-testid="module-down" @click="modules = moveByIndex(modules, index, 1)">下移</button>
              <button class="nx-btn nx-btn--text" type="button" data-testid="module-remove" @click="modules.splice(index, 1)">移除</button>
            </div>
          </li>
        </ul>
        <ul v-if="moduleIssues.length" class="nx-field-errors" data-testid="module-problems">
          <li v-for="issue in moduleIssues" :key="issue">{{ problemText(issue) }}</li>
        </ul>
      </section>

      <p v-if="saveError" class="nx-error" data-testid="save-error">{{ saveError }}</p>
      <p v-if="saved" data-testid="save-ok">已保存。成员门户会按这里的菜单和模块显示。</p>
      <button class="nx-btn nx-btn--primary" type="button" :disabled="saving" data-testid="save-portal" @click="save">保存</button>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { NxErrorState, NxPageHeader, NxSkeleton } from "../../components";
import { portalMenu, portalModules, replacePortalMenu, replacePortalModules } from "../../api/portal";
import {
  blankEntry,
  blankGroup,
  blankModule,
  fromMenuBody,
  fromModuleBody,
  groupsOf,
  menuPayload,
  menuProblems,
  modulePayload,
  moduleProblems,
  moveByIndex,
  moveSibling,
  orderedMenu,
  problemText,
} from "../../portal/portalConfig";

const loading = ref(true);
const saving = ref(false);
const saved = ref(false);
const loadError = ref("");
const saveError = ref("");
const traceId = ref("");
const items = ref([]);
const modules = ref([]);

const rows = computed(() => orderedMenu(items.value));
const menuIssues = computed(() => menuProblems(items.value));
const moduleIssues = computed(() => moduleProblems(modules.value));

function parentChoices(item) {
  return groupsOf(items.value).filter((group) => group.id !== item.id);
}

function setParent(item, parentId) {
  item.parent_id = parentId || null;
  const family = items.value.filter((row) => row.id !== item.id && (row.parent_id || null) === item.parent_id);
  item.sort = family.length;
}

function addGroup() {
  const row = blankGroup();
  row.sort = items.value.filter((item) => !item.parent_id).length;
  items.value = [...items.value, row];
}

function addEntry() {
  const groups = groupsOf(items.value);
  const parent = groups[0]?.id || null;
  const row = blankEntry(parent);
  row.sort = items.value.filter((item) => (item.parent_id || null) === parent).length;
  items.value = [...items.value, row];
}

function addModule() {
  const row = blankModule();
  row.sort = modules.value.length;
  modules.value = [...modules.value, row];
}

function removeItem(id) {
  items.value = items.value
    .filter((item) => item.id !== id)
    .map((item) => (item.parent_id === id ? { ...item, parent_id: null } : item));
}

async function load() {
  loading.value = true;
  loadError.value = "";
  traceId.value = "";
  saved.value = false;
  try {
    const [menuBody, moduleBody] = await Promise.all([portalMenu(), portalModules()]);
    items.value = fromMenuBody(menuBody);
    modules.value = fromModuleBody(moduleBody);
  } catch (cause) {
    loadError.value = cause?.message || "加载失败";
    traceId.value = cause?.traceId || "";
  } finally {
    loading.value = false;
  }
}

async function save() {
  saved.value = false;
  saveError.value = "";
  if (menuIssues.value.length || moduleIssues.value.length) {
    saveError.value = "请先修正标出的问题，再保存。";
    return;
  }
  saving.value = true;
  try {
    const menuBody = await replacePortalMenu(menuPayload(items.value));
    const moduleBody = await replacePortalModules(modulePayload(modules.value));
    items.value = fromMenuBody(menuBody);
    modules.value = fromModuleBody(moduleBody);
    saved.value = true;
  } catch (cause) {
    saveError.value = cause?.message || "保存失败";
    traceId.value = cause?.traceId || "";
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.portal-config { display: grid; gap: 16px; }
.nx-card { display: grid; gap: 12px; padding: 16px; }
.section-head, .actions, .row { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; }
.section-head { justify-content: space-between; align-items: center; }
.section-head h2 { margin: 0; font-size: 16px; }
.rows { list-style: none; margin: 0; padding: 0; display: grid; gap: 12px; }
.row { padding-bottom: 12px; border-bottom: 1px solid var(--nx-line, #e6e8ee); }
.nx-field { display: grid; gap: 4px; min-width: 140px; }
.check { display: flex; gap: 6px; align-items: center; min-height: 36px; }
.hint { margin: 0; color: var(--nx-text-secondary, #667); }
</style>
