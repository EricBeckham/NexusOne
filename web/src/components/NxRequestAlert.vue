<template>
  <section class="request-alert" role="alert" data-testid="nx-request-alert">
    <div class="body">
      <strong>{{ title }}</strong>
      <p data-testid="nx-request-alert-hint">{{ error.hint }}</p>
      <p v-if="error.serverMessage" class="server" data-testid="nx-request-alert-server">
        服务端信息：{{ error.serverMessage }}<template v-if="error.code">（{{ error.code }}）</template>
      </p>
      <p v-else-if="error.code" class="server">错误码 {{ error.code }}</p>
      <code v-if="error.traceId" data-testid="nx-request-alert-trace">追踪号 {{ error.traceId }}</code>
    </div>
    <div class="actions">
      <slot name="actions" />
      <button v-if="retryLabel && error.retryable" class="primary" type="button" data-testid="nx-request-alert-retry" :disabled="busy" @click="$emit('retry')">
        {{ busy ? busyLabel : retryLabel }}
      </button>
      <button v-if="dismissible" class="ghost" type="button" data-testid="nx-request-alert-dismiss" @click="$emit('dismiss')">关闭</button>
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  error: { type: Object, required: true },
  retryLabel: { type: String, default: "重试" },
  busyLabel: { type: String, default: "处理中…" },
  busy: { type: Boolean, default: false },
  dismissible: { type: Boolean, default: true },
});
defineEmits(["retry", "dismiss"]);
</script>

<style scoped>
.request-alert {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--nx-space-3);
  padding: var(--nx-space-3) var(--nx-space-4);
  border: 1px solid color-mix(in srgb, var(--nx-danger) 30%, white);
  border-left: 4px solid var(--nx-danger);
  border-radius: var(--nx-radius-sm);
  background: var(--nx-danger-light);
}
.body { display: grid; gap: 4px; min-width: 0; }
.body strong { color: var(--nx-danger); }
.body p { margin: 0; color: var(--nx-text-regular); font-size: var(--nx-font-size-sm); }
.body .server { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); overflow-wrap: anywhere; }
.body code { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.actions { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); }
.actions .primary, .actions .ghost { min-height: 36px; }
</style>
