<template>
  <main>
    <NxPageHeader
      eyebrow="DEV ONLY"
      title="公共组件示例"
      description="W1 / V1a 公共组件示例；指标无业务数据，图表位不绘制虚构趋势；本页不会进入生产路由。"
    >
      <template #actions><button type="button" class="primary">页面操作</button></template>
    </NxPageHeader>

    <NxKpiRow aria-label="KPI 行示例" :items="[
      { id: 'tenants', label: '租户总数', value: null },
      { id: 'healthy-services', label: '健康服务', value: null, tone: 'success' },
      { id: 'pending', label: '待处理', value: null, tone: 'warning' },
    ]" />

    <NxPanelCard class="section" title="经营趋势" eyebrow="面板卡 · 图表空态" description="连接真实聚合接口后展示趋势。">
      <template #actions><button type="button" class="ghost">时间范围</button></template>
      <NxChartPlaceholder title="经营趋势" description="数据接入后显示" />
    </NxPanelCard>

    <section class="nx-card section">
      <h2>状态标签</h2>
      <div class="tag-row">
        <NxStatusTag status="ACTIVE" />
        <NxStatusTag status="PENDING" />
        <NxStatusTag status="PROCESSING" />
        <NxStatusTag status="FAILED" />
        <NxStatusTag status="DISABLED" />
      </div>
    </section>

    <section class="gallery-grid states" aria-label="页面状态示例">
      <div class="nx-card"><NxEmpty description="创建第一条记录后会显示在这里" compact /></div>
      <div class="nx-card"><NxErrorState trace-id="dev-trace-id" /></div>
      <div class="nx-card"><NxNoPermission /></div>
    </section>
  </main>
</template>

<script setup>
import {
  NxEmpty,
  NxErrorState,
  NxChartPlaceholder,
  NxKpiRow,
  NxNoPermission,
  NxPanelCard,
  NxPageHeader,
  NxStatusTag,
} from "../../components";
</script>

<style scoped>
.gallery-grid { display: grid; gap: var(--nx-space-4); }
.states { grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); }
.section { margin: var(--nx-space-4) var(--nx-space-0); }
h2 { margin: var(--nx-space-0) var(--nx-space-0) var(--nx-space-3); font-size: var(--nx-font-size-md); }
.tag-row { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); }
</style>
