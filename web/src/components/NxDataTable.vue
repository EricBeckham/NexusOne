<template>
  <div
    class="nx-data-table"
    :class="{ 'nx-data-table--card': card }"
    :data-testid="testid"
    :data-density="density"
  >
    <!-- §12 NxDataTable: density tokens + S feedback shell (error / skeleton / empty). No fake rows. -->
    <NxErrorState
      v-if="hasError"
      :title="errorTitle"
      :description="errorDescription"
      :trace-id="traceId"
      :retry-label="retryLabel"
      data-testid="nx-data-table-error"
      @retry="$emit('retry')"
    >
      <template v-if="$slots['error-action']" #action>
        <slot name="error-action" />
      </template>
    </NxErrorState>

    <div
      v-else-if="loading"
      class="nx-data-table__loading"
      role="status"
      aria-busy="true"
      data-testid="nx-data-table-loading"
    >
      <NxSkeleton
        v-for="n in skeletonCount"
        :key="n"
        variant="list-row"
        :label="loadingLabel"
        :compact="density === 'compact'"
      />
    </div>

    <NxEmpty
      v-else-if="isEmpty"
      :title="emptyTitle"
      :description="emptyDescription"
      :compact="emptyCompact"
      data-testid="nx-data-table-empty"
    >
      <template v-if="hasEmptyNext" #next>
        <slot name="empty-next">
          <slot name="empty-action" />
        </slot>
      </template>
    </NxEmpty>

    <div v-else class="nx-data-table__body" data-testid="nx-data-table-body">
      <div class="nx-data-table__scroll">
        <table
          class="nx-table"
          :class="tableClass"
          :aria-label="ariaLabel"
          data-testid="nx-data-table"
        >
          <thead v-if="$slots.header">
            <slot name="header" />
          </thead>
          <tbody>
            <slot />
          </tbody>
        </table>
      </div>
      <div v-if="$slots.footer" class="nx-data-table__footer" data-testid="nx-data-table-footer">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, useSlots } from "vue";
import NxEmpty from "./NxEmpty.vue";
import NxErrorState from "./NxErrorState.vue";
import NxSkeleton from "./NxSkeleton.vue";

/**
 * Spec §12 NxDataTable — thin shared list table.
 * Owns loading / empty / error (S shell) and §12.1 density classes on .nx-table.
 * Column markup stays in the page via #header + default body slots (no fake data).
 */
const props = defineProps({
  loading: { type: Boolean, default: false },
  /** Error copy: string shown as description (title stays default) or object { title, description }. */
  error: { type: [String, Object], default: "" },
  traceId: { type: String, default: "" },
  retryLabel: { type: String, default: "重试" },
  /** When true (and not loading/error), show NxEmpty instead of the table. */
  empty: { type: Boolean, default: false },
  emptyTitle: { type: String, default: "暂无数据" },
  emptyDescription: { type: String, default: "" },
  emptyCompact: { type: Boolean, default: false },
  /** default (48) | compact (40) | large (56) — §12.1 / Q1 tokens. */
  density: {
    type: String,
    default: "default",
    validator: (value) => ["default", "compact", "large"].includes(value),
  },
  skeletonCount: { type: Number, default: 3 },
  loadingLabel: { type: String, default: "加载中" },
  ariaLabel: { type: String, default: "数据列表" },
  testid: { type: String, default: "nx-data-table-root" },
  /** Optional card chrome around the table body. */
  card: { type: Boolean, default: false },
});

defineEmits(["retry"]);

const slots = useSlots();

const hasError = computed(() => {
  if (props.error == null || props.error === false) return false;
  if (typeof props.error === "string") return props.error.trim().length > 0;
  if (typeof props.error === "object") {
    return Boolean(props.error.title || props.error.description);
  }
  return false;
});

const errorTitle = computed(() => {
  if (typeof props.error === "object" && props.error?.title) return props.error.title;
  return "加载失败";
});

const errorDescription = computed(() => {
  if (typeof props.error === "string") return props.error;
  if (typeof props.error === "object") return props.error.description || "";
  return "";
});

const isEmpty = computed(() => props.empty);

const hasEmptyNext = computed(() => Boolean(slots["empty-next"] || slots["empty-action"]));

const tableClass = computed(() => ({
  "nx-table--compact": props.density === "compact",
  "nx-table--large": props.density === "large",
}));
</script>

<style scoped>
.nx-data-table { min-width: 0; }
.nx-data-table__loading {
  display: grid;
  gap: var(--nx-space-2);
}
.nx-data-table__scroll { overflow-x: auto; }
.nx-data-table__footer { margin-top: var(--nx-space-3); }
.nx-data-table--card .nx-data-table__scroll {
  background: var(--nx-surface);
  border-radius: var(--nx-radius);
}
</style>
