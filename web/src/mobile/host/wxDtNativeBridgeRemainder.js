/**
 * G15 leftover remainder: WeChat / DingTalk prod-gap + native-bridge
 * honesty PASS/SKIP beyond #167/#181/#198.
 *
 * Reuses the #167 / #181 / #198 leftover contracts. Does not redo
 * #117/#132/#151/#167/#181/#198 product code. Does not add a harness
 * route. Does not close product G15. Not IPA/APK. G18 stays closed.
 * AuditPort same-TX is untouched.
 */
import {
  ALL_SIBLING_LEFTOVER_ROWS as PARENT_ALL_SIBLING_LEFTOVER_ROWS,
  SIBLING_LEFTOVER_ROWS as PARENT_SIBLING_LEFTOVER_ROWS,
  describeWxDtNativeBridgeHonestyContract,
  honestWxDtNativeBridgeReport,
  liveVerdict,
  probeLiveHosts,
  verifyWxDtNativeBridgeHonestyReport,
} from "./wxDtNativeBridgeHonesty.js";

export { liveVerdict, probeLiveHosts };

export const LEFTOVER_KIND = "wx-dt-native-bridge-remainder";

export const NOT_CLAIMED_VS_LEFTOVERS = Object.freeze({
  "#117": "simulated 小程序 web-view parent — not WeChat / DingTalk production",
  "#132": "WeChat/DingTalk production-host stub — not production accept",
  "#151": "APP WebView native-bridge stub — not store-ready IPA/APK",
  "#167": "WeChat/DingTalk production-gap honesty — not production accept",
  "#181": "native-bridge honesty leftover — not IPA/APK production accept",
  "#198": "wx-dt/native-bridge honesty leftover — harness PASS / live host SKIP",
});

export const SIBLING_LEFTOVER_ROWS = Object.freeze([
  ...PARENT_SIBLING_LEFTOVER_ROWS,
  "g15-wx-dt-native-bridge-honesty",
]);

export const ALL_SIBLING_LEFTOVER_ROWS = Object.freeze([
  ...new Set([
    ...SIBLING_LEFTOVER_ROWS,
    ...PARENT_ALL_SIBLING_LEFTOVER_ROWS,
    "g2-ops-pg-dump-honesty",
    "g9-sentinel-cluster-remainder-honesty",
    "g5-publishhook-ddl-honesty",
    "g17-gm-tls-nginx-handshake-honesty",
    "g1-queryast-export-honesty",
    "g17-mfa-recovery-vue-otp-honesty",
    "g9-redis-down-invalidate-remainder",
    "g2-h5-every-page-form-write-honesty",
    "g4-export-wizard-deny-honesty",
    "g10-leftover-honesty",
    "g5-mid-publish-failure-honesty",
    "g3-outbox-consume-crash-honesty",
    "g1-queryast-boundsql-honesty",
  ]),
]);

export function describeWxDtNativeBridgeRemainderContract(win, env, tools) {
  const parent = describeWxDtNativeBridgeHonestyContract(win, env, tools);
  const leftover198 = verifyWxDtNativeBridgeHonestyReport(honestWxDtNativeBridgeReport());
  return {
    leftoverKind: LEFTOVER_KIND,
    stub: true,
    productionAccepted: parent.productionAccepted,
    storeReady: parent.storeReady,
    storePackage: parent.storePackage,
    nativeBinary: parent.nativeBinary,
    vendorJsapiLoaded: parent.vendorJsapiLoaded,
    vendorDomainAllowed: parent.vendorDomainAllowed,
    g15Close: "NOT_CLAIMED",
    leftover167Harness: parent.wxDtHarnessContract,
    leftover181Harness: parent.nativeBridgeHarnessContract,
    leftover198Harness: leftover198.ok ? "PASS" : "FAIL",
    wxDtHarnessContract: parent.wxDtHarnessContract,
    nativeBridgeHarnessContract: parent.nativeBridgeHarnessContract,
    wechatMiniProgramProduction: parent.wechatMiniProgramProduction,
    dingtalkMiniProgramProduction: parent.dingtalkMiniProgramProduction,
    iosIpaProduction: parent.iosIpaProduction,
    androidApkProduction: parent.androidApkProduction,
    storeReadyStatus: parent.storeReadyStatus,
    wechatWalkObserved: parent.wechatWalkObserved,
    dingtalkWalkObserved: parent.dingtalkWalkObserved,
    iosWalkObserved: parent.iosWalkObserved,
    androidWalkObserved: parent.androidWalkObserved,
    wechatLiveHost: parent.wechatLiveHost,
    dingtalkLiveHost: parent.dingtalkLiveHost,
    iosLiveHost: parent.iosLiveHost,
    androidLiveHost: parent.androidLiveHost,
    wechatSkipReason: parent.wechatSkipReason,
    dingtalkSkipReason: parent.dingtalkSkipReason,
    iosSkipReason: parent.iosSkipReason,
    androidSkipReason: parent.androidSkipReason,
    envelope: parent.envelope,
    notClaimed: { ...NOT_CLAIMED_VS_LEFTOVERS },
    leftoverRowsKept: [...SIBLING_LEFTOVER_ROWS],
    siblingRowsKept: [...ALL_SIBLING_LEFTOVER_ROWS],
    redone117: false,
    redone132: false,
    redone151: false,
    redone167: false,
    redone181: false,
    redone198: false,
    parentLeftoverKind: parent.leftoverKind,
    gapLeftoverKind: parent.gapLeftoverKind,
    nativeLeftoverKind: parent.nativeLeftoverKind,
  };
}

export function honestWxDtNativeBridgeRemainderReport() {
  return {
    ...honestWxDtNativeBridgeReport(),
    leftover_167_harness: "PASS",
    leftover_181_harness: "PASS",
    leftover_198_harness: "PASS",
    leftover_rows_kept: SIBLING_LEFTOVER_ROWS.join(","),
    redone_117: "false",
    redone_132: "false",
    redone_198: "false",
  };
}

function truthy(value) {
  return String(value || "").toLowerCase() in { true: 1, yes: 1, "1": 1 };
}

export function verifyWxDtNativeBridgeRemainderReport(report = {}) {
  const errors = [];
  const parent = verifyWxDtNativeBridgeHonestyReport(report);
  if (!parent.ok) {
    errors.push(...parent.errors);
  }
  const parentHonest = verifyWxDtNativeBridgeHonestyReport(honestWxDtNativeBridgeReport());
  if (!parentHonest.ok) {
    errors.push(`#198 harness honesty must still PASS: ${parentHonest.errors.join("; ")}`);
  }
  if (report.leftover_198_harness !== "PASS") {
    errors.push("leftover_198_harness must be PASS");
  }
  if (report.leftover_167_harness && report.leftover_167_harness !== "PASS") {
    errors.push("leftover_167_harness must be PASS");
  }
  if (report.leftover_181_harness && report.leftover_181_harness !== "PASS") {
    errors.push("leftover_181_harness must be PASS");
  }
  if (String(report.redone_117).toLowerCase() !== "false") {
    errors.push("redone_117 must stay false");
  }
  if (String(report.redone_132).toLowerCase() !== "false") {
    errors.push("redone_132 must stay false");
  }
  if (String(report.redone_198).toLowerCase() !== "false") {
    errors.push("redone_198 must stay false");
  }
  const rows = String(report.leftover_rows_kept || "");
  for (const row of SIBLING_LEFTOVER_ROWS) {
    if (!rows.includes(row)) {
      errors.push(`leftover_rows_kept must keep ${row}`);
    }
  }
  if (truthy(report.audit_port_changed)) {
    errors.push("audit_port_changed must stay false");
  }
  return { ok: errors.length === 0, errors };
}
