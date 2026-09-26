<template>
  <main class="tenant-home-page">
    <NxPageHeader
      eyebrow="星域 · 租户中心"
      title="星域"
      description="平台租户总览。指标与列表只展示接口已返回的数据。"
      :crumbs="[{ label: '星域' }]"
    >
      <template #actions>
        <router-link class="primary" to="/p1/tenants">租户目录</router-link>
      </template>
    </NxPageHeader>

    <DashboardLayers>
      <template #kpi>
        <NxKpiRow aria-label="星域指标" :items="kpiItems" />
      </template>
      <template #charts>
        <NxPanelCard title="租户趋势" description="没有趋势聚合接口。">
          <NxChartPlaceholder title="租户趋势" />
        </NxPanelCard>
        <NxPanelCard title="租户构成" description="没有构成聚合接口。">
          <NxChartPlaceholder title="租户构成" />
        </NxPanelCard>
        <NxPanelCard title="租户状态" description="没有状态分布接口。">
          <NxChartPlaceholder title="租户状态" />
        </NxPanelCard>
      </template>
      <template #lists>
        <NxPanelCard title="待处理" data-testid="home-todos-panel">
          <NxEmpty
            title="暂无待办读模型"
            description="星域首页还不提供待办列表。需要处理租户时，请到租户目录查看。"
            compact
          >
            <template #next><router-link class="primary" to="/p1/tenants">打开租户目录</router-link></template>
          </NxEmpty>
        </NxPanelCard>
        <NxPanelCard title="近期活动" data-testid="home-activity-panel">
          <NxEmpty
            title="暂无活动动态"
            description="当前没有活动时间线接口，因此不展示空列表占位。"
            compact
          >
            <template #next><router-link class="ghost" to="/p1/audit">查看平台审计</router-link></template>
          </NxEmpty>
        </NxPanelCard>
        <NxPanelCard title="运行健康" data-testid="home-health-panel">
          <NxEmpty
            title="暂无健康探针"
            description="首页不拼接假健康项。平台服务态可在对应页面查看。"
            compact
          >
            <template #next><router-link class="ghost" to="/p1/services">打开平台服务态</router-link></template>
          </NxEmpty>
        </NxPanelCard>
      </template>
      <template #table>
        <NxPanelCard title="租户" description="只展示接口已返回的租户。">
          <template #actions>
            <router-link class="text-link" to="/p1/tenants">查看全部</router-link>
          </template>
          <NxEmpty v-if="!tenants.length" title="暂无租户" description="当前没有可展示的租户。可前往租户目录新建。" compact>
            <template #next><router-link class="primary" to="/p1/tenants">租户目录</router-link></template>
          </NxEmpty>
          <div v-else class="table-scroll">
            <table class="nx-table nx-table--compact" data-testid="tenant-home-table">
              <thead>
                <tr><th scope="col">名称</th><th scope="col">编码</th><th scope="col">状态</th></tr>
              </thead>
              <tbody>
                <tr v-for="row in tenants.slice(0, 8)" :key="row.id || row.code">
                  <td>{{ text(row.name || row.display_name) }}</td>
                  <td class="nx-code">{{ text(row.code) }}</td>
                  <td><NxStatusTag :status="row.status || ''" :label="statusLabel(row.status)" /></td>
                </tr>
              </tbody>
            </table>
          </div>
        </NxPanelCard>
      </template>
    </DashboardLayers>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { p1 } from "../../api/client";
import {
  NxChartPlaceholder,
  NxEmpty,
  NxKpiRow,
  NxPageHeader,
  NxPanelCard,
  NxStatusTag,
} from "../../components";
import { realCount, recordName } from "../homeStats";
import DashboardLayers from "../DashboardLayers.vue";

const tenantCount = ref(null);
const tenantHint = ref("");
const platformName = ref(null);
const platformHint = ref("");
const tenants = ref([]);

const kpiItems = computed(() => [
  { id: "tenants", label: "租户数", value: tenantCount.value, unit: "个", hint: tenantHint.value },
  { id: "platform", label: "平台租户", value: platformName.value, hint: platformHint.value },
]);

function text(value) {
  return value === null || value === undefined || value === "" ? "暂无" : String(value);
}

function statusLabel(status) {
  return ({ ENABLED: "启用", DISABLED: "停用", PENDING: "待处理" })[status] || status || "暂无";
}

onMounted(async () => {
  const [tenantResult, platform] = await Promise.allSettled([p1.listTenants(), p1.platformTenant()]);
  if (tenantResult.status === "fulfilled" && Array.isArray(tenantResult.value)) {
    tenants.value = tenantResult.value;
    tenantCount.value = realCount(tenantResult.value);
  } else if (tenantResult.status === "fulfilled") {
    tenantHint.value = "接口没有返回列表";
  } else {
    tenantHint.value = "接口不可用";
  }
  if (platform.status === "fulfilled") {
    platformName.value = recordName(platform.value);
    if (platformName.value === null) {
      platformHint.value = "接口没有返回名称";
    }
  } else {
    platformHint.value = "接口不可用";
  }
});
</script>

<style scoped>
.tenant-home-page { display: grid; gap: var(--nx-space-4); }
.tenant-home-page :deep(.page-header) { margin-bottom: 0; }
.table-scroll { overflow-x: auto; }
.text-link { color: var(--nx-primary); text-decoration: none; }
.primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--nx-control-height);
  padding: 0 var(--nx-space-3);
  border-radius: var(--nx-control-radius);
  color: #fff;
  background: var(--nx-product);
  border: 1px solid var(--nx-product);
  text-decoration: none;
}
</style>
