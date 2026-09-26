<template>
  <!-- S3: post-submit result shell — pages decide when to show; no route binding. -->
  <section
    class="nx-result"
    :class="[`nx-result--${tone}`, { compact }]"
    role="status"
    data-testid="nx-result"
    :data-tone="tone"
  >
    <div class="nx-result__icon" data-testid="nx-result-icon" aria-hidden="true">
      <slot name="icon">
        <svg v-if="tone === 'success'" viewBox="0 0 48 48" fill="none">
          <circle cx="24" cy="24" r="20" stroke="currentColor" stroke-width="2" />
          <path d="M15 24.5 21.5 31 33 18" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <svg v-else-if="tone === 'warning'" viewBox="0 0 48 48" fill="none">
          <path d="M24 8 42 40H6L24 8Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
          <path d="M24 20v10" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
          <circle cx="24" cy="35" r="1.5" fill="currentColor" />
        </svg>
        <svg v-else viewBox="0 0 48 48" fill="none">
          <circle cx="24" cy="24" r="20" stroke="currentColor" stroke-width="2" />
          <path d="M24 16v12" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
          <circle cx="24" cy="33" r="1.5" fill="currentColor" />
        </svg>
      </slot>
    </div>
    <strong class="nx-result__title" data-testid="nx-result-title">{{ title }}</strong>
    <p v-if="description" class="nx-result__description" data-testid="nx-result-description">{{ description }}</p>
    <div v-if="hasNext" class="nx-result__next" data-testid="nx-result-next">
      <slot name="next">
        <slot name="action" />
      </slot>
    </div>
  </section>
</template>

<script setup>
import { computed, useSlots } from "vue";

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: "" },
  /** success | info | warning — default success for post-submit shells. */
  tone: { type: String, default: "success" },
  compact: { type: Boolean, default: false },
});

const slots = useSlots();
const hasNext = computed(() => Boolean(slots.next || slots.action));
</script>

<style scoped>
.nx-result {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--nx-space-2);
  padding: var(--nx-space-10) var(--nx-space-4);
  text-align: center;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius);
  background: var(--nx-surface);
}
.nx-result.compact { padding: var(--nx-space-6) var(--nx-space-3); }
.nx-result__icon {
  width: var(--nx-state-icon);
  height: var(--nx-state-icon);
  color: var(--nx-success);
}
.nx-result--info .nx-result__icon { color: var(--nx-info); }
.nx-result--warning .nx-result__icon { color: var(--nx-warning); }
.nx-result__icon :deep(svg) { width: 100%; height: 100%; }
.nx-result__title {
  color: var(--nx-text-primary);
  font-size: var(--nx-font-size-lg);
  font-weight: 650;
}
.nx-result__description {
  margin: 0;
  max-width: 28rem;
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-sm);
  line-height: 1.5;
}
.nx-result__next {
  margin-top: var(--nx-space-3);
  display: flex;
  flex-wrap: wrap;
  gap: var(--nx-space-2);
  justify-content: center;
}
</style>
