/**
 * Hold the H5 PKCE verifier in sessionStorage. Never put it in the URL.
 */
import { DEFAULT_MOBILE_HOME, savePendingRedirect, consumePendingRedirect, safeMobileReturn } from "./safeReturn.js";

export const PENDING_PKCE_KEY = "nexus.g15.pending-pkce";

export function savePendingPkce({ state, verifier, challenge, returnTo } = {}) {
  const payload = {
    state: state || "",
    verifier: verifier || "",
    challenge: challenge || "",
    method: "S256",
    returnTo: safeMobileReturn(returnTo),
    savedAt: Date.now(),
  };
  sessionStorage.setItem(PENDING_PKCE_KEY, JSON.stringify(payload));
  savePendingRedirect({ state: payload.state, returnTo: payload.returnTo });
  return payload;
}

export function loadPendingPkce() {
  try {
    const raw = sessionStorage.getItem(PENDING_PKCE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw);
    return {
      state: parsed.state || "",
      verifier: parsed.verifier || "",
      challenge: parsed.challenge || "",
      method: parsed.method || "S256",
      returnTo: safeMobileReturn(parsed.returnTo),
      savedAt: parsed.savedAt || 0,
    };
  } catch {
    return null;
  }
}

export function clearPendingPkce() {
  sessionStorage.removeItem(PENDING_PKCE_KEY);
}

export function consumePendingPkce(state) {
  const pending = loadPendingPkce();
  if (pending && pending.state && state && pending.state !== state) {
    clearPendingPkce();
    return { ok: false, reason: "state_mismatch", returnTo: DEFAULT_MOBILE_HOME, verifier: "" };
  }
  const redirect = consumePendingRedirect(state);
  if (!redirect.ok) {
    clearPendingPkce();
    return { ok: false, reason: redirect.reason, returnTo: DEFAULT_MOBILE_HOME, verifier: "" };
  }
  clearPendingPkce();
  return {
    ok: true,
    reason: pending ? "pkce_matched" : redirect.reason,
    returnTo: pending?.returnTo || redirect.returnTo,
    verifier: pending?.verifier || "",
    challenge: pending?.challenge || "",
  };
}
