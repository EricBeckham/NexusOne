#!/usr/bin/env node
/**
 * G15 leftover: APP WebView native-bridge contract stub.
 * Same HostBridgePort envelope. Models iOS WK / Android JSInterface
 * handler names. Does not claim a store-ready IPA/APK or production
 * WeChat / DingTalk. Distinct from #114 / #117 / #132 web simulations.
 * No Docker. No Playwright. No Xcode / Gradle.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "../src/mobile/host/fakeWindow.js";
import {
  HOST_CHANNELS,
  H5_SOURCE,
  HOST_SOURCE,
  NATIVE_ANDROID_OBJECT,
  NATIVE_BRIDGE_HOST,
  NATIVE_IOS_HANDLER,
  decodeHostMessage,
  encodeHostMessage,
  hostCapabilities,
  isSafeHostPath,
  postHost,
} from "../src/mobile/host/hostBridge.js";
import {
  NATIVE_BRIDGE_WALK,
  NOT_CLAIMED_VS_SIMULATIONS,
  applyNativeBridgeInbound,
  attachNativeBridge,
  childSrcForNativeBridge,
  decodeNativeBridgeMessage,
  describeNativeBridgeContract,
  isAllowedNativeBridgeChannel,
  isAllowedNativeBridgeOrigin,
  isAllowedNativeBridgeSrc,
  postToNativeBridgeChild,
  walkNativeBridgeOutbound,
} from "../src/mobile/host/nativeBridgeStub.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  router: read("src/router.js"),
  hostBridge: read("src/mobile/host/hostBridge.js"),
  adapter: read("src/mobile/host/nativeBridgeStub.js"),
  harness: read("src/mobile/products/p3-studio/pages/NativeBridgeHarnessPage.vue"),
  hostPage: read("src/mobile/products/p3-studio/pages/HostMessagePage.vue"),
  home: read("src/mobile/products/p3-studio/pages/HomePage.vue"),
  doc: read("../docs/g15-native-bridge-stub.md"),
  claim: read("../docs/planning/团队领取任务清单_V1.1.md"),
  docsIndex: read("../docs/README.md"),
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

check(HOST_CHANNELS.join(",") === "login,route,file,close", "must reuse F014 channels");
check(childSrcForNativeBridge() === "/m/host?embedded=true&host=native-bridge-stub", "native child src");
check(isSafeHostPath("/m/native-bridge-harness") === false, "harness path is not a business route");
check(NATIVE_BRIDGE_WALK.map(([channel]) => channel).join(",") === "login,route,file,close", "walk all four channels");
check(isAllowedNativeBridgeChannel("eval") === false, "eval channel denied");

const standalone = createFakeWindow({ embedded: false });
const standaloneCaps = hostCapabilities(standalone);
check(standaloneCaps.otherHostsOpen.includes("APP WebView"), "APP WebView leftover stays listed");
check(
  standaloneCaps.otherHostsOpen.includes("WeChat/DingTalk 小程序 production"),
  "WeChat/DingTalk production leftover stays listed",
);
check(standaloneCaps.nativeBinary === false, "standalone must not claim native binary");
check(standaloneCaps.storeReady === false, "standalone must not claim store-ready");
check(standaloneCaps.productionAccepted === false, "standalone must not claim production");

for (const platform of ["ios", "android"]) {
  const win = createFakeWindow({ nativeBridge: platform });
  const caps = hostCapabilities(win);
  check(caps.host === NATIVE_BRIDGE_HOST, `${platform} stub host kind`);
  check(caps.accepted === true, `${platform} stub contract is accepted as a stub`);
  check(caps.nativeBinary === false, `${platform} must not claim native binary`);
  check(caps.storeReady === false, `${platform} must not claim store-ready`);
  check(caps.storePackage === false, `${platform} must not claim store package`);
  check(caps.productionAccepted === false, `${platform} must not claim production`);
  check(caps.fallback === null, `${platform} stub has no web fallback`);

  const contract = describeNativeBridgeContract(win, platform);
  check(contract.stub === true, `${platform} describe.stub`);
  check(contract.storeReady === false, `${platform} describe.storeReady`);
  check(contract.storePackage === false, `${platform} describe.storePackage`);
  check(contract.iosProject === false, `${platform} no Xcode project`);
  check(contract.androidProject === false, `${platform} no Gradle project`);
  check(contract.notClaimed["#114"] === NOT_CLAIMED_VS_SIMULATIONS["#114"], `${platform} #114 honesty`);
  check(contract.notClaimed["#117"] === NOT_CLAIMED_VS_SIMULATIONS["#117"], `${platform} #117 honesty`);
  check(contract.notClaimed["#132"] === NOT_CLAIMED_VS_SIMULATIONS["#132"], `${platform} #132 honesty`);
  check(contract.originAllowlist.join(",") === win.location.origin, `${platform} origin allowlist is H5 origin only`);

  check(isAllowedNativeBridgeOrigin(win.location.origin, win) === true, `${platform} same origin allowed`);
  check(isAllowedNativeBridgeSrc(childSrcForNativeBridge(), win) === true, `${platform} same-origin /m/host src`);
  check(isAllowedNativeBridgeSrc("https://evil.example/m/host", win) === false, "off-site src denied");
  check(isAllowedNativeBridgeSrc("/m/native-bridge-harness", win) === false, "harness src denied");
  check(isAllowedNativeBridgeSrc("/m/app-webview-harness", win) === false, "#114 harness src denied");
  check(isAllowedNativeBridgeSrc("/m/wx-dt-harness", win) === false, "#132 harness src denied");
  check(isAllowedNativeBridgeSrc("/m/mp-webview-harness", win) === false, "#117 harness src denied");

  const inbound = applyNativeBridgeInbound(createFakeWindow({ nativeBridge: platform }));
  check(inbound.seen.includes("login:request"), `${platform} inbound login applied`);
  check(inbound.seen.includes("route:navigate") || inbound.seen.includes("nav:/m/p3/files?tab=recent"), `${platform} inbound route`);
  check(inbound.seen.includes("file:open") || inbound.seen.includes("nav:/m/p3/files/demo-file-id"), `${platform} inbound file`);
  check(inbound.seen.includes("close"), `${platform} inbound close`);
  check(inbound.caps.storeReady === false, `${platform} inbound caps stay honest`);
  inbound.uninstall();

  const outboundWin = createFakeWindow({ nativeBridge: platform });
  const outbound = walkNativeBridgeOutbound(outboundWin);
  check(outbound.every((item) => item.ok), `${platform} outbound login/route/file/close ok`);
  check(outboundWin.__nativeInbox.length === 4, `${platform} four envelopes on native handler`);
  check(outboundWin.__nativeInbox.every((item) => item.data.source === H5_SOURCE), `${platform} outbound source nexus-h5`);
  check(
    outboundWin.__nativeInbox.map((item) => item.data.channel).join(",") === "login,route,file,close",
    `${platform} outbound channel order`,
  );
  check(outboundWin.__parentInbox.length === 0, `${platform} does not post to window.parent`);
  check(outboundWin.__appWebViewInbox.length === 0, `${platform} does not use #114 NexusAppWebView`);

  const handshake = applyNativeBridgeInbound(createFakeWindow({ nativeBridge: platform }), []);
  check(handshake.caps.host === NATIVE_BRIDGE_HOST, `${platform} handshake host`);
  handshake.uninstall();

  const child = { inbox: [], postMessage(data, origin) { this.inbox.push({ data, origin }); } };
  for (const [channel, payload] of NATIVE_BRIDGE_WALK) {
    const sent = postToNativeBridgeChild(child, channel, payload, win);
    check(sent.ok === true, `${platform} parent → h5 ${channel} ok`);
    check(child.inbox.at(-1)?.data?.source === HOST_SOURCE, `${platform} parent → h5 ${channel} source`);
  }
  check(postToNativeBridgeChild(child, "eval", {}, win).reason === "channel_not_allowed", `${platform} eval denied`);

  check(decodeNativeBridgeMessage({
    origin: "https://evil.example",
    data: { source: H5_SOURCE, channel: "close", payload: { action: "close" } },
  }, win).reason === "origin_rejected", `${platform} evil origin rejected`);
  check(decodeHostMessage({
    origin: "https://servicewechat.com",
    data: { source: HOST_SOURCE, channel: "close", payload: { action: "close" } },
  }, win).reason === "origin_rejected", `${platform} WeChat origin rejected`);

  check(encodeHostMessage({
    source: HOST_SOURCE,
    channel: "login",
    payload: { action: "done", access_token: "leak" },
  }).reason === "secret_key", "token payload rejected");
}

const attached = attachNativeBridge({}, "ios");
check(attached.storeReady === false && attached.nativeBinary === false, "attach stays honest");
check(postHost("eval", {}, createFakeWindow({ nativeBridge: "ios" })).reason === "channel_not_allowed", "eval rejected");

check(files.router.includes('path: "/m/native-bridge-harness"'), "router wires native-bridge harness");
check(files.harness.includes("native-bridge-harness"), "harness page marker");
check(files.harness.includes("native-bridge-pick-ios") && files.harness.includes("native-bridge-pick-android"), "platform switch");
check(files.harness.includes("不是上架") || files.harness.includes("非上架"), "harness states not store-ready");
check(files.harness.includes("#114") && files.harness.includes("#117") && files.harness.includes("#132"), "harness names web simulations");
check(files.harness.includes("storeReady"), "harness shows storeReady");
check(files.hostPage.includes("/m/native-bridge-harness"), "host page links native-bridge harness");
check(files.home.includes("/m/native-bridge-harness"), "home links native-bridge harness");
check(files.adapter.includes("not a store-ready") || files.adapter.includes("not a store-ready IPA"), "adapter stays honest");
check(files.adapter.includes("#114") && files.adapter.includes("#117") && files.adapter.includes("#132"), "adapter names simulations");
check(files.hostBridge.includes("NATIVE_IOS_HANDLER") && files.hostBridge.includes(NATIVE_ANDROID_OBJECT), "hostBridge knows native handlers");
check(files.hostBridge.includes(NATIVE_IOS_HANDLER), "iOS handler name");
check(files.doc.includes("does not close product G15") || files.doc.includes("does **not** close product G15"), "doc must not close G15");
check(files.doc.includes("#114") && files.doc.includes("#117") && files.doc.includes("#132"), "doc contrasts web simulations");
check(files.doc.includes("store-ready") || files.doc.includes("上架"), "doc names store-ready leftover");
check(!files.doc.includes("store-ready APP accepted") && !files.doc.includes("storeReady: true"), "doc must not claim store-ready");
check(files.claim.includes("g15-native-bridge-stub.md"), "claim-list leftover row links this stub");
check(files.claim.includes("上架") || files.claim.includes("原生桥"), "claim-list keeps native leftover honest");
check(files.docsIndex.includes("g15-native-bridge-stub"), "docs index lists this leftover");

forbidIncludes("hostBridge", files.hostBridge, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("nativeBridgeStub", files.adapter, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("native-bridge harness", files.harness, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("host page", files.hostPage, ["isWeChat", "isDingTalk", "wx.miniProgram"]);

for (const [name, text] of Object.entries({
  ledger: files.ledger,
  ledgerDetail: files.ledgerDetail,
  formList: files.formList,
  formStudio: files.formStudio,
  todos: files.todos,
  notify: files.notify,
})) {
  forbidIncludes(`desktop ${name}`, text, ["/m/native-bridge-harness", "nativeBridgeStub", "NxMobileShell"]);
}

if (failures.length) {
  console.error("FAIL G15 native-bridge contract stub");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: native-bridge stub (iOS WK / Android JS) same F014 envelope; not store-ready APP; #114/#117/#132 leftover");
