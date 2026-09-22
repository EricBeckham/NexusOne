/**
 * Web-only APP WebView host simulation for the G15 leftover walk.
 *
 * Reuses F014 HostBridgePort. Does not claim an iOS/Android binary,
 * WKWebView, Android WebView, or 小程序 web-view.
 */
import {
  APP_WEBVIEW_OBJECT,
  H5_SOURCE,
  HOST_CHANNELS,
  HOST_SOURCE,
  applyWebFallback,
  decodeHostMessage,
  encodeHostMessage,
  hostCapabilities,
  installHostBridge,
  postHost,
  postToFrame,
} from "./hostBridge.js";

export const APP_WEBVIEW_WALK = Object.freeze([
  Object.freeze(["login", Object.freeze({ action: "request", returnTo: "/m/host" })]),
  Object.freeze(["route", Object.freeze({ action: "navigate", path: "/m/p3/files?tab=recent" })]),
  Object.freeze(["file", Object.freeze({ action: "open", fileId: "demo-file-id" })]),
  Object.freeze(["close", Object.freeze({ action: "close" })]),
]);

export function sameEnvelopeContract(left, right) {
  if (!left || !right) {
    return false;
  }
  return left.source === right.source
    && left.channel === right.channel
    && JSON.stringify(left.payload) === JSON.stringify(right.payload)
    && typeof left.requestId === "string"
    && typeof right.requestId === "string";
}

export function attachAppWebViewBridge(win, onPost) {
  if (!win) {
    return null;
  }
  const inbox = [];
  const bridge = {
    kind: "app-webview-sim",
    nativeBinary: false,
    postMessage(data, targetOrigin) {
      inbox.push({ data, targetOrigin });
      if (typeof onPost === "function") {
        onPost(data, targetOrigin);
      }
    },
  };
  win[APP_WEBVIEW_OBJECT] = bridge;
  bridge.inbox = inbox;
  return bridge;
}

export function walkAppWebViewOutbound(win, channels = APP_WEBVIEW_WALK) {
  const posted = [];
  for (const [channel, payload] of channels) {
    posted.push(postHost(channel, payload, win));
  }
  return posted;
}

export function walkAppWebViewInbound(h5Win, hostWin, channels = APP_WEBVIEW_WALK) {
  const delivered = [];
  for (const [channel, payload] of channels) {
    delivered.push(postToFrame(h5Win, channel, payload, hostWin));
  }
  return delivered;
}

export function applyAppWebViewInbound(win, channels = APP_WEBVIEW_WALK) {
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

export function proveSameEnvelope(channel, payload, appWin, iframeWin) {
  const encoded = encodeHostMessage({ source: H5_SOURCE, channel, payload });
  const decoded = decodeHostMessage({
    origin: appWin.location.origin,
    data: encoded.envelope,
  }, appWin);
  const appPosted = postHost(channel, payload, appWin);
  const iframePosted = postHost(channel, payload, iframeWin);
  return {
    encoded,
    decoded,
    appPosted,
    iframePosted,
    same: encoded.ok
      && decoded.ok
      && appPosted.ok
      && iframePosted.ok
      && sameEnvelopeContract(encoded.envelope, decoded.envelope)
      && sameEnvelopeContract(appPosted.envelope, iframePosted.envelope)
      && HOST_CHANNELS.includes(channel),
  };
}
