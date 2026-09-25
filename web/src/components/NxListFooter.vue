<template>
  <nav class="nx-list-footer" role="navigation" :aria-label="ariaLabel" data-testid="nx-list-footer">
    <p class="summary" data-testid="nx-list-footer-summary">
      <slot name="summary">
        <template v-if="total != null">共 {{ total }} 条</template>
        <template v-if="page != null && pageCount != null">
          <span v-if="total != null"> · </span>第 {{ page }} / {{ pageCount }} 页
        </template>
      </slot>
    </p>
    <div class="actions" data-testid="nx-list-footer-actions">
      <button
        class="ghost"
        type="button"
        data-testid="nx-list-footer-prev"
        :disabled="prevDisabled"
        @click="$emit('prev')"
      >上一页</button>
      <button
        class="ghost"
        type="button"
        data-testid="nx-list-footer-next"
        :disabled="nextDisabled"
        @click="$emit('next')"
      >下一页</button>
      <slot />
    </div>
  </nav>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  page: { type: Number, default: null },
  pageCount: { type: Number, default: null },
  total: { type: Number, default: null },
  loading: { type: Boolean, default: false },
  ariaLabel: { type: String, default: "列表分页" },
});

defineEmits(["prev", "next"]);

const prevDisabled = computed(() => props.loading || props.page == null || props.page <= 1);
const nextDisabled = computed(() =>
  props.loading || props.page == null || props.pageCount == null || props.page >= props.pageCount);
</script>

<style scoped>
.nx-list-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--nx-space-3);
  flex-wrap: wrap;
  margin-top: var(--nx-space-3);
  padding-top: var(--nx-space-3);
  border-top: 1px solid var(--nx-divider);
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-sm);
}
.summary { margin: 0; }
.actions {
  display: flex;
  align-items: center;
  gap: var(--nx-space-2);
  margin-left: auto;
}
.actions :deep(button:disabled) {
  opacity: 0.55;
  cursor: not-allowed;
}
@media (max-width: 560px) {
  .nx-list-footer { align-items: flex-start; flex-direction: column; }
  .actions { margin-left: 0; }
}
</style>
