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

    <section class="nx-card section" data-testid="toast-gallery">
      <h2>Toast（S2）</h2>
      <p class="hint">共享 nxMessage.success / error / info；短文案，错误可带可复制 Trace。不替代字段错误。</p>
      <div class="tag-row">
        <button type="button" class="ghost" data-testid="toast-success" @click="nxMessage.success('已保存')">成功</button>
        <button type="button" class="ghost" data-testid="toast-info" @click="nxMessage.info('已复制追踪号')">提示</button>
        <button type="button" class="ghost" data-testid="toast-error" @click="nxMessage.error('操作失败', { traceId: 'dev-trace-gallery' })">失败 + Trace</button>
      </div>
    </section>

    <section class="nx-card section" data-testid="result-gallery">
      <h2>Result 壳（S3）</h2>
      <p class="hint">提交后结果态：图标 + 标题 + 描述 + 下一步槽；页卡自行决定何时展示。</p>
      <NxResult title="申请已提交" description="可在我的申请中查看进度。" compact>
        <template #next>
          <button type="button" class="primary">查看我的申请</button>
          <button type="button" class="ghost">返回已开通</button>
        </template>
      </NxResult>
    </section>

        <section class="nx-card section" data-testid="data-table-gallery">
      <h2>NxDataTable</h2>
      <p class="hint">§12 density + Empty / Skeleton / Error shell. Demo rows only.</p>
      <NxDataTable aria-label="gallery-table" testid="gallery-data-table" density="compact">
        <template #header>
          <tr><th>Name</th><th>Status</th></tr>
        </template>
        <tr><td>Row A</td><td><NxStatusTag status="ACTIVE" /></td></tr>
        <tr><td>Row B</td><td><NxStatusTag status="PENDING" /></td></tr>
      </NxDataTable>
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
  NxResult,
  NxStatusTag,
  nxMessage,
} from "../../components";
</script>

<style scoped>
.gallery-grid { display: grid; gap: var(--nx-space-4); }
.states { grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); }
.section { margin: var(--nx-space-4) var(--nx-space-0); }
h2 { margin: var(--nx-space-0) var(--nx-space-0) var(--nx-space-3); font-size: var(--nx-font-size-md); }
.hint { margin: 0 0 var(--nx-space-3); color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.tag-row { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); }
</style>
