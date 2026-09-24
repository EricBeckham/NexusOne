<template>
  <section class="health-list" :aria-label="ariaLabel" data-testid="nx-health-list">
    <slot v-if="!items.length" name="empty">
      <NxEmpty title="暂无健康状态" description="当前没有可展示的服务健康检查。" compact />
    </slot>
    <ul v-else>
      <li v-for="(item, index) in items" :key="item.id || item.code || index" class="health-row">
        <slot name="item" :item="item" :index="index">
          <span class="health-indicator" :data-tone="item.tone || 'neutral'" aria-hidden="true" />
          <span class="health-copy"><strong>{{ item.label || item.name || "未命名服务" }}</strong><small v-if="item.detail">{{ item.detail }}</small></span>
          <span class="health-meta"><NxStatusTag :status="item.status" :label="item.statusLabel" :tone="item.tone" /><small v-if="item.checkedAt">{{ item.checkedAt }}</small></span>
        </slot>
      </li>
    </ul>
  </section>
</template>

<script setup>
import NxEmpty from "./NxEmpty.vue";
import NxStatusTag from "./NxStatusTag.vue";

defineProps({
  items: { type: Array, default: () => [] },
  ariaLabel: { type: String, default: "服务健康状态" },
});
</script>

<style scoped>
ul { list-style: none; margin: 0; padding: 0; }
.health-row { display: flex; align-items: center; gap: var(--nx-space-3); min-width: 0; padding: var(--nx-space-3) 0; border-bottom: 1px solid var(--nx-divider); }
.health-row:last-child { border-bottom: 0; }
.health-indicator { width: 8px; height: 8px; flex: none; border-radius: var(--nx-radius-pill); background: var(--nx-text-disabled); }
.health-indicator[data-tone="success"] { background: var(--nx-success); }
.health-indicator[data-tone="warning"] { background: var(--nx-warning); }
.health-indicator[data-tone="danger"] { background: var(--nx-danger); }
.health-indicator[data-tone="info"] { background: var(--nx-info); }
.health-copy { display: flex; flex: 1; flex-direction: column; min-width: 0; gap: var(--nx-space-1); }
.health-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
.health-copy small,.health-meta small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.health-meta { display: flex; flex-direction: column; align-items: flex-end; gap: var(--nx-space-1); }
</style>
