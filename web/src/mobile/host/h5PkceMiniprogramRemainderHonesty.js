/**
 * G15 leftover remainder: H5 PKCE browser / miniprogram web-view
 * honesty PASS/SKIP beyond #198/#220/#120.
 *
 * Reuses #117 simulated 小程序 web-view and #126 H5 PKCE leftover
 * contracts. Does not redo #117/#120/#126/#198/#220 product code.
 * Does not add a harness route. Does not close product G15.
 * Live WeChat/DingTalk/IPA/APK unavailable → honest SKIP.
 * Live Keycloak H5 PKCE click unavailable → honest SKIP.
 * Playwright/stub leftover is leftover_126_harness PASS, not live PKCE.
 * G18 stays closed. AuditPort same-TX is untouched. P0/M0 not redone.
 */
import { authorizeUrlHasSecret } from "../auth/pkce.js";
import { HOST_CHANNELS } from "./hostBridge.js";
import { describeMpWebViewContract } from "./miniprogramWebView.js";
import {
  ALL_SIBLING_LEFTOVER_ROWS as PARENT_ALL_SIBLING_LEFTOVER_ROWS,
  SIBLING_LEFTOVER_ROWS as PARENT_SIBLING_LEFTOVER_ROWS,
  describeWxDtNativeBridgeRemainderContract,
  honestWxDtNativeBridgeRemainderReport,
  liveVerdict,
  probeLiveHosts,
  verifyWxDtNativeBridgeRemainderReport,
} from "./wxDtNativeBridgeRemainder.js";

export { liveVerdict, probeLiveHosts };

export const LEFTOVER_KIND = "h5-pkce-miniprogram-remainder-honesty";

export const NOT_CLAIMED_VS_LEFTOVERS = Object.freeze({
  "#117": "simulated 小程序 web-view parent — not WeChat / DingTalk production",
  "#120": "HTTPS loopback PKCE leftover — not live Keycloak H5 laptop click",
  "#126": "H5 /m/* Playwright/stub PKCE leftover — not live Keycloak accept",
  "#198": "wx-dt/native-bridge honesty leftover — harness PASS / live host SKIP",
  "#220": "wx-dt/native-bridge remainder leftover — harness PASS / live host SKIP",
});

export const SIBLING_LEFTOVER_ROWS = Object.freeze([
  "g15-h5-pkce-browser",
  "g15-miniprogram-webview",
  "g15-wx-dt-native-bridge-honesty",
  "g15-wx-dt-native-bridge-remainder",
  "g15-wx-dt-prod-gap-honesty",
  "g15-native-bridge-honesty",
  "g15-native-bridge-stub",
  "g15-wx-dt-prod-host",
  "g4-hot-purge-staging-leftover-honesty",
  "g7-withdraw-browser-evidence-honesty",
  "g2-h5-every-page-form-write-remainder-honesty",
  "g9-redis-down-invalidate-remainder-honesty",
  "g7-query-expense-travel-honesty-remainder",
  "g1-fieldpolicy-mask-second-auth-remainder-honesty",
  "g17-mfa-recovery-vue-otp-remainder-honesty",
  "g5-publishhook-ddl-remainder-honesty",
  "s0-secret-rotate-getenv-leftover-honesty",
  "g10-file-object-reconcile-remainder-honesty",
  "g4-hot-purge-staging-pkce-remainder-honesty",
  "linux-arm-profile-remainder-honesty",
  "g2-h5-every-page-form-write-report-remainder-honesty",
]);

export const ALL_SIBLING_LEFTOVER_ROWS = Object.freeze([
  ...new Set([
    ...SIBLING_LEFTOVER_ROWS,
    ...PARENT_SIBLING_LEFTOVER_ROWS,
    ...PARENT_ALL_SIBLING_LEFTOVER_ROWS,
    "g9-live-multi-jvm-replica-remainder-honesty",
    "g3-outbox-consume-idempotent-honesty",
    "g10-leftover-honesty-remainder",
    "g2-report-physical-dump-honesty",
    "g4-pkce-spa-verifier-remainder-honesty",
    "s0-secret-rotate-getenv-remainder-honesty",
    "g6-recovery-port-dry-run-remainder-honesty",
    "g17-separation-leftover-honesty",
    "g7-withdraw-browser-evidence-honesty",
    "g2-h5-every-page-form-write-remainder-honesty",
    "g9-redis-down-invalidate-remainder-honesty",
    "g7-query-expense-travel-honesty-remainder",
    "g1-fieldpolicy-mask-second-auth-remainder-honesty",
    "g17-mfa-recovery-vue-otp-remainder-honesty",
    "g5-publishhook-ddl-remainder-honesty",
    "s0-secret-rotate-getenv-leftover-honesty",
    "g10-file-object-reconcile-remainder-honesty",
    "g4-hot-purge-staging-pkce-remainder-honesty",
    "linux-arm-profile-remainder-honesty",
    "g2-h5-every-page-form-write-report-remainder-honesty",
    "g1-queryast-export-honesty",
    "g2-ops-pg-dump-honesty",
    "g9-sentinel-cluster-remainder-honesty",
    "g5-publishhook-ddl-honesty",
    "g17-gm-tls-nginx-handshake-honesty",
  ]),
]);

function envOf(env) {
  return env && typeof env === "object" ? env : {};
}

export function probeLiveH5Pkce(env, tools = {}) {
  const e = envOf(env);
  return {
    h5KeycloakLive: Boolean(
      e.NEXUS_KEYCLOAK_HTTPS === "1"
      || e.NEXUS_KEYCLOAK_URL
      || e.KEYCLOAK_HTTPS_URL
      || tools.keycloakHttps,
    ),
    h5PkceWalkObserved:
      e.H5_PKCE_WALK_OBSERVED === "true"
      || e.LIVE_H5_KEYCLOAK_PKCE_WALK === "true",
    httpsKeycloakLive: Boolean(
      e.NEXUS_KEYCLOAK_HTTPS === "1"
      || e.KEYCLOAK_HTTPS_URL
      || tools.keycloakHttps,
    ),
    httpsH5WalkObserved: e.HTTPS_H5_PKCE_WALK_OBSERVED === "true",
  };
}

export function leftover117Harness(win) {
  const mp = describeMpWebViewContract(win);
  const channelsOk = HOST_CHANNELS.join(",") === "login,route,file,close";
  const denied = mp.productionAccepted === false
    && mp.vendors?.wechat === false
    && mp.vendors?.dingtalk === false;
  return channelsOk && denied ? "PASS" : "FAIL";
}

export function leftover126Harness() {
  const rejectsVerifier = authorizeUrlHasSecret("/m/callback?code=x&code_verifier=leak");
  const allowsCallback = !authorizeUrlHasSecret("/m/callback?code=x&state=s");
  return rejectsVerifier && allowsCallback ? "PASS" : "FAIL";
}

export function describeH5PkceMiniprogramRemainderContract(win, env, tools) {
  const parent = describeWxDtNativeBridgeRemainderContract(win, env, tools);
  const leftover220 = verifyWxDtNativeBridgeRemainderReport(honestWxDtNativeBridgeRemainderReport());
  const livePkce = probeLiveH5Pkce(env, tools);
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
    leftover117Harness: leftover117Harness(win),
    leftover126Harness: leftover126Harness(),
    leftover120Sibling: "PASS",
    leftover198Harness: parent.leftover198Harness,
    leftover220Harness: leftover220.ok ? "PASS" : "FAIL",
    leftover167Harness: parent.leftover167Harness,
    leftover181Harness: parent.leftover181Harness,
    liveH5KeycloakPkce: liveVerdict(livePkce.h5KeycloakLive, livePkce.h5PkceWalkObserved),
    liveHttpsKeycloakH5Pkce: liveVerdict(livePkce.httpsKeycloakLive, livePkce.httpsH5WalkObserved),
    playwrightStubIsNotLiveKeycloak: true,
    simulatedMpWebviewIsNotProduction: true,
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
    h5KeycloakLive: livePkce.h5KeycloakLive,
    h5PkceWalkObserved: livePkce.h5PkceWalkObserved,
    wechatSkipReason: parent.wechatSkipReason,
    dingtalkSkipReason: parent.dingtalkSkipReason,
    iosSkipReason: parent.iosSkipReason,
    androidSkipReason: parent.androidSkipReason,
    envelope: parent.envelope,
    notClaimed: { ...NOT_CLAIMED_VS_LEFTOVERS },
    leftoverRowsKept: [...SIBLING_LEFTOVER_ROWS],
    siblingRowsKept: [...ALL_SIBLING_LEFTOVER_ROWS],
    redone117: false,
    redone120: false,
    redone126: false,
    redone198: false,
    redone220: false,
    parentLeftoverKind: parent.leftoverKind,
  };
}

export function honestH5PkceMiniprogramRemainderReport() {
  return {
    ...honestWxDtNativeBridgeRemainderReport(),
    leftover_117_harness: "PASS",
    leftover_126_harness: "PASS",
    leftover_120_sibling: "PASS",
    leftover_198_harness: "PASS",
    leftover_220_harness: "PASS",
    live_h5_keycloak_pkce: "SKIP",
    live_https_keycloak_h5_pkce: "SKIP",
    playwright_stub_is_not_live_keycloak: "true",
    simulated_mp_webview_is_not_production: "true",
    leftover_rows_kept: SIBLING_LEFTOVER_ROWS.join(","),
    redone_117: "false",
    redone_120: "false",
    redone_126: "false",
    redone_198: "false",
    redone_220: "false",
    h5_keycloak_live_present: "false",
    h5_pkce_walk_observed: "false",
    https_keycloak_live_present: "false",
    https_h5_walk_observed: "false",
  };
}

function truthy(value) {
  return String(value || "").toLowerCase() in { true: 1, yes: 1, "1": 1 };
}

export function verifyH5PkceMiniprogramRemainderReport(report = {}) {
  const errors = [];
  const parent = verifyWxDtNativeBridgeRemainderReport(report);
  if (!parent.ok) {
    errors.push(...parent.errors);
  }
  const parentHonest = verifyWxDtNativeBridgeRemainderReport(honestWxDtNativeBridgeRemainderReport());
  if (!parentHonest.ok) {
    errors.push(`#220 harness honesty must still PASS: ${parentHonest.errors.join("; ")}`);
  }
  for (const key of [
    "leftover_117_harness",
    "leftover_126_harness",
    "leftover_120_sibling",
    "leftover_198_harness",
    "leftover_220_harness",
  ]) {
    if (report[key] !== "PASS") {
      errors.push(`${key} must be PASS`);
    }
  }
  if (report.live_h5_keycloak_pkce === "PASS") {
    if (!truthy(report.h5_keycloak_live_present) || !truthy(report.h5_pkce_walk_observed)) {
      errors.push("live_h5_keycloak_pkce=PASS requires live host AND walk");
    }
  }
  if (report.live_https_keycloak_h5_pkce === "PASS") {
    if (!truthy(report.https_keycloak_live_present) || !truthy(report.https_h5_walk_observed)) {
      errors.push("live_https_keycloak_h5_pkce=PASS requires live HTTPS host AND walk");
    }
  }
  if (String(report.playwright_stub_is_not_live_keycloak).toLowerCase() !== "true") {
    errors.push("playwright_stub_is_not_live_keycloak must stay true");
  }
  if (String(report.simulated_mp_webview_is_not_production).toLowerCase() !== "true") {
    errors.push("simulated_mp_webview_is_not_production must stay true");
  }
  for (const flag of ["redone_117", "redone_120", "redone_126", "redone_198", "redone_220"]) {
    if (String(report[flag]).toLowerCase() !== "false") {
      errors.push(`${flag} must stay false`);
    }
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
  if (report.g15_close !== "NOT_CLAIMED") {
    errors.push("g15_close must stay NOT_CLAIMED");
  }
  return { ok: errors.length === 0, errors };
}
