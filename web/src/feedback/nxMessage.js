/**
 * §21 / S2: shared toast API. Short copy only — do not replace field errors or dump stacks.
 * Host: mount `<NxToastHost />` once (App.vue). Call sites migrate in later S cards.
 */
import { reactive } from "vue";

const MAX_MESSAGE = 120;
const DEFAULT_MS = 4000;

const state = reactive({
  items: [],
});

let seq = 0;

function clip(message) {
  const text = String(message ?? "").trim() || "操作完成";
  return text.length > MAX_MESSAGE ? `${text.slice(0, MAX_MESSAGE - 1)}…` : text;
}

function push({ tone, message, traceId = "", durationMs = DEFAULT_MS }) {
  const id = ++seq;
  const item = {
    id,
    tone,
    message: clip(message),
    traceId: traceId ? String(traceId) : "",
  };
  state.items.push(item);
  if (durationMs > 0) {
    globalThis.setTimeout(() => dismiss(id), durationMs);
  }
  return id;
}

export function dismiss(id) {
  const index = state.items.findIndex((item) => item.id === id);
  if (index >= 0) state.items.splice(index, 1);
}

export function clearToasts() {
  state.items.splice(0, state.items.length);
}

/** Reactive list for NxToastHost. */
export function useNxToastState() {
  return state;
}

export const nxMessage = {
  success(message, options = {}) {
    return push({ tone: "success", message, ...options });
  },
  error(message, options = {}) {
    return push({ tone: "error", message, ...options });
  },
  info(message, options = {}) {
    return push({ tone: "info", message, ...options });
  },
  dismiss,
  clear: clearToasts,
};
