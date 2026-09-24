<template>
  <NxPageHeader eyebrow="星域" title="星域" description="Nexus Tenant" />
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
      <NxPanelCard title="待处理">
        <NxTodoList :items="[]" />
      </NxPanelCard>
      <NxPanelCard title="近期活动">
        <NxActivityTimeline :items="[]" />
      </NxPanelCard>
      <NxPanelCard title="运行健康">
        <NxHealthList :items="[]" />
      </NxPanelCard>
    </template>
    <template #table>
      <NxPanelCard title="租户" description="只展示接口已返回的租户。">
        <template #actions>
          <router-link to="/p1/tenants">查看全部</router-link>
        </template>
        <NxEmpty v-if="!tenants.length" title="暂无" description="当前没有可展示的租户。" compact />
        <table v-else class="nx-table">
          <thead>
            <tr><th>名称</th><th>编码</th><th>状态</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in tenants.slice(0, 8)" :key="row.id || row.code">
              <td>{{ text(row.name || row.display_name) }}</td>
              <td>{{ text(row.code) }}</td>
              <td>{{ text(row.status) }}</td>
            </tr>
          </tbody>
        </table>
      </NxPanelCard>
    </template>
  </DashboardLayers>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { p1 } from "../../api/client";
import {
  NxActivityTimeline,
  NxChartPlaceholder,
  NxEmpty,
  NxHealthList,
  NxKpiRow,
  NxPageHeader,
  NxPanelCard,
  NxTodoList,
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
