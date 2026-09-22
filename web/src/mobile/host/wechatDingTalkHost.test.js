import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "./fakeWindow.js";
import {
  HOST_CHANNELS,
  H5_SOURCE,
  HOST_SOURCE,
  VENDOR_DINGTALK_HOST,
  VENDOR_WECHAT_HOST,
  detectHostKind,
  encodeHostMessage,
  hostCapabilities,
  installHostBridge,
  isSafeHostPath,
} from "./hostBridge.js";
import {
  VENDOR_PROD_ORIGINS,
  VENDOR_STUB_WALK,
  childSrcForVendor,
  decodeVendorHostMessage,
  describeVendorProdContract,
  isAllowedVendorHostChannel,
  isAllowedVendorHostOrigin,
  isAllowedVendorHostSrc,
  postToVendorHost,
} from "./wechatDingTalkHost.js";

const source = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "wechatDingTalkHost.js"), "utf8");
const hostBridgeSource = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "hostBridge.js"), "utf8");

describe("WeChat / DingTalk production host contract stub", () => {
  it("reuses HostBridgePort channels and does not load vendor JSAPI", () => {
    expect([...HOST_CHANNELS]).toEqual(["login", "route", "file", "close"]);
    expect(source).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
    expect(hostBridgeSource).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
    expect(isAllowedVendorHostChannel("login")).toBe(true);
    expect(isAllowedVendorHostChannel("eval")).toBe(false);
  });

  it("names both vendor stubs and leaves production unaccepted", () => {
    const wechat = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=wechat-prod-stub",
    });
    const dingtalk = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=dingtalk-prod-stub",
    });
    expect(detectHostKind(wechat)).toBe(VENDOR_WECHAT_HOST);
    expect(detectHostKind(dingtalk)).toBe(VENDOR_DINGTALK_HOST);
    const wechatCaps = hostCapabilities(wechat);
    expect(wechatCaps.host).toBe(VENDOR_WECHAT_HOST);
    expect(wechatCaps.accepted).toBe(true);
    expect(wechatCaps.productionAccepted).toBe(false);
    expect(wechatCaps.otherHostsOpen).toEqual(["APP WebView", "WeChat/DingTalk 小程序 production"]);
    const described = describeVendorProdContract(wechat, "wechat");
    expect(described.stub).toBe(true);
    expect(described.productionAccepted).toBe(false);
    expect(described.vendors).toEqual({
      wechat: { stub: true, productionAccepted: false },
      dingtalk: { stub: true, productionAccepted: false },
    });
    expect(describeVendorProdContract(dingtalk, "dingtalk").host).toBe(VENDOR_DINGTALK_HOST);
    expect(described.originAllowlist).toEqual([wechat.location.origin]);
  });

  it("allows only the H5 origin; vendor production / evil stay denied", () => {
    const win = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=wechat-prod-stub",
    });
    expect(isAllowedVendorHostOrigin(win.location.origin, win)).toBe(true);
    for (const origin of VENDOR_PROD_ORIGINS) {
      expect(isAllowedVendorHostOrigin(origin, win)).toBe(false);
      expect(decodeVendorHostMessage({
        origin,
        data: { source: HOST_SOURCE, channel: "close", payload: { action: "close" } },
      }, win).reason).toBe("origin_rejected");
    }
  });

  it("allows same-origin /m/host src and rejects off-site / token / harness", () => {
    const win = createFakeWindow();
    expect(isAllowedVendorHostSrc(childSrcForVendor("wechat"), win)).toBe(true);
    expect(isAllowedVendorHostSrc(childSrcForVendor("dingtalk"), win)).toBe(true);
    expect(isAllowedVendorHostSrc("/m/host?embedded=true&host=wechat-prod-stub", win)).toBe(true);
    expect(isAllowedVendorHostSrc("https://evil.example/m/host", win)).toBe(false);
    expect(isAllowedVendorHostSrc("https://servicewechat.com/m/host", win)).toBe(false);
    expect(isAllowedVendorHostSrc("https://h5.dingtalk.com/m/host", win)).toBe(false);
    expect(isAllowedVendorHostSrc("/m/host?access_token=leak", win)).toBe(false);
    expect(isAllowedVendorHostSrc("/m/wx-dt-harness", win)).toBe(false);
    expect(isAllowedVendorHostSrc("/p3/ledgers", win)).toBe(false);
    expect(isSafeHostPath("/m/wx-dt-harness")).toBe(false);
  });

  it("parent → stub uses nexus-host and rejects unknown channels / secrets", () => {
    const win = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=dingtalk-prod-stub",
    });
    const child = { inbox: [], postMessage(data, origin) { this.inbox.push({ data, origin }); } };
    const sent = postToVendorHost(child, "route", { action: "navigate", path: "/m/host" }, win);
    expect(sent.ok).toBe(true);
    expect(child.inbox[0].data.source).toBe(HOST_SOURCE);
    expect(child.inbox[0].origin).toBe(win.location.origin);
    expect(postToVendorHost(child, "eval", {}, win).reason).toBe("channel_not_allowed");
    expect(encodeHostMessage({
      source: HOST_SOURCE,
      channel: "login",
      payload: { action: "done", access_token: "leak" },
    }).reason).toBe("secret_key");
  });

  it("child handshakes login/ready toward both vendor stubs", () => {
    for (const vendor of ["wechat", "dingtalk"]) {
      const win = createFakeWindow({
        embedded: true,
        search: `?embedded=true&host=${vendor}-prod-stub`,
      });
      const uninstall = installHostBridge({
        navigate() {
          return { ok: true };
        },
      }, win);
      expect(win.__parentInbox[0].data).toMatchObject({
        source: H5_SOURCE,
        channel: "login",
        payload: { action: "ready" },
      });
      expect(win.__parentInbox[0].targetOrigin).toBe(win.location.origin);
      const inbound = decodeVendorHostMessage({
        origin: win.location.origin,
        data: win.__parentInbox[0].data,
      }, win);
      expect(inbound.ok).toBe(true);
      uninstall();
    }
  });

  it("walks login/route/file/close on the stub envelope", () => {
    expect(VENDOR_STUB_WALK.map(([channel]) => channel)).toEqual(["login", "route", "file", "close"]);
    const win = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=wechat-prod-stub",
    });
    const child = { inbox: [], postMessage(data, origin) { this.inbox.push({ data, origin }); } };
    for (const [channel, payload] of VENDOR_STUB_WALK) {
      expect(postToVendorHost(child, channel, payload, win).ok).toBe(true);
    }
    expect(child.inbox.map((item) => item.data.channel)).toEqual(["login", "route", "file", "close"]);
  });
});
