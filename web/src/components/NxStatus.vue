<template>
  <span class="nx-status" :data-tone="tone">{{ label }}</span>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  tone: { type: String, default: "info" },
});
</script>

<style scoped>
/* §2.3 / §12.4 / Q4: density from shared tokens; tones use semantic status colours only. */
.nx-status {
  display: inline-flex;
  align-items: center;
  box-sizing: border-box;
  height: var(--nx-status-tag-height);
  padding: 0 var(--nx-status-tag-padding-x);
  border-radius: var(--nx-radius-pill);
  font-size: var(--nx-status-tag-font-size);
  font-weight: var(--nx-status-tag-font-weight);
  line-height: var(--nx-status-tag-line-height);
  white-space: nowrap;
}
.nx-status[data-tone="success"] { background: var(--nx-success-light); color: var(--nx-success); }
.nx-status[data-tone="warning"] { background: var(--nx-warning-light); color: var(--nx-warning); }
.nx-status[data-tone="danger"] { background: var(--nx-danger-light); color: var(--nx-danger); }
.nx-status[data-tone="info"] { background: var(--nx-info-light); color: var(--nx-info); }
.nx-status[data-tone="neutral"] { background: var(--nx-divider); color: var(--nx-text-regular); }
</style>
