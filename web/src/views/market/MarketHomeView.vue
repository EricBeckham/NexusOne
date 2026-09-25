<template>
  <main>
    <NxPageHeader
      eyebrow="P4 · 星市"
      title="星市工作台"
      description="浏览已发布应用。无支付，订阅后即时开通。"
    >
      <template #actions>
        <router-link class="primary" to="/p4/market">打开应用目录</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <DashboardLayers>
      <template #kpi>
        <section v-if="!error" class="stats" aria-label="星市目录摘要">
          <NxStatCard label="已上架应用" :value="loading ? null : appCount" />
          <NxStatCard label="可用版本" :value="loading ? null : catalog.length" />
          <NxStatCard label="可选 SKU" :value="loading ? null : skuCount" />
        </section>
      </template>
      <template #charts>
        <NxPanelCard title="上架趋势" description="没有趋势聚合接口。">
          <NxChartPlaceholder title="上架趋势" />
        </NxPanelCard>
        <NxPanelCard title="目录构成" description="没有构成聚合接口。">
          <NxChartPlaceholder title="目录构成" />
        </NxPanelCard>
        <NxPanelCard title="版本状态" description="没有状态分布接口。">
          <NxChartPlaceholder title="版本状态" />
        </NxPanelCard>
      </template>
      <template #lists>
        <NxPanelCard title="待处理">
          <NxTodoList :items="[]" />
        </NxPanelCard>
        <NxPanelCard title="近期上架">
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
        </NxPanelCard>
        <NxPanelCard title="运行健康">
          <NxHealthList :items="[]" />
        </NxPanelCard>
      </template>
      <template #table>
        <NxPanelCard title="已发布版本" description="只展示目录接口已返回的版本。">
          <template #actions>
            <router-link to="/p4/market">查看全部</router-link>
          </template>
          <NxEmpty v-if="!loading && !catalog.length && !error" title="暂无" description="当前没有可展示的版本。" compact />
          <table v-else-if="catalog.length" class="nx-table">
            <thead>
              <tr><th>应用</th><th>编码</th><th>版本</th><th>状态</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in catalog.slice(0, 8)" :key="item.version.id">
                <td>{{ item.app.name || "暂无" }}</td>
                <td>{{ item.app.code || "暂无" }}</td>
                <td>{{ item.version.version || "暂无" }}</td>
                <td>已发布</td>
              </tr>
            </tbody>
          </table>
        </NxPanelCard>
      </template>
    </DashboardLayers>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import {
  NxChartPlaceholder,
  NxEmpty,
  NxErrorState,
  NxHealthList,
  NxPageHeader,
  NxPanelCard,
  NxStatCard,
  NxStatusTag,
  NxTodoList,
} from "../../components";
import { p4 } from "../../api/client";
import DashboardLayers from "../DashboardLayers.vue";

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
}
ul { list-style: none; margin: 0; padding: 0; }
li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--nx-space-3);
  padding: var(--nx-space-3) 0;
  border-top: 1px solid var(--nx-divider);
}
li > div { display: flex; flex-direction: column; gap: var(--nx-space-1); }
.code { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
</style>
