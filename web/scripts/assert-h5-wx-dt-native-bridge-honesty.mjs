#!/usr/bin/env node
/**
 * G15 leftover: WeChat / DingTalk prod-gap + native-bridge honesty
 * PASS/SKIP beyond #167/#181. Not a product G15 close. Not IPA/APK.
 * Does not redo #151/#167/#181 product code. Keeps ALL sibling leftover rows.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "../src/mobile/host/fakeWindow.js";
import { HOST_CHANNELS } from "../src/mobile/host/hostBridge.js";
import {
  ALL_SIBLING_LEFTOVER_ROWS,
  LEFTOVER_KIND,
  NOT_CLAIMED_VS_LEFTOVERS,
  SIBLING_LEFTOVER_ROWS,
  describeWxDtNativeBridgeHonestyContract,
  honestWxDtNativeBridgeReport,
  verifyWxDtNativeBridgeHonestyReport,
} from "../src/mobile/host/wxDtNativeBridgeHonesty.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  leftover: read("src/mobile/host/wxDtNativeBridgeHonesty.js"),
  gap: read("src/mobile/host/wechatDingTalkProdGapHonesty.js"),
  native: read("src/mobile/host/nativeBridgeHonesty.js"),
  stub: read("src/mobile/host/nativeBridgeStub.js"),
  router: read("src/router.js"),
  doc: read("../docs/g15-wx-dt-native-bridge-honesty.md"),
  gapDoc: read("../docs/g15-wx-dt-prod-gap-honesty.md"),
  nativeDoc: read("../docs/g15-native-bridge-honesty.md"),
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

const win = createFakeWindow({ embedded: true });
const contract = describeWxDtNativeBridgeHonestyContract(win);
check(contract.leftoverKind === LEFTOVER_KIND, "leftover kind");
check(contract.g15Close === "NOT_CLAIMED", "g15Close");
check(contract.wxDtHarnessContract === "PASS", "#167 harness PASS");
check(contract.nativeBridgeHarnessContract === "PASS", "#181 harness PASS");
check(contract.wechatMiniProgramProduction === "SKIP", "wechat SKIP");
check(contract.dingtalkMiniProgramProduction === "SKIP", "dingtalk SKIP");
check(contract.iosIpaProduction === "SKIP", "ios SKIP");
check(contract.androidApkProduction === "SKIP", "android SKIP");
check(contract.productionAccepted === false, "productionAccepted");
check(contract.storeReady === false, "storeReady");
check(contract.redone151 === false && contract.redone167 === false && contract.redone181 === false, "not redone");
check(contract.notClaimed["#167"] === NOT_CLAIMED_VS_LEFTOVERS["#167"], "#167 honesty");
check(contract.notClaimed["#181"] === NOT_CLAIMED_VS_LEFTOVERS["#181"], "#181 honesty");

const honest = verifyWxDtNativeBridgeHonestyReport(honestWxDtNativeBridgeReport());
check(honest.ok === true, `honest report must pass: ${honest.errors.join("; ")}`);
const fakeClose = verifyWxDtNativeBridgeHonestyReport({
  ...honestWxDtNativeBridgeReport(),
  g15_close: "CLOSED",
});
check(fakeClose.ok === false, "fake g15_close=CLOSED must fail");
const fakeWechat = verifyWxDtNativeBridgeHonestyReport({
  ...honestWxDtNativeBridgeReport(),
  wechat_miniprogram_production: "PASS",
  wechat_walk_observed: "false",
});
check(fakeWechat.ok === false, "fake wechat PASS without walk must fail");
const fakeIpa = verifyWxDtNativeBridgeHonestyReport({
  ...honestWxDtNativeBridgeReport(),
  ios_ipa_production: "PASS",
  ios_walk_observed: "false",
});
check(fakeIpa.ok === false, "fake IPA PASS without walk must fail");

check(files.gap.includes('leftoverKind: "wx-dt-prod-gap-honesty"'), "#167 leftover kind stays");
check(files.native.includes('leftoverKind: "native-bridge-honesty"'), "#181 leftover kind stays");
check(files.stub.includes("not a store-ready"), "#151 stub stays honest");
check(!files.router.includes("wx-dt-native-bridge-honesty-harness"), "no new harness route");
check(files.leftover.includes("Does not redo"), "leftover states not a redo");
check(files.leftover.includes("Does not close product G15"), "leftover stays honest");

check(files.doc.includes("does **not** close product G15") || files.doc.includes("does not close product G15"), "doc must not close G15");
check(files.doc.includes("#167") && files.doc.includes("#181"), "doc extends #167/#181");
check(files.doc.includes("#151"), "doc names #151 leftover");
check(files.doc.includes("g15-wx-dt-prod-gap-honesty") && files.doc.includes("g15-native-bridge-honesty"), "doc keeps parent leftover rows");
check(!files.doc.includes("storeReady: true"), "doc must not claim store-ready");
check(files.gapDoc.includes("g15-wx-dt-native-bridge-honesty"), "#167 doc points at follow-on leftover");
check(files.nativeDoc.includes("g15-wx-dt-native-bridge-honesty"), "#181 doc points at follow-on leftover");
check(files.claim.includes("g15-wx-dt-native-bridge-honesty.md"), "claim-list leftover row links this leftover");
check(files.claim.includes("g15-wx-dt-prod-gap-honesty.md"), "claim-list keeps #167 leftover");
check(files.claim.includes("g15-native-bridge-honesty.md"), "claim-list keeps #181 leftover");
check(files.docsIndex.includes("[g15-wx-dt-native-bridge-honesty]"), "docs index lists this leftover");

for (const row of [...SIBLING_LEFTOVER_ROWS, ...ALL_SIBLING_LEFTOVER_ROWS]) {
  check(files.docsIndex.includes(row), `docs index keeps leftover row ${row}`);
}

forbidIncludes("leftover", files.leftover, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("doc", files.doc, ["g15_close=CLOSED", "product G15 closed"]);

for (const [name, text] of Object.entries({
  ledger: files.ledger,
  ledgerDetail: files.ledgerDetail,
  formList: files.formList,
  formStudio: files.formStudio,
  todos: files.todos,
  notify: files.notify,
})) {
  forbidIncludes(`desktop ${name}`, text, [
    "wxDtNativeBridgeHonesty",
    "NxMobileShell",
  ]);
}

if (failures.length) {
  console.error("FAIL G15 WeChat/DingTalk prod-gap + native-bridge honesty leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: wx-dt/native-bridge honesty leftover; harness PASS; live host SKIP; #151/#167/#181 not redone; sibling leftover rows kept");
