<template>
  <header class="page-header" data-testid="nx-page-header">
    <div class="heading">
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: "" },
  eyebrow: { type: String, default: "" },
});
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--nx-space-4);
  margin-bottom: var(--nx-space-4);
}
.heading { min-width: var(--nx-space-0); }
.eyebrow {
  margin: var(--nx-space-0) var(--nx-space-0) var(--nx-space-1);
  color: var(--nx-product);
  font-size: var(--nx-font-size-xs);
  font-weight: 650;
}
h1 {
  margin: var(--nx-space-0);
  color: var(--nx-text-primary);
  font-size: var(--nx-font-size-page-title);
  font-weight: 700;
}
.description {
  margin: var(--nx-space-1) var(--nx-space-0) var(--nx-space-0);
  color: var(--nx-text-secondary);
}
.actions {
  display: flex;
  align-items: center;
  gap: var(--nx-space-2);
  flex: none;
}
</style>
