<template>
  <div class="nx-toast-host" aria-live="polite" aria-relevant="additions" data-testid="nx-toast-host">
    <div
      v-for="item in state.items"
      :key="item.id"
      class="nx-toast"
      :data-tone="item.tone"
      :data-testid="`nx-toast-${item.tone}`"
      role="status"
    >
      <div class="nx-toast__body">
        <p class="nx-toast__message">{{ item.message }}</p>
        <button
          v-if="item.traceId"
          class="nx-toast__trace"
          type="button"
          data-testid="nx-toast-trace"
          :title="item.traceId"
          @click="copyTrace(item.traceId)"
        >追踪号 {{ item.traceId }}</button>
      </div>
      <button class="nx-toast__close" type="button" aria-label="关闭" @click="dismiss(item.id)">×</button>
    </div>
  </div>
</template>

<script setup>
import { dismiss, useNxToastState } from "../feedback/nxMessage";

const state = useNxToastState();

async function copyTrace(traceId) {
  try {
    await globalThis.navigator?.clipboard?.writeText(traceId);
  } catch {
    /* clipboard may be unavailable in tests / insecure contexts */
  }
}
</script>

<style scoped>
.nx-toast-host {
  position: fixed;
  z-index: 80;
  right: var(--nx-space-4);
  bottom: var(--nx-space-4);
  display: grid;
  gap: var(--nx-space-2);
  width: min(360px, calc(100vw - var(--nx-space-8)));
  pointer-events: none;
}
.nx-toast {
  pointer-events: auto;
  display: flex;
  align-items: flex-start;
  gap: var(--nx-space-2);
  padding: var(--nx-space-3) var(--nx-space-3);
  border-radius: var(--nx-radius-sm);
  border: 1px solid var(--nx-border);
  background: var(--nx-surface);
  box-shadow: var(--nx-shadow-popover);
  color: var(--nx-text-primary);
}
.nx-toast[data-tone="success"] {
  border-color: color-mix(in srgb, var(--nx-success) 35%, white);
  background: var(--nx-success-light);
}
.nx-toast[data-tone="error"] {
  border-color: color-mix(in srgb, var(--nx-danger) 35%, white);
  background: var(--nx-danger-light);
}
.nx-toast[data-tone="info"] {
  border-color: color-mix(in srgb, var(--nx-info) 35%, white);
  background: var(--nx-info-light);
}
.nx-toast__body { min-width: 0; flex: 1; display: grid; gap: var(--nx-space-1); }
.nx-toast__message {
  margin: 0;
  font-size: var(--nx-font-size-sm);
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.nx-toast__trace {
  justify-self: start;
  padding: 0;
  border: 0;
  background: none;
  color: var(--nx-text-secondary);
  font: inherit;
  font-size: var(--nx-font-size-xs);
  text-decoration: underline;
  cursor: pointer;
}
.nx-toast__close {
  flex: none;
  width: var(--nx-control-height-sm);
  height: var(--nx-control-height-sm);
  border: 0;
  border-radius: var(--nx-radius-sm);
  background: transparent;
  color: var(--nx-text-secondary);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}
</style>
