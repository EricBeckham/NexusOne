import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "./fakeWindow.js";
import { HOST_CHANNELS, hostCapabilities, isSafeHostPath } from "./hostBridge.js";
import { VENDOR_PROD_ORIGINS } from "./wechatDingTalkHost.js";
import {
  DINGTALK_SKIP_REASON,
  NOT_CLAIMED_VS_LEFTOVERS,
  PROD_GAP_HARNESS_PATH,
  PROD_GAP_ITEMS,
  WECHAT_SKIP_REASON,
  describeProdGapContract,
  honestProdGapReport,
  isProdGapHarnessPath,
  probeSameEnvelopeDenied,
  probeVendorOriginsDenied,
  verifyProdGapReport,
} from "./wechatDingTalkProdGapHonesty.js";

const source = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "wechatDingTalkProdGapHonesty.js"),
  "utf8",
);
const hostBridgeSource = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "hostBridge.js"),
  "utf8",
);

describe("G15 WeChat/DingTalk production mini-program gap honesty", () => {
  it("reuses F014 channels and does not load vendor JSAPI", () => {
    expect([...HOST_CHANNELS]).toEqual(["login", "route", "file", "close"]);
    expect(source).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
    expect(hostBridgeSource).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
    expect(source).toMatch(/#117/);
    expect(source).toMatch(/#132/);
    expect(source).toMatch(/#151/);
    expect(PROD_GAP_ITEMS).toEqual(expect.arrayContaining([
      "wechat_miniprogram_package",
      "dingtalk_miniprogram_package",
      "vendor_jsapi",
    ]));
  });

  it("names the gap stub and leaves production / G15 unaccepted", () => {
    const win = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=wechat-prod-stub",
    });
    const contract = describeProdGapContract(win, "wechat");
    expect(contract.leftoverKind).toBe("wx-dt-prod-gap-honesty");
    expect(contract.stub).toBe(true);
    expect(contract.productionAccepted).toBe(false);
    expect(contract.g15Close).toBe("NOT_CLAIMED");
    expect(contract.wechatMiniProgramProduction).toBe("SKIP");
    expect(contract.dingtalkMiniProgramProduction).toBe("SKIP");
    expect(contract.vendorJsapiLoaded).toBe(false);
    expect(contract.vendorDomainAllowed).toBe(false);
    expect(contract.wechatWalkObserved).toBe(false);
    expect(contract.dingtalkWalkObserved).toBe(false);
    expect(contract.wechatSkipReason).toBe(WECHAT_SKIP_REASON);
    expect(contract.dingtalkSkipReason).toBe(DINGTALK_SKIP_REASON);
    expect(contract.notClaimed).toEqual(NOT_CLAIMED_VS_LEFTOVERS);
    expect(contract.leftover).toEqual(["APP WebView", "WeChat/DingTalk 小程序 production"]);
    expect(contract.vendors.wechat.productionAccepted).toBe(false);
    expect(contract.vendors.dingtalk.productionAccepted).toBe(false);
    expect(hostCapabilities(win).productionAccepted).toBe(false);
    expect(hostCapabilities(win).otherHostsOpen).toEqual([
      "APP WebView",
      "WeChat/DingTalk 小程序 production",
    ]);
  });

  it("denies vendor production origins and the honesty harness as a business src", () => {
    const win = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=dingtalk-prod-stub",
    });
    const origins = probeVendorOriginsDenied(win);
    expect(origins).toHaveLength(VENDOR_PROD_ORIGINS.length);
    expect(origins.every((item) => item.ok)).toBe(true);
    const denied = probeSameEnvelopeDenied(win);
    expect(denied.tokenRejected).toBe(true);
    expect(denied.evalRejected).toBe(true);
    expect(denied.wechatSrcDenied).toBe(true);
    expect(denied.dingtalkSrcDenied).toBe(true);
    expect(denied.harnessSrcDenied).toBe(true);
    expect(isProdGapHarnessPath(PROD_GAP_HARNESS_PATH)).toBe(true);
    expect(isSafeHostPath(PROD_GAP_HARNESS_PATH)).toBe(false);
  });

  it("accepts the honest leftover report and rejects fake production / G15 close", () => {
    expect(verifyProdGapReport(honestProdGapReport())).toEqual({ ok: true, errors: [] });
    const fakeClose = verifyProdGapReport({
      ...honestProdGapReport(),
      g15_close: "CLOSED",
    });
    expect(fakeClose.ok).toBe(false);
    expect(fakeClose.errors.join(" ")).toMatch(/g15_close/);
    const fakePass = verifyProdGapReport({
      ...honestProdGapReport(),
      wechat_miniprogram_production: "PASS",
      wechat_walk_observed: "false",
    });
    expect(fakePass.ok).toBe(false);
    expect(fakePass.errors.join(" ")).toMatch(/wechat_walk_observed/);
    const fakeAccepted = verifyProdGapReport({
      ...honestProdGapReport(),
      production_accepted: "true",
    });
    expect(fakeAccepted.ok).toBe(false);
    expect(fakeAccepted.errors.join(" ")).toMatch(/production_accepted/);
  });
});
