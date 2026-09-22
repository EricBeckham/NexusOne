/**
 * G15 leftover: WeChat / DingTalk prod-gap + native-bridge honesty
 * PASS/SKIP beyond #167/#181.
 *
 * Reuses the #167 / #181 leftover contracts. Does not redo
 * #151/#167/#181 product code. Does not add a harness route.
 * Does not close product G15. Not IPA/APK. G18 stays closed.
 */
import {
  DINGTALK_SKIP_REASON,
  WECHAT_SKIP_REASON,
  describeProdGapContract,
  honestProdGapReport,
  verifyProdGapReport,
} from "./wechatDingTalkProdGapHonesty.js";
import {
  ANDROID_SKIP_REASON,
  IOS_SKIP_REASON,
  describeNativeBridgeHonestyContract,
  honestNativeBridgeReport,
  verifyNativeBridgeHonestyReport,
} from "./nativeBridgeHonesty.js";

export const LEFTOVER_KIND = "wx-dt-native-bridge-honesty";

export const NOT_CLAIMED_VS_LEFTOVERS = Object.freeze({
  "#151": "APP WebView native-bridge stub — not store-ready IPA/APK",
  "#167": "WeChat/DingTalk production-gap honesty — not production accept",
  "#181": "native-bridge honesty leftover — not IPA/APK production accept",
});

export const SIBLING_LEFTOVER_ROWS = Object.freeze([
  "g15-wx-dt-prod-gap-honesty",
  "g15-native-bridge-honesty",
  "g15-native-bridge-stub",
  "g15-wx-dt-prod-host",
]);

export const ALL_SIBLING_LEFTOVER_ROWS = Object.freeze([
  ...SIBLING_LEFTOVER_ROWS,
  "g4-p1-staging-audit-events-honesty",
  "g6-recovery-port-dry-run-honesty",
  "s0s1-live-redis-minio-isolation-honesty",
  "g17-separation-honesty",
  "g4-hot-purge-90d-honesty",
  "g1-fieldpolicy-mask-second-auth-honesty",
  "g10-identity-drift-honesty",
  "g4-pkce-spa-verifier-honesty",
  "s0-secret-getenv-snapshot-honesty",
  "g5-browser-failed-publish",
  "g7-withdraw-browser-honesty",
  "g9-redis-down-invalidate-honesty",
  "g10-file-reconcile-honesty",
]);

function envOf(env) {
  return env && typeof env === "object" ? env : {};
}

export function probeLiveHosts(env, tools = {}) {
  const e = envOf(env);
  return {
    wechatLiveHost: Boolean(e.WECHAT_PROD_HOST || e.WECHAT_JSAPI_URL || tools.wechatDevtools),
    dingtalkLiveHost: Boolean(e.DINGTALK_PROD_HOST || e.DINGTALK_JSAPI_URL || tools.dingtalkDevtools),
    iosLiveHost: Boolean(e.XCODE_PROJECT || tools.xcodebuild),
    androidLiveHost: Boolean(e.ANDROID_HOME || e.ANDROID_SDK_ROOT || tools.gradle),
    wechatWalkObserved: e.WECHAT_WALK_OBSERVED === "true",
    dingtalkWalkObserved: e.DINGTALK_WALK_OBSERVED === "true",
    iosWalkObserved: e.IOS_WALK_OBSERVED === "true",
    androidWalkObserved: e.ANDROID_WALK_OBSERVED === "true",
  };
}

export function liveVerdict(livePresent, walkObserved) {
  if (walkObserved && livePresent) {
    return "PASS";
  }
  return "SKIP";
}

export function describeWxDtNativeBridgeHonestyContract(win, env, tools) {
  const gap = describeProdGapContract(win, "wechat");
  const native = describeNativeBridgeHonestyContract(win, "ios");
  const live = probeLiveHosts(env, tools);
  return {
    leftoverKind: LEFTOVER_KIND,
    stub: true,
    productionAccepted: false,
    storeReady: false,
    storePackage: false,
    nativeBinary: false,
    vendorJsapiLoaded: false,
    vendorDomainAllowed: false,
    g15Close: "NOT_CLAIMED",
    wxDtHarnessContract: verifyProdGapReport(honestProdGapReport()).ok ? "PASS" : "FAIL",
    nativeBridgeHarnessContract: native.stubContract,
    wechatMiniProgramProduction: liveVerdict(live.wechatLiveHost, live.wechatWalkObserved),
    dingtalkMiniProgramProduction: liveVerdict(live.dingtalkLiveHost, live.dingtalkWalkObserved),
    iosIpaProduction: liveVerdict(live.iosLiveHost, live.iosWalkObserved),
    androidApkProduction: liveVerdict(live.androidLiveHost, live.androidWalkObserved),
    storeReadyStatus: "SKIP",
    wechatWalkObserved: live.wechatWalkObserved,
    dingtalkWalkObserved: live.dingtalkWalkObserved,
    iosWalkObserved: live.iosWalkObserved,
    androidWalkObserved: live.androidWalkObserved,
    wechatLiveHost: live.wechatLiveHost,
    dingtalkLiveHost: live.dingtalkLiveHost,
    iosLiveHost: live.iosLiveHost,
    androidLiveHost: live.androidLiveHost,
    wechatSkipReason: WECHAT_SKIP_REASON,
    dingtalkSkipReason: DINGTALK_SKIP_REASON,
    iosSkipReason: IOS_SKIP_REASON,
    androidSkipReason: ANDROID_SKIP_REASON,
    envelope: "F014 HostBridgePort (unchanged)",
    notClaimed: { ...NOT_CLAIMED_VS_LEFTOVERS },
    leftoverRowsKept: [...SIBLING_LEFTOVER_ROWS],
    siblingRowsKept: [...ALL_SIBLING_LEFTOVER_ROWS],
    redone151: false,
    redone167: false,
    redone181: false,
    gapLeftoverKind: gap.leftoverKind,
    nativeLeftoverKind: native.leftoverKind,
  };
}

export function honestWxDtNativeBridgeReport() {
  return {
    schema_version: "1",
    g15_close: "NOT_CLAIMED",
    wx_dt_harness_contract: "PASS",
    native_bridge_harness_contract: "PASS",
    wechat_miniprogram_production: "SKIP",
    dingtalk_miniprogram_production: "SKIP",
    ios_ipa_production: "SKIP",
    android_apk_production: "SKIP",
    store_ready: "SKIP",
    production_accepted: "false",
    store_package: "false",
    native_binary: "false",
    vendor_jsapi_loaded: "false",
    vendor_domain_allowed: "false",
    wechat_live_host_present: "false",
    dingtalk_live_host_present: "false",
    ios_live_host_present: "false",
    android_live_host_present: "false",
    wechat_walk_observed: "false",
    dingtalk_walk_observed: "false",
    ios_walk_observed: "false",
    android_walk_observed: "false",
    wechat_skip_reason: WECHAT_SKIP_REASON,
    dingtalk_skip_reason: DINGTALK_SKIP_REASON,
    ios_skip_reason: IOS_SKIP_REASON,
    android_skip_reason: ANDROID_SKIP_REASON,
    leftover_rows_kept: SIBLING_LEFTOVER_ROWS.join(","),
    docker_used: "false",
    g18_status: "closed_unchanged",
    desktop_p3_changed: "false",
    f014_rewritten: "false",
    audit_port_changed: "false",
    p0_m0_redone: "false",
    redone_151: "false",
    redone_167: "false",
    redone_181: "false",
    ipa_apk_claimed: "false",
  };
}

function truthy(value) {
  return String(value || "").toLowerCase() in { true: 1, yes: 1, "1": 1 };
}

function requireVerdict(report, key, errors) {
  const value = report[key];
  if (!["PASS", "SKIP", "FAIL"].includes(value)) {
    errors.push(`${key} must be PASS/SKIP/FAIL`);
  }
  return value;
}

export function verifyWxDtNativeBridgeHonestyReport(report = {}) {
  const errors = [];
  const close = String(report.g15_close || "");
  if (close.toUpperCase() in { CLOSED: 1, PASS: 1, TRUE: 1 } || close !== "NOT_CLAIMED") {
    errors.push("g15_close must stay NOT_CLAIMED");
  }
  if (truthy(report.production_accepted)) {
    errors.push("production_accepted must stay false");
  }
  if (truthy(report.store_package)) {
    errors.push("store_package must stay false");
  }
  if (truthy(report.native_binary)) {
    errors.push("native_binary must stay false");
  }
  if (truthy(report.vendor_jsapi_loaded)) {
    errors.push("vendor_jsapi_loaded must stay false");
  }
  if (truthy(report.vendor_domain_allowed)) {
    errors.push("vendor_domain_allowed must stay false");
  }
  if (String(report.docker_used).toLowerCase() !== "false") {
    errors.push("docker_used must be false");
  }
  if (report.g18_status !== "closed_unchanged") {
    errors.push("g18_status must stay closed_unchanged");
  }
  if (String(report.desktop_p3_changed).toLowerCase() !== "false") {
    errors.push("desktop_p3_changed must be false");
  }
  if (String(report.f014_rewritten).toLowerCase() !== "false") {
    errors.push("f014_rewritten must be false");
  }
  if (String(report.audit_port_changed).toLowerCase() !== "false") {
    errors.push("audit_port_changed must be false");
  }
  if (String(report.p0_m0_redone).toLowerCase() !== "false") {
    errors.push("p0_m0_redone must stay false");
  }
  if (String(report.redone_151).toLowerCase() !== "false") {
    errors.push("redone_151 must stay false");
  }
  if (String(report.redone_167).toLowerCase() !== "false") {
    errors.push("redone_167 must stay false");
  }
  if (String(report.redone_181).toLowerCase() !== "false") {
    errors.push("redone_181 must stay false");
  }
  if (truthy(report.ipa_apk_claimed)) {
    errors.push("ipa_apk_claimed must stay false");
  }

  const rows = String(report.leftover_rows_kept || "");
  for (const row of SIBLING_LEFTOVER_ROWS) {
    if (!rows.includes(row)) {
      errors.push(`leftover_rows_kept must keep ${row}`);
    }
  }

  if (report.wx_dt_harness_contract !== "PASS") {
    errors.push("wx_dt_harness_contract must be PASS");
  }
  if (report.native_bridge_harness_contract !== "PASS") {
    errors.push("native_bridge_harness_contract must be PASS");
  }

  const wechat = requireVerdict(report, "wechat_miniprogram_production", errors);
  const dingtalk = requireVerdict(report, "dingtalk_miniprogram_production", errors);
  const ios = requireVerdict(report, "ios_ipa_production", errors);
  const android = requireVerdict(report, "android_apk_production", errors);
  const store = requireVerdict(report, "store_ready", errors);

  if (wechat === "PASS") {
    if (String(report.wechat_walk_observed).toLowerCase() !== "true") {
      errors.push("wechat_miniprogram_production=PASS requires wechat_walk_observed=true");
    }
    if (String(report.wechat_live_host_present).toLowerCase() !== "true") {
      errors.push("wechat_miniprogram_production=PASS requires wechat_live_host_present=true");
    }
  }
  if (dingtalk === "PASS") {
    if (String(report.dingtalk_walk_observed).toLowerCase() !== "true") {
      errors.push("dingtalk_miniprogram_production=PASS requires dingtalk_walk_observed=true");
    }
    if (String(report.dingtalk_live_host_present).toLowerCase() !== "true") {
      errors.push("dingtalk_miniprogram_production=PASS requires dingtalk_live_host_present=true");
    }
  }
  if (ios === "PASS") {
    if (String(report.ios_walk_observed).toLowerCase() !== "true") {
      errors.push("ios_ipa_production=PASS requires ios_walk_observed=true");
    }
    if (String(report.ios_live_host_present).toLowerCase() !== "true") {
      errors.push("ios_ipa_production=PASS requires ios_live_host_present=true");
    }
  }
  if (android === "PASS") {
    if (String(report.android_walk_observed).toLowerCase() !== "true") {
      errors.push("android_apk_production=PASS requires android_walk_observed=true");
    }
    if (String(report.android_live_host_present).toLowerCase() !== "true") {
      errors.push("android_apk_production=PASS requires android_live_host_present=true");
    }
  }
  if (store === "PASS") {
    errors.push("store_ready=PASS is not allowed without IPA/APK product walks");
  }
  if (wechat === "SKIP" && !String(report.wechat_skip_reason || "").trim()) {
    errors.push("wechat SKIP needs wechat_skip_reason");
  }
  if (dingtalk === "SKIP" && !String(report.dingtalk_skip_reason || "").trim()) {
    errors.push("dingtalk SKIP needs dingtalk_skip_reason");
  }
  if (ios === "SKIP" && !String(report.ios_skip_reason || "").trim()) {
    errors.push("ios SKIP needs ios_skip_reason");
  }
  if (android === "SKIP" && !String(report.android_skip_reason || "").trim()) {
    errors.push("android SKIP needs android_skip_reason");
  }
  if (wechat === "SKIP" && String(report.wechat_walk_observed).toLowerCase() === "true") {
    errors.push("SKIP cannot claim a WeChat walk");
  }
  if (dingtalk === "SKIP" && String(report.dingtalk_walk_observed).toLowerCase() === "true") {
    errors.push("SKIP cannot claim a DingTalk walk");
  }
  if (ios === "SKIP" && String(report.ios_walk_observed).toLowerCase() === "true") {
    errors.push("SKIP cannot claim an iOS walk");
  }
  if (android === "SKIP" && String(report.android_walk_observed).toLowerCase() === "true") {
    errors.push("SKIP cannot claim an Android walk");
  }

  const parentGap = verifyProdGapReport(honestProdGapReport());
  if (!parentGap.ok) {
    errors.push(`#167 harness honesty must still PASS: ${parentGap.errors.join("; ")}`);
  }
  const parentNative = verifyNativeBridgeHonestyReport(honestNativeBridgeReport());
  if (!parentNative.ok) {
    errors.push(`#181 harness honesty must still PASS: ${parentNative.errors.join("; ")}`);
  }

  return { ok: errors.length === 0, errors };
}
