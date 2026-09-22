#!/usr/bin/env node
/**
 * G15 F014: HostBridgePort protocol (no browser, no Playwright, no Docker).
 * Accepts the mobile-browser postMessage contract. Does not claim APP WebView
 * or WeChat/DingTalk production 小程序.
 */
import { createFakeWindow } from "../src/mobile/host/fakeWindow.js";
import {
  HOST_CHANNELS,
  H5_SOURCE,
  HOST_SOURCE,
  applyWebFallback,
  decodeHostMessage,
  encodeHostMessage,
  hostCapabilities,
  installHostBridge,
  postHost,
  postToFrame,
  resetHostBridgeLog,
} from "../src/mobile/host/hostBridge.js";

const failures = [];
function check(cond, message) {
  if (!cond) {
    failures.push(message);
  }
}

resetHostBridgeLog();

check(HOST_CHANNELS.join(",") === "login,route,file,close", "channels must be login/route/file/close");

const standalone = createFakeWindow({ embedded: false });
const standaloneCaps = hostCapabilities(standalone);
check(standaloneCaps.accepted === true, "F014 postMessage contract must be accepted");
check(standaloneCaps.host === "mobile-browser", "standalone host is mobile-browser");
check(standaloneCaps.fallback === "web", "standalone must web-fallback");
check(standaloneCaps.otherHostsOpen.join(",") === "APP WebView,WeChat/DingTalk 小程序 production", "other hosts stay open");

const noHost = postHost("close", { action: "close" }, standalone);
check(noHost.ok === false && noHost.reason === "no_host", "no parent => no_host");

const embedded = createFakeWindow({ embedded: true });
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
}, embedded);

check(embedded.__parentInbox[0]?.data?.source === H5_SOURCE, "handshake source nexus-h5");
check(embedded.__parentInbox[0]?.data?.channel === "login", "handshake channel login");
check(embedded.__parentInbox[0]?.data?.payload?.action === "ready", "handshake action ready");
check(embedded.__parentInbox[0]?.targetOrigin === embedded.location.origin, "handshake same-origin");

for (const [channel, payload] of [
  ["login", { action: "request", returnTo: "/m/host" }],
  ["route", { action: "navigate", path: "/m/p3/files?tab=recent" }],
  ["file", { action: "open", fileId: "demo-file-id" }],
  ["close", { action: "close" }],
]) {
  embedded.__dispatch({
    origin: embedded.location.origin,
    data: { source: HOST_SOURCE, channel, payload },
  });
}
check(seen.includes("login:request"), "inbound login applied");
check(seen.includes("route:navigate") || seen.includes("nav:/m/p3/files?tab=recent"), "inbound route applied");
check(seen.includes("file:open") || seen.includes("nav:/m/p3/files/demo-file-id"), "inbound file applied");
check(seen.includes("close"), "inbound close applied");

const posted = postHost("file", { action: "upload", fileId: "demo-file-id", fileName: "note.txt" }, embedded);
check(posted.ok === true, "embedded outbound file ok");
check(embedded.__parentInbox.at(-1)?.data?.payload?.fileId === "demo-file-id", "outbound fileId only");

const evilOrigin = decodeHostMessage({
  origin: "https://evil.example",
  data: { source: HOST_SOURCE, channel: "close", payload: { action: "close" } },
}, embedded);
check(evilOrigin.reason === "origin_rejected", "cross-origin rejected");

const token = encodeHostMessage({ source: H5_SOURCE, channel: "login", payload: { action: "done", access_token: "x" } });
check(token.reason === "secret_key", "token payload rejected");

const evalDenied = postHost("eval", {}, embedded);
check(evalDenied.reason === "channel_not_allowed", "eval channel rejected");

const child = { inbox: [], postMessage(data, origin) { this.inbox.push({ data, origin }); } };
const fromHost = postToFrame(child, "route", { action: "navigate", path: "/m/host" }, standalone);
check(fromHost.ok && child.inbox[0].data.source === HOST_SOURCE, "host → frame source");

uninstall();
check(embedded.__listeners.length === 0, "listener removed");

if (failures.length) {
  console.error("FAIL G15 F014 host-message protocol");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: F014 HostBridgePort postMessage login/route/file/close on mobile-browser; APP WebView / WeChat/DingTalk production leftover");
