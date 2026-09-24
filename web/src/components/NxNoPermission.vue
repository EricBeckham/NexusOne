<template>
  <section class="state" role="status" data-testid="nx-no-permission">
    <div class="icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none">
        <rect x="5" y="10" width="14" height="10" rx="2" />
        <path d="M8 10V7a4 4 0 018 0v3M12 14v2" />
      </svg>
    </div>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <div v-if="$slots.action" class="action"><slot name="action" /></div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: "暂无权限" },
  description: { type: String, default: "请联系管理员申请访问权限" },
});
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
.icon { width: var(--nx-state-icon); height: var(--nx-state-icon); color: var(--nx-warning); }
.icon svg { width: 100%; height: 100%; stroke: currentColor; stroke-width: 1.5; }
strong { color: var(--nx-text-primary); }
p { margin: var(--nx-space-0); }
.action { margin-top: var(--nx-space-2); }
</style>
