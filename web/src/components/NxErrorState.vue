<template>
  <section class="state" role="alert" data-testid="nx-error-state">
    <div class="icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v6M12 17h.01" />
      </svg>
    </div>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <code v-if="traceId">Trace ID: {{ traceId }}</code>
    <div class="action">
      <slot name="action">
        <button v-if="retryLabel" type="button" class="primary" @click="$emit('retry')">{{ retryLabel }}</button>
      </slot>
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: "加载失败" },
  description: { type: String, default: "请稍后重试" },
  traceId: { type: String, default: "" },
  retryLabel: { type: String, default: "重试" },
});

defineEmits(["retry"]);
</script>

<style scoped>
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--nx-space-2);
  padding: var(--nx-space-10) var(--nx-space-4);
  color: var(--nx-text-secondary);
  text-align: center;
}
.icon { width: var(--nx-state-icon); height: var(--nx-state-icon); color: var(--nx-danger); }
.icon svg { width: 100%; height: 100%; stroke: currentColor; stroke-width: 1.5; }
strong { color: var(--nx-text-primary); }
p { margin: var(--nx-space-0); }
code { color: var(--nx-text-regular); font-size: var(--nx-font-size-xs); }
.action { margin-top: var(--nx-space-2); }
</style>
