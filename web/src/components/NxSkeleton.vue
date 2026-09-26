<template>
  <div
    class="nx-skeleton"
    :class="[`nx-skeleton--${variant}`, { compact }]"
    role="status"
    aria-busy="true"
    aria-live="polite"
    data-testid="nx-skeleton"
    :data-variant="variant"
  >
    <!-- §22.1 / O6: page loading uses Skeleton, not a full-screen spinner. -->
    <span class="sr-only">{{ label }}</span>
    <template v-if="variant === 'list-row'">
      <div class="bone bone-avatar" />
      <div class="bone-stack">
        <div class="bone bone-line w-60" />
        <div class="bone bone-line w-40" />
      </div>
      <div class="bone bone-chip" />
    </template>
    <template v-else>
      <div class="bone bone-line w-30" />
      <div class="bone bone-input" />
      <div class="bone bone-line w-30" />
      <div class="bone bone-input" />
      <div class="bone bone-line w-30" />
      <div class="bone bone-area" />
    </template>
  </div>
</template>

<script setup>
defineProps({
  /** `list-row` | `form-block` */
  variant: { type: String, default: "list-row" },
  label: { type: String, default: "加载中" },
  compact: { type: Boolean, default: false },
});
</script>

<style scoped>
.nx-skeleton {
  display: grid;
  gap: var(--nx-space-3);
  padding: var(--nx-space-4);
  background: var(--nx-surface);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
}
.nx-skeleton--list-row {
  grid-template-columns: 40px minmax(0, 1fr) 72px;
  align-items: center;
  min-height: 48px;
  padding: var(--nx-space-3) var(--nx-space-4);
}
.nx-skeleton--form-block {
  max-width: 720px;
  gap: var(--nx-form-label-gap, var(--nx-space-2));
}
.nx-skeleton.compact { padding: var(--nx-space-2) var(--nx-space-3); }
.bone-stack { display: grid; gap: var(--nx-space-2); min-width: 0; }
.bone {
  border-radius: var(--nx-control-radius, 6px);
  background: linear-gradient(90deg, var(--nx-divider) 0%, var(--nx-border) 50%, var(--nx-divider) 100%);
  background-size: 200% 100%;
  animation: nx-skeleton-shimmer 1.2s ease-in-out infinite;
}
.bone-avatar { width: 40px; height: 40px; border-radius: var(--nx-radius-sm); }
.bone-line { height: 12px; }
.bone-chip { height: var(--nx-status-tag-height); border-radius: var(--nx-radius-pill); }
.bone-input { height: var(--nx-control-height, 36px); margin-bottom: var(--nx-form-field-gap, var(--nx-space-5)); }
.bone-area { height: 72px; margin-bottom: var(--nx-space-2); }
.w-30 { width: 30%; }
.w-40 { width: 40%; }
.w-60 { width: 60%; }
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
@keyframes nx-skeleton-shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
</style>
