<template>
  <main>
    <NxPageHeader
      eyebrow="P4 · NEXUS MARKET"
      title="星市工作台"
      description="浏览已审核发布的应用版本，并为当前租户安装所需 SKU。"
    >
      <template #actions>
        <router-link class="primary" to="/p4/market">打开应用目录</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <section v-else class="stats" aria-label="星市目录摘要">
      <NxStatCard label="已上架应用" :value="loading ? null : appCount" />
      <NxStatCard label="可用版本" :value="loading ? null : catalog.length" />
      <NxStatCard label="可选 SKU" :value="loading ? null : skuCount" />
    </section>

    <section class="nx-card catalog-preview">
      <h2>近期上架</h2>
      <NxEmpty
        v-if="!loading && !catalog.length && !error"
        title="暂无已发布应用"
        description="应用通过审核并发布后，会出现在这里。"
        compact
      />
      <ul v-else-if="catalog.length">
        <li v-for="item in catalog.slice(0, 5)" :key="item.version.id">
          <div>
            <strong>{{ item.app.name }}</strong>
            <span class="code">{{ item.app.code }} · {{ item.version.version }}</span>
          </div>
          <NxStatusTag status="ACTIVE" label="已发布" />
        </li>
      </ul>
      <p v-else-if="loading" class="nx-muted">正在加载目录…</p>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatCard, NxStatusTag } from "../../components";
import { p4 } from "../../api/client";

const catalog = ref([]);
const loading = ref(true);
const error = ref("");
const appCount = computed(() => new Set(catalog.value.map((item) => item.app.id)).size);
const skuCount = computed(() => catalog.value.reduce((total, item) => total + (item.skus?.length || 0), 0));

async function load() {
  loading.value = true;
  error.value = "";
  try {
    catalog.value = (await p4.catalog()) || [];
  } catch (err) {
    error.value = err.message || "星市目录暂时不可用";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--nx-space-4);
  margin-bottom: var(--nx-space-4);
}
.catalog-preview h2 { margin: var(--nx-space-0); font-size: var(--nx-font-size-md); }
ul { list-style: none; margin: var(--nx-space-3) var(--nx-space-0) var(--nx-space-0); padding: var(--nx-space-0); }
li { display: flex; align-items: center; justify-content: space-between; gap: var(--nx-space-3); padding: var(--nx-space-3) var(--nx-space-0); border-top: 1px solid var(--nx-divider); }
li > div { display: flex; flex-direction: column; gap: var(--nx-space-1); }
.code { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
</style>
