<template>
  <section class="activity-timeline" :aria-label="ariaLabel" data-testid="nx-activity-timeline">
    <slot v-if="!items.length" name="empty">
      <NxEmpty title="暂无活动" description="有新的动态时会显示在这里。" compact />
    </slot>
    <ol v-else>
      <li v-for="(item, index) in items" :key="item.id || item.datetime || index" class="activity-item">
        <slot name="item" :item="item" :index="index">
          <span class="activity-marker" :data-tone="item.tone || 'neutral'" aria-hidden="true" />
          <div class="activity-content">
            <div class="activity-heading"><strong>{{ item.title || "未命名活动" }}</strong><time v-if="item.datetime" :datetime="item.datetime">{{ item.timeLabel || item.datetime }}</time><span v-else class="time-empty">时间暂无</span></div>
            <p v-if="item.description">{{ item.description }}</p>
            <small v-if="item.actor">{{ item.actor }}</small>
          </div>
        </slot>
      </li>
    </ol>
  </section>
</template>

<script setup>
import NxEmpty from "./NxEmpty.vue";

defineProps({
  items: { type: Array, default: () => [] },
  ariaLabel: { type: String, default: "最近活动" },
});
</script>

<style scoped>
ol { list-style: none; margin: 0; padding: 0; }
.activity-item { position: relative; display: flex; gap: var(--nx-space-3); min-width: 0; padding: 0 0 var(--nx-space-4); }
.activity-item:not(:last-child)::before { position: absolute; top: 18px; bottom: 0; left: 5px; width: 1px; background: var(--nx-divider); content: ""; }
.activity-marker { z-index: 1; width: 11px; height: 11px; flex: none; margin-top: 4px; border: 2px solid var(--nx-surface); border-radius: var(--nx-radius-pill); background: var(--nx-text-disabled); box-shadow: 0 0 0 1px var(--nx-border); }
.activity-marker[data-tone="success"] { background: var(--nx-success); }
.activity-marker[data-tone="warning"] { background: var(--nx-warning); }
.activity-marker[data-tone="danger"] { background: var(--nx-danger); }
.activity-marker[data-tone="info"] { background: var(--nx-info); }
.activity-content { flex: 1; min-width: 0; }
.activity-heading { display: flex; justify-content: space-between; align-items: baseline; gap: var(--nx-space-3); }
.activity-heading strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; font-weight: 600; }
time,.time-empty,.activity-content small { flex: none; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.activity-content p { margin: var(--nx-space-1) 0; color: var(--nx-text-regular); }
.activity-content small { display: block; }
@media(max-width:600px) { .activity-heading { align-items: flex-start; flex-direction: column; gap: var(--nx-space-1); } }
</style>
