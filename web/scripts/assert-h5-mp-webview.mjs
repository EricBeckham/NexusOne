#!/usr/bin/env node
/**
 * G15 leftover: simulated 小程序 web-view parent.
 * Same HostBridgePort channels. Proves origin / channel allowlist.
 * Does not claim WeChat / DingTalk production acceptance.
 * No Docker. No Playwright.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "../src/mobile/host/fakeWindow.js";
import {
  HOST_CHANNELS,
  H5_SOURCE,
  HOST_SOURCE,
  MP_WEBVIEW_HOST,
  applyWebFallback,
  decodeHostMessage,
  encodeHostMessage,
  hostCapabilities,
  installHostBridge,
  isSafeHostPath,
} from "../src/mobile/host/hostBridge.js";
import {
  FOREIGN_WEBVIEW_ORIGINS,
  MP_WEBVIEW_CHILD_SRC,
  decodeMiniProgramMessage,
  describeMpWebViewContract,
  isAllowedMiniProgramChannel,
  isAllowedMiniProgramOrigin,
  isAllowedWebViewSrc,
  postToWebView,
} from "../src/mobile/host/miniprogramWebView.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  router: read("src/router.js"),
  hostBridge: read("src/mobile/host/hostBridge.js"),
  mp: read("src/mobile/host/miniprogramWebView.js"),
  harness: read("src/mobile/products/p3-studio/pages/MiniProgramWebViewHarnessPage.vue"),
  hostPage: read("src/mobile/products/p3-studio/pages/HostMessagePage.vue"),
  home: read("src/mobile/products/p3-studio/pages/HomePage.vue"),
  doc: read("../docs/g15-miniprogram-webview.md"),
  ledger: read("src/views/studio/LedgerListView.vue"),
  ledgerDetail: read("src/views/studio/LedgerDetailView.vue"),
  formList: read("src/views/studio/FormListView.vue"),
  formStudio: read("src/views/studio/FormStudioView.vue"),
  todos: read("src/views/studio/TodoInboxView.vue"),
  notify: read("src/views/studio/NotifyInboxView.vue"),
};

const failures = [];
function check(cond, message) {
  if (!cond) {
    failures.push(message);
  }
}

function forbidIncludes(name, text, snippets) {
  for (const snippet of snippets) {
    if (text.includes(snippet)) {
      failures.push(`${name} must not include ${snippet}`);
    }
  }
}

check(HOST_CHANNELS.join(",") === "login,route,file,close", "must reuse HostBridgePort channels");
check(MP_WEBVIEW_CHILD_SRC === "/m/host?embedded=true&host=mp-webview", "child src must mark simulated host");
check(isSafeHostPath("/m/mp-webview-harness") === false, "harness path is not a business route");

const standalone = createFakeWindow({ embedded: false });
const standaloneCaps = hostCapabilities(standalone);
check(standaloneCaps.otherHostsOpen.includes("APP WebView"), "APP WebView leftover stays listed");
check(
  standaloneCaps.otherHostsOpen.includes("WeChat/DingTalk 小程序 production"),
  "WeChat/DingTalk production leftover stays listed",
);
check(standaloneCaps.productionAccepted === false, "standalone must not claim production");

const parent = createFakeWindow({
  embedded: true,
  search: "?embedded=true&host=mp-webview",
});
const caps = hostCapabilities(parent);
check(caps.host === MP_WEBVIEW_HOST, "embedded host=mp-webview is miniprogram-webview-sim");
check(caps.accepted === true, "simulated contract is accepted");
check(caps.productionAccepted === false, "must not claim WeChat/DingTalk production");
check(caps.transport === "postMessage", "transport stays postMessage");
check(caps.fallback === null, "simulated parent has no web fallback");

const contract = describeMpWebViewContract(parent);
check(contract.vendors.wechat === false && contract.vendors.dingtalk === false, "vendors stay unaccepted");
check(contract.originAllowlist.join(",") === parent.location.origin, "origin allowlist is H5 origin only");

check(isAllowedMiniProgramOrigin(parent.location.origin, parent) === true, "same origin allowed");
check(isAllowedMiniProgramChannel("login") && isAllowedMiniProgramChannel("close"), "four channels allowed");
check(isAllowedMiniProgramChannel("eval") === false, "eval channel denied");
check(isAllowedWebViewSrc(MP_WEBVIEW_CHILD_SRC, parent) === true, "same-origin /m/host src allowed");
check(isAllowedWebViewSrc("https://evil.example/m/host", parent) === false, "off-site web-view src denied");
check(isAllowedWebViewSrc("https://servicewechat.com/m/host", parent) === false, "WeChat src denied");
check(isAllowedWebViewSrc("/m/host?access_token=x", parent) === false, "token src denied");

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
}, parent);

check(parent.__parentInbox[0]?.data?.source === H5_SOURCE, "handshake source nexus-h5");
check(parent.__parentInbox[0]?.data?.channel === "login", "handshake channel login");
check(parent.__parentInbox[0]?.data?.payload?.action === "ready", "handshake action ready");
check(parent.__parentInbox[0]?.targetOrigin === parent.location.origin, "handshake same-origin");

const inboundReady = decodeMiniProgramMessage({
  origin: parent.location.origin,
  data: parent.__parentInbox[0].data,
}, parent);
check(inboundReady.ok === true, "parent accepts same-origin handshake");

for (const [channel, payload] of [
  ["login", { action: "request", returnTo: "/m/host" }],
  ["route", { action: "navigate", path: "/m/p3/files?tab=recent" }],
  ["file", { action: "open", fileId: "demo-file-id" }],
  ["close", { action: "close" }],
]) {
  parent.__dispatch({
    origin: parent.location.origin,
    data: { source: HOST_SOURCE, channel, payload },
  });
}
check(seen.includes("login:request"), "inbound login applied under mp parent");
check(seen.includes("route:navigate") || seen.includes("nav:/m/p3/files?tab=recent"), "inbound route applied under mp parent");
check(seen.includes("file:open") || seen.includes("nav:/m/p3/files/demo-file-id"), "inbound file applied under mp parent");
check(seen.includes("close"), "inbound close applied under mp parent");

const child = { inbox: [], postMessage(data, origin) { this.inbox.push({ data, origin }); } };
for (const channel of HOST_CHANNELS) {
  const payload = channel === "file"
    ? { action: "open", fileId: "demo-file-id" }
    : channel === "route"
      ? { action: "navigate", path: "/m/host" }
      : { action: channel === "close" ? "close" : "request" };
  const sent = postToWebView(child, channel, payload, parent);
  check(sent.ok === true, `parent → web-view ${channel} ok`);
  check(child.inbox.at(-1)?.data?.source === HOST_SOURCE, `parent → web-view ${channel} source`);
  check(child.inbox.at(-1)?.origin === parent.location.origin, `parent → web-view ${channel} origin`);
}
check(postToWebView(child, "eval", {}, parent).reason === "channel_not_allowed", "parent eval denied");

for (const origin of FOREIGN_WEBVIEW_ORIGINS) {
  const decoded = decodeMiniProgramMessage({
    origin,
    data: { source: H5_SOURCE, channel: "close", payload: { action: "close" } },
  }, parent);
  check(decoded.reason === "origin_rejected", `${origin} must be origin_rejected`);
  check(decodeHostMessage({
    origin,
    data: { source: HOST_SOURCE, channel: "close", payload: { action: "close" } },
  }, parent).reason === "origin_rejected", `HostBridgePort also rejects ${origin}`);
}

check(encodeHostMessage({
  source: HOST_SOURCE,
  channel: "login",
  payload: { action: "done", access_token: "leak" },
}).reason === "secret_key", "token payload rejected");

uninstall();
check(parent.__listeners.length === 0, "listener removed");

check(files.router.includes('path: "/m/mp-webview-harness"'), "router wires mp harness");
check(files.harness.includes("mp-webview-harness"), "harness page marker");
check(files.harness.includes("mp-capsule"), "mini-program chrome capsule");
check(files.harness.includes(MP_WEBVIEW_CHILD_SRC) || files.harness.includes("MP_WEBVIEW_CHILD_SRC"), "harness embeds marked child");
check(files.harness.includes("不是微信") || files.harness.includes("不是微信 / 钉钉"), "harness states not production");
check(files.hostPage.includes("/m/mp-webview-harness"), "host page links mp harness");
check(files.home.includes("/m/mp-webview-harness"), "home links mp harness");
check(files.doc.includes("WeChat") && files.doc.includes("DingTalk"), "doc names production leftover");
check(files.doc.includes("HostBridgePort"), "doc names HostBridgePort");
check(!files.doc.includes("closes product G15") && !files.doc.includes("production accepted"), "doc must not close G15 / production");

forbidIncludes("hostBridge", files.hostBridge, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("miniprogramWebView", files.mp, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("mp harness", files.harness, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("host page", files.hostPage, ["isWeChat", "isDingTalk", "wx.miniProgram"]);

for (const [name, text] of Object.entries({
  ledger: files.ledger,
  ledgerDetail: files.ledgerDetail,
  formList: files.formList,
  formStudio: files.formStudio,
  todos: files.todos,
  notify: files.notify,
})) {
  forbidIncludes(`desktop ${name}`, text, ["/m/mp-webview-harness", "miniprogramWebView", "NxMobileShell"]);
}

if (failures.length) {
  console.error("FAIL G15 simulated 小程序 web-view host contract");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: simulated 小程序 web-view parent allowlist (origin/channel) on HostBridgePort; WeChat/DingTalk production leftover");
