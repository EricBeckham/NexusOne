#!/usr/bin/env node
/**
 * G15 leftover: APP WebView native-bridge honesty beyond #151.
 * Same F014 envelope as #114 / #117 / #132 / #151 / #167.
 * Extends honest PASS/SKIP. NOT IPA/APK production accept.
 * Does not close product G15. Keeps both leftover rows.
 * No Docker. No Playwright. Desktop P3 untouched.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "../src/mobile/host/fakeWindow.js";
import { HOST_CHANNELS, hostCapabilities, isSafeHostPath } from "../src/mobile/host/hostBridge.js";
import {
  BOTH_LEFTOVER_ROWS,
  NATIVE_BRIDGE_HONESTY_PATH,
  NOT_CLAIMED_VS_LEFTOVERS,
  describeNativeBridgeHonestyContract,
  honestNativeBridgeReport,
  probeNativeEnvelopeDenied,
  probeNativeOriginsDenied,
  verifyNativeBridgeHonestyReport,
} from "../src/mobile/host/nativeBridgeHonesty.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  router: read("src/router.js"),
  hostBridge: read("src/mobile/host/hostBridge.js"),
  adapter: read("src/mobile/host/nativeBridgeHonesty.js"),
  stub: read("src/mobile/host/nativeBridgeStub.js"),
  harness: read("src/mobile/products/p3-studio/pages/NativeBridgeHonestyPage.vue"),
  stubHarness: read("src/mobile/products/p3-studio/pages/NativeBridgeHarnessPage.vue"),
  hostPage: read("src/mobile/products/p3-studio/pages/HostMessagePage.vue"),
  home: read("src/mobile/products/p3-studio/pages/HomePage.vue"),
  doc: read("../docs/g15-native-bridge-honesty.md"),
  stubDoc: read("../docs/g15-native-bridge-stub.md"),
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
check(isSafeHostPath(NATIVE_BRIDGE_HONESTY_PATH) === false, "honesty harness is not a business route");

const standalone = createFakeWindow({ embedded: false });
const standaloneCaps = hostCapabilities(standalone);
check(standaloneCaps.otherHostsOpen.includes("APP WebView"), "APP WebView leftover stays listed");
check(
  standaloneCaps.otherHostsOpen.includes("WeChat/DingTalk 小程序 production"),
  "WeChat/DingTalk production leftover stays listed",
);
check(standaloneCaps.storeReady === false, "standalone must not claim store-ready");
check(standaloneCaps.nativeBinary === false, "standalone must not claim native binary");
check(standaloneCaps.productionAccepted === false, "standalone must not claim production");

for (const platform of ["ios", "android"]) {
  const win = createFakeWindow({ nativeBridge: platform });
  const contract = describeNativeBridgeHonestyContract(win, platform);
  check(contract.leftoverKind === "native-bridge-honesty", `${platform} leftover kind`);
  check(contract.stub === true, `${platform} stub`);
  check(contract.storeReady === false, `${platform} storeReady`);
  check(contract.g15Close === "NOT_CLAIMED", `${platform} g15Close`);
  check(contract.stubContract === "PASS", `${platform} stub contract PASS`);
  check(contract.iosIpaProduction === "SKIP", `${platform} ios IPA SKIP`);
  check(contract.androidApkProduction === "SKIP", `${platform} android APK SKIP`);
  check(contract.storeReadyStatus === "SKIP", `${platform} storeReady SKIP`);
  check(contract.iosWalkObserved === false, `${platform} no iOS walk`);
  check(contract.androidWalkObserved === false, `${platform} no Android walk`);
  check(contract.notClaimed["#114"] === NOT_CLAIMED_VS_LEFTOVERS["#114"], `${platform} #114 honesty`);
  check(contract.notClaimed["#117"] === NOT_CLAIMED_VS_LEFTOVERS["#117"], `${platform} #117 honesty`);
  check(contract.notClaimed["#132"] === NOT_CLAIMED_VS_LEFTOVERS["#132"], `${platform} #132 honesty`);
  check(contract.notClaimed["#151"] === NOT_CLAIMED_VS_LEFTOVERS["#151"], `${platform} #151 honesty`);
  check(contract.notClaimed["#167"] === NOT_CLAIMED_VS_LEFTOVERS["#167"], `${platform} #167 honesty`);
  check(
    contract.leftoverRowsKept.join(",") === BOTH_LEFTOVER_ROWS.join(","),
    `${platform} both leftover rows`,
  );

  const origins = probeNativeOriginsDenied(win);
  check(origins.length === 3, `${platform} origin count`);
  check(origins.every((item) => item.ok), `${platform} evil/vendor origins origin_rejected`);
  const denied = probeNativeEnvelopeDenied(win);
  check(denied.tokenRejected && denied.evalRejected, `${platform} token/eval denied`);
  check(denied.harnessSrcDenied, `${platform} honesty harness src denied`);
  check(denied.stubHarnessSrcDenied, `${platform} #151 harness src denied`);
}

const honest = verifyNativeBridgeHonestyReport(honestNativeBridgeReport());
check(honest.ok === true, `honest report must pass: ${honest.errors.join("; ")}`);
const fakeClose = verifyNativeBridgeHonestyReport({ ...honestNativeBridgeReport(), g15_close: "CLOSED" });
check(fakeClose.ok === false, "fake g15_close=CLOSED must fail");
const fakeIpa = verifyNativeBridgeHonestyReport({
  ...honestNativeBridgeReport(),
  ios_ipa_production: "PASS",
  ios_walk_observed: "false",
});
check(fakeIpa.ok === false, "fake IPA PASS without walk must fail");
const fakeApk = verifyNativeBridgeHonestyReport({
  ...honestNativeBridgeReport(),
  android_apk_production: "PASS",
  android_walk_observed: "false",
});
check(fakeApk.ok === false, "fake APK PASS without walk must fail");

check(files.router.includes('path: "/m/native-bridge-honesty-harness"'), "router wires honesty harness");
check(files.harness.includes("native-bridge-honesty-harness"), "harness page marker");
check(files.harness.includes("不是上架") || files.harness.includes("非上架"), "harness states not store-ready");
check(files.harness.includes("#114") && files.harness.includes("#117") && files.harness.includes("#132"), "harness names leftovers");
check(files.harness.includes("#151") && files.harness.includes("#167"), "harness names #151/#167");
check(files.harness.includes("g15Close") || files.harness.includes("NOT_CLAIMED"), "harness shows G15 not claimed");
check(files.hostPage.includes("/m/native-bridge-honesty-harness"), "host page links honesty harness");
check(files.home.includes("/m/native-bridge-honesty-harness"), "home links honesty harness");
check(files.hostBridge.includes("native-bridge-honesty-harness"), "hostBridge denies honesty harness path");
check(files.adapter.includes("Does not close product G15"), "adapter stays honest");
check(files.stub.includes("not a store-ready") || files.stub.includes("not a store-ready IPA"), "#151 stub stays honest");
check(files.stubHarness.includes("native-bridge-harness"), "#151 harness page stays");

check(files.doc.includes("does not close product G15") || files.doc.includes("does **not** close product G15"), "doc must not close G15");
check(files.doc.includes("#114") && files.doc.includes("#117") && files.doc.includes("#132"), "doc contrasts leftovers");
check(files.doc.includes("#151") && files.doc.includes("#167"), "doc contrasts #151/#167");
check(files.doc.includes("g15-native-bridge-stub") && files.doc.includes("g15-wx-dt-prod-gap-honesty"), "doc keeps both leftover rows");
check(files.doc.includes("g7-withdraw-browser") && files.doc.includes("g7-query-expense-travel"), "doc keeps G7 leftover rows");
check(!files.doc.includes("store-ready APP accepted") && !files.doc.includes("storeReady: true"), "doc must not claim store-ready");
check(files.stubDoc.includes("does **not** close product G15") || files.stubDoc.includes("does not close product G15"), "#151 stub doc stays honest");
check(files.claim.includes("g15-native-bridge-honesty.md"), "claim-list leftover row links this honesty leftover");
check(files.claim.includes("g15-native-bridge-stub.md"), "claim-list keeps #151 leftover");
check(files.claim.includes("g15-wx-dt-prod-gap-honesty.md"), "claim-list keeps #167 leftover");
check(files.claim.includes("上架") || files.claim.includes("原生桥"), "claim-list keeps native leftover honest");
check(files.docsIndex.includes("g15-native-bridge-honesty"), "docs index lists this leftover");
check(files.docsIndex.includes("g15-native-bridge-stub"), "docs index keeps #151 leftover row");
check(files.docsIndex.includes("g15-wx-dt-prod-gap-honesty"), "docs index keeps #167 leftover row");
check(files.docsIndex.includes("g7-withdraw-browser-honesty"), "docs index keeps #175 leftover row");
check(files.docsIndex.includes("g7-query-expense-travel"), "docs index keeps G7 query leftover row");
check(files.docsIndex.includes("g5-browser-failed-publish"), "docs index keeps #171 leftover row");
check(files.doc.includes("g5-browser-failed-publish"), "doc keeps #171 leftover row");
check(files.claim.includes("g5-browser-failed-publish.md"), "claim-list keeps #171 leftover");
check(files.docsIndex.includes("s0-secret-getenv-snapshot-honesty"), "docs index keeps #180 leftover row");
check(files.doc.includes("s0-secret-getenv-snapshot-honesty"), "doc keeps #180 leftover row");
check(files.docsIndex.includes("s0-secret-rotate-without-restart"), "docs index keeps #143 leftover row");
check(files.docsIndex.includes("g4-pkce-spa-verifier-honesty"), "docs index keeps #177 leftover row");
check(files.doc.includes("g4-pkce-spa-verifier-honesty"), "doc keeps #177 leftover row");
check(files.docsIndex.includes("[g4-pkce-spa-verifier]"), "docs index keeps #147 leftover row");

forbidIncludes("hostBridge", files.hostBridge, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("native-bridge honesty", files.adapter, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("honesty harness", files.harness, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("host page", files.hostPage, ["isWeChat", "isDingTalk", "wx.miniProgram"]);

for (const [name, text] of Object.entries({
  ledger: files.ledger,
  ledgerDetail: files.ledgerDetail,
  formList: files.formList,
  formStudio: files.formStudio,
  todos: files.todos,
  notify: files.notify,
})) {
  forbidIncludes(`desktop ${name}`, text, [
    "/m/native-bridge-honesty-harness",
    "nativeBridgeHonesty",
    "NxMobileShell",
  ]);
}

if (failures.length) {
  console.error("FAIL G15 native-bridge honesty leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: native-bridge honesty leftover; not IPA/APK accept; #114/#117/#132/#151/#167 leftover; both README rows kept");
