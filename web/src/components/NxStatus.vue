<template>
  <span class="chip" :data-tone="tone">{{ label }}</span>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  tone: { type: String, default: "info" },
});
</script>

<style scoped>
.chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.chip[data-tone="success"] { background: var(--nx-success-light); color: var(--nx-success); }
.chip[data-tone="warning"] { background: var(--nx-warning-light); color: var(--nx-warning); }
.chip[data-tone="danger"] { background: var(--nx-danger-light); color: var(--nx-danger); }
.chip[data-tone="info"] { background: var(--nx-info-light); color: var(--nx-info); }
.chip[data-tone="neutral"] { background: var(--nx-divider); color: var(--nx-text-regular); }
</style>
