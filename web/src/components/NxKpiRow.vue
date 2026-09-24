<template>
  <section class="kpi-row" :aria-label="ariaLabel" data-testid="nx-kpi-row">
    <NxEmpty v-if="!items.length" title="暂无统计" description="当前没有可展示的统计指标。" compact />
    <NxStatCard
      v-for="item in items"
      :key="item.id || item.label"
      :label="item.label"
      :value="item.value"
      :unit="item.unit"
      :hint="item.hint"
      :tone="item.tone || 'product'"
    >
      <template v-if="item.icon" #extra>
        <span class="kpi-icon" aria-hidden="true"><component :is="item.icon" /></span>
      </template>
    </NxStatCard>
  </section>
</template>

<script setup>
import NxEmpty from "./NxEmpty.vue";
import NxStatCard from "./NxStatCard.vue";

defineProps({
  items: { type: Array, default: () => [] },
  ariaLabel: { type: String, default: "关键指标" },
});
</script>

<style scoped>
.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 180px), 1fr));
  gap: var(--nx-space-4);
  min-width: 0;
}
.kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--nx-product);
  background: var(--nx-product-light);
  border-radius: var(--nx-radius-pill);
}
.kpi-icon :deep(svg) { width: 18px; height: 18px; }
</style>
