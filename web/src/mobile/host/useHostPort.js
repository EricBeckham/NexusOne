import { onMounted, onUnmounted, reactive } from "vue";
import { useRouter } from "vue-router";
import { DEFAULT_MOBILE_HOME, isSafeMobileReturn } from "../auth/safeReturn.js";
import {
  applyWebFallback,
  hostBridgeLog,
  hostCapabilities,
  installHostBridge,
  postHost,
} from "./hostBridge.js";

export const hostUiLog = reactive({
  lastIn: null,
  lastOut: null,
  events: [],
});

function pullLog() {
  hostUiLog.lastIn = hostBridgeLog.lastIn;
  hostUiLog.lastOut = hostBridgeLog.lastOut;
  hostUiLog.events = hostBridgeLog.events;
}

let installCount = 0;
let uninstallBridge = null;

function createPort(router) {
  return {
    navigate(path) {
      const dest = isSafeMobileReturn(path) || String(path).startsWith("/m/login")
        ? path
        : DEFAULT_MOBILE_HOME;
      router.replace(dest);
      return { ok: true, fallback: "web", path: dest };
    },
    close() {
      if (typeof window !== "undefined" && window.history.length > 1) {
        router.back();
        return true;
      }
      router.replace(DEFAULT_MOBILE_HOME);
      return true;
    },
    onLog() {
      pullLog();
    },
    onHostMessage(channel, payload) {
      pullLog();
      return applyWebFallback(channel, payload, this);
    },
  };
}

export function useHostPort() {
  const router = useRouter();
  onMounted(() => {
    installCount += 1;
    if (installCount === 1) {
      uninstallBridge = installHostBridge(createPort(router));
    }
    pullLog();
  });
  onUnmounted(() => {
    installCount -= 1;
    if (installCount <= 0) {
      uninstallBridge?.();
      uninstallBridge = null;
      installCount = 0;
    }
  });
  return {
    postHost,
    caps: hostCapabilities(),
    log: hostUiLog,
    pullLog,
  };
}
