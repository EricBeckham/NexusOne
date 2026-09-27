/**
 * Product Keycloak authorization-code callback (confidential client).
 * Browser posts code + redirect_uri to POST /api/v1/auth/login; the server
 * exchanges with the client secret. No PKCE verifier is held in the SPA.
 * Desktop leftover PKCE still uses api/pkce-desktop.js.
 */
export const PRODUCT_AUTH_AFTER = "/";

export async function completeProductAuthCodeCallback({
  code,
  redirectUri,
  login,
  applySession,
  rememberTrace,
  traceId,
}) {
  if (!code) {
    throw new Error("missing authorization code");
  }
  const redirect = String(redirectUri || "").trim();
  if (!redirect) {
    throw new Error("missing redirect_uri");
  }
  const view = await login(
    {
      code,
      redirect_uri: redirect,
      redirectUri: redirect,
    },
    traceId ? { traceId } : {},
  );
  if (rememberTrace && traceId) {
    rememberTrace(traceId);
  }
  applySession(view);
  return { view, pending: { trace_id: traceId || "", redirect_uri: redirect } };
}