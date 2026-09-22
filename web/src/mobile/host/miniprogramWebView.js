/**
 * Simulated 小程序 web-view parent contract.
 *
 * Same HostBridgePort channels (login / route / file / close) over
 * same-origin postMessage. This is a harness-shaped parent, not a
 * WeChat or DingTalk production walk. Those vendor origins stay denied.
 */
import {
  HOST_CHANNELS,
  H5_SOURCE,
  HOST_SOURCE,
  MP_WEBVIEW_HOST,
  MP_WEBVIEW_QUERY,
  OTHER_HOSTS_OPEN,
  allowedHostOrigin,
  decodeHostMessage,
  isAllowedOrigin,
  isSafeHostPath,
  postToFrame,
} from "./hostBridge.js";

export { MP_WEBVIEW_HOST, MP_WEBVIEW_QUERY };

export const MP_WEBVIEW_CHILD_SRC = `/m/host?embedded=true&host=${MP_WEBVIEW_QUERY}`;

export const FOREIGN_WEBVIEW_ORIGINS = Object.freeze([
  "https://servicewechat.com",
  "https://open.weixin.qq.com",
  "https://h5.dingtalk.com",
  "https://evil.example",
]);

export function allowedWebViewOrigins(win) {
  const origin = allowedHostOrigin(win);
  return origin ? Object.freeze([origin]) : Object.freeze([]);
}

export function isAllowedMiniProgramOrigin(origin, win) {
  if (FOREIGN_WEBVIEW_ORIGINS.includes(origin)) {
    return false;
  }
  return isAllowedOrigin(origin, win);
}

export function isAllowedMiniProgramChannel(channel) {
  return HOST_CHANNELS.includes(channel);
}

export function isAllowedWebViewSrc(src, win) {
  if (!src || typeof src !== "string") {
    return false;
  }
  const origin = allowedHostOrigin(win);
  let url;
  try {
    url = new URL(src, origin || "http://127.0.0.1");
  } catch {
    return false;
  }
  if (origin && url.origin !== origin) {
    return false;
  }
  const path = `${url.pathname}${url.search}`;
  if (url.pathname === "/m/mp-webview-harness" || url.pathname === "/m/host-harness") {
    return false;
  }
  if (!url.pathname.startsWith("/m/")) {
    return false;
  }
  return isSafeHostPath(path);
}

export function decodeMiniProgramMessage(event, win) {
  if (!event || !isAllowedMiniProgramOrigin(event.origin, win)) {
    return { ok: false, reason: "origin_rejected" };
  }
  const decoded = decodeHostMessage(event, win);
  if (!decoded.ok) {
    return decoded;
  }
  if (decoded.envelope.source !== H5_SOURCE && decoded.envelope.source !== HOST_SOURCE) {
    return { ok: false, reason: "source_not_allowed" };
  }
  if (!isAllowedMiniProgramChannel(decoded.envelope.channel)) {
    return { ok: false, reason: "channel_not_allowed" };
  }
  return decoded;
}

export function postToWebView(frameWindow, channel, payload, win) {
  if (!isAllowedMiniProgramChannel(channel)) {
    return { ok: false, reason: "channel_not_allowed" };
  }
  return postToFrame(frameWindow, channel, payload, win);
}

export function describeMpWebViewContract(win) {
  return {
    host: MP_WEBVIEW_HOST,
    transport: "postMessage",
    channels: [...HOST_CHANNELS],
    originAllowlist: [...allowedWebViewOrigins(win)],
    productionAccepted: false,
    vendors: Object.freeze({ wechat: false, dingtalk: false }),
    leftover: [...OTHER_HOSTS_OPEN],
  };
}
