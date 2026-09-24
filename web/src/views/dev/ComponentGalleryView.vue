<template>
  <main>
    <NxPageHeader
      eyebrow="DEV ONLY"
      title="公共组件示例"
      description="W1 公共组件最小集；本页不会进入生产路由。"
    >
      <template #actions><button type="button" class="primary">页面操作</button></template>
    </NxPageHeader>

    <section class="gallery-grid stats" aria-label="统计卡示例">
      <NxStatCard label="租户数" :value="12" unit="个" hint="示例数据，仅组件开发使用" />
      <NxStatCard label="健康服务" :value="8" unit="项" tone="success" />
      <NxStatCard label="待处理" tone="warning" />
    </section>

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
  NxNoPermission,
  NxPageHeader,
  NxStatCard,
  NxStatusTag,
} from "../../components";
</script>

<style scoped>
.gallery-grid { display: grid; gap: var(--nx-space-4); }
.stats { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); }
.states { grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); }
.section { margin: var(--nx-space-4) var(--nx-space-0); }
h2 { margin: var(--nx-space-0) var(--nx-space-0) var(--nx-space-3); font-size: var(--nx-font-size-md); }
.tag-row { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); }
</style>
