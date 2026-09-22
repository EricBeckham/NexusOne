import { afterEach, describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "./fakeWindow.js";
import {
  H5_SOURCE,
  HOST_CHANNELS,
  HOST_SOURCE,
  NATIVE_ANDROID_OBJECT,
  NATIVE_BRIDGE_HOST,
  NATIVE_IOS_HANDLER,
  detectHostKind,
  encodeHostMessage,
  hostCapabilities,
  isSafeHostPath,
  postHost,
  resetHostBridgeLog,
} from "./hostBridge.js";
import {
  NATIVE_BRIDGE_WALK,
  NOT_CLAIMED_VS_SIMULATIONS,
  applyNativeBridgeInbound,
  attachNativeBridge,
  childSrcForNativeBridge,
  decodeNativeBridgeMessage,
  describeNativeBridgeContract,
  isAllowedNativeBridgeChannel,
  isAllowedNativeBridgeOrigin,
  isAllowedNativeBridgeSrc,
  postToNativeBridgeChild,
  walkNativeBridgeOutbound,
} from "./nativeBridgeStub.js";

const source = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "nativeBridgeStub.js"), "utf8");
const hostBridgeSource = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "hostBridge.js"), "utf8");

afterEach(() => {
  resetHostBridgeLog();
});

describe("APP WebView native-bridge stub (not a store-ready package)", () => {
  it("reuses the F014 envelope and does not claim a store package", () => {
    expect([...HOST_CHANNELS]).toEqual(["login", "route", "file", "close"]);
    expect(source).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram/i);
    expect(source).not.toMatch(/storeReady:\s*true|store-ready APP accepted/i);
    expect(hostBridgeSource).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram/i);
    expect(isAllowedNativeBridgeChannel("login")).toBe(true);
    expect(isAllowedNativeBridgeChannel("eval")).toBe(false);
  });

  it("names the stub host and leaves store-ready / production open", () => {
    const ios = createFakeWindow({ nativeBridge: "ios" });
    const android = createFakeWindow({ nativeBridge: "android" });
    expect(detectHostKind(ios)).toBe(NATIVE_BRIDGE_HOST);
    expect(detectHostKind(android)).toBe(NATIVE_BRIDGE_HOST);
    const caps = hostCapabilities(ios);
    expect(caps.host).toBe(NATIVE_BRIDGE_HOST);
    expect(caps.accepted).toBe(true);
    expect(caps.nativeBinary).toBe(false);
    expect(caps.storeReady).toBe(false);
    expect(caps.storePackage).toBe(false);
    expect(caps.productionAccepted).toBe(false);
    expect(caps.otherHostsOpen).toEqual(["APP WebView", "WeChat/DingTalk 小程序 production"]);
    const described = describeNativeBridgeContract(ios, "ios");
    expect(described.stub).toBe(true);
    expect(described.storeReady).toBe(false);
    expect(described.iosProject).toBe(false);
    expect(described.androidProject).toBe(false);
    expect(described.notClaimed).toEqual(NOT_CLAIMED_VS_SIMULATIONS);
    expect(described.platforms.ios.handler).toBe(`webkit.messageHandlers.${NATIVE_IOS_HANDLER}`);
    expect(describeNativeBridgeContract(android, "android").platforms.android.handler).toBe(NATIVE_ANDROID_OBJECT);
  });

  it("posts the same F014 envelope to iOS / Android stubs instead of NexusAppWebView", () => {
    const ios = createFakeWindow({ nativeBridge: "ios" });
    const posted = postHost("file", { action: "open", fileId: "demo-file-id" }, ios);
    expect(posted.ok).toBe(true);
    expect(posted.envelope.source).toBe(H5_SOURCE);
    expect(ios.__nativeInbox).toHaveLength(1);
    expect(ios.__nativeInbox[0].platform).toBe("ios");
    expect(ios.__nativeInbox[0].data.payload.fileId).toBe("demo-file-id");
    expect(ios.__parentInbox).toHaveLength(0);
    expect(ios.__appWebViewInbox).toHaveLength(0);

    const android = createFakeWindow({ nativeBridge: "android" });
    expect(postHost("close", { action: "close" }, android).ok).toBe(true);
    expect(android.__nativeInbox[0].platform).toBe("android");
    expect(android.__nativeInbox[0].data.channel).toBe("close");
    expect(typeof android.__nativeInbox[0].raw).toBe("string");
  });

  it("walks login/route/file/close both ways on the stub", () => {
    const outbound = walkNativeBridgeOutbound(createFakeWindow({ nativeBridge: "ios" }));
    expect(outbound.every((item) => item.ok)).toBe(true);
    const inbound = applyNativeBridgeInbound(createFakeWindow({ nativeBridge: "android" }));
    expect(inbound.seen).toEqual(expect.arrayContaining([
      "login:request",
      "route:navigate",
      "file:open",
      "close",
    ]));
    expect(inbound.caps.storeReady).toBe(false);
    inbound.uninstall();
    expect(NATIVE_BRIDGE_WALK.map(([channel]) => channel)).toEqual(["login", "route", "file", "close"]);
  });

  it("allows only the H5 origin and same-origin /m/host src", () => {
    const win = createFakeWindow({ nativeBridge: "ios" });
    expect(isAllowedNativeBridgeOrigin(win.location.origin, win)).toBe(true);
    expect(decodeNativeBridgeMessage({
      origin: "https://evil.example",
      data: { source: HOST_SOURCE, channel: "close", payload: { action: "close" } },
    }, win).reason).toBe("origin_rejected");
    expect(isAllowedNativeBridgeSrc(childSrcForNativeBridge(), win)).toBe(true);
    expect(isAllowedNativeBridgeSrc("https://evil.example/m/host", win)).toBe(false);
    expect(isAllowedNativeBridgeSrc("/m/native-bridge-harness", win)).toBe(false);
    expect(isAllowedNativeBridgeSrc("/m/app-webview-harness", win)).toBe(false);
    expect(isAllowedNativeBridgeSrc("/m/wx-dt-harness", win)).toBe(false);
    expect(isAllowedNativeBridgeSrc("/m/mp-webview-harness", win)).toBe(false);
    expect(isSafeHostPath("/m/native-bridge-harness")).toBe(false);
    expect(postToNativeBridgeChild(
      { inbox: [], postMessage(data, origin) { this.inbox.push({ data, origin }); } },
      "eval",
      {},
      win,
    ).reason).toBe("channel_not_allowed");
    expect(encodeHostMessage({
      source: HOST_SOURCE,
      channel: "login",
      payload: { action: "done", access_token: "leak" },
    }).reason).toBe("secret_key");
  });

  it("keeps attach honesty flags off and does not invent a store package", () => {
    const child = {};
    const ios = attachNativeBridge(child, "ios");
    expect(ios.storeReady).toBe(false);
    expect(ios.nativeBinary).toBe(false);
    expect(ios.storePackage).toBe(false);
    expect(child.webkit.messageHandlers[NATIVE_IOS_HANDLER].storeReady).toBe(false);
    const androidChild = {};
    const android = attachNativeBridge(androidChild, "android");
    expect(android.kind).toBe("android-jsinterface-stub");
    expect(androidChild[NATIVE_ANDROID_OBJECT].storePackage).toBe(false);
  });
});
