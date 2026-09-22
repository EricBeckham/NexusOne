/**
 * APP WebView native-bridge contract stub.
 *
 * Same F014 HostBridgePort envelope (login / route / file / close).
 * Models iOS WKScriptMessageHandler and Android JavascriptInterface
 * handler names. This is not a store-ready IPA/APK, not an Xcode /
 * Gradle project, and not WeChat / DingTalk production.
 *
 * Distinct from:
 *   #114 /m/app-webview-harness  — web NexusAppWebView injection
 *   #117 /m/mp-webview-harness   — simulated 小程序 web-view parent
 *   #132 /m/wx-dt-harness        — WeChat/DingTalk production-host stub
 */
import {
  HOST_CHANNELS,
  HOST_SOURCE,
  NATIVE_ANDROID_OBJECT,
  NATIVE_BRIDGE_HOST,
  NATIVE_BRIDGE_QUERY,
  NATIVE_IOS_HANDLER,
  OTHER_HOSTS_OPEN,
  allowedHostOrigin,
  applyWebFallback,
  decodeHostMessage,
  encodeHostMessage,
  hostCapabilities,
  installHostBridge,
  isAllowedOrigin,
  isSafeHostPath,
  postHost,
  postToFrame,
} from "./hostBridge.js";

export {
  NATIVE_ANDROID_OBJECT,
  NATIVE_BRIDGE_HOST,
  NATIVE_BRIDGE_QUERY,
  NATIVE_IOS_HANDLER,
};

export const NATIVE_BRIDGE_PLATFORMS = Object.freeze(["ios", "android"]);

export const NATIVE_BRIDGE_WALK = Object.freeze([
  Object.freeze(["login", Object.freeze({ action: "request", returnTo: "/m/host" })]),
  Object.freeze(["route", Object.freeze({ action: "navigate", path: "/m/p3/files?tab=recent" })]),
  Object.freeze(["file", Object.freeze({ action: "open", fileId: "demo-file-id" })]),
  Object.freeze(["close", Object.freeze({ action: "close" })]),
]);

export const NOT_CLAIMED_VS_SIMULATIONS = Object.freeze({
  "#114": "web NexusAppWebView harness — not a native package",
  "#117": "simulated 小程序 web-view parent — not production",
  "#132": "WeChat/DingTalk production-host stub — not production accept",
});

export function childSrcForNativeBridge() {
  return `/m/host?embedded=true&host=${NATIVE_BRIDGE_QUERY}`;
}

export function allowedNativeBridgeOrigins(win) {
  const origin = allowedHostOrigin(win);
  return origin ? Object.freeze([origin]) : Object.freeze([]);
}

export function isAllowedNativeBridgeOrigin(origin, win) {
  return isAllowedOrigin(origin, win);
}

export function isAllowedNativeBridgeChannel(channel) {
  return HOST_CHANNELS.includes(channel);
}

export function isAllowedNativeBridgeSrc(src, win) {
  if (!src || typeof src !== "string") {
    return false;
  }
  const origin = allowedHostOrigin(win);
  let url;
  try {
    url = new URL(src, origin || "http://127.0.0.1");
  } catch {
    return false;
  }
  if (origin && url.origin !== origin) {
    return false;
  }
  if (!url.pathname.startsWith("/m/")) {
    return false;
  }
  const path = `${url.pathname}${url.search}`;
  return isSafeHostPath(path);
}

export function decodeNativeBridgeMessage(event, win) {
  if (!event || !isAllowedNativeBridgeOrigin(event.origin, win)) {
    return { ok: false, reason: "origin_rejected" };
  }
  return decodeHostMessage(event, win);
}

export function attachNativeBridge(win, platform = "ios", onPost) {
  if (!win) {
    return null;
  }
  const inbox = [];
  const kind = platform === "android" ? "android-jsinterface-stub" : "ios-wk-stub";
  if (platform === "android") {
    win[NATIVE_ANDROID_OBJECT] = {
      kind,
      storeReady: false,
      nativeBinary: false,
      storePackage: false,
      postMessage(data) {
        const parsed = typeof data === "string"
          ? JSON.parse(data)
          : data;
        inbox.push({ platform: "android", data: parsed, raw: data });
        if (typeof onPost === "function") {
          onPost(parsed, "android");
        }
      },
    };
  } else {
    win.webkit = win.webkit || {};
    win.webkit.messageHandlers = win.webkit.messageHandlers || {};
    win.webkit.messageHandlers[NATIVE_IOS_HANDLER] = {
      kind,
      storeReady: false,
      nativeBinary: false,
      storePackage: false,
      postMessage(data) {
        inbox.push({ platform: "ios", data });
        if (typeof onPost === "function") {
          onPost(data, "ios");
        }
      },
    };
  }
  return {
    kind,
    platform: platform === "android" ? "android" : "ios",
    inbox,
    storeReady: false,
    nativeBinary: false,
    storePackage: false,
  };
}

export function walkNativeBridgeOutbound(win, channels = NATIVE_BRIDGE_WALK) {
  return channels.map(([channel, payload]) => postHost(channel, payload, win));
}

export function walkNativeBridgeInbound(h5Win, hostWin, channels = NATIVE_BRIDGE_WALK) {
  return channels.map(([channel, payload]) => postToFrame(h5Win, channel, payload, hostWin));
}

export function applyNativeBridgeInbound(win, channels = NATIVE_BRIDGE_WALK) {
  const seen = [];
  const uninstall = installHostBridge({
    navigate(path) {
      seen.push(`nav:${path}`);
      return { ok: true, path };
    },
    close() {
      seen.push("close");
      return true;
    },
    onHostMessage(channel, payload) {
      seen.push(`${channel}:${payload.action || payload.path || payload.fileId || ""}`);
      return applyWebFallback(channel, payload, this);
    },
  }, win);
  for (const [channel, payload] of channels) {
    const encoded = encodeHostMessage({ source: HOST_SOURCE, channel, payload });
    if (!encoded.ok) {
      seen.push(`reject:${encoded.reason}`);
      continue;
    }
    win.__dispatch({
      origin: win.location.origin,
      data: encoded.envelope,
    });
  }
  return { seen, uninstall, caps: hostCapabilities(win) };
}

export function describeNativeBridgeContract(win, platform = "ios") {
  const kind = platform === "android" ? "android-jsinterface-stub" : "ios-wk-stub";
  return {
    host: NATIVE_BRIDGE_HOST,
    stub: true,
    storeReady: false,
    storePackage: false,
    nativeBinary: false,
    productionAccepted: false,
    iosProject: false,
    androidProject: false,
    wkWebViewWired: false,
    androidWebViewWired: false,
    transport: "native-bridge-stub",
    envelope: "F014 HostBridgePort (unchanged)",
    channels: [...HOST_CHANNELS],
    originAllowlist: [...allowedNativeBridgeOrigins(win)],
    platform: platform === "android" ? "android" : "ios",
    platforms: Object.freeze({
      ios: Object.freeze({
        handler: `webkit.messageHandlers.${NATIVE_IOS_HANDLER}`,
        kind: "ios-wk-stub",
        storeReady: false,
        wkWebView: false,
      }),
      android: Object.freeze({
        handler: NATIVE_ANDROID_OBJECT,
        kind: "android-jsinterface-stub",
        storeReady: false,
        androidWebView: false,
      }),
    }),
    handlerKind: kind,
    notClaimed: { ...NOT_CLAIMED_VS_SIMULATIONS },
    leftover: [...OTHER_HOSTS_OPEN],
  };
}

export function postToNativeBridgeChild(frameWindow, channel, payload, win) {
  if (!isAllowedNativeBridgeChannel(channel)) {
    return { ok: false, reason: "channel_not_allowed" };
  }
  return postToFrame(frameWindow, channel, payload, win);
}
