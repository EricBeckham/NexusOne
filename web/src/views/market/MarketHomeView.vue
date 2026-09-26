<template>
  <main class="market-home">
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
    <!-- S8: real numbers and data only — no chart placeholders or always-empty 待处理 / 运行健康 stubs. -->
    <section v-if="!error" class="stats" aria-label="星市目录摘要">
      <NxStatCard label="已上架应用" :value="loading ? null : appCount" />
      <NxStatCard label="可用版本" :value="loading ? null : catalog.length" />
      <NxStatCard label="可选 SKU" :value="loading ? null : skuCount" />
    </section>

    <div class="home-row">
      <NxPanelCard title="已发布版本" description="最近可订阅的应用版本。" data-testid="market-home-versions">
        <template #actions>
          <router-link to="/p4/market">查看全部</router-link>
        </template>
        <NxSkeleton v-if="loading" variant="list-row" label="正在加载目录" />
        <NxEmpty v-else-if="!catalog.length && !error" title="暂无已发布应用" description="应用通过审核并发布后，会出现在这里，并可在应用目录订阅开通。" compact />
        <table v-else-if="catalog.length" class="nx-table">
          <thead>
            <tr><th>应用</th><th>编码</th><th>版本</th><th>状态</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in catalog.slice(0, 8)" :key="item.version.id">
              <td class="app-name">{{ item.app.name || "暂无" }}</td>
              <td class="nx-code code">{{ item.app.code || "暂无" }}</td>
              <td>{{ item.version.version || "暂无" }}</td>
              <td><NxStatusTag status="PUBLISHED" label="已发布" /></td>
            </tr>
          </tbody>
        </table>
      </NxPanelCard>

      <NxPanelCard title="快速开始" data-testid="market-home-start">
        <ol class="start-steps">
          <li><router-link to="/p4/market"><strong>应用目录</strong><small>选择 SKU，无支付即时开通</small></router-link></li>
          <li><router-link to="/p4/installations"><strong>我的已开通</strong><small>对已开通产品发起申请</small></router-link></li>
          <li><router-link to="/p3/todos?box=requests"><strong>我的申请</strong><small>查看审批进度与意见</small></router-link></li>
        </ol>
      </NxPanelCard>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxPanelCard, NxSkeleton, NxStatCard, NxStatusTag } from "../../components";
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
.market-home { display: grid; gap: var(--nx-space-4); }
.stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--nx-space-4); }
.home-row { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: var(--nx-space-4); align-items: start; }
.app-name { color: var(--nx-text-primary); font-weight: 500; }
.code { color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.start-steps { display: grid; gap: var(--nx-space-2); margin: 0; padding: 0; list-style: none; counter-reset: step; }
.start-steps li { counter-increment: step; }
.start-steps a { display: grid; grid-template-columns: 24px minmax(0, 1fr); column-gap: var(--nx-space-3); padding: var(--nx-space-2); border-radius: var(--nx-radius-sm); color: inherit; text-decoration: none; }
.start-steps a::before { content: counter(step); grid-row: span 2; display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; background: var(--nx-product-light); color: var(--nx-product); font-size: var(--nx-font-size-xs); font-weight: 600; }
.start-steps a:hover { background: var(--nx-bg-page); }
.start-steps strong { color: var(--nx-text-primary); font-weight: 500; }
.start-steps small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
@media (max-width: 1100px) { .home-row { grid-template-columns: 1fr; } }
</style>