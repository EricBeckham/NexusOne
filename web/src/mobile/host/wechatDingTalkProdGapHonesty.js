/**
 * G15 leftover: WeChat / DingTalk production mini-program acceptance
 * gap honesty. Contract stub that inventories what is still NOT
 * production验收.
 *
 * Extends the #132 production-host stub. Same F014 HostBridgePort
 * envelope (login / route / file / close). Distinct from:
 *   #117 /m/mp-webview-harness     — simulated 小程序 web-view parent
 *   #132 /m/wx-dt-harness          — WeChat/DingTalk production-host stub
 *   #151 /m/native-bridge-harness  — APP WebView native-bridge stub
 *
 * Does not load vendor JSAPI. Vendor origins stay denied.
 * Does not close product G15. Does not redo desktop P3.
 */
import {
  HOST_CHANNELS,
  HOST_SOURCE,
  OTHER_HOSTS_OPEN,
  encodeHostMessage,
  isSafeHostPath,
} from "./hostBridge.js";
import {
  VENDOR_PROD_ORIGINS,
  decodeVendorHostMessage,
  describeVendorProdContract,
  isAllowedVendorHostChannel,
  isAllowedVendorHostOrigin,
  isAllowedVendorHostSrc,
} from "./wechatDingTalkHost.js";

export const PROD_GAP_HARNESS_PATH = "/m/wx-dt-prod-gap-harness";

export const NOT_CLAIMED_VS_LEFTOVERS = Object.freeze({
  "#117": "simulated 小程序 web-view parent — not production",
  "#132": "WeChat/DingTalk production-host stub — not production accept",
  "#151": "APP WebView native-bridge stub — not store-ready; not WeChat/DingTalk production",
});

export const WECHAT_SKIP_REASON =
  "no WeChat mini-program package / JSAPI / servicewechat.com walk";
export const DINGTALK_SKIP_REASON =
  "no DingTalk mini-program package / JSAPI / h5.dingtalk.com walk";

export const PROD_GAP_ITEMS = Object.freeze([
  "wechat_miniprogram_package",
  "dingtalk_miniprogram_package",
  "vendor_jsapi",
  "vendor_domain_allow",
  "wechat_production_walk",
  "dingtalk_production_walk",
]);

export function isProdGapHarnessPath(path) {
  const clean = String(path || "").trim().split("?")[0];
  return clean === PROD_GAP_HARNESS_PATH || clean.startsWith(`${PROD_GAP_HARNESS_PATH}/`);
}

export function describeProdGapContract(win, vendor = "wechat") {
  const base = describeVendorProdContract(win, vendor);
  return {
    host: base.host,
    vendor: base.vendor,
    leftoverKind: "wx-dt-prod-gap-honesty",
    stub: true,
    productionAccepted: false,
    g15Close: "NOT_CLAIMED",
    wechatMiniProgramProduction: "SKIP",
    dingtalkMiniProgramProduction: "SKIP",
    vendorJsapiLoaded: false,
    vendorDomainAllowed: false,
    wechatWalkObserved: false,
    dingtalkWalkObserved: false,
    wechatSkipReason: WECHAT_SKIP_REASON,
    dingtalkSkipReason: DINGTALK_SKIP_REASON,
    transport: base.transport,
    envelope: "F014 HostBridgePort (unchanged)",
    channels: [...HOST_CHANNELS],
    originAllowlist: [...base.originAllowlist],
    vendors: base.vendors,
    notClaimed: { ...NOT_CLAIMED_VS_LEFTOVERS },
    leftover: [...OTHER_HOSTS_OPEN],
    gapItems: [...PROD_GAP_ITEMS],
  };
}

export function honestProdGapReport() {
  return {
    schema_version: "1",
    g15_close: "NOT_CLAIMED",
    wechat_miniprogram_production: "SKIP",
    dingtalk_miniprogram_production: "SKIP",
    production_accepted: "false",
    vendor_jsapi_loaded: "false",
    vendor_domain_allowed: "false",
    wechat_walk_observed: "false",
    dingtalk_walk_observed: "false",
    wechat_skip_reason: WECHAT_SKIP_REASON,
    dingtalk_skip_reason: DINGTALK_SKIP_REASON,
    docker_used: "false",
    g18_status: "closed_unchanged",
    desktop_p3_changed: "false",
    f014_rewritten: "false",
    pr_117_sim: "leftover",
    pr_132_stub: "leftover",
    pr_151_native: "leftover",
    g1_mask_leftover_row: "kept",
    g17_vue_otp_leftover_row: "kept",
    g17_gmtls_handshake_leftover_row: "kept",
  };
}

export function verifyProdGapReport(report = {}) {
  const errors = [];
  const close = String(report.g15_close || "");
  if (close.toUpperCase() in { CLOSED: 1, PASS: 1, TRUE: 1 } || close !== "NOT_CLAIMED") {
    errors.push("g15_close must stay NOT_CLAIMED");
  }
  if (String(report.production_accepted).toLowerCase() in { true: 1, yes: 1, "1": 1 }) {
    errors.push("production_accepted must stay false");
  }
  if (String(report.vendor_jsapi_loaded).toLowerCase() in { true: 1, yes: 1, "1": 1 }) {
    errors.push("vendor_jsapi_loaded must stay false");
  }
  if (String(report.vendor_domain_allowed).toLowerCase() in { true: 1, yes: 1, "1": 1 }) {
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
  for (const leftover of ["pr_117_sim", "pr_132_stub", "pr_151_native"]) {
    if (report[leftover] !== "leftover") {
      errors.push(`${leftover} must stay leftover`);
    }
  }
  if (report.g1_mask_leftover_row !== "kept") {
    errors.push("g1_mask_leftover_row must stay kept");
  }
  if (report.g17_vue_otp_leftover_row !== "kept") {
    errors.push("g17_vue_otp_leftover_row must stay kept");
  }
  if (report.g17_gmtls_handshake_leftover_row !== "kept") {
    errors.push("g17_gmtls_handshake_leftover_row must stay kept");
  }

  const wechat = report.wechat_miniprogram_production;
  const dingtalk = report.dingtalk_miniprogram_production;
  if (!["PASS", "SKIP", "FAIL"].includes(wechat)) {
    errors.push("wechat_miniprogram_production must be PASS/SKIP/FAIL");
  }
  if (!["PASS", "SKIP", "FAIL"].includes(dingtalk)) {
    errors.push("dingtalk_miniprogram_production must be PASS/SKIP/FAIL");
  }
  if (wechat === "PASS") {
    if (String(report.wechat_walk_observed).toLowerCase() !== "true") {
      errors.push("wechat_miniprogram_production=PASS requires wechat_walk_observed=true");
    }
  }
  if (dingtalk === "PASS") {
    if (String(report.dingtalk_walk_observed).toLowerCase() !== "true") {
      errors.push("dingtalk_miniprogram_production=PASS requires dingtalk_walk_observed=true");
    }
  }
  if (wechat === "SKIP" && !String(report.wechat_skip_reason || "").trim()) {
    errors.push("wechat SKIP needs wechat_skip_reason");
  }
  if (dingtalk === "SKIP" && !String(report.dingtalk_skip_reason || "").trim()) {
    errors.push("dingtalk SKIP needs dingtalk_skip_reason");
  }
  return { ok: errors.length === 0, errors };
}

export function probeVendorOriginsDenied(win) {
  return VENDOR_PROD_ORIGINS.map((origin) => {
    const decoded = decodeVendorHostMessage({
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

export function probeSameEnvelopeDenied(win) {
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
    evalRejected: evalCh.reason === "channel_not_allowed" || !isAllowedVendorHostChannel("eval"),
    tokenReason: token.reason,
    evalReason: evalCh.reason || "channel_not_allowed",
    wechatSrcDenied: isAllowedVendorHostSrc("https://servicewechat.com/m/host", win) === false,
    dingtalkSrcDenied: isAllowedVendorHostSrc("https://h5.dingtalk.com/m/host", win) === false,
    harnessSrcDenied: isAllowedVendorHostSrc(PROD_GAP_HARNESS_PATH, win) === false
      && isSafeHostPath(PROD_GAP_HARNESS_PATH) === false,
    sameOriginAllowed: isAllowedVendorHostOrigin(win?.location?.origin, win) === true,
  };
}
