/**
 * LO4: where the browser goes after "退出". The server answers logout with `end_session_url` (Keycloak RP logout,
 * see docs/engineering/runbooks/oidc-rp-logout.md); following it clears the IdP SSO cookie so the next Keycloak
 * login shows the username / password form. Anything suspicious falls back to the plain `/login` route.
 */
import { keycloakPublicConfig } from "./keycloakPublic";

// `id_token_hint` is expected on an RP-logout URL; these are not and must never leave through the address bar.
const FORBIDDEN_QUERY_KEYS = ["access_token", "refresh_token", "id_token", "client_secret", "password", "code", "token"];

/**
 * Returns the URL to leave to, or "" to stay on the app's /login route.
 * When the public issuer is configured, the URL must be that issuer's logout endpoint.
 */
export function endSessionTarget(response, idp = keycloakPublicConfig()) {
  const raw = response && (response.end_session_url || response.endSessionUrl);
  if (typeof raw !== "string" || !raw.trim()) return "";
  let url;
  try {
    url = new URL(raw.trim());
  } catch {
    return "";
  }
  if (url.protocol !== "https:" && url.protocol !== "http:") return "";
  if (url.username || url.password) return "";
  for (const key of url.searchParams.keys()) {
    if (FORBIDDEN_QUERY_KEYS.includes(key.toLowerCase())) return "";
  }
  if (idp?.configured) {
    const expected = `${idp.issuer}/protocol/openid-connect/logout`;
    if (`${url.origin}${url.pathname}` !== expected) return "";
  }
  return url.toString();
}

/** Full-page navigation (not a router push) so Keycloak can clear its cookie. Kept separate for tests. */
export function leaveTo(url) {
  window.location.assign(url);
}
