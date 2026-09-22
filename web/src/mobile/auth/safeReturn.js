/**
 * F011 / F013: keep a same-origin H5 return path. Never put tokens or tenant
 * identity in the URL. Off-site and protocol-relative targets are rejected.
 */
export const DEFAULT_MOBILE_HOME = "/m/p3/home";
export const PENDING_REDIRECT_KEY = "nexus.g15.pending-redirect";

export function isSafeMobileReturn(path) {
  if (typeof path !== "string") {
    return false;
  }
  const value = path.trim();
  if (!value.startsWith("/m/") && value !== "/m") {
    return false;
  }
  if (value.startsWith("//") || value.includes("://")) {
    return false;
  }
  if (value.includes("\\")) {
    return false;
  }
  if (/[\u0000-\u001F]/.test(value)) {
    return false;
  }
  if (/(access_token|id_token|refresh_token|token=)/i.test(value)) {
    return false;
  }
  if (/(^|[?&#])tenant_id=/i.test(value)) {
    return false;
  }
  return true;
}

export function safeMobileReturn(path, fallback = DEFAULT_MOBILE_HOME) {
  return isSafeMobileReturn(path) ? path.trim() : fallback;
}

export function savePendingRedirect({ state, returnTo } = {}) {
  const payload = {
    state: state || "",
    returnTo: safeMobileReturn(returnTo),
    savedAt: Date.now(),
  };
  sessionStorage.setItem(PENDING_REDIRECT_KEY, JSON.stringify(payload));
  return payload;
}

export function loadPendingRedirect() {
  try {
    const raw = sessionStorage.getItem(PENDING_REDIRECT_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw);
    return {
      state: parsed.state || "",
      returnTo: safeMobileReturn(parsed.returnTo),
      savedAt: parsed.savedAt || 0,
    };
  } catch {
    return null;
  }
}

export function clearPendingRedirect() {
  sessionStorage.removeItem(PENDING_REDIRECT_KEY);
}

export function consumePendingRedirect(state) {
  const pending = loadPendingRedirect();
  if (!pending) {
    return { ok: true, returnTo: DEFAULT_MOBILE_HOME, reason: "none" };
  }
  if (pending.state && state && pending.state !== state) {
    return { ok: false, returnTo: DEFAULT_MOBILE_HOME, reason: "state_mismatch" };
  }
  clearPendingRedirect();
  return { ok: true, returnTo: pending.returnTo, reason: "matched" };
}
