#!/usr/bin/env node
/**
 * G15 leftover: WeChat / DingTalk production mini-program acceptance
 * gap honesty. Same F014 envelope as #117 / #132 / #151. Contract stub
 * that is NOT production验收. Keeps the #162 FieldPolicy mask leftover
 * row. No Docker. No Playwright. Desktop P3 untouched.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "../src/mobile/host/fakeWindow.js";
import { HOST_CHANNELS, hostCapabilities, isSafeHostPath } from "../src/mobile/host/hostBridge.js";
import { VENDOR_PROD_ORIGINS } from "../src/mobile/host/wechatDingTalkHost.js";
import {
  NOT_CLAIMED_VS_LEFTOVERS,
  PROD_GAP_HARNESS_PATH,
  describeProdGapContract,
  honestProdGapReport,
  probeSameEnvelopeDenied,
  probeVendorOriginsDenied,
  verifyProdGapReport,
} from "../src/mobile/host/wechatDingTalkProdGapHonesty.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  router: read("src/router.js"),
  hostBridge: read("src/mobile/host/hostBridge.js"),
  adapter: read("src/mobile/host/wechatDingTalkProdGapHonesty.js"),
  stub: read("src/mobile/host/wechatDingTalkHost.js"),
  harness: read("src/mobile/products/p3-studio/pages/WeChatDingTalkProdGapHonestyPage.vue"),
  hostPage: read("src/mobile/products/p3-studio/pages/HostMessagePage.vue"),
  home: read("src/mobile/products/p3-studio/pages/HomePage.vue"),
  doc: read("../docs/g15-wx-dt-prod-gap-honesty.md"),
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
check(isSafeHostPath(PROD_GAP_HARNESS_PATH) === false, "honesty harness is not a business route");

const standalone = createFakeWindow({ embedded: false });
const standaloneCaps = hostCapabilities(standalone);
check(standaloneCaps.otherHostsOpen.includes("APP WebView"), "APP WebView leftover stays listed");
check(
  standaloneCaps.otherHostsOpen.includes("WeChat/DingTalk 小程序 production"),
  "WeChat/DingTalk production leftover stays listed",
);
check(standaloneCaps.productionAccepted === false, "standalone must not claim production");

for (const vendor of ["wechat", "dingtalk"]) {
  const win = createFakeWindow({
    embedded: true,
    search: `?embedded=true&host=${vendor}-prod-stub`,
  });
  const contract = describeProdGapContract(win, vendor);
  check(contract.leftoverKind === "wx-dt-prod-gap-honesty", `${vendor} leftover kind`);
  check(contract.stub === true, `${vendor} stub`);
  check(contract.productionAccepted === false, `${vendor} productionAccepted`);
  check(contract.g15Close === "NOT_CLAIMED", `${vendor} g15Close`);
  check(contract.wechatMiniProgramProduction === "SKIP", `${vendor} wechat SKIP`);
  check(contract.dingtalkMiniProgramProduction === "SKIP", `${vendor} dingtalk SKIP`);
  check(contract.vendorJsapiLoaded === false, `${vendor} no JSAPI`);
  check(contract.vendorDomainAllowed === false, `${vendor} no vendor domain`);
  check(contract.notClaimed["#117"] === NOT_CLAIMED_VS_LEFTOVERS["#117"], `${vendor} #117 honesty`);
  check(contract.notClaimed["#132"] === NOT_CLAIMED_VS_LEFTOVERS["#132"], `${vendor} #132 honesty`);
  check(contract.notClaimed["#151"] === NOT_CLAIMED_VS_LEFTOVERS["#151"], `${vendor} #151 honesty`);

  const origins = probeVendorOriginsDenied(win);
  check(origins.length === VENDOR_PROD_ORIGINS.length, `${vendor} vendor origin count`);
  check(origins.every((item) => item.ok), `${vendor} vendor origins origin_rejected`);
  const denied = probeSameEnvelopeDenied(win);
  check(denied.tokenRejected && denied.evalRejected, `${vendor} token/eval denied`);
  check(denied.wechatSrcDenied && denied.dingtalkSrcDenied, `${vendor} vendor src denied`);
  check(denied.harnessSrcDenied, `${vendor} honesty harness src denied`);
}

const honest = verifyProdGapReport(honestProdGapReport());
check(honest.ok === true, `honest report must pass: ${honest.errors.join("; ")}`);
const fakeClose = verifyProdGapReport({ ...honestProdGapReport(), g15_close: "CLOSED" });
check(fakeClose.ok === false, "fake g15_close=CLOSED must fail");
const fakePass = verifyProdGapReport({
  ...honestProdGapReport(),
  wechat_miniprogram_production: "PASS",
  wechat_walk_observed: "false",
});
check(fakePass.ok === false, "fake wechat PASS without walk must fail");

check(files.router.includes('path: "/m/wx-dt-prod-gap-harness"'), "router wires honesty harness");
check(files.harness.includes("wx-dt-prod-gap-harness"), "harness page marker");
check(files.harness.includes("不是生产验收"), "harness states not production accept");
check(files.harness.includes("#117") && files.harness.includes("#132") && files.harness.includes("#151"), "harness names leftovers");
check(files.harness.includes("g15Close") || files.harness.includes("g15_close") || files.harness.includes("NOT_CLAIMED"), "harness shows G15 not claimed");
check(files.hostPage.includes("/m/wx-dt-prod-gap-harness"), "host page links honesty harness");
check(files.home.includes("/m/wx-dt-prod-gap-harness"), "home links honesty harness");
check(files.hostBridge.includes("wx-dt-prod-gap-harness"), "hostBridge denies honesty harness path");
check(files.adapter.includes("does not close product G15") || files.adapter.includes("Does not close product G15"), "adapter stays honest");
check(files.stub.includes("does not accept vendor") || files.stub.includes("does not accept"), "#132 stub stays honest");

check(files.doc.includes("does not close product G15") || files.doc.includes("does **not** close product G15"), "doc must not close G15");
check(files.doc.includes("#117") && files.doc.includes("#132") && files.doc.includes("#151"), "doc contrasts leftovers");
check(files.doc.includes("g9-live-multi-jvm-replica"), "doc keeps #161 leftover row");
check(files.doc.includes("g17-gm-tls-nginx-handshake"), "doc keeps #160 leftover row");
check(!files.doc.includes("production accepted") && !files.doc.includes("productionAccepted: true"), "doc must not claim production");
check(files.claim.includes("g15-wx-dt-prod-gap-honesty.md"), "claim-list leftover row links this honesty stub");
check(files.claim.includes("不是生产验收") || files.claim.includes("生产小程序"), "claim-list keeps production leftover honest");
check(files.docsIndex.includes("g15-wx-dt-prod-gap-honesty"), "docs index lists this leftover");
check(files.docsIndex.includes("g9-live-multi-jvm-replica"), "docs index keeps #161 leftover row");
check(files.docsIndex.includes("g17-gm-tls-nginx-handshake"), "docs index keeps #160 leftover row");
check(files.docsIndex.includes("g15-native-bridge-stub"), "docs index keeps #151 leftover row");
check(files.docsIndex.includes("g15-wx-dt-prod-host"), "docs index keeps #132 leftover row");

forbidIncludes("hostBridge", files.hostBridge, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("prod gap honesty", files.adapter, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
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
    "/m/wx-dt-prod-gap-harness",
    "wechatDingTalkProdGapHonesty",
    "NxMobileShell",
  ]);
}

if (failures.length) {
  console.error("FAIL G15 WeChat/DingTalk production mini-program gap honesty");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: WeChat/DingTalk prod-gap honesty stub; not production accept; #117/#132/#151 leftover; #162 row kept");
