<template>
  <form class="nx-filter-bar" role="search" :aria-label="label" @submit.prevent="$emit('search')">
    <div class="row">
      <div class="fields"><slot /></div>
      <div class="actions">
        <button v-if="searchable" class="primary" type="submit" :data-testid="`${testid}-search`">查询</button>
        <button class="ghost" type="button" :data-testid="`${testid}-reset`" @click="$emit('reset')">重置</button>
        <button
          v-if="$slots.more"
          class="more-toggle"
          type="button"
          :aria-expanded="moreOpen"
          :data-testid="`${testid}-more`"
          @click="moreOpen = !moreOpen"
        >
          更多筛选<NxIcon :name="moreOpen ? 'arrowUp' : 'chevronDown'" :size="14" />
        </button>
      </div>
      <p v-if="$slots.summary" class="summary" aria-live="polite"><slot name="summary" /></p>
    </div>
    <div v-if="$slots.more && moreOpen" class="more" :data-testid="`${testid}-more-panel`"><slot name="more" /></div>
  </form>
</template>

<script setup>
import { ref } from "vue";
import NxIcon from "../icons/NxIcon.vue";

/**
 * Spec §11 FilterBar: one flat row — [conditions…] [查询][重置] (更多筛选) … summary — never a big Card.
 * Up to five common conditions go in the default slot (wrap each in `.nx-filter-field`); advanced ones in #more.
 * `searchable` shows 查询 for pages that filter on submit; pages that filter as you type leave it off (Enter still submits).
 * Keeping conditions in the URL query and resetting to defaults stays with the page, which owns the filter state.
 */
defineProps({
  label: { type: String, default: "筛选" },
  searchable: { type: Boolean, default: false },
  testid: { type: String, default: "filter" },
});
defineEmits(["search", "reset"]);

const moreOpen = ref(false);
</script>

<style scoped>
.nx-filter-bar {
  display: grid;
  gap: var(--nx-space-3);
  padding-bottom: var(--nx-space-4);
  border-bottom: 1px solid var(--nx-divider, var(--nx-border));
}
.row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--nx-space-3); }
.fields { display: flex; flex-wrap: wrap; align-items: center; gap: var(--nx-space-3); min-width: 0; }
.actions { display: flex; align-items: center; gap: var(--nx-space-2); }
.more-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 4px;
  border: 0;
  background: none;
  color: var(--nx-primary);
  cursor: pointer;
  font-size: var(--nx-font-size-sm, 13px);
}
.summary { margin: 0 0 0 auto; color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm, 13px); white-space: nowrap; }
.more { display: flex; flex-wrap: wrap; gap: var(--nx-space-3); }
/* Condition: inline label + 36px control, so a row of conditions stays one line high. */
.nx-filter-bar :slotted(.nx-filter-field) {
  display: inline-flex;
  align-items: center;
  gap: var(--nx-space-2);
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-sm, 13px);
  white-space: nowrap;
}
.nx-filter-bar :slotted(.nx-filter-field input),
.nx-filter-bar :slotted(.nx-filter-field select) {
  height: 36px;
  min-width: 200px;
  padding: 0 var(--nx-space-3);
  color: var(--nx-text-primary);
  background: var(--nx-surface);
  border: 1px solid var(--nx-border-strong);
  border-radius: 6px;
}
.nx-filter-bar :slotted(.nx-filter-field.wide input) { width: min(420px, 60vw); }
@media (max-width: 640px) {
  .row, .fields { align-items: stretch; flex-direction: column; }
  .summary { margin-left: 0; }
  .nx-filter-bar :slotted(.nx-filter-field.wide input) { width: 100%; }
}
</style>
