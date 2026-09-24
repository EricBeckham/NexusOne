<template>
  <article class="stat-card" :data-tone="tone" data-testid="nx-stat-card">
    <div class="topline">
      <span class="label">{{ label }}</span>
      <slot name="extra" />
    </div>
    <div class="value-row">
      <strong class="value">{{ displayValue }}</strong>
      <span v-if="unit && displayValue !== emptyText" class="unit">{{ unit }}</span>
    </div>
    <p v-if="hint" class="hint">{{ hint }}</p>
  </article>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: null },
  unit: { type: String, default: "" },
  hint: { type: String, default: "" },
  emptyText: { type: String, default: "暂无" },
  tone: {
    type: String,
    default: "product",
    validator: (value) => ["product", "success", "warning", "danger", "neutral"].includes(value),
  },
});

const displayValue = computed(() => (
  props.value === null || props.value === undefined || props.value === "" ? props.emptyText : props.value
));
</script>

<style scoped>
.stat-card {
  min-width: var(--nx-space-0);
  padding: var(--nx-space-5);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius);
  background: var(--nx-surface);
  box-shadow: var(--nx-shadow-card);
  border-top-color: var(--tone-color, var(--nx-product));
  border-top-width: var(--nx-space-1);
}
.stat-card[data-tone="success"] { --tone-color: var(--nx-success); }
.stat-card[data-tone="warning"] { --tone-color: var(--nx-warning); }
.stat-card[data-tone="danger"] { --tone-color: var(--nx-danger); }
.stat-card[data-tone="neutral"] { --tone-color: var(--nx-text-secondary); }
.topline { display: flex; justify-content: space-between; gap: var(--nx-space-2); }
.label, .hint, .unit { color: var(--nx-text-secondary); }
.label { font-size: var(--nx-font-size-sm); }
.value-row { display: flex; align-items: baseline; gap: var(--nx-space-1); margin-top: var(--nx-space-2); }
.value { color: var(--nx-text-primary); font-size: var(--nx-font-size-stat); line-height: 1.2; }
.unit { font-size: var(--nx-font-size-sm); }
.hint { margin: var(--nx-space-2) var(--nx-space-0) var(--nx-space-0); font-size: var(--nx-font-size-xs); }
</style>
