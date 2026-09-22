/**
 * F014 host-message contract (not accepted this slice).
 * Only login / route / file / close are exposed. No WeChat/DingTalk branches.
 */
export const HOST_CHANNELS = Object.freeze(["login", "route", "file", "close"]);

export function hostCapabilities() {
  const embedded = typeof window !== "undefined" && window.parent && window.parent !== window;
  return {
    host: embedded ? "embedded" : "mobile-browser",
    supported: embedded ? HOST_CHANNELS : [],
    unsupported: embedded ? [] : HOST_CHANNELS,
    accepted: false,
  };
}

export function postHost(channel, payload) {
  if (!HOST_CHANNELS.includes(channel)) {
    return { ok: false, reason: "channel_not_allowed" };
  }
  const caps = hostCapabilities();
  if (caps.host !== "embedded" || typeof window.parent?.postMessage !== "function") {
    return { ok: false, reason: "no_host", fallback: "web" };
  }
  window.parent.postMessage({ source: "nexus-h5", channel, payload: payload || {} }, window.location.origin);
  return { ok: true };
}
