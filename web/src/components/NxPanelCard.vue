<template>
  <section class="panel-card" :aria-labelledby="titleId" data-testid="nx-panel-card">
    <header class="panel-header">
      <div class="panel-heading">
        <slot name="eyebrow"><span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span></slot>
        <h2 :id="titleId">{{ title }}</h2>
        <p v-if="description" class="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="panel-actions"><slot name="actions" /></div>
    </header>
    <div class="panel-body"><slot /></div>
    <footer v-if="$slots.footer" class="panel-footer"><slot name="footer" /></footer>
  </section>
</template>

<script setup>
import { useId } from "vue";

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: "" },
  eyebrow: { type: String, default: "" },
  titleId: { type: String, default: "" },
});
const generatedTitleId = useId();
const titleId = props.titleId || generatedTitleId;
</script>

<style scoped>
.panel-card {
  min-width: 0;
  padding: var(--nx-space-5);
  background: var(--nx-surface);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius);
  box-shadow: var(--nx-shadow-card);
}
.panel-header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--nx-space-4); }
.panel-heading { min-width: 0; }
h2 { margin: 0; color: var(--nx-text-primary); font-size: var(--nx-font-size-md); font-weight: 650; }
.eyebrow { display: block; margin-bottom: var(--nx-space-1); color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.description { margin: var(--nx-space-1) 0 0; color: var(--nx-text-secondary); }
.panel-actions { display: flex; align-items: center; gap: var(--nx-space-2); flex: none; }
.panel-body { min-width: 0; margin-top: var(--nx-space-4); }
.panel-footer { margin-top: var(--nx-space-4); padding-top: var(--nx-space-3); border-top: 1px solid var(--nx-divider); }
</style>
