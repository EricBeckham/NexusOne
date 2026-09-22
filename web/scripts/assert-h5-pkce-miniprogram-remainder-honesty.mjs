#!/usr/bin/env node
/**
 * G15 leftover remainder: H5 PKCE browser / miniprogram web-view
 * honesty PASS/SKIP beyond #198/#220/#120. Not a product G15 close.
 * Not IPA/APK. Does not redo #117/#120/#126/#198/#220.
 * Keeps ALL sibling leftover rows including #230
 * g4-hot-purge-staging-leftover-honesty, #212
 * g7-withdraw-browser-evidence-honesty, #232
 * g2-h5-every-page-form-write-remainder-honesty, #237
 * g9-redis-down-invalidate-remainder-honesty, #239
 * g7-query-expense-travel-honesty-remainder, #238
 * g1-fieldpolicy-mask-second-auth-remainder-honesty, #233
 * g17-mfa-recovery-vue-otp-remainder-honesty, #235
 * g5-publishhook-ddl-remainder-honesty, #241
 * s0-secret-rotate-getenv-leftover-honesty, #243
 * g10-file-object-reconcile-remainder-honesty, #251
 * g4-hot-purge-staging-pkce-remainder-honesty, #236
 * linux-arm-profile-remainder-honesty, and #254
 * g2-h5-every-page-form-write-report-remainder-honesty.
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
  describeH5PkceMiniprogramRemainderContract,
  honestH5PkceMiniprogramRemainderReport,
  verifyH5PkceMiniprogramRemainderReport,
} from "../src/mobile/host/h5PkceMiniprogramRemainderHonesty.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  leftover: read("src/mobile/host/h5PkceMiniprogramRemainderHonesty.js"),
  parent: read("src/mobile/host/wxDtNativeBridgeRemainder.js"),
  honesty: read("src/mobile/host/wxDtNativeBridgeHonesty.js"),
  mp: read("src/mobile/host/miniprogramWebView.js"),
  pkce: read("src/mobile/auth/pkce.js"),
  router: read("src/router.js"),
  doc: read("../docs/g15-h5-pkce-miniprogram-remainder-honesty.md"),
  parentDoc: read("../docs/g15-wx-dt-native-bridge-remainder.md"),
  honestyDoc: read("../docs/g15-wx-dt-native-bridge-honesty.md"),
  pkceDoc: read("../docs/g15-h5-pkce-browser.md"),
  mpDoc: read("../docs/g15-miniprogram-webview.md"),
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
const contract = describeH5PkceMiniprogramRemainderContract(win);
check(contract.leftoverKind === LEFTOVER_KIND, "leftover kind");
check(contract.g15Close === "NOT_CLAIMED", "g15Close");
check(contract.leftover117Harness === "PASS", "#117 harness PASS");
check(contract.leftover126Harness === "PASS", "#126 harness PASS");
check(contract.leftover120Sibling === "PASS", "#120 sibling PASS");
check(contract.leftover198Harness === "PASS", "#198 harness PASS");
check(contract.leftover220Harness === "PASS", "#220 harness PASS");
check(contract.liveH5KeycloakPkce === "SKIP", "live H5 Keycloak SKIP");
check(contract.wechatMiniProgramProduction === "SKIP", "wechat SKIP");
check(contract.dingtalkMiniProgramProduction === "SKIP", "dingtalk SKIP");
check(contract.iosIpaProduction === "SKIP", "ios SKIP");
check(contract.androidApkProduction === "SKIP", "android SKIP");
check(contract.productionAccepted === false, "productionAccepted");
check(contract.playwrightStubIsNotLiveKeycloak === true, "Playwright stub is not live Keycloak");
check(contract.simulatedMpWebviewIsNotProduction === true, "simulated mp is not production");
check(
  contract.redone117 === false
    && contract.redone120 === false
    && contract.redone126 === false
    && contract.redone198 === false
    && contract.redone220 === false,
  "not redone",
);
check(contract.notClaimed["#126"] === NOT_CLAIMED_VS_LEFTOVERS["#126"], "#126 honesty");

const honest = verifyH5PkceMiniprogramRemainderReport(honestH5PkceMiniprogramRemainderReport());
check(honest.ok === true, `honest report must pass: ${honest.errors.join("; ")}`);
const fakeClose = verifyH5PkceMiniprogramRemainderReport({
  ...honestH5PkceMiniprogramRemainderReport(),
  g15_close: "CLOSED",
});
check(fakeClose.ok === false, "fake g15_close=CLOSED must fail");
const fakeLive = verifyH5PkceMiniprogramRemainderReport({
  ...honestH5PkceMiniprogramRemainderReport(),
  live_h5_keycloak_pkce: "PASS",
  h5_pkce_walk_observed: "false",
});
check(fakeLive.ok === false, "fake live H5 PKCE PASS without walk must fail");
const fakeWechat = verifyH5PkceMiniprogramRemainderReport({
  ...honestH5PkceMiniprogramRemainderReport(),
  wechat_miniprogram_production: "PASS",
  wechat_walk_observed: "false",
});
check(fakeWechat.ok === false, "fake wechat PASS without walk must fail");
const fakeIpa = verifyH5PkceMiniprogramRemainderReport({
  ...honestH5PkceMiniprogramRemainderReport(),
  ios_ipa_production: "PASS",
  ios_walk_observed: "false",
});
check(fakeIpa.ok === false, "fake IPA PASS without walk must fail");
const fakeRedo = verifyH5PkceMiniprogramRemainderReport({
  ...honestH5PkceMiniprogramRemainderReport(),
  redone_220: "true",
});
check(fakeRedo.ok === false, "fake #220 redo must fail");

check(files.parent.includes('LEFTOVER_KIND = "wx-dt-native-bridge-remainder"'), "#220 leftover kind stays");
check(files.honesty.includes('LEFTOVER_KIND = "wx-dt-native-bridge-honesty"'), "#198 leftover kind stays");
check(files.mp.includes("productionAccepted: false"), "#117 simulated parent stays honest");
check(files.pkce.includes("never puts it in a URL") || files.pkce.includes("code_verifier"), "#126 PKCE leftover stays");
check(!files.router.includes("h5-pkce-miniprogram-remainder-harness"), "no new harness route");
check(files.leftover.includes("Does not redo"), "leftover states not a redo");
check(files.leftover.includes("Does not close product G15"), "leftover stays honest");
check(files.leftover.includes("AuditPort same-TX is untouched"), "AuditPort untouched");

check(files.doc.includes("does **not** close product G15") || files.doc.includes("does not close product G15"), "doc must not close G15");
check(files.doc.includes("#117") && files.doc.includes("#126") && files.doc.includes("#120"), "doc names #117/#126/#120");
check(files.doc.includes("#198") && files.doc.includes("#220"), "doc extends #198/#220");
check(files.doc.includes("g15-wx-dt-native-bridge-remainder"), "doc keeps #220 leftover row");
check(files.doc.includes("g4-hot-purge-staging-leftover-honesty"), "doc keeps #230 leftover row");
check(files.doc.includes("g7-withdraw-browser-evidence-honesty"), "doc keeps #212 leftover row");
check(files.doc.includes("g2-h5-every-page-form-write-remainder-honesty"), "doc keeps #232 leftover row");
check(files.doc.includes("g9-redis-down-invalidate-remainder-honesty"), "doc keeps #237 leftover row");
check(files.doc.includes("g7-query-expense-travel-honesty-remainder"), "doc keeps #239 leftover row");
check(files.doc.includes("g1-fieldpolicy-mask-second-auth-remainder-honesty"), "doc keeps #238 leftover row");
check(files.doc.includes("g17-mfa-recovery-vue-otp-remainder-honesty"), "doc keeps #233 leftover row");
check(files.doc.includes("g5-publishhook-ddl-remainder-honesty"), "doc keeps #235 leftover row");
check(files.doc.includes("s0-secret-rotate-getenv-leftover-honesty"), "doc keeps #241 leftover row");
check(files.doc.includes("g10-file-object-reconcile-remainder-honesty"), "doc keeps #243 leftover row");
check(files.doc.includes("g4-hot-purge-staging-pkce-remainder-honesty"), "doc keeps #251 leftover row");
check(files.doc.includes("linux-arm-profile-remainder-honesty"), "doc keeps #236 leftover row");
check(files.doc.includes("g2-h5-every-page-form-write-report-remainder-honesty"), "doc keeps #254 leftover row");
check(files.doc.includes("1d499ab"), "doc points at origin/main 1d499ab");
check(files.claim.includes("g4-hot-purge-staging-leftover-honesty.md"), "claim-list keeps #230 leftover");
check(files.claim.includes("g7-withdraw-browser-evidence-honesty.md"), "claim-list keeps #212 leftover");
check(files.claim.includes("g2-h5-every-page-form-write-remainder-honesty.md"), "claim-list keeps #232 leftover");
check(files.claim.includes("g9-redis-down-invalidate-remainder-honesty.md"), "claim-list keeps #237 leftover");
check(files.claim.includes("g7-query-expense-travel-honesty-remainder.md"), "claim-list keeps #239 leftover");
check(files.claim.includes("g1-fieldpolicy-mask-second-auth-remainder-honesty.md"), "claim-list keeps #238 leftover");
check(files.claim.includes("g17-mfa-recovery-vue-otp-remainder-honesty.md"), "claim-list keeps #233 leftover");
check(files.claim.includes("g5-publishhook-ddl-remainder-honesty.md"), "claim-list keeps #235 leftover");
check(files.claim.includes("s0-secret-rotate-getenv-leftover-honesty.md"), "claim-list keeps #241 leftover");
check(files.claim.includes("g10-file-object-reconcile-remainder-honesty.md"), "claim-list keeps #243 leftover");
check(files.claim.includes("g4-hot-purge-staging-pkce-remainder-honesty.md"), "claim-list keeps #251 leftover");
check(files.claim.includes("linux-arm-profile-remainder-honesty.md"), "claim-list keeps #236 leftover");
check(files.claim.includes("g2-h5-every-page-form-write-report-remainder-honesty.md"), "claim-list keeps #254 leftover");
check(files.docsIndex.includes("[g4-hot-purge-staging-leftover-honesty]"), "docs index keeps #230 leftover");
check(files.docsIndex.includes("[g7-withdraw-browser-evidence-honesty]"), "docs index keeps #212 leftover");
check(files.docsIndex.includes("[g2-h5-every-page-form-write-remainder-honesty]"), "docs index keeps #232 leftover");
check(files.docsIndex.includes("[g9-redis-down-invalidate-remainder-honesty]"), "docs index keeps #237 leftover");
check(files.docsIndex.includes("[g7-query-expense-travel-honesty-remainder]"), "docs index keeps #239 leftover");
check(files.docsIndex.includes("[g1-fieldpolicy-mask-second-auth-remainder-honesty]"), "docs index keeps #238 leftover");
check(files.docsIndex.includes("[g17-mfa-recovery-vue-otp-remainder-honesty]"), "docs index keeps #233 leftover");
check(files.docsIndex.includes("[g5-publishhook-ddl-remainder-honesty]"), "docs index keeps #235 leftover");
check(files.docsIndex.includes("[s0-secret-rotate-getenv-leftover-honesty]"), "docs index keeps #241 leftover");
check(files.docsIndex.includes("[g10-file-object-reconcile-remainder-honesty]"), "docs index keeps #243 leftover");
check(files.docsIndex.includes("[g4-hot-purge-staging-pkce-remainder-honesty]"), "docs index keeps #251 leftover");
check(files.docsIndex.includes("[linux-arm-profile-remainder-honesty]"), "docs index keeps #236 leftover");
check(files.docsIndex.includes("[g2-h5-every-page-form-write-report-remainder-honesty]"), "docs index keeps #254 leftover");
check(files.docsIndex.includes("[g15-wx-dt-native-bridge-remainder]"), "docs index keeps #220 leftover");
check(!files.doc.includes("storeReady: true"), "doc must not claim store-ready");
check(files.parentDoc.includes("g15-h5-pkce-miniprogram-remainder-honesty"), "#220 doc points at this leftover");
check(files.honestyDoc.includes("g15-h5-pkce-miniprogram-remainder-honesty"), "#198 doc points at this leftover");
check(files.pkceDoc.includes("g15-h5-pkce-miniprogram-remainder-honesty"), "#126 doc points at this leftover");
check(files.mpDoc.includes("g15-h5-pkce-miniprogram-remainder-honesty"), "#117 doc points at this leftover");
check(files.claim.includes("g15-h5-pkce-miniprogram-remainder-honesty.md"), "claim-list leftover row links this leftover");
check(files.claim.includes("g15-wx-dt-native-bridge-remainder.md"), "claim-list keeps #220 leftover");
check(files.docsIndex.includes("[g15-h5-pkce-miniprogram-remainder-honesty]"), "docs index lists this leftover");
check(files.docsIndex.includes("[g15-h5-pkce-browser]"), "docs index keeps #126 leftover");
check(files.docsIndex.includes("[g15-miniprogram-webview]"), "docs index keeps #117 leftover");

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
    "h5PkceMiniprogramRemainderHonesty",
    "wxDtNativeBridgeRemainder",
    "NxMobileShell",
  ]);
}

if (failures.length) {
  console.error("FAIL G15 H5 PKCE / miniprogram web-view remainder leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: H5 PKCE / miniprogram remainder leftover; harness PASS; live host SKIP; #117/#120/#126/#198/#220 not redone; sibling leftover rows kept");
