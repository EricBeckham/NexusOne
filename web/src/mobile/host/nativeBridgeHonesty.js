/**
 * G15 leftover: APP WebView native-bridge honesty beyond #151.
 * Extends honest PASS/SKIP. Not IPA/APK production accept.
 *
 * Same F014 HostBridgePort envelope (login / route / file / close).
 * Distinct from:
 *   #114 /m/app-webview-harness     — web NexusAppWebView injection
 *   #117 /m/mp-webview-harness      — simulated 小程序 web-view parent
 *   #132 /m/wx-dt-harness           — WeChat/DingTalk production-host stub
 *   #151 /m/native-bridge-harness   — APP WebView native-bridge stub
 *   #167 /m/wx-dt-prod-gap-harness  — WeChat/DingTalk production-gap honesty
 *
 * Does not close product G15. Does not redo #114/#117/#132/#151/#167.
 * Does not redo desktop P3. Does not invent a store-ready IPA/APK.
 */
import {
  HOST_CHANNELS,
  HOST_SOURCE,
  OTHER_HOSTS_OPEN,
  encodeHostMessage,
  isSafeHostPath,
} from "./hostBridge.js";
import {
  NATIVE_BRIDGE_WALK,
  decodeNativeBridgeMessage,
  describeNativeBridgeContract,
  isAllowedNativeBridgeChannel,
  isAllowedNativeBridgeOrigin,
  isAllowedNativeBridgeSrc,
} from "./nativeBridgeStub.js";

export const NATIVE_BRIDGE_HONESTY_PATH = "/m/native-bridge-honesty-harness";

export const NOT_CLAIMED_VS_LEFTOVERS = Object.freeze({
  "#114": "web NexusAppWebView harness — not a native package",
  "#117": "simulated 小程序 web-view parent — not production",
  "#132": "WeChat/DingTalk production-host stub — not production accept",
  "#151": "APP WebView native-bridge stub — not store-ready IPA/APK",
  "#167": "WeChat/DingTalk production-gap honesty — not production accept",
});

export const IOS_SKIP_REASON =
  "no Xcode / WKWebView / TestFlight / App Store IPA walk";
export const ANDROID_SKIP_REASON =
  "no Gradle / Android WebView / Play APK walk";

export const NATIVE_BRIDGE_HONESTY_ITEMS = Object.freeze([
  "ios_ipa_store_package",
  "android_apk_store_package",
  "wkwebview_product_host",
  "android_webview_product_host",
  "xcode_project",
  "gradle_project",
]);

export const BOTH_LEFTOVER_ROWS = Object.freeze([
  "g15-native-bridge-stub",
  "g15-wx-dt-prod-gap-honesty",
]);

export function isNativeBridgeHonestyPath(path) {
  const clean = String(path || "").trim().split("?")[0];
  return clean === NATIVE_BRIDGE_HONESTY_PATH
    || clean.startsWith(`${NATIVE_BRIDGE_HONESTY_PATH}/`);
}

export function describeNativeBridgeHonestyContract(win, platform = "ios") {
  const base = describeNativeBridgeContract(win, platform);
  return {
    host: base.host,
    leftoverKind: "native-bridge-honesty",
    stub: true,
    storeReady: false,
    storePackage: false,
    nativeBinary: false,
    productionAccepted: false,
    iosProject: false,
    androidProject: false,
    wkWebViewWired: false,
    androidWebViewWired: false,
    g15Close: "NOT_CLAIMED",
    stubContract: "PASS",
    iosIpaProduction: "SKIP",
    androidApkProduction: "SKIP",
    storeReadyStatus: "SKIP",
    iosWalkObserved: false,
    androidWalkObserved: false,
    iosSkipReason: IOS_SKIP_REASON,
    androidSkipReason: ANDROID_SKIP_REASON,
    transport: base.transport,
    envelope: "F014 HostBridgePort (unchanged)",
    channels: [...HOST_CHANNELS],
    originAllowlist: [...base.originAllowlist],
    platform: base.platform,
    platforms: base.platforms,
    handlerKind: base.handlerKind,
    notClaimed: { ...NOT_CLAIMED_VS_LEFTOVERS },
    leftover: [...OTHER_HOSTS_OPEN],
    gapItems: [...NATIVE_BRIDGE_HONESTY_ITEMS],
    leftoverRowsKept: [...BOTH_LEFTOVER_ROWS],
  };
}

export function honestNativeBridgeReport() {
  return {
    schema_version: "1",
    g15_close: "NOT_CLAIMED",
    stub_contract: "PASS",
    ios_ipa_production: "SKIP",
    android_apk_production: "SKIP",
    store_ready: "SKIP",
    store_package: "false",
    native_binary: "false",
    production_accepted: "false",
    ios_walk_observed: "false",
    android_walk_observed: "false",
    ios_skip_reason: IOS_SKIP_REASON,
    android_skip_reason: ANDROID_SKIP_REASON,
    docker_used: "false",
    g18_status: "closed_unchanged",
    desktop_p3_changed: "false",
    f014_rewritten: "false",
    pr_114_web: "leftover",
    pr_117_sim: "leftover",
    pr_132_stub: "leftover",
    pr_151_native: "leftover",
    pr_167_wx_dt_gap: "leftover",
    g15_native_bridge_stub_row: "kept",
    g15_wx_dt_prod_gap_honesty_row: "kept",
    g7_withdraw_browser_row: "kept",
    g7_query_expense_travel_row: "kept",
  };
}

export function verifyNativeBridgeHonestyReport(report = {}) {
  const errors = [];
  const close = String(report.g15_close || "");
  if (close.toUpperCase() in { CLOSED: 1, PASS: 1, TRUE: 1 } || close !== "NOT_CLAIMED") {
    errors.push("g15_close must stay NOT_CLAIMED");
  }
  if (String(report.production_accepted).toLowerCase() in { true: 1, yes: 1, "1": 1 }) {
    errors.push("production_accepted must stay false");
  }
  if (String(report.store_package).toLowerCase() in { true: 1, yes: 1, "1": 1 }) {
    errors.push("store_package must stay false");
  }
  if (String(report.native_binary).toLowerCase() in { true: 1, yes: 1, "1": 1 }) {
    errors.push("native_binary must stay false");
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
  for (const leftover of [
    "pr_114_web",
    "pr_117_sim",
    "pr_132_stub",
    "pr_151_native",
    "pr_167_wx_dt_gap",
  ]) {
    if (report[leftover] !== "leftover") {
      errors.push(`${leftover} must stay leftover`);
    }
  }
  if (report.g15_native_bridge_stub_row !== "kept") {
    errors.push("g15_native_bridge_stub_row must stay kept");
  }
  if (report.g15_wx_dt_prod_gap_honesty_row !== "kept") {
    errors.push("g15_wx_dt_prod_gap_honesty_row must stay kept");
  }
  if (report.g7_withdraw_browser_row !== "kept") {
    errors.push("g7_withdraw_browser_row must stay kept");
  }
  if (report.g7_query_expense_travel_row !== "kept") {
    errors.push("g7_query_expense_travel_row must stay kept");
  }

  const stub = report.stub_contract;
  const ios = report.ios_ipa_production;
  const android = report.android_apk_production;
  const store = report.store_ready;
  if (!["PASS", "SKIP", "FAIL"].includes(stub)) {
    errors.push("stub_contract must be PASS/SKIP/FAIL");
  }
  if (!["PASS", "SKIP", "FAIL"].includes(ios)) {
    errors.push("ios_ipa_production must be PASS/SKIP/FAIL");
  }
  if (!["PASS", "SKIP", "FAIL"].includes(android)) {
    errors.push("android_apk_production must be PASS/SKIP/FAIL");
  }
  if (!["PASS", "SKIP", "FAIL"].includes(store)) {
    errors.push("store_ready must be PASS/SKIP/FAIL");
  }
  if (ios === "PASS" && String(report.ios_walk_observed).toLowerCase() !== "true") {
    errors.push("ios_ipa_production=PASS requires ios_walk_observed=true");
  }
  if (android === "PASS" && String(report.android_walk_observed).toLowerCase() !== "true") {
    errors.push("android_apk_production=PASS requires android_walk_observed=true");
  }
  if (store === "PASS") {
    if (String(report.ios_walk_observed).toLowerCase() !== "true"
      || String(report.android_walk_observed).toLowerCase() !== "true") {
      errors.push("store_ready=PASS requires ios_walk_observed and android_walk_observed");
    }
  }
  if (ios === "SKIP" && !String(report.ios_skip_reason || "").trim()) {
    errors.push("ios SKIP needs ios_skip_reason");
  }
  if (android === "SKIP" && !String(report.android_skip_reason || "").trim()) {
    errors.push("android SKIP needs android_skip_reason");
  }
  return { ok: errors.length === 0, errors };
}

export function probeNativeOriginsDenied(win) {
  const origins = [
    "https://evil.example",
    "https://servicewechat.com",
    "https://h5.dingtalk.com",
  ];
  return origins.map((origin) => {
    const decoded = decodeNativeBridgeMessage({
      origin,
      data: { source: "nexus-h5", channel: "close", payload: { action: "close" } },
    }, win);
    return {
      origin,
      ok: decoded.ok === false && decoded.reason === "origin_rejected",
      reason: decoded.reason,
    };
  });
}

export function probeNativeEnvelopeDenied(win) {
  const token = encodeHostMessage({
    source: HOST_SOURCE,
    channel: "login",
    payload: { action: "done", access_token: "leak" },
  });
  const evalCh = encodeHostMessage({
    source: HOST_SOURCE,
    channel: "eval",
    payload: {},
  });
  return {
    tokenRejected: token.reason === "secret_key",
    evalRejected: evalCh.reason === "channel_not_allowed" || !isAllowedNativeBridgeChannel("eval"),
    tokenReason: token.reason,
    evalReason: evalCh.reason || "channel_not_allowed",
    harnessSrcDenied: isAllowedNativeBridgeSrc(NATIVE_BRIDGE_HONESTY_PATH, win) === false
      && isSafeHostPath(NATIVE_BRIDGE_HONESTY_PATH) === false,
    stubHarnessSrcDenied: isAllowedNativeBridgeSrc("/m/native-bridge-harness", win) === false,
    appWebViewSrcDenied: isAllowedNativeBridgeSrc("/m/app-webview-harness", win) === false,
    wxDtSrcDenied: isAllowedNativeBridgeSrc("/m/wx-dt-harness", win) === false,
    mpSrcDenied: isAllowedNativeBridgeSrc("/m/mp-webview-harness", win) === false,
    prodGapSrcDenied: isAllowedNativeBridgeSrc("/m/wx-dt-prod-gap-harness", win) === false,
    sameOriginAllowed: isAllowedNativeBridgeOrigin(win?.location?.origin, win) === true,
    walkChannels: NATIVE_BRIDGE_WALK.map(([channel]) => channel).join(","),
  };
}
