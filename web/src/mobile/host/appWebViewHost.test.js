import { afterEach, describe, expect, it } from "vitest";
import { createFakeWindow } from "./fakeWindow.js";
import {
  APP_WEBVIEW_OBJECT,
  H5_SOURCE,
  HOST_SOURCE,
  hostCapabilities,
  postHost,
  resetHostBridgeLog,
} from "./hostBridge.js";
import {
  APP_WEBVIEW_WALK,
  applyAppWebViewInbound,
  attachAppWebViewBridge,
  proveSameEnvelope,
  sameEnvelopeContract,
  walkAppWebViewOutbound,
} from "./appWebViewHost.js";

afterEach(() => {
  resetHostBridgeLog();
});

describe("APP WebView web harness (not a native binary)", () => {
  it("detects the injected NexusAppWebView host and leaves the native leftover open", () => {
    const win = createFakeWindow({ appWebView: true });
    const caps = hostCapabilities(win);
    expect(win[APP_WEBVIEW_OBJECT]).toBeTruthy();
    expect(caps.host).toBe("app-webview");
    expect(caps.nativeBinary).toBe(false);
    expect(caps.otherHostsOpen).toEqual(["APP WebView", "WeChat/DingTalk 小程序 production"]);
  });

  it("posts the same F014 envelope to NexusAppWebView instead of window.parent", () => {
    const win = createFakeWindow({ appWebView: true });
    const posted = postHost("file", { action: "open", fileId: "demo-file-id" }, win);
    expect(posted.ok).toBe(true);
    expect(posted.envelope.source).toBe(H5_SOURCE);
    expect(win.__appWebViewInbox).toHaveLength(1);
    expect(win.__parentInbox).toHaveLength(0);
    expect(win.__appWebViewInbox[0].data.payload.fileId).toBe("demo-file-id");
  });

  it("walks login/route/file/close both ways on the same envelope", () => {
    const outboundWin = createFakeWindow({ appWebView: true });
    const outbound = walkAppWebViewOutbound(outboundWin);
    expect(outbound.every((item) => item.ok)).toBe(true);
    expect(outboundWin.__appWebViewInbox.map((item) => item.data.channel)).toEqual([
      "login",
      "route",
      "file",
      "close",
    ]);

    const inbound = applyAppWebViewInbound(createFakeWindow({ appWebView: true }));
    expect(inbound.seen).toEqual(expect.arrayContaining([
      "login:request",
      "route:navigate",
      "file:open",
      "close",
    ]));
    inbound.uninstall();
  });

  it("matches the mobile-browser iframe envelope for every channel", () => {
    const iframe = createFakeWindow({ embedded: true });
    for (const [channel, payload] of APP_WEBVIEW_WALK) {
      const proof = proveSameEnvelope(channel, payload, createFakeWindow({ appWebView: true }), iframe);
      expect(proof.same).toBe(true);
      expect(proof.appPosted.envelope.source).toBe(H5_SOURCE);
      expect(sameEnvelopeContract(proof.encoded.envelope, proof.decoded.envelope)).toBe(true);
    }
  });

  it("lets the simulated native host deliver nexus-host envelopes", () => {
    const child = {};
    const bridge = attachAppWebViewBridge(child);
    expect(bridge.nativeBinary).toBe(false);
    child[APP_WEBVIEW_OBJECT].postMessage({
      source: HOST_SOURCE,
      channel: "close",
      requestId: "h5-sim",
      payload: { action: "close" },
    }, "http://127.0.0.1:5173");
    expect(bridge.inbox[0].data.channel).toBe("close");
  });
});
