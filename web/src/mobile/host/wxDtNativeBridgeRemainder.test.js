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
  describeWxDtNativeBridgeRemainderContract,
  honestWxDtNativeBridgeRemainderReport,
  liveVerdict,
  probeLiveHosts,
  verifyWxDtNativeBridgeRemainderReport,
} from "./wxDtNativeBridgeRemainder.js";
import {
  honestWxDtNativeBridgeReport,
  verifyWxDtNativeBridgeHonestyReport,
} from "./wxDtNativeBridgeHonesty.js";

const source = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "wxDtNativeBridgeRemainder.js"),
  "utf8",
);
const parentSource = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "wxDtNativeBridgeHonesty.js"),
  "utf8",
);

describe("G15 WeChat/DingTalk prod-gap + native-bridge remainder beyond #167/#181/#198", () => {
  it("reuses F014 leftover contracts and does not redo #117/#132/#151/#167/#181/#198", () => {
    expect([...HOST_CHANNELS]).toEqual(["login", "route", "file", "close"]);
    expect(source).toMatch(/Does not redo/);
    expect(source).toMatch(/#117/);
    expect(source).toMatch(/#132/);
    expect(source).toMatch(/#151/);
    expect(source).toMatch(/#167/);
    expect(source).toMatch(/#181/);
    expect(source).toMatch(/#198/);
    expect(source).toMatch(/Does not close product G15/);
    expect(source).toMatch(/AuditPort same-TX is untouched/);
    expect(source).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
    expect(parentSource).toMatch(/LEFTOVER_KIND = "wx-dt-native-bridge-honesty"/);
    expect(SIBLING_LEFTOVER_ROWS).toEqual(expect.arrayContaining([
      "g15-wx-dt-prod-gap-honesty",
      "g15-native-bridge-honesty",
      "g15-native-bridge-stub",
      "g15-wx-dt-native-bridge-honesty",
    ]));
    expect(ALL_SIBLING_LEFTOVER_ROWS).toEqual(expect.arrayContaining([
      "g2-ops-pg-dump-honesty",
      "g9-sentinel-cluster-remainder-honesty",
      "g5-publishhook-ddl-honesty",
      "g17-gm-tls-nginx-handshake-honesty",
      "g17-mfa-recovery-vue-otp-honesty",
      "g9-redis-down-invalidate-remainder",
      "g15-wx-dt-native-bridge-honesty",
    ]));
  });

  it("PASSes leftover harness contracts and SKIPs live hosts when unavailable", () => {
    const win = createFakeWindow({ embedded: true });
    const contract = describeWxDtNativeBridgeRemainderContract(win);
    expect(contract.leftoverKind).toBe(LEFTOVER_KIND);
    expect(contract.g15Close).toBe("NOT_CLAIMED");
    expect(contract.leftover167Harness).toBe("PASS");
    expect(contract.leftover181Harness).toBe("PASS");
    expect(contract.leftover198Harness).toBe("PASS");
    expect(contract.wechatMiniProgramProduction).toBe("SKIP");
    expect(contract.dingtalkMiniProgramProduction).toBe("SKIP");
    expect(contract.iosIpaProduction).toBe("SKIP");
    expect(contract.androidApkProduction).toBe("SKIP");
    expect(contract.storeReadyStatus).toBe("SKIP");
    expect(contract.productionAccepted).toBe(false);
    expect(contract.storeReady).toBe(false);
    expect(contract.nativeBinary).toBe(false);
    expect(contract.redone117).toBe(false);
    expect(contract.redone132).toBe(false);
    expect(contract.redone151).toBe(false);
    expect(contract.redone167).toBe(false);
    expect(contract.redone181).toBe(false);
    expect(contract.redone198).toBe(false);
    expect(contract.parentLeftoverKind).toBe("wx-dt-native-bridge-honesty");
    expect(contract.notClaimed).toEqual(NOT_CLAIMED_VS_LEFTOVERS);
    expect(verifyWxDtNativeBridgeHonestyReport(honestWxDtNativeBridgeReport())).toEqual({
      ok: true,
      errors: [],
    });
  });

  it("only PASSes a live host when that host is present and a walk was observed", () => {
    expect(liveVerdict(false, false)).toBe("SKIP");
    expect(liveVerdict(true, false)).toBe("SKIP");
    expect(liveVerdict(false, true)).toBe("SKIP");
    expect(liveVerdict(true, true)).toBe("PASS");
    const live = probeLiveHosts({
      WECHAT_PROD_HOST: "https://servicewechat.com",
      WECHAT_WALK_OBSERVED: "true",
    });
    expect(live.wechatLiveHost).toBe(true);
    expect(live.wechatWalkObserved).toBe(true);
    const win = createFakeWindow({ embedded: true });
    const contract = describeWxDtNativeBridgeRemainderContract(win, {
      WECHAT_PROD_HOST: "https://servicewechat.com",
      WECHAT_WALK_OBSERVED: "true",
    });
    expect(contract.wechatMiniProgramProduction).toBe("PASS");
    expect(contract.dingtalkMiniProgramProduction).toBe("SKIP");
    expect(contract.g15Close).toBe("NOT_CLAIMED");
  });

  it("accepts the honest leftover report and rejects fake close / live PASS / redo", () => {
    expect(verifyWxDtNativeBridgeRemainderReport(honestWxDtNativeBridgeRemainderReport())).toEqual({
      ok: true,
      errors: [],
    });
    const fakeClose = verifyWxDtNativeBridgeRemainderReport({
      ...honestWxDtNativeBridgeRemainderReport(),
      g15_close: "CLOSED",
    });
    expect(fakeClose.ok).toBe(false);
    expect(fakeClose.errors.join(" ")).toMatch(/g15_close/);
    const fakeWechat = verifyWxDtNativeBridgeRemainderReport({
      ...honestWxDtNativeBridgeRemainderReport(),
      wechat_miniprogram_production: "PASS",
      wechat_walk_observed: "false",
    });
    expect(fakeWechat.ok).toBe(false);
    expect(fakeWechat.errors.join(" ")).toMatch(/wechat_walk_observed/);
    const fakeIpa = verifyWxDtNativeBridgeRemainderReport({
      ...honestWxDtNativeBridgeRemainderReport(),
      ios_ipa_production: "PASS",
      ios_walk_observed: "false",
    });
    expect(fakeIpa.ok).toBe(false);
    expect(fakeIpa.errors.join(" ")).toMatch(/ios_walk_observed/);
    const fakeRedo = verifyWxDtNativeBridgeRemainderReport({
      ...honestWxDtNativeBridgeRemainderReport(),
      redone_198: "true",
    });
    expect(fakeRedo.ok).toBe(false);
    expect(fakeRedo.errors.join(" ")).toMatch(/redone_198/);
    const dropRow = verifyWxDtNativeBridgeRemainderReport({
      ...honestWxDtNativeBridgeRemainderReport(),
      leftover_rows_kept: "g15-wx-dt-prod-gap-honesty,g15-native-bridge-honesty",
    });
    expect(dropRow.ok).toBe(false);
    expect(dropRow.errors.join(" ")).toMatch(/g15-wx-dt-native-bridge-honesty/);
  });
});
