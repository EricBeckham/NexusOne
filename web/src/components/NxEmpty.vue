<template>
  <section class="state" :class="{ compact }" role="status" data-testid="nx-empty">
    <div class="icon" aria-hidden="true">
      <slot name="icon">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="M4 7.5h16v11H4zM4 13h4l1.5 2h5l1.5-2h4M8 7.5l1-3h6l1 3" />
        </svg>
      </slot>
    </div>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <div v-if="$slots.action" class="action"><slot name="action" /></div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: "暂无数据" },
  description: { type: String, default: "" },
  compact: { type: Boolean, default: false },
});
</script>

<style scoped>
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--nx-space-2);
  padding: var(--nx-space-10) var(--nx-space-4);
  color: var(--nx-text-secondary);
  text-align: center;
}
.state.compact { padding: var(--nx-space-6) var(--nx-space-3); }
.icon {
  width: var(--nx-state-icon);
  height: var(--nx-state-icon);
  color: var(--nx-text-disabled);
}
.icon :deep(svg) { width: 100%; height: 100%; stroke: currentColor; stroke-width: 1.5; }
strong { color: var(--nx-text-primary); }
p { margin: var(--nx-space-0); }
.action { margin-top: var(--nx-space-2); }
</style>
