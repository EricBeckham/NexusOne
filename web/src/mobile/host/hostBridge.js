/**
 * F014 HostBridgePort — 需求规格 P3-M01-G01-F014 / G15 H5 §12.
 *
 * Only login / route / file / close. Transport is window.postMessage to the
 * same origin. Business pages must not branch on WeChat / DingTalk
 * (H5 规范 §80.2). Payloads never carry tokens or tenant_id
 * (技术框架 §10.2).
 *
 * Mobile-browser acceptance: standalone uses the web fallback; the
 * same-origin iframe at /m/host-harness is the postMessage parent.
 * APP WebView / 小程序 web-view are not accepted in this slice.
 */
import { DEFAULT_MOBILE_HOME, isSafeMobileReturn, safeMobileReturn } from "../auth/safeReturn.js";

export const HOST_CHANNELS = Object.freeze(["login", "route", "file", "close"]);
export const H5_SOURCE = "nexus-h5";
export const HOST_SOURCE = "nexus-host";
export const HOST_ACTIONS = Object.freeze({
  login: Object.freeze(["ready", "request", "done", "session_expired"]),
  route: Object.freeze(["navigate", "changed"]),
  file: Object.freeze(["open", "upload", "download"]),
  close: Object.freeze(["close"]),
});

const SECRET_KEY = /^(access_token|id_token|refresh_token|token|authorization|password|secret|tenant_id|x-tenant-id)$/i;
const SECRET_VALUE = /(access_token|id_token|refresh_token|Bearer\s+)/i;

export const hostBridgeLog = {
  lastIn: null,
  lastOut: null,
  events: [],
};

export function resetHostBridgeLog() {
  hostBridgeLog.lastIn = null;
  hostBridgeLog.lastOut = null;
  hostBridgeLog.events = [];
}

function defaultWindow() {
  return typeof window !== "undefined" ? window : undefined;
}

function newRequestId() {
  return `h5-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function remember(dir, envelope) {
  const entry = {
    dir,
    channel: envelope.channel,
    payload: envelope.payload,
    requestId: envelope.requestId,
    at: Date.now(),
  };
  if (dir === "in") {
    hostBridgeLog.lastIn = entry;
  } else {
    hostBridgeLog.lastOut = entry;
  }
  hostBridgeLog.events = [entry, ...hostBridgeLog.events].slice(0, 16);
}

export function allowedHostOrigin(win = defaultWindow()) {
  return String(win?.location?.origin || "");
}

export function isAllowedOrigin(origin, win = defaultWindow()) {
  const allowed = allowedHostOrigin(win);
  return Boolean(origin) && Boolean(allowed) && origin === allowed;
}

export function isEmbedded(win = defaultWindow()) {
  if (!win) {
    return false;
  }
  try {
    const params = new URLSearchParams(win.location?.search || "");
    if (params.get("embedded") === "true") {
      return true;
    }
  } catch {
    // ignore malformed search
  }
  return Boolean(win.parent && win.parent !== win);
}

export function hostCapabilities(win = defaultWindow()) {
  const embedded = isEmbedded(win);
  return {
    host: embedded ? "embedded" : "mobile-browser",
    supported: [...HOST_CHANNELS],
    unsupported: [],
    accepted: true,
    transport: "postMessage",
    origin: allowedHostOrigin(win),
    fallback: embedded ? null : "web",
    otherHostsOpen: ["APP WebView", "小程序 web-view"],
  };
}

export function isSafeHostPath(path) {
  if (!isSafeMobileReturn(path)) {
    return false;
  }
  const clean = String(path).trim().split("?")[0];
  if (clean === "/m/host-harness" || clean.startsWith("/m/host-harness/")) {
    return false;
  }
  return true;
}

export function safeHostPath(path, fallback = DEFAULT_MOBILE_HOME) {
  return isSafeHostPath(path) ? String(path).trim() : fallback;
}

export function sanitizeHostPayload(payload) {
  if (payload == null) {
    return { ok: true, payload: {} };
  }
  if (typeof payload !== "object" || Array.isArray(payload)) {
    return { ok: false, reason: "payload_not_object" };
  }
  const out = {};
  for (const [key, value] of Object.entries(payload)) {
    if (SECRET_KEY.test(key)) {
      return { ok: false, reason: "secret_key", key };
    }
    if (typeof value === "string" && SECRET_VALUE.test(value)) {
      return { ok: false, reason: "secret_value", key };
    }
    if (value && typeof value === "object") {
      return { ok: false, reason: "nested_payload" };
    }
    if (typeof value === "function") {
      return { ok: false, reason: "payload_function" };
    }
    out[key] = value;
  }
  return { ok: true, payload: out };
}

function validateChannelPayload(channel, payload) {
  const action = payload.action;
  if (action && !HOST_ACTIONS[channel].includes(action)) {
    return { ok: false, reason: "action_not_allowed" };
  }
  if (channel === "route" && payload.path != null && !isSafeHostPath(payload.path)) {
    return { ok: false, reason: "unsafe_path" };
  }
  if (channel === "file") {
    if (payload.content != null || payload.contentBase64 != null || payload.content_base64 != null || payload.url != null) {
      return { ok: false, reason: "file_content_forbidden" };
    }
  }
  if (channel === "login" && payload.returnTo != null && !isSafeHostPath(payload.returnTo)) {
    return { ok: false, reason: "unsafe_return" };
  }
  return { ok: true, payload };
}

export function encodeHostMessage({ source, channel, payload, requestId } = {}) {
  if (source !== H5_SOURCE && source !== HOST_SOURCE) {
    return { ok: false, reason: "source_not_allowed" };
  }
  if (!HOST_CHANNELS.includes(channel)) {
    return { ok: false, reason: "channel_not_allowed" };
  }
  const clean = sanitizeHostPayload(payload);
  if (!clean.ok) {
    return clean;
  }
  const checked = validateChannelPayload(channel, clean.payload);
  if (!checked.ok) {
    return checked;
  }
  return {
    ok: true,
    envelope: {
      source,
      channel,
      requestId: requestId || newRequestId(),
      payload: clean.payload,
    },
  };
}

export function decodeHostMessage(event, win = defaultWindow()) {
  if (!event || !isAllowedOrigin(event.origin, win)) {
    return { ok: false, reason: "origin_rejected" };
  }
  const data = event.data;
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    return { ok: false, reason: "not_envelope" };
  }
  return encodeHostMessage(data);
}

export function resolvePostTarget(win = defaultWindow()) {
  if (!win) {
    return null;
  }
  if (win.parent && win.parent !== win && typeof win.parent.postMessage === "function") {
    return win.parent;
  }
  return null;
}

export function postHost(channel, payload, win = defaultWindow()) {
  const encoded = encodeHostMessage({ source: H5_SOURCE, channel, payload });
  if (!encoded.ok) {
    return encoded;
  }
  const target = resolvePostTarget(win);
  if (!target) {
    return { ok: false, reason: "no_host", fallback: "web", envelope: encoded.envelope };
  }
  target.postMessage(encoded.envelope, allowedHostOrigin(win));
  remember("out", encoded.envelope);
  return { ok: true, envelope: encoded.envelope };
}

export function postToFrame(frameWindow, channel, payload, win = defaultWindow()) {
  const encoded = encodeHostMessage({ source: HOST_SOURCE, channel, payload });
  if (!encoded.ok) {
    return encoded;
  }
  if (!frameWindow || typeof frameWindow.postMessage !== "function") {
    return { ok: false, reason: "no_frame" };
  }
  frameWindow.postMessage(encoded.envelope, allowedHostOrigin(win));
  remember("out", encoded.envelope);
  return { ok: true, envelope: encoded.envelope };
}

export function applyWebFallback(channel, payload, port) {
  if (!HOST_CHANNELS.includes(channel)) {
    return { ok: false, reason: "channel_not_allowed" };
  }
  const body = payload || {};
  if (channel === "login") {
    if (body.action === "done") {
      return { ok: true, fallback: "web", action: "stay" };
    }
    const dest = body.returnTo && isSafeHostPath(body.returnTo)
      ? `/m/login?return=${encodeURIComponent(body.returnTo)}`
      : "/m/login";
    return port.navigate(dest);
  }
  if (channel === "route") {
    return port.navigate(safeHostPath(body.path));
  }
  if (channel === "file") {
    if (body.fileId && /^[A-Za-z0-9._-]+$/.test(String(body.fileId))) {
      return port.navigate(`/m/p3/files/${body.fileId}`);
    }
    return port.navigate("/m/p3/files");
  }
  if (windowHistoryClose(port)) {
    return { ok: true, fallback: "web", action: "close" };
  }
  return port.navigate(DEFAULT_MOBILE_HOME);
}

function windowHistoryClose(port) {
  if (typeof port.close === "function") {
    return port.close();
  }
  return false;
}

export function installHostBridge(port, win = defaultWindow()) {
  if (!win || typeof win.addEventListener !== "function") {
    return () => {};
  }
  function onMessage(event) {
    const decoded = decodeHostMessage(event, win);
    if (!decoded.ok) {
      return decoded;
    }
    if (decoded.envelope.source !== HOST_SOURCE && !port.acceptH5) {
      return { ok: false, reason: "not_from_host" };
    }
    remember("in", decoded.envelope);
    if (typeof port.onLog === "function") {
      port.onLog("in", decoded.envelope);
    }
    const { channel, payload } = decoded.envelope;
    if (typeof port.onHostMessage === "function") {
      return port.onHostMessage(channel, payload, decoded.envelope);
    }
    return applyWebFallback(channel, payload, port);
  }
  win.addEventListener("message", onMessage);
  if (isEmbedded(win)) {
    postHost("login", { action: "ready" }, win);
  }
  return function uninstall() {
    win.removeEventListener("message", onMessage);
  };
}
