/**
 * DPO-C1: public Keycloak login shell flags only.
 * Reads the public issuer (and optional public client id). Never reads or returns a client secret.
 */

export const IDP_UNCONFIGURED_COPY = "演示 IdP 未配置";

export function keycloakPublicConfig(env = import.meta.env) {
  const issuer = String(env?.VITE_NEXUS_KEYCLOAK_ISSUER || "").trim().replace(/\/$/, "");
  const clientId = String(env?.VITE_NEXUS_KEYCLOAK_CLIENT_ID || "").trim();
  return {
    configured: Boolean(issuer),
    issuer,
    clientId,
    unconfiguredCopy: IDP_UNCONFIGURED_COPY,
  };
}

/** Authorization-code entry. Public parameters only — no secret query. */
export function keycloakAuthorizeHref({ issuer, clientId, redirectUri, state }) {
  const base = String(issuer || "").replace(/\/$/, "");
  const url = new URL(`${base}/protocol/openid-connect/auth`);
  url.searchParams.set("client_id", clientId || "nexus-web");
  url.searchParams.set("redirect_uri", redirectUri);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", "openid");
  if (state) url.searchParams.set("state", state);
  return url.toString();
}
