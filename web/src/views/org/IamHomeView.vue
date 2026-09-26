<template>
  <NxPageHeader eyebrow="星权" title="星权" description="Nexus IAM" />
  <DashboardLayers>
    <template #kpi>
      <NxKpiRow aria-label="星权指标" :items="kpiItems" />
    </template>
    <template #charts>
      <NxPanelCard title="用户趋势" description="没有趋势聚合接口。">
        <NxChartPlaceholder title="用户趋势" />
      </NxPanelCard>
      <NxPanelCard title="组织构成" description="没有构成聚合接口。">
        <NxChartPlaceholder title="组织构成" />
      </NxPanelCard>
      <NxPanelCard title="账号状态" description="没有状态分布接口。">
        <NxChartPlaceholder title="账号状态" />
      </NxPanelCard>
    </template>
    <template #lists>
      <NxPanelCard title="待处理" data-testid="home-todos-panel">
        <NxEmpty
          title="暂无待办读模型"
          description="星权首页还不提供待办列表。需要管理成员时，请到用户目录。"
          compact
        >
          <template #next><router-link class="primary" to="/p2/persons">打开用户</router-link></template>
        </NxEmpty>
      </NxPanelCard>
      <NxPanelCard title="近期活动" data-testid="home-activity-panel">
        <NxEmpty
          title="暂无活动动态"
          description="当前没有活动时间线接口，因此不展示空列表占位。"
          compact
        >
          <template #next><router-link class="ghost" to="/p2/persons">查看用户</router-link></template>
        </NxEmpty>
      </NxPanelCard>
      <NxPanelCard title="运行健康" data-testid="home-health-panel">
        <NxEmpty
          title="暂无健康探针"
          description="首页不拼接假健康项。组织与岗位可在对应页面查看。"
          compact
        >
          <template #next><router-link class="ghost" to="/p2/org">打开组织架构</router-link></template>
        </NxEmpty>
      </NxPanelCard>
    </template>
    <template #table>
      <NxPanelCard title="用户" description="只展示接口已返回的用户。">
        <template #actions>
          <router-link to="/p2/persons">查看全部</router-link>
        </template>
        <NxEmpty v-if="contextMissing" title="暂无" description="需要租户上下文" compact />
        <NxEmpty v-else-if="!persons.length" title="暂无" description="当前没有可展示的用户。" compact />
        <table v-else class="nx-table">
          <thead>
            <tr><th>姓名</th><th>邮箱</th><th>状态</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in persons.slice(0, 8)" :key="row.id || row.email">
              <td>{{ text(row.display_name || row.displayName || row.name) }}</td>
              <td>{{ text(row.email) }}</td>
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
import { p2 } from "../../api/client";
import { session } from "../../api/session";
import {
  NxChartPlaceholder,
  NxEmpty,
  NxKpiRow,
  NxPageHeader,
  NxPanelCard,
} from "../../components";
import { countTree, realCount } from "../homeStats";
import DashboardLayers from "../DashboardLayers.vue";

const personCount = ref(null);
const personHint = ref("");
const departmentCount = ref(null);
const departmentHint = ref("");
const positionCount = ref(null);
const positionHint = ref("");
const persons = ref([]);
const contextMissing = ref(false);

const kpiItems = computed(() => [
  { id: "persons", label: "用户", value: personCount.value, unit: "人", hint: personHint.value },
  { id: "departments", label: "部门", value: departmentCount.value, unit: "个", hint: departmentHint.value },
  { id: "positions", label: "岗位", value: positionCount.value, unit: "个", hint: positionHint.value },
]);

function text(value) {
  return value === null || value === undefined || value === "" ? "暂无" : String(value);
}

onMounted(async () => {
  if (!session.contextId) {
    contextMissing.value = true;
    const hint = "需要租户上下文";
    personHint.value = hint;
    departmentHint.value = hint;
    positionHint.value = hint;
    return;
  }
  const [personResult, departments, positions] = await Promise.allSettled([
    p2.persons(),
    p2.departments(),
    p2.positions(),
  ]);
  if (personResult.status === "fulfilled" && Array.isArray(personResult.value)) {
    persons.value = personResult.value;
    personCount.value = realCount(personResult.value);
  } else if (personResult.status === "fulfilled") {
    personHint.value = "接口没有返回列表";
  } else {
    personHint.value = "接口不可用";
  }
  if (departments.status === "fulfilled") {
    departmentCount.value = countTree(departments.value);
    if (departmentCount.value === null) {
      departmentHint.value = "接口没有返回列表";
    }
  } else {
    departmentHint.value = "接口不可用";
  }
  if (positions.status === "fulfilled") {
    positionCount.value = realCount(positions.value);
    if (positionCount.value === null) {
      positionHint.value = "接口没有返回列表";
    }
  } else {
    positionHint.value = "接口不可用";
  }
});
</script>
