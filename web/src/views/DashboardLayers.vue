<template>
  <div class="dashboard-home" data-testid="dashboard-layers">
    <section class="layer layer-kpi" aria-label="关键指标">
      <slot name="kpi" />
    </section>
    <section class="layer layer-charts" aria-label="趋势与状态">
      <slot name="charts" />
    </section>
    <section class="layer layer-lists" aria-label="待办、活动与健康">
      <slot name="lists" />
    </section>
    <section class="layer layer-table" aria-label="重点对象">
      <slot name="table" />
    </section>
  </div>
</template>

<style scoped>
.dashboard-home {
  display: grid;
  gap: var(--nx-space-4);
}
.layer-charts,
.layer-lists {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--nx-space-4);
}
@media (max-width: 1100px) {
  .layer-charts,
  .layer-lists {
    grid-template-columns: 1fr;
  }
}
</style>
