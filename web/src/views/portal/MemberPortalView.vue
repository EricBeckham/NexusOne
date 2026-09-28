<template>
  <main class="member-portal" data-testid="member-portal">
    <NxPageHeader eyebrow="门户" title="我的门户" description="这里只列出当前岗位可以阅读的入口。列表本身不是办理授权。" />

    <NxErrorState
      v-if="error"
      title="门户暂时打不开"
      :description="error"
      :trace-id="traceId"
      retry-label="重试"
      @retry="load"
    />
    <NxSkeleton v-else-if="loading" variant="list-row" label="正在加载门户" data-testid="portal-loading" />
    <template v-else>
      <section class="modules" aria-label="首页模块">
        <article v-for="item in modules" :key="item.code" class="nx-card module" data-testid="portal-module">
          <h2>{{ item.title }}</h2>
        </article>
        <NxEmpty v-if="!modules.length" title="还没有首页模块" description="租户管理员配置首页模块后会出现在这里。" compact />
      </section>

      <section aria-label="功能入口">
        <h2 class="section-title">功能入口</h2>
        <ul v-if="entries.length" class="entries">
          <li v-for="item in entries" :key="item.id" class="nx-card entry" :data-entry="item.entry_key || undefined">
            <strong>{{ item.label }}</strong>
            <span v-if="item.entry_key" class="nx-code">{{ item.entry_key }}</span>
          </li>
        </ul>
        <NxEmpty
          v-else
          title="还没有可以阅读的入口"
          description="岗位还没有读权限，或管理员还没有配置菜单。门户不会因此列出全部功能。"
        />
      </section>

      <section aria-label="已开通应用">
        <h2 class="section-title">已开通应用</h2>
        <ul v-if="apps.length" class="entries" data-testid="portal-apps">
          <li v-for="app in apps" :key="app.instance_id" class="nx-card entry">
            <strong>{{ app.app_code }}</strong>
            <span v-if="app.version" class="nx-code">{{ app.version }}</span>
          </li>
        </ul>
        <NxEmpty
          v-else
          title="还没有应用实例"
          description="订购或批准开通后，已安装的应用会出现在这里。"
          compact
        />
      </section>
    </template>
  </main>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton } from "../../components";
import { currentInstances, portalMenu, portalModules, readableEntries } from "../../api/portal";
import { visiblePortal } from "../../portal/memberPortal";

const loading = ref(true);
const error = ref("");
const traceId = ref("");
const entries = ref([]);
const modules = ref([]);
const apps = ref([]);

async function load() {
  loading.value = true;
  error.value = "";
  traceId.value = "";
  try {
    const [menuBody, moduleBody] = await Promise.all([portalMenu(), portalModules()]);
    const menu = Array.isArray(menuBody?.items) ? menuBody.items : [];
    const keys = menu.map((item) => item.entry_key).filter(Boolean);
    const readable = await readableEntries(keys);
    let instances = [];
    try {
      instances = await currentInstances();
    } catch {
      instances = [];
    }
    const view = visiblePortal({
      menu,
      modules: Array.isArray(moduleBody?.modules) ? moduleBody.modules : [],
      readable,
      instances,
    });
    entries.value = view.entries;
    modules.value = view.modules;
    apps.value = instances.filter((row) => row && row.status !== "UNINSTALLED");
  } catch (cause) {
    error.value = cause?.message || "加载失败";
    traceId.value = cause?.traceId || "";
    entries.value = [];
    modules.value = [];
    apps.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.modules, .entries { display: grid; gap: 12px; }
.modules { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); margin-bottom: 20px; }
.module h2, .section-title { margin: 0 0 8px; font-size: 16px; }
.entry { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
</style>
