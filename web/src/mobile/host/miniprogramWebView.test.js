import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "./fakeWindow.js";
import {
  HOST_CHANNELS,
  H5_SOURCE,
  HOST_SOURCE,
  MP_WEBVIEW_HOST,
  detectHostKind,
  encodeHostMessage,
  hostCapabilities,
  installHostBridge,
  isSafeHostPath,
} from "./hostBridge.js";
import {
  FOREIGN_WEBVIEW_ORIGINS,
  MP_WEBVIEW_CHILD_SRC,
  decodeMiniProgramMessage,
  describeMpWebViewContract,
  isAllowedMiniProgramChannel,
  isAllowedMiniProgramOrigin,
  isAllowedWebViewSrc,
  postToWebView,
} from "./miniprogramWebView.js";

const source = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "miniprogramWebView.js"), "utf8");

describe("simulated 小程序 web-view parent", () => {
  it("reuses HostBridgePort channels and does not branch on vendor SDKs", () => {
    expect([...HOST_CHANNELS]).toEqual(["login", "route", "file", "close"]);
    expect(source).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
    expect(isAllowedMiniProgramChannel("login")).toBe(true);
    expect(isAllowedMiniProgramChannel("eval")).toBe(false);
  });

  it("names the simulated host and leaves production vendors open", () => {
    const win = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=mp-webview",
    });
    expect(detectHostKind(win)).toBe(MP_WEBVIEW_HOST);
    const caps = hostCapabilities(win);
    expect(caps.host).toBe(MP_WEBVIEW_HOST);
    expect(caps.accepted).toBe(true);
    expect(caps.productionAccepted).toBe(false);
    expect(caps.otherHostsOpen).toEqual(["APP WebView", "WeChat/DingTalk 小程序 production"]);
    const described = describeMpWebViewContract(win);
    expect(described.vendors).toEqual({ wechat: false, dingtalk: false });
    expect(described.originAllowlist).toEqual([win.location.origin]);
  });

  it("allows only the H5 origin; WeChat / DingTalk / evil stay denied", () => {
    const win = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=mp-webview",
    });
    expect(isAllowedMiniProgramOrigin(win.location.origin, win)).toBe(true);
    for (const origin of FOREIGN_WEBVIEW_ORIGINS) {
      expect(isAllowedMiniProgramOrigin(origin, win)).toBe(false);
      expect(decodeMiniProgramMessage({
        origin,
        data: { source: HOST_SOURCE, channel: "close", payload: { action: "close" } },
      }, win).reason).toBe("origin_rejected");
    }
  });

  it("allows same-origin /m/host web-view src and rejects off-site / token / harness", () => {
    const win = createFakeWindow();
    expect(isAllowedWebViewSrc(MP_WEBVIEW_CHILD_SRC, win)).toBe(true);
    expect(isAllowedWebViewSrc("/m/host?embedded=true&host=mp-webview", win)).toBe(true);
    expect(isAllowedWebViewSrc("https://evil.example/m/host", win)).toBe(false);
    expect(isAllowedWebViewSrc("https://servicewechat.com/m/host", win)).toBe(false);
    expect(isAllowedWebViewSrc("/m/host?access_token=leak", win)).toBe(false);
    expect(isAllowedWebViewSrc("/m/mp-webview-harness", win)).toBe(false);
    expect(isAllowedWebViewSrc("/p3/ledgers", win)).toBe(false);
    expect(isSafeHostPath("/m/mp-webview-harness")).toBe(false);
  });

  it("parent → web-view uses nexus-host and rejects unknown channels / secrets", () => {
    const win = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=mp-webview",
    });
    const child = { inbox: [], postMessage(data, origin) { this.inbox.push({ data, origin }); } };
    const sent = postToWebView(child, "route", { action: "navigate", path: "/m/host" }, win);
    expect(sent.ok).toBe(true);
    expect(child.inbox[0].data.source).toBe(HOST_SOURCE);
    expect(child.inbox[0].origin).toBe(win.location.origin);
    expect(postToWebView(child, "eval", {}, win).reason).toBe("channel_not_allowed");
    expect(encodeHostMessage({
      source: HOST_SOURCE,
      channel: "login",
      payload: { action: "done", access_token: "leak" },
    }).reason).toBe("secret_key");
  });

  it("child handshakes login/ready toward the simulated parent", () => {
    const win = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=mp-webview",
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
    const inbound = decodeMiniProgramMessage({
      origin: win.location.origin,
      data: win.__parentInbox[0].data,
    }, win);
    expect(inbound.ok).toBe(true);
    uninstall();
  });
});
