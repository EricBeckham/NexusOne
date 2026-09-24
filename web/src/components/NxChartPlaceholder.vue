<template>
  <section class="chart-placeholder" role="group" :aria-label="`${title}：${emptyLabel}`" data-testid="nx-chart-placeholder">
    <svg class="chart-grid" viewBox="0 0 640 220" preserveAspectRatio="none" aria-hidden="true">
      <path d="M48 24H624M48 66H624M48 108H624M48 150H624M48 192H624M48 24V192" />
    </svg>
    <div class="chart-empty-copy">
      <span class="chart-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none"><path d="M4 19V5m0 14h16M8 15v-3m4 3V8m4 7v-5m4 5V7" /></svg>
      </span>
      <strong>{{ emptyLabel }}</strong>
      <span v-if="description">{{ description }}</span>
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: "" },
  emptyLabel: { type: String, default: "暂无图表数据" },
});
</script>

<style scoped>
.chart-placeholder {
  position: relative;
  display: grid;
  place-items: center;
  min-width: 0;
  min-height: 220px;
  overflow: hidden;
  border-radius: var(--nx-radius-sm);
  background: var(--nx-surface);
}
.chart-grid { position: absolute; inset: 0; width: 100%; height: 100%; }
.chart-grid path { fill: none; stroke: var(--nx-divider); stroke-width: 1; vector-effect: non-scaling-stroke; }
.chart-empty-copy { position: relative; display: flex; flex-direction: column; align-items: center; gap: var(--nx-space-2); padding: var(--nx-space-4); color: var(--nx-text-secondary); text-align: center; }
.chart-empty-copy strong { color: var(--nx-text-regular); font-size: var(--nx-font-size-sm); font-weight: 600; }
.chart-mark { display: grid; place-items: center; width: 40px; height: 40px; color: var(--nx-text-disabled); background: var(--nx-bg-sidebar); border-radius: var(--nx-radius-pill); }
.chart-mark svg { width: 20px; height: 20px; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }
</style>
