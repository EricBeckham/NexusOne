<template>
  <aside
    class="nx-property-panel"
    :class="{ 'nx-property-panel--compact': compact }"
    :aria-label="ariaLabel"
    data-testid="nx-property-panel"
  >
    <header v-if="title || $slots.header" class="nx-property-panel__header">
      <slot name="header">
        <h2 class="nx-property-panel__title">{{ title }}</h2>
      </slot>
    </header>

    <div class="nx-tabs" role="tablist" :aria-label="tablistLabel">
      <button
        v-for="item in tabs"
        :key="item.id"
        type="button"
        role="tab"
        :id="tabId(item.id)"
        :aria-selected="activeId === item.id"
        :aria-controls="panelId(item.id)"
        :tabindex="activeId === item.id ? 0 : -1"
        :class="{ active: activeId === item.id }"
        :data-testid="`nx-property-tab-${item.id}`"
        @click="select(item.id)"
        @keydown="onTabKeydown($event, item.id)"
      >
        {{ item.label }}
      </button>
    </div>

    <template v-for="item in tabs" :key="`panel-${item.id}`">
      <div
        v-if="activeId === item.id"
        :id="panelId(item.id)"
        class="nx-property-panel__body"
        role="tabpanel"
        :aria-labelledby="tabId(item.id)"
        :data-testid="`nx-property-panel-${item.id}`"
      >
        <slot :name="item.id" :tab="item" :active="true">
          <p class="nx-property-panel__placeholder nx-muted">暂无内容</p>
        </slot>
      </div>
    </template>
  </aside>
</template>

<script setup>
import { computed, useId } from "vue";

const props = defineProps({
  /** @type {{ id: string, label: string }[]} */
  tabs: { type: Array, required: true },
  modelValue: { type: String, default: "" },
  title: { type: String, default: "" },
  ariaLabel: { type: String, default: "属性面板" },
  tablistLabel: { type: String, default: "属性分区" },
  compact: { type: Boolean, default: false },
});

const emit = defineEmits(["update:modelValue"]);

const uid = useId();

const activeId = computed(() => {
  const ids = props.tabs.map((t) => t?.id).filter(Boolean);
  if (ids.includes(props.modelValue)) return props.modelValue;
  return ids[0] || "";
});

function select(id) {
  if (id && id !== props.modelValue) emit("update:modelValue", id);
}

function tabId(id) {
  return `${uid}-tab-${id}`;
}

function panelId(id) {
  return `${uid}-panel-${id}`;
}

function onTabKeydown(event, id) {
  const ids = props.tabs.map((t) => t?.id).filter(Boolean);
  const index = ids.indexOf(id);
  if (index < 0) return;
  let next = -1;
  if (event.key === "ArrowRight" || event.key === "ArrowDown") next = (index + 1) % ids.length;
  else if (event.key === "ArrowLeft" || event.key === "ArrowUp") next = (index - 1 + ids.length) % ids.length;
  else if (event.key === "Home") next = 0;
  else if (event.key === "End") next = ids.length - 1;
  if (next < 0) return;
  event.preventDefault();
  select(ids[next]);
  const el = event.currentTarget?.parentElement?.querySelector(`[data-testid="nx-property-tab-${ids[next]}"]`);
  el?.focus?.();
}
</script>

<style scoped>
.nx-property-panel {
  min-width: 0;
  padding: var(--nx-space-1) var(--nx-space-4) var(--nx-space-4);
  background: var(--nx-surface);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius);
  box-shadow: var(--nx-shadow-card);
}
.nx-property-panel--compact {
  padding: var(--nx-space-1) var(--nx-space-3) var(--nx-space-3);
}
.nx-property-panel__header {
  margin: var(--nx-space-3) 0 var(--nx-space-2);
}
.nx-property-panel__title {
  margin: 0;
  color: var(--nx-text-primary);
  font-size: var(--nx-font-size-md);
  font-weight: 650;
}
.nx-property-panel__body {
  min-width: 0;
}
.nx-property-panel__placeholder {
  margin: var(--nx-space-3) 0 0;
  font-size: var(--nx-font-size-sm);
}
.nx-property-panel :deep(.nx-tabs) {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--nx-surface);
  padding-top: var(--nx-space-3);
}
</style>