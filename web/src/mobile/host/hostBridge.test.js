import { afterEach, describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createFakeWindow } from "./fakeWindow.js";
import {
  HOST_CHANNELS,
  H5_SOURCE,
  HOST_SOURCE,
  applyWebFallback,
  decodeHostMessage,
  encodeHostMessage,
  hostCapabilities,
  installHostBridge,
  isSafeHostPath,
  postHost,
  postToFrame,
  resetHostBridgeLog,
  sanitizeHostPayload,
} from "./hostBridge.js";

const source = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "hostBridge.js"), "utf8");

afterEach(() => {
  resetHostBridgeLog();
});

describe("F014 HostBridgePort contract", () => {
  it("exposes only login/route/file/close", () => {
    expect([...HOST_CHANNELS]).toEqual(["login", "route", "file", "close"]);
  });

  it("does not branch on WeChat or DingTalk", () => {
    expect(source).not.toMatch(/isWeChat|isDingTalk|wx\.miniProgram|tt\.miniProgram/i);
  });

  it("marks the postMessage contract accepted and leaves other hosts open", () => {
    const caps = hostCapabilities(createFakeWindow());
    expect(caps.accepted).toBe(true);
    expect(caps.transport).toBe("postMessage");
    expect(caps.host).toBe("mobile-browser");
    expect(caps.fallback).toBe("web");
    expect(caps.otherHostsOpen).toEqual(["APP WebView", "WeChat/DingTalk 小程序 production"]);
    expect(caps.productionAccepted).toBe(false);
    expect(caps.nativeBinary).toBe(false);
  });

  it("treats an injected NexusAppWebView as the APP WebView host", () => {
    const caps = hostCapabilities(createFakeWindow({ appWebView: true }));
    expect(caps.host).toBe("app-webview");
    expect(caps.fallback).toBeNull();
    expect(caps.accepted).toBe(true);
    expect(caps.nativeBinary).toBe(false);
  });

  it("detects embedded via parent or query", () => {
    expect(hostCapabilities(createFakeWindow({ embedded: true })).host).toBe("embedded");
    expect(hostCapabilities(createFakeWindow({ embedded: true })).fallback).toBeNull();
  });

  it("names the simulated mini-program web-view host from query", () => {
    const win = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=mp-webview",
    });
    expect(hostCapabilities(win).host).toBe("miniprogram-webview-sim");
    expect(hostCapabilities(win).fallback).toBeNull();
  });

  it("names WeChat / DingTalk production stubs from query and leaves production open", () => {
    const wechat = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=wechat-prod-stub",
    });
    const dingtalk = createFakeWindow({
      embedded: true,
      search: "?embedded=true&host=dingtalk-prod-stub",
    });
    expect(hostCapabilities(wechat).host).toBe("wechat-prod-stub");
    expect(hostCapabilities(dingtalk).host).toBe("dingtalk-prod-stub");
    expect(hostCapabilities(wechat).productionAccepted).toBe(false);
    expect(hostCapabilities(dingtalk).fallback).toBeNull();
  });
});

describe("payload and origin guards", () => {
  it("rejects secret keys and Bearer values", () => {
    expect(sanitizeHostPayload({ access_token: "abc" }).ok).toBe(false);
    expect(sanitizeHostPayload({ tenant_id: "t1" }).ok).toBe(false);
    expect(sanitizeHostPayload({ note: "Bearer abc" }).ok).toBe(false);
    expect(sanitizeHostPayload({ action: "ready" }).ok).toBe(true);
  });

  it("rejects unknown channel, nested payload, and file bytes", () => {
    expect(encodeHostMessage({ source: H5_SOURCE, channel: "eval", payload: {} }).reason).toBe("channel_not_allowed");
    expect(encodeHostMessage({ source: H5_SOURCE, channel: "file", payload: { nested: { a: 1 } } }).reason).toBe("nested_payload");
    expect(encodeHostMessage({
      source: H5_SOURCE,
      channel: "file",
      payload: { action: "upload", content_base64: "AAAA" },
    }).reason).toBe("file_content_forbidden");
  });

  it("rejects off-site and token-bearing routes", () => {
    expect(isSafeHostPath("https://evil.example/m/p3/home")).toBe(false);
    expect(isSafeHostPath("/m/p3/files?access_token=abc")).toBe(false);
    expect(isSafeHostPath("/m/host-harness")).toBe(false);
    expect(isSafeHostPath("/m/mp-webview-harness")).toBe(false);
    expect(isSafeHostPath("/m/app-webview-harness")).toBe(false);
    expect(isSafeHostPath("/m/wx-dt-harness")).toBe(false);
    expect(isSafeHostPath("/m/p3/files?tab=recent")).toBe(true);
    expect(encodeHostMessage({
      source: HOST_SOURCE,
      channel: "route",
      payload: { action: "navigate", path: "/p3/ledgers" },
    }).reason).toBe("unsafe_path");
  });

  it("rejects the wrong origin and unknown source", () => {
    const win = createFakeWindow();
    expect(decodeHostMessage({
      origin: "https://evil.example",
      data: { source: HOST_SOURCE, channel: "close", payload: { action: "close" } },
    }, win).reason).toBe("origin_rejected");
    expect(decodeHostMessage({
      origin: win.location.origin,
      data: { source: "wechat", channel: "close", payload: { action: "close" } },
    }, win).reason).toBe("source_not_allowed");
  });

  it("round-trips a same-origin host envelope", () => {
    const win = createFakeWindow();
    const encoded = encodeHostMessage({
      source: HOST_SOURCE,
      channel: "route",
      payload: { action: "navigate", path: "/m/p3/files?tab=recent" },
    });
    expect(encoded.ok).toBe(true);
    const decoded = decodeHostMessage({ origin: win.location.origin, data: encoded.envelope }, win);
    expect(decoded.ok).toBe(true);
    expect(decoded.envelope.payload.path).toBe("/m/p3/files?tab=recent");
  });
});

describe("postMessage transport", () => {
  it("falls back to web when there is no parent host", () => {
    const win = createFakeWindow({ embedded: false });
    const result = postHost("close", { action: "close" }, win);
    expect(result.ok).toBe(false);
    expect(result.reason).toBe("no_host");
    expect(result.fallback).toBe("web");
    expect(win.__parentInbox).toHaveLength(0);
  });

  it("posts only the four channels to parent when embedded", () => {
    const win = createFakeWindow({ embedded: true });
    const posted = postHost("file", { action: "open", fileId: "demo-file-id", fileName: "note.txt" }, win);
    expect(posted.ok).toBe(true);
    expect(win.__parentInbox).toHaveLength(1);
    expect(win.__parentInbox[0].targetOrigin).toBe(win.location.origin);
    expect(win.__parentInbox[0].data.source).toBe(H5_SOURCE);
    expect(win.__parentInbox[0].data.channel).toBe("file");
    expect(win.__parentInbox[0].data.payload.fileId).toBe("demo-file-id");
    expect(posted.envelope.requestId).toMatch(/^h5-/);
  });

  it("host → iframe uses nexus-host source", () => {
    const win = createFakeWindow();
    const child = { inbox: [], postMessage(data, origin) { this.inbox.push({ data, origin }); } };
    const sent = postToFrame(child, "login", { action: "request", returnTo: "/m/host" }, win);
    expect(sent.ok).toBe(true);
    expect(child.inbox[0].data.source).toBe(HOST_SOURCE);
    expect(child.inbox[0].origin).toBe(win.location.origin);
  });
});

describe("inbound apply + handshake", () => {
  it("applies web fallback for each channel", () => {
    const navigated = [];
    const port = {
      navigate(path) {
        navigated.push(path);
        return { ok: true, fallback: "web", path };
      },
      close() {
        navigated.push("close");
        return true;
      },
    };
    applyWebFallback("login", { action: "session_expired", returnTo: "/m/host" }, port);
    applyWebFallback("route", { action: "navigate", path: "/m/p3/files?tab=recent" }, port);
    applyWebFallback("file", { action: "open", fileId: "demo-file-id" }, port);
    applyWebFallback("close", { action: "close" }, port);
    expect(navigated).toEqual([
      "/m/login?return=%2Fm%2Fhost",
      "/m/p3/files?tab=recent",
      "/m/p3/files/demo-file-id",
      "close",
    ]);
  });

  it("installs a listener, handshakes with login/ready, and ignores H5 echoes", () => {
    const win = createFakeWindow({ embedded: true });
    const seen = [];
    const uninstall = installHostBridge({
      navigate(path) {
        seen.push(path);
        return { ok: true, path };
      },
      onHostMessage(channel, payload) {
        seen.push(`${channel}:${payload.action || payload.path || ""}`);
        return { ok: true };
      },
    }, win);
    expect(win.__parentInbox[0].data).toMatchObject({
      source: H5_SOURCE,
      channel: "login",
      payload: { action: "ready" },
    });
    win.__dispatch({
      origin: win.location.origin,
      data: { source: H5_SOURCE, channel: "route", payload: { action: "navigate", path: "/m/host" } },
    });
    expect(seen).toEqual([]);
    win.__dispatch({
      origin: win.location.origin,
      data: { source: HOST_SOURCE, channel: "route", payload: { action: "navigate", path: "/m/host" } },
    });
    expect(seen).toEqual(["route:navigate"]);
    uninstall();
    expect(win.__listeners).toHaveLength(0);
  });
});
