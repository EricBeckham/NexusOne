/**
 * Desktop Authorization Code + PKCE leftover (G4).
 * BFF-shaped: authorize-url?pkce=s256 still mints code_verifier.
 * Not H5 /m/callback. Not a product G4 close.
 */
export const DESKTOP_PKCE_STORAGE = "nexus.g4.pkce";
export const DESKTOP_PKCE_TRACE = "0199a1c0-0000-7000-8000-00000000c219";
export const DESKTOP_PKCE_AFTER = "/p2/permissions/datascope";

export function saveDesktopPkcePending(pending) {
  sessionStorage.setItem(DESKTOP_PKCE_STORAGE, JSON.stringify(pending));
  return pending;
}

export function readDesktopPkcePending() {
  const raw = sessionStorage.getItem(DESKTOP_PKCE_STORAGE);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function clearDesktopPkcePending() {
  sessionStorage.removeItem(DESKTOP_PKCE_STORAGE);
}

export async function startDesktopPkceLogin({
  authorizeUrl,
  assignLocation,
  origin,
  traceId = DESKTOP_PKCE_TRACE,
}) {
  const body = await authorizeUrl({ pkce: "s256", traceId });
  const redirectUri = `${origin.replace(/\/$/, "")}/callback`;
  const pending = saveDesktopPkcePending({
    code_verifier: body.code_verifier || body.codeVerifier || "",
    state: body.state || "",
    trace_id: traceId,
    redirect_uri: redirectUri,
  });
  if (!pending.code_verifier) {
    throw new Error("authorize-url?pkce=s256 did not return code_verifier");
  }
  const url = body.url;
  if (!url) {
    throw new Error("authorize-url missing url");
  }
  if (!String(url).includes("code_challenge")) {
    throw new Error("authorize URL missing PKCE code_challenge");
  }
  assignLocation(url);
  return pending;
}

export async function completeDesktopPkceCallback({ code, state, login, applySession, rememberTrace }) {
  const pending = readDesktopPkcePending();
  if (!pending?.code_verifier) {
    throw new Error("missing desktop PKCE pending (code_verifier)");
  }
  if (state && pending.state && String(state) !== String(pending.state)) {
    throw new Error("state mismatch");
  }
  if (!code) {
    throw new Error("missing authorization code");
  }
  const view = await login(
    {
      code,
      redirect_uri: pending.redirect_uri,
      code_verifier: pending.code_verifier,
      codeVerifier: pending.code_verifier,
    },
    { traceId: pending.trace_id },
  );
  clearDesktopPkcePending();
  if (rememberTrace) {
    rememberTrace(pending.trace_id);
  }
  applySession(view);
  return { view, pending };
}
