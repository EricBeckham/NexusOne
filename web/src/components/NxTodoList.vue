<template>
  <section class="todo-list" :aria-label="ariaLabel" data-testid="nx-todo-list">
    <slot v-if="!items.length" name="empty">
      <NxEmpty title="暂无待办" description="当前没有需要处理的事项。" compact />
    </slot>
    <ul v-else>
      <li v-for="(item, index) in items" :key="item.id || index" class="todo-row">
        <slot name="item" :item="item" :index="index">
          <span class="todo-marker" :data-done="item.done ? 'true' : 'false'" aria-hidden="true" />
          <div class="todo-content">
            <strong>{{ item.title || "未命名事项" }}</strong>
            <p v-if="item.description">{{ item.description }}</p>
            <small v-if="item.dueAt">{{ dueLabel }}：{{ item.dueAt }}</small>
          </div>
          <slot name="actions" :item="item" :index="index" />
        </slot>
      </li>
    </ul>
  </section>
</template>

<script setup>
import NxEmpty from "./NxEmpty.vue";

defineProps({
  items: { type: Array, default: () => [] },
  ariaLabel: { type: String, default: "待办事项" },
  dueLabel: { type: String, default: "截止" },
});
</script>

<style scoped>
ul { list-style: none; margin: 0; padding: 0; }
.todo-row { display: flex; align-items: flex-start; gap: var(--nx-space-3); min-width: 0; padding: var(--nx-space-3) 0; border-bottom: 1px solid var(--nx-divider); }
.todo-row:last-child { border-bottom: 0; }
.todo-marker { width: 16px; height: 16px; flex: none; margin-top: 2px; border: 1px solid var(--nx-border-strong); border-radius: 4px; background: var(--nx-surface); }
.todo-marker[data-done="true"] { border-color: var(--nx-success); background: var(--nx-success-light); }
.todo-content { flex: 1; min-width: 0; }
.todo-content strong { font-weight: 600; }
.todo-content p { margin: var(--nx-space-1) 0; color: var(--nx-text-regular); }
.todo-content small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
</style>
