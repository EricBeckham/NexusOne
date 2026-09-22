#!/usr/bin/env node
/**
 * G15 leftover: WeChat / DingTalk production host contract stub.
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
  VENDOR_DINGTALK_HOST,
  VENDOR_WECHAT_HOST,
  applyWebFallback,
  decodeHostMessage,
  encodeHostMessage,
  hostCapabilities,
  installHostBridge,
  isSafeHostPath,
} from "../src/mobile/host/hostBridge.js";
import {
  VENDOR_PROD_ORIGINS,
  VENDOR_STUB_WALK,
  childSrcForVendor,
  decodeVendorHostMessage,
  describeVendorProdContract,
  isAllowedVendorHostChannel,
  isAllowedVendorHostOrigin,
  isAllowedVendorHostSrc,
  postToVendorHost,
} from "../src/mobile/host/wechatDingTalkHost.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  router: read("src/router.js"),
  hostBridge: read("src/mobile/host/hostBridge.js"),
  adapter: read("src/mobile/host/wechatDingTalkHost.js"),
  harness: read("src/mobile/products/p3-studio/pages/WeChatDingTalkHarnessPage.vue"),
  hostPage: read("src/mobile/products/p3-studio/pages/HostMessagePage.vue"),
  home: read("src/mobile/products/p3-studio/pages/HomePage.vue"),
  doc: read("../docs/g15-wx-dt-prod-host.md"),
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
check(childSrcForVendor("wechat") === "/m/host?embedded=true&host=wechat-prod-stub", "wechat child src");
check(childSrcForVendor("dingtalk") === "/m/host?embedded=true&host=dingtalk-prod-stub", "dingtalk child src");
check(isSafeHostPath("/m/wx-dt-harness") === false, "harness path is not a business route");
check(VENDOR_STUB_WALK.map(([channel]) => channel).join(",") === "login,route,file,close", "walk all four channels");

const standalone = createFakeWindow({ embedded: false });
const standaloneCaps = hostCapabilities(standalone);
check(standaloneCaps.otherHostsOpen.includes("APP WebView"), "APP WebView leftover stays listed");
check(
  standaloneCaps.otherHostsOpen.includes("WeChat/DingTalk 小程序 production"),
  "WeChat/DingTalk production leftover stays listed",
);
check(standaloneCaps.productionAccepted === false, "standalone must not claim production");

for (const [vendor, hostKind] of [
  ["wechat", VENDOR_WECHAT_HOST],
  ["dingtalk", VENDOR_DINGTALK_HOST],
]) {
  const parent = createFakeWindow({
    embedded: true,
    search: `?embedded=true&host=${vendor}-prod-stub`,
  });
  const caps = hostCapabilities(parent);
  check(caps.host === hostKind, `${vendor} stub host kind`);
  check(caps.accepted === true, `${vendor} stub contract is accepted as a stub`);
  check(caps.productionAccepted === false, `${vendor} must not claim production`);
  check(caps.transport === "postMessage", `${vendor} transport stays postMessage`);
  check(caps.fallback === null, `${vendor} stub has no web fallback`);

  const contract = describeVendorProdContract(parent, vendor);
  check(contract.stub === true, `${vendor} describe.stub`);
  check(contract.productionAccepted === false, `${vendor} describe.productionAccepted`);
  check(contract.vendors.wechat.productionAccepted === false, "wechat production stays false");
  check(contract.vendors.dingtalk.productionAccepted === false, "dingtalk production stays false");
  check(contract.originAllowlist.join(",") === parent.location.origin, `${vendor} origin allowlist is H5 origin only`);

  check(isAllowedVendorHostOrigin(parent.location.origin, parent) === true, `${vendor} same origin allowed`);
  check(isAllowedVendorHostChannel("login") && isAllowedVendorHostChannel("close"), "four channels allowed");
  check(isAllowedVendorHostChannel("eval") === false, "eval channel denied");
  check(isAllowedVendorHostSrc(childSrcForVendor(vendor), parent) === true, `${vendor} same-origin /m/host src allowed`);
  check(isAllowedVendorHostSrc("https://evil.example/m/host", parent) === false, "off-site src denied");
  check(isAllowedVendorHostSrc("https://servicewechat.com/m/host", parent) === false, "WeChat src denied");
  check(isAllowedVendorHostSrc("https://h5.dingtalk.com/m/host", parent) === false, "DingTalk src denied");
  check(isAllowedVendorHostSrc("/m/host?access_token=x", parent) === false, "token src denied");
  check(isAllowedVendorHostSrc("/m/wx-dt-harness", parent) === false, "harness src denied");

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

  check(parent.__parentInbox[0]?.data?.source === H5_SOURCE, `${vendor} handshake source nexus-h5`);
  check(parent.__parentInbox[0]?.data?.channel === "login", `${vendor} handshake channel login`);
  check(parent.__parentInbox[0]?.data?.payload?.action === "ready", `${vendor} handshake action ready`);
  check(parent.__parentInbox[0]?.targetOrigin === parent.location.origin, `${vendor} handshake same-origin`);

  const inboundReady = decodeVendorHostMessage({
    origin: parent.location.origin,
    data: parent.__parentInbox[0].data,
  }, parent);
  check(inboundReady.ok === true, `${vendor} parent accepts same-origin handshake`);

  for (const [channel, payload] of VENDOR_STUB_WALK) {
    parent.__dispatch({
      origin: parent.location.origin,
      data: { source: HOST_SOURCE, channel, payload },
    });
  }
  check(seen.includes("login:request"), `${vendor} inbound login applied`);
  check(seen.includes("route:navigate") || seen.includes("nav:/m/p3/files?tab=recent"), `${vendor} inbound route applied`);
  check(seen.includes("file:open") || seen.includes("nav:/m/p3/files/demo-file-id"), `${vendor} inbound file applied`);
  check(seen.includes("close"), `${vendor} inbound close applied`);

  const child = { inbox: [], postMessage(data, origin) { this.inbox.push({ data, origin }); } };
  for (const [channel, payload] of VENDOR_STUB_WALK) {
    const sent = postToVendorHost(child, channel, payload, parent);
    check(sent.ok === true, `${vendor} parent → h5 ${channel} ok`);
    check(child.inbox.at(-1)?.data?.source === HOST_SOURCE, `${vendor} parent → h5 ${channel} source`);
    check(child.inbox.at(-1)?.origin === parent.location.origin, `${vendor} parent → h5 ${channel} origin`);
  }
  check(postToVendorHost(child, "eval", {}, parent).reason === "channel_not_allowed", `${vendor} parent eval denied`);

  for (const origin of VENDOR_PROD_ORIGINS) {
    const decoded = decodeVendorHostMessage({
      origin,
      data: { source: H5_SOURCE, channel: "close", payload: { action: "close" } },
    }, parent);
    check(decoded.reason === "origin_rejected", `${origin} must be origin_rejected under ${vendor}`);
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
  check(parent.__listeners.length === 0, `${vendor} listener removed`);
}

check(files.router.includes('path: "/m/wx-dt-harness"'), "router wires wx-dt harness");
check(files.harness.includes("wx-dt-harness"), "harness page marker");
check(files.harness.includes("wx-dt-pick-wechat") && files.harness.includes("wx-dt-pick-dingtalk"), "harness vendor switch");
check(files.harness.includes("不是微信") || files.harness.includes("不是生产验收"), "harness states not production");
check(files.harness.includes("productionAccepted"), "harness shows productionAccepted");
check(files.hostPage.includes("/m/wx-dt-harness"), "host page links wx-dt harness");
check(files.home.includes("/m/wx-dt-harness"), "home links wx-dt harness");
check(files.doc.includes("WeChat") && files.doc.includes("DingTalk"), "doc names vendors");
check(files.doc.includes("HostBridgePort"), "doc names HostBridgePort");
check(files.doc.includes("does not close product G15") || files.doc.includes("does **not** close product G15"), "doc must not close G15");
check(!files.doc.includes("production accepted") && !files.doc.includes("productionAccepted: true"), "doc must not claim production");
check(files.adapter.includes("does not accept vendor") || files.adapter.includes("does not accept"), "adapter stays honest");

forbidIncludes("hostBridge", files.hostBridge, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("wechatDingTalkHost", files.adapter, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("wx-dt harness", files.harness, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("host page", files.hostPage, ["isWeChat", "isDingTalk", "wx.miniProgram"]);

for (const [name, text] of Object.entries({
  ledger: files.ledger,
  ledgerDetail: files.ledgerDetail,
  formList: files.formList,
  formStudio: files.formStudio,
  todos: files.todos,
  notify: files.notify,
})) {
  forbidIncludes(`desktop ${name}`, text, ["/m/wx-dt-harness", "wechatDingTalkHost", "NxMobileShell"]);
}

if (failures.length) {
  console.error("FAIL G15 WeChat/DingTalk production host contract stub");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: WeChat/DingTalk production host contract stub allowlist (origin/channel); production leftover");
