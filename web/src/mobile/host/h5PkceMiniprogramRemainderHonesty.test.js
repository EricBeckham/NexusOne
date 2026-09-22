import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "./fakeWindow.js";
import { HOST_CHANNELS } from "./hostBridge.js";
import {
  ALL_SIBLING_LEFTOVER_ROWS,
  LEFTOVER_KIND,
  NOT_CLAIMED_VS_LEFTOVERS,
  SIBLING_LEFTOVER_ROWS,
  describeH5PkceMiniprogramRemainderContract,
  honestH5PkceMiniprogramRemainderReport,
  leftover117Harness,
  leftover126Harness,
  liveVerdict,
  probeLiveH5Pkce,
  verifyH5PkceMiniprogramRemainderReport,
} from "./h5PkceMiniprogramRemainderHonesty.js";
import {
  honestWxDtNativeBridgeRemainderReport,
  verifyWxDtNativeBridgeRemainderReport,
} from "./wxDtNativeBridgeRemainder.js";

const source = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "h5PkceMiniprogramRemainderHonesty.js"),
  "utf8",
);
const parentSource = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "wxDtNativeBridgeRemainder.js"),
  "utf8",
);

describe("G15 H5 PKCE / miniprogram web-view remainder beyond #198/#220/#120", () => {
  it("reuses leftover contracts and does not redo #117/#120/#126/#198/#220", () => {
    expect([...HOST_CHANNELS]).toEqual(["login", "route", "file", "close"]);
    expect(source).toMatch(/Does not redo/);
    expect(source).toMatch(/#117/);
    expect(source).toMatch(/#120/);
    expect(source).toMatch(/#126/);
    expect(source).toMatch(/#198/);
    expect(source).toMatch(/#220/);
    expect(source).toMatch(/Does not close product G15/);
    expect(source).toMatch(/AuditPort same-TX is untouched/);
    expect(source).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
    expect(parentSource).toMatch(/LEFTOVER_KIND = "wx-dt-native-bridge-remainder"/);
    expect(SIBLING_LEFTOVER_ROWS).toEqual(expect.arrayContaining([
      "g15-h5-pkce-browser",
      "g15-miniprogram-webview",
      "g15-wx-dt-native-bridge-honesty",
      "g15-wx-dt-native-bridge-remainder",
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
    ]));
    expect(ALL_SIBLING_LEFTOVER_ROWS).toEqual(expect.arrayContaining([
      "g9-live-multi-jvm-replica-remainder-honesty",
      "g3-outbox-consume-idempotent-honesty",
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
    ]));
  });

  it("PASSes leftover harness contracts and SKIPs live hosts when unavailable", () => {
    const win = createFakeWindow({ embedded: true });
    const contract = describeH5PkceMiniprogramRemainderContract(win);
    expect(contract.leftoverKind).toBe(LEFTOVER_KIND);
    expect(contract.g15Close).toBe("NOT_CLAIMED");
    expect(contract.leftover117Harness).toBe("PASS");
    expect(contract.leftover126Harness).toBe("PASS");
    expect(contract.leftover120Sibling).toBe("PASS");
    expect(contract.leftover198Harness).toBe("PASS");
    expect(contract.leftover220Harness).toBe("PASS");
    expect(leftover117Harness(win)).toBe("PASS");
    expect(leftover126Harness()).toBe("PASS");
    expect(contract.liveH5KeycloakPkce).toBe("SKIP");
    expect(contract.liveHttpsKeycloakH5Pkce).toBe("SKIP");
    expect(contract.wechatMiniProgramProduction).toBe("SKIP");
    expect(contract.dingtalkMiniProgramProduction).toBe("SKIP");
    expect(contract.iosIpaProduction).toBe("SKIP");
    expect(contract.androidApkProduction).toBe("SKIP");
    expect(contract.playwrightStubIsNotLiveKeycloak).toBe(true);
    expect(contract.simulatedMpWebviewIsNotProduction).toBe(true);
    expect(contract.productionAccepted).toBe(false);
    expect(contract.redone117).toBe(false);
    expect(contract.redone120).toBe(false);
    expect(contract.redone126).toBe(false);
    expect(contract.redone198).toBe(false);
    expect(contract.redone220).toBe(false);
    expect(contract.parentLeftoverKind).toBe("wx-dt-native-bridge-remainder");
    expect(contract.notClaimed).toEqual(NOT_CLAIMED_VS_LEFTOVERS);
    expect(verifyWxDtNativeBridgeRemainderReport(honestWxDtNativeBridgeRemainderReport())).toEqual({
      ok: true,
      errors: [],
    });
  });

  it("only PASSes live H5 Keycloak PKCE when a live host and walk were observed", () => {
    expect(liveVerdict(false, false)).toBe("SKIP");
    expect(liveVerdict(true, false)).toBe("SKIP");
    expect(liveVerdict(false, true)).toBe("SKIP");
    expect(liveVerdict(true, true)).toBe("PASS");
    const live = probeLiveH5Pkce({
      NEXUS_KEYCLOAK_HTTPS: "1",
      H5_PKCE_WALK_OBSERVED: "true",
    });
    expect(live.h5KeycloakLive).toBe(true);
    expect(live.h5PkceWalkObserved).toBe(true);
    const win = createFakeWindow({ embedded: true });
    const contract = describeH5PkceMiniprogramRemainderContract(win, {
      NEXUS_KEYCLOAK_HTTPS: "1",
      H5_PKCE_WALK_OBSERVED: "true",
    });
    expect(contract.liveH5KeycloakPkce).toBe("PASS");
    expect(contract.g15Close).toBe("NOT_CLAIMED");
    expect(contract.wechatMiniProgramProduction).toBe("SKIP");
    const chromeOnly = describeH5PkceMiniprogramRemainderContract(win, {}, { chrome: true });
    expect(chromeOnly.liveH5KeycloakPkce).toBe("SKIP");
  });

  it("accepts the honest leftover report and rejects fake close / live PASS / redo", () => {
    expect(verifyH5PkceMiniprogramRemainderReport(honestH5PkceMiniprogramRemainderReport())).toEqual({
      ok: true,
      errors: [],
    });
    const fakeClose = verifyH5PkceMiniprogramRemainderReport({
      ...honestH5PkceMiniprogramRemainderReport(),
      g15_close: "CLOSED",
    });
    expect(fakeClose.ok).toBe(false);
    expect(fakeClose.errors.join(" ")).toMatch(/g15_close/);
    const fakeLivePkce = verifyH5PkceMiniprogramRemainderReport({
      ...honestH5PkceMiniprogramRemainderReport(),
      live_h5_keycloak_pkce: "PASS",
      h5_pkce_walk_observed: "false",
    });
    expect(fakeLivePkce.ok).toBe(false);
    expect(fakeLivePkce.errors.join(" ")).toMatch(/live_h5_keycloak_pkce/);
    const fakeWechat = verifyH5PkceMiniprogramRemainderReport({
      ...honestH5PkceMiniprogramRemainderReport(),
      wechat_miniprogram_production: "PASS",
      wechat_walk_observed: "false",
    });
    expect(fakeWechat.ok).toBe(false);
    expect(fakeWechat.errors.join(" ")).toMatch(/wechat_walk_observed/);
    const fakeIpa = verifyH5PkceMiniprogramRemainderReport({
      ...honestH5PkceMiniprogramRemainderReport(),
      ios_ipa_production: "PASS",
      ios_walk_observed: "false",
    });
    expect(fakeIpa.ok).toBe(false);
    expect(fakeIpa.errors.join(" ")).toMatch(/ios_walk_observed/);
    const fakeRedo = verifyH5PkceMiniprogramRemainderReport({
      ...honestH5PkceMiniprogramRemainderReport(),
      redone_220: "true",
    });
    expect(fakeRedo.ok).toBe(false);
    expect(fakeRedo.errors.join(" ")).toMatch(/redone_220/);
    const dropRow = verifyH5PkceMiniprogramRemainderReport({
      ...honestH5PkceMiniprogramRemainderReport(),
      leftover_rows_kept: "g15-h5-pkce-browser,g15-miniprogram-webview",
    });
    expect(dropRow.ok).toBe(false);
    expect(dropRow.errors.join(" ")).toMatch(/g15-wx-dt-native-bridge-remainder|g4-hot-purge-staging-leftover-honesty|g7-withdraw-browser-evidence-honesty|g2-h5-every-page-form-write-remainder-honesty|g9-redis-down-invalidate-remainder-honesty|g7-query-expense-travel-honesty-remainder|g1-fieldpolicy-mask-second-auth-remainder-honesty|g17-mfa-recovery-vue-otp-remainder-honesty|g5-publishhook-ddl-remainder-honesty|s0-secret-rotate-getenv-leftover-honesty|g10-file-object-reconcile-remainder-honesty|g4-hot-purge-staging-pkce-remainder-honesty|linux-arm-profile-remainder-honesty|g2-h5-every-page-form-write-report-remainder-honesty/);
  });
});
