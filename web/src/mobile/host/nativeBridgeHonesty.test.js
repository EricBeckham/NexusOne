import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "./fakeWindow.js";
import { HOST_CHANNELS, hostCapabilities, isSafeHostPath } from "./hostBridge.js";
import {
  ANDROID_SKIP_REASON,
  BOTH_LEFTOVER_ROWS,
  IOS_SKIP_REASON,
  NATIVE_BRIDGE_HONESTY_ITEMS,
  NATIVE_BRIDGE_HONESTY_PATH,
  NOT_CLAIMED_VS_LEFTOVERS,
  describeNativeBridgeHonestyContract,
  honestNativeBridgeReport,
  isNativeBridgeHonestyPath,
  probeNativeEnvelopeDenied,
  probeNativeOriginsDenied,
  verifyNativeBridgeHonestyReport,
} from "./nativeBridgeHonesty.js";

const source = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "nativeBridgeHonesty.js"),
  "utf8",
);
const hostBridgeSource = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "hostBridge.js"),
  "utf8",
);
const stubSource = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "nativeBridgeStub.js"),
  "utf8",
);

describe("G15 APP WebView native-bridge honesty beyond #151", () => {
  it("reuses F014 channels and does not invent a store-ready IPA/APK", () => {
    expect([...HOST_CHANNELS]).toEqual(["login", "route", "file", "close"]);
    expect(source).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
    expect(hostBridgeSource).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
    expect(stubSource).toMatch(/not a store-ready/);
    expect(source).toMatch(/#114/);
    expect(source).toMatch(/#117/);
    expect(source).toMatch(/#132/);
    expect(source).toMatch(/#151/);
    expect(source).toMatch(/#167/);
    expect(source).toMatch(/Does not close product G15/);
    expect(NATIVE_BRIDGE_HONESTY_ITEMS).toEqual(expect.arrayContaining([
      "ios_ipa_store_package",
      "android_apk_store_package",
      "wkwebview_product_host",
    ]));
    expect(BOTH_LEFTOVER_ROWS).toEqual([
      "g15-native-bridge-stub",
      "g15-wx-dt-prod-gap-honesty",
    ]);
  });

  it("names the honesty leftover and leaves IPA/APK / G15 unaccepted", () => {
    const win = createFakeWindow({ nativeBridge: "ios" });
    const contract = describeNativeBridgeHonestyContract(win, "ios");
    expect(contract.leftoverKind).toBe("native-bridge-honesty");
    expect(contract.stub).toBe(true);
    expect(contract.storeReady).toBe(false);
    expect(contract.storePackage).toBe(false);
    expect(contract.nativeBinary).toBe(false);
    expect(contract.productionAccepted).toBe(false);
    expect(contract.g15Close).toBe("NOT_CLAIMED");
    expect(contract.stubContract).toBe("PASS");
    expect(contract.iosIpaProduction).toBe("SKIP");
    expect(contract.androidApkProduction).toBe("SKIP");
    expect(contract.storeReadyStatus).toBe("SKIP");
    expect(contract.iosWalkObserved).toBe(false);
    expect(contract.androidWalkObserved).toBe(false);
    expect(contract.iosSkipReason).toBe(IOS_SKIP_REASON);
    expect(contract.androidSkipReason).toBe(ANDROID_SKIP_REASON);
    expect(contract.iosProject).toBe(false);
    expect(contract.androidProject).toBe(false);
    expect(contract.wkWebViewWired).toBe(false);
    expect(contract.androidWebViewWired).toBe(false);
    expect(contract.notClaimed).toEqual(NOT_CLAIMED_VS_LEFTOVERS);
    expect(contract.leftover).toEqual(["APP WebView", "WeChat/DingTalk 小程序 production"]);
    expect(contract.leftoverRowsKept).toEqual(BOTH_LEFTOVER_ROWS);
    expect(hostCapabilities(win).storeReady).toBe(false);
    expect(hostCapabilities(win).nativeBinary).toBe(false);
    expect(hostCapabilities(win).productionAccepted).toBe(false);
    expect(hostCapabilities(win).otherHostsOpen).toEqual([
      "APP WebView",
      "WeChat/DingTalk 小程序 production",
    ]);
  });

  it("denies vendor / evil origins and the honesty harness as a business src", () => {
    const win = createFakeWindow({ nativeBridge: "android" });
    const origins = probeNativeOriginsDenied(win);
    expect(origins).toHaveLength(3);
    expect(origins.every((item) => item.ok)).toBe(true);
    const denied = probeNativeEnvelopeDenied(win);
    expect(denied.tokenRejected).toBe(true);
    expect(denied.evalRejected).toBe(true);
    expect(denied.harnessSrcDenied).toBe(true);
    expect(denied.stubHarnessSrcDenied).toBe(true);
    expect(denied.appWebViewSrcDenied).toBe(true);
    expect(denied.wxDtSrcDenied).toBe(true);
    expect(denied.mpSrcDenied).toBe(true);
    expect(denied.prodGapSrcDenied).toBe(true);
    expect(denied.walkChannels).toBe("login,route,file,close");
    expect(isNativeBridgeHonestyPath(NATIVE_BRIDGE_HONESTY_PATH)).toBe(true);
    expect(isSafeHostPath(NATIVE_BRIDGE_HONESTY_PATH)).toBe(false);
  });

  it("accepts the honest leftover report and rejects fake IPA/APK / G15 close", () => {
    expect(verifyNativeBridgeHonestyReport(honestNativeBridgeReport())).toEqual({
      ok: true,
      errors: [],
    });
    const fakeClose = verifyNativeBridgeHonestyReport({
      ...honestNativeBridgeReport(),
      g15_close: "CLOSED",
    });
    expect(fakeClose.ok).toBe(false);
    expect(fakeClose.errors.join(" ")).toMatch(/g15_close/);
    const fakeIpa = verifyNativeBridgeHonestyReport({
      ...honestNativeBridgeReport(),
      ios_ipa_production: "PASS",
      ios_walk_observed: "false",
    });
    expect(fakeIpa.ok).toBe(false);
    expect(fakeIpa.errors.join(" ")).toMatch(/ios_walk_observed/);
    const fakeApk = verifyNativeBridgeHonestyReport({
      ...honestNativeBridgeReport(),
      android_apk_production: "PASS",
      android_walk_observed: "false",
    });
    expect(fakeApk.ok).toBe(false);
    expect(fakeApk.errors.join(" ")).toMatch(/android_walk_observed/);
    const fakeStore = verifyNativeBridgeHonestyReport({
      ...honestNativeBridgeReport(),
      store_ready: "PASS",
      ios_walk_observed: "false",
      android_walk_observed: "false",
    });
    expect(fakeStore.ok).toBe(false);
    expect(fakeStore.errors.join(" ")).toMatch(/store_ready/);
    const dropRow = verifyNativeBridgeHonestyReport({
      ...honestNativeBridgeReport(),
      g15_native_bridge_stub_row: "dropped",
    });
    expect(dropRow.ok).toBe(false);
    expect(dropRow.errors.join(" ")).toMatch(/g15_native_bridge_stub_row/);
  });
});
