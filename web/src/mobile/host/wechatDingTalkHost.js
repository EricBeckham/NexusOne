/**
 * WeChat / DingTalk production host contract stub.
 *
 * Same HostBridgePort channels (login / route / file / close) over
 * same-origin postMessage. This is a harness-shaped parent for the
 * leftover production-host envelope. It does not accept vendor
 * production: vendor origins stay denied, productionAccepted stays
 * false, and no vendor JSAPI is loaded.
 */
import {
  HOST_CHANNELS,
  H5_SOURCE,
  HOST_SOURCE,
  OTHER_HOSTS_OPEN,
  VENDOR_DINGTALK_HOST,
  VENDOR_DINGTALK_QUERY,
  VENDOR_WECHAT_HOST,
  VENDOR_WECHAT_QUERY,
  allowedHostOrigin,
  decodeHostMessage,
  isAllowedOrigin,
  isSafeHostPath,
  postToFrame,
} from "./hostBridge.js";

export {
  VENDOR_DINGTALK_HOST,
  VENDOR_DINGTALK_QUERY,
  VENDOR_WECHAT_HOST,
  VENDOR_WECHAT_QUERY,
};

export const VENDOR_STUB_VENDORS = Object.freeze(["wechat", "dingtalk"]);

export const VENDOR_PROD_ORIGINS = Object.freeze([
  "https://servicewechat.com",
  "https://open.weixin.qq.com",
  "https://h5.dingtalk.com",
  "https://n.dingtalk.com",
  "https://aflow.dingtalk.com",
  "https://evil.example",
]);

export const VENDOR_STUB_WALK = Object.freeze([
  Object.freeze(["login", Object.freeze({ action: "request", returnTo: "/m/host" })]),
  Object.freeze(["route", Object.freeze({ action: "navigate", path: "/m/p3/files?tab=recent" })]),
  Object.freeze(["file", Object.freeze({ action: "open", fileId: "demo-file-id" })]),
  Object.freeze(["close", Object.freeze({ action: "close" })]),
]);

export function childSrcForVendor(vendor) {
  const query = vendor === "dingtalk" ? VENDOR_DINGTALK_QUERY : VENDOR_WECHAT_QUERY;
  return `/m/host?embedded=true&host=${query}`;
}

export function hostKindForVendor(vendor) {
  return vendor === "dingtalk" ? VENDOR_DINGTALK_HOST : VENDOR_WECHAT_HOST;
}

export function allowedVendorHostOrigins(win) {
  const origin = allowedHostOrigin(win);
  return origin ? Object.freeze([origin]) : Object.freeze([]);
}

export function isAllowedVendorHostOrigin(origin, win) {
  if (VENDOR_PROD_ORIGINS.includes(origin)) {
    return false;
  }
  return isAllowedOrigin(origin, win);
}

export function isAllowedVendorHostChannel(channel) {
  return HOST_CHANNELS.includes(channel);
}

export function isAllowedVendorHostSrc(src, win) {
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
  if (!url.pathname.startsWith("/m/")) {
    return false;
  }
  const path = `${url.pathname}${url.search}`;
  return isSafeHostPath(path);
}

export function decodeVendorHostMessage(event, win) {
  if (!event || !isAllowedVendorHostOrigin(event.origin, win)) {
    return { ok: false, reason: "origin_rejected" };
  }
  const decoded = decodeHostMessage(event, win);
  if (!decoded.ok) {
    return decoded;
  }
  if (decoded.envelope.source !== H5_SOURCE && decoded.envelope.source !== HOST_SOURCE) {
    return { ok: false, reason: "source_not_allowed" };
  }
  if (!isAllowedVendorHostChannel(decoded.envelope.channel)) {
    return { ok: false, reason: "channel_not_allowed" };
  }
  return decoded;
}

export function postToVendorHost(frameWindow, channel, payload, win) {
  if (!isAllowedVendorHostChannel(channel)) {
    return { ok: false, reason: "channel_not_allowed" };
  }
  return postToFrame(frameWindow, channel, payload, win);
}

export function describeVendorProdContract(win, vendor = "wechat") {
  const kind = hostKindForVendor(vendor);
  return {
    host: kind,
    vendor,
    transport: "postMessage",
    channels: [...HOST_CHANNELS],
    originAllowlist: [...allowedVendorHostOrigins(win)],
    productionAccepted: false,
    stub: true,
    vendors: Object.freeze({
      wechat: Object.freeze({ stub: true, productionAccepted: false }),
      dingtalk: Object.freeze({ stub: true, productionAccepted: false }),
    }),
    leftover: [...OTHER_HOSTS_OPEN],
  };
}
