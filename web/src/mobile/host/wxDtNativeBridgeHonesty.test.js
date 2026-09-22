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
  describeWxDtNativeBridgeHonestyContract,
  honestWxDtNativeBridgeReport,
  liveVerdict,
  probeLiveHosts,
  verifyWxDtNativeBridgeHonestyReport,
} from "./wxDtNativeBridgeHonesty.js";
import { honestProdGapReport, verifyProdGapReport } from "./wechatDingTalkProdGapHonesty.js";
import {
  honestNativeBridgeReport,
  verifyNativeBridgeHonestyReport,
} from "./nativeBridgeHonesty.js";

const source = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "wxDtNativeBridgeHonesty.js"),
  "utf8",
);
const gapSource = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "wechatDingTalkProdGapHonesty.js"),
  "utf8",
);
const nativeSource = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "nativeBridgeHonesty.js"),
  "utf8",
);
const stubSource = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "nativeBridgeStub.js"),
  "utf8",
);

describe("G15 WeChat/DingTalk prod-gap + native-bridge honesty beyond #167/#181", () => {
  it("reuses F014 leftover contracts and does not redo #151/#167/#181 product code", () => {
    expect([...HOST_CHANNELS]).toEqual(["login", "route", "file", "close"]);
    expect(source).toMatch(/Does not redo/);
    expect(source).toMatch(/#151/);
    expect(source).toMatch(/#167/);
    expect(source).toMatch(/#181/);
    expect(source).toMatch(/Does not close product G15/);
    expect(source).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
    expect(gapSource).toMatch(/leftoverKind: "wx-dt-prod-gap-honesty"/);
    expect(nativeSource).toMatch(/leftoverKind: "native-bridge-honesty"/);
    expect(stubSource).toMatch(/not a store-ready/);
    expect(SIBLING_LEFTOVER_ROWS).toEqual(expect.arrayContaining([
      "g15-wx-dt-prod-gap-honesty",
      "g15-native-bridge-honesty",
      "g15-native-bridge-stub",
    ]));
    expect(ALL_SIBLING_LEFTOVER_ROWS).toEqual(expect.arrayContaining([
      "g4-p1-staging-audit-events-honesty",
      "g6-recovery-port-dry-run-honesty",
      "s0s1-live-redis-minio-isolation-honesty",
    ]));
  });

  it("PASSes leftover harness contracts and SKIPs live hosts when unavailable", () => {
    const win = createFakeWindow({ embedded: true });
    const contract = describeWxDtNativeBridgeHonestyContract(win);
    expect(contract.leftoverKind).toBe(LEFTOVER_KIND);
    expect(contract.g15Close).toBe("NOT_CLAIMED");
    expect(contract.wxDtHarnessContract).toBe("PASS");
    expect(contract.nativeBridgeHarnessContract).toBe("PASS");
    expect(contract.wechatMiniProgramProduction).toBe("SKIP");
    expect(contract.dingtalkMiniProgramProduction).toBe("SKIP");
    expect(contract.iosIpaProduction).toBe("SKIP");
    expect(contract.androidApkProduction).toBe("SKIP");
    expect(contract.storeReadyStatus).toBe("SKIP");
    expect(contract.productionAccepted).toBe(false);
    expect(contract.storeReady).toBe(false);
    expect(contract.nativeBinary).toBe(false);
    expect(contract.vendorJsapiLoaded).toBe(false);
    expect(contract.redone151).toBe(false);
    expect(contract.redone167).toBe(false);
    expect(contract.redone181).toBe(false);
    expect(contract.gapLeftoverKind).toBe("wx-dt-prod-gap-honesty");
    expect(contract.nativeLeftoverKind).toBe("native-bridge-honesty");
    expect(contract.notClaimed).toEqual(NOT_CLAIMED_VS_LEFTOVERS);
    expect(verifyProdGapReport(honestProdGapReport())).toEqual({ ok: true, errors: [] });
    expect(verifyNativeBridgeHonestyReport(honestNativeBridgeReport())).toEqual({
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
    expect(live.dingtalkLiveHost).toBe(false);
    const win = createFakeWindow({ embedded: true });
    const contract = describeWxDtNativeBridgeHonestyContract(win, {
      WECHAT_PROD_HOST: "https://servicewechat.com",
      WECHAT_WALK_OBSERVED: "true",
    });
    expect(contract.wechatMiniProgramProduction).toBe("PASS");
    expect(contract.dingtalkMiniProgramProduction).toBe("SKIP");
    expect(contract.g15Close).toBe("NOT_CLAIMED");
  });

  it("accepts the honest leftover report and rejects fake close / live PASS / redo", () => {
    expect(verifyWxDtNativeBridgeHonestyReport(honestWxDtNativeBridgeReport())).toEqual({
      ok: true,
      errors: [],
    });
    const fakeClose = verifyWxDtNativeBridgeHonestyReport({
      ...honestWxDtNativeBridgeReport(),
      g15_close: "CLOSED",
    });
    expect(fakeClose.ok).toBe(false);
    expect(fakeClose.errors.join(" ")).toMatch(/g15_close/);
    const fakeWechat = verifyWxDtNativeBridgeHonestyReport({
      ...honestWxDtNativeBridgeReport(),
      wechat_miniprogram_production: "PASS",
      wechat_walk_observed: "false",
    });
    expect(fakeWechat.ok).toBe(false);
    expect(fakeWechat.errors.join(" ")).toMatch(/wechat_walk_observed/);
    const fakeIpa = verifyWxDtNativeBridgeHonestyReport({
      ...honestWxDtNativeBridgeReport(),
      ios_ipa_production: "PASS",
      ios_walk_observed: "false",
    });
    expect(fakeIpa.ok).toBe(false);
    expect(fakeIpa.errors.join(" ")).toMatch(/ios_walk_observed/);
    const fakeRedo = verifyWxDtNativeBridgeHonestyReport({
      ...honestWxDtNativeBridgeReport(),
      redone_167: "true",
    });
    expect(fakeRedo.ok).toBe(false);
    expect(fakeRedo.errors.join(" ")).toMatch(/redone_167/);
    const dropRow = verifyWxDtNativeBridgeHonestyReport({
      ...honestWxDtNativeBridgeReport(),
      leftover_rows_kept: "g15-native-bridge-honesty",
    });
    expect(dropRow.ok).toBe(false);
    expect(dropRow.errors.join(" ")).toMatch(/g15-wx-dt-prod-gap-honesty/);
  });
});
