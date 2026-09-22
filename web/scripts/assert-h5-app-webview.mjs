#!/usr/bin/env node
/**
 * G15 leftover: APP WebView host contract walk (web harness only).
 * Reuses F014 HostBridgePort. Does not claim a native APP binary.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "../src/mobile/host/fakeWindow.js";
import {
  APP_WEBVIEW_OBJECT,
  HOST_CHANNELS,
  H5_SOURCE,
  HOST_SOURCE,
  decodeHostMessage,
  encodeHostMessage,
  hostCapabilities,
  postHost,
  resetHostBridgeLog,
} from "../src/mobile/host/hostBridge.js";
import {
  APP_WEBVIEW_WALK,
  applyAppWebViewInbound,
  attachAppWebViewBridge,
  proveSameEnvelope,
  walkAppWebViewOutbound,
} from "../src/mobile/host/appWebViewHost.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  router: read("src/router.js"),
  hostBridge: read("src/mobile/host/hostBridge.js"),
  adapter: read("src/mobile/host/appWebViewHost.js"),
  harness: read("src/mobile/products/p3-studio/pages/AppWebViewHarnessPage.vue"),
  hostPage: read("src/mobile/products/p3-studio/pages/HostMessagePage.vue"),
  doc: read("../docs/g15-app-webview-host.md"),
  ledger: read("src/views/studio/LedgerListView.vue"),
  formStudio: read("src/views/studio/FormStudioView.vue"),
  todos: read("src/views/studio/TodoInboxView.vue"),
};

const failures = [];
function check(cond, message) {
  if (!cond) {
    failures.push(message);
  }
}

resetHostBridgeLog();

check(HOST_CHANNELS.join(",") === "login,route,file,close", "must reuse F014 channels");
check(APP_WEBVIEW_OBJECT === "NexusAppWebView", "injected object name");
check(APP_WEBVIEW_WALK.map(([channel]) => channel).join(",") === "login,route,file,close", "walk all four channels");

const topLevel = createFakeWindow({ appWebView: true });
const topCaps = hostCapabilities(topLevel);
check(topCaps.host === "app-webview", "top-level injected host is app-webview");
check(topCaps.accepted === true, "APP WebView contract accepted on web harness");
check(topCaps.transport === "postMessage", "same postMessage transport");
check(topCaps.nativeBinary === false, "must not claim a native binary");
check(topCaps.fallback === null, "injected host has no web fallback");
check(
  topCaps.otherHostsOpen.join(",") === "APP WebView,WeChat/DingTalk 小程序 production",
  "native APP and WeChat/DingTalk production stay open",
);

const handshake = applyAppWebViewInbound(topLevel, []);
check(topLevel.__appWebViewInbox[0]?.data?.source === H5_SOURCE, "handshake source nexus-h5");
check(topLevel.__appWebViewInbox[0]?.data?.channel === "login", "handshake channel login");
check(topLevel.__appWebViewInbox[0]?.data?.payload?.action === "ready", "handshake action ready");
check(topLevel.__appWebViewInbox[0]?.targetOrigin === topLevel.location.origin, "handshake same-origin");
check(topLevel.__parentInbox.length === 0, "top-level WebView does not post to window.parent");
handshake.uninstall();

const inbound = applyAppWebViewInbound(createFakeWindow({ appWebView: true }));
check(inbound.seen.includes("login:request"), "inbound login applied");
check(inbound.seen.includes("route:navigate") || inbound.seen.includes("nav:/m/p3/files?tab=recent"), "inbound route applied");
check(inbound.seen.includes("file:open") || inbound.seen.includes("nav:/m/p3/files/demo-file-id"), "inbound file applied");
check(inbound.seen.includes("close"), "inbound close applied");
inbound.uninstall();

const outboundWin = createFakeWindow({ appWebView: true });
const outbound = walkAppWebViewOutbound(outboundWin);
check(outbound.every((item) => item.ok), "outbound login/route/file/close ok");
check(outboundWin.__appWebViewInbox.length === 4, "four envelopes on NexusAppWebView");
check(outboundWin.__appWebViewInbox.every((item) => item.data.source === H5_SOURCE), "outbound source nexus-h5");
check(
  outboundWin.__appWebViewInbox.map((item) => item.data.channel).join(",") === "login,route,file,close",
  "outbound channel order",
);

const iframe = createFakeWindow({ embedded: true });
for (const [channel, payload] of APP_WEBVIEW_WALK) {
  const proof = proveSameEnvelope(channel, payload, createFakeWindow({ appWebView: true }), iframe);
  check(proof.same, `same F014 envelope on APP WebView and iframe for ${channel}`);
}

const token = encodeHostMessage({
  source: HOST_SOURCE,
  channel: "login",
  payload: { action: "done", access_token: "leak" },
});
check(token.reason === "secret_key", "APP WebView host must reject tokens");

const evalDenied = postHost("eval", {}, outboundWin);
check(evalDenied.reason === "channel_not_allowed", "eval channel rejected");

const evilOrigin = decodeHostMessage({
  origin: "https://evil.example",
  data: { source: HOST_SOURCE, channel: "close", payload: { action: "close" } },
}, outboundWin);
check(evilOrigin.reason === "origin_rejected", "cross-origin rejected");

const hostWin = createFakeWindow();
const child = { inbox: [], postMessage(data, origin) { this.inbox.push({ data, origin }); } };
attachAppWebViewBridge(child);
const fromHost = encodeHostMessage({
  source: HOST_SOURCE,
  channel: "file",
  payload: { action: "open", fileId: "demo-file-id" },
});
check(fromHost.ok && fromHost.envelope.source === HOST_SOURCE, "host → H5 uses nexus-host");
child.postMessage(fromHost.envelope, hostWin.location.origin);
check(child.inbox[0].data.channel === "file", "simulated native deliver file");

check(files.router.includes('path: "/m/app-webview-harness"'), "router wires APP WebView harness");
check(files.hostPage.includes("/m/app-webview-harness"), "host page links the APP WebView harness");
check(files.harness.includes("NexusAppWebView"), "harness injects NexusAppWebView");
check(files.harness.includes("app-webview-send-login"), "harness login control");
check(files.harness.includes("app-webview-send-route"), "harness route control");
check(files.harness.includes("app-webview-send-file"), "harness file control");
check(files.harness.includes("app-webview-send-close"), "harness close control");
check(files.harness.includes("非原生"), "harness labels itself as not native");
check(files.adapter.includes("Does not claim an iOS/Android binary"), "adapter stays honest");
check(!files.hostBridge.includes("isWeChat"), "HostBridgePort still has no WeChat branch");
check(!files.hostBridge.includes("isDingTalk"), "HostBridgePort still has no DingTalk branch");
check(files.doc.includes("APP WebView native binary"), "doc keeps native leftover");
check(files.doc.includes("does not close product G15") || files.doc.includes("does **not** close product G15"), "doc keeps G15 open");
check(files.doc.includes("G18"), "doc leaves G18 closed");
check(!files.doc.includes("native APP accepted"), "doc must not claim native APP");
check(!files.ledger.includes("NexusAppWebView"), "desktop ledger untouched");
check(!files.formStudio.includes("app-webview-harness"), "desktop form studio untouched");
check(!files.todos.includes("appWebViewHost"), "desktop todos untouched");

if (failures.length) {
  console.error("FAIL G15 APP WebView host contract walk");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: F014 envelope login/route/file/close on web APP WebView harness; native APP binary / 小程序 still open");
