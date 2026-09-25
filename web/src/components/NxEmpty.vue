<template>
  <section
    class="nx-empty"
    :class="{ compact }"
    role="status"
    data-testid="nx-empty"
  >
    <!-- §12.5 / §23 / O5: icon + explanation + next-step. Slots: icon, next (preferred), action (compat). -->
    <div class="icon" data-testid="nx-empty-icon" aria-hidden="true">
      <slot name="icon">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="M4 7.5h16v11H4zM4 13h4l1.5 2h5l1.5-2h4M8 7.5l1-3h6l1 3" />
        </svg>
      </slot>
    </div>
    <strong class="title" data-testid="nx-empty-title">{{ title }}</strong>
    <p v-if="description" class="description" data-testid="nx-empty-description">{{ description }}</p>
    <div v-if="hasNext" class="next" data-testid="nx-empty-next">
      <slot name="next">
        <slot name="action" />
      </slot>
    </div>
  </section>
</template>

<script setup>
import { computed, useSlots } from "vue";

defineProps({
  title: { type: String, default: "暂无数据" },
  description: { type: String, default: "" },
  compact: { type: Boolean, default: false },
});

const slots = useSlots();
const hasNext = computed(() => Boolean(slots.next || slots.action));
</script>

<style scoped>
.nx-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--nx-space-2);
  padding: var(--nx-space-10) var(--nx-space-4);
  color: var(--nx-text-secondary);
  text-align: center;
}
.nx-empty.compact { padding: var(--nx-space-6) var(--nx-space-3); }
.icon {
  width: var(--nx-state-icon);
  height: var(--nx-state-icon);
  color: var(--nx-text-disabled);
}
.icon :deep(svg) { width: 100%; height: 100%; stroke: currentColor; stroke-width: 1.5; }
.title { color: var(--nx-text-primary); font-size: var(--nx-font-size-md); font-weight: 600; }
.description { margin: var(--nx-space-0); max-width: 28rem; line-height: 1.5; }
.next { margin-top: var(--nx-space-2); display: flex; flex-wrap: wrap; gap: var(--nx-space-2); justify-content: center; }
</style>
