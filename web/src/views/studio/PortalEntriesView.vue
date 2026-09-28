<template>
  <main class="portal">
    <NxPageHeader
      eyebrow="P3 · 星构"
      title="功能入口"
      description="已安装应用里，当前身份可以看见的功能。看见入口不等于可以操作。"
    />
    <p v-if="error" class="nx-error" data-testid="portal-error">{{ error }}</p>
    <NxSkeleton v-else-if="!loaded" variant="list-row" label="正在加载功能入口" />
    <NxEmpty
      v-else-if="!groups.length"
      title="还没有可进入的功能入口"
      description="已安装应用的入口会显示在这里。"
    />
    <template v-else>
      <section v-for="group in groups" :key="group.title" class="nx-card" data-testid="portal-group">
        <h2>{{ group.title }}</h2>
        <article v-for="entry in group.entries" :key="entry.key" data-testid="portal-entry">
          <strong>{{ entry.name }}</strong>
          <small>{{ kindLabel(entry.kind) }}</small>
        </article>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { NxEmpty, NxPageHeader, NxSkeleton } from "../../components";
import { api } from "../../api/client";

const rows = ref([]);
const error = ref("");
const loaded = ref(false);

const groups = computed(() => {
  const order = [];
  const byTitle = new Map();
  for (const entry of rows.value) {
    const title = entry.default_group || "未分组";
    if (!byTitle.has(title)) {
      byTitle.set(title, []);
      order.push(title);
    }
    byTitle.get(title).push(entry);
  }
  return order.map((title) => ({ title, entries: byTitle.get(title) }));
});

onMounted(load);

async function load() {
  error.value = "";
  try {
    const body = await api("/api/v1/p3/portal/entries");
    rows.value = Array.isArray(body?.entries) ? body.entries : [];
  } catch (cause) {
    rows.value = [];
    error.value = cause?.message || "功能入口暂时无法加载";
  } finally {
    loaded.value = true;
  }
}

function kindLabel(kind) {
  return { FILL: "填报", LIST: "列表", VIEW: "查看" }[kind] || "入口";
}
</script>

<style scoped>
.portal { display: grid; gap: 16px; }
section { display: grid; gap: 8px; padding: 16px; }
article { display: flex; justify-content: space-between; gap: 12px; }
</style>
