<template>
  <main class="operations-analytics" data-testid="operations-analytics">
    <NxPageHeader
      eyebrow="P4 · 星市"
      title="运营分析"
      description="轻量运营分析，非独立 BI。计数来自已有上架 / 订阅 / 安装列表 API，无假 KPI。"
    >
      <template #actions>
        <button class="ghost" type="button" :disabled="loading" data-testid="operations-refresh" @click="load">刷新</button>
        <router-link class="ghost" to="/p4/market">应用目录</router-link>
        <router-link class="ghost" to="/p4/entitlements">商业权益</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState
      v-if="error"
      data-testid="operations-error"
      :title="error"
      :trace-id="traceId"
      retry-label="重试"
      @retry="load"
    />

    <template v-else>
      <section class="stats" aria-label="运营计数" data-testid="operations-stats">
        <NxSkeleton v-if="loading" variant="list-row" label="正在加载运营摘要" data-testid="operations-skeleton" />
        <template v-else>
          <NxStatCard label="已上架应用" :value="appCount" data-testid="ops-stat-apps" />
          <NxStatCard label="订阅数" :value="subscriptionCount" data-testid="ops-stat-subscriptions" />
          <NxStatCard label="已开通安装" :value="installationCount" data-testid="ops-stat-installations" />
        </template>
      </section>

      <p v-if="tenantHint" class="nx-muted tenant-hint" data-testid="operations-tenant-hint">{{ tenantHint }}</p>

      <NxPanelCard title="上架摘要" description="来自应用目录（catalog）已发布条目。" data-testid="operations-catalog">
        <template #actions>
          <router-link to="/p4/market">查看目录</router-link>
        </template>
        <NxSkeleton v-if="loading" variant="list-row" label="正在加载目录" />
        <NxEmpty
          v-else-if="!catalog.length"
          data-testid="operations-catalog-empty"
          title="暂无已上架应用"
          description="应用通过审核并发布后，会出现在这里。"
          compact
        >
          <template #action>
            <router-link class="primary" to="/p4/market">前往应用目录</router-link>
          </template>
        </NxEmpty>
        <table v-else class="nx-table" data-testid="operations-catalog-table">
          <thead>
            <tr><th>应用</th><th>编码</th><th>版本</th><th>SKU 数</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in catalog.slice(0, 12)" :key="item.version?.id || item.app?.id">
              <td>{{ item.app?.name || "暂无" }}</td>
              <td class="nx-muted">{{ item.app?.code || "暂无" }}</td>
              <td>{{ item.version?.version || "暂无" }}</td>
              <td>{{ Array.isArray(item.skus) ? item.skus.length : 0 }}</td>
            </tr>
          </tbody>
        </table>
      </NxPanelCard>

      <NxPanelCard title="使用趋势" description="无时间序列读模型时不画假图。" data-testid="operations-trend">
        <p class="nx-muted" data-testid="operations-trend-note">趋势暂无读模型。本波不发明聚合服务，也不绘制假折线。</p>
      </NxPanelCard>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import {
  NxEmpty, NxErrorState, NxPageHeader, NxPanelCard, NxSkeleton, NxStatCard,
} from "../../components";
import { p4 } from "../../api/client";
import { session } from "../../api/session";

const catalog = ref([]);
const subscriptions = ref([]);
const installations = ref([]);
const loading = ref(true);
const error = ref("");
const traceId = ref("");
const tenantPartial = ref("");

const appCount = computed(() => new Set(catalog.value.map((item) => item.app?.id).filter(Boolean)).size);
const subscriptionCount = computed(() => (Array.isArray(subscriptions.value) ? subscriptions.value.length : 0));
const installationCount = computed(() => (Array.isArray(installations.value) ? installations.value.length : 0));
const tenantHint = computed(() => tenantPartial.value);

async function load() {
  loading.value = true;
  error.value = "";
  traceId.value = "";
  tenantPartial.value = "";
  try {
    catalog.value = (await p4.catalog()) || [];
  } catch (err) {
    catalog.value = [];
    error.value = err.message || "运营目录暂时不可用";
    traceId.value = err.traceId || "";
    loading.value = false;
    return;
  }

  if (!session.contextId) {
    subscriptions.value = [];
    installations.value = [];
    tenantPartial.value = "订阅与安装计数需租户上下文；当前未选择租户，仅展示上架摘要。";
  } else {
    try {
      const [subs, installs] = await Promise.all([p4.subscriptions(), p4.installations()]);
      subscriptions.value = Array.isArray(subs) ? subs : [];
      installations.value = Array.isArray(installs) ? installs : [];
    } catch (err) {
      subscriptions.value = [];
      installations.value = [];
      tenantPartial.value = err.message
        ? `订阅/安装列表暂不可用：${err.message}`
        : "订阅/安装列表暂不可用，上架计数仍可用。";
      traceId.value = err.traceId || "";
    }
  }
  loading.value = false;
}

onMounted(load);
</script>

<style scoped>
.operations-analytics {
  display: grid;
  gap: var(--nx-space-4);
}
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--nx-space-4);
}
.tenant-hint { margin: 0; font-size: var(--nx-font-size-sm); }
</style>
